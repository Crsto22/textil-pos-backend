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
public class ProductoVarianteOfertaSucursalSchemaMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProductoVarianteOfertaSucursalSchemaMigration.class);
    private static final String TABLE_NAME = "producto_variante_oferta_sucursal";

    private final DataSource dataSource;

    public ProductoVarianteOfertaSucursalSchemaMigration(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            if (!tableExists(connection, "producto_variante")
                    || !tableExists(connection, "sucursal")
                    || !tableExists(connection, "usuario")) {
                return;
            }

            if (!tableExists(connection, TABLE_NAME)) {
                statement.execute("""
                        CREATE TABLE producto_variante_oferta_sucursal (
                            id_producto_variante_oferta_sucursal INT NOT NULL AUTO_INCREMENT,
                            id_producto_variante INT NOT NULL,
                            id_sucursal INT NOT NULL,
                            precio_oferta DECIMAL(10,2) DEFAULT NULL,
                            oferta_inicio DATETIME(6) DEFAULT NULL,
                            oferta_fin DATETIME(6) DEFAULT NULL,
                            id_usuario_creacion INT DEFAULT NULL,
                            created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                            updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
                            deleted_at DATETIME(6) DEFAULT NULL,
                            PRIMARY KEY (id_producto_variante_oferta_sucursal),
                            CONSTRAINT fk_pvos_variante FOREIGN KEY (id_producto_variante)
                                REFERENCES producto_variante(id_producto_variante),
                            CONSTRAINT fk_pvos_sucursal FOREIGN KEY (id_sucursal)
                                REFERENCES sucursal(id_sucursal),
                            CONSTRAINT fk_pvos_usuario_creacion FOREIGN KEY (id_usuario_creacion)
                                REFERENCES usuario(id_usuario),
                            CONSTRAINT uk_pvos_variante_sucursal UNIQUE (id_producto_variante, id_sucursal)
                        )
                        """);
                log.info("Tabla {} creada", TABLE_NAME);
                return;
            }

            asegurarColumna(connection, statement, "precio_oferta", "DECIMAL(10,2) DEFAULT NULL");
            asegurarColumna(connection, statement, "oferta_inicio", "DATETIME(6) DEFAULT NULL");
            asegurarColumna(connection, statement, "oferta_fin", "DATETIME(6) DEFAULT NULL");
            asegurarColumna(connection, statement, "id_usuario_creacion", "INT DEFAULT NULL");
            asegurarColumna(connection, statement, "created_at", "DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)");
            asegurarColumna(connection, statement, "updated_at",
                    "DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)");
            asegurarColumna(connection, statement, "deleted_at", "DATETIME(6) DEFAULT NULL");
            asegurarIndiceUnico(connection, statement);
            asegurarIndice(connection, statement, "idx_pvos_usuario_creacion", "id_usuario_creacion");
            asegurarForeignKey(connection, statement, "fk_pvos_usuario_creacion",
                    "id_usuario_creacion", "usuario", "id_usuario");
        }
    }

    private void asegurarColumna(Connection connection, Statement statement, String columnName, String definition)
            throws Exception {
        if (columnExists(connection, TABLE_NAME, columnName)) {
            return;
        }
        statement.execute("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + columnName + " " + definition);
        log.info("Columna {}.{} creada", TABLE_NAME, columnName);
    }

    private void asegurarIndiceUnico(Connection connection, Statement statement) throws Exception {
        if (indexExists(connection, TABLE_NAME, "uk_pvos_variante_sucursal")) {
            return;
        }
        statement.execute("""
                ALTER TABLE producto_variante_oferta_sucursal
                ADD CONSTRAINT uk_pvos_variante_sucursal UNIQUE (id_producto_variante, id_sucursal)
                """);
        log.info("Indice unico uk_pvos_variante_sucursal creado");
    }

    private void asegurarIndice(Connection connection, Statement statement, String indexName, String columnName)
            throws Exception {
        if (indexExists(connection, TABLE_NAME, indexName)) {
            return;
        }
        statement.execute("ALTER TABLE " + TABLE_NAME + " ADD INDEX " + indexName + " (" + columnName + ")");
        log.info("Indice {} creado", indexName);
    }

    private void asegurarForeignKey(
            Connection connection,
            Statement statement,
            String constraintName,
            String columnName,
            String referencedTable,
            String referencedColumn) throws Exception {
        if (foreignKeyExists(connection, TABLE_NAME, constraintName)) {
            return;
        }
        statement.execute("ALTER TABLE " + TABLE_NAME
                + " ADD CONSTRAINT " + constraintName
                + " FOREIGN KEY (" + columnName + ") REFERENCES "
                + referencedTable + "(" + referencedColumn + ")");
        log.info("Foreign key {} creada", constraintName);
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
