package com.sistemapos.sistematextil.services;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class CrmWhatsappEventService {

    private static final long EMITTER_TIMEOUT_MS = 30L * 60L * 1000L;

    private final Map<String, Subscriber> subscribers = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Integer userId, boolean admin) {
        String subscriberId = UUID.randomUUID().toString();
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        Subscriber subscriber = new Subscriber(userId, admin, emitter);
        subscribers.put(subscriberId, subscriber);
        emitter.onCompletion(() -> subscribers.remove(subscriberId));
        emitter.onTimeout(() -> subscribers.remove(subscriberId));
        emitter.onError(error -> subscribers.remove(subscriberId));
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data(Map.of("connected", true)));
        } catch (IOException error) {
            subscribers.remove(subscriberId);
        }
        return emitter;
    }

    public void publishAfterCommit(
            Object fullPayload,
            Object waitingPayload,
            Integer assignedUserId,
            boolean unassignedWaiting) {
        Runnable publish = () -> publish(fullPayload, waitingPayload, assignedUserId, unassignedWaiting);
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish.run();
                }
            });
            return;
        }
        publish.run();
    }

    public void publishToUserAfterCommit(Object payload, Integer userId) {
        if (userId == null) {
            return;
        }
        Runnable publish = () -> subscribers.forEach((subscriberId, subscriber) -> {
            if (!userId.equals(subscriber.userId()) || subscriber.admin()) {
                return;
            }
            try {
                synchronized (subscriber.emitter()) {
                    subscriber.emitter().send(SseEmitter.event().name("crm-event").data(payload));
                }
            } catch (IOException | IllegalStateException error) {
                subscribers.remove(subscriberId);
            }
        });
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish.run();
                }
            });
            return;
        }
        publish.run();
    }

    public void publishToUnassignedUsersAfterCommit(Object payload, Integer assignedUserId) {
        Runnable publish = () -> subscribers.forEach((subscriberId, subscriber) -> {
            if (subscriber.admin()
                    || (assignedUserId != null && assignedUserId.equals(subscriber.userId()))) {
                return;
            }
            try {
                synchronized (subscriber.emitter()) {
                    subscriber.emitter().send(SseEmitter.event().name("crm-event").data(payload));
                }
            } catch (IOException | IllegalStateException error) {
                subscribers.remove(subscriberId);
            }
        });
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish.run();
                }
            });
            return;
        }
        publish.run();
    }

    private void publish(
            Object fullPayload,
            Object waitingPayload,
            Integer assignedUserId,
            boolean unassignedWaiting) {
        subscribers.forEach((subscriberId, subscriber) -> {
            Object payload = null;
            if (subscriber.admin() || (assignedUserId != null && assignedUserId.equals(subscriber.userId()))) {
                payload = fullPayload;
            } else if (unassignedWaiting) {
                payload = waitingPayload;
            }
            if (payload == null) {
                return;
            }
            try {
                synchronized (subscriber.emitter()) {
                    subscriber.emitter().send(SseEmitter.event().name("crm-event").data(payload));
                }
            } catch (IOException | IllegalStateException error) {
                subscribers.remove(subscriberId);
            }
        });
    }

    @Scheduled(fixedDelay = 20000L)
    public void heartbeat() {
        subscribers.forEach((subscriberId, subscriber) -> {
            try {
                synchronized (subscriber.emitter()) {
                    subscriber.emitter().send(SseEmitter.event().comment("keepalive"));
                }
            } catch (IOException | IllegalStateException error) {
                subscribers.remove(subscriberId);
            }
        });
    }

    private record Subscriber(Integer userId, boolean admin, SseEmitter emitter) {
    }
}
