package com.sistemapos.sistematextil.services;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.sistemapos.sistematextil.events.VentaRegistradaEvent;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class VentaSunatListener {

    private final VentaService ventaService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onVentaRegistrada(VentaRegistradaEvent event) {
        ventaService.procesarEmisionElectronica(event.idVenta());
    }
}
