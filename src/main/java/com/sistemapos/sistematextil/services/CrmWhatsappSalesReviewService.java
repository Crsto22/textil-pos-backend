package com.sistemapos.sistematextil.services;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.repositories.SucursalRepository;

import lombok.RequiredArgsConstructor;

/** Read model for the CRM sales and payment-review screen. */
@Service
@RequiredArgsConstructor
public class CrmWhatsappSalesReviewService {

    private final JdbcTemplate jdbc;
    private final SucursalRepository sucursalRepository;

    @Transactional(readOnly = true)
    public SalesPage list(Usuario actor, String query, String reviewStatus, int page, int size) {
        int companyId = companyId(actor);
        int safePage = Math.max(0, page);
        int safeSize = Math.min(100, Math.max(1, size));
        String term = query == null ? "" : query.trim();
        String status = reviewStatus == null ? "ALL" : reviewStatus.trim().toUpperCase();
        String like = "%" + term + "%";
        String reviewFilter = switch (status) {
            case "PENDING" -> " AND ((pe.validation_status = 'ACEPTABLE' AND pr.status = 'UNDER_REVIEW') OR fe.validation_status = 'ACEPTABLE' OR fa.status IN ('AWAITING_PREVIOUS_ORDER_DECISION','ADVISOR_REVIEW_REQUIRED','AWAITING_PAYMENT','PAYMENT_OVERDUE','PAYMENT_CONFIGURATION_REQUIRED')) ";
            case "ACCEPTED" -> " AND pe.validation_status = 'VALIDADO' AND (fe.id_film_evidence IS NULL OR fe.validation_status <> 'ACEPTABLE') AND (fa.id_film_addon IS NULL OR fa.status IN ('CONFIRMED','COVERED_BY_PREVIOUS_ORDER','ORDER_CANCELLED')) ";
            case "REJECTED" -> " AND (pe.validation_status = 'RECHAZADO' OR v.estado = 'ANULADA') ";
            default -> "";
        };
        String searchFilter = term.isBlank() ? "" : """
                AND (CONCAT(COALESCE(v.serie, ''), '-', LPAD(COALESCE(v.correlativo, 0), 6, '0')) LIKE ?
                     OR COALESCE(c.nombres, '') LIKE ? OR COALESCE(c.telefono, '') LIKE ?
                     OR COALESCE(pe.operation_code, '') LIKE ?)
                """;
        String from = """
                FROM venta v
                JOIN sucursal s ON s.id_sucursal = v.id_sucursal
                LEFT JOIN cliente c ON c.id_cliente = v.id_cliente
                LEFT JOIN crm_whatsapp_payment_request pr ON pr.id_payment_request = (
                    SELECT MAX(pr2.id_payment_request)
                    FROM crm_whatsapp_payment_request pr2
                    WHERE pr2.id_venta = v.id_venta)
                LEFT JOIN crm_whatsapp_agent_session ag ON ag.id_agent_session = pr.id_agent_session
                LEFT JOIN crm_whatsapp_conversation cv ON cv.id_conversation = pr.id_conversation
                LEFT JOIN metodo_pago_config mp ON mp.id_metodo_pago = pr.id_metodo_pago
                LEFT JOIN crm_whatsapp_fulfillment f ON f.id_fulfillment = (
                    SELECT MAX(f2.id_fulfillment)
                    FROM crm_whatsapp_fulfillment f2
                    WHERE f2.id_venta = v.id_venta
                       OR (f2.id_venta IS NULL AND f2.id_payment_request = pr.id_payment_request))
                LEFT JOIN crm_whatsapp_payment_evidence pe ON pe.id_payment_evidence = (
                    SELECT MAX(pe2.id_payment_evidence)
                    FROM crm_whatsapp_payment_evidence pe2
                    WHERE pe2.id_payment_request = pr.id_payment_request)
                LEFT JOIN crm_whatsapp_film_addon fa ON fa.id_venta = v.id_venta
                LEFT JOIN usuario fur ON fur.id_usuario = fa.resolved_by
                LEFT JOIN crm_whatsapp_film_evidence fe ON fe.id_film_evidence = (
                    SELECT MAX(fe2.id_film_evidence) FROM crm_whatsapp_film_evidence fe2
                    WHERE fe2.id_film_addon = fa.id_film_addon)
                WHERE s.id_empresa = ? AND v.origen = 'CRM_WHATSAPP'
                """ + searchFilter + reviewFilter;
        Object[] baseArgs = term.isBlank()
                ? new Object[] {companyId}
                : new Object[] {companyId, like, like, like, like};
        long total = jdbc.queryForObject("SELECT COUNT(DISTINCT v.id_venta) " + from, Long.class, baseArgs);
        String sql = """
                SELECT v.id_venta, v.tipo_comprobante, v.serie, v.correlativo, v.total, v.moneda,
                       v.estado, v.fecha, v.motivo_anulacion, s.nombre AS branch_name,
                       c.nombres AS customer_name, c.telefono AS customer_phone,
                       pr.id_payment_request, pr.status AS payment_request_status,
                       pe.id_payment_evidence, pe.validation_status, pe.processing_status,
                       pe.detected_amount, pe.detected_provider, pe.operation_code, pe.operation_at,
                       pe.confidence, pe.review_note, pe.reviewed_at, mp.nombre AS payment_method,
                       cv.id_conversation, cv.phone_number, ag.id_agent_session,
                       f.method AS delivery_method, f.shalom_agency, f.shalom_agency_address,
                       fa.id_film_addon, fa.status AS film_status, fa.amount AS film_amount,
                       fa.expires_at AS film_expires_at, fa.resolution_source AS film_resolution_source,
                       fa.covered_by_sale_id, fa.resolved_at AS film_resolved_at,
                       CONCAT_WS(' ', fur.nombre, fur.apellido) AS film_resolved_by,
                       fe.id_film_evidence, fe.validation_status AS film_validation_status,
                       fe.processing_status AS film_processing_status, fe.detected_amount AS film_detected_amount,
                       fe.operation_code AS film_operation_code, fe.operation_at AS film_operation_at,
                       fe.confidence AS film_confidence, fe.review_note AS film_review_note,
                       fe.reviewed_at AS film_reviewed_at
                """ + from + " ORDER BY v.fecha DESC, v.id_venta DESC LIMIT ? OFFSET ?";
        Object[] args = new Object[baseArgs.length + 2];
        System.arraycopy(baseArgs, 0, args, 0, baseArgs.length);
        args[args.length - 2] = safeSize;
        args[args.length - 1] = safePage * safeSize;
        List<SaleRow> rows = jdbc.query(sql, (rs, rowNum) -> mapSale(rs), args);
        return new SalesPage(rows, total, safePage, safeSize, (int) Math.ceil(total / (double) safeSize));
    }

    private SaleRow mapSale(ResultSet rs) throws SQLException {
        int saleId = rs.getInt("id_venta");
        Long evidenceId = nullableLong(rs, "id_payment_evidence");
        String mainEvidenceStatus=clean(rs.getString("validation_status"));
        String filmStatus=clean(rs.getString("film_status"));
        boolean unresolvedFilm=List.of("AWAITING_PREVIOUS_ORDER_DECISION","ADVISOR_REVIEW_REQUIRED",
                "AWAITING_PAYMENT","PAYMENT_OVERDUE","PAYMENT_CONFIGURATION_REQUIRED").contains(filmStatus);
        String reviewStatus = ("ACEPTABLE".equals(clean(rs.getString("film_validation_status")))||unresolvedFilm) ? "PENDING"
                : evidenceId == null ? "NO_EVIDENCE" : switch (mainEvidenceStatus) {
            case "VALIDADO" -> "ACCEPTED";
            case "RECHAZADO" -> "REJECTED";
            case "ACEPTABLE" -> "PENDING";
            default -> "PROCESSING";
        };
        return new SaleRow(
                saleId, rs.getString("tipo_comprobante"), saleNumber(rs), rs.getBigDecimal("total"),
                rs.getString("moneda"), rs.getString("estado"), dateTime(rs, "fecha"),
                rs.getString("motivo_anulacion"), rs.getString("branch_name"),
                rs.getString("customer_name"), rs.getString("customer_phone"),
                nullableLong(rs, "id_conversation"), rs.getString("phone_number"),
                nullableLong(rs, "id_agent_session") == null ? "CRM" : "IA_KIMENTS",
                nullableLong(rs, "id_payment_request"), rs.getString("payment_request_status"),
                evidenceId, mainEvidenceStatus, reviewStatus, rs.getString("processing_status"),
                rs.getBigDecimal("detected_amount"), rs.getString("detected_provider"),
                rs.getString("operation_code"), dateTime(rs, "operation_at"),
                nullableInteger(rs, "confidence"), rs.getString("review_note"),
                dateTime(rs, "reviewed_at"), rs.getString("payment_method"),
                rs.getString("delivery_method"),
                rs.getString("shalom_agency"), rs.getString("shalom_agency_address"),
                nullableLong(rs,"id_film_addon"),rs.getString("film_status"), rs.getBigDecimal("film_amount"),
                dateTime(rs,"film_expires_at"),rs.getString("film_resolution_source"),
                nullableLong(rs,"covered_by_sale_id"),dateTime(rs,"film_resolved_at"),rs.getString("film_resolved_by"),
                filmEvidence(rs),
                findItems(saleId));
    }

    private FilmEvidenceRow filmEvidence(ResultSet rs) throws SQLException {
        Long id=nullableLong(rs,"id_film_evidence"); if(id==null)return null;
        return new FilmEvidenceRow(id,rs.getString("film_validation_status"),rs.getString("film_processing_status"),
                rs.getBigDecimal("film_detected_amount"),rs.getString("film_operation_code"),
                dateTime(rs,"film_operation_at"),nullableInteger(rs,"film_confidence"),
                rs.getString("film_review_note"),dateTime(rs,"film_reviewed_at"),
                "/api/crm/whatsapp/film-evidences/"+id+"/media");
    }

    private List<SaleItem> findItems(int saleId) {
        return jdbc.query("""
                SELECT vd.descripcion, vd.cantidad, vd.precio_unitario, vd.descuento, vd.total_detalle
                FROM venta_detalle vd
                WHERE vd.id_venta = ? AND vd.deleted_at IS NULL
                ORDER BY vd.id_venta_detalle
                """, (rs, rowNum) -> new SaleItem(rs.getString("descripcion"), rs.getInt("cantidad"),
                        rs.getBigDecimal("precio_unitario"), rs.getBigDecimal("descuento"),
                        rs.getBigDecimal("total_detalle")), saleId);
    }

    private int companyId(Usuario actor) {
        if (actor != null && actor.getSucursal() != null && actor.getSucursal().getEmpresa() != null) {
            return actor.getSucursal().getEmpresa().getIdEmpresa();
        }
        return sucursalRepository.findByDeletedAtIsNullAndEstadoOrderByIdSucursalAsc("ACTIVO").stream()
                .filter(branch -> branch.getEmpresa() != null)
                .findFirst()
                .map(branch -> branch.getEmpresa().getIdEmpresa())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "No hay una empresa activa configurada"));
    }

    private String saleNumber(ResultSet rs) throws SQLException {
        String serie = clean(rs.getString("serie"));
        Integer correlativo = nullableInteger(rs, "correlativo");
        return serie.isBlank() ? String.valueOf(correlativo == null ? "" : correlativo)
                : serie + "-" + String.format("%06d", correlativo == null ? 0 : correlativo);
    }

    private LocalDateTime dateTime(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
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

    private String clean(String value) { return value == null ? "" : value.trim().toUpperCase(); }

    public record SalesPage(List<SaleRow> content, long totalElements, int page, int size, int totalPages) {}
    public record SaleRow(Integer saleId, String receiptType, String number, BigDecimal total, String currency,
            String saleStatus, LocalDateTime createdAt, String cancellationReason, String branch,
            String customerName, String customerPhone, Long conversationId, String whatsappPhone,
            String source, Long paymentRequestId, String paymentRequestStatus, Long evidenceId,
            String mainEvidenceStatus, String reviewStatus, String processingStatus, BigDecimal detectedAmount, String detectedProvider,
            String operationCode, LocalDateTime operationAt, Integer confidence, String reviewNote,
            LocalDateTime reviewedAt, String paymentMethod, String deliveryMethod,
            String shalomAgency, String shalomAgencyAddress, Long filmAddonId, String filmStatus, BigDecimal filmAmount,
            LocalDateTime filmExpiresAt, String filmResolutionSource, Long coveredBySaleId,
            LocalDateTime filmResolvedAt, String filmResolvedBy, FilmEvidenceRow filmEvidence, List<SaleItem> items) {}
    public record FilmEvidenceRow(Long evidenceId,String validationStatus,String processingStatus,
            BigDecimal detectedAmount,String operationCode,LocalDateTime operationAt,Integer confidence,
            String reviewNote,LocalDateTime reviewedAt,String mediaUrl) {}
    public record SaleItem(String description, Integer quantity, BigDecimal unitPrice, BigDecimal discount,
            BigDecimal total) {}
}
