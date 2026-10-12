package com.sistemapos.sistematextil.services.agent.batch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.sistemapos.sistematextil.services.agent.batch.AgentJobStore.FailureResult;
import com.sistemapos.sistematextil.services.agent.batch.AgentJobStore.JobLease;
import com.sistemapos.sistematextil.services.agent.batch.AgentJobStore.RecoveryResult;
import com.sistemapos.sistematextil.services.ai.AiProviderException;

class AgentJobCoordinatorTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 8, 18, 0);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-08T18:00:00Z"), ZoneOffset.UTC);

    @Test
    void claimsWithAnExclusiveLeaseAndCompletesOnlyTheCurrentJob() {
        AgentJobStore store = mock(AgentJobStore.class);
        AgentJobCoordinator coordinator = coordinator(store);
        when(store.claimNext(org.mockito.ArgumentMatchers.startsWith("worker-1:"), eq(NOW), eq(NOW.plusSeconds(90))))
                .thenAnswer(invocation -> Optional.of(new JobLease(
                        1L, 10L, 100L, 1, invocation.getArgument(0), NOW.plusSeconds(90))));
        when(store.isCurrent(1L)).thenReturn(true);

        JobLease claimed = coordinator.claimNext().orElseThrow();
        when(store.complete(1L, claimed.workerId(), NOW)).thenReturn(true);

        assertTrue(claimed.workerId().startsWith("worker-1:"));
        assertTrue(coordinator.complete(claimed));

        JobLease stale = new JobLease(2L, 10L, 99L, 1, claimed.workerId(), NOW.plusSeconds(90));
        when(store.isCurrent(2L)).thenReturn(false);
        assertFalse(coordinator.complete(stale));
        verify(store, never()).complete(eq(2L), eq(claimed.workerId()), any());
    }

    @Test
    void schedulesExponentialRetryAndHonorsTheAttemptLimitInTheStore() {
        AgentJobStore store = mock(AgentJobStore.class);
        AgentJobCoordinator coordinator = coordinator(store);
        JobLease secondAttempt = new JobLease(2L, 10L, 100L, 2, "worker-1:lease-2", NOW.plusSeconds(90));
        when(store.failOrRetry(eq(2L), eq("worker-1:lease-2"), eq(NOW), any(), eq(3), eq("fallo temporal")))
                .thenReturn(FailureResult.RETRY_SCHEDULED);

        FailureResult result = coordinator.fail(secondAttempt, new IllegalStateException("fallo temporal"));

        assertEquals(FailureResult.RETRY_SCHEDULED, result);
        ArgumentCaptor<LocalDateTime> retryAt = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(store).failOrRetry(eq(2L), eq("worker-1:lease-2"), eq(NOW), retryAt.capture(), eq(3), eq("fallo temporal"));
        assertEquals(NOW.plusSeconds(4), retryAt.getValue());
    }

    @Test
    void doesNotRetryAProviderErrorMarkedAsPermanent() {
        AgentJobStore store = mock(AgentJobStore.class);
        AgentJobCoordinator coordinator = coordinator(store);
        JobLease lease = new JobLease(2L, 10L, 100L, 1, "worker-1:lease-2", NOW.plusSeconds(90));
        when(store.failOrRetry(eq(2L), eq("worker-1:lease-2"), eq(NOW), any(), eq(1), eq("credencial invalida")))
                .thenReturn(FailureResult.FAILED_PERMANENTLY);

        assertEquals(FailureResult.FAILED_PERMANENTLY,
                coordinator.fail(lease, new AiProviderException("credencial invalida", false)));
    }

    @Test
    void delegatesRestartRecoveryWithTheConfiguredLimit() {
        AgentJobStore store = mock(AgentJobStore.class);
        AgentJobCoordinator coordinator = coordinator(store);
        when(store.recoverExpired(NOW, 3)).thenReturn(new RecoveryResult(2, 1));

        RecoveryResult result = coordinator.recoverExpired();

        assertEquals(2, result.recovered());
        assertEquals(1, result.failedPermanently());
    }

    @Test
    void simultaneousWorkersCanClaimTheConversationJobOnlyOnce() throws Exception {
        SingleLeaseStore store = new SingleLeaseStore();
        AgentJobCoordinator coordinator = coordinator(store);
        int workers = 12;
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(workers)) {
            var futures = java.util.stream.IntStream.range(0, workers)
                    .mapToObj(index -> executor.submit(() -> {
                        ready.countDown();
                        assertTrue(start.await(5, TimeUnit.SECONDS));
                        return coordinator.claimNext();
                    }))
                    .toList();
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            long claimed = 0;
            for (var future : futures) if (future.get(5, TimeUnit.SECONDS).isPresent()) claimed++;
            assertEquals(1, claimed);
        }
    }

    private AgentJobCoordinator coordinator(AgentJobStore store) {
        return new AgentJobCoordinator(
                store, CLOCK, "worker-1", Duration.ofSeconds(90), Duration.ofSeconds(2), 3);
    }

    private static final class SingleLeaseStore implements AgentJobStore {
        private final AtomicBoolean claimed = new AtomicBoolean();

        @Override
        public Optional<JobReference> findByAnchorMessage(Long anchorMessageId) { return Optional.empty(); }

        @Override
        public int invalidateOpenWork(Long sessionId, LocalDateTime obsoleteAt, String reason) { return 0; }

        @Override
        public JobReference enqueue(Long sessionId, Long anchorMessageId, LocalDateTime availableAt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<JobLease> claimNext(String workerId, LocalDateTime now, LocalDateTime leaseUntil) {
            return claimed.compareAndSet(false, true)
                    ? Optional.of(new JobLease(1L, 10L, 100L, 1, workerId, leaseUntil))
                    : Optional.empty();
        }

        @Override
        public boolean complete(Long jobId, String workerId, LocalDateTime completedAt) { return true; }

        @Override
        public FailureResult failOrRetry(Long jobId, String workerId, LocalDateTime failedAt,
                LocalDateTime retryAt, int maxAttempts, String error) {
            return FailureResult.IGNORED_STALE_LEASE;
        }

        @Override
        public RecoveryResult recoverExpired(LocalDateTime now, int maxAttempts) {
            return new RecoveryResult(0, 0);
        }

        @Override
        public boolean isCurrent(Long jobId) { return true; }
    }
}
