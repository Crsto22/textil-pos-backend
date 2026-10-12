package com.sistemapos.sistematextil.model;

import java.util.Locale;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Maps reasons from the retired AI runtime to the general human-attention queue. */
@Converter
public class CrmWhatsappWaitingReasonConverter
        implements AttributeConverter<CrmWhatsappWaitingReason, String> {

    @Override
    public String convertToDatabaseColumn(CrmWhatsappWaitingReason value) {
        return value == null ? null : value.name();
    }

    @Override
    public CrmWhatsappWaitingReason convertToEntityAttribute(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        try {
            return CrmWhatsappWaitingReason.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return CrmWhatsappWaitingReason.ADVISOR_REQUIRED;
        }
    }
}
