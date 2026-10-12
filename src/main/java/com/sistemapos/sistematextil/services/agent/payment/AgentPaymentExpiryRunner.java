package com.sistemapos.sistematextil.services.agent.payment;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class AgentPaymentExpiryRunner {
    private final AgentPaymentExpiryService expiryService;

    public AgentPaymentExpiryRunner(AgentPaymentExpiryService expiryService) {
        this.expiryService = expiryService;
    }

    @Scheduled(
            initialDelayString = "${crm.whatsapp.agent.payment-expiry-initial-delay-ms:1000}",
            fixedDelayString = "${crm.whatsapp.agent.payment-expiry-delay-ms:1000}")
    public void expireDueRequests() {
        for (Long requestId : expiryService.findDueRequestIds(100)) {
            try {
                expiryService.expire(requestId);
            } catch (RuntimeException error) {
                log.warn("No se pudo vencer la solicitud de pago del agente {}: {}",
                        requestId, error.getMessage());
            }
        }
    }
}
