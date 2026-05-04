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
public class ProductoVarianteOfertaUsuarioCreacionSchemaMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProductoVarianteOfertaUsuarioCreacionSchemaMigration.class);
    private static final String TABLE_NAME = "producto_variante";

    private final DataSource dataSource;

    public ProductoVarianteOfertaUsuarioCreacionSchemaMigration(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            if (!tableExists(connection, TABLE_NAME) || !tableExists(connection, "usuario")) {
                return;
            }

            asegurarColumna(connection, statement);
            asegurarIndice(connection, statement);
            asegurarForeignKey(connection, statement);
        }
    }

    private void asegurarColumna(Connection connection, Statement statement) throws Exception {
        if (columnExists(connection, TABLE_NAME, "id_usuario_creacion")) {
            return;
        }
        statement.execute("""
                ALTER TABLE producto_variante
                ADD COLUMN id_usuario_creacion INT DEFAULT NULL AFTER oferta_fin
                """);
        log.info("Columna {}.id_usuario_creacion creada", TABLE_NAME);
    }

    private void asegurarIndice(Connection connection, Statement statement) throws Exception {
        if (indexExists(connection, TABLE_NAME, "idx_producto_variante_usuario_creacion")) {
            return;
        }
        statement.execute("""
                ALTER TABLE producto_variante
                ADD INDEX idx_producto_variante_usuario_creacion (id_usuario_creacion)
                """);
        log.info("Indice idx_producto_variante_usuario_creacion creado");
    }

    private void asegurarForeignKey(Connection connection, Statement statement) throws Exception {
        if (foreignKeyExists(connection, TABLE_NAME, "fk_producto_variante_usuario_creacion")) {
            return;
        }
        statement.execute("""
                ALTER TABLE producto_variante
                ADD CONSTRAINT fk_producto_variante_usuario_creacion
                FOREIGN KEY (id_usuario_creacion) REFERENCES usuario(id_usuario)
                """);
        log.info("Foreign key fk_producto_variante_usuario_creacion creada");
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
            try (var resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getInt(1) > 0;
            }
        }
    }

    private boolean foreignKeyExists(Connection connection, String tableName, String constraintName) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COUNT(*)
                FROM information_schema.TABLE_CONSTRAINTS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND CONSTRAINT_NAME = ?
                  AND CONSTRAINT_TYPE = 'FOREIGN KEY'
                """)) {
            statement.setString(1, tableName);
            statement.setString(2, constraintName);
            try (var resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getInt(1) > 0;
            }
        }
    }
}
