package com.sistemapos.sistematextil.services;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class CrmWarehouseEventService {
    private static final long TIMEOUT_MS = 30L * 60L * 1000L;
    private final Map<String, SseEmitter> subscribers = new ConcurrentHashMap<>();

    public SseEmitter subscribe() {
        String id = UUID.randomUUID().toString();
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        subscribers.put(id, emitter);
        emitter.onCompletion(() -> subscribers.remove(id));
        emitter.onTimeout(() -> subscribers.remove(id));
        emitter.onError(error -> subscribers.remove(id));
        send(id, emitter, "connected", Map.of("connected", true));
        return emitter;
    }

    public void publishAfterCommit(Object payload) {
        Runnable action = () -> broadcast("warehouse-event", payload);
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { action.run(); }
            });
        } else {
            action.run();
        }
    }

    @EventListener
    public void shippingConversationUpdated(CrmWarehouseConversationEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", event.messageId() == null
                ? "warehouse.message.updated"
                : "warehouse.message.status.updated");
        payload.put("conversationId", event.conversationId());
        if (event.messageId() != null) payload.put("messageId", event.messageId());
        if (event.messageStatus() != null) payload.put("status", event.messageStatus());
        publishAfterCommit(payload);
    }

    @Scheduled(fixedDelay = 20000L)
    public void heartbeat() {
        broadcast("heartbeat", Map.of("timestamp", Instant.now().toString()));
    }

    private void broadcast(String eventName, Object payload) {
        subscribers.forEach((id, emitter) -> send(id, emitter, eventName, payload));
    }

    private void send(String id, SseEmitter emitter, String eventName, Object payload) {
        try {
            synchronized (emitter) {
                emitter.send(SseEmitter.event().name(eventName).data(payload));
            }
        } catch (IOException | IllegalStateException exception) {
            subscribers.remove(id);
        }
    }
}
