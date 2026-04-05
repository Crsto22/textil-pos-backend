package com.sistemapos.sistematextil.config;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class ComprobanteConfigIndexMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ComprobanteConfigIndexMigration.class);
    private static final String TABLE_NAME = "comprobante_config";
    private static final String OLD_INDEX = "uk_comprobante_config_sucursal_tipo";
    private static final String LEGACY_SERIE_INDEX = "uk_comprobante_config_sucursal_tipo_serie";
    private static final String GLOBAL_INDEX = "uk_comprobante_config_tipo_serie";
    private static final String LOOKUP_INDEX = "idx_comprobante_config_lookup";

    private final DataSource dataSource;

    public ComprobanteConfigIndexMigration(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            if (!tableExists(connection, TABLE_NAME)) {
                return;
            }

            try (Statement statement = connection.createStatement()) {
                dropIndexIfExists(connection, statement, OLD_INDEX);
                dropIndexIfExists(connection, statement, LEGACY_SERIE_INDEX);

                if (!indexExists(connection, TABLE_NAME, GLOBAL_INDEX)) {
                    statement.execute("""
                            ALTER TABLE comprobante_config
                            ADD UNIQUE KEY uk_comprobante_config_tipo_serie
                            (tipo_comprobante, serie)
                            """);
                    log.info("Indice global {} creado en {}", GLOBAL_INDEX, TABLE_NAME);
                }

                if (!indexExists(connection, TABLE_NAME, LOOKUP_INDEX)) {
                    statement.execute("""
                            ALTER TABLE comprobante_config
                            ADD KEY idx_comprobante_config_lookup
                            (tipo_comprobante, serie, activo, deleted_at)
                            """);
                    log.info("Indice {} creado en {}", LOOKUP_INDEX, TABLE_NAME);
                }
            }
        }
    }

    private void dropIndexIfExists(Connection connection, Statement statement, String indexName) throws Exception {
        if (!indexExists(connection, TABLE_NAME, indexName)) {
            return;
        }
        statement.execute("ALTER TABLE " + TABLE_NAME + " DROP INDEX " + indexName);
        log.info("Indice {} eliminado de {}", indexName, TABLE_NAME);
    }

    private boolean tableExists(Connection connection, String tableName) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COUNT(*)
                FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                """)) {
            statement.setString(1, tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getInt(1) > 0;
            }
        }
    }

    private boolean indexExists(Connection connection, String tableName, String indexName) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COUNT(*)
                FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND INDEX_NAME = ?
                """)) {
            statement.setString(1, tableName);
            statement.setString(2, indexName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getInt(1) > 0;
            }
        }
    }
}
