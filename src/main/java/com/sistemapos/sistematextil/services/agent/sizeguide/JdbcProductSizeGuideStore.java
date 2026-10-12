package com.sistemapos.sistematextil.services.agent.sizeguide;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import com.sistemapos.sistematextil.services.agent.sizeguide.ProductSizeGuideValidator.ValidatedGuide;
import com.sistemapos.sistematextil.services.agent.sizeguide.ProductSizeGuideVisionGateway.VisionResult;

@Repository
public class JdbcProductSizeGuideStore {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public JdbcProductSizeGuideStore(JdbcTemplate jdbc, TransactionTemplate transactions) {
        this.jdbc = jdbc;
        this.transactions = transactions;
    }

    public List<ProductSource> productsWithGuide() {
        return jdbc.query("""
                SELECT producto_id, nombre, guia_tallas_url
                FROM producto
                WHERE deleted_at IS NULL AND guia_tallas_url IS NOT NULL AND TRIM(guia_tallas_url) <> ''
                ORDER BY producto_id
                """, (rs, row) -> new ProductSource(rs.getInt(1), rs.getString(2), rs.getString(3)));
    }

    public boolean alreadyTracksHash(Integer productId, String sourceHash) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM (
                  SELECT source_hash FROM producto_guia_talla
                  WHERE producto_id=? AND source_hash=? AND estado IN ('ACTIVE','SUPERSEDED')
                  UNION ALL
                  SELECT source_hash FROM producto_guia_talla_job WHERE producto_id=? AND source_hash=?
                ) tracked
                """, Integer.class, productId, sourceHash, productId, sourceHash);
        return count != null && count > 0;
    }

    public void enqueue(Integer productId, Long connectionId, String sourceUrl, String sourceHash, boolean force) {
        LocalDateTime now = LocalDateTime.now();
        int updated = jdbc.update("""
                INSERT INTO producto_guia_talla_job
                  (producto_id,id_connection,source_url,source_hash,status,attempts,available_at,created_at,updated_at)
                VALUES (?,?,?,?, 'PENDING',0,?,?,?)
                ON DUPLICATE KEY UPDATE
                  id_connection=VALUES(id_connection), source_url=VALUES(source_url),
                  status=IF(?,'PENDING',status), attempts=IF(? ,0,attempts),
                  available_at=IF(?,VALUES(available_at),available_at),
                  locked_by=IF(?,NULL,locked_by), lease_until=IF(?,NULL,lease_until),
                  last_error=IF(?,NULL,last_error), completed_at=IF(?,NULL,completed_at), updated_at=VALUES(updated_at)
                """, productId, connectionId, sourceUrl, sourceHash, now, now, now,
                force, force, force, force, force, force, force);
        if (updated < 1) throw new IllegalStateException("No se pudo encolar la guia de tallas");
    }

    public ClaimedJob claim(String worker, int maxAttempts) {
        return transactions.execute(status -> {
            LocalDateTime now = LocalDateTime.now();
            jdbc.update("""
                    UPDATE producto_guia_talla_job
                    SET status=IF(attempts>=?,'FAILED','RETRY'), locked_by=NULL, lease_until=NULL,
                        available_at=?, updated_at=?, last_error=COALESCE(last_error,'Lease vencido')
                    WHERE status='PROCESSING' AND lease_until < ?
                    """, maxAttempts, now, now, now);
            List<ClaimedJob> jobs = jdbc.query("""
                    SELECT id_job,producto_id,id_connection,source_url,source_hash,attempts
                    FROM producto_guia_talla_job
                    WHERE status IN ('PENDING','RETRY') AND available_at<=? AND attempts<?
                    ORDER BY available_at,id_job LIMIT 1 FOR UPDATE SKIP LOCKED
                    """, (rs, row) -> claimed(rs), now, maxAttempts);
            if (jobs.isEmpty()) return null;
            ClaimedJob job = jobs.getFirst();
            jdbc.update("""
                    UPDATE producto_guia_talla_job SET status='PROCESSING',attempts=attempts+1,
                      locked_by=?,lease_until=?,updated_at=? WHERE id_job=?
                    """, worker, now.plusMinutes(3), now, job.jobId());
            return new ClaimedJob(job.jobId(), job.productId(), job.connectionId(), job.sourceUrl(),
                    job.sourceHash(), job.attempts() + 1);
        });
    }

    public void complete(ClaimedJob job, VisionResult vision, ValidatedGuide guide) {
        transactions.executeWithoutResult(status -> {
            Integer nextVersion = jdbc.queryForObject(
                    "SELECT COALESCE(MAX(version),0)+1 FROM producto_guia_talla WHERE producto_id=?",
                    Integer.class, job.productId());
            jdbc.update("""
                    INSERT INTO producto_guia_talla
                      (producto_id,source_url,source_hash,version,estado,tipo_medicion,unidad,source_unit,
                       descripcion,confianza,provider,model,credential_source,latency_ms,analyzed_at,created_at,updated_at)
                    VALUES (?,?,?,?, 'ACTIVE',?,'CM',?,?,?,?,?,?,?,NOW(6),NOW(6),NOW(6))
                    ON DUPLICATE KEY UPDATE source_url=VALUES(source_url),estado='ACTIVE',
                      tipo_medicion=VALUES(tipo_medicion),unidad='CM',source_unit=VALUES(source_unit),
                      descripcion=VALUES(descripcion),confianza=VALUES(confianza),provider=VALUES(provider),
                      model=VALUES(model),credential_source=VALUES(credential_source),latency_ms=VALUES(latency_ms),
                      analyzed_at=NOW(6),updated_at=NOW(6)
                    """, job.productId(), job.sourceUrl(), job.sourceHash(), nextVersion,
                    guide.measurementType(), guide.sourceUnit(), nullIfBlank(guide.description()), guide.confidence(),
                    vision.provider(), vision.model(), vision.credentialSource(), vision.latencyMs());
            Long guideId = jdbc.queryForObject("""
                    SELECT id_guia FROM producto_guia_talla WHERE producto_id=? AND source_hash=?
                    """, Long.class, job.productId(), job.sourceHash());
            jdbc.update("UPDATE producto_guia_talla SET estado='SUPERSEDED' WHERE producto_id=? AND id_guia<>? AND estado='ACTIVE'",
                    job.productId(), guideId);
            jdbc.update("DELETE FROM producto_guia_talla_componente WHERE id_guia=?", guideId);
            int componentOrder = 0;
            for (var component : guide.extraction().components()) {
                String componentCode = ProductSizeGuideValidator.normalizedCode(component.name());
                jdbc.update("""
                        INSERT INTO producto_guia_talla_componente
                          (id_guia,nombre,nombre_normalizado,descripcion,orden,created_at)
                        VALUES (?,?,?,?,?,NOW(6))
                        """, guideId, component.name().trim(), componentCode,
                        nullIfBlank(component.description()), componentOrder++);
                Long componentId = jdbc.queryForObject("""
                        SELECT id_componente FROM producto_guia_talla_componente
                        WHERE id_guia=? AND nombre_normalizado=?
                        """, Long.class, guideId, componentCode);
                for (var row : component.measurements()) {
                    String measureCode = ProductSizeGuideValidator.normalizedCode(row.measurementName());
                    Long typeId = ensureMeasurementType(measureCode, row.measurementName());
                    String size = row.sizeLabel().trim().toUpperCase(Locale.ROOT);
                    Integer sizeId = findSizeId(size);
                    jdbc.update("""
                            INSERT INTO producto_guia_talla_medida
                              (id_componente,talla_id,talla_etiqueta,id_tipo_medida,valor_referencia_cm,
                               valor_min_cm,valor_max_cm,tolerancia_cm,valor_original,created_at)
                            VALUES (?,?,?,?,?,?,?,?,?,NOW(6))
                            """, componentId, sizeId, size, typeId,
                            centimeters(row.referenceValue(), guide.sourceUnit()),
                            centimeters(row.minimumValue(), guide.sourceUnit()),
                            centimeters(row.maximumValue(), guide.sourceUnit()),
                            centimeters(row.toleranceValue(), guide.sourceUnit()),
                            nullIfBlank(row.originalValue()));
                }
            }
            jdbc.update("""
                    UPDATE producto_guia_talla_job SET status='COMPLETED',locked_by=NULL,lease_until=NULL,
                      last_error=NULL,completed_at=NOW(6),updated_at=NOW(6) WHERE id_job=?
                    """, job.jobId());
        });
    }

    public void fail(ClaimedJob job, Throwable error, int maxAttempts) {
        boolean terminal = job.attempts() >= maxAttempts;
        LocalDateTime next = LocalDateTime.now().plusSeconds(Math.min(300, 15L * job.attempts() * job.attempts()));
        jdbc.update("""
                UPDATE producto_guia_talla_job SET status=?,available_at=?,locked_by=NULL,lease_until=NULL,
                  last_error=?,updated_at=NOW(6) WHERE id_job=?
                """, terminal ? "FAILED" : "RETRY", next, truncate(error), job.jobId());
    }

    public GuideStatus status(Integer productId) {
        List<GuideStatus> rows = jdbc.query("""
                SELECT p.producto_id,p.guia_tallas_url,
                  CASE WHEN j.status IN ('PENDING','RETRY','PROCESSING')
                              AND (g.id_guia IS NULL OR j.source_hash<>g.source_hash) THEN 'PROCESSING'
                       WHEN j.status='FAILED' AND (g.id_guia IS NULL OR j.source_hash<>g.source_hash) THEN 'FAILED'
                       WHEN g.id_guia IS NOT NULL AND g.estado='ACTIVE'
                            AND g.source_url COLLATE utf8mb4_general_ci=p.guia_tallas_url THEN 'AVAILABLE'
                       WHEN g.id_guia IS NOT NULL AND g.estado='FAILED'
                            AND g.source_url COLLATE utf8mb4_general_ci=p.guia_tallas_url THEN 'FAILED'
                       WHEN p.guia_tallas_url IS NOT NULL AND TRIM(p.guia_tallas_url)<>'' THEN 'PROCESSING'
                       ELSE 'NOT_ANALYZED' END analysis_status,
                  g.tipo_medicion,g.analyzed_at,g.confianza,j.last_error
                FROM producto p
                LEFT JOIN producto_guia_talla g ON g.id_guia=(
                  SELECT g2.id_guia FROM producto_guia_talla g2 WHERE g2.producto_id=p.producto_id
                  ORDER BY (g2.estado='ACTIVE') DESC,g2.id_guia DESC LIMIT 1)
                LEFT JOIN producto_guia_talla_job j ON j.id_job=(
                  SELECT j2.id_job FROM producto_guia_talla_job j2 WHERE j2.producto_id=p.producto_id
                  ORDER BY j2.id_job DESC LIMIT 1)
                WHERE p.producto_id=?
                """, (rs, row) -> new GuideStatus(rs.getInt(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getTimestamp(5) == null ? null : rs.getTimestamp(5).toLocalDateTime(),
                        (Integer) rs.getObject(6), rs.getString(7)), productId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private ClaimedJob claimed(ResultSet rs) throws SQLException {
        return new ClaimedJob(rs.getLong(1), rs.getInt(2), (Long) rs.getObject(3), rs.getString(4),
                rs.getString(5), rs.getInt(6));
    }

    private Long ensureMeasurementType(String code, String label) {
        jdbc.update("""
                INSERT INTO guia_talla_tipo_medida(codigo,nombre,activo,created_at,updated_at)
                VALUES (?,?,1,NOW(6),NOW(6)) ON DUPLICATE KEY UPDATE nombre=VALUES(nombre),updated_at=NOW(6)
                """, code, label.trim());
        return jdbc.queryForObject("SELECT id_tipo_medida FROM guia_talla_tipo_medida WHERE codigo=?", Long.class, code);
    }

    private Integer findSizeId(String size) {
        List<Integer> ids = jdbc.query("""
                SELECT talla_id FROM tallas WHERE deleted_at IS NULL AND UPPER(TRIM(nombre))=? LIMIT 1
                """, (rs, row) -> rs.getInt(1), size);
        return ids.isEmpty() ? null : ids.getFirst();
    }

    private BigDecimal centimeters(BigDecimal value, String unit) {
        if (value == null) return null;
        return ("IN".equals(unit) ? value.multiply(new BigDecimal("2.54")) : value)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private String nullIfBlank(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String truncate(Throwable error) {
        String message = error == null || error.getMessage() == null ? "Error desconocido" : error.getMessage();
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }

    public record ProductSource(Integer productId, String productName, String sourceUrl) {}
    public record ClaimedJob(Long jobId, Integer productId, Long connectionId, String sourceUrl,
            String sourceHash, int attempts) {}
    public record GuideStatus(Integer productId, String sourceUrl, String analysisStatus,
            String measurementType, LocalDateTime analyzedAt, Integer confidence, String error) {}
}
