package com.sistemapos.sistematextil.services.agent.outbox;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class JdbcAgentOutboxStoreRecoveryTest {
    @Test
    void bindsExactlyOneValueForEveryRecoveryPlaceholder() {
        RecordingJdbcTemplate jdbc = new RecordingJdbcTemplate();
        JdbcAgentOutboxStore store = new JdbcAgentOutboxStore(jdbc);

        store.recoverExpired(LocalDateTime.of(2026, 10, 8, 20, 39), 5);

        assertEquals(List.of(4, 4), jdbc.parameterCounts);
    }

    private static final class RecordingJdbcTemplate extends JdbcTemplate {
        private final List<Integer> parameterCounts = new ArrayList<>();

        @Override
        public int update(String sql, Object... args) {
            long placeholders = sql.chars().filter(value -> value == '?').count();
            assertEquals(placeholders, args.length, "Cada placeholder SQL debe recibir exactamente un parametro");
            parameterCounts.add(args.length);
            return 0;
        }
    }
}
