package com.sistemapos.sistematextil.services.agent.outbox;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sistemapos.sistematextil.services.CrmWhatsappBridgeService;
import com.sistemapos.sistematextil.services.CrmWhatsappChatService;
import com.sistemapos.sistematextil.services.S3StorageService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AgentWhatsappOutboxDeliveryService {
    private static final int MAX_MEDIA_BYTES = 10 * 1024 * 1024;
    private final CrmWhatsappBridgeService bridge;
    private final CrmWhatsappChatService chatService;
    private final S3StorageService storage;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public DeliveryReceipt send(AgentOutboxStore.OutboxLease lease) {
        AgentOutboxStore.OutboxEntry entry = lease.entry();
        CrmWhatsappChatService.AgentReplyPayload reply = chatService.prepararRespuestaAgente(
                entry.conversationId(), entry.replyToMessageId());
        ResponseEntity<String> response;
        if ("TEXT".equals(entry.messageType())) {
            response = bridge.enviarTexto(lease.conversationPhone(), entry.body(), reply.quotedObject(),
                    entry.idempotencyKey());
        } else {
            byte[] bytes = storage.readBytes(entry.mediaStoragePath());
            if (bytes.length == 0 || bytes.length > MAX_MEDIA_BYTES) {
                throw new DeliveryException("El archivo del outbox esta vacio o supera 10 MB", false);
            }
            response = bridge.enviarMedia(lease.conversationPhone(), bytes, entry.mediaFileName(),
                    entry.mediaMimeType(), entry.caption(), reply.quotedJson(), entry.idempotencyKey());
        }
        if (!response.getStatusCode().is2xxSuccessful()) {
            int status = response.getStatusCode().value();
            boolean retryable = status == 408 || status == 409 || status == 425 || status == 429 || status >= 500;
            throw new DeliveryException("Bridge WhatsApp respondio HTTP " + status, retryable);
        }
        try {
            JsonNode json = objectMapper.readTree(response.getBody() == null ? "{}" : response.getBody());
            String messageId = text(json, "messageId");
            if (messageId == null || messageId.isBlank()) {
                throw new DeliveryException("El bridge no devolvio messageId", true);
            }
            return new DeliveryReceipt(messageId, jsonString(json.get("messageKey")),
                    jsonString(json.get("baileysMessage")));
        } catch (DeliveryException error) {
            throw error;
        } catch (Exception error) {
            throw new DeliveryException("Respuesta invalida del bridge de WhatsApp", true, error);
        }
    }

    private String text(JsonNode json, String field) {
        JsonNode value = json.get(field); return value == null || value.isNull() ? null : value.asText();
    }
    private String jsonString(JsonNode value) { return value == null || value.isNull() ? null : value.toString(); }

    public record DeliveryReceipt(String whatsappMessageId, String messageKeyJson, String baileysMessageJson) {}

    public static class DeliveryException extends RuntimeException {
        private final boolean retryable;
        public DeliveryException(String message, boolean retryable) { super(message); this.retryable = retryable; }
        public DeliveryException(String message, boolean retryable, Throwable cause) { super(message, cause); this.retryable = retryable; }
        public boolean retryable() { return retryable; }
    }
}
