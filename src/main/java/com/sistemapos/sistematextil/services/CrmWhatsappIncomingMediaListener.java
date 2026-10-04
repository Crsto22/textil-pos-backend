package com.sistemapos.sistematextil.services;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CrmWhatsappIncomingMediaListener {
    private final CrmWhatsappPaymentEvidenceService paymentEvidenceService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIncomingMedia(CrmWhatsappIncomingMediaEvent event) {
        paymentEvidenceService.registerIncomingMedia(event.messageId());
    }
}
