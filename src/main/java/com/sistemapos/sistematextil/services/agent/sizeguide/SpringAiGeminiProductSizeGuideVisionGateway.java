package com.sistemapos.sistematextil.services.agent.sizeguide;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.sistemapos.sistematextil.services.CrmWhatsappAiCredentialService;
import com.sistemapos.sistematextil.services.CrmWhatsappAiCredentialService.CredentialMaterial;
import com.sistemapos.sistematextil.services.ai.AiProviderException;
import com.sistemapos.sistematextil.services.agent.ai.AiInvocationAuditService;
import com.sistemapos.sistematextil.services.agent.ai.AiInvocationPhase;
import com.sistemapos.sistematextil.services.agent.ai.AiTokenUsage;
import com.sistemapos.sistematextil.services.agent.sizeguide.ProductSizeGuideVisionGateway.Component;
import com.sistemapos.sistematextil.services.agent.sizeguide.ProductSizeGuideVisionGateway.Extraction;
import com.sistemapos.sistematextil.services.agent.sizeguide.ProductSizeGuideVisionGateway.Measurement;
import com.sistemapos.sistematextil.services.agent.sizeguide.ProductSizeGuideVisionGateway.VisionResult;

@Service
public class SpringAiGeminiProductSizeGuideVisionGateway implements ProductSizeGuideVisionGateway {
    private static final String SYSTEM_PROMPT = """
            Eres un extractor preciso de guias de tallas de prendas.
            Analiza solo la imagen adjunta y devuelve una estructura completa, sin recomendar tallas.
            Cada tabla o pieza de la prenda es un componente (por ejemplo BLAZER, CHALECO o PANTALON).
            Convierte la matriz visual en measurements: una fila por componente, talla y tipo de medida.
            Conserva etiquetas de talla como XS, S, M, L, XL, XXL, UNICA o numeros.
            measurementName debe ser el texto de la medida, por ejemplo CONTORNO DE CINTURA.
            Usa referenceValue cuando la celda contiene un valor exacto; minimumValue y maximumValue solo para rangos.
            Los valores numericos no llevan unidad. originalValue conserva el texto visible de la celda.
            toleranceValue contiene la tolerancia solo cuando la imagen la indica; en otro caso usa cadena vacia.
            measurementType debe ser CUERPO si la imagen indica medidas corporales, PRENDA si indica dimensiones
            de la prenda, o INDETERMINADO si no lo especifica claramente. Nunca lo adivines.
            unit debe ser CM o IN. No inventes componentes, tallas ni valores ilegibles.
            Los textos ausentes deben ser cadena vacia, las listas ausentes listas vacias y nunca null.
            sizeGuide es true solo si realmente hay una tabla de tallas o medidas.
            confidence va de 0 a 100 y representa la confianza global de lectura.
            """;

    private final CrmWhatsappAiCredentialService credentials;
    private final int timeoutMs;
    private final int maxOutputTokens;
    private final AiInvocationAuditService invocationAudit;

    public SpringAiGeminiProductSizeGuideVisionGateway(CrmWhatsappAiCredentialService credentials,
            int timeoutMs, int maxOutputTokens) {
        this(credentials, timeoutMs, maxOutputTokens, null);
    }

    @Autowired
    public SpringAiGeminiProductSizeGuideVisionGateway(CrmWhatsappAiCredentialService credentials,
            @Value("${crm.whatsapp.ai.gemini.timeout-ms:60000}") int timeoutMs,
            @Value("${crm.whatsapp.agent.size-guide.max-output-tokens:5000}") int maxOutputTokens,
            AiInvocationAuditService invocationAudit) {
        this.credentials = credentials;
        this.timeoutMs = Math.max(10000, timeoutMs);
        this.maxOutputTokens = Math.max(1500, maxOutputTokens);
        this.invocationAudit = invocationAudit;
    }

    @Override
    public VisionResult extract(Long connectionId, byte[] content, String mimeType) {
        if (connectionId == null) throw new IllegalArgumentException("No existe una conexion Gemini para analizar la guia");
        if (content == null || content.length == 0) throw new IllegalArgumentException("La guia de tallas esta vacia");
        MimeType mediaType = supported(mimeType);
        CredentialMaterial credential = credentials.resolve(connectionId);
        long started = System.nanoTime();
        ByteArrayResource image = new ByteArrayResource(content) {
            @Override public String getFilename() { return "guia-tallas" + extension(mediaType.toString()); }
        };
        try (Client client = Client.builder().apiKey(credential.apiKey())
                .httpOptions(HttpOptions.builder().timeout(timeoutMs).build()).build()) {
            GoogleGenAiChatOptions options = GoogleGenAiChatOptions.builder().model(credential.model())
                    .temperature(0.0).maxOutputTokens(maxOutputTokens).build();
            var model = GoogleGenAiChatModel.builder().genAiClient(client).options(options).build();
            var entity = ChatClient.create(model).prompt().system(SYSTEM_PROMPT)
                    .user(user -> user.text("Extrae completamente la guia de tallas adjunta.").media(mediaType, image))
                    .call().responseEntity(RawExtraction.class);
            RawExtraction raw = entity.entity();
            Extraction extraction = normalize(raw);
            long latency = Duration.ofNanos(System.nanoTime() - started).toMillis();
            audit(connectionId, credential, latency,
                    AiTokenUsage.from(entity.response().getMetadata().getUsage()), null);
            return new VisionResult("GEMINI", credential.model(), credential.source(), latency, extraction);
        } catch (RuntimeException error) {
            audit(connectionId, credential, Duration.ofNanos(System.nanoTime() - started).toMillis(), null,
                    error.getClass().getSimpleName());
            throw error instanceof AiProviderException ai ? ai
                    : new AiProviderException("No se pudo analizar la guia de tallas: " + safe(error), true, error);
        }
    }

    private void audit(Long connectionId, CredentialMaterial credential, long latency, AiTokenUsage usage,
            String errorCode) {
        if (invocationAudit == null) return;
        var invocation = new AiInvocationAuditService.Invocation(connectionId, null, null, null,
                AiInvocationPhase.SIZE_GUIDE, "GEMINI", credential.model(), credential.apiKey(), latency, 0);
        if (errorCode == null) invocationAudit.success(invocation, usage);
        else invocationAudit.failure(invocation, errorCode);
    }

    Extraction normalize(RawExtraction value) {
        if (value == null) return null;
        List<Component> components = value.components() == null ? List.of() : value.components().stream()
                .filter(java.util.Objects::nonNull)
                .map(component -> new Component(clean(component.name()), clean(component.description()),
                        component.measurements() == null ? List.of() : component.measurements().stream()
                                .filter(java.util.Objects::nonNull)
                                .map(row -> new Measurement(clean(row.sizeLabel()), clean(row.measurementName()),
                                        decimal(row.referenceValue()), decimal(row.minimumValue()),
                                        decimal(row.maximumValue()), decimal(row.toleranceValue()),
                                        clean(row.originalValue())))
                                .toList()))
                .toList();
        return new Extraction(value.sizeGuide(), clean(value.measurementType()).toUpperCase(Locale.ROOT),
                clean(value.unit()).toUpperCase(Locale.ROOT), clean(value.description()), components,
                value.confidence());
    }

    private BigDecimal decimal(String value) {
        String normalized = clean(value).replace(',', '.').replaceAll("[^0-9.\\-]", "");
        if (normalized.isBlank() || "-".equals(normalized) || ".".equals(normalized)) return null;
        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Gemini devolvio una medida numerica invalida: " + clean(value));
        }
    }

    private MimeType supported(String value) {
        String type = clean(value).toLowerCase(Locale.ROOT);
        if (!List.of("image/jpeg", "image/png", "image/webp").contains(type)) {
            throw new IllegalArgumentException("Formato de guia no compatible: " + type);
        }
        return MimeTypeUtils.parseMimeType(type);
    }

    private String extension(String mimeType) {
        return "image/png".equals(mimeType) ? ".png" : "image/webp".equals(mimeType) ? ".webp" : ".jpg";
    }

    private String clean(String value) { return value == null ? "" : value.trim(); }
    private String safe(Throwable error) {
        return error.getMessage() == null || error.getMessage().isBlank()
                ? error.getClass().getSimpleName() : error.getMessage();
    }

    record RawExtraction(boolean sizeGuide, String measurementType, String unit, String description,
            List<RawComponent> components, int confidence) {}
    record RawComponent(String name, String description, List<RawMeasurement> measurements) {}
    record RawMeasurement(String sizeLabel, String measurementName, String referenceValue,
            String minimumValue, String maximumValue, String toleranceValue, String originalValue) {}
}
