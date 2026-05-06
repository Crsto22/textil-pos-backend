package com.sistemapos.sistematextil.config;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class SunatPdfStorageMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SunatPdfStorageMigration.class);

    private final DataSource dataSource;

    public SunatPdfStorageMigration(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            asegurarColumnas(connection, statement, "venta");
            asegurarColumnas(connection, statement, "nota_credito");
            asegurarColumnas(connection, statement, "guia_remision");
            asegurarColumnas(connection, statement, "cotizacion");
        }
    }

    private void asegurarColumnas(Connection connection, Statement statement, String tableName) throws Exception {
        if (!tableExists(connection, tableName)) {
            return;
        }
        for (ColumnSpec column : columns()) {
            if (columnExists(connection, tableName, column.name())) {
                continue;
            }
            statement.execute("""
                    ALTER TABLE %s
                    ADD COLUMN %s %s DEFAULT NULL
                    """.formatted(tableName, column.name(), column.definition()));
            log.info("Columna {}.{} creada", tableName, column.name());
        }
    }

    private List<ColumnSpec> columns() {
        return List.of(
                new ColumnSpec("sunat_pdf_nombre", "VARCHAR(180)"),
                new ColumnSpec("sunat_pdf_key", "VARCHAR(600)"));
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

    private record ColumnSpec(String name, String definition) {
    }
}
