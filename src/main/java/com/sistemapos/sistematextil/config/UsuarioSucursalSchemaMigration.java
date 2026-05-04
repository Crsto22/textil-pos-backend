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
public class UsuarioSucursalSchemaMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(UsuarioSucursalSchemaMigration.class);

    private final DataSource dataSource;

    public UsuarioSucursalSchemaMigration(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            if (!tableExists(connection, "usuario") || !tableExists(connection, "sucursal")) {
                return;
            }

            crearTablaSiNoExiste(connection, statement);
            poblarDesdeSucursalPrincipal(statement);
        }
    }

    private void crearTablaSiNoExiste(Connection connection, Statement statement) throws Exception {
        if (tableExists(connection, "usuario_sucursal")) {
            return;
        }

        statement.execute("""
                CREATE TABLE usuario_sucursal (
                  id_usuario_sucursal INT(11) NOT NULL AUTO_INCREMENT,
                  id_usuario INT(11) NOT NULL,
                  id_sucursal INT(11) NOT NULL,
                  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                  PRIMARY KEY (id_usuario_sucursal),
                  UNIQUE KEY uk_usuario_sucursal_usuario_sucursal (id_usuario, id_sucursal),
                  KEY idx_usuario_sucursal_sucursal (id_sucursal),
                  CONSTRAINT fk_usuario_sucursal_usuario
                    FOREIGN KEY (id_usuario) REFERENCES usuario (id_usuario)
                    ON DELETE CASCADE ON UPDATE RESTRICT,
                  CONSTRAINT fk_usuario_sucursal_sucursal
                    FOREIGN KEY (id_sucursal) REFERENCES sucursal (id_sucursal)
                    ON DELETE RESTRICT ON UPDATE RESTRICT
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
                """);
        log.info("Tabla usuario_sucursal creada");
    }

    private void poblarDesdeSucursalPrincipal(Statement statement) throws Exception {
        int insertedRows = statement.executeUpdate("""
                INSERT IGNORE INTO usuario_sucursal (id_usuario, id_sucursal)
                SELECT u.id_usuario, u.id_sucursal
                FROM usuario u
                WHERE u.id_sucursal IS NOT NULL
                  AND u.deleted_at IS NULL
                """);

        if (insertedRows > 0) {
            log.info("Se migraron {} asignaciones iniciales a usuario_sucursal", insertedRows);
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
}
