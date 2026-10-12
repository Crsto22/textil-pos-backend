package com.sistemapos.sistematextil.services.agent.batch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.sistemapos.sistematextil.model.CrmWhatsappAgentSession;
import com.sistemapos.sistematextil.model.CrmWhatsappAgentState;
import com.sistemapos.sistematextil.model.CrmWhatsappConversation;
import com.sistemapos.sistematextil.model.CrmWhatsappMessage;
import com.sistemapos.sistematextil.repositories.CrmWhatsappMessageRepository;
import com.sistemapos.sistematextil.services.agent.batch.AgentJobStore.JobReference;

class AgentMessageBatchSchedulerTest {

    @Test
    void startsANewTransactionWhenCalledAfterWebhookCommit() throws Exception {
        Transactional transactional = AgentMessageBatchScheduler.class
                .getMethod("schedule", Long.class)
                .getAnnotation(Transactional.class);

        assertNotNull(transactional);
        assertSame(Propagation.REQUIRES_NEW, transactional.propagation());
    }

    @Test
    void fourConsecutiveMessagesLeaveOnlyTheLastJobExecutableAndRestartTheWindow() {
        CrmWhatsappMessageRepository messages = mock(CrmWhatsappMessageRepository.class);
        AgentSessionResolver sessions = mock(AgentSessionResolver.class);
        InMemoryJobStore jobs = new InMemoryJobStore();
        Clock clock = mock(Clock.class);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(clock.instant()).thenReturn(
                Instant.parse("2026-10-08T18:00:00Z"),
                Instant.parse("2026-10-08T18:00:01Z"),
                Instant.parse("2026-10-08T18:00:02Z"),
                Instant.parse("2026-10-08T18:00:03Z"));
        AgentMessageBatchScheduler scheduler = new AgentMessageBatchScheduler(
                messages, sessions, jobs, Duration.ofSeconds(5), clock);
        CrmWhatsappAgentSession session = new CrmWhatsappAgentSession();
        session.setIdAgentSession(10L);
        when(sessions.resolveForIncoming(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Optional.of(session));
        for (long id = 1; id <= 4; id++) when(messages.findById(id)).thenReturn(Optional.of(message(id)));

        List<AgentMessageBatchScheduler.ScheduleResult> results = new ArrayList<>();
        for (long id = 1; id <= 4; id++) results.add(scheduler.schedule(id));

        assertTrue(results.stream().allMatch(AgentMessageBatchScheduler.ScheduleResult::scheduled));
        assertEquals(4, jobs.jobs.size());
        assertEquals(3, jobs.jobs.stream().filter(job -> "OBSOLETE".equals(job.status())).count());
        JobReference current = jobs.jobs.getLast();
        assertEquals(4L, current.anchorMessageId());
        assertEquals(LocalDateTime.of(2026, 10, 8, 18, 0, 8), current.availableAt());
        assertTrue(scheduler.isCurrentBeforeDelivery(current.jobId()));
        assertFalse(scheduler.isCurrentBeforeDelivery(jobs.jobs.getFirst().jobId()));
    }

    @Test
    void paymentPdfIsHandledByTheEvidenceFlowAndDoesNotCreateAConversationJob() {
        CrmWhatsappMessageRepository messages = mock(CrmWhatsappMessageRepository.class);
        AgentSessionResolver sessions = mock(AgentSessionResolver.class);
        InMemoryJobStore jobs = new InMemoryJobStore();
        Clock clock = Clock.fixed(Instant.parse("2026-10-08T18:00:00Z"), ZoneId.of("UTC"));
        AgentMessageBatchScheduler scheduler = new AgentMessageBatchScheduler(
                messages, sessions, jobs, Duration.ofSeconds(5), clock);
        CrmWhatsappAgentSession session = new CrmWhatsappAgentSession();
        session.setIdAgentSession(10L);
        session.setState(CrmWhatsappAgentState.ESPERANDO_COMPROBANTE);
        CrmWhatsappMessage paymentPdf = message(7L);
        paymentPdf.setMessageType("DOCUMENT");
        paymentPdf.setMediaMimeType("application/pdf");
        when(messages.findById(7L)).thenReturn(Optional.of(paymentPdf));
        when(sessions.resolveForIncoming(paymentPdf)).thenReturn(Optional.of(session));

        AgentMessageBatchScheduler.ScheduleResult result = scheduler.schedule(7L);

        assertFalse(result.scheduled());
        assertTrue(jobs.jobs.isEmpty());
    }

    private CrmWhatsappMessage message(long id) {
        CrmWhatsappConversation conversation = new CrmWhatsappConversation();
        conversation.setIdConversation(20L);
        CrmWhatsappMessage message = new CrmWhatsappMessage();
        message.setIdMessage(id);
        message.setConversation(conversation);
        message.setDirection("INCOMING");
        return message;
    }

    private static final class InMemoryJobStore implements AgentJobStore {
        private final List<JobReference> jobs = new ArrayList<>();
        private long sequence;

        @Override
        public Optional<JobReference> findByAnchorMessage(Long anchorMessageId) {
            return jobs.stream().filter(job -> job.anchorMessageId().equals(anchorMessageId)).findFirst();
        }

        @Override
        public int invalidateOpenWork(Long sessionId, LocalDateTime obsoleteAt, String reason) {
            int count = 0;
            for (int index = 0; index < jobs.size(); index++) {
                JobReference job = jobs.get(index);
                if (job.sessionId().equals(sessionId) && "PENDING".equals(job.status())) {
                    jobs.set(index, new JobReference(job.jobId(), job.sessionId(), job.anchorMessageId(),
                            "OBSOLETE", job.availableAt()));
                    count++;
                }
            }
            return count;
        }

        @Override
        public JobReference enqueue(Long sessionId, Long anchorMessageId, LocalDateTime availableAt) {
            JobReference job = new JobReference(++sequence, sessionId, anchorMessageId, "PENDING", availableAt);
            jobs.add(job);
            return job;
        }

        @Override
        public Optional<JobLease> claimNext(String workerId, LocalDateTime now, LocalDateTime leaseUntil) {
            return Optional.empty();
        }

        @Override
        public boolean complete(Long jobId, String workerId, LocalDateTime completedAt) {
            return false;
        }

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
        public boolean isCurrent(Long jobId) {
            return !jobs.isEmpty() && jobs.getLast().jobId().equals(jobId)
                    && "PENDING".equals(jobs.getLast().status());
        }
    }
}
