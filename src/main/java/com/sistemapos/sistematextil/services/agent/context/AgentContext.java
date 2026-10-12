package com.sistemapos.sistematextil.services.agent.context;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.sistemapos.sistematextil.model.CrmWhatsappAgentEvent;
import com.sistemapos.sistematextil.model.CrmWhatsappAgentState;

public record AgentContext(
        Long sessionId,
        String sessionKey,
        Long conversationId,
        Long connectionId,
        Integer branchId,
        Long anchorMessageId,
        CrmWhatsappAgentState state,
        CrmWhatsappAgentState resumeState,
        CrmWhatsappAgentEvent lastEvent,
        String previousSummary,
        Customer customer,
        List<Message> recentMessages,
        Cart cart,
        List<AgentMissingData> missingData,
        Payment payment,
        Fulfillment fulfillment,
        List<ToolResult> toolResults,
        String fingerprint) {

    public record Customer(Integer id, String name, String phone) {
    }

    public record Message(
            Long id,
            Role role,
            String messageType,
            String content,
            LocalDateTime createdAt) {
    }

    public enum Role {
        CUSTOMER,
        ASSISTANT
    }

    public record Cart(
            Long id,
            String status,
            String currency,
            BigDecimal subtotal,
            BigDecimal discountTotal,
            BigDecimal total,
            List<CartItem> items,
            List<CartPromotion> promotions) {

        public static Cart empty() {
            return new Cart(null, "EMPTY", "PEN", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    List.of(), List.of());
        }
    }

    public record CartItem(
            Long id,
            Integer variantId,
            String product,
            String color,
            String size,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal lineTotal) {
    }

    public record CartPromotion(
            Long id,
            Integer promotionId,
            String name,
            int quantity,
            BigDecimal comboPrice,
            BigDecimal discountAmount) {
    }

    public record Payment(
            Long id,
            String status,
            String reservationStatus,
            String method,
            BigDecimal expectedAmount,
            String currency,
            LocalDateTime expiresAt,
            PaymentEvidence latestEvidence) {
    }

    public record PaymentEvidence(
            Long id,
            String validationStatus,
            String processingStatus,
            String detectedProvider,
            BigDecimal detectedAmount,
            String detectedCurrency,
            Integer confidence,
            LocalDateTime operationAt) {
    }

    public record Fulfillment(
            Long id,
            Integer saleId,
            String method,
            String status,
            String recipientName,
            String documentType,
            String documentNumber,
            String phone,
            Integer pickupBranchId,
            Long shalomAgencyId,
            String shalomAgency,
            String shalomDestination,
            String shalomAgencyAddress,
            String shalomCandidateIds) {
    }

    public record ToolResult(
            Long id,
            String toolName,
            String status,
            String responseJson,
            String errorCode,
            String errorMessage,
            LocalDateTime completedAt) {
    }
}
