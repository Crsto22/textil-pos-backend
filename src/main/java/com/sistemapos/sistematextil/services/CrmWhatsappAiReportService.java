package com.sistemapos.sistematextil.services;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.util.usuario.Rol;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CrmWhatsappAiReportService {
    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public AiReportResponse report(String filter, LocalDate requestedFrom, LocalDate requestedTo, Usuario actor) {
        if (actor == null || actor.getSucursal() == null || actor.getSucursal().getEmpresa() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autorizado");
        }
        Range range = range(filter, requestedFrom, requestedTo);
        Integer companyId = actor.getSucursal().getEmpresa().getIdEmpresa();
        boolean admin = actor.getRol() == Rol.ADMINISTRADOR;
        Scope scope = new Scope(companyId, admin ? null : actor.getIdUsuario(), range.from().atStartOfDay(), range.to().plusDays(1).atStartOfDay());

        long autoSent = scalar("SELECT COUNT(*) FROM crm_whatsapp_ai_delivery d " + joins("d.id_conversation")
                + where(scope, "d.sent_at") + " AND d.status='SENT'", scope);
        long autoFailed = scalar("SELECT COUNT(*) FROM crm_whatsapp_ai_delivery d " + joins("d.id_conversation")
                + where(scope, "d.created_at") + " AND d.status='FAILED'", scope);
        long autoCancelled = scalar("SELECT COUNT(*) FROM crm_whatsapp_ai_delivery d " + joins("d.id_conversation")
                + where(scope, "d.created_at") + " AND d.status='CANCELLED'", scope);
        long suggestions = scalar("SELECT COUNT(*) FROM crm_whatsapp_ai_run r JOIN crm_whatsapp_ai_job j ON j.id_ai_job=r.id_ai_job "
                + joins("r.id_conversation") + where(scope, "r.created_at") + " AND j.trigger_type='MANUAL' AND r.outcome='DRAFT_READY'", scope);
        Map<String, Long> feedback = grouped("SELECT f.decision label, COUNT(*) value FROM crm_whatsapp_ai_feedback f "
                + "JOIN crm_whatsapp_ai_run r ON r.id_ai_run=f.id_ai_run " + joins("r.id_conversation")
                + where(scope, "f.created_at") + " GROUP BY f.decision", scope);
        long transfers = scalar("SELECT COUNT(*) FROM crm_whatsapp_ai_run r " + joins("r.id_conversation")
                + where(scope, "r.created_at") + " AND r.requires_human=TRUE", scope);
        long unresolved = scalar("SELECT COUNT(*) FROM crm_whatsapp_conversation c JOIN crm_whatsapp_connection conn ON conn.id_connection=c.id_connection "
                + "JOIN crm_whatsapp_ai_run r ON r.id_ai_run=(SELECT MAX(r2.id_ai_run) FROM crm_whatsapp_ai_run r2 WHERE r2.id_conversation=c.id_conversation) "
                + whereConversation(scope, "r.created_at") + " AND c.status='ESPERA' AND r.outcome IN ('HUMAN_REQUIRED','FAILED')", scope);
        long prepared = scalar("SELECT COUNT(*) FROM crm_whatsapp_ai_sale_draft s " + joins("s.id_conversation")
                + where(scope, "s.created_at"), scope);
        long confirmed = scalar("SELECT COUNT(*) FROM crm_whatsapp_ai_sale_draft s " + joins("s.id_conversation")
                + where(scope, "s.customer_confirmed_at") + " AND s.customer_confirmed_at IS NOT NULL", scope);
        List<Map<String, Object>> saleRow = rows("SELECT COUNT(*) cantidad, COALESCE(SUM(v.total),0) monto FROM crm_whatsapp_ai_sale_draft s "
                + "JOIN venta v ON v.id_venta=s.id_venta " + joins("s.id_conversation")
                + where(scope, "s.updated_at") + " AND s.status='COMPLETED' AND v.deleted_at IS NULL", scope);
        long assistedSales = saleRow.isEmpty() ? 0 : number(saleRow.get(0).get("cantidad")).longValue();
        BigDecimal assistedAmount = saleRow.isEmpty() ? BigDecimal.ZERO : decimal(saleRow.get(0).get("monto"));
        List<Map<String, Object>> usageRow = rows("SELECT COALESCE(SUM(r.total_tokens),0) tokens, COALESCE(SUM(r.estimated_cost_usd),0) cost, "
                + "COALESCE(AVG(r.latency_ms),0) latency, SUM(CASE WHEN r.outcome='FAILED' THEN 1 ELSE 0 END) failures, COUNT(*) runs "
                + "FROM crm_whatsapp_ai_run r " + joins("r.id_conversation") + where(scope, "r.created_at"), scope);
        Map<String, Object> usage = usageRow.isEmpty() ? Map.of() : usageRow.get(0);
        long reviewed = feedback.values().stream().mapToLong(Long::longValue).sum();
        long approved = feedback.getOrDefault("APPROVED", 0L);
        long edited = feedback.getOrDefault("EDITED", 0L);
        List<Map<String, Object>> editRows = rows("SELECT COALESCE(AVG(100-f.similarity_percentage),0) value FROM crm_whatsapp_ai_feedback f "
                + "JOIN crm_whatsapp_ai_run r ON r.id_ai_run=f.id_ai_run " + joins("r.id_conversation")
                + where(scope, "f.created_at") + " AND f.decision='EDITED'", scope);
        BigDecimal averageEdit = editRows.isEmpty() ? BigDecimal.ZERO : decimal(editRows.get(0).get("value"));
        List<CategoryItem> security = auditDistribution(scope);
        long securityBlocks = security.stream().mapToLong(CategoryItem::value).sum();

        return new AiReportResponse(range.filter(), range.from(), range.to(), admin,
                new AiKpis(autoSent, autoFailed, autoCancelled, suggestions, approved, edited,
                        feedback.getOrDefault("DISCARDED", 0L), percentage(approved, reviewed), averageEdit, transfers, unresolved, securityBlocks,
                        prepared, confirmed, assistedSales, assistedAmount, number(usage.get("tokens")).longValue(),
                        admin ? decimal(usage.get("cost")) : null, number(usage.get("latency")).longValue(),
                        percentage(number(usage.get("failures")).longValue(), number(usage.get("runs")).longValue())),
                timeline(scope), categories("r.intent", "crm_whatsapp_ai_run r", "r.id_conversation", "r.created_at", scope, "r.intent IS NOT NULL"),
                categories("r.reason", "crm_whatsapp_ai_run r", "r.id_conversation", "r.created_at", scope, "r.requires_human=TRUE"),
                productRanking(scope), paymentDistribution(scope), outcomeDistribution(scope), security);
    }

    private List<SeriesPoint> timeline(Scope s) {
        String sql = "SELECT DATE(r.created_at) fecha, COUNT(*) value FROM crm_whatsapp_ai_run r " + joins("r.id_conversation")
                + where(s, "r.created_at") + " GROUP BY DATE(r.created_at) ORDER BY fecha";
        return rows(sql, s).stream().map(row -> new SeriesPoint(String.valueOf(row.get("fecha")), number(row.get("value")).longValue())).toList();
    }
    private List<CategoryItem> productRanking(Scope s) {
        String sql = "SELECT p.nombre label, COUNT(*) value FROM crm_whatsapp_ai_product_query q JOIN producto p ON p.producto_id=q.id_producto "
                + joins("q.id_conversation") + where(s, "q.created_at") + " GROUP BY p.producto_id,p.nombre ORDER BY value DESC LIMIT 10";
        return categoryRows(sql, s);
    }
    private List<CategoryItem> paymentDistribution(Scope s) {
        String sql = "SELECT e.validation_status label, COUNT(*) value FROM crm_whatsapp_payment_evidence e " + joins("e.id_conversation")
                + where(s, "e.created_at") + " GROUP BY e.validation_status ORDER BY value DESC";
        return categoryRows(sql, s);
    }
    private List<CategoryItem> outcomeDistribution(Scope s) {
        return categories("r.outcome", "crm_whatsapp_ai_run r", "r.id_conversation", "r.created_at", s, "1=1");
    }
    private List<CategoryItem> auditDistribution(Scope s) {
        String sql = "SELECT a.event_type label, COUNT(*) value FROM crm_whatsapp_ai_audit_event a "
                + "JOIN crm_whatsapp_connection conn ON conn.id_connection=a.id_connection "
                + "LEFT JOIN crm_whatsapp_conversation c ON c.id_conversation=a.id_conversation "
                + "WHERE conn.id_empresa=? AND a.created_at>=? AND a.created_at<? "
                + (s.userId()==null ? "" : "AND c.assigned_user_id=? ")
                + "AND a.event_type IN ('INPUT_BLOCKED','OUTPUT_BLOCKED','LIMIT_BLOCKED','RATE_LIMIT_BLOCKED','PROVIDER_ERROR') "
                + "GROUP BY a.event_type ORDER BY value DESC";
        return categoryRows(sql, s);
    }
    private List<CategoryItem> categories(String column, String table, String conversationColumn, String dateColumn, Scope s, String extra) {
        return categoryRows("SELECT " + column + " label, COUNT(*) value FROM " + table + " " + joins(conversationColumn)
                + where(s, dateColumn) + " AND " + extra + " GROUP BY " + column + " ORDER BY value DESC LIMIT 10", s);
    }
    private List<CategoryItem> categoryRows(String sql, Scope s) {
        return rows(sql, s).stream().map(row -> new CategoryItem(String.valueOf(row.get("label")), number(row.get("value")).longValue())).toList();
    }
    private Map<String, Long> grouped(String sql, Scope s) {
        Map<String, Long> result = new LinkedHashMap<>();
        rows(sql, s).forEach(row -> result.put(String.valueOf(row.get("label")), number(row.get("value")).longValue()));
        return result;
    }
    private String joins(String id) { return "JOIN crm_whatsapp_conversation c ON c.id_conversation=" + id + " JOIN crm_whatsapp_connection conn ON conn.id_connection=c.id_connection "; }
    private String where(Scope s, String date) { return "WHERE conn.id_empresa=? AND " + date + ">=? AND " + date + "<? " + (s.userId()==null?"":"AND c.assigned_user_id=? "); }
    private String whereConversation(Scope s, String date) { return "WHERE conn.id_empresa=? AND " + date + ">=? AND " + date + "<? " + (s.userId()==null?"":"AND c.assigned_user_id=? "); }
    private Object[] args(Scope s) { return s.userId()==null ? new Object[]{s.companyId(),s.from(),s.to()} : new Object[]{s.companyId(),s.from(),s.to(),s.userId()}; }
    private long scalar(String sql, Scope s) { Long value=jdbc.queryForObject(sql, Long.class, args(s)); return value==null?0:value; }
    private List<Map<String,Object>> rows(String sql, Scope s) { return jdbc.queryForList(sql, args(s)); }
    private Number number(Object value) { return value instanceof Number n ? n : 0; }
    private BigDecimal decimal(Object value) { return value==null?BigDecimal.ZERO:new BigDecimal(value.toString()); }
    private BigDecimal percentage(long value,long total) { return total==0?BigDecimal.ZERO:BigDecimal.valueOf(value*100.0/total).setScale(2,java.math.RoundingMode.HALF_UP); }
    private Range range(String filter, LocalDate from, LocalDate to) {
        LocalDate today=LocalDate.now();
        if(from!=null&&to!=null){if(from.isAfter(to))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"La fecha inicial no puede superar la final");return new Range("PERSONALIZADO",from,to);}
        String normalized=filter==null?"HOY":filter.toUpperCase(Locale.ROOT);
        int days=switch(normalized){case "ULT_7_DIAS"->7;case "ULT_14_DIAS"->14;case "ULT_30_DIAS"->30;default->1;};
        return new Range(days==1?"HOY":normalized,today.minusDays(days-1L),today);
    }
    private record Scope(Integer companyId,Integer userId,LocalDateTime from,LocalDateTime to){}
    private record Range(String filter,LocalDate from,LocalDate to){}
    public record AiReportResponse(String filtro,LocalDate desde,LocalDate hasta,boolean admin,AiKpis kpis,List<SeriesPoint> actividad,List<CategoryItem> intenciones,List<CategoryItem> transferencias,List<CategoryItem> productos,List<CategoryItem> pagos,List<CategoryItem> resultados,List<CategoryItem> seguridad){}
    public record AiKpis(long respuestasAutomaticas,long respuestasFallidas,long respuestasCanceladas,long sugerencias,long aprobadas,long editadas,long descartadas,BigDecimal tasaAprobacion,BigDecimal promedioEdicion,long transferencias,long sinResolver,long bloqueosSeguridad,long pedidosPreparados,long pedidosConfirmados,long ventasAsistidas,BigDecimal montoVentasAsistidas,long tokensTotales,BigDecimal costoEstimadoUsd,long latenciaPromedioMs,BigDecimal tasaError){}
    public record SeriesPoint(String fecha,long valor){}
    public record CategoryItem(String label,long value){}
}
