package com.sistemapos.sistematextil.config;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class EcommercePublicacionSchemaMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EcommercePublicacionSchemaMigration.class);

    private final DataSource dataSource;

    public EcommercePublicacionSchemaMigration(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            asegurarColumna(
                    connection,
                    statement,
                    "producto",
                    "publicar_ecommerce",
                    "ALTER TABLE producto ADD COLUMN publicar_ecommerce TINYINT(1) NOT NULL DEFAULT 0");
            asegurarColumna(
                    connection,
                    statement,
                    "sucursal",
                    "publicar_ecommerce",
                    "ALTER TABLE sucursal ADD COLUMN publicar_ecommerce TINYINT(1) NOT NULL DEFAULT 0");
        }
    }

    private void asegurarColumna(
            Connection connection,
            Statement statement,
            String tableName,
            String columnName,
            String alterSql) throws Exception {
        if (!tableExists(connection, tableName) || columnExists(connection, tableName, columnName)) {
            return;
        }
        statement.execute(alterSql);
        log.info("Columna {}.{} creada", tableName, columnName);
    }

    private boolean tableExists(Connection connection, String tableName) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COUNT(*)
                FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                """)) {
            statement.setString(1, tableName);
            try (var resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getInt(1) > 0;
            }
        }
    }

    private boolean columnExists(Connection connection, String tableName, String columnName) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COUNT(*)
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND COLUMN_NAME = ?
                """)) {
            statement.setString(1, tableName);
            statement.setString(2, columnName);
            try (var resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getInt(1) > 0;
            }
        }
    }
}
