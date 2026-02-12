package com.sistemapos.sistematextil.model.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class EstadoActivoConverter implements AttributeConverter<String, Boolean> {

    @Override
    public Boolean convertToDatabaseColumn(String estado) {
        if (estado == null) {
            return null;
        }
        return "ACTIVO".equalsIgnoreCase(estado);
    }

    @Override
    public String convertToEntityAttribute(Boolean activo) {
        if (activo == null) {
            return "INACTIVO";
        }
        return activo ? "ACTIVO" : "INACTIVO";
    }
}
