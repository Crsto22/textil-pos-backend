package com.sistemapos.sistematextil.services.agent.visibility;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sistemapos.sistematextil.model.CrmWhatsappAgentSession;
import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAgentSessionRepository;
import com.sistemapos.sistematextil.services.agent.context.AgentContext.Cart;
import com.sistemapos.sistematextil.services.agent.context.AgentContext.Fulfillment;
import com.sistemapos.sistematextil.services.agent.context.AgentContextDataSource;
import com.sistemapos.sistematextil.util.usuario.Rol;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AgentConversationVisibilityService {

    private final AgentConversationAccessGuard accessGuard;
    private final CrmWhatsappAgentSessionRepository sessionRepository;
    private final AgentContextDataSource contextDataSource;
    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public AgentConversationSummary getSummary(Long conversationId, Usuario actor) {
        accessGuard.requireVisible(conversationId, actor);
        boolean admin = actor != null && actor.getRol() == Rol.ADMINISTRADOR;
        CrmWhatsappAgentSession session = sessionRepository
                .findByConversation_IdConversationAndActiveSlotTrue(conversationId)
                .or(() -> sessionRepository.findFirstByConversation_IdConversationOrderByIdAgentSessionDesc(conversationId))
                .orElse(null);
        if (session == null) {
            return new AgentConversationSummary(false, admin, null, null, Cart.empty(), null, null, null,
                    List.of(), List.of(), LocalDateTime.now());
        }

        Long sessionId = session.getIdAgentSession();
        SessionView sessionView = new SessionView(
                sessionId, session.getSessionKey(), enumName(session.getState()), enumName(session.getResumeState()),
                enumName(session.getLastEvent()), session.getSummary(), Boolean.TRUE.equals(session.getActiveSlot()),
                session.getStartedAt(), session.getLastActivityAt(), session.getStateChangedAt(), session.getCompletedAt());
        return new AgentConversationSummary(
                true, admin, sessionView, findCustomer(sessionId),
                contextDataSource.findActiveCart(sessionId).orElse(Cart.empty()),
                findPayment(sessionId), contextDataSource.findFulfillment(sessionId).orElse(null), findSale(sessionId),
                findSupportCases(conversationId), admin ? findToolCalls(sessionId) : List.of(), LocalDateTime.now());
    }

    private CustomerView findCustomer(Long sessionId) {
        List<CustomerView> rows = jdbc.query("""
                SELECT c.id_cliente, c.nombres, c.telefono, c.tipo_documento, c.nro_documento, c.correo, c.direccion
                FROM crm_whatsapp_agent_session s
                LEFT JOIN cliente c ON c.id_cliente = s.id_cliente
                WHERE s.id_agent_session = ?
                """, (rs, row) -> rs.getObject("id_cliente") == null ? null : new CustomerView(
                        rs.getInt("id_cliente"), rs.getString("nombres"), rs.getString("telefono"),
                        rs.getString("tipo_documento"), rs.getString("nro_documento"),
                        rs.getString("correo"), rs.getString("direccion")), sessionId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private PaymentView findPayment(Long sessionId) {
        List<PaymentView> rows = jdbc.query("""
                SELECT r.id_payment_request, r.status, r.reservation_status, r.expected_amount, r.currency,
                       r.reserved_at, r.expires_at, r.evidence_accepted_at, r.review_expires_at, r.released_at, r.release_reason,
                       mp.nombre AS payment_method, a.numero_cuenta, a.titular,
                       e.id_payment_evidence, e.validation_status, e.processing_status, e.detected_provider,
                       e.detected_amount, e.detected_currency, e.operation_code, e.operation_at, e.recipient,
                       e.confidence, e.extracted_text, e.warnings_json, e.review_note, e.reviewed_at,
                       e.file_name, e.mime_type
                FROM crm_whatsapp_payment_request r
                LEFT JOIN metodo_pago_config mp ON mp.id_metodo_pago = r.id_metodo_pago
                LEFT JOIN metodo_pago_cuenta a ON a.id_metodo_pago_cuenta = r.id_metodo_pago_cuenta
                LEFT JOIN crm_whatsapp_payment_evidence e ON e.id_payment_evidence = (
                    SELECT e2.id_payment_evidence FROM crm_whatsapp_payment_evidence e2
                    WHERE e2.id_payment_request = r.id_payment_request
                    ORDER BY CASE e2.validation_status
                        WHEN 'VALIDADO' THEN 0
                        WHEN 'ACEPTABLE' THEN 1
                        WHEN 'PENDIENTE_VALIDACION' THEN 2
                        WHEN 'OBSERVADO' THEN 3
                        WHEN 'RECHAZADO' THEN 4
                        ELSE 5 END,
                        e2.id_payment_evidence DESC
                    LIMIT 1)
                WHERE r.id_agent_session = ?
                ORDER BY r.id_payment_request DESC LIMIT 1
                """, (rs, row) -> mapPayment(rs), sessionId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private PaymentView mapPayment(ResultSet rs) throws SQLException {
        LocalDateTime expiresAt = dateTime(rs, "expires_at");
        LocalDateTime acceptedAt = dateTime(rs, "evidence_accepted_at");
        long remaining = expiresAt == null || acceptedAt != null ? 0
                : Math.max(0, Duration.between(LocalDateTime.now(), expiresAt).getSeconds());
        Long evidenceId = nullableLong(rs, "id_payment_evidence");
        PaymentEvidenceView evidence = evidenceId == null ? null : new PaymentEvidenceView(
                evidenceId, rs.getString("validation_status"), rs.getString("processing_status"),
                rs.getString("detected_provider"), rs.getBigDecimal("detected_amount"),
                rs.getString("detected_currency"), rs.getString("operation_code"), dateTime(rs, "operation_at"),
                rs.getString("recipient"), nullableInteger(rs, "confidence"), rs.getString("extracted_text"),
                rs.getString("warnings_json"), rs.getString("review_note"), dateTime(rs, "reviewed_at"),
                rs.getString("file_name"), rs.getString("mime_type"),
                "/api/crm/whatsapp/payment-evidences/" + evidenceId + "/media");
        return new PaymentView(
                rs.getLong("id_payment_request"), rs.getString("status"), rs.getString("reservation_status"),
                rs.getString("payment_method"), maskAccount(rs.getString("numero_cuenta")), rs.getString("titular"),
                rs.getBigDecimal("expected_amount"), rs.getString("currency"), dateTime(rs, "reserved_at"),
                expiresAt, remaining, acceptedAt, dateTime(rs, "review_expires_at"), dateTime(rs, "released_at"),
                rs.getString("release_reason"), evidence);
    }

    private SaleView findSale(Long sessionId) {
        List<SaleView> rows = jdbc.query("""
                SELECT v.id_venta, v.tipo_comprobante, v.serie, v.correlativo, v.total, v.moneda,
                       v.estado, v.origen, v.fecha, v.motivo_anulacion
                FROM venta v
                WHERE v.id_venta = (
                    SELECT COALESCE(
                        (SELECT f.id_venta FROM crm_whatsapp_fulfillment f WHERE f.id_agent_session = s.id_agent_session LIMIT 1),
                        (SELECT r.id_venta FROM crm_whatsapp_payment_request r WHERE r.id_agent_session = s.id_agent_session
                         AND r.id_venta IS NOT NULL ORDER BY r.id_payment_request DESC LIMIT 1))
                    FROM crm_whatsapp_agent_session s WHERE s.id_agent_session = ?)
                LIMIT 1
                """, (rs, row) -> new SaleView(
                        rs.getInt("id_venta"), rs.getString("tipo_comprobante"), rs.getString("serie"),
                        nullableInteger(rs, "correlativo"), rs.getBigDecimal("total"), rs.getString("moneda"),
                        rs.getString("estado"), rs.getString("origen"), dateTime(rs, "fecha"),
                        rs.getString("motivo_anulacion")), sessionId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private List<SupportCaseView> findSupportCases(Long conversationId) {
        return jdbc.query("""
                SELECT sc.id_support_case, sc.category, sc.reason, sc.summary, sc.priority, sc.status,
                       sc.resolution_note, sc.opened_at, sc.assigned_at, sc.resolved_at,
                       CONCAT_WS(' ', u.nombre, u.apellido) AS assigned_user
                FROM crm_whatsapp_support_case sc
                LEFT JOIN usuario u ON u.id_usuario = sc.assigned_user_id
                WHERE sc.id_conversation = ?
                ORDER BY sc.opened_at DESC, sc.id_support_case DESC LIMIT 20
                """, (rs, row) -> new SupportCaseView(
                        rs.getLong("id_support_case"), rs.getString("category"), rs.getString("reason"),
                        rs.getString("summary"), rs.getString("priority"), rs.getString("status"),
                        rs.getString("assigned_user"), rs.getString("resolution_note"), dateTime(rs, "opened_at"),
                        dateTime(rs, "assigned_at"), dateTime(rs, "resolved_at")), conversationId);
    }

    private List<ToolCallView> findToolCalls(Long sessionId) {
        return jdbc.query("""
                SELECT id_agent_tool_call, id_agent_job, tool_name, status, request_json, response_json,
                       error_code, error_message, started_at, completed_at, created_at
                FROM crm_whatsapp_agent_tool_call
                WHERE id_agent_session = ?
                ORDER BY id_agent_tool_call DESC LIMIT 50
                """, (rs, row) -> new ToolCallView(
                        rs.getLong("id_agent_tool_call"), nullableLong(rs, "id_agent_job"),
                        rs.getString("tool_name"), rs.getString("status"), rs.getString("request_json"),
                        rs.getString("response_json"), rs.getString("error_code"), rs.getString("error_message"),
                        dateTime(rs, "started_at"), dateTime(rs, "completed_at"), dateTime(rs, "created_at")), sessionId);
    }

    private LocalDateTime dateTime(ResultSet rs, String column) throws SQLException {
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

    private String maskAccount(String value) {
        if (value == null || value.isBlank()) return null;
        String compact = value.replaceAll("\\s+", "");
        return compact.length() <= 4 ? compact : "•••• " + compact.substring(compact.length() - 4);
    }

    private String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }

    public record AgentConversationSummary(boolean hasSession, boolean toolHistoryVisible, SessionView session,
            CustomerView customer, Cart cart, PaymentView payment, Fulfillment fulfillment, SaleView sale,
            List<SupportCaseView> supportCases, List<ToolCallView> toolCalls, LocalDateTime generatedAt) {}

    public record SessionView(Long id, String key, String state, String resumeState, String lastEvent,
            String summary, boolean active, LocalDateTime startedAt, LocalDateTime lastActivityAt,
            LocalDateTime stateChangedAt, LocalDateTime completedAt) {}

    public record CustomerView(Integer id, String names, String phone, String documentType, String documentNumber,
            String email, String address) {}

    public record PaymentView(Long id, String status, String reservationStatus, String method,
            String maskedAccount, String accountHolder, BigDecimal expectedAmount, String currency,
            LocalDateTime reservedAt, LocalDateTime expiresAt, long remainingSeconds,
            LocalDateTime evidenceAcceptedAt, LocalDateTime reviewExpiresAt, LocalDateTime releasedAt, String releaseReason,
            PaymentEvidenceView evidence) {}

    public record PaymentEvidenceView(Long id, String validationStatus, String processingStatus,
            String detectedProvider, BigDecimal detectedAmount, String detectedCurrency, String operationCode,
            LocalDateTime operationAt, String recipient, Integer confidence, String extractedText,
            String warningsJson, String reviewNote, LocalDateTime reviewedAt, String fileName, String mimeType,
            String mediaUrl) {}

    public record SaleView(Integer id, String documentType, String series, Integer number, BigDecimal total,
            String currency, String status, String origin, LocalDateTime createdAt, String cancellationReason) {}

    public record SupportCaseView(Long id, String category, String reason, String summary, String priority,
            String status, String assignedUser, String resolutionNote, LocalDateTime openedAt,
            LocalDateTime assignedAt, LocalDateTime resolvedAt) {}

    public record ToolCallView(Long id, Long jobId, String name, String status, String requestJson,
            String responseJson, String errorCode, String errorMessage, LocalDateTime startedAt,
            LocalDateTime completedAt, LocalDateTime createdAt) {}
}
