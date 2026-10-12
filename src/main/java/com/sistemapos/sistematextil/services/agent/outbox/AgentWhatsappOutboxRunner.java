package com.sistemapos.sistematextil.services.agent.outbox;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import com.sistemapos.sistematextil.services.agent.config.AgentRuntimeConfigService;

@Component
@Slf4j
public class AgentWhatsappOutboxRunner {
    private final AgentOutboxStore store;
    private final AgentWhatsappOutboxDeliveryService delivery;
    private final AgentOutboxFinalizer finalizer;
    private final String workerId = "agent-outbox-" + UUID.randomUUID();
    private final int maxAttempts;
    private final int leaseSeconds;
    private final int batchSize;
    private final AgentRuntimeConfigService runtimeConfig;

    @Autowired
    public AgentWhatsappOutboxRunner(AgentOutboxStore store,
            AgentWhatsappOutboxDeliveryService delivery, AgentOutboxFinalizer finalizer,
            AgentRuntimeConfigService runtimeConfig,
            @Value("${crm.whatsapp.agent.outbox.max-attempts:5}") int maxAttempts,
            @Value("${crm.whatsapp.agent.outbox.lease-seconds:90}") int leaseSeconds,
            @Value("${crm.whatsapp.agent.outbox.batch-size:20}") int batchSize) {
        this.store = store;
        this.delivery = delivery;
        this.finalizer = finalizer;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.leaseSeconds = Math.max(15, leaseSeconds);
        this.batchSize = Math.max(1, Math.min(100, batchSize));
        this.runtimeConfig = runtimeConfig;
    }

    AgentWhatsappOutboxRunner(AgentOutboxStore store,
            AgentWhatsappOutboxDeliveryService delivery, AgentOutboxFinalizer finalizer,
            int maxAttempts, int leaseSeconds, int batchSize) {
        this.store = store;
        this.delivery = delivery;
        this.finalizer = finalizer;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.leaseSeconds = Math.max(15, leaseSeconds);
        this.batchSize = Math.max(1, Math.min(100, batchSize));
        this.runtimeConfig = null;
    }

    @Scheduled(initialDelayString = "${crm.whatsapp.agent.outbox.initial-delay-ms:3000}",
            fixedDelayString = "${crm.whatsapp.agent.outbox.delay-ms:500}")
    public void deliverReady() {
        recoverExpired();
        for (int i = 0; i < batchSize; i++) {
            LocalDateTime now = LocalDateTime.now();
            var lease = store.claimNext(workerId, now, now.plusSeconds(leaseSeconds));
            if (lease.isEmpty()) return;
            deliver(lease.get());
        }
    }

    private void deliver(AgentOutboxStore.OutboxLease lease) {
        try {
            var receipt = delivery.send(lease);
            finalizer.complete(lease, receipt, LocalDateTime.now());
        } catch (AgentWhatsappOutboxDeliveryService.DeliveryException error) {
            fail(lease, error, error.retryable());
        } catch (RuntimeException error) {
            fail(lease, error, true);
        }
    }

    private void fail(AgentOutboxStore.OutboxLease lease, RuntimeException error, boolean retryable) {
        LocalDateTime now = LocalDateTime.now();
        long delay = Math.min(60, 1L << Math.min(lease.attempt(), 6));
        int effectiveMax = runtimeConfig == null ? maxAttempts
                : runtimeConfig.forSession(lease.entry().sessionId()).outboxMaxAttempts();
        var outcome = store.failOrRetry(lease.entry().outboxId(), workerId, now, now.plusSeconds(delay),
                effectiveMax, error.getMessage(), retryable);
        log.warn("Entrega WhatsApp {} no completada ({}): {}", lease.entry().outboxId(), outcome,
                error.getMessage());
    }

    private void recoverExpired() {
        AgentOutboxStore.RecoveryResult result = store.recoverExpired(LocalDateTime.now(), maxAttempts);
        if (result.recovered() > 0 || result.failedPermanently() > 0) {
            log.info("Outbox recuperado: {} reintentos, {} fallidos", result.recovered(), result.failedPermanently());
        }
    }
}
