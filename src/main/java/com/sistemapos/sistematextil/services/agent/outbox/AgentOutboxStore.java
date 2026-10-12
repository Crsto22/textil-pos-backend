package com.sistemapos.sistematextil.services.agent.outbox;

import java.time.LocalDateTime;
import java.util.Optional;

public interface AgentOutboxStore {
    OutboxEntry enqueue(EnqueueCommand command);

    Optional<OutboxLease> claimNext(String workerId, LocalDateTime now, LocalDateTime leaseUntil);

    boolean complete(Long outboxId, String workerId, String whatsappMessageId, Long messageId, LocalDateTime sentAt);

    FailureResult failOrRetry(Long outboxId, String workerId, LocalDateTime now, LocalDateTime retryAt,
            int maxAttempts, String error, boolean retryable);

    RecoveryResult recoverExpired(LocalDateTime now, int maxAttempts);

    record EnqueueCommand(Long sessionId, Long jobId, Long replyToMessageId, int ordinal,
            String messageType, String body, String mediaStoragePath, String mediaFileName,
            String mediaMimeType, String caption) {
    }

    record OutboxEntry(Long outboxId, Long sessionId, Long jobId, Long conversationId,
            Long replyToMessageId, String idempotencyKey, long sequenceNo, String messageType,
            String body, String mediaStoragePath, String mediaFileName, String mediaMimeType,
            String caption, String status) {
    }

    record OutboxLease(OutboxEntry entry, int attempt, String workerId, LocalDateTime leaseUntil,
            String conversationPhone) {
    }

    enum FailureResult { RETRY_SCHEDULED, FAILED_PERMANENTLY, IGNORED_STALE_LEASE }

    record RecoveryResult(int recovered, int failedPermanently) {
    }
}
