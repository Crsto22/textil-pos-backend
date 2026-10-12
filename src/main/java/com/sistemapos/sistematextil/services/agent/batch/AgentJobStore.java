package com.sistemapos.sistematextil.services.agent.batch;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;
import com.sistemapos.sistematextil.services.agent.ai.AgentMediaRequest;

public interface AgentJobStore {
    Optional<JobReference> findByAnchorMessage(Long anchorMessageId);

    int invalidateOpenWork(Long sessionId, LocalDateTime obsoleteAt, String reason);

    JobReference enqueue(Long sessionId, Long anchorMessageId, LocalDateTime availableAt);

    Optional<JobLease> claimNext(String workerId, LocalDateTime now, LocalDateTime leaseUntil);

    default Optional<GeneratedTurn> findGeneratedTurn(Long jobId, String workerId) {
        return Optional.empty();
    }

    default boolean saveGeneratedTurn(Long jobId, String workerId, GeneratedTurn turn, LocalDateTime generatedAt) {
        throw new UnsupportedOperationException("Este almacenamiento no persiste respuestas generadas");
    }

    boolean complete(Long jobId, String workerId, LocalDateTime completedAt);

    FailureResult failOrRetry(
            Long jobId,
            String workerId,
            LocalDateTime failedAt,
            LocalDateTime retryAt,
            int maxAttempts,
            String error);

    RecoveryResult recoverExpired(LocalDateTime now, int maxAttempts);

    boolean isCurrent(Long jobId);

    record JobReference(Long jobId, Long sessionId, Long anchorMessageId, String status, LocalDateTime availableAt) {
    }

    record JobLease(
            Long jobId,
            Long sessionId,
            Long anchorMessageId,
            int attempt,
            String workerId,
            LocalDateTime leaseUntil) {
    }

    record GeneratedTurn(String customerMessage, String conversationSummary, boolean requiresHumanHelp,
            String provider, String model, long latencyMs, int toolCalls, String contextFingerprint,
            List<AgentMediaRequest> media) {
    }

    enum FailureResult {
        RETRY_SCHEDULED,
        FAILED_PERMANENTLY,
        IGNORED_STALE_LEASE
    }

    record RecoveryResult(int recovered, int failedPermanently) {
    }
}
