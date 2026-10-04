package com.sistemapos.sistematextil.services;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class CrmWhatsappPaymentEvidenceRunner {
    private final CrmWhatsappPaymentEvidenceService service;

    @Scheduled(
            initialDelayString = "${crm.whatsapp.payment.worker-initial-delay-ms:60000}",
            fixedDelayString = "${crm.whatsapp.payment.worker-delay-ms:3000}")
    public void process() {
        for (Long messageId : service.unregisteredIncomingMediaIds()) {
            runSafely("registrar mensaje", messageId, () -> service.registerIncomingMedia(messageId));
        }
        for (Long evidenceId : service.readyEvidenceIds()) {
            runSafely("procesar evidencia", evidenceId, () -> service.processOne(evidenceId));
        }
        for (Long evidenceId : service.observedExactAmountEvidenceIds()) {
            runSafely("reconciliar evidencia", evidenceId, () -> service.promoteObservedExactAmount(evidenceId));
        }
    }

    @Scheduled(initialDelay = 60000L, fixedDelay = 60000L)
    public void expire() {
        runSafely("vencer solicitudes", null, service::expireRequests);
    }

    private void runSafely(String action, Long id, Runnable operation) {
        try {
            operation.run();
        } catch (RuntimeException error) {
            log.warn("No se pudo {}{}: {}", action, id == null ? "" : " " + id, error.getMessage());
        }
    }
}
