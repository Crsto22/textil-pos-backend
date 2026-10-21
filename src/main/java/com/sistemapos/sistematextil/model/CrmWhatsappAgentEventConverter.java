package com.sistemapos.sistematextil.model;

import java.util.Locale;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Keeps agent sessions readable when historical event names are retired. */
@Converter
public class CrmWhatsappAgentEventConverter
        implements AttributeConverter<CrmWhatsappAgentEvent, String> {

    @Override
    public String convertToDatabaseColumn(CrmWhatsappAgentEvent value) {
        return value == null ? null : value.name();
    }

    @Override
    public CrmWhatsappAgentEvent convertToEntityAttribute(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if ("FILM_PAYMENT_CONFIRMED".equals(normalized)) {
            return CrmWhatsappAgentEvent.PAGO_FILM_APROBADO;
        }
        try {
            return CrmWhatsappAgentEvent.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
