package com.sistemapos.sistematextil.services.agent.payment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class AgentPaymentEvidenceAnalysisRunner {
    private final AgentPaymentEvidenceAnalysisStore store;
    private final AgentPaymentEvidenceAnalysisService analysisService;
    private final int staleMinutes;

    public AgentPaymentEvidenceAnalysisRunner(
            AgentPaymentEvidenceAnalysisStore store,
            AgentPaymentEvidenceAnalysisService analysisService,
            @Value("${crm.whatsapp.agent.payment-vision.stale-minutes:2}") int staleMinutes) {
        this.store = store;
        this.analysisService = analysisService;
        this.staleMinutes = Math.max(1, staleMinutes);
    }

    @Scheduled(
            initialDelayString = "${crm.whatsapp.agent.payment-vision.initial-delay-ms:3000}",
            fixedDelayString = "${crm.whatsapp.agent.payment-vision.delay-ms:1000}")
    public void process() {
        recoverStale();
        for (Long evidenceId : store.findReadyIds(20)) {
            try {
                analysisService.process(evidenceId);
            } catch (RuntimeException error) {
                log.warn("No se pudo analizar el comprobante del agente {}: {}",
                        evidenceId, error.getMessage());
            }
        }
    }

    private void recoverStale() {
        for (Long evidenceId : store.findStaleIds(staleMinutes, 20)) {
            try {
                store.recoverStale(evidenceId);
            } catch (RuntimeException error) {
                log.warn("No se pudo recuperar el comprobante bloqueado {}: {}",
                        evidenceId, error.getMessage());
            }
        }
    }
}
