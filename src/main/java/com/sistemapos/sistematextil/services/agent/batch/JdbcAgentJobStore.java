package com.sistemapos.sistematextil.services.agent.batch;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sistemapos.sistematextil.services.agent.ai.AgentMediaRequest;

@Repository
public class JdbcAgentJobStore implements AgentJobStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public JdbcAgentJobStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<JobReference> findByAnchorMessage(Long anchorMessageId) {
        List<JobReference> rows = jdbc.query("""
                SELECT id_agent_job, id_agent_session, id_anchor_message, status, available_at
                FROM crm_whatsapp_agent_job
                WHERE id_anchor_message = ?
                LIMIT 1
                """, (rs, row) -> new JobReference(
                        rs.getLong("id_agent_job"),
                        rs.getLong("id_agent_session"),
                        rs.getLong("id_anchor_message"),
                        rs.getString("status"),
                        rs.getTimestamp("available_at").toLocalDateTime()), anchorMessageId);
        return rows.stream().findFirst();
    }

    @Override
    public int invalidateOpenWork(Long sessionId, LocalDateTime obsoleteAt, String reason) {
        jdbc.update("""
                UPDATE crm_whatsapp_agent_outbox
                SET status = 'CANCELLED', last_error = ?, updated_at = ?
                WHERE id_agent_session = ?
                  AND sent_at IS NULL
                  AND status IN ('PENDING', 'RETRY', 'PROCESSING')
                """, reason, obsoleteAt, sessionId);
        return jdbc.update("""
                UPDATE crm_whatsapp_agent_job
                SET status = 'OBSOLETE', active_slot = NULL, obsolete_at = ?, obsolete_reason = ?,
                    lease_until = NULL, locked_by = NULL, updated_at = ?
                WHERE id_agent_session = ?
                  AND obsolete_at IS NULL
                  AND status IN ('PENDING', 'RUNNING', 'PROCESSING', 'COMPLETED')
                """, obsoleteAt, reason, obsoleteAt, sessionId);
    }

    @Override
    public JobReference enqueue(Long sessionId, Long anchorMessageId, LocalDateTime availableAt) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        String sql = """
                INSERT INTO crm_whatsapp_agent_job
                    (id_agent_session, id_anchor_message, idempotency_key, job_type, status, active_slot,
                     attempts, available_at, created_at, updated_at)
                VALUES (?, ?, ?, 'PROCESS_MESSAGE', 'PENDING', 1, 0, ?, ?, ?)
                """;
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, sessionId);
            statement.setLong(2, anchorMessageId);
            statement.setString(3, "agent-message-" + anchorMessageId);
            statement.setObject(4, availableAt);
            statement.setObject(5, LocalDateTime.now());
            statement.setObject(6, LocalDateTime.now());
            return statement;
        }, keys);
        Number key = keys.getKey();
        if (key == null) throw new IllegalStateException("No se pudo obtener el id del job del agente");
        return new JobReference(key.longValue(), sessionId, anchorMessageId, "PENDING", availableAt);
    }

    @Override
    @Transactional
    public Optional<JobLease> claimNext(String workerId, LocalDateTime now, LocalDateTime leaseUntil) {
        List<Long> ready = jdbc.query("""
                SELECT id_agent_job
                FROM crm_whatsapp_agent_job j
                INNER JOIN crm_whatsapp_agent_session s
                  ON s.id_agent_session = j.id_agent_session
                 AND s.active_slot = 1
                 AND s.last_customer_message_id = j.id_anchor_message
                INNER JOIN crm_whatsapp_conversation c
                  ON c.id_conversation = s.id_conversation
                 AND c.attention_queue = 'AGENT_ACTIVE'
                 AND c.assigned_user_id IS NULL
                INNER JOIN crm_whatsapp_ai_config cfg
                  ON cfg.id_connection = s.id_connection AND cfg.agent_enabled = TRUE
                WHERE j.status = 'PENDING'
                  AND j.active_slot = 1
                  AND j.obsolete_at IS NULL
                  AND j.available_at <= ?
                ORDER BY j.available_at, j.id_agent_job
                LIMIT 1
                FOR UPDATE SKIP LOCKED
                """, (rs, row) -> rs.getLong(1), now);
        if (ready.isEmpty()) return Optional.empty();
        Long jobId = ready.getFirst();
        int updated = jdbc.update("""
                UPDATE crm_whatsapp_agent_job
                SET status = 'RUNNING', attempts = attempts + 1, locked_by = ?, lease_until = ?,
                    started_at = COALESCE(started_at, ?), updated_at = ?
                WHERE id_agent_job = ?
                  AND status = 'PENDING'
                  AND active_slot = 1
                  AND obsolete_at IS NULL
                """, workerId, leaseUntil, now, now, jobId);
        if (updated != 1) return Optional.empty();
        return findLease(jobId, workerId);
    }

    @Override
    public Optional<GeneratedTurn> findGeneratedTurn(Long jobId, String workerId) {
        return jdbc.query("""
                SELECT response_text, conversation_summary, requires_human_help, provider, model,
                       latency_ms, tool_calls, context_fingerprint, media_json
                FROM crm_whatsapp_agent_job
                WHERE id_agent_job=? AND status='RUNNING' AND locked_by=?
                  AND response_text IS NOT NULL
                """, (rs, row) -> new GeneratedTurn(rs.getString("response_text"),
                        rs.getString("conversation_summary"), rs.getBoolean("requires_human_help"),
                        rs.getString("provider"), rs.getString("model"), rs.getLong("latency_ms"),
                        rs.getInt("tool_calls"), rs.getString("context_fingerprint"),
                        readMedia(rs.getString("media_json"))), jobId, workerId)
                .stream().findFirst();
    }

    @Override
    @Transactional
    public boolean saveGeneratedTurn(Long jobId, String workerId, GeneratedTurn turn, LocalDateTime generatedAt) {
        if (turn == null || turn.customerMessage() == null || turn.customerMessage().isBlank()) {
            throw new IllegalArgumentException("La respuesta generada es obligatoria");
        }
        return jdbc.update("""
                UPDATE crm_whatsapp_agent_job
                SET response_text=?, conversation_summary=?, requires_human_help=?, provider=?, model=?,
                    latency_ms=?, tool_calls=?, context_fingerprint=?, media_json=?, generated_at=?, updated_at=?
                WHERE id_agent_job=? AND status='RUNNING' AND locked_by=?
                  AND obsolete_at IS NULL AND active_slot=1
                  AND response_text IS NULL
                """, turn.customerMessage(), turn.conversationSummary(), turn.requiresHumanHelp(),
                turn.provider(), turn.model(), turn.latencyMs(), turn.toolCalls(), turn.contextFingerprint(),
                writeMedia(turn.media()), generatedAt, generatedAt, jobId, workerId) == 1;
    }

    private List<AgentMediaRequest> readMedia(String json) {
        if (json == null || json.isBlank()) return List.of();
        try { return objectMapper.readValue(json, new TypeReference<List<AgentMediaRequest>>() {}); }
        catch (Exception error) { throw new IllegalStateException("Los medios guardados del job no son validos", error); }
    }

    private String writeMedia(List<AgentMediaRequest> media) {
        try { return objectMapper.writeValueAsString(media == null ? List.of() : media); }
        catch (Exception error) { throw new IllegalStateException("No se pudieron guardar los medios del job", error); }
    }

    @Override
    @Transactional
    public boolean complete(Long jobId, String workerId, LocalDateTime completedAt) {
        return jdbc.update("""
                UPDATE crm_whatsapp_agent_job
                SET status = 'COMPLETED', active_slot = NULL, lease_until = NULL, locked_by = NULL,
                    completed_at = ?, updated_at = ?
                WHERE id_agent_job = ?
                  AND status = 'RUNNING'
                  AND locked_by = ?
                  AND obsolete_at IS NULL
                  AND active_slot = 1
                  AND EXISTS (
                    SELECT 1
                    FROM crm_whatsapp_agent_session s
                    WHERE s.id_agent_session = crm_whatsapp_agent_job.id_agent_session
                      AND s.active_slot = 1
                      AND s.last_customer_message_id = crm_whatsapp_agent_job.id_anchor_message
                  )
                """, completedAt, completedAt, jobId, workerId) == 1;
    }

    @Override
    @Transactional
    public FailureResult failOrRetry(
            Long jobId,
            String workerId,
            LocalDateTime failedAt,
            LocalDateTime retryAt,
            int maxAttempts,
            String error) {
        List<Integer> attempts = jdbc.query("""
                SELECT attempts
                FROM crm_whatsapp_agent_job
                WHERE id_agent_job = ?
                  AND status = 'RUNNING'
                  AND locked_by = ?
                  AND obsolete_at IS NULL
                  AND active_slot = 1
                FOR UPDATE
                """, (rs, row) -> rs.getInt(1), jobId, workerId);
        if (attempts.isEmpty()) return FailureResult.IGNORED_STALE_LEASE;
        String safeError = truncate(error, 1000);
        if (attempts.getFirst() >= maxAttempts) {
            jdbc.update("""
                    UPDATE crm_whatsapp_agent_job
                    SET status = 'FAILED', active_slot = NULL, lease_until = NULL, locked_by = NULL,
                        last_error = ?, completed_at = ?, updated_at = ?
                    WHERE id_agent_job = ? AND status = 'RUNNING' AND locked_by = ?
                    """, safeError, failedAt, failedAt, jobId, workerId);
            return FailureResult.FAILED_PERMANENTLY;
        }
        jdbc.update("""
                UPDATE crm_whatsapp_agent_job
                SET status = 'PENDING', lease_until = NULL, locked_by = NULL,
                    last_error = ?, available_at = ?, updated_at = ?
                WHERE id_agent_job = ? AND status = 'RUNNING' AND locked_by = ?
                """, safeError, retryAt, failedAt, jobId, workerId);
        return FailureResult.RETRY_SCHEDULED;
    }

    @Override
    @Transactional
    public RecoveryResult recoverExpired(LocalDateTime now, int maxAttempts) {
        int failed = jdbc.update("""
                UPDATE crm_whatsapp_agent_job
                SET status = 'FAILED', active_slot = NULL, locked_by = NULL, lease_until = NULL,
                    last_error = 'Lease vencido y limite de reintentos alcanzado',
                    completed_at = ?, updated_at = ?
                WHERE status = 'RUNNING'
                  AND active_slot = 1
                  AND obsolete_at IS NULL
                  AND lease_until < ?
                  AND attempts >= ?
                """, now, now, now, maxAttempts);
        int recovered = jdbc.update("""
                UPDATE crm_whatsapp_agent_job
                SET status = 'PENDING', locked_by = NULL, lease_until = NULL,
                    available_at = ?, last_error = 'Lease vencido; job recuperado al reiniciar', updated_at = ?
                WHERE status = 'RUNNING'
                  AND active_slot = 1
                  AND obsolete_at IS NULL
                  AND lease_until < ?
                  AND attempts < ?
                """, now, now, now, maxAttempts);
        return new RecoveryResult(recovered, failed);
    }

    @Override
    public boolean isCurrent(Long jobId) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM crm_whatsapp_agent_job j
                INNER JOIN crm_whatsapp_agent_session s ON s.id_agent_session = j.id_agent_session
                INNER JOIN crm_whatsapp_conversation c
                  ON c.id_conversation = s.id_conversation
                 AND c.attention_queue = 'AGENT_ACTIVE'
                 AND c.assigned_user_id IS NULL
                WHERE j.id_agent_job = ?
                  AND j.obsolete_at IS NULL
                  AND j.status <> 'OBSOLETE'
                  AND j.id_anchor_message = s.last_customer_message_id
                """, Long.class, jobId);
        return count != null && count == 1L;
    }

    private Optional<JobLease> findLease(Long jobId, String workerId) {
        List<JobLease> rows = jdbc.query("""
                SELECT id_agent_job, id_agent_session, id_anchor_message, attempts, locked_by, lease_until
                FROM crm_whatsapp_agent_job
                WHERE id_agent_job = ? AND status = 'RUNNING' AND locked_by = ?
                """, (rs, row) -> new JobLease(
                        rs.getLong("id_agent_job"),
                        rs.getLong("id_agent_session"),
                        rs.getLong("id_anchor_message"),
                        rs.getInt("attempts"),
                        rs.getString("locked_by"),
                        rs.getTimestamp("lease_until").toLocalDateTime()), jobId, workerId);
        return rows.stream().findFirst();
    }

    private String truncate(String value, int maxLength) {
        String safe = value == null || value.isBlank() ? "Error no especificado" : value.trim();
        return safe.length() <= maxLength ? safe : safe.substring(0, maxLength);
    }
}
