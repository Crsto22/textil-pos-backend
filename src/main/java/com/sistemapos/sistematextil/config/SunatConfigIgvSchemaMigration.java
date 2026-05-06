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
public class SunatConfigIgvSchemaMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SunatConfigIgvSchemaMigration.class);

    private final DataSource dataSource;

    public SunatConfigIgvSchemaMigration(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            if (!tableExists(connection, "sunat_config")) {
                return;
            }
            if (!columnExists(connection, "sunat_config", "igv_porcentaje")) {
                statement.execute("""
                        ALTER TABLE sunat_config
                        ADD COLUMN igv_porcentaje DECIMAL(5,2) NOT NULL DEFAULT 18.00
                        AFTER client_secret
                        """);
                log.info("Columna sunat_config.igv_porcentaje creada");
            }
            statement.execute("""
                    UPDATE sunat_config
                    SET igv_porcentaje = 18.00
                    WHERE igv_porcentaje IS NULL
                    """);
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
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getInt(1) > 0;
            }
        }
    }
}
