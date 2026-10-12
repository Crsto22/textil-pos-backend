package com.sistemapos.sistematextil.services.agent.batch;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.sistemapos.sistematextil.model.CrmWhatsappAgentSession;
import com.sistemapos.sistematextil.model.CrmWhatsappAgentState;
import com.sistemapos.sistematextil.model.CrmWhatsappConversation;
import com.sistemapos.sistematextil.model.CrmWhatsappMessage;
import com.sistemapos.sistematextil.model.CrmWhatsappFilmStatus;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAgentSessionRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappConversationRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappFilmAddonRepository;

@Service
public class AgentSessionResolver {
    private final CrmWhatsappConversationRepository conversationRepository;
    private final CrmWhatsappAgentSessionRepository sessionRepository;
    private final CrmWhatsappFilmAddonRepository filmAddons;

    public AgentSessionResolver(
            CrmWhatsappConversationRepository conversationRepository,
            CrmWhatsappAgentSessionRepository sessionRepository,
            CrmWhatsappFilmAddonRepository filmAddons) {
        this.conversationRepository = conversationRepository;
        this.sessionRepository = sessionRepository;
        this.filmAddons = filmAddons;
    }

    public Optional<CrmWhatsappAgentSession> resolveForIncoming(CrmWhatsappMessage message) {
        if (message == null || message.getConversation() == null
                || message.getConversation().getIdConversation() == null) {
            return Optional.empty();
        }
        CrmWhatsappConversation conversation = conversationRepository
                .findForUpdateById(message.getConversation().getIdConversation())
                .orElse(null);
        if (conversation == null || conversation.getConnection() == null) return Optional.empty();

        CrmWhatsappAgentSession session = sessionRepository
                .findByConversation_IdConversationAndActiveSlotTrue(conversation.getIdConversation())
                .orElseGet(() -> newSession(conversation));
        if (session.getState() == CrmWhatsappAgentState.VENTA_PENDIENTE_REVISION
                && !hasPendingFilmPayment(session)) {
            // La venta anterior permanece vinculada a su sesion historica para la
            // revision humana. El siguiente contacto inicia un carrito independiente.
            session.setActiveSlot(null);
            sessionRepository.saveAndFlush(session);
            session = newSession(conversation);
        }
        session.setLastCustomerMessage(message);
        LocalDateTime activityAt = message.getCreatedAt() == null ? LocalDateTime.now() : message.getCreatedAt();
        if (session.getLastActivityAt() == null || activityAt.isAfter(session.getLastActivityAt())) {
            session.setLastActivityAt(activityAt);
        }
        return Optional.of(sessionRepository.saveAndFlush(session));
    }

    private boolean hasPendingFilmPayment(CrmWhatsappAgentSession session) {
        if (filmAddons == null || session == null || session.getIdAgentSession() == null) return false;
        return filmAddons.findByAgentSession_IdAgentSession(session.getIdAgentSession())
                .map(addon -> addon.getStatus() == CrmWhatsappFilmStatus.AWAITING_PAYMENT)
                .orElse(false);
    }

    private CrmWhatsappAgentSession newSession(CrmWhatsappConversation conversation) {
        CrmWhatsappAgentSession session = new CrmWhatsappAgentSession();
        session.setConversation(conversation);
        session.setConnection(conversation.getConnection());
        session.setCliente(conversation.getCliente());
        session.setSessionKey(UUID.randomUUID().toString());
        session.setState(CrmWhatsappAgentState.IDLE);
        session.setActiveSlot(Boolean.TRUE);
        return session;
    }
}
