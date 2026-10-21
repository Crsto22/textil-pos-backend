package com.sistemapos.sistematextil.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class CrmWhatsappConversationLegacyEnumConverterTest {

    private final CrmWhatsappAttentionQueueConverter queueConverter =
            new CrmWhatsappAttentionQueueConverter();
    private final CrmWhatsappWaitingReasonConverter reasonConverter =
            new CrmWhatsappWaitingReasonConverter();
    private final CrmWhatsappAgentEventConverter eventConverter =
            new CrmWhatsappAgentEventConverter();

    @Test
    void legacyAiActiveQueueMapsToTheNewAgentQueue() {
        assertEquals(CrmWhatsappAttentionQueue.AGENT_ACTIVE,
                queueConverter.convertToEntityAttribute("AI_ACTIVE"));
        assertEquals(CrmWhatsappAttentionQueue.ADVISOR_REQUIRED,
                queueConverter.convertToEntityAttribute("future_legacy_value"));
    }

    @Test
    void retiredPaymentQueueMapsToAgentAndCurrentValuesKeepTheirMeaning() {
        assertEquals(CrmWhatsappAttentionQueue.AGENT_ACTIVE,
                queueConverter.convertToEntityAttribute("PAYMENT_VERIFICATION"));
        assertEquals("HUMAN_ACTIVE",
                queueConverter.convertToDatabaseColumn(CrmWhatsappAttentionQueue.HUMAN_ACTIVE));
    }

    @Test
    void legacyWaitingReasonFallsBackWithoutChangingNull() {
        assertEquals(CrmWhatsappWaitingReason.ADVISOR_REQUIRED,
                reasonConverter.convertToEntityAttribute("LOW_CONFIDENCE"));
        assertNull(reasonConverter.convertToEntityAttribute(null));
    }

    @Test
    void activeAgentQueueIsPreservedWhenConversationIsNormalized() {
        CrmWhatsappConversation conversation = new CrmWhatsappConversation();
        conversation.setStatus("ESPERA");
        conversation.setAttentionQueue(CrmWhatsappAttentionQueue.AGENT_ACTIVE);
        conversation.setWaitingReason(null);

        conversation.onUpdate();

        assertEquals(CrmWhatsappAttentionQueue.AGENT_ACTIVE, conversation.getAttentionQueue());
        assertNull(conversation.getWaitingReason());
    }

    @Test
    void legacyFilmPaymentEventMapsToCurrentEvent() {
        assertEquals(CrmWhatsappAgentEvent.PAGO_FILM_APROBADO,
                eventConverter.convertToEntityAttribute("FILM_PAYMENT_CONFIRMED"));
        assertEquals("PAGO_FILM_APROBADO",
                eventConverter.convertToDatabaseColumn(CrmWhatsappAgentEvent.PAGO_FILM_APROBADO));
    }

    @Test
    void unknownHistoricalAgentEventDoesNotBreakSessionReads() {
        assertNull(eventConverter.convertToEntityAttribute("future_retired_event"));
        assertNull(eventConverter.convertToEntityAttribute(null));
    }
}
