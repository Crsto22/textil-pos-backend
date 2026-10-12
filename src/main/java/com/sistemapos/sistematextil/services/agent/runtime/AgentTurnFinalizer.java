package com.sistemapos.sistematextil.services.agent.runtime;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sistemapos.sistematextil.model.CrmWhatsappAgentEvent;
import com.sistemapos.sistematextil.model.CrmWhatsappAgentSession;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAgentSessionRepository;
import com.sistemapos.sistematextil.services.agent.CrmWhatsappAgentStateMachine;
import com.sistemapos.sistematextil.services.agent.batch.AgentJobStore.GeneratedTurn;
import com.sistemapos.sistematextil.services.agent.batch.AgentJobStore.JobLease;
import com.sistemapos.sistematextil.services.agent.config.AgentRuntimeConfigService;
import com.sistemapos.sistematextil.services.agent.outbox.AgentOutboxService;
import com.sistemapos.sistematextil.services.agent.catalog.AgentCatalogQueryService;
import com.sistemapos.sistematextil.services.agent.ai.AgentMediaRequest;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AgentTurnFinalizer {
    private final AgentOutboxService outbox;
    private final AgentCatalogQueryService catalog;
    private final CrmWhatsappAgentSessionRepository sessions;
    private final CrmWhatsappAgentStateMachine stateMachine;
    private final AgentRuntimeConfigService runtimeConfig;
    private final JdbcTemplate jdbc;

    @Transactional
    public void finalizeTurn(JobLease lease, GeneratedTurn turn) {
        CrmWhatsappAgentSession session = sessions.findForUpdate(lease.sessionId())
                .orElseThrow(() -> new IllegalStateException("La sesion del agente ya no existe"));
        if (!Boolean.TRUE.equals(session.getActiveSlot()) || session.getLastCustomerMessage() == null
                || !lease.anchorMessageId().equals(session.getLastCustomerMessage().getIdMessage())) {
            throw new StaleAgentTurnException();
        }
        String summary = clean(turn.conversationSummary());
        if (!summary.isBlank()) session.setSummary(truncate(summary, 4000));
        session.setLastActivityAt(LocalDateTime.now());
        boolean escalated = turn.requiresHumanHelp()
                && runtimeConfig.forSession(lease.sessionId()).advisorAttentionEnabled();
        if (escalated) {
            escalate(session, lease.jobId(), "La IA determino que el caso requiere revision humana");
        } else {
            markAgentActive(session);
        }
        sessions.save(session);
        int ordinal = enqueueWelcomeForNewConversation(session, lease, 0);
        ordinal = enqueueValidatedMedia(lease, turn, ordinal);
        outbox.enqueueText(lease.sessionId(), lease.jobId(), lease.anchorMessageId(), ordinal,
                turn.customerMessage().trim());
    }

    private int enqueueWelcomeForNewConversation(CrmWhatsappAgentSession session, JobLease lease, int ordinal) {
        if (session.getConversation() == null || session.getConversation().getIdConversation() == null) {
            return ordinal;
        }
        Long previousMessages = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM crm_whatsapp_message
                WHERE id_conversation=? AND id_message < ?
                """, Long.class, session.getConversation().getIdConversation(), lease.anchorMessageId());
        if (previousMessages != null && previousMessages > 0) return ordinal;
        String welcome = clean(runtimeConfig.forSession(lease.sessionId()).welcomeMessage());
        if (welcome.isBlank()) return ordinal;
        outbox.enqueueOperationalText(lease.sessionId(), lease.jobId(), lease.anchorMessageId(), ordinal, welcome);
        return ordinal + 1;
    }

    private int enqueueValidatedMedia(JobLease lease, GeneratedTurn turn, int ordinal) {
        if (turn.media() == null) return ordinal;
        Set<String> queuedSources = new HashSet<>();
        for (AgentMediaRequest request : turn.media()) {
            if (request == null || request.type() == null || request.productId() == null) continue;
            String source;
            if (request.type() == AgentMediaRequest.MediaType.SIZE_GUIDE) {
                var product = catalog.getProduct(lease.sessionId(), request.productId());
                source = product.sizeGuideUrl();
            } else {
                var images = catalog.getProductImage(lease.sessionId(), request.productId(), request.color());
                source = images.colorImages().isEmpty() ? images.globalImageUrl() : images.colorImages().getFirst().url();
            }
            if (source == null || source.isBlank()) continue;
            if (!queuedSources.add(source.trim())) continue;
            outbox.enqueueImage(lease.sessionId(), lease.jobId(), lease.anchorMessageId(), ordinal++, source,
                    fileName(source, request.type()), mimeType(source), null);
        }
        return ordinal;
    }

    private String fileName(String source, AgentMediaRequest.MediaType type) {
        String clean = source == null ? "" : source.replace('\\', '/');
        int query = clean.indexOf('?');
        if (query >= 0) clean = clean.substring(0, query);
        String name = clean.substring(clean.lastIndexOf('/') + 1);
        return name.isBlank() ? (type == AgentMediaRequest.MediaType.SIZE_GUIDE ? "guia-tallas.jpg" : "producto.jpg") : name;
    }

    private String mimeType(String source) {
        String value = source == null ? "" : source.toLowerCase();
        if (value.contains(".png")) return "image/png";
        if (value.contains(".webp")) return "image/webp";
        return "image/jpeg";
    }

    @Transactional
    public void failPermanently(JobLease lease, String reason) {
        CrmWhatsappAgentSession session = sessions.findForUpdate(lease.sessionId()).orElse(null);
        if (session == null || !Boolean.TRUE.equals(session.getActiveSlot())) return;
        if (runtimeConfig.forSession(lease.sessionId()).advisorAttentionEnabled()) {
            escalate(session, lease.jobId(), truncate(clean(reason), 160));
        } else {
            markAgentActive(session);
            // El job ya quedo FAILED. La notificacion de recuperacion no debe
            // depender de que ese job llegue a COMPLETED, pues bloquearia el
            // orden completo del outbox de la conversacion.
            int ordinal = Math.floorMod(Objects.hash("AGENT_FAILURE", lease.jobId()), Integer.MAX_VALUE);
            outbox.enqueueText(lease.sessionId(), null, lease.anchorMessageId(), ordinal,
                    "Tuve un inconveniente al procesar tu ultimo mensaje. "
                    + "Por favor escribelo nuevamente y continuare desde el punto actual.");
        }
        sessions.save(session);
    }

    private void escalate(CrmWhatsappAgentSession session, Long jobId, String reason) {
        if (stateMachine.canTransition(session.getState(), CrmWhatsappAgentEvent.AYUDA_REQUERIDA)) {
            session.setResumeState(session.getState());
            session.setState(stateMachine.transition(session.getState(), CrmWhatsappAgentEvent.AYUDA_REQUERIDA));
            session.setLastEvent(CrmWhatsappAgentEvent.AYUDA_REQUERIDA);
            session.setStateChangedAt(LocalDateTime.now());
        }
        Long conversationId = session.getConversation().getIdConversation();
        jdbc.update("""
                INSERT IGNORE INTO crm_whatsapp_support_case
                  (id_agent_session,id_conversation,idempotency_key,category,reason,summary,priority,status,
                   opened_at,created_at,updated_at)
                VALUES (?,?,?,'AGENT_EXECUTION',?,?, 'HIGH','OPEN',?,?,?)
                """, session.getIdAgentSession(), conversationId, "agent-job-help-" + jobId,
                truncate(reason, 160), truncate(session.getSummary(), 4000),
                LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now());
        jdbc.update("""
                UPDATE crm_whatsapp_conversation
                SET status='ESPERA', attention_queue='ADVISOR_REQUIRED', waiting_reason='ADVISOR_REQUIRED',
                    waiting_detail=?, assigned_user_id=NULL, assigned_at=NULL, updated_at=?
                WHERE id_conversation=?
                """, truncate(reason, 500), LocalDateTime.now(), conversationId);
    }

    private void markAgentActive(CrmWhatsappAgentSession session) {
        if (session.getConversation() == null || session.getConversation().getIdConversation() == null) return;
        jdbc.update("""
                UPDATE crm_whatsapp_conversation
                SET status='ESPERA', attention_queue='AGENT_ACTIVE', waiting_reason=NULL,
                    waiting_detail=NULL, assigned_user_id=NULL, assigned_at=NULL, updated_at=?
                WHERE id_conversation=?
                """, LocalDateTime.now(), session.getConversation().getIdConversation());
    }

    private String clean(String value) { return value == null ? "" : value.trim(); }
    private String truncate(String value, int max) {
        String safe = clean(value); return safe.length() <= max ? safe : safe.substring(0, max);
    }

    public static class StaleAgentTurnException extends RuntimeException {
        public StaleAgentTurnException() { super("La respuesta quedo obsoleta por un mensaje mas reciente"); }
    }
}
