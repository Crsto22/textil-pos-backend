package com.sistemapos.sistematextil.services.agent.runtime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.sistemapos.sistematextil.services.agent.batch.AgentJobCoordinator;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class AgentConversationRunner {
    private final AgentJobCoordinator coordinator;
    private final AgentConversationOrchestrator orchestrator;
    private final int batchSize;

    public AgentConversationRunner(AgentJobCoordinator coordinator, AgentConversationOrchestrator orchestrator,
            @Value("${crm.whatsapp.agent.runner.batch-size:5}") int batchSize) {
        this.coordinator = coordinator;
        this.orchestrator = orchestrator;
        this.batchSize = Math.max(1, Math.min(batchSize, 20));
    }

    @Scheduled(initialDelayString = "${crm.whatsapp.agent.runner.initial-delay-ms:5000}",
            fixedDelayString = "${crm.whatsapp.agent.runner.delay-ms:750}")
    public void processReadyJobs() {
        for (int processed = 0; processed < batchSize; processed++) {
            var lease = coordinator.claimNext();
            if (lease.isEmpty()) return;
            try {
                var result = orchestrator.process(lease.get());
                if (result == AgentConversationOrchestrator.Outcome.FAILED) {
                    log.error("Job del agente {} fallo permanentemente", lease.get().jobId());
                } else if (result == AgentConversationOrchestrator.Outcome.RETRY) {
                    log.warn("Job del agente {} programado para reintento", lease.get().jobId());
                }
            } catch (RuntimeException unexpected) {
                // Aisla un job defectuoso para que los demas chats del lote sigan
                // procesandose y el scheduler permanezca operativo.
                log.error("Error inesperado aislado al procesar el job {}",
                        lease.get().jobId(), unexpected);
            }
        }
    }
}
