package com.sistemapos.sistematextil.services.agent.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class AgentOptimizationSafetyServiceTest {
    @Test
    void automaticallyFallsBackWhenCurrentRatesExceedBaselineThresholds() {
        long[] values = {100, 10, 10, 50, 5, 100, 1, 1, 50, 0};
        AtomicInteger index = new AtomicInteger();
        JdbcTemplate jdbc = new JdbcTemplate() {
            @Override public <T> T queryForObject(String sql, Class<T> type, Object... args) {
                return type.cast(Long.valueOf(values[index.getAndIncrement()]));
            }
        };
        assertFalse(new AgentOptimizationSafetyService(jdbc).allows(7L));
    }

    @Test
    void keepsConfiguredOptimizationUntilThereIsEnoughEvidence() {
        JdbcTemplate jdbc = new JdbcTemplate() {
            @Override public <T> T queryForObject(String sql, Class<T> type, Object... args) {
                return type.cast(Long.valueOf(0));
            }
        };
        assertTrue(new AgentOptimizationSafetyService(jdbc).allows(7L));
    }
}
