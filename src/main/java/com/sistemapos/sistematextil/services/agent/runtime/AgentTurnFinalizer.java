package com.sistemapos.sistematextil.services.agent.runtime;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sistemapos.sistematextil.model.CrmWhatsappAgentEvent;
import com.sistemapos.sistematextil.model.CrmWhatsappAgentSession;
import com.sistemapos.sistematextil.model.CrmWhatsappAgentState;
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
    public static final String ADVISOR_CONTACT = "Para recibir ayuda con tu caso, comunícate con nuestra asesora al 933918047.";
    public static final String PROVIDER_MAINTENANCE_MESSAGE = "Disculpa, bella 💛 Nuestro asistente virtual se encuentra "
            + "temporalmente en mantenimiento. Para continuar con tu compra y recibir atención, comunícate con "
            + "nuestra asesora al 933918047. Gracias por tu comprensión.";
    private static final long PROVIDER_MAINTENANCE_NOTICE_MINUTES = 30L;
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
        // La configuracion es la autoridad final: el modelo nunca puede pausar por
        // si solo una conversacion cuando la atencion de asesoras esta deshabilitada.
        boolean escalated = turn.requiresHumanHelp() && advisorAttentionEnabled(lease.sessionId());
        if (escalated) {
            escalate(session, lease.jobId(), "La IA determino que el caso requiere revision humana");
        } else {
            resumeAutomaticAttention(session);
            markAgentActive(session);
        }
        sessions.save(session);
        int ordinal = enqueueWelcomeForNewConversation(session, lease, 0);
        ordinal = enqueueValidatedMedia(session, lease, turn, ordinal);
        String customerMessage=turn.customerMessage().trim();
        if(escalated&&!customerMessage.contains("933918047"))customerMessage=customerMessage+"\n\n"+ADVISOR_CONTACT;
        outbox.enqueueText(lease.sessionId(), lease.jobId(), lease.anchorMessageId(), ordinal,customerMessage);
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

    private int enqueueValidatedMedia(CrmWhatsappAgentSession session, JobLease lease, GeneratedTurn turn, int ordinal) {
        if (turn.media() == null) return ordinal;
        Set<String> queuedSources = new HashSet<>();
        boolean catalogPreviewAlreadySent = session.getCatalogPreviewSentAt() != null;
        boolean catalogPreviewQueued = false;
        for (AgentMediaRequest request : turn.media()) {
            if (request == null || request.type() == null || request.productId() == null) continue;
            if (request.catalogPreview() && catalogPreviewAlreadySent) continue;
            String source;
            String caption = null;
            if (request.type() == AgentMediaRequest.MediaType.SIZE_GUIDE) {
                var product = catalog.getProduct(lease.sessionId(), request.productId());
                source = product.sizeGuideUrl();
                caption = "Guía de tallas — " + product.name();
            } else {
                var images = catalog.getProductImage(lease.sessionId(), request.productId(), request.color());
                if (request.catalogPreview()) {
                    source = images.globalImageUrl();
                } else {
                    boolean exactColorRequested = request.color() != null && !request.color().isBlank();
                    if (exactColorRequested && images.colorImages().isEmpty()) continue;
                    source = images.colorImages().isEmpty()
                            ? images.globalImageUrl() : images.colorImages().getFirst().url();
                }
                caption = productImageCaption(images.productName(), request.color());
            }
            if (source == null || source.isBlank()) continue;
            if (!queuedSources.add(source.trim())) continue;
            if (request.suppressIfRecentlySent() && wasRecentlySent(session, source)) continue;
            outbox.enqueueImage(lease.sessionId(), lease.jobId(), lease.anchorMessageId(), ordinal++, source,
                    fileName(source, request.type()), mimeType(source), caption);
            if (request.catalogPreview()) catalogPreviewQueued = true;
        }
        if (catalogPreviewQueued) {
            session.setCatalogPreviewSentAt(LocalDateTime.now());
            sessions.save(session);
        }
        return ordinal;
    }

    private boolean wasRecentlySent(CrmWhatsappAgentSession session, String source) {
        if (session.getConversation() == null || session.getConversation().getIdConversation() == null
                || source == null || source.isBlank()) return false;
        Long matches = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM (
                    SELECT media_storage_path
                    FROM crm_whatsapp_message
                    WHERE id_conversation=? AND direction='OUTGOING' AND deleted_at IS NULL
                      AND media_storage_path IS NOT NULL
                    ORDER BY id_message DESC
                    LIMIT 20
                ) recent_media
                WHERE media_storage_path=?
                """, Long.class, session.getConversation().getIdConversation(), source.trim());
        return matches != null && matches > 0;
    }

    private String productImageCaption(String productName, String color) {
        String product = clean(productName);
        String selectedColor = clean(color);
        if (product.isBlank()) return selectedColor.isBlank() ? null : selectedColor;
        return selectedColor.isBlank() ? product : product + " — " + selectedColor;
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
        if (providerCreditsDepleted(reason)) {
            // Un problema de facturación del proveedor no debe alterar el flujo
            // comercial, crear un caso humano ni borrar carrito o reservas.
            resumeAutomaticAttention(session);
            markAgentActive(session);
            if (!maintenanceNoticeRecentlyQueued(session)) {
                int ordinal = Math.floorMod(Objects.hash("AGENT_PROVIDER_MAINTENANCE", lease.jobId()),
                        Integer.MAX_VALUE);
                outbox.enqueueText(lease.sessionId(), null, lease.anchorMessageId(), ordinal,
                        PROVIDER_MAINTENANCE_MESSAGE);
            }
        } else if (advisorAttentionEnabled(lease.sessionId())) {
            escalate(session, lease.jobId(), truncate(clean(reason), 160));
            int ordinal=Math.floorMod(Objects.hash("AGENT_FAILURE_HELP",lease.jobId()),Integer.MAX_VALUE);
            outbox.enqueueText(lease.sessionId(),null,lease.anchorMessageId(),ordinal,ADVISOR_CONTACT);
        } else {
            markAgentActive(session);
            // El job ya quedo FAILED. La notificacion de recuperacion no debe
            // depender de que ese job llegue a COMPLETED, pues bloquearia el
            // orden completo del outbox de la conversacion.
            int ordinal = Math.floorMod(Objects.hash("AGENT_FAILURE", lease.jobId()), Integer.MAX_VALUE);
            outbox.enqueueText(lease.sessionId(), null, lease.anchorMessageId(), ordinal,
                    recoverableCustomerMessage(reason));
        }
        sessions.save(session);
    }

    private boolean providerCreditsDepleted(String reason) {
        String normalized = clean(reason).toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("http 402")
                || normalized.contains("credits are depleted")
                || normalized.contains("prepayment credits")
                || normalized.contains("saldo prepago agotado");
    }

    private boolean maintenanceNoticeRecentlyQueued(CrmWhatsappAgentSession session) {
        if (session.getConversation() == null || session.getConversation().getIdConversation() == null) return false;
        Long conversationId = session.getConversation().getIdConversation();
        LocalDateTime since = LocalDateTime.now().minus(PROVIDER_MAINTENANCE_NOTICE_MINUTES, ChronoUnit.MINUTES);
        Long matches = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM (
                    SELECT id_agent_outbox AS id
                    FROM crm_whatsapp_agent_outbox
                    WHERE id_conversation=? AND body=? AND created_at>=?
                      AND status IN ('PENDING','PROCESSING','RETRY','SENT')
                    UNION ALL
                    SELECT id_message AS id
                    FROM crm_whatsapp_message
                    WHERE id_conversation=? AND direction='OUTGOING' AND deleted_at IS NULL
                      AND body=? AND created_at>=?
                ) maintenance_notices
                """, Long.class,
                conversationId, PROVIDER_MAINTENANCE_MESSAGE, since,
                conversationId, PROVIDER_MAINTENANCE_MESSAGE, since);
        return matches != null && matches > 0;
    }

    private String recoverableCustomerMessage(String reason) {
        String normalized = clean(reason).toLowerCase(java.util.Locale.ROOT);
        if (normalized.contains("stock insuficiente") || normalized.contains("ya no esta disponible")) {
            return "El producto seleccionado ya no cuenta con stock suficiente, bella. "
                    + "Puedes elegir otro color, talla o modelo y continuaré con tu pedido.";
        }
        if (normalized.contains("carrito no puede modificarse")
                || normalized.contains("carrito anterior no puede reutilizarse")) {
            return "Tu compra anterior ya finalizó. Envíame nuevamente el producto, color y talla "
                    + "que deseas para iniciar un pedido nuevo, bella.";
        }
        return "No pude completar esta acción, bella. Tu mensaje sí fue recibido; "
                + "puedes intentarlo nuevamente o elegir otro producto.";
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

    private void resumeAutomaticAttention(CrmWhatsappAgentSession session) {
        if (session.getState() != CrmWhatsappAgentState.REQUIERE_AYUDA) return;
        CrmWhatsappAgentState resume = session.getResumeState();
        if (resume == null || resume == CrmWhatsappAgentState.REQUIERE_AYUDA) {
            resume = CrmWhatsappAgentState.IDLE;
        }
        session.setState(resume);
        session.setResumeState(null);
        session.setLastEvent(CrmWhatsappAgentEvent.AYUDA_RESUELTA);
        session.setStateChangedAt(LocalDateTime.now());
    }

    private boolean advisorAttentionEnabled(Long sessionId) {
        AgentRuntimeConfigService.RuntimeConfig config = runtimeConfig.forSession(sessionId);
        return config != null && config.advisorAttentionEnabled();
    }

    private String clean(String value) { return value == null ? "" : value.trim(); }
    private String truncate(String value, int max) {
        String safe = clean(value); return safe.length() <= max ? safe : safe.substring(0, max);
    }

    public static class StaleAgentTurnException extends RuntimeException {
        public StaleAgentTurnException() { super("La respuesta quedo obsoleta por un mensaje mas reciente"); }
    }
}
