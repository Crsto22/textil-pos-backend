package com.sistemapos.sistematextil.services.agent.film;

import java.time.LocalDateTime;

import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.sistemapos.sistematextil.model.*;
import com.sistemapos.sistematextil.repositories.*;
import com.sistemapos.sistematextil.services.agent.CrmWhatsappAgentStateMachine;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component @RequiredArgsConstructor @Slf4j
public class AgentFilmExpiryRunner {
    private final CrmWhatsappFilmAddonRepository addons;
    private final CrmWhatsappFilmEvidenceRepository evidence;
    private final CrmWhatsappAgentSessionRepository sessions;
    private final CrmWhatsappAgentStateMachine stateMachine;
    private final AgentFilmEscalationService escalation;
    private final TransactionTemplate transactions;

    @Scheduled(initialDelayString="${crm.whatsapp.agent.film-expiry.initial-delay-ms:5000}",fixedDelayString="${crm.whatsapp.agent.film-expiry.delay-ms:5000}")
    public void expire(){for(Long id:addons.findExpiredIds(CrmWhatsappFilmStatus.AWAITING_PAYMENT,LocalDateTime.now(),PageRequest.of(0,20))){try{expireOne(id);}catch(RuntimeException e){log.warn("No se pudo vencer el film {}: {}",id,e.getMessage());}}}

    public void expireOne(Long id){transactions.executeWithoutResult(status -> expireOneInTransaction(id));}

    private void expireOneInTransaction(Long id){CrmWhatsappFilmAddon addon=addons.findForUpdate(id).orElse(null);if(addon==null||addon.getStatus()!=CrmWhatsappFilmStatus.AWAITING_PAYMENT||addon.getExpiresAt()==null||addon.getExpiresAt().isAfter(LocalDateTime.now()))return;
        var last=evidence.findFirstByFilmAddon_IdFilmAddonOrderByCreatedAtDesc(id).orElse(null);
        if(last!=null&&last.getMessage().getCreatedAt()!=null&&!last.getMessage().getCreatedAt().isAfter(addon.getExpiresAt())
                &&(last.getProcessingStatus()==CrmWhatsappPaymentProcessingStatus.QUEUED||last.getProcessingStatus()==CrmWhatsappPaymentProcessingStatus.PROCESSING))return;
        addon.setStatus(CrmWhatsappFilmStatus.PAYMENT_OVERDUE);addon.setResolvedAt(LocalDateTime.now());
        addon.getFulfillment().setStatus(CrmWhatsappFulfillmentStatus.FILM_PAYMENT_OVERDUE);addons.save(addon);
        CrmWhatsappAgentSession s=sessions.findForUpdate(addon.getAgentSession().getIdAgentSession()).orElseThrow();
        if(s.getState()==CrmWhatsappAgentState.ESPERANDO_PAGO_FILM){s.setState(stateMachine.transition(s.getState(),CrmWhatsappAgentEvent.PAGO_FILM_EXPIRADO));s.setLastEvent(CrmWhatsappAgentEvent.PAGO_FILM_EXPIRADO);s.setStateChangedAt(LocalDateTime.now());sessions.save(s);}
        escalation.escalate(s.getIdAgentSession(),id,"Venció el plazo de 1 hora para pagar el film obligatorio");}
}
