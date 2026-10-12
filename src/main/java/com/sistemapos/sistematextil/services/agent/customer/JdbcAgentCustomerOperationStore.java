package com.sistemapos.sistematextil.services.agent.customer;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAgentCustomerOperationStore implements AgentCustomerOperationStore {
    private final JdbcTemplate jdbc;

    public JdbcAgentCustomerOperationStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<OperationRow> find(String idempotencyKey) {
        return jdbc.query("""
                SELECT idempotency_key, tool_name, status, request_json, response_json
                FROM crm_whatsapp_agent_tool_call
                WHERE idempotency_key = ?
                LIMIT 1
                """, (rs, row) -> new OperationRow(
                rs.getString("idempotency_key"), rs.getString("tool_name"), rs.getString("status"),
                rs.getString("request_json"), rs.getString("response_json")), idempotencyKey)
                .stream().findFirst();
    }

    @Override
    public boolean claim(Long sessionId, String idempotencyKey, String toolName, String requestFingerprint,
            LocalDateTime now) {
        return jdbc.update("""
                INSERT IGNORE INTO crm_whatsapp_agent_tool_call
                    (id_agent_session, idempotency_key, tool_name, status, request_json, started_at, created_at)
                VALUES (?, ?, ?, 'RUNNING', ?, ?, ?)
                """, sessionId, idempotencyKey, toolName, requestFingerprint, now, now) == 1;
    }

    @Override
    public void complete(String idempotencyKey, String responseJson, LocalDateTime now) {
        int updated = jdbc.update("""
                UPDATE crm_whatsapp_agent_tool_call
                SET status = 'COMPLETED', response_json = ?, completed_at = ?
                WHERE idempotency_key = ? AND status = 'RUNNING'
                """, responseJson, now, idempotencyKey);
        if (updated != 1) {
            throw new IllegalStateException("No se pudo completar la operacion idempotente del cliente");
        }
    }
}
