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
public class ProductoVarianteBarcodeMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProductoVarianteBarcodeMigration.class);
    private static final String TABLE_NAME = "producto_variante";
    private static final String COLUMN_NAME = "codigo_barras";
    private static final String INDEX_NAME = "uk_variante_sucursal_codigo_barras";

    private final DataSource dataSource;

    public ProductoVarianteBarcodeMigration(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            if (!tableExists(connection, TABLE_NAME)) {
                return;
            }

            asegurarColumnaCodigoBarras(connection, statement);
            normalizarCodigosBarrasVacios(statement);
            validarCodigosBarrasDuplicados(connection);
            asegurarIndiceCodigoBarras(connection, statement);
        }
    }

    private void asegurarColumnaCodigoBarras(Connection connection, Statement statement) throws Exception {
        if (columnExists(connection, TABLE_NAME, COLUMN_NAME)) {
            return;
        }
        statement.execute("""
                ALTER TABLE producto_variante
                ADD COLUMN codigo_barras VARCHAR(100) DEFAULT NULL AFTER sku
                """);
        log.info("Columna {}.{} creada", TABLE_NAME, COLUMN_NAME);
    }

    private void normalizarCodigosBarrasVacios(Statement statement) throws Exception {
        statement.executeUpdate("""
                UPDATE producto_variante
                SET codigo_barras = NULL
                WHERE codigo_barras = ''
                """);
    }

    private void validarCodigosBarrasDuplicados(Connection connection) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT sucursal_id, codigo_barras, COUNT(*) AS total
                FROM producto_variante
                WHERE codigo_barras IS NOT NULL
                GROUP BY sucursal_id, codigo_barras
                HAVING COUNT(*) > 1
                LIMIT 1
                """);
                ResultSet resultSet = statement.executeQuery()) {
            if (!resultSet.next()) {
                return;
            }
            throw new IllegalStateException(
                    "Existen codigos de barras duplicados en producto_variante. "
                            + "Sucursal: " + resultSet.getInt("sucursal_id")
                            + ", codigo_barras: " + resultSet.getString("codigo_barras")
                            + ", total: " + resultSet.getInt("total"));
        }
    }

    private void asegurarIndiceCodigoBarras(Connection connection, Statement statement) throws Exception {
        if (indexExists(connection, TABLE_NAME, INDEX_NAME)) {
            return;
        }
        statement.execute("""
                ALTER TABLE producto_variante
                ADD UNIQUE KEY uk_variante_sucursal_codigo_barras (sucursal_id, codigo_barras)
                """);
        log.info("Indice {} creado en {}", INDEX_NAME, TABLE_NAME);
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
