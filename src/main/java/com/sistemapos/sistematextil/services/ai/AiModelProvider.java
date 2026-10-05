package com.sistemapos.sistematextil.services.ai;

import java.util.List;
import java.util.Map;

public interface AiModelProvider {

    ClassificationResult classify(ClassificationRequest request);

    DraftResult generateDraft(DraftRequest request);

    SaleActionResult interpretSaleAction(SaleActionRequest request);

    PaymentEvidenceExtraction extractPaymentEvidence(PaymentEvidenceRequest request);

    AudioTranscriptionResult transcribeAudio(AudioTranscriptionRequest request);

    ConnectionTest testConnection(Long connectionId);

    String provider();

    String model(Long connectionId);

    record ClassificationRequest(
            Long connectionId,
            String systemInstruction,
            String conversationContext,
            List<String> allowedIntents) {}

    record ClassificationResult(
            String intent,
            int confidence,
            boolean requiresHuman,
            String reason,
            List<ToolCall> tools,
            Usage usage) {}

    record ToolCall(String name, Map<String, Object> arguments) {}

    record DraftRequest(
            Long connectionId,
            String systemInstruction,
            String conversationContext,
            String intent,
            List<Map<String, Object>> toolResults) {}

    record DraftResult(
            String response,
            boolean requiresHuman,
            String reason,
            List<MediaSuggestion> media,
            Usage usage) {}

    record MediaSuggestion(
            Integer productId,
            Integer variantId,
            String color,
            String url,
            String thumbnailUrl) {}

    record SaleActionRequest(
            Long connectionId,
            String systemInstruction,
            String conversationContext,
            String intent,
            List<Map<String, Object>> toolResults) {}

    record SaleActionResult(
            String action,
            String productQuery,
            Integer promotionId,
            String color,
            String size,
            Integer quantity,
            String paymentMethod,
            int confidence,
            String reason,
            Usage usage) {}

    record PaymentEvidenceRequest(
            Long connectionId,
            byte[] fileBytes,
            String mimeType,
            String fileName) {}

    record PaymentEvidenceExtraction(
            boolean paymentEvidence,
            String provider,
            String amount,
            String currency,
            String operationCode,
            String operationDateTime,
            String recipient,
            int confidence,
            String extractedText,
            Map<String, Integer> fieldConfidences,
            List<String> warnings,
            Usage usage) {}

    record AudioTranscriptionRequest(
            Long connectionId,
            byte[] fileBytes,
            String mimeType,
            String fileName) {}

    record AudioTranscriptionResult(
            String status,
            String transcription,
            String language,
            int confidence,
            Usage usage) {}

    record Usage(Integer inputTokens, Integer outputTokens, Integer totalTokens) {
        public static Usage empty() {
            return new Usage(null, null, null);
        }
    }

    record ConnectionTest(boolean ok, String provider, String model, long latencyMs) {}
}
