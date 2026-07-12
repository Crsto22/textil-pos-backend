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
public class ProductoPreventaSchemaMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProductoPreventaSchemaMigration.class);
    private static final String TABLE_NAME = "producto";

    private final DataSource dataSource;

    public ProductoPreventaSchemaMigration(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            if (!tableExists(connection, TABLE_NAME)) {
                return;
            }

            if (!columnExists(connection, TABLE_NAME, "preventa")) {
                statement.execute("""
                        ALTER TABLE producto
                        ADD COLUMN preventa TINYINT(1) NOT NULL DEFAULT 0 AFTER publicar_ecommerce
                        """);
                log.info("Columna {}.preventa creada", TABLE_NAME);
            }
            if (!columnExists(connection, TABLE_NAME, "fecha_envio_preventa")) {
                statement.execute("""
                        ALTER TABLE producto
                        ADD COLUMN fecha_envio_preventa DATE DEFAULT NULL AFTER preventa
                        """);
                log.info("Columna {}.fecha_envio_preventa creada", TABLE_NAME);
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
