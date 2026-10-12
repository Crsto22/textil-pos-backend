package com.sistemapos.sistematextil.model;

import java.util.Locale;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Keeps conversation reads compatible with queue values written by the retired
 * AI runtime. A legacy value must never prevent an inbound WhatsApp message
 * from being persisted.
 */
@Converter
public class CrmWhatsappAttentionQueueConverter
        implements AttributeConverter<CrmWhatsappAttentionQueue, String> {

    @Override
    public String convertToDatabaseColumn(CrmWhatsappAttentionQueue value) {
        return value == null ? CrmWhatsappAttentionQueue.ADVISOR_REQUIRED.name() : value.name();
    }

    @Override
    public CrmWhatsappAttentionQueue convertToEntityAttribute(String value) {
        if (value == null || value.isBlank()) {
            return CrmWhatsappAttentionQueue.ADVISOR_REQUIRED;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if ("AI_ACTIVE".equals(normalized)
                || "AUTOMATICA".equals(normalized)
                || "PAYMENT_VERIFICATION".equals(normalized)) {
            return CrmWhatsappAttentionQueue.AGENT_ACTIVE;
        }
        try {
            return CrmWhatsappAttentionQueue.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return CrmWhatsappAttentionQueue.ADVISOR_REQUIRED;
        }
    }
}
