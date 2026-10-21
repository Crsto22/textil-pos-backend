package com.sistemapos.sistematextil.config;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Locale;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.LOWEST_PRECEDENCE - 50)
public class VentaPosEntregaSchemaMigration implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(VentaPosEntregaSchemaMigration.class);
    private final DataSource dataSource;

    public VentaPosEntregaSchemaMigration(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            String url = connection.getMetaData().getURL();
            if (url == null || !url.toLowerCase(Locale.ROOT).startsWith("jdbc:mysql:")) return;
            try (Statement statement = connection.createStatement()) {
                if (!columnExists(connection, "venta", "idempotency_key")) {
                    statement.execute("ALTER TABLE venta ADD COLUMN idempotency_key VARCHAR(64) NULL AFTER origen");
                }
                if (!indexExists(connection, "venta", "uk_venta_idempotency_key")) {
                    statement.execute("ALTER TABLE venta ADD UNIQUE KEY uk_venta_idempotency_key (idempotency_key)");
                }
                if (tableExists(connection, "crm_whatsapp_film_addon")) {
                    statement.execute("ALTER TABLE crm_whatsapp_film_addon MODIFY COLUMN id_agent_session BIGINT NULL");
                    statement.execute("ALTER TABLE crm_whatsapp_film_addon MODIFY COLUMN id_conversation BIGINT NULL");
                }
            }
            log.info("Esquema de entregas opcionales del POS verificado");
        }
    }

    private boolean tableExists(Connection connection, String table) throws Exception {
        return exists(connection, """
                SELECT 1 FROM information_schema.TABLES
                WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? LIMIT 1
                """, table);
    }

    private boolean columnExists(Connection connection, String table, String column) throws Exception {
        return exists(connection, """
                SELECT 1 FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND COLUMN_NAME=? LIMIT 1
                """, table, column);
    }

    private boolean indexExists(Connection connection, String table, String index) throws Exception {
        return exists(connection, """
                SELECT 1 FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND INDEX_NAME=? LIMIT 1
                """, table, index);
    }

    private boolean exists(Connection connection, String sql, String... params) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) statement.setString(i + 1, params[i]);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }
}
