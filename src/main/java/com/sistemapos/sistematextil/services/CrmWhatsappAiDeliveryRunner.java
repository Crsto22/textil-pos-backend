package com.sistemapos.sistematextil.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CrmWhatsappAiDeliveryRunner {
    private static final Logger log = LoggerFactory.getLogger(CrmWhatsappAiDeliveryRunner.class);
    private final CrmWhatsappAiDeliveryService deliveryService;

    @Scheduled(
            fixedDelayString = "${crm.whatsapp.ai.delivery.fixed-delay-ms:1000}",
            initialDelayString = "${crm.whatsapp.ai.delivery.initial-delay-ms:10000}")
    public void process() {
        try {
            for (Long id : deliveryService.readyIds(5)) {
                if (!deliveryService.claim(id)) continue;
                try { deliveryService.send(deliveryService.prepare(id)); }
                catch (RuntimeException error) {
                    log.error("Error enviando entrega IA {}: {}", id, error.getMessage(), error);
                    deliveryService.fail(id, error);
                }
            }
        } catch (RuntimeException error) {
            if (!missingTable(error)) throw error;
        }
    }

    private boolean missingTable(Throwable error) {
        Throwable current = error;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.toLowerCase().contains("crm_whatsapp_ai_delivery")
                    && message.toLowerCase().contains("exist")) return true;
            current = current.getCause();
        }
        return false;
    }
}
