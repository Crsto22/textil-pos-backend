package com.sistemapos.sistematextil.services;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.sistemapos.sistematextil.model.CrmWhatsappConversation;

@Service
public class CrmWhatsappAiSafetyService {
    private static final List<String> INJECTION_MARKERS = List.of(
            "ignora las instrucciones", "ignore previous", "prompt del sistema", "system prompt",
            "actua como administrador", "ejecuta sql", "cambia de sucursal", "usa otro cliente",
            "revela tu prompt", "herramienta no autorizada");
    private static final List<String> FORBIDDEN_CLAIMS = List.of(
            "pago confirmado", "pago validado", "producto reservado", "stock reservado",
            "descuento autorizado", "venta emitida", "compra registrada automaticamente");
    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s)]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern MONEY_PATTERN = Pattern.compile("(?i)(?:S/|S\\.)\\s*(\\d+(?:[.,]\\d{1,2})?)");
    private static final Pattern EMPHASIS_PATTERN = Pattern.compile("\\*([^*\\n]{2,100})\\*");
    private static final Set<String> SAFE_LABELS = Set.of(
            "productos disponibles", "modelos disponibles", "colores", "tallas", "precio", "stock",
            "modalidad", "envios desde", "ver producto", "promociones", "ofertas", "metodos de pago");
    private final CrmWhatsappAiAuditService auditService;

    public CrmWhatsappAiSafetyService(CrmWhatsappAiAuditService auditService) { this.auditService = auditService; }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String validateInput(CrmWhatsappConversation conversation, String body) {
        String text = clean(body);
        String reason = text.length() > 2000 ? "El mensaje supera el limite seguro de procesamiento"
                : INJECTION_MARKERS.stream().anyMatch(text.toLowerCase(Locale.ROOT)::contains)
                        ? "Se detectaron instrucciones que intentan cambiar las reglas de la IA" : null;
        if (reason != null) record(conversation, "INPUT_BLOCKED", reason, text);
        return reason;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String validateOutput(CrmWhatsappConversation conversation, String output) {
        String normalized = clean(output).toLowerCase(Locale.ROOT);
        String reason = FORBIDDEN_CLAIMS.stream().anyMatch(normalized::contains)
                ? "La respuesta contenia una afirmacion comercial no permitida" : null;
        if (reason != null) record(conversation, "OUTPUT_BLOCKED", reason, output);
        return reason;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String validateGroundedOutput(
            CrmWhatsappConversation conversation,
            String output,
            List<Map<String, Object>> toolResults) {
        String forbidden = forbiddenReason(output);
        if (forbidden != null) {
            record(conversation, "OUTPUT_BLOCKED", forbidden, output);
            return forbidden;
        }
        Grounding grounding = grounding(toolResults);
        Matcher urls = URL_PATTERN.matcher(clean(output));
        while (urls.find()) {
            String url = stripTrailingPunctuation(urls.group());
            if (!grounding.text().contains(url.toLowerCase(Locale.ROOT))) {
                return groundedFailure(conversation, output, "La respuesta incluyo un enlace no respaldado");
            }
        }
        Matcher money = MONEY_PATTERN.matcher(clean(output));
        while (money.find()) {
            BigDecimal amount = decimal(money.group(1));
            if (amount != null && grounding.amounts().stream().noneMatch(value -> value.compareTo(amount) == 0)) {
                return groundedFailure(conversation, output, "La respuesta incluyo un importe no respaldado");
            }
        }
        Matcher emphasized = EMPHASIS_PATTERN.matcher(clean(output));
        while (emphasized.find()) {
            String claim = normalize(emphasized.group(1).replace(":", ""));
            if (claim.isBlank() || SAFE_LABELS.contains(claim)) continue;
            if (!grounding.text().contains(claim)) {
                return groundedFailure(conversation, output, "La respuesta incluyo un producto o dato destacado no respaldado");
            }
        }
        return null;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFallback(CrmWhatsappConversation conversation, String reason) {
        auditService.record(conversation.getConnection(), conversation, null, null,
                "NATURAL_RESPONSE_FALLBACK", "WARN", clean(reason), Map.of());
    }

    private String groundedFailure(CrmWhatsappConversation conversation, String output, String reason) {
        record(conversation, "OUTPUT_GROUNDING_BLOCKED", reason, output);
        return reason;
    }

    private String forbiddenReason(String output) {
        String normalized = clean(output).toLowerCase(Locale.ROOT);
        return FORBIDDEN_CLAIMS.stream().anyMatch(normalized::contains)
                ? "La respuesta contenia una afirmacion comercial no permitida" : null;
    }

    private Grounding grounding(List<Map<String, Object>> toolResults) {
        StringBuilder text = new StringBuilder();
        List<BigDecimal> amounts = new ArrayList<>();
        collect(toolResults == null ? List.of() : toolResults, text, amounts);
        return new Grounding(normalize(text.toString()), amounts);
    }

    private void collect(Object value, StringBuilder text, List<BigDecimal> amounts) {
        if (value == null) return;
        if (value instanceof Map<?, ?> map) {
            map.values().forEach(item -> collect(item, text, amounts));
            return;
        }
        if (value instanceof Iterable<?> iterable) {
            iterable.forEach(item -> collect(item, text, amounts));
            return;
        }
        text.append(' ').append(String.valueOf(value));
        if (value instanceof Number number) amounts.add(new BigDecimal(number.toString()));
        if (value instanceof String string) {
            BigDecimal parsed = decimal(string);
            if (parsed != null) amounts.add(parsed);
        }
    }

    private BigDecimal decimal(String value) {
        String clean = clean(value).replace(',', '.');
        if (!clean.matches("\\d+(?:\\.\\d{1,6})?")) return null;
        try { return new BigDecimal(clean); } catch (NumberFormatException ignored) { return null; }
    }

    private String normalize(String value) {
        return clean(value).toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private String stripTrailingPunctuation(String value) {
        return clean(value).replaceAll("[.,;:!?]+$", "");
    }

    private void record(CrmWhatsappConversation conversation, String type, String reason, String value) {
        auditService.record(conversation.getConnection(), conversation, null, null, type, "HIGH", reason,
                Map.of("contentHash", sha256(clean(value)), "length", clean(value).length()));
    }

    private String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception ignored) { return "unavailable"; }
    }
    private String clean(String value) { return value == null ? "" : value.trim(); }
    private record Grounding(String text, List<BigDecimal> amounts) {}
}
