package com.sistemapos.sistematextil.services;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    private void record(CrmWhatsappConversation conversation, String type, String reason, String value) {
        auditService.record(conversation.getConnection(), conversation, null, null, type, "HIGH", reason,
                Map.of("contentHash", sha256(clean(value)), "length", clean(value).length()));
    }

    private String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception ignored) { return "unavailable"; }
    }
    private String clean(String value) { return value == null ? "" : value.trim(); }
}
