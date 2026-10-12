package com.sistemapos.sistematextil.services.agent.payment;

/**
 * Datos visibles extraidos por el modelo multimodal. Ninguno de estos valores
 * autoriza por si mismo un pago; la decision pertenece al validador Java.
 */
public record AgentPaymentReceiptExtraction(
        boolean paymentReceipt,
        String amount,
        String currency,
        String operationDate,
        String operationTime,
        String recipient,
        String destinationAccount,
        String operationNumber,
        String paymentMethod,
        String visibleText,
        int confidence) {
}
