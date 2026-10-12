package com.sistemapos.sistematextil.services.agent.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class AgentIncomingMessageListener {
    private static final Logger log = LoggerFactory.getLogger(AgentIncomingMessageListener.class);

    private final AgentMessageBatchScheduler scheduler;

    public AgentIncomingMessageListener(AgentMessageBatchScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIncomingMessage(AgentIncomingMessageStoredEvent event) {
        if (event == null || event.messageId() == null) return;
        try {
            scheduler.schedule(event.messageId());
        } catch (RuntimeException error) {
            log.error("No se pudo agrupar el mensaje entrante {} para el agente", event.messageId(), error);
        }
    }
}
