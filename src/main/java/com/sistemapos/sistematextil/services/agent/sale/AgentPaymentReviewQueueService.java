package com.sistemapos.sistematextil.services.agent.sale;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sistemapos.sistematextil.model.CrmWhatsappConversation;
import com.sistemapos.sistematextil.model.CrmWhatsappAttentionQueue;
import com.sistemapos.sistematextil.repositories.CrmWhatsappConversationRepository;
import com.sistemapos.sistematextil.services.CrmWhatsappChatService;
import com.sistemapos.sistematextil.services.CrmWhatsappChatService.CrmRealtimeEvent;
import com.sistemapos.sistematextil.services.CrmWhatsappEventService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AgentPaymentReviewQueueService {
    private final CrmWhatsappConversationRepository conversationRepository;
    private final CrmWhatsappEventService eventService;
    private final ObjectProvider<CrmWhatsappChatService> chatServiceProvider;

    @Transactional
    public void enqueue(CrmWhatsappConversation conversation, Integer saleId) {
        if (conversation == null || conversation.getIdConversation() == null) {
            throw new IllegalStateException("La venta automatica no tiene conversacion relacionada");
        }
        conversation.setStatus("ESPERA");
        conversation.setAttentionQueue(CrmWhatsappAttentionQueue.AGENT_ACTIVE);
        conversation.setWaitingReason(null);
        conversation.setWaitingDetail(null);
        CrmWhatsappConversation saved = conversationRepository.save(conversation);
        var response = chatServiceProvider.getObject().conversationResponse(saved);
        CrmRealtimeEvent event = new CrmRealtimeEvent(
                "conversation.updated", saved.getIdConversation(), response, null);
        Integer assignedUserId = saved.getAssignedUser() == null
                ? null : saved.getAssignedUser().getIdUsuario();
        eventService.publishAfterCommit(event, event, assignedUserId, assignedUserId == null);
    }
}
