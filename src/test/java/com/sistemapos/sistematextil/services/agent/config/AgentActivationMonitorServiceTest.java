package com.sistemapos.sistematextil.services.agent.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class AgentActivationMonitorServiceTest {
    @Test
    void reportsFailuresAndDuplicateReceiptsForTheConnection() {
        JdbcTemplate jdbc = new JdbcTemplate() {
            @Override
            public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
                long value = sql.contains("o.status='FAILED'") ? 2L
                        : sql.contains("duplicate_of_id IS NOT NULL") ? 3L
                        : sql.contains("o.status='SENT'") ? 8L : 0L;
                return requiredType.cast(value);
            }
        };

        var snapshot = new AgentActivationMonitorService(jdbc).snapshot(7L);

        assertTrue(snapshot.available());
        assertEquals("ALERT", snapshot.health());
        assertEquals(2, snapshot.failedOutputs());
        assertEquals(3, snapshot.duplicateReceipts());
        assertEquals(8, snapshot.sentOutputs());
    }
}
