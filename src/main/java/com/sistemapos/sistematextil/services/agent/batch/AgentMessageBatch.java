package com.sistemapos.sistematextil.services.agent.batch;

import java.time.LocalDateTime;
import java.util.List;

public record AgentMessageBatch(
        Long sessionId,
        Long conversationId,
        Long anchorMessageId,
        List<Item> messages,
        String combinedInput,
        boolean containsImage,
        boolean containsAudio,
        boolean audioTranscriptionPending) {

    public record Item(
            Long messageId,
            String messageType,
            String content,
            boolean image,
            boolean audio,
            boolean transcriptionPending,
            LocalDateTime createdAt) {
    }
}
