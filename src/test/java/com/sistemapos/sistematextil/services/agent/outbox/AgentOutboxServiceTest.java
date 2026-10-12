package com.sistemapos.sistematextil.services.agent.outbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AgentOutboxServiceTest {
    @Test
    void queuesTextWithAStableLogicalOrdinal() {
        AgentOutboxStore store = mock(AgentOutboxStore.class);
        AgentOutboxService service = new AgentOutboxService(store);
        var expected = new AgentOutboxStore.OutboxEntry(1L, 2L, 3L, 4L, null, "key", 1,
                "TEXT", "Hola", null, null, null, null, "PENDING");
        when(store.enqueue(org.mockito.ArgumentMatchers.any())).thenReturn(expected);

        assertEquals(expected, service.enqueueText(2L, 3L, null, 0, "Hola"));

        ArgumentCaptor<AgentOutboxStore.EnqueueCommand> command = ArgumentCaptor.forClass(
                AgentOutboxStore.EnqueueCommand.class);
        verify(store).enqueue(command.capture());
        assertEquals(0, command.getValue().ordinal());
        assertEquals("TEXT", command.getValue().messageType());
    }
}
