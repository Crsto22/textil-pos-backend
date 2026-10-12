package com.sistemapos.sistematextil.services.agent.context;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.sistemapos.sistematextil.model.CrmWhatsappAgentEvent;
import com.sistemapos.sistematextil.model.CrmWhatsappAgentState;
import com.sistemapos.sistematextil.services.agent.context.AgentContext.Cart;
import com.sistemapos.sistematextil.services.agent.context.AgentContext.CartItem;
import com.sistemapos.sistematextil.services.agent.context.AgentContext.CartPromotion;
import com.sistemapos.sistematextil.services.agent.context.AgentContext.Fulfillment;
import com.sistemapos.sistematextil.services.agent.context.AgentContext.Message;
import com.sistemapos.sistematextil.services.agent.context.AgentContext.Payment;
import com.sistemapos.sistematextil.services.agent.context.AgentContext.PaymentEvidence;
import com.sistemapos.sistematextil.services.agent.context.AgentContext.Role;
import com.sistemapos.sistematextil.services.agent.context.AgentContext.ToolResult;

@Repository
public class JdbcAgentContextDataSource implements AgentContextDataSource {
    private final JdbcTemplate jdbc;

    public JdbcAgentContextDataSource(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public SessionSnapshot requireSession(Long sessionId) {
        List<SessionSnapshot> rows = jdbc.query("""
                SELECT s.id_agent_session, s.session_key, s.id_conversation, s.id_connection,
                       cn.id_sucursal, s.last_customer_message_id, s.state, s.resume_state,
                       s.last_event, s.summary, cl.id_cliente, cl.nombres,
                       COALESCE(NULLIF(cl.telefono, ''), NULLIF(c.phone_number, ''), c.phone) AS telefono
                FROM crm_whatsapp_agent_session s
                INNER JOIN crm_whatsapp_connection cn ON cn.id_connection = s.id_connection
                INNER JOIN crm_whatsapp_conversation c ON c.id_conversation = s.id_conversation
                LEFT JOIN cliente cl ON cl.id_cliente = COALESCE(s.id_cliente, c.id_cliente)
                WHERE s.id_agent_session = ?
                """, (rs, row) -> new SessionSnapshot(
                        rs.getLong("id_agent_session"),
                        rs.getString("session_key"),
                        rs.getLong("id_conversation"),
                        rs.getLong("id_connection"),
                        nullableInteger(rs, "id_sucursal"),
                        nullableLong(rs, "last_customer_message_id"),
                        enumValue(CrmWhatsappAgentState.class, rs.getString("state")),
                        enumValue(CrmWhatsappAgentState.class, rs.getString("resume_state")),
                        enumValue(CrmWhatsappAgentEvent.class, rs.getString("last_event")),
                        rs.getString("summary"),
                        nullableInteger(rs, "id_cliente"),
                        rs.getString("nombres"),
                        rs.getString("telefono")), sessionId);
        if (rows.isEmpty()) throw new IllegalArgumentException("Sesion de agente no encontrada: " + sessionId);
        return rows.getFirst();
    }

    @Override
    public List<MessageSource> findRecentMessages(Long conversationId, Long anchorMessageId, int limit) {
        List<MessageSource> descending = jdbc.query("""
                SELECT id_message, direction, origin, message_type,
                       COALESCE(NULLIF(audio_transcription, ''), body, '') AS context_content,
                       created_at
                FROM crm_whatsapp_message
                WHERE id_conversation = ?
                  AND deleted_at IS NULL
                  AND (? IS NULL OR id_message <= ?)
                  AND (
                    (direction = 'INCOMING' AND origin <> 'CRM_SYSTEM')
                    OR (direction = 'OUTGOING' AND origin IN ('AGENT', 'AI', 'AI_KIMENTS'))
                  )
                ORDER BY id_message DESC
                LIMIT ?
                """, (rs, row) -> new MessageSource(
                        new Message(
                                rs.getLong("id_message"),
                                Role.CUSTOMER,
                                rs.getString("message_type"),
                                rs.getString("context_content"),
                                localDateTime(rs, "created_at")),
                        rs.getString("direction"),
                        rs.getString("origin")),
                conversationId, anchorMessageId, anchorMessageId, limit);
        Collections.reverse(descending);
        return List.copyOf(descending);
    }

    @Override
    public Optional<Cart> findActiveCart(Long sessionId) {
        List<CartHeader> headers = jdbc.query("""
                SELECT id_agent_cart, status, currency, subtotal, discount_total, total
                FROM crm_whatsapp_agent_cart
                WHERE id_agent_session = ? AND active_slot = 1
                ORDER BY id_agent_cart DESC
                LIMIT 1
                """, (rs, row) -> new CartHeader(
                        rs.getLong("id_agent_cart"),
                        rs.getString("status"),
                        rs.getString("currency"),
                        rs.getBigDecimal("subtotal"),
                        rs.getBigDecimal("discount_total"),
                        rs.getBigDecimal("total")), sessionId);
        if (headers.isEmpty()) return Optional.empty();

        CartHeader header = headers.getFirst();
        List<CartItem> items = jdbc.query("""
                SELECT id_agent_cart_item, id_producto_variante, product_name_snapshot,
                       color_snapshot, size_snapshot, quantity, unit_price, line_total
                FROM crm_whatsapp_agent_cart_item
                WHERE id_agent_cart = ?
                ORDER BY id_agent_cart_item
                """, (rs, row) -> new CartItem(
                        rs.getLong("id_agent_cart_item"),
                        rs.getInt("id_producto_variante"),
                        rs.getString("product_name_snapshot"),
                        rs.getString("color_snapshot"),
                        rs.getString("size_snapshot"),
                        rs.getInt("quantity"),
                        rs.getBigDecimal("unit_price"),
                        rs.getBigDecimal("line_total")), header.id());
        List<CartPromotion> promotions = jdbc.query("""
                SELECT id_agent_cart_promotion, id_ecommerce_promocion_combo,
                       promotion_name_snapshot, quantity, combo_price_snapshot, discount_amount
                FROM crm_whatsapp_agent_cart_promotion
                WHERE id_agent_cart = ?
                ORDER BY application_order, id_agent_cart_promotion
                """, (rs, row) -> new CartPromotion(
                        rs.getLong("id_agent_cart_promotion"),
                        rs.getInt("id_ecommerce_promocion_combo"),
                        rs.getString("promotion_name_snapshot"),
                        rs.getInt("quantity"),
                        rs.getBigDecimal("combo_price_snapshot"),
                        rs.getBigDecimal("discount_amount")), header.id());
        return Optional.of(new Cart(header.id(), header.status(), header.currency(), header.subtotal(),
                header.discountTotal(), header.total(), List.copyOf(items), List.copyOf(promotions)));
    }

    @Override
    public Optional<Payment> findLatestPayment(Long sessionId) {
        List<PaymentHeader> payments = jdbc.query("""
                SELECT r.id_payment_request, r.status, r.reservation_status, mp.nombre AS payment_method,
                       r.expected_amount, r.currency, r.expires_at
                FROM crm_whatsapp_payment_request r
                LEFT JOIN metodo_pago_config mp ON mp.id_metodo_pago = r.id_metodo_pago
                WHERE r.id_agent_session = ?
                ORDER BY r.id_payment_request DESC
                LIMIT 1
                """, (rs, row) -> new PaymentHeader(
                        rs.getLong("id_payment_request"),
                        rs.getString("status"),
                        rs.getString("reservation_status"),
                        rs.getString("payment_method"),
                        rs.getBigDecimal("expected_amount"),
                        rs.getString("currency"),
                        localDateTime(rs, "expires_at")), sessionId);
        if (payments.isEmpty()) return Optional.empty();
        PaymentHeader payment = payments.getFirst();
        List<PaymentEvidence> evidence = jdbc.query("""
                SELECT id_payment_evidence, validation_status, processing_status, detected_provider,
                       detected_amount, detected_currency, confidence, operation_at
                FROM crm_whatsapp_payment_evidence
                WHERE id_payment_request = ?
                ORDER BY id_payment_evidence DESC
                LIMIT 1
                """, (rs, row) -> new PaymentEvidence(
                        rs.getLong("id_payment_evidence"),
                        rs.getString("validation_status"),
                        rs.getString("processing_status"),
                        rs.getString("detected_provider"),
                        rs.getBigDecimal("detected_amount"),
                        rs.getString("detected_currency"),
                        nullableInteger(rs, "confidence"),
                        localDateTime(rs, "operation_at")), payment.id());
        return Optional.of(new Payment(payment.id(), payment.status(), payment.reservationStatus(), payment.method(),
                payment.expectedAmount(), payment.currency(), payment.expiresAt(),
                evidence.isEmpty() ? null : evidence.getFirst()));
    }

    @Override
    public Optional<Fulfillment> findFulfillment(Long sessionId) {
        List<Fulfillment> rows = jdbc.query("""
                SELECT id_fulfillment, id_venta, method, status, recipient_name, document_type, document_number,
                       phone, id_pickup_branch, id_shalom_agency, shalom_agency, shalom_destination,
                       shalom_agency_address, shalom_candidate_ids
                FROM crm_whatsapp_fulfillment
                WHERE id_agent_session = ?
                LIMIT 1
                """, (rs, row) -> new Fulfillment(
                        rs.getLong("id_fulfillment"),
                        nullableInteger(rs, "id_venta"),
                        rs.getString("method"),
                        rs.getString("status"),
                        rs.getString("recipient_name"),
                        rs.getString("document_type"),
                        rs.getString("document_number"),
                        rs.getString("phone"),
                        nullableInteger(rs, "id_pickup_branch"),
                        nullableLong(rs, "id_shalom_agency"),
                        rs.getString("shalom_agency"),
                        rs.getString("shalom_destination"),
                        rs.getString("shalom_agency_address"),
                        rs.getString("shalom_candidate_ids")), sessionId);
        return rows.stream().findFirst();
    }

    @Override
    public List<ToolResult> findRecentToolResults(Long sessionId, int limit) {
        List<ToolResult> descending = jdbc.query("""
                SELECT id_agent_tool_call, tool_name, status, response_json,
                       error_code, error_message, completed_at
                FROM crm_whatsapp_agent_tool_call
                WHERE id_agent_session = ?
                  AND status NOT IN ('PENDING', 'RUNNING')
                ORDER BY id_agent_tool_call DESC
                LIMIT ?
                """, (rs, row) -> new ToolResult(
                        rs.getLong("id_agent_tool_call"),
                        rs.getString("tool_name"),
                        rs.getString("status"),
                        rs.getString("response_json"),
                        rs.getString("error_code"),
                        rs.getString("error_message"),
                        localDateTime(rs, "completed_at")), sessionId, limit);
        Collections.reverse(descending);
        return List.copyOf(descending);
    }

    private LocalDateTime localDateTime(ResultSet rs, String column) throws SQLException {
        var value = rs.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }

    private Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private Integer nullableInteger(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private <E extends Enum<E>> E enumValue(Class<E> type, String value) {
        return blank(value) ? null : Enum.valueOf(type, value);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private record CartHeader(
            Long id,
            String status,
            String currency,
            java.math.BigDecimal subtotal,
            java.math.BigDecimal discountTotal,
            java.math.BigDecimal total) {
    }

    private record PaymentHeader(
            Long id,
            String status,
            String reservationStatus,
            String method,
            java.math.BigDecimal expectedAmount,
            String currency,
            LocalDateTime expiresAt) {
    }
}
