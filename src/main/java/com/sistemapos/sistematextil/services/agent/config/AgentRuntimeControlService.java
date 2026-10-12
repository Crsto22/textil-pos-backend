package com.sistemapos.sistematextil.services.agent.config;

import java.time.LocalDateTime;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AgentRuntimeControlService {
    private final JdbcTemplate jdbc;

    @Transactional
    public StopResult stop(Long connectionId, String reason) {
        if (connectionId == null) return new StopResult(0, 0);
        LocalDateTime now = LocalDateTime.now();
        String safeReason = reason == null || reason.isBlank() ? "Agente desactivado" : reason.trim();
        int outbox = jdbc.update("""
                UPDATE crm_whatsapp_agent_outbox o
                JOIN crm_whatsapp_agent_session s ON s.id_agent_session=o.id_agent_session
                SET o.status='CANCELLED', o.locked_by=NULL, o.lease_until=NULL,
                    o.last_error=?, o.updated_at=?
                WHERE s.id_connection=? AND o.status IN ('PENDING','RETRY','PROCESSING')
                """, safeReason, now, connectionId);
        int jobs = jdbc.update("""
                UPDATE crm_whatsapp_agent_job j
                JOIN crm_whatsapp_agent_session s ON s.id_agent_session=j.id_agent_session
                SET j.status='OBSOLETE', j.active_slot=NULL, j.obsolete_at=?,
                    j.obsolete_reason=?, j.locked_by=NULL, j.lease_until=NULL, j.updated_at=?
                WHERE s.id_connection=? AND j.obsolete_at IS NULL
                  AND j.status IN ('PENDING','RUNNING','PROCESSING','COMPLETED')
                """, now, safeReason, now, connectionId);
        return new StopResult(jobs, outbox);
    }

    public record StopResult(int cancelledJobs, int cancelledOutbox) {}
}
