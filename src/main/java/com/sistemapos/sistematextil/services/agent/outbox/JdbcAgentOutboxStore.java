package com.sistemapos.sistematextil.services.agent.outbox;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcAgentOutboxStore implements AgentOutboxStore {
    private static final Set<String> TYPES = Set.of("TEXT", "IMAGE", "DOCUMENT");
    private final JdbcTemplate jdbc;

    public JdbcAgentOutboxStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public OutboxEntry enqueue(EnqueueCommand raw) {
        EnqueueCommand command = validate(raw);
        SessionRow session = lockSession(command.sessionId());
        RuntimeLimits limits = runtimeLimits(session.connectionId());
        if (!limits.enabled()) throw new IllegalStateException("El agente esta desactivado");
        validateJob(command.jobId(), command.sessionId());
        String idempotencyKey = sha256("agent-outbox-v1|" + command.sessionId() + "|"
                + (command.jobId() == null ? "none" : command.jobId()) + "|" + command.ordinal()
                + "|" + command.messageType());
        String payloadHash = sha256(String.join("|", command.messageType(), safe(command.body()),
                safe(command.mediaStoragePath()), safe(command.mediaFileName()), safe(command.mediaMimeType()),
                safe(command.caption()), String.valueOf(command.replyToMessageId())));
        Optional<StoredRow> existing = findStoredByKey(idempotencyKey);
        if (existing.isPresent()) return validateExisting(existing.get(), payloadHash);
        if (command.jobId() != null && !AgentOutboxService.OPERATIONAL_ORIGIN_MARKER.equals(command.caption())) {
            Long outputCount = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM crm_whatsapp_agent_outbox
                    WHERE id_agent_job=? AND (caption IS NULL OR caption <> 'CRM_SYSTEM')
                    """, Long.class, command.jobId());
            if (outputCount != null && outputCount >= limits.maxOutputs()) {
                throw new IllegalStateException("La ejecucion alcanzo su limite de salidas");
            }
        }

        Long next = jdbc.queryForObject("""
                SELECT COALESCE(MAX(sequence_no), 0) + 1
                FROM crm_whatsapp_agent_outbox
                WHERE id_conversation = ?
                """, Long.class, session.conversationId());
        LocalDateTime now = LocalDateTime.now();
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        String sql = """
                INSERT INTO crm_whatsapp_agent_outbox
                  (id_agent_session, id_agent_job, id_conversation, reply_to_message_id,
                   idempotency_key, payload_hash, sequence_no, message_type, body,
                   media_storage_path, media_file_name, media_mime_type, caption,
                   status, attempts, available_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', 0, ?, ?, ?)
                """;
        try {
            jdbc.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                int i = 1;
                ps.setObject(i++, command.sessionId()); ps.setObject(i++, command.jobId());
                ps.setObject(i++, session.conversationId()); ps.setObject(i++, command.replyToMessageId());
                ps.setString(i++, idempotencyKey); ps.setString(i++, payloadHash); ps.setObject(i++, next);
                ps.setString(i++, command.messageType()); ps.setString(i++, command.body());
                ps.setString(i++, command.mediaStoragePath()); ps.setString(i++, command.mediaFileName());
                ps.setString(i++, command.mediaMimeType()); ps.setString(i++, command.caption());
                ps.setObject(i++, now); ps.setObject(i++, now); ps.setObject(i, now);
                return ps;
            }, keys);
        } catch (DuplicateKeyException duplicate) {
            return validateExisting(findStoredByKey(idempotencyKey).orElseThrow(() -> duplicate), payloadHash);
        }
        Number id = keys.getKey();
        if (id == null) throw new IllegalStateException("No se pudo obtener el id del outbox");
        return findStored(id.longValue()).orElseThrow().entry();
    }

    @Override
    @Transactional
    public Optional<OutboxLease> claimNext(String workerId, LocalDateTime now, LocalDateTime leaseUntil) {
        List<Long> ids = jdbc.query("""
                SELECT o.id_agent_outbox
                FROM crm_whatsapp_agent_outbox o
                JOIN crm_whatsapp_agent_session s ON s.id_agent_session=o.id_agent_session
                JOIN crm_whatsapp_ai_config cfg ON cfg.id_connection=s.id_connection AND cfg.agent_enabled=TRUE
                WHERE o.status IN ('PENDING', 'RETRY') AND o.available_at <= ?
                  AND NOT EXISTS (
                    SELECT 1 FROM crm_whatsapp_agent_outbox before_o
                    WHERE before_o.id_conversation = o.id_conversation
                      AND before_o.sequence_no < o.sequence_no
                      AND before_o.status IN ('PENDING', 'RETRY', 'PROCESSING'))
                  AND (o.id_agent_job IS NULL OR EXISTS (
                    SELECT 1 FROM crm_whatsapp_agent_job j
                    WHERE j.id_agent_job = o.id_agent_job AND j.obsolete_at IS NULL
                      AND j.status = 'COMPLETED'))
                ORDER BY o.available_at, o.id_conversation, o.sequence_no
                LIMIT 1 FOR UPDATE SKIP LOCKED
                """, (rs, row) -> rs.getLong(1), now);
        if (ids.isEmpty()) return Optional.empty();
        Long id = ids.getFirst();
        if (jdbc.update("""
                UPDATE crm_whatsapp_agent_outbox
                SET status='PROCESSING', attempts=attempts+1, locked_by=?, lease_until=?, updated_at=?
                WHERE id_agent_outbox=? AND status IN ('PENDING','RETRY')
                """, workerId, leaseUntil, now, id) != 1) return Optional.empty();
        List<OutboxLease> rows = jdbc.query("""
                SELECT o.*, c.phone
                FROM crm_whatsapp_agent_outbox o
                JOIN crm_whatsapp_conversation c ON c.id_conversation=o.id_conversation
                WHERE o.id_agent_outbox=? AND o.status='PROCESSING' AND o.locked_by=?
                """, (rs, row) -> new OutboxLease(mapEntry(rs), rs.getInt("attempts"), workerId,
                        timestamp(rs, "lease_until"), rs.getString("phone")), id, workerId);
        return rows.stream().findFirst();
    }

    @Override
    public boolean complete(Long id, String workerId, String wamid, Long messageId, LocalDateTime sentAt) {
        return jdbc.update("""
                UPDATE crm_whatsapp_agent_outbox
                SET status='SENT', whatsapp_message_id=?, id_message=?, sent_at=?,
                    locked_by=NULL, lease_until=NULL, last_error=NULL, updated_at=?
                WHERE id_agent_outbox=? AND status='PROCESSING' AND locked_by=?
                """, wamid, messageId, sentAt, sentAt, id, workerId) == 1;
    }

    @Override
    @Transactional
    public FailureResult failOrRetry(Long id, String workerId, LocalDateTime now, LocalDateTime retryAt,
            int maxAttempts, String error, boolean retryable) {
        List<Integer> attempts = jdbc.query("""
                SELECT attempts FROM crm_whatsapp_agent_outbox
                WHERE id_agent_outbox=? AND status='PROCESSING' AND locked_by=? FOR UPDATE
                """, (rs, row) -> rs.getInt(1), id, workerId);
        if (attempts.isEmpty()) return FailureResult.IGNORED_STALE_LEASE;
        String message = truncate(error, 1000);
        if (!retryable || attempts.getFirst() >= maxAttempts) {
            jdbc.update("""
                    UPDATE crm_whatsapp_agent_outbox
                    SET status='FAILED', failed_at=?, last_error=?, locked_by=NULL, lease_until=NULL, updated_at=?
                    WHERE id_agent_outbox=? AND status='PROCESSING' AND locked_by=?
                    """, now, message, now, id, workerId);
            return FailureResult.FAILED_PERMANENTLY;
        }
        jdbc.update("""
                UPDATE crm_whatsapp_agent_outbox
                SET status='RETRY', available_at=?, last_error=?, locked_by=NULL, lease_until=NULL, updated_at=?
                WHERE id_agent_outbox=? AND status='PROCESSING' AND locked_by=?
                """, retryAt, message, now, id, workerId);
        return FailureResult.RETRY_SCHEDULED;
    }

    @Override
    @Transactional
    public RecoveryResult recoverExpired(LocalDateTime now, int maxAttempts) {
        int failed = jdbc.update("""
                UPDATE crm_whatsapp_agent_outbox SET status='FAILED', failed_at=?, locked_by=NULL,
                  lease_until=NULL, last_error='Lease vencido y limite de reintentos alcanzado', updated_at=?
                WHERE status='PROCESSING' AND lease_until < ? AND attempts >= ?
                """, now, now, now, maxAttempts);
        int recovered = jdbc.update("""
                UPDATE crm_whatsapp_agent_outbox SET status='RETRY', available_at=?, locked_by=NULL,
                  lease_until=NULL, last_error='Lease vencido; entrega recuperada', updated_at=?
                WHERE status='PROCESSING' AND lease_until < ? AND attempts < ?
                """, now, now, now, maxAttempts);
        return new RecoveryResult(recovered, failed);
    }

    private SessionRow lockSession(Long sessionId) {
        List<SessionRow> rows = jdbc.query("""
                SELECT id_conversation, id_connection FROM crm_whatsapp_agent_session
                WHERE id_agent_session=? FOR UPDATE
                """, (rs, row) -> new SessionRow(rs.getLong(1), rs.getLong(2)), sessionId);
        if (rows.isEmpty()) throw new IllegalArgumentException("Sesion del agente no encontrada");
        return rows.getFirst();
    }

    private RuntimeLimits runtimeLimits(Long connectionId) {
        List<RuntimeLimits> rows = jdbc.query("""
                SELECT agent_enabled, max_output_messages_per_run
                FROM crm_whatsapp_ai_config WHERE id_connection=?
                """, (rs, row) -> new RuntimeLimits(rs.getBoolean(1), rs.getInt(2)), connectionId);
        return rows.stream().findFirst().orElse(new RuntimeLimits(false, 6));
    }

    private void validateJob(Long jobId, Long sessionId) {
        if (jobId == null) return;
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM crm_whatsapp_agent_job
                WHERE id_agent_job=? AND id_agent_session=? AND obsolete_at IS NULL
                """, Long.class, jobId, sessionId);
        if (count == null || count != 1) throw new IllegalArgumentException("Job invalido u obsoleto");
    }

    private EnqueueCommand validate(EnqueueCommand c) {
        if (c == null || c.sessionId() == null || c.ordinal() < 0) throw new IllegalArgumentException("Salida invalida");
        String type = safe(c.messageType()).toUpperCase();
        if (!TYPES.contains(type)) throw new IllegalArgumentException("Tipo de salida no soportado");
        if ("TEXT".equals(type) && safe(c.body()).isBlank()) throw new IllegalArgumentException("Texto vacio");
        if (!"TEXT".equals(type) && safe(c.mediaStoragePath()).isBlank()) throw new IllegalArgumentException("Archivo requerido");
        return new EnqueueCommand(c.sessionId(), c.jobId(), c.replyToMessageId(), c.ordinal(), type,
                blankToNull(c.body()), blankToNull(c.mediaStoragePath()), blankToNull(c.mediaFileName()),
                blankToNull(c.mediaMimeType()), blankToNull(c.caption()));
    }

    private Optional<StoredRow> findStoredByKey(String key) {
        return jdbc.query("SELECT * FROM crm_whatsapp_agent_outbox WHERE idempotency_key=?",
                (rs, row) -> new StoredRow(mapEntry(rs), rs.getString("payload_hash")), key).stream().findFirst();
    }

    private Optional<StoredRow> findStored(Long id) {
        return jdbc.query("SELECT * FROM crm_whatsapp_agent_outbox WHERE id_agent_outbox=?",
                (rs, row) -> new StoredRow(mapEntry(rs), rs.getString("payload_hash")), id).stream().findFirst();
    }

    private OutboxEntry validateExisting(StoredRow existing, String payloadHash) {
        if (existing.payloadHash() != null && !existing.payloadHash().equals(payloadHash)) {
            throw new IllegalStateException("La clave idempotente ya existe con otro contenido");
        }
        return existing.entry();
    }

    private OutboxEntry mapEntry(ResultSet rs) throws java.sql.SQLException {
        return new OutboxEntry(rs.getLong("id_agent_outbox"), rs.getLong("id_agent_session"),
                nullableLong(rs, "id_agent_job"), rs.getLong("id_conversation"),
                nullableLong(rs, "reply_to_message_id"), rs.getString("idempotency_key"),
                rs.getLong("sequence_no"), rs.getString("message_type"), rs.getString("body"),
                rs.getString("media_storage_path"), rs.getString("media_file_name"),
                rs.getString("media_mime_type"), rs.getString("caption"), rs.getString("status"));
    }

    private Long nullableLong(ResultSet rs, String name) throws java.sql.SQLException {
        long value = rs.getLong(name); return rs.wasNull() ? null : value;
    }
    private LocalDateTime timestamp(ResultSet rs, String name) throws java.sql.SQLException {
        var value = rs.getTimestamp(name); return value == null ? null : value.toLocalDateTime();
    }
    private String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception error) { throw new IllegalStateException("SHA-256 no disponible", error); }
    }
    private String safe(String value) { return value == null ? "" : value.trim(); }
    private String blankToNull(String value) { String v = safe(value); return v.isBlank() ? null : v; }
    private String truncate(String value, int max) { String v = safe(value); if (v.isBlank()) v = "Error no especificado"; return v.length() <= max ? v : v.substring(0, max); }
    private record SessionRow(Long conversationId, Long connectionId) {}
    private record RuntimeLimits(boolean enabled, int maxOutputs) {}
    private record StoredRow(OutboxEntry entry, String payloadHash) {}
}
