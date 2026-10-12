package com.sistemapos.sistematextil.services.agent.batch;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.sistemapos.sistematextil.services.agent.batch.AgentJobStore.FailureResult;
import com.sistemapos.sistematextil.services.agent.batch.AgentJobStore.JobLease;
import com.sistemapos.sistematextil.services.agent.batch.AgentJobStore.RecoveryResult;
import com.sistemapos.sistematextil.services.agent.config.AgentRuntimeConfigService;
import com.sistemapos.sistematextil.services.ai.AiProviderException;

@Service
public class AgentJobCoordinator {
    private final AgentJobStore jobStore;
    private final Clock clock;
    private final String workerId;
    private final Duration leaseDuration;
    private final Duration retryBaseDelay;
    private final int maxAttempts;
    private final AgentRuntimeConfigService runtimeConfig;

    @Autowired
    public AgentJobCoordinator(
            AgentJobStore jobStore,
            AgentRuntimeConfigService runtimeConfig,
            @Value("${crm.whatsapp.agent.job.lease-ms:90000}") long leaseMs,
            @Value("${crm.whatsapp.agent.job.retry-base-ms:2000}") long retryBaseMs,
            @Value("${crm.whatsapp.agent.job.max-attempts:3}") int maxAttempts) {
        this(jobStore, Clock.systemDefaultZone(), UUID.randomUUID().toString(),
                Duration.ofMillis(clamp(leaseMs, 10000, 600000)),
                Duration.ofMillis(clamp(retryBaseMs, 500, 60000)),
                (int) clamp(maxAttempts, 1, 10), runtimeConfig);
    }

    AgentJobCoordinator(
            AgentJobStore jobStore,
            Clock clock,
            String workerId,
            Duration leaseDuration,
            Duration retryBaseDelay,
            int maxAttempts) {
        this(jobStore, clock, workerId, leaseDuration, retryBaseDelay, maxAttempts, null);
    }

    AgentJobCoordinator(AgentJobStore jobStore, Clock clock, String workerId,
            Duration leaseDuration, Duration retryBaseDelay, int maxAttempts,
            AgentRuntimeConfigService runtimeConfig) {
        this.jobStore = jobStore;
        this.clock = clock;
        this.workerId = workerId;
        this.leaseDuration = leaseDuration;
        this.retryBaseDelay = retryBaseDelay;
        this.maxAttempts = maxAttempts;
        this.runtimeConfig = runtimeConfig;
    }

    public Optional<JobLease> claimNext() {
        LocalDateTime now = LocalDateTime.now(clock);
        String leaseOwner = workerId + ":" + UUID.randomUUID();
        return jobStore.claimNext(leaseOwner, now, now.plus(leaseDuration));
    }

    public boolean complete(JobLease lease) {
        if (!owns(lease) || !jobStore.isCurrent(lease.jobId())) return false;
        return jobStore.complete(lease.jobId(), lease.workerId(), LocalDateTime.now(clock));
    }

    public FailureResult fail(JobLease lease, Throwable error) {
        if (!owns(lease)) return FailureResult.IGNORED_STALE_LEASE;
        LocalDateTime now = LocalDateTime.now(clock);
        long multiplier = 1L << Math.min(Math.max(lease.attempt() - 1, 0), 6);
        LocalDateTime retryAt = now.plus(retryBaseDelay.multipliedBy(multiplier));
        int effectiveMax = runtimeConfig == null ? maxAttempts
                : runtimeConfig.forSession(lease.sessionId()).jobMaxAttempts();
        if (error instanceof AiProviderException providerError && !providerError.isRetryable()) {
            effectiveMax = 1;
        }
        return jobStore.failOrRetry(
                lease.jobId(), lease.workerId(), now, retryAt, effectiveMax, safeMessage(error));
    }

    public RecoveryResult recoverExpired() {
        return jobStore.recoverExpired(LocalDateTime.now(clock), maxAttempts);
    }

    public boolean isCurrent(JobLease lease) {
        return owns(lease) && jobStore.isCurrent(lease.jobId());
    }

    String workerId() {
        return workerId;
    }

    private boolean owns(JobLease lease) {
        return lease != null && lease.workerId() != null && lease.workerId().startsWith(workerId + ":");
    }

    private String safeMessage(Throwable error) {
        if (error == null) return "Error no especificado";
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }

    private static long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }
}
