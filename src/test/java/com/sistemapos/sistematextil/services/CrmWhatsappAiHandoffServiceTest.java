package com.sistemapos.sistematextil.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.sistemapos.sistematextil.model.CrmWhatsappAiAttentionMode;
import com.sistemapos.sistematextil.model.CrmWhatsappConversation;
import com.sistemapos.sistematextil.model.CrmWhatsappWaitingReason;
import com.sistemapos.sistematextil.repositories.CrmWhatsappConversationRepository;

class CrmWhatsappAiHandoffServiceTest {

    @Test
    void falloTerminalPasaElChatAEsperaDesactivaLaIaYConservaElMotivo() {
        CrmWhatsappConversationRepository conversations = mock(CrmWhatsappConversationRepository.class);
        CrmWhatsappAiMemoryService memory = mock(CrmWhatsappAiMemoryService.class);
        CrmWhatsappEventService events = mock(CrmWhatsappEventService.class);
        CrmWhatsappAiHandoffService service = new CrmWhatsappAiHandoffService(conversations, memory, events);
        CrmWhatsappConversation conversation = new CrmWhatsappConversation();
        conversation.setIdConversation(41L);
        conversation.setStatus("ESPERA");
        conversation.setAiAttentionMode(CrmWhatsappAiAttentionMode.AUTOMATICA);
        when(conversations.findForUpdateById(41L)).thenReturn(Optional.of(conversation));
        when(conversations.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertTrue(service.requireAdvisorForFailure(41L, "WhatsApp no respondio", 77L));

        assertEquals("ESPERA", conversation.getStatus());
        assertEquals(CrmWhatsappAiAttentionMode.HUMANA, conversation.getAiAttentionMode());
        assertTrue(conversation.getAiAttentionModeExplicit());
        assertEquals(CrmWhatsappWaitingReason.AI_RESPONSE_FAILED, conversation.getWaitingReason());
        assertEquals("WhatsApp no respondio", conversation.getWaitingDetail());
        verify(memory).pauseForHuman(conversation, "WhatsApp no respondio");
        verify(events).publishAfterCommit(any(), any(), eq(null), eq(true));
    }
}
