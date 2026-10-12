package com.sistemapos.sistematextil.services.agent.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AgentJobRecoveryRunner {
    private static final Logger log = LoggerFactory.getLogger(AgentJobRecoveryRunner.class);

    private final AgentJobCoordinator coordinator;

    public AgentJobRecoveryRunner(AgentJobCoordinator coordinator) {
        this.coordinator = coordinator;
    }

    @Scheduled(
            initialDelayString = "${crm.whatsapp.agent.job.recovery-initial-delay-ms:5000}",
            fixedDelayString = "${crm.whatsapp.agent.job.recovery-delay-ms:10000}")
    public void recoverExpiredJobs() {
        try {
            var result = coordinator.recoverExpired();
            if (result.recovered() > 0 || result.failedPermanently() > 0) {
                log.info("Jobs del agente recuperados={} fallidos={}",
                        result.recovered(), result.failedPermanently());
            }
        } catch (RuntimeException error) {
            log.error("No se pudieron recuperar los jobs vencidos del agente", error);
        }
    }
}
