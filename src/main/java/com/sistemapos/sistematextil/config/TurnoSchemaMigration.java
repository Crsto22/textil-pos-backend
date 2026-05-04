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
public class TurnoSchemaMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TurnoSchemaMigration.class);

    private final DataSource dataSource;

    public TurnoSchemaMigration(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            crearTablaTurnoSiNoExiste(connection, statement);
            asegurarTablaTurnoDia(connection, statement);
            asegurarRelacionUsuarioTurno(connection, statement);
            poblarDiasSemanaParaTurnosExistentes(statement);
        }
    }

    private void crearTablaTurnoSiNoExiste(Connection connection, Statement statement) throws Exception {
        if (tableExists(connection, "turno")) {
            return;
        }

        statement.execute("""
                CREATE TABLE turno (
                  id_turno INT(11) NOT NULL AUTO_INCREMENT,
                  nombre VARCHAR(80) NOT NULL,
                  hora_inicio TIME NOT NULL,
                  hora_fin TIME NOT NULL,
                  activo TINYINT(1) NOT NULL DEFAULT 1,
                  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
                  deleted_at DATETIME(6) DEFAULT NULL,
                  PRIMARY KEY (id_turno),
                  KEY idx_turno_nombre (nombre)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
                """);
        log.info("Tabla turno creada");
    }

    private void asegurarTablaTurnoDia(Connection connection, Statement statement) throws Exception {
        if (!tableExists(connection, "turno_dia")) {
            statement.execute("""
                    CREATE TABLE turno_dia (
                      id_turno_dia INT(11) NOT NULL AUTO_INCREMENT,
                      id_turno INT(11) NOT NULL,
                      dia_semana ENUM('LUNES','MARTES','MIERCOLES','JUEVES','VIERNES','SABADO','DOMINGO') NOT NULL,
                      PRIMARY KEY (id_turno_dia),
                      UNIQUE KEY uk_turno_dia_turno_dia (id_turno, dia_semana),
                      CONSTRAINT fk_turno_dia_turno
                        FOREIGN KEY (id_turno) REFERENCES turno (id_turno)
                        ON DELETE CASCADE ON UPDATE RESTRICT
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
                    """);
            log.info("Tabla turno_dia creada");
            return;
        }

        if (!columnExists(connection, "turno_dia", "dia_semana")) {
            statement.execute("""
                    ALTER TABLE turno_dia
                    ADD COLUMN dia_semana ENUM('LUNES','MARTES','MIERCOLES','JUEVES','VIERNES','SABADO','DOMINGO') NOT NULL
                    """);
            log.info("Columna turno_dia.dia_semana creada");
        }

        if (!indexExists(connection, "turno_dia", "uk_turno_dia_turno_dia")) {
            statement.execute("""
                    ALTER TABLE turno_dia
                    ADD CONSTRAINT uk_turno_dia_turno_dia UNIQUE (id_turno, dia_semana)
                    """);
            log.info("Restriccion unica uk_turno_dia_turno_dia creada");
        }

        if (!foreignKeyExists(connection, "turno_dia", "fk_turno_dia_turno")) {
            statement.execute("""
                    ALTER TABLE turno_dia
                    ADD CONSTRAINT fk_turno_dia_turno
                    FOREIGN KEY (id_turno) REFERENCES turno (id_turno)
                    ON DELETE CASCADE ON UPDATE RESTRICT
                    """);
            log.info("FK fk_turno_dia_turno creada");
        }
    }

    private void asegurarRelacionUsuarioTurno(Connection connection, Statement statement) throws Exception {
        if (!tableExists(connection, "usuario")) {
            return;
        }

        if (!columnExists(connection, "usuario", "id_turno")) {
            statement.execute("""
                    ALTER TABLE usuario
                    ADD COLUMN id_turno INT(11) DEFAULT NULL AFTER id_sucursal
                    """);
            log.info("Columna usuario.id_turno creada");
        }

        if (!indexExists(connection, "usuario", "idx_usuario_turno")) {
            statement.execute("""
                    ALTER TABLE usuario
                    ADD INDEX idx_usuario_turno (id_turno)
                    """);
            log.info("Indice idx_usuario_turno creado");
        }

        if (!foreignKeyExists(connection, "usuario", "fk_usuario_turno")) {
            statement.execute("""
                    ALTER TABLE usuario
                    ADD CONSTRAINT fk_usuario_turno
                    FOREIGN KEY (id_turno) REFERENCES turno (id_turno)
                    ON DELETE RESTRICT ON UPDATE RESTRICT
                    """);
            log.info("FK fk_usuario_turno creada");
        }
    }

    private void poblarDiasSemanaParaTurnosExistentes(Statement statement) throws Exception {
        int insertedRows = statement.executeUpdate("""
                INSERT INTO turno_dia (id_turno, dia_semana)
                SELECT turno_sin_dias.id_turno, dias.dia_semana
                FROM (
                    SELECT t.id_turno
                    FROM turno t
                    WHERE NOT EXISTS (
                        SELECT 1
                        FROM turno_dia td
                        WHERE td.id_turno = t.id_turno
                    )
                ) turno_sin_dias
                CROSS JOIN (
                    SELECT 'LUNES' AS dia_semana
                    UNION ALL SELECT 'MARTES'
                    UNION ALL SELECT 'MIERCOLES'
                    UNION ALL SELECT 'JUEVES'
                    UNION ALL SELECT 'VIERNES'
                    UNION ALL SELECT 'SABADO'
                    UNION ALL SELECT 'DOMINGO'
                ) dias
                """);

        if (insertedRows > 0) {
            log.info("Se poblaron {} filas en turno_dia para turnos existentes", insertedRows);
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
                FROM information_schema.TABLE_CONSTRAINTS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND CONSTRAINT_NAME = ?
                  AND CONSTRAINT_TYPE = 'FOREIGN KEY'
                """)) {
            statement.setString(1, tableName);
            statement.setString(2, constraintName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getInt(1) > 0;
            }
        }
    }
}
