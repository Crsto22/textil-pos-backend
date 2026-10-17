package com.sistemapos.sistematextil.services.agent.cart;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AgentCartStore {
    Optional<CartRow> lockActiveCart(Long sessionId);

    Optional<CartRow> findLatestCart(Long sessionId);

    CartRow createCart(Long sessionId, String idempotencyKey, LocalDateTime now);

    List<CartItemRow> findItems(Long cartId);

    void upsertItem(Long cartId, PricedCartItem item, LocalDateTime now);

    int deleteItem(Long cartId, Integer variantId);

    void clearCart(Long cartId);

    void replacePromotions(Long cartId, List<PromotionApplicationRow> promotions, LocalDateTime now);

    void updateTotals(Long cartId, BigDecimal subtotal, BigDecimal discount, BigDecimal total, LocalDateTime now);

    void confirm(Long cartId, LocalDateTime now);

    void reopen(Long cartId, LocalDateTime now);

    Optional<PendingPromotionRow> findPendingPromotion(Long sessionId);

    void upsertPendingPromotion(Long sessionId, Integer promotionId, String promotionName,
            String selectionsJson, Long sourceJobId, LocalDateTime now);

    void deletePendingPromotion(Long sessionId);

    Optional<OperationRow> findOperation(String idempotencyKey);

    boolean claimOperation(Long sessionId, String idempotencyKey, String toolName, String requestFingerprint,
            LocalDateTime now);

    void completeOperation(String idempotencyKey, String responseJson, LocalDateTime now);

    record CartRow(Long id, Long sessionId, String status, String currency, BigDecimal subtotal,
            BigDecimal discountTotal, BigDecimal total, Long lockVersion) {
    }

    record CartItemRow(Long id, Integer variantId, int quantity, String productName, String color, String size,
            BigDecimal unitPrice, BigDecimal unitDiscount, BigDecimal lineTotal) {
    }

    record PricedCartItem(Integer variantId, int quantity, String productName, String color, String size,
            BigDecimal unitPrice, BigDecimal unitDiscount, BigDecimal lineTotal) {
    }

    record PromotionApplicationRow(Integer promotionId, int applicationOrder, String name,
            BigDecimal comboPrice, BigDecimal discountAmount, String applicationKey) {
    }

    record OperationRow(String idempotencyKey, String toolName, String status, String requestFingerprint,
            String responseJson) {
    }

    record PendingPromotionRow(Long sessionId, Integer promotionId, String promotionName,
            String selectionsJson, Long sourceJobId) {
    }
}
