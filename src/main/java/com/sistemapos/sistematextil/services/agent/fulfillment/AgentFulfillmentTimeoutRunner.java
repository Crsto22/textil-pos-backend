package com.sistemapos.sistematextil.services.agent.fulfillment;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class AgentFulfillmentTimeoutRunner {
    private final AgentFulfillmentTimeoutService service;

    @Scheduled(
            initialDelayString = "${crm.whatsapp.agent.fulfillment-timeout.initial-delay-ms:10000}",
            fixedDelayString = "${crm.whatsapp.agent.fulfillment-timeout.delay-ms:30000}")
    public void completeTimedOutDeliveries() {
        for (Long sessionId : service.readyIds(20)) {
            try {
                service.completeTimedOutDelivery(sessionId);
            } catch (RuntimeException error) {
                log.warn("No se pudo cerrar la entrega pendiente de la sesión {}: {}",
                        sessionId, error.getMessage());
            }
        }
    }
}
