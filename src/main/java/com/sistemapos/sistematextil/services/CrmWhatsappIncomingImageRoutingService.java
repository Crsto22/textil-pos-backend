package com.sistemapos.sistematextil.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sistemapos.sistematextil.model.CrmWhatsappAiAttentionMode;
import com.sistemapos.sistematextil.model.CrmWhatsappConversation;
import com.sistemapos.sistematextil.model.CrmWhatsappMessage;
import com.sistemapos.sistematextil.model.CrmWhatsappWaitingReason;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAiConfigRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappConversationRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CrmWhatsappIncomingImageRoutingService {

    private final CrmWhatsappAiConfigRepository configRepository;
    private final CrmWhatsappConversationRepository conversationRepository;
    private final CrmWhatsappAiMemoryService memoryService;
    private final CrmWhatsappAiSaleDraftService saleDraftService;

    @Transactional
    public boolean routeIfEnabled(CrmWhatsappConversation conversation, CrmWhatsappMessage message) {
        if (conversation == null || message == null
                || !"INCOMING".equals(message.getDirection())
                || !"IMAGE".equalsIgnoreCase(message.getMessageType())
                || conversation.getAssignedUser() != null
                || conversation.getConnection() == null) {
            return false;
        }
        boolean paymentFlowActive = conversation.getWaitingReason() == CrmWhatsappWaitingReason.PAYMENT_VERIFICATION
                || saleDraftService.hasActivePaymentFlow(conversation.getIdConversation());
        if (paymentFlowActive) return false;

        boolean enabled = configRepository.findByConnection_IdConnection(
                        conversation.getConnection().getIdConnection())
                .map(config -> Boolean.TRUE.equals(config.getTransferirImagenesAsesora()))
                .orElse(false);
        if (!enabled) return false;

        conversation.setAiAttentionMode(CrmWhatsappAiAttentionMode.HUMANA);
        conversation.setAiAttentionModeExplicit(true);
        conversation.setStatus("ESPERA");
        conversation.setWaitingReason(CrmWhatsappWaitingReason.IMAGE_RECEIVED);
        conversationRepository.save(conversation);
        memoryService.registerIncoming(conversation, message);
        memoryService.pauseForHuman(conversation, "Imagen recibida; requiere atencion de una asesora");
        return true;
    }
}
