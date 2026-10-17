package com.sistemapos.sistematextil.services.agent.ai;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum AgentCartAction {
    NONE,
    ADD_PRODUCT,
    CHANGE_QUANTITY,
    CHANGE_VARIANT,
    REPLACE_PRODUCT,
    REMOVE_PRODUCT,
    CLEAR_CART,
    CONFIRM_CART,
    ADD_PROMOTION;

    /**
     * Gemini puede devolver la accion comercial CHANGE_CART dentro del campo
     * cartAction. La tratamos como una solicitud generica y nunca ejecutamos una
     * mutacion concreta sin conocer la operacion exacta.
     */
    @JsonCreator
    public static AgentCartAction fromJson(String value) {
        if (value == null || value.isBlank()) return NONE;
        String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        if ("CHANGE_CART".equals(normalized) || "UPDATE_CART".equals(normalized)) return NONE;
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return NONE;
        }
    }
}
