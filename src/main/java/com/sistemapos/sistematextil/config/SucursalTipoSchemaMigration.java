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
public class SucursalTipoSchemaMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SucursalTipoSchemaMigration.class);

    private final DataSource dataSource;

    public SucursalTipoSchemaMigration(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            if (!tableExists(connection, "sucursal")) {
                return;
            }

            asegurarColumnaTipo(connection, statement);
            completarTiposNulos(statement);
            normalizarDefinicionColumna(connection, statement);
        }
    }

    private void asegurarColumnaTipo(Connection connection, Statement statement) throws Exception {
        if (columnExists(connection, "sucursal", "tipo")) {
            return;
        }
        statement.execute("""
                ALTER TABLE sucursal
                ADD COLUMN tipo ENUM('VENTA','ALMACEN') DEFAULT 'VENTA' AFTER codigo_establecimiento_sunat
                """);
        log.info("Columna sucursal.tipo creada");
    }

    private void completarTiposNulos(Statement statement) throws Exception {
        statement.executeUpdate("""
                UPDATE sucursal
                SET tipo = 'VENTA'
                WHERE tipo IS NULL
                """);
    }

    private void normalizarDefinicionColumna(Connection connection, Statement statement) throws Exception {
        if (!columnExists(connection, "sucursal", "tipo")) {
            return;
        }
        statement.execute("""
                ALTER TABLE sucursal
                MODIFY COLUMN tipo ENUM('VENTA','ALMACEN') NOT NULL DEFAULT 'VENTA'
                """);
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
