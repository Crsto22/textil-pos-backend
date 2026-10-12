package com.sistemapos.sistematextil.services.agent.config;

import java.time.LocalDateTime;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AgentActivationMonitorService {
    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public MonitorSnapshot snapshot(Long connectionId) {
        if (connectionId == null) return MonitorSnapshot.unavailable();
        LocalDateTime since = LocalDateTime.now().minusHours(24);
        try {
            long sessions = count("SELECT COUNT(*) FROM crm_whatsapp_agent_session WHERE id_connection=? AND created_at>=?",
                    connectionId, since);
            long completedJobs = count("""
                    SELECT COUNT(*) FROM crm_whatsapp_agent_job j
                    JOIN crm_whatsapp_agent_session s ON s.id_agent_session=j.id_agent_session
                    WHERE s.id_connection=? AND j.status='COMPLETED' AND j.created_at>=?
                    """, connectionId, since);
            long failedJobs = count("""
                    SELECT COUNT(*) FROM crm_whatsapp_agent_job j
                    JOIN crm_whatsapp_agent_session s ON s.id_agent_session=j.id_agent_session
                    WHERE s.id_connection=? AND j.status='FAILED' AND j.created_at>=?
                    """, connectionId, since);
            long suppressedResponses = count("""
                    SELECT COUNT(*) FROM crm_whatsapp_agent_job j
                    JOIN crm_whatsapp_agent_session s ON s.id_agent_session=j.id_agent_session
                    WHERE s.id_connection=? AND j.status='OBSOLETE' AND j.created_at>=?
                    """, connectionId, since);
            long failedTools = count("""
                    SELECT COUNT(*) FROM crm_whatsapp_agent_tool_call t
                    JOIN crm_whatsapp_agent_session s ON s.id_agent_session=t.id_agent_session
                    WHERE s.id_connection=? AND t.status='FAILED' AND t.created_at>=?
                    """, connectionId, since);
            long sentOutputs = count("""
                    SELECT COUNT(*) FROM crm_whatsapp_agent_outbox o
                    JOIN crm_whatsapp_agent_session s ON s.id_agent_session=o.id_agent_session
                    WHERE s.id_connection=? AND o.status='SENT' AND o.created_at>=?
                    """, connectionId, since);
            long failedOutputs = count("""
                    SELECT COUNT(*) FROM crm_whatsapp_agent_outbox o
                    JOIN crm_whatsapp_agent_session s ON s.id_agent_session=o.id_agent_session
                    WHERE s.id_connection=? AND o.status='FAILED' AND o.created_at>=?
                    """, connectionId, since);
            long pendingOutputs = count("""
                    SELECT COUNT(*) FROM crm_whatsapp_agent_outbox o
                    JOIN crm_whatsapp_agent_session s ON s.id_agent_session=o.id_agent_session
                    WHERE s.id_connection=? AND o.status IN ('PENDING','RETRY','PROCESSING')
                    """, connectionId);
            long duplicateReceipts = count("""
                    SELECT COUNT(*) FROM crm_whatsapp_payment_evidence e
                    JOIN crm_whatsapp_agent_session s ON s.id_agent_session=e.id_agent_session
                    WHERE s.id_connection=? AND e.duplicate_of_id IS NOT NULL AND e.created_at>=?
                    """, connectionId, since);
            long automaticSales = count("""
                    SELECT COUNT(*) FROM crm_whatsapp_payment_request p
                    JOIN crm_whatsapp_agent_session s ON s.id_agent_session=p.id_agent_session
                    WHERE s.id_connection=? AND p.id_venta IS NOT NULL AND p.created_at>=?
                    """, connectionId, since);
            String health = failedJobs > 0 || failedOutputs > 0 ? "ALERT"
                    : failedTools > 0 || duplicateReceipts > 0 || pendingOutputs > 10 ? "WATCH" : "HEALTHY";
            return new MonitorSnapshot(true, since, health, sessions, completedJobs, failedJobs,
                    suppressedResponses, failedTools, sentOutputs, failedOutputs, pendingOutputs,
                    duplicateReceipts, automaticSales);
        } catch (DataAccessException unavailable) {
            return MonitorSnapshot.unavailable();
        }
    }

    private long count(String sql, Object... parameters) {
        Long result = jdbc.queryForObject(sql, Long.class, parameters);
        return result == null ? 0 : result;
    }

    public record MonitorSnapshot(boolean available, LocalDateTime since, String health,
            long sessions, long completedJobs, long failedJobs, long suppressedResponses,
            long failedTools, long sentOutputs, long failedOutputs, long pendingOutputs,
            long duplicateReceipts, long automaticSales) {
        static MonitorSnapshot unavailable() {
            return new MonitorSnapshot(false, null, "UNAVAILABLE", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }
    }
}
