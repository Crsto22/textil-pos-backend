package com.sistemapos.sistematextil.services.agent.film;

import java.time.LocalDateTime;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sistemapos.sistematextil.model.CrmWhatsappAgentEvent;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAgentSessionRepository;
import com.sistemapos.sistematextil.services.agent.CrmWhatsappAgentStateMachine;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AgentFilmEscalationService {
    private final CrmWhatsappAgentSessionRepository sessions;
    private final CrmWhatsappAgentStateMachine stateMachine;
    private final JdbcTemplate jdbc;

    @Transactional
    public void escalate(Long sessionId, Long addonId, String reason) {
        var session=sessions.findForUpdate(sessionId).orElseThrow();
        if(stateMachine.canTransition(session.getState(),CrmWhatsappAgentEvent.AYUDA_REQUERIDA)){
            session.setResumeState(session.getState());
            session.setState(stateMachine.transition(session.getState(),CrmWhatsappAgentEvent.AYUDA_REQUERIDA));
            session.setLastEvent(CrmWhatsappAgentEvent.AYUDA_REQUERIDA);session.setStateChangedAt(LocalDateTime.now());
            sessions.save(session);
        }
        Long conversationId=session.getConversation().getIdConversation();LocalDateTime now=LocalDateTime.now();
        jdbc.update("""
                INSERT IGNORE INTO crm_whatsapp_support_case
                  (id_agent_session,id_conversation,id_venta,idempotency_key,category,reason,summary,priority,status,opened_at,created_at,updated_at)
                SELECT ?,?,f.id_venta,?,'FILM_PAYMENT',?,?,'HIGH','OPEN',?,?,?
                FROM crm_whatsapp_film_addon f WHERE f.id_film_addon=?
                """,sessionId,conversationId,"film-payment-help-"+addonId,reason,reason,now,now,now,addonId);
        jdbc.update("""
                UPDATE crm_whatsapp_conversation SET status='ESPERA',attention_queue='ADVISOR_REQUIRED',
                  waiting_reason='ADVISOR_REQUIRED',waiting_detail=?,assigned_user_id=NULL,assigned_at=NULL,updated_at=?
                WHERE id_conversation=?
                """,reason,now,conversationId);
    }
}
