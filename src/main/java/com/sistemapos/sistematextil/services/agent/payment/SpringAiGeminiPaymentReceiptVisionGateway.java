package com.sistemapos.sistematextil.services.agent.payment;

import java.time.Duration;
import java.util.Locale;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

import com.google.genai.Client;
import com.google.genai.errors.ApiException;
import com.google.genai.errors.GenAiIOException;
import com.google.genai.types.HttpOptions;
import com.sistemapos.sistematextil.services.CrmWhatsappAiCredentialService;
import com.sistemapos.sistematextil.services.CrmWhatsappAiCredentialService.CredentialMaterial;
import com.sistemapos.sistematextil.services.ai.AiProviderException;

@Service
public class SpringAiGeminiPaymentReceiptVisionGateway implements AgentPaymentReceiptVisionGateway {
    private static final String SYSTEM_PROMPT = """
            Eres un extractor OCR de comprobantes de pago peruanos.
            Analiza solamente el archivo adjunto y devuelve datos estructurados; no apruebes ni rechaces pagos.
            Prioriza con máxima precisión estos datos obligatorios: amount, operationDate y operationNumber.
            operationTime y los demás campos son informativos y pueden quedar vacíos si no son visibles.
            No uses datos supuestos y no completes campos ilegibles. Para todo campo de texto ausente usa
            una cadena vacia (""); nunca devuelvas null porque el esquema exige textos.
            amount debe contener solamente el importe decimal, sin simbolos ni separadores de miles.
            currency debe ser un codigo ISO de tres letras, normalmente PEN.
            operationDate debe estar en formato ISO yyyy-MM-dd.
            operationTime debe estar en formato HH:mm:ss de 24 horas; usa 00 segundos si no aparecen.
            operationNumber debe conservar las letras y numeros visibles del codigo o numero de operacion.
            recipient es el nombre visible de la persona o empresa que recibe el pago.
            destinationAccount es el celular, cuenta o identificador visible del destinatario.
            paymentMethod es la aplicacion, banco o medio visible, por ejemplo YAPE, PLIN o TRANSFERENCIA.
            visibleText debe transcribir de forma compacta todo el texto legible relevante.
            paymentReceipt solo es true cuando el archivo parece realmente un comprobante de pago.
            confidence va de 0 a 100 y representa la confianza global de lectura del documento.
            """;

    private final CrmWhatsappAiCredentialService credentialService;
    private final int timeoutMs;
    private final int maxOutputTokens;

    public SpringAiGeminiPaymentReceiptVisionGateway(
            CrmWhatsappAiCredentialService credentialService,
            @Value("${crm.whatsapp.ai.gemini.timeout-ms:60000}") int timeoutMs,
            @Value("${crm.whatsapp.agent.payment-vision.max-output-tokens:1200}") int maxOutputTokens) {
        this.credentialService = credentialService;
        this.timeoutMs = Math.max(10000, timeoutMs);
        this.maxOutputTokens = Math.max(512, maxOutputTokens);
    }

    @Override
    public VisionResult extract(Long connectionId, byte[] content, String mimeType) {
        if (connectionId == null) throw new IllegalArgumentException("La conexion es obligatoria");
        if (content == null || content.length == 0) throw new IllegalArgumentException("El comprobante esta vacio");
        MimeType mediaType = supportedMimeType(mimeType);
        CredentialMaterial credentials = resolveCredentials(connectionId);
        long started = System.nanoTime();
        ByteArrayResource resource = new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return "comprobante" + extension(mediaType.toString());
            }
        };

        try (Client client = Client.builder()
                .apiKey(credentials.apiKey())
                .httpOptions(HttpOptions.builder().timeout(timeoutMs).build())
                .build()) {
            GoogleGenAiChatOptions options = GoogleGenAiChatOptions.builder()
                    .model(credentials.model())
                    .temperature(0.0)
                    .maxOutputTokens(maxOutputTokens)
                    .build();
            GoogleGenAiChatModel model = GoogleGenAiChatModel.builder()
                    .genAiClient(client)
                    .options(options)
                    .build();
            AgentPaymentReceiptExtraction extraction = ChatClient.create(model).prompt()
                    .system(SYSTEM_PROMPT)
                    .user(user -> user
                            .text("Extrae todos los datos visibles del comprobante adjunto.")
                            .media(mediaType, resource))
                    .call()
                    // Los campos OCR de texto son opcionales. No se usa la validacion
                    // estricta del schema porque Gemini puede representar un dato no
                    // visible como null; Java lo normaliza y valida de forma segura.
                    .entity(AgentPaymentReceiptExtraction.class);
            extraction = normalize(extraction);
            validate(extraction);
            return new VisionResult("GEMINI", credentials.model(), credentials.source(),
                    Duration.ofNanos(System.nanoTime() - started).toMillis(), extraction);
        } catch (AiProviderException error) {
            throw error;
        } catch (RuntimeException error) {
            throw mapProviderError(error);
        }
    }

    private CredentialMaterial resolveCredentials(Long connectionId) {
        try {
            return credentialService.resolve(connectionId);
        } catch (IllegalStateException error) {
            throw new AiProviderException(error.getMessage(), false, error);
        }
    }

    private MimeType supportedMimeType(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!(normalized.equals("image/jpeg") || normalized.equals("image/png")
                || normalized.equals("image/webp") || normalized.equals("application/pdf"))) {
            throw new IllegalArgumentException("Formato de comprobante no compatible: " + normalized);
        }
        return MimeTypeUtils.parseMimeType(normalized);
    }

    private void validate(AgentPaymentReceiptExtraction extraction) {
        if (extraction == null) {
            throw new AiProviderException("Gemini no devolvio una extraccion estructurada", true);
        }
        if (extraction.confidence() < 0 || extraction.confidence() > 100) {
            throw new AiProviderException("Gemini devolvio una confianza fuera de rango", true);
        }
    }

    AgentPaymentReceiptExtraction normalize(AgentPaymentReceiptExtraction extraction) {
        if (extraction == null) return null;
        return new AgentPaymentReceiptExtraction(
                extraction.paymentReceipt(), clean(extraction.amount()), clean(extraction.currency()),
                clean(extraction.operationDate()), clean(extraction.operationTime()), clean(extraction.recipient()),
                clean(extraction.destinationAccount()), clean(extraction.operationNumber()),
                clean(extraction.paymentMethod()), clean(extraction.visibleText()), extraction.confidence());
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private AiProviderException mapProviderError(RuntimeException error) {
        ApiException apiError = findCause(error, ApiException.class);
        if (apiError != null) {
            int code = apiError.code();
            boolean retryable = code == 408 || code == 429 || code >= 500;
            return new AiProviderException("Gemini Vision respondio HTTP " + code + ": " + safe(error),
                    retryable, error);
        }
        if (findCause(error, GenAiIOException.class) != null) {
            return new AiProviderException("No se pudo conectar con Gemini Vision: " + safe(error), true, error);
        }
        return new AiProviderException("No se pudo analizar el comprobante: " + safe(error), true, error);
    }

    private <T extends Throwable> T findCause(Throwable error, Class<T> type) {
        Throwable current = error;
        while (current != null) {
            if (type.isInstance(current)) return type.cast(current);
            current = current.getCause();
        }
        return null;
    }

    private String extension(String mimeType) {
        return switch (mimeType) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "application/pdf" -> ".pdf";
            default -> ".jpg";
        };
    }

    private String safe(Throwable error) {
        return error.getMessage() == null || error.getMessage().isBlank()
                ? error.getClass().getSimpleName() : error.getMessage();
    }
}
