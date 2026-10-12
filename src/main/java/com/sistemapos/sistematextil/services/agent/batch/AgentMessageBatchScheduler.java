package com.sistemapos.sistematextil.services.agent.batch;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.sistemapos.sistematextil.model.CrmWhatsappAgentSession;
import com.sistemapos.sistematextil.model.CrmWhatsappAgentState;
import com.sistemapos.sistematextil.model.CrmWhatsappMessage;
import com.sistemapos.sistematextil.repositories.CrmWhatsappMessageRepository;
import com.sistemapos.sistematextil.services.agent.batch.AgentJobStore.JobReference;
import com.sistemapos.sistematextil.services.agent.config.AgentRuntimeConfigService;

@Service
public class AgentMessageBatchScheduler {
    private final CrmWhatsappMessageRepository messageRepository;
    private final AgentSessionResolver sessionResolver;
    private final AgentJobStore jobStore;
    private final Duration window;
    private final Clock clock;
    private final AgentRuntimeConfigService runtimeConfig;

    @Autowired
    public AgentMessageBatchScheduler(
            CrmWhatsappMessageRepository messageRepository,
            AgentSessionResolver sessionResolver,
            AgentJobStore jobStore,
            AgentRuntimeConfigService runtimeConfig,
            @Value("${crm.whatsapp.agent.batch.window-ms:5000}") long windowMs) {
        this(messageRepository, sessionResolver, jobStore,
                Duration.ofMillis(Math.max(500, Math.min(windowMs, 60000))), Clock.systemDefaultZone(), runtimeConfig);
    }

    AgentMessageBatchScheduler(
            CrmWhatsappMessageRepository messageRepository,
            AgentSessionResolver sessionResolver,
            AgentJobStore jobStore,
            Duration window,
            Clock clock) {
        this(messageRepository, sessionResolver, jobStore, window, clock, null);
    }

    AgentMessageBatchScheduler(
            CrmWhatsappMessageRepository messageRepository,
            AgentSessionResolver sessionResolver,
            AgentJobStore jobStore,
            Duration window,
            Clock clock,
            AgentRuntimeConfigService runtimeConfig) {
        this.messageRepository = messageRepository;
        this.sessionResolver = sessionResolver;
        this.jobStore = jobStore;
        this.window = window;
        this.clock = clock;
        this.runtimeConfig = runtimeConfig;
    }

    // This method is invoked by an AFTER_COMMIT listener. REQUIRED would reuse
    // the synchronization of the transaction that has just committed, leaving
    // JPA without an active transaction for the pessimistic conversation lock.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ScheduleResult schedule(Long messageId) {
        CrmWhatsappMessage message = messageRepository.findById(messageId).orElse(null);
        if (!eligible(message)) return ScheduleResult.ignored(messageId);
        Long connectionId = message.getConversation() == null || message.getConversation().getConnection() == null
                ? null : message.getConversation().getConnection().getIdConnection();
        String phone = message.getConversation() == null ? null
                : (message.getConversation().getPhoneNumber() == null
                        ? message.getConversation().getPhone() : message.getConversation().getPhoneNumber());
        Long conversationId = message.getConversation() == null ? null : message.getConversation().getIdConversation();
        if (runtimeConfig != null && !runtimeConfig.allowsIncoming(connectionId, conversationId, phone)) {
            return ScheduleResult.ignored(messageId);
        }
        var duplicate = jobStore.findByAnchorMessage(messageId);
        if (duplicate.isPresent()) return ScheduleResult.duplicate(duplicate.get());

        CrmWhatsappAgentSession session = sessionResolver.resolveForIncoming(message).orElse(null);
        if (session == null) return ScheduleResult.ignored(messageId);

        LocalDateTime now = LocalDateTime.now(clock);
        String reason = "Nuevo mensaje recibido; la respuesta anterior quedo obsoleta";
        int invalidated = jobStore.invalidateOpenWork(session.getIdAgentSession(), now, reason);
        if (isWorkflowEvidence(message, session.getState())) {
            return new ScheduleResult(false, false, null, session.getIdAgentSession(), messageId,
                    null, invalidated);
        }
        Duration effectiveWindow = runtimeConfig == null ? window
                : Duration.ofMillis(runtimeConfig.forConnection(connectionId).messageGroupingMs());
        JobReference job = jobStore.enqueue(session.getIdAgentSession(), messageId, now.plus(effectiveWindow));
        return new ScheduleResult(true, false, job.jobId(), session.getIdAgentSession(), messageId,
                job.availableAt(), invalidated);
    }

    public boolean isCurrentBeforeDelivery(Long jobId) {
        return jobId != null && jobStore.isCurrent(jobId);
    }

    private boolean eligible(CrmWhatsappMessage message) {
        return message != null
                && message.getDeletedAt() == null
                && "INCOMING".equalsIgnoreCase(message.getDirection());
    }

    private boolean isWorkflowEvidence(CrmWhatsappMessage message, CrmWhatsappAgentState state) {
        if (state != CrmWhatsappAgentState.ESPERANDO_COMPROBANTE
                && state != CrmWhatsappAgentState.VALIDANDO_COMPROBANTE
                && state != CrmWhatsappAgentState.ESPERANDO_PAGO_FILM
                && state != CrmWhatsappAgentState.VENTA_PENDIENTE_REVISION) return false;
        String mime = message.getMediaMimeType() == null ? "" : message.getMediaMimeType().toLowerCase();
        return mime.equals("image/jpeg") || mime.equals("image/png") || mime.equals("image/webp")
                || mime.equals("application/pdf");
    }

    public record ScheduleResult(
            boolean scheduled,
            boolean duplicate,
            Long jobId,
            Long sessionId,
            Long anchorMessageId,
            LocalDateTime availableAt,
            int invalidatedJobs) {

        static ScheduleResult ignored(Long messageId) {
            return new ScheduleResult(false, false, null, null, messageId, null, 0);
        }

        static ScheduleResult duplicate(JobReference job) {
            return new ScheduleResult(false, true, job.jobId(), job.sessionId(), job.anchorMessageId(),
                    job.availableAt(), 0);
        }
    }
}
