package com.sistemapos.sistematextil.services.agent.outbox;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class AgentWhatsappOutboxRunnerTest {
    @Test
    void finalizesAClaimedMessageOnlyOnce() {
        AgentOutboxStore store = mock(AgentOutboxStore.class);
        AgentWhatsappOutboxDeliveryService delivery = mock(AgentWhatsappOutboxDeliveryService.class);
        AgentOutboxFinalizer finalizer = mock(AgentOutboxFinalizer.class);
        AgentOutboxStore.OutboxEntry entry = entry();
        AgentOutboxStore.OutboxLease lease = new AgentOutboxStore.OutboxLease(entry, 1, "worker", LocalDateTime.now(), "51999@s.whatsapp.net");
        when(store.recoverExpired(any(), eq(5))).thenReturn(new AgentOutboxStore.RecoveryResult(0, 0));
        when(store.claimNext(anyString(), any(), any())).thenReturn(Optional.of(lease), Optional.empty());
        var receipt = new AgentWhatsappOutboxDeliveryService.DeliveryReceipt("wamid", "{}", "{}");
        when(delivery.send(lease)).thenReturn(receipt);

        new AgentWhatsappOutboxRunner(store, delivery, finalizer, 5, 90, 20).deliverReady();

        verify(delivery).send(lease);
        verify(finalizer).complete(eq(lease), eq(receipt), any());
    }

    @Test
    void retriesATemporaryBridgeFailure() {
        AgentOutboxStore store = mock(AgentOutboxStore.class);
        AgentWhatsappOutboxDeliveryService delivery = mock(AgentWhatsappOutboxDeliveryService.class);
        AgentOutboxFinalizer finalizer = mock(AgentOutboxFinalizer.class);
        AgentOutboxStore.OutboxLease lease = new AgentOutboxStore.OutboxLease(entry(), 2, "worker", LocalDateTime.now(), "51999@s.whatsapp.net");
        when(store.recoverExpired(any(), eq(5))).thenReturn(new AgentOutboxStore.RecoveryResult(0, 0));
        when(store.claimNext(anyString(), any(), any())).thenReturn(Optional.of(lease), Optional.empty());
        when(delivery.send(lease)).thenThrow(new AgentWhatsappOutboxDeliveryService.DeliveryException("timeout", true));
        when(store.failOrRetry(eq(8L), anyString(), any(), any(), eq(5), eq("timeout"), eq(true)))
                .thenReturn(AgentOutboxStore.FailureResult.RETRY_SCHEDULED);

        new AgentWhatsappOutboxRunner(store, delivery, finalizer, 5, 90, 20).deliverReady();

        verify(store).failOrRetry(eq(8L), anyString(), any(), any(), eq(5), eq("timeout"), eq(true));
    }

    private AgentOutboxStore.OutboxEntry entry() {
        return new AgentOutboxStore.OutboxEntry(8L, 2L, 3L, 4L, null, "key", 1,
                "TEXT", "Hola", null, null, null, null, "PROCESSING");
    }
}
