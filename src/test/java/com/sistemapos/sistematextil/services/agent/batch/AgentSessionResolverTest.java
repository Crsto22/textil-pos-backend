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
import com.sistemapos.sistematextil.model.CrmWhatsappMessage;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAgentSessionRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappConversationRepository;

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

        var resolved = new AgentSessionResolver(conversations, sessions).resolveForIncoming(incoming).orElseThrow();

        assertNull(previous.getActiveSlot());
        assertNotSame(previous, resolved);
        assertEquals(CrmWhatsappAgentState.IDLE, resolved.getState());
        assertEquals(incoming, resolved.getLastCustomerMessage());
        assertTrue(Boolean.TRUE.equals(resolved.getActiveSlot()));
    }
}
