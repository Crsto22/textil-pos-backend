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
public class ClienteEmpresaSchemaMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ClienteEmpresaSchemaMigration.class);

    private final DataSource dataSource;

    public ClienteEmpresaSchemaMigration(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            if (!tableExists(connection, "cliente")) {
                return;
            }

            asegurarColumnaEmpresa(connection, statement);
            migrarEmpresaDesdeSucursal(connection, statement);
            completarEmpresaFaltante(connection, statement);
            asegurarEmpresaNoNula(connection, statement);
            asegurarIndiceEmpresa(connection, statement);
            asegurarForeignKeyEmpresa(connection, statement);
            eliminarRelacionSucursal(connection, statement);
        }
    }

    private void asegurarColumnaEmpresa(Connection connection, Statement statement) throws Exception {
        if (columnExists(connection, "cliente", "id_empresa")) {
            return;
        }
        statement.execute("""
                ALTER TABLE cliente
                ADD COLUMN id_empresa INT(11) DEFAULT NULL AFTER id_cliente
                """);
        log.info("Columna cliente.id_empresa creada");
    }

    private void migrarEmpresaDesdeSucursal(Connection connection, Statement statement) throws Exception {
        if (!columnExists(connection, "cliente", "id_empresa") || !columnExists(connection, "cliente", "id_sucursal")) {
            return;
        }
        statement.executeUpdate("""
                UPDATE cliente c
                JOIN sucursal s ON s.id_sucursal = c.id_sucursal
                SET c.id_empresa = COALESCE(c.id_empresa, s.id_empresa)
                WHERE c.id_empresa IS NULL
                """);
    }

    private void completarEmpresaFaltante(Connection connection, Statement statement) throws Exception {
        if (!columnExists(connection, "cliente", "id_empresa")) {
            return;
        }
        if (!hasClientesSinEmpresa(connection)) {
            return;
        }
        Integer idEmpresaDefault = obtenerEmpresaPorDefecto(connection);
        if (idEmpresaDefault != null) {
            statement.executeUpdate("""
                    UPDATE cliente
                    SET id_empresa = %d
                    WHERE id_empresa IS NULL
                    """.formatted(idEmpresaDefault));
        }
        if (hasClientesSinEmpresa(connection)) {
            throw new IllegalStateException("No se pudo migrar cliente.id_empresa para todos los registros");
        }
    }

    private void asegurarEmpresaNoNula(Connection connection, Statement statement) throws Exception {
        if (!columnExists(connection, "cliente", "id_empresa")) {
            return;
        }
        statement.execute("""
                ALTER TABLE cliente
                MODIFY COLUMN id_empresa INT(11) NOT NULL
                """);
    }

    private void asegurarIndiceEmpresa(Connection connection, Statement statement) throws Exception {
        if (indexExists(connection, "cliente", "idx_cliente_empresa")) {
            return;
        }
        statement.execute("""
                ALTER TABLE cliente
                ADD KEY idx_cliente_empresa (id_empresa)
                """);
        log.info("Indice idx_cliente_empresa creado");
    }

    private void asegurarForeignKeyEmpresa(Connection connection, Statement statement) throws Exception {
        if (foreignKeyExists(connection, "cliente", "fk_cliente_empresa")) {
            return;
        }
        statement.execute("""
                ALTER TABLE cliente
                ADD CONSTRAINT fk_cliente_empresa
                FOREIGN KEY (id_empresa) REFERENCES empresa (id_empresa)
                ON DELETE RESTRICT ON UPDATE RESTRICT
                """);
        log.info("FK fk_cliente_empresa creada");
    }

    private void eliminarRelacionSucursal(Connection connection, Statement statement) throws Exception {
        if (foreignKeyExists(connection, "cliente", "fk_cliente_sucursal")) {
            statement.execute("ALTER TABLE cliente DROP FOREIGN KEY fk_cliente_sucursal");
            log.info("FK fk_cliente_sucursal eliminada");
        }
        if (indexExists(connection, "cliente", "idx_cliente_sucursal")) {
            statement.execute("ALTER TABLE cliente DROP INDEX idx_cliente_sucursal");
            log.info("Indice idx_cliente_sucursal eliminado");
        }
        if (columnExists(connection, "cliente", "id_sucursal")) {
            statement.execute("ALTER TABLE cliente DROP COLUMN id_sucursal");
            log.info("Columna cliente.id_sucursal eliminada");
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

    private boolean foreignKeyExists(Connection connection, String tableName, String constraintName) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COUNT(*)
                FROM information_schema.REFERENTIAL_CONSTRAINTS
                WHERE CONSTRAINT_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND CONSTRAINT_NAME = ?
                """)) {
            statement.setString(1, tableName);
            statement.setString(2, constraintName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getInt(1) > 0;
            }
        }
    }

    private boolean hasClientesSinEmpresa(Connection connection) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COUNT(*)
                FROM cliente
                WHERE id_empresa IS NULL
                """);
                ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() && resultSet.getInt(1) > 0;
        }
    }

    private Integer obtenerEmpresaPorDefecto(Connection connection) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id_empresa
                FROM empresa
                ORDER BY id_empresa ASC
                LIMIT 1
                """);
                ResultSet resultSet = statement.executeQuery()) {
            if (!resultSet.next()) {
                return null;
            }
            return resultSet.getInt(1);
        }
    }
}
