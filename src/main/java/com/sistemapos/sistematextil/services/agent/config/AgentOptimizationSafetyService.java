package com.sistemapos.sistematextil.services.agent.config;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Reversión automática en memoria; no cambia datos ni requiere migraciones. */
@Service
public class AgentOptimizationSafetyService {
    private static final int MINIMUM_SAMPLE = 20;
    private final JdbcTemplate jdbc;
    private final ConcurrentHashMap<Long, CachedDecision> cache = new ConcurrentHashMap<>();

    public AgentOptimizationSafetyService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean allows(Long connectionId) {
        if (connectionId == null) return false;
        CachedDecision cached = cache.get(connectionId);
        if (cached != null && cached.expiresAt().isAfter(LocalDateTime.now())) return cached.allowed();
        boolean allowed = evaluate(connectionId);
        cache.put(connectionId, new CachedDecision(allowed, LocalDateTime.now().plusMinutes(5)));
        return allowed;
    }

    private boolean evaluate(Long connectionId) {
        LocalDateTime now = LocalDateTime.now();
        Window current = window(connectionId, now.minusHours(24), now);
        Window baseline = window(connectionId, now.minusDays(7), now.minusHours(24));
        if (current.calls() < MINIMUM_SAMPLE || baseline.calls() < MINIMUM_SAMPLE) return true;
        return current.failureRate() <= baseline.failureRate() + 0.02D
                && current.correctionRate() <= baseline.correctionRate() + 0.05D
                && current.decisionFailureRate() <= baseline.decisionFailureRate() + 0.02D;
    }

    private Window window(Long connectionId, LocalDateTime from, LocalDateTime to) {
        try {
            long calls = count("""
                    SELECT COUNT(*) FROM crm_whatsapp_agent_ai_usage
                    WHERE id_connection=? AND created_at>=? AND created_at<?
                    """, connectionId, from, to);
            long failures = count("""
                    SELECT COUNT(*) FROM crm_whatsapp_agent_ai_usage
                    WHERE id_connection=? AND created_at>=? AND created_at<? AND status='FAILED'
                    """, connectionId, from, to);
            long corrections = count("""
                    SELECT COUNT(*) FROM crm_whatsapp_agent_ai_usage
                    WHERE id_connection=? AND created_at>=? AND created_at<? AND phase='CORRECTION'
                    """, connectionId, from, to);
            long decisions = count("""
                    SELECT COUNT(*) FROM crm_whatsapp_agent_ai_usage
                    WHERE id_connection=? AND created_at>=? AND created_at<? AND phase='DECISION'
                    """, connectionId, from, to);
            long decisionFailures = count("""
                    SELECT COUNT(*) FROM crm_whatsapp_agent_ai_usage
                    WHERE id_connection=? AND created_at>=? AND created_at<?
                      AND phase='DECISION' AND status='FAILED'
                    """, connectionId, from, to);
            return new Window(calls, failures, corrections, decisions, decisionFailures);
        } catch (DataAccessException unavailable) {
            // Ante ausencia de métricas no se bloquea el agente; el despliegue sigue controlado por sus flags.
            return Window.empty();
        }
    }

    private long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    private record CachedDecision(boolean allowed, LocalDateTime expiresAt) {}
    private record Window(long calls, long failures, long corrections, long decisions, long decisionFailures) {
        static Window empty() { return new Window(0, 0, 0, 0, 0); }
        double failureRate() { return calls == 0 ? 0 : (double) failures / calls; }
        double correctionRate() { return calls == 0 ? 0 : (double) corrections / calls; }
        double decisionFailureRate() { return decisions == 0 ? 0 : (double) decisionFailures / decisions; }
    }
}
