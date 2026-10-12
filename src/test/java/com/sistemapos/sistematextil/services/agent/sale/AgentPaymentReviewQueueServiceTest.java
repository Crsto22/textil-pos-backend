package com.sistemapos.sistematextil.services.agent.sale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import com.sistemapos.sistematextil.model.CrmWhatsappConversation;
import com.sistemapos.sistematextil.model.CrmWhatsappAttentionQueue;
import com.sistemapos.sistematextil.repositories.CrmWhatsappConversationRepository;
import com.sistemapos.sistematextil.services.CrmWhatsappChatService;
import com.sistemapos.sistematextil.services.CrmWhatsappEventService;

class AgentPaymentReviewQueueServiceTest {
    @Test
    void keepsAutomaticSaleInAgentQueueForReviewInSalesModule() {
        CrmWhatsappConversationRepository conversations = mock(CrmWhatsappConversationRepository.class);
        CrmWhatsappEventService events = mock(CrmWhatsappEventService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<CrmWhatsappChatService> provider = mock(ObjectProvider.class);
        CrmWhatsappChatService chats = mock(CrmWhatsappChatService.class);
        CrmWhatsappConversation conversation = new CrmWhatsappConversation();
        conversation.setIdConversation(12L);
        when(conversations.save(conversation)).thenReturn(conversation);
        when(provider.getObject()).thenReturn(chats);

        AgentPaymentReviewQueueService service = new AgentPaymentReviewQueueService(
                conversations, events, provider);
        service.enqueue(conversation, 501);

        assertEquals("ESPERA", conversation.getStatus());
        assertEquals(CrmWhatsappAttentionQueue.AGENT_ACTIVE, conversation.getAttentionQueue());
        assertNull(conversation.getWaitingReason());
        assertNull(conversation.getWaitingDetail());
        verify(conversations).save(conversation);
        verify(events).publishAfterCommit(anyEvent(), anyEvent(),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.eq(true));
    }

    private CrmWhatsappChatService.CrmRealtimeEvent anyEvent() {
        return org.mockito.ArgumentMatchers.any(CrmWhatsappChatService.CrmRealtimeEvent.class);
    }
}
