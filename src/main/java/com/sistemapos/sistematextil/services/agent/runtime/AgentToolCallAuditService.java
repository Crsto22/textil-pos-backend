package com.sistemapos.sistematextil.services.agent.runtime;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AgentToolCallAuditService {
    private final JdbcTemplate jdbc;

    public String execute(Long sessionId, Long jobId, int ordinal, String toolName, String request,
            Supplier<String> action) {
        if (sessionId == null || jobId == null) return action.get();
        String fingerprint = sha256(toolName + "|" + safe(request));
        // Gemini puede repetir las mismas herramientas en otro orden tras un error
        // transitorio. La identidad debe depender de la operacion, no del ordinal
        // elegido en cada intento del modelo.
        String key = sha256("agent-tool-v2|" + jobId + "|" + toolName + "|" + fingerprint);
        Optional<Row> existing = find(key);
        if (existing.isPresent()) {
            Row row = existing.get();
            if (!toolName.equals(row.toolName()) || !fingerprint.equals(row.requestFingerprint())) {
                throw new IllegalStateException("La llamada de herramienta reintentada no coincide con la original");
            }
            if ("COMPLETED".equals(row.status()) && row.response() != null) return row.response();
        }
        LocalDateTime now = LocalDateTime.now();
        if (existing.isEmpty()) {
            jdbc.update("""
                    INSERT INTO crm_whatsapp_agent_tool_call
                      (id_agent_session,id_agent_job,idempotency_key,tool_name,status,request_json,
                       request_fingerprint,started_at,created_at)
                    VALUES (?,?,?,?,'RUNNING',?,?,?,?)
                    """, sessionId, jobId, key, toolName, truncate(request, 65535), fingerprint, now, now);
        } else {
            jdbc.update("""
                    UPDATE crm_whatsapp_agent_tool_call
                    SET status='RUNNING', error_code=NULL, error_message=NULL, started_at=?
                    WHERE idempotency_key=?
                    """, now, key);
        }
        try {
            String response = action.get();
            jdbc.update("""
                    UPDATE crm_whatsapp_agent_tool_call
                    SET status='COMPLETED', response_json=?, completed_at=?
                    WHERE idempotency_key=?
                    """, response, LocalDateTime.now(), key);
            return response;
        } catch (RuntimeException error) {
            jdbc.update("""
                    UPDATE crm_whatsapp_agent_tool_call
                    SET status='FAILED', error_code=?, error_message=?, completed_at=?
                    WHERE idempotency_key=?
                    """, error.getClass().getSimpleName(), truncate(error.getMessage(), 1000),
                    LocalDateTime.now(), key);
            throw error;
        }
    }

    private Optional<Row> find(String key) {
        List<Row> rows = jdbc.query("""
                SELECT tool_name,status,response_json,request_fingerprint
                FROM crm_whatsapp_agent_tool_call WHERE idempotency_key=?
                """, (rs, index) -> new Row(rs.getString("tool_name"), rs.getString("status"),
                        rs.getString("response_json"), rs.getString("request_fingerprint")), key);
        return rows.stream().findFirst();
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalStateException("SHA-256 no esta disponible", error);
        }
    }

    private String safe(String value) { return value == null ? "" : value; }
    private String truncate(String value, int max) {
        String safe = safe(value);
        return safe.length() <= max ? safe : safe.substring(0, max);
    }
    private record Row(String toolName, String status, String response, String requestFingerprint) {}
}
