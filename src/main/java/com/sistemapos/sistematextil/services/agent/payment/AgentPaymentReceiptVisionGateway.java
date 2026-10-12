package com.sistemapos.sistematextil.services.agent.payment;

public interface AgentPaymentReceiptVisionGateway {
    VisionResult extract(Long connectionId, byte[] content, String mimeType);

    record VisionResult(
            String provider,
            String model,
            String credentialSource,
            long latencyMs,
            AgentPaymentReceiptExtraction extraction) {
    }
}
