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
public class ClienteTelefonoUniqueMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ClienteTelefonoUniqueMigration.class);
    private static final String TABLE_NAME = "cliente";
    private static final String COLUMN_NAME = "telefono";
    private static final String INDEX_NAME = "uk_cliente_empresa_telefono";

    private final DataSource dataSource;

    public ClienteTelefonoUniqueMigration(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            if (!tableExists(connection, TABLE_NAME)
                    || !columnExists(connection, TABLE_NAME, "id_empresa")
                    || !columnExists(connection, TABLE_NAME, COLUMN_NAME)
                    || indexExists(connection, TABLE_NAME, INDEX_NAME)) {
                return;
            }

            normalizarTelefonos(statement);
            validarTelefonosDuplicados(connection);
            statement.execute("""
                    ALTER TABLE cliente
                    ADD UNIQUE KEY uk_cliente_empresa_telefono (id_empresa, telefono)
                    """);
            log.info("Indice {} creado en {}", INDEX_NAME, TABLE_NAME);
        }
    }

    private void normalizarTelefonos(Statement statement) throws Exception {
        statement.executeUpdate("""
                UPDATE cliente
                SET telefono = NULLIF(TRIM(telefono), '')
                WHERE telefono IS NOT NULL
                """);
    }

    private void validarTelefonosDuplicados(Connection connection) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id_empresa, telefono, COUNT(*) AS total
                FROM cliente
                WHERE telefono IS NOT NULL
                GROUP BY id_empresa, telefono
                HAVING COUNT(*) > 1
                LIMIT 1
                """);
                ResultSet resultSet = statement.executeQuery()) {
            if (!resultSet.next()) {
                return;
            }
            throw new IllegalStateException(
                    "Existen telefonos duplicados en cliente. "
                            + "Empresa: " + resultSet.getInt("id_empresa")
                            + ", telefono: " + resultSet.getString("telefono")
                            + ", total: " + resultSet.getInt("total"));
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
