package com.sistemapos.sistematextil.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class CrmWhatsappConversationLegacyEnumConverterTest {

    private final CrmWhatsappAttentionQueueConverter queueConverter =
            new CrmWhatsappAttentionQueueConverter();
    private final CrmWhatsappWaitingReasonConverter reasonConverter =
            new CrmWhatsappWaitingReasonConverter();

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
}
