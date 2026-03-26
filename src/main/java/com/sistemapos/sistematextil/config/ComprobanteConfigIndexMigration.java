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
    private static final String NEW_INDEX = "uk_comprobante_config_sucursal_tipo_serie";

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

            boolean oldIndexExists = indexExists(connection, TABLE_NAME, OLD_INDEX);
            boolean newIndexExists = indexExists(connection, TABLE_NAME, NEW_INDEX);

            if (!oldIndexExists && newIndexExists) {
                return;
            }

            try (Statement statement = connection.createStatement()) {
                if (oldIndexExists) {
                    statement.execute("ALTER TABLE comprobante_config DROP INDEX " + OLD_INDEX);
                    log.info("Indice antiguo {} eliminado de {}", OLD_INDEX, TABLE_NAME);
                }
                if (!newIndexExists) {
                    statement.execute("""
                            ALTER TABLE comprobante_config
                            ADD UNIQUE KEY uk_comprobante_config_sucursal_tipo_serie
                            (id_sucursal, tipo_comprobante, serie)
                            """);
                    log.info("Indice nuevo {} creado en {}", NEW_INDEX, TABLE_NAME);
                }
            }
        }
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
