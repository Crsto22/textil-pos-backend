package com.sistemapos.sistematextil.services;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.sistemapos.sistematextil.events.GuiaRemisionRegistradaEvent;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class GuiaRemisionSunatListener {

    private final SunatJobService sunatJobService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onGuiaRemisionRegistrada(GuiaRemisionRegistradaEvent event) {
        sunatJobService.enqueueGuiaRemision(event.idGuiaRemision());
    }
}
