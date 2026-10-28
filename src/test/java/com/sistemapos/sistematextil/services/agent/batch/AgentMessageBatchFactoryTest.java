package com.sistemapos.sistematextil.services.agent.batch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import com.sistemapos.sistematextil.model.CrmWhatsappMessage;
import com.sistemapos.sistematextil.repositories.CrmWhatsappMessageRepository;

class AgentMessageBatchFactoryTest {
    private static final LocalDateTime BASE = LocalDateTime.of(2026, 10, 8, 13, 0);

    @Test
    void groupsFourConsecutiveMessagesInChronologicalOrderWithMediaSignals() {
        CrmWhatsappMessageRepository repository = mock(CrmWhatsappMessageRepository.class);
        AgentMessageBatchFactory factory = new AgentMessageBatchFactory(repository, 5000, 15);
        CrmWhatsappMessage fourth = message(4L, BASE.plusSeconds(4), "TEXT", "talla L");
        CrmWhatsappMessage third = message(3L, BASE.plusSeconds(3), "IMAGE", "foto del modelo");
        CrmWhatsappMessage second = message(2L, BASE.plusSeconds(2), "AUDIO", "");
        second.setAudioTranscription("quiero el color plata");
        CrmWhatsappMessage first = message(1L, BASE.plusSeconds(1), "TEXT", "Quiero Alice rayas");
        when(repository.findActiveMessagesEndingAt(eq(20L), eq(4L), any(Pageable.class)))
                .thenReturn(List.of(fourth, third, second, first));

        AgentMessageBatch batch = factory.build(10L, 20L, 4L);

        assertEquals(List.of(1L, 2L, 3L, 4L),
                batch.messages().stream().map(AgentMessageBatch.Item::messageId).toList());
        assertEquals("Quiero Alice rayas\nquiero el color plata\nfoto del modelo\n[IMAGEN ADJUNTA]\ntalla L",
                batch.combinedInput());
        assertTrue(batch.containsImage());
        assertTrue(batch.containsAudio());
        assertFalse(batch.audioTranscriptionPending());
    }

    @Test
    void stopsAtTheConfiguredGapAndAtAnOutgoingReply() {
        CrmWhatsappMessageRepository repository = mock(CrmWhatsappMessageRepository.class);
        AgentMessageBatchFactory factory = new AgentMessageBatchFactory(repository, 5000, 15);
        CrmWhatsappMessage anchor = message(8L, BASE.plusSeconds(20), "TEXT", "nuevo grupo");
        CrmWhatsappMessage old = message(7L, BASE.plusSeconds(10), "TEXT", "grupo anterior");
        when(repository.findActiveMessagesEndingAt(eq(20L), eq(8L), any(Pageable.class)))
                .thenReturn(List.of(anchor, old));

        AgentMessageBatch byGap = factory.build(10L, 20L, 8L);
        assertEquals(List.of(8L), byGap.messages().stream().map(AgentMessageBatch.Item::messageId).toList());

        CrmWhatsappMessage latest = message(12L, BASE.plusSeconds(4), "TEXT", "despues");
        CrmWhatsappMessage outgoing = message(11L, BASE.plusSeconds(3), "TEXT", "respuesta");
        outgoing.setDirection("OUTGOING");
        outgoing.setOrigin("AGENT");
        CrmWhatsappMessage before = message(10L, BASE.plusSeconds(2), "TEXT", "antes");
        when(repository.findActiveMessagesEndingAt(eq(20L), eq(12L), any(Pageable.class)))
                .thenReturn(List.of(latest, outgoing, before));

        AgentMessageBatch byReply = factory.build(10L, 20L, 12L);
        assertEquals(List.of(12L), byReply.messages().stream().map(AgentMessageBatch.Item::messageId).toList());
    }

    @Test
    void keepsSemanticallyRelatedFragmentsBeyondTheQuietWindow() {
        CrmWhatsappMessageRepository repository = mock(CrmWhatsappMessageRepository.class);
        AgentMessageBatchFactory factory = new AgentMessageBatchFactory(repository, 5000, 30000, 15);
        CrmWhatsappMessage third = message(3L, BASE.plusSeconds(13), "TEXT", "Lo envies");
        CrmWhatsappMessage second = message(2L, BASE.plusSeconds(7), "TEXT", "Para mañana");
        CrmWhatsappMessage first = message(1L, BASE, "TEXT", "Que modelos tienes en talla S");
        when(repository.findActiveMessagesEndingAt(eq(20L), eq(3L), any(Pageable.class)))
                .thenReturn(List.of(third, second, first));

        AgentMessageBatch batch = factory.build(10L, 20L, 3L);

        assertEquals(List.of(1L, 2L, 3L),
                batch.messages().stream().map(AgentMessageBatch.Item::messageId).toList());
        assertEquals("Que modelos tienes en talla S\nPara mañana\nLo envies", batch.combinedInput());
    }

    @Test
    void marksAudioWithoutTranscriptAndHonorsTheMaximumBatchSize() {
        CrmWhatsappMessageRepository repository = mock(CrmWhatsappMessageRepository.class);
        AgentMessageBatchFactory factory = new AgentMessageBatchFactory(repository, 5000, 2);
        CrmWhatsappMessage audio = message(3L, BASE.plusSeconds(3), "AUDIO", "");
        CrmWhatsappMessage second = message(2L, BASE.plusSeconds(2), "TEXT", "dos");
        CrmWhatsappMessage first = message(1L, BASE.plusSeconds(1), "TEXT", "uno");
        when(repository.findActiveMessagesEndingAt(eq(20L), eq(3L), any(Pageable.class)))
                .thenReturn(List.of(audio, second, first));

        AgentMessageBatch batch = factory.build(10L, 20L, 3L);

        assertEquals(2, batch.messages().size());
        assertEquals(List.of(2L, 3L), batch.messages().stream().map(AgentMessageBatch.Item::messageId).toList());
        assertTrue(batch.audioTranscriptionPending());
        assertTrue(batch.combinedInput().contains("[AUDIO PENDIENTE DE TRANSCRIPCION]"));
    }

    private CrmWhatsappMessage message(Long id, LocalDateTime at, String type, String body) {
        CrmWhatsappMessage message = new CrmWhatsappMessage();
        message.setIdMessage(id);
        message.setDirection("INCOMING");
        message.setOrigin("EXTERNAL");
        message.setMessageType(type);
        message.setBody(body);
        message.setCreatedAt(at);
        return message;
    }
}
