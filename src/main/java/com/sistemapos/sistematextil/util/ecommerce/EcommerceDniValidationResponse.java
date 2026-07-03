package com.sistemapos.sistematextil.util.ecommerce;

public record EcommerceDniValidationResponse(
        Boolean valid,
        String dni,
        String nombres,
        String apellidos,
        String message) {
}
