package com.sistemapos.sistematextil.services;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.sistemapos.sistematextil.services.CrmWhatsappAiSaleDraftService.CrmWhatsappAiSaleConfirmationRequested;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CrmWhatsappAiSaleConfirmationListener {
    private final CrmWhatsappChatService chatService;
    private final CrmWhatsappAiSaleDraftService draftService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void send(CrmWhatsappAiSaleConfirmationRequested event) {
        var message = chatService.enviarMensaje(event.conversationId(),
                new CrmWhatsappChatService.SendMessageRequest(event.message(), null), event.actor());
        draftService.markConfirmationSent(event.conversationId(), message.id());
    }
}
