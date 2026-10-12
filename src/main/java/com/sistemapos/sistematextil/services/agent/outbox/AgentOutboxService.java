package com.sistemapos.sistematextil.services.agent.outbox;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AgentOutboxService {
    public static final String OPERATIONAL_ORIGIN_MARKER = "CRM_SYSTEM";
    private final AgentOutboxStore store;

    public AgentOutboxStore.OutboxEntry enqueueText(Long sessionId, Long jobId, Long replyToMessageId,
            int ordinal, String text) {
        return store.enqueue(new AgentOutboxStore.EnqueueCommand(sessionId, jobId, replyToMessageId,
                ordinal, "TEXT", text, null, null, null, null));
    }

    /** Envia un aviso fijo que debe quedar fuera del contexto conversacional de Gemini. */
    public AgentOutboxStore.OutboxEntry enqueueOperationalText(Long sessionId, Long jobId,
            Long replyToMessageId, int ordinal, String text) {
        return store.enqueue(new AgentOutboxStore.EnqueueCommand(sessionId, jobId, replyToMessageId,
                ordinal, "TEXT", text, null, null, null, OPERATIONAL_ORIGIN_MARKER));
    }

    public AgentOutboxStore.OutboxEntry enqueueImage(Long sessionId, Long jobId, Long replyToMessageId,
            int ordinal, String storagePath, String fileName, String mimeType, String caption) {
        return enqueueMedia(sessionId, jobId, replyToMessageId, ordinal, "IMAGE", storagePath,
                fileName, mimeType, caption);
    }

    public AgentOutboxStore.OutboxEntry enqueueDocument(Long sessionId, Long jobId, Long replyToMessageId,
            int ordinal, String storagePath, String fileName, String mimeType, String caption) {
        return enqueueMedia(sessionId, jobId, replyToMessageId, ordinal, "DOCUMENT", storagePath,
                fileName, mimeType, caption);
    }

    private AgentOutboxStore.OutboxEntry enqueueMedia(Long sessionId, Long jobId, Long replyToMessageId,
            int ordinal, String type, String storagePath, String fileName, String mimeType, String caption) {
        return store.enqueue(new AgentOutboxStore.EnqueueCommand(sessionId, jobId, replyToMessageId,
                ordinal, type, null, storagePath, fileName, mimeType, caption));
    }
}
