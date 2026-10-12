package com.sistemapos.sistematextil.services.agent.batch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.sistemapos.sistematextil.model.CrmWhatsappAgentSession;
import com.sistemapos.sistematextil.model.CrmWhatsappAgentState;
import com.sistemapos.sistematextil.model.CrmWhatsappConnection;
import com.sistemapos.sistematextil.model.CrmWhatsappConversation;
import com.sistemapos.sistematextil.model.CrmWhatsappFilmAddon;
import com.sistemapos.sistematextil.model.CrmWhatsappFilmStatus;
import com.sistemapos.sistematextil.model.CrmWhatsappMessage;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAgentSessionRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappConversationRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappFilmAddonRepository;

class AgentSessionResolverTest {

    @Test
    void startsCleanSessionAfterSaleWasCreatedForHumanReview() {
        var conversations = mock(CrmWhatsappConversationRepository.class);
        var sessions = mock(CrmWhatsappAgentSessionRepository.class);
        var conversation = new CrmWhatsappConversation();
        conversation.setIdConversation(81L);
        var connection = new CrmWhatsappConnection();
        connection.setIdConnection(7L);
        conversation.setConnection(connection);

        var previous = new CrmWhatsappAgentSession();
        previous.setIdAgentSession(12L);
        previous.setConversation(conversation);
        previous.setConnection(connection);
        previous.setState(CrmWhatsappAgentState.VENTA_PENDIENTE_REVISION);
        previous.setActiveSlot(true);

        var incoming = new CrmWhatsappMessage();
        incoming.setIdMessage(900L);
        incoming.setConversation(conversation);
        incoming.setCreatedAt(LocalDateTime.now());

        when(conversations.findForUpdateById(81L)).thenReturn(Optional.of(conversation));
        when(sessions.findByConversation_IdConversationAndActiveSlotTrue(81L)).thenReturn(Optional.of(previous));
        when(sessions.saveAndFlush(any(CrmWhatsappAgentSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var resolved = new AgentSessionResolver(conversations, sessions, null).resolveForIncoming(incoming).orElseThrow();

        assertNull(previous.getActiveSlot());
        assertNotSame(previous, resolved);
        assertEquals(CrmWhatsappAgentState.IDLE, resolved.getState());
        assertEquals(incoming, resolved.getLastCustomerMessage());
        assertTrue(Boolean.TRUE.equals(resolved.getActiveSlot()));
    }

    @Test
    void keepsSaleSessionActiveWhileMandatoryFilmPaymentIsPending() {
        var conversations=mock(CrmWhatsappConversationRepository.class);
        var sessions=mock(CrmWhatsappAgentSessionRepository.class);
        var films=mock(CrmWhatsappFilmAddonRepository.class);
        var conversation=new CrmWhatsappConversation();conversation.setIdConversation(81L);
        var connection=new CrmWhatsappConnection();connection.setIdConnection(7L);conversation.setConnection(connection);
        var current=new CrmWhatsappAgentSession();current.setIdAgentSession(12L);current.setConversation(conversation);
        current.setConnection(connection);current.setState(CrmWhatsappAgentState.VENTA_PENDIENTE_REVISION);
        current.setActiveSlot(true);
        var addon=new CrmWhatsappFilmAddon();addon.setStatus(CrmWhatsappFilmStatus.AWAITING_PAYMENT);
        var incoming=new CrmWhatsappMessage();incoming.setIdMessage(900L);incoming.setConversation(conversation);
        incoming.setCreatedAt(LocalDateTime.now());
        when(conversations.findForUpdateById(81L)).thenReturn(Optional.of(conversation));
        when(sessions.findByConversation_IdConversationAndActiveSlotTrue(81L)).thenReturn(Optional.of(current));
        when(films.findByAgentSession_IdAgentSession(12L)).thenReturn(Optional.of(addon));
        when(sessions.saveAndFlush(any(CrmWhatsappAgentSession.class))).thenAnswer(i->i.getArgument(0));

        var resolved=new AgentSessionResolver(conversations,sessions,films).resolveForIncoming(incoming).orElseThrow();

        assertEquals(current,resolved);assertTrue(Boolean.TRUE.equals(current.getActiveSlot()));
        assertEquals(incoming,current.getLastCustomerMessage());
    }
}
