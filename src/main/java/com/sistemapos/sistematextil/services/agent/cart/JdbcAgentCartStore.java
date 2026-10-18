package com.sistemapos.sistematextil.services.agent.cart;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAgentCartStore implements AgentCartStore {
    private final JdbcTemplate jdbc;

    public JdbcAgentCartStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<CartRow> lockActiveCart(Long sessionId) {
        return queryCart("""
                SELECT id_agent_cart, id_agent_session, status, currency, subtotal, discount_total, total, lock_version,
                       confirmed_at
                FROM crm_whatsapp_agent_cart
                WHERE id_agent_session = ? AND active_slot = 1
                LIMIT 1
                FOR UPDATE
                """, sessionId);
    }

    @Override
    public Optional<CartRow> findLatestCart(Long sessionId) {
        return queryCart("""
                SELECT id_agent_cart, id_agent_session, status, currency, subtotal, discount_total, total, lock_version,
                       confirmed_at
                FROM crm_whatsapp_agent_cart
                WHERE id_agent_session = ?
                ORDER BY id_agent_cart DESC
                LIMIT 1
                """, sessionId);
    }

    @Override
    public CartRow createCart(Long sessionId, String idempotencyKey, LocalDateTime now) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO crm_whatsapp_agent_cart
                        (id_agent_session, idempotency_key, status, active_slot, currency,
                         subtotal, discount_total, total, lock_version, created_at, updated_at)
                    VALUES (?, ?, 'DRAFT', 1, 'PEN', 0.00, 0.00, 0.00, 0, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, sessionId);
            statement.setString(2, idempotencyKey);
            statement.setObject(3, now);
            statement.setObject(4, now);
            return statement;
        }, keys);
        Number key = keys.getKey();
        if (key == null) throw new IllegalStateException("No se pudo crear el carrito persistente");
        return new CartRow(key.longValue(), sessionId, "DRAFT", "PEN",
                java.math.BigDecimal.ZERO.setScale(2), java.math.BigDecimal.ZERO.setScale(2),
                java.math.BigDecimal.ZERO.setScale(2), 0L, null);
    }

    @Override
    public List<CartItemRow> findItems(Long cartId) {
        return jdbc.query("""
                SELECT id_agent_cart_item, id_producto_variante, quantity, product_name_snapshot,
                       color_snapshot, size_snapshot, unit_price, unit_discount, line_total
                FROM crm_whatsapp_agent_cart_item
                WHERE id_agent_cart = ?
                ORDER BY id_agent_cart_item
                """, (rs, row) -> new CartItemRow(
                rs.getLong("id_agent_cart_item"), rs.getInt("id_producto_variante"), rs.getInt("quantity"),
                rs.getString("product_name_snapshot"), rs.getString("color_snapshot"),
                rs.getString("size_snapshot"), rs.getBigDecimal("unit_price"),
                rs.getBigDecimal("unit_discount"), rs.getBigDecimal("line_total")), cartId);
    }

    @Override
    public void upsertItem(Long cartId, PricedCartItem item, LocalDateTime now) {
        jdbc.update("""
                INSERT INTO crm_whatsapp_agent_cart_item
                    (id_agent_cart, id_producto_variante, quantity, product_name_snapshot, color_snapshot,
                     size_snapshot, unit_price, unit_discount, line_total, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    quantity = VALUES(quantity), product_name_snapshot = VALUES(product_name_snapshot),
                    color_snapshot = VALUES(color_snapshot), size_snapshot = VALUES(size_snapshot),
                    unit_price = VALUES(unit_price), unit_discount = VALUES(unit_discount),
                    line_total = VALUES(line_total), updated_at = VALUES(updated_at)
                """, cartId, item.variantId(), item.quantity(), item.productName(), item.color(), item.size(),
                item.unitPrice(), item.unitDiscount(), item.lineTotal(), now, now);
    }

    @Override
    public int deleteItem(Long cartId, Integer variantId) {
        return jdbc.update("DELETE FROM crm_whatsapp_agent_cart_item WHERE id_agent_cart = ? AND id_producto_variante = ?",
                cartId, variantId);
    }

    @Override
    public void clearCart(Long cartId) {
        jdbc.update("DELETE FROM crm_whatsapp_agent_cart_promotion WHERE id_agent_cart = ?", cartId);
        jdbc.update("DELETE FROM crm_whatsapp_agent_cart_item WHERE id_agent_cart = ?", cartId);
    }

    @Override
    public void replacePromotions(Long cartId, List<PromotionApplicationRow> promotions, LocalDateTime now) {
        jdbc.update("DELETE FROM crm_whatsapp_agent_cart_promotion WHERE id_agent_cart = ?", cartId);
        for (PromotionApplicationRow promotion : promotions) {
            jdbc.update("""
                    INSERT INTO crm_whatsapp_agent_cart_promotion
                        (id_agent_cart, id_ecommerce_promocion_combo, application_order, quantity,
                         promotion_name_snapshot, combo_price_snapshot, discount_amount, application_key, created_at)
                    VALUES (?, ?, ?, 1, ?, ?, ?, ?, ?)
                    """, cartId, promotion.promotionId(), promotion.applicationOrder(), promotion.name(),
                    promotion.comboPrice(), promotion.discountAmount(), promotion.applicationKey(), now);
        }
    }

    @Override
    public void updateTotals(Long cartId, java.math.BigDecimal subtotal, java.math.BigDecimal discount,
            java.math.BigDecimal total, LocalDateTime now) {
        jdbc.update("""
                UPDATE crm_whatsapp_agent_cart
                SET subtotal = ?, discount_total = ?, total = ?, lock_version = lock_version + 1, updated_at = ?
                WHERE id_agent_cart = ? AND status = 'DRAFT' AND active_slot = 1
                """, subtotal, discount, total, now, cartId);
    }

    @Override
    public void confirm(Long cartId, LocalDateTime now) {
        int updated = jdbc.update("""
                UPDATE crm_whatsapp_agent_cart
                SET status = 'CONFIRMED', confirmed_at = ?,
                    lock_version = lock_version + 1, updated_at = ?
                WHERE id_agent_cart = ? AND status = 'DRAFT' AND active_slot = 1
                """, now, now, cartId);
        if (updated != 1) throw new IllegalStateException("El carrito ya no puede confirmarse");
    }

    @Override
    public void reopen(Long cartId, LocalDateTime now) {
        int updated = jdbc.update("""
                UPDATE crm_whatsapp_agent_cart
                SET status='DRAFT', confirmed_at=NULL, lock_version=lock_version+1, updated_at=?
                WHERE id_agent_cart=? AND status='CONFIRMED' AND active_slot=1
                """, now, cartId);
        if (updated != 1) throw new IllegalStateException("El carrito no puede volver a edición");
    }

    @Override
    public Optional<PendingPromotionRow> findPendingPromotion(Long sessionId) {
        return jdbc.query("""
                SELECT id_agent_session, id_ecommerce_promocion_combo, promotion_name, selections_json,
                       source_job_id
                FROM crm_whatsapp_agent_pending_promotion
                WHERE id_agent_session = ?
                """, (rs, row) -> new PendingPromotionRow(
                rs.getLong("id_agent_session"), rs.getInt("id_ecommerce_promocion_combo"),
                rs.getString("promotion_name"), rs.getString("selections_json"),
                rs.getObject("source_job_id", Long.class)), sessionId).stream().findFirst();
    }

    @Override
    public void upsertPendingPromotion(Long sessionId, Integer promotionId, String promotionName,
            String selectionsJson, Long sourceJobId, LocalDateTime now) {
        jdbc.update("""
                INSERT INTO crm_whatsapp_agent_pending_promotion
                    (id_agent_session, id_ecommerce_promocion_combo, promotion_name, selections_json,
                     source_job_id, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    id_ecommerce_promocion_combo = VALUES(id_ecommerce_promocion_combo),
                    promotion_name = VALUES(promotion_name), selections_json = VALUES(selections_json),
                    source_job_id = VALUES(source_job_id), updated_at = VALUES(updated_at)
                """, sessionId, promotionId, promotionName, selectionsJson, sourceJobId, now, now);
    }

    @Override
    public void deletePendingPromotion(Long sessionId) {
        jdbc.update("DELETE FROM crm_whatsapp_agent_pending_promotion WHERE id_agent_session = ?", sessionId);
    }

    @Override
    public Optional<OperationRow> findOperation(String idempotencyKey) {
        return jdbc.query("""
                SELECT idempotency_key, tool_name, status, request_json, response_json
                FROM crm_whatsapp_agent_tool_call
                WHERE idempotency_key = ?
                LIMIT 1
                """, (rs, row) -> new OperationRow(rs.getString("idempotency_key"), rs.getString("tool_name"),
                rs.getString("status"), rs.getString("request_json"), rs.getString("response_json")),
                idempotencyKey).stream().findFirst();
    }

    @Override
    public boolean claimOperation(Long sessionId, String idempotencyKey, String toolName, String requestFingerprint,
            LocalDateTime now) {
        return jdbc.update("""
                INSERT IGNORE INTO crm_whatsapp_agent_tool_call
                    (id_agent_session, idempotency_key, tool_name, status, request_json, started_at, created_at)
                VALUES (?, ?, ?, 'RUNNING', ?, ?, ?)
                """, sessionId, idempotencyKey, toolName, requestFingerprint, now, now) == 1;
    }

    @Override
    public void completeOperation(String idempotencyKey, String responseJson, LocalDateTime now) {
        int updated = jdbc.update("""
                UPDATE crm_whatsapp_agent_tool_call
                SET status = 'COMPLETED', response_json = ?, completed_at = ?
                WHERE idempotency_key = ? AND status = 'RUNNING'
                """, responseJson, now, idempotencyKey);
        if (updated != 1) throw new IllegalStateException("No se pudo completar la operacion idempotente del carrito");
    }

    private Optional<CartRow> queryCart(String sql, Long sessionId) {
        return jdbc.query(sql, (rs, row) -> new CartRow(
                rs.getLong("id_agent_cart"), rs.getLong("id_agent_session"), rs.getString("status"),
                rs.getString("currency"), rs.getBigDecimal("subtotal"), rs.getBigDecimal("discount_total"),
                rs.getBigDecimal("total"), rs.getLong("lock_version"),
                rs.getObject("confirmed_at", LocalDateTime.class)), sessionId).stream().findFirst();
    }
}
