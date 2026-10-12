package com.sistemapos.sistematextil.services.agent.batch;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sistemapos.sistematextil.model.CrmWhatsappMessage;
import com.sistemapos.sistematextil.repositories.CrmWhatsappMessageRepository;
import com.sistemapos.sistematextil.services.agent.batch.AgentMessageBatch.Item;
import com.sistemapos.sistematextil.services.agent.config.AgentRuntimeConfigService;

@Service
public class AgentMessageBatchFactory {
    private final CrmWhatsappMessageRepository messageRepository;
    private final Duration window;
    private final int maxMessages;
    private final AgentRuntimeConfigService runtimeConfig;

    @Autowired
    public AgentMessageBatchFactory(
            CrmWhatsappMessageRepository messageRepository,
            AgentRuntimeConfigService runtimeConfig,
            @Value("${crm.whatsapp.agent.batch.window-ms:5000}") long windowMs,
            @Value("${crm.whatsapp.agent.batch.max-messages:15}") int maxMessages) {
        this.messageRepository = messageRepository;
        this.window = Duration.ofMillis(Math.max(500, Math.min(windowMs, 60000)));
        this.maxMessages = Math.max(1, Math.min(maxMessages, 50));
        this.runtimeConfig = runtimeConfig;
    }

    AgentMessageBatchFactory(CrmWhatsappMessageRepository messageRepository, long windowMs, int maxMessages) {
        this.messageRepository = messageRepository;
        this.window = Duration.ofMillis(Math.max(500, Math.min(windowMs, 60000)));
        this.maxMessages = Math.max(1, Math.min(maxMessages, 50));
        this.runtimeConfig = null;
    }

    @Transactional(readOnly = true)
    public AgentMessageBatch build(Long sessionId, Long conversationId, Long anchorMessageId) {
        if (sessionId == null || conversationId == null || anchorMessageId == null) {
            throw new IllegalArgumentException("Sesion, conversacion y mensaje ancla son obligatorios");
        }
        var config = runtimeConfig == null ? null : runtimeConfig.forSession(sessionId);
        Duration effectiveWindow = config == null ? window : Duration.ofMillis(config.messageGroupingMs());
        int effectiveMax = config == null ? maxMessages : config.maxMessagesPerBatch();
        List<CrmWhatsappMessage> candidates = messageRepository.findActiveMessagesEndingAt(
                conversationId, anchorMessageId, PageRequest.of(0, Math.min(200, effectiveMax * 4)));
        List<Item> descending = collect(candidates, anchorMessageId, effectiveWindow, effectiveMax);
        Collections.reverse(descending);
        List<Item> chronological = List.copyOf(descending);
        String combined = chronological.stream()
                .map(Item::content)
                .filter(value -> value != null && !value.isBlank())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
        return new AgentMessageBatch(
                sessionId,
                conversationId,
                anchorMessageId,
                chronological,
                combined,
                chronological.stream().anyMatch(Item::image),
                chronological.stream().anyMatch(Item::audio),
                chronological.stream().anyMatch(Item::transcriptionPending));
    }

    private List<Item> collect(List<CrmWhatsappMessage> candidates, Long anchorMessageId,
            Duration effectiveWindow, int effectiveMax) {
        List<Item> result = new ArrayList<>();
        LocalDateTime newerMessageAt = null;
        boolean anchorFound = false;
        for (CrmWhatsappMessage message : candidates) {
            if (!anchorFound) {
                if (!anchorMessageId.equals(message.getIdMessage())) continue;
                anchorFound = true;
            }
            if (isOperational(message)) continue;
            if ("OUTGOING".equalsIgnoreCase(message.getDirection())) break;
            if (!"INCOMING".equalsIgnoreCase(message.getDirection())) continue;
            if (newerMessageAt != null && message.getCreatedAt() != null
                    && Duration.between(message.getCreatedAt(), newerMessageAt).compareTo(effectiveWindow) > 0) {
                break;
            }
            result.add(toItem(message));
            if (message.getCreatedAt() != null) newerMessageAt = message.getCreatedAt();
            if (result.size() >= effectiveMax) break;
        }
        if (!anchorFound || result.isEmpty()) {
            throw new IllegalStateException("El mensaje ancla ya no esta disponible para agrupar");
        }
        return result;
    }

    private Item toItem(CrmWhatsappMessage message) {
        String type = normalize(message.getMessageType());
        boolean image = "IMAGE".equals(type) || "STICKER".equals(type);
        boolean audio = "AUDIO".equals(type);
        String transcript = clean(message.getAudioTranscription());
        boolean transcriptionPending = audio && transcript.isBlank();
        String text = audio && !transcript.isBlank() ? transcript : clean(message.getBody());
        List<String> parts = new ArrayList<>();
        if (!text.isBlank()) parts.add(text);
        if (image) parts.add("STICKER".equals(type) ? "[STICKER ADJUNTO]" : "[IMAGEN ADJUNTA]");
        if (audio && transcriptionPending) parts.add("[AUDIO PENDIENTE DE TRANSCRIPCION]");
        if (parts.isEmpty() && !"TEXT".equals(type)) parts.add("[" + type + " ADJUNTO]");
        return new Item(message.getIdMessage(), type, String.join("\n", parts), image, audio,
                transcriptionPending, message.getCreatedAt());
    }

    private boolean isOperational(CrmWhatsappMessage message) {
        return "SYSTEM".equalsIgnoreCase(message.getDirection())
                || "CRM_SYSTEM".equalsIgnoreCase(message.getOrigin());
    }

    private String normalize(String value) {
        String normalized = clean(value).toUpperCase(Locale.ROOT);
        return normalized.isBlank() ? "TEXT" : normalized;
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
