package com.sistemapos.sistematextil.services.agent.ai;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AiInvocationAuditService {
    private static final Logger log = LoggerFactory.getLogger(AiInvocationAuditService.class);
    private final JdbcTemplate jdbc;

    public AiInvocationAuditService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void success(Invocation invocation, AiTokenUsage usage) {
        save(invocation, usage == null ? AiTokenUsage.unknown() : usage, "SUCCESS", null);
    }

    public void failure(Invocation invocation, String errorCode) {
        save(invocation, AiTokenUsage.unknown(), "FAILED", clean(errorCode, 80));
    }

    private void save(Invocation value, AiTokenUsage usage, String status, String errorCode) {
        if (value == null || value.connectionId() == null) return;
        try {
            jdbc.update("""
                    INSERT INTO crm_whatsapp_agent_ai_usage
                    (id_connection,id_agent_session,id_agent_job,id_conversation,phase,provider,model,
                     credential_fingerprint,input_tokens,output_tokens,cached_tokens,total_tokens,
                     latency_ms,tool_calls,status,error_code,created_at)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP(6))
                    """, value.connectionId(), value.sessionId(), value.jobId(), value.conversationId(),
                    value.phase().name(), clean(value.provider(), 30), clean(value.model(), 120),
                    fingerprint(value.credential()), usage.inputTokens(), usage.outputTokens(),
                    usage.cachedTokens(), usage.totalTokens(), value.latencyMs(), value.toolCalls(), status,
                    errorCode);
        } catch (DataAccessException error) {
            // La telemetria nunca puede interrumpir una venta ni provocar un reintento del modelo.
            log.warn("No se pudo registrar el uso de IA de la conexion {}: {}",
                    value.connectionId(), error.getMostSpecificCause().getMessage());
        }
    }

    static String fingerprint(String credential) {
        if (credential == null || credential.isBlank()) return null;
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(credential.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception impossible) {
            throw new IllegalStateException("SHA-256 no esta disponible", impossible);
        }
    }

    private String clean(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        return normalized.length() <= max ? normalized : normalized.substring(0, max);
    }

    public record Invocation(Long connectionId, Long sessionId, Long jobId, Long conversationId,
            AiInvocationPhase phase, String provider, String model, String credential,
            long latencyMs, int toolCalls) {
    }
}
