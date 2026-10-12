package com.sistemapos.sistematextil.services;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import lombok.RequiredArgsConstructor;
import com.sistemapos.sistematextil.services.agent.film.AgentFilmEvidenceService;

@Component
@RequiredArgsConstructor
public class CrmWhatsappIncomingMediaListener {
    private final CrmWhatsappPaymentEvidenceService paymentEvidenceService;
    private final AgentFilmEvidenceService filmEvidenceService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIncomingMedia(CrmWhatsappIncomingMediaEvent event) {
        if (!filmEvidenceService.registerIncomingMedia(event.messageId())) {
            paymentEvidenceService.registerIncomingMedia(event.messageId());
        }
    }
}
