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
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(10)
public class AsistenciaSchemaMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AsistenciaSchemaMigration.class);
    private final DataSource dataSource;

    public AsistenciaSchemaMigration(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            asegurarToleranciaTurno(connection, statement);
            crearCargoTrabajador(connection, statement);
            crearTrabajador(connection, statement);
            asegurarCargoTrabajador(connection, statement);
            asegurarSucursalOpcional(connection, statement);
            asegurarTurnoOpcional(connection, statement);
            asegurarTrabajadorRotativo(connection, statement);
            asegurarUsuarioTrabajador(connection, statement);
            crearDispositivo(connection, statement);
            crearMarcacion(connection, statement);
            asegurarAuditoriaMarcacion(connection, statement);
        }
    }

    private void asegurarToleranciaTurno(Connection connection, Statement statement) throws Exception {
        if (!tableExists(connection, "turno") || columnExists(connection, "turno", "tolerancia_minutos")) {
            return;
        }
        statement.execute("""
                ALTER TABLE turno
                ADD COLUMN tolerancia_minutos INT NOT NULL DEFAULT 10 AFTER hora_fin
                """);
        log.info("Columna turno.tolerancia_minutos creada");
    }

    private void crearTrabajador(Connection connection, Statement statement) throws Exception {
        if (tableExists(connection, "trabajador")) {
            return;
        }
        statement.execute("""
                CREATE TABLE trabajador (
                  id_trabajador INT NOT NULL AUTO_INCREMENT,
                  codigo_zkteco VARCHAR(24) NOT NULL,
                  dni VARCHAR(8) NOT NULL,
                  nombres VARCHAR(100) NOT NULL,
                  apellidos VARCHAR(100) NOT NULL,
                  activo TINYINT(1) NOT NULL DEFAULT 1,
                  id_sucursal INT DEFAULT NULL,
                  id_turno INT DEFAULT NULL,
                  id_cargo INT DEFAULT NULL,
                  rotativo TINYINT(1) NOT NULL DEFAULT 0,
                  id_usuario INT DEFAULT NULL,
                  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
                  deleted_at DATETIME(6) DEFAULT NULL,
                  PRIMARY KEY (id_trabajador),
                  UNIQUE KEY uk_trabajador_codigo_zkteco (codigo_zkteco),
                  UNIQUE KEY uk_trabajador_dni (dni),
                  KEY idx_trabajador_sucursal (id_sucursal),
                  KEY idx_trabajador_turno (id_turno),
                  KEY idx_trabajador_cargo (id_cargo),
                  UNIQUE KEY uk_trabajador_usuario (id_usuario),
                  CONSTRAINT fk_trabajador_sucursal FOREIGN KEY (id_sucursal) REFERENCES sucursal (id_sucursal),
                  CONSTRAINT fk_trabajador_turno FOREIGN KEY (id_turno) REFERENCES turno (id_turno),
                  CONSTRAINT fk_trabajador_cargo FOREIGN KEY (id_cargo) REFERENCES cargo_trabajador (id_cargo),
                  CONSTRAINT fk_trabajador_usuario FOREIGN KEY (id_usuario) REFERENCES usuario (id_usuario)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
                """);
        log.info("Tabla trabajador creada");
    }

    private void crearCargoTrabajador(Connection connection, Statement statement) throws Exception {
        if (tableExists(connection, "cargo_trabajador")) {
            return;
        }
        statement.execute("""
                CREATE TABLE cargo_trabajador (
                  id_cargo INT NOT NULL AUTO_INCREMENT,
                  nombre VARCHAR(100) NOT NULL,
                  activo TINYINT(1) NOT NULL DEFAULT 1,
                  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
                  deleted_at DATETIME(6) DEFAULT NULL,
                  PRIMARY KEY (id_cargo),
                  UNIQUE KEY uk_cargo_trabajador_nombre (nombre)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
                """);
        log.info("Tabla cargo_trabajador creada");
    }

    private void asegurarCargoTrabajador(Connection connection, Statement statement) throws Exception {
        if (!tableExists(connection, "trabajador") || !tableExists(connection, "cargo_trabajador")) {
            return;
        }
        if (!columnExists(connection, "trabajador", "id_cargo")) {
            statement.execute("ALTER TABLE trabajador ADD COLUMN id_cargo INT NULL AFTER id_turno");
            log.info("Columna trabajador.id_cargo creada");
        }
        if (!indexExists(connection, "trabajador", "idx_trabajador_cargo")) {
            statement.execute("ALTER TABLE trabajador ADD KEY idx_trabajador_cargo (id_cargo)");
        }
        if (!constraintExists(connection, "trabajador", "fk_trabajador_cargo")) {
            statement.execute("""
                    ALTER TABLE trabajador
                    ADD CONSTRAINT fk_trabajador_cargo
                    FOREIGN KEY (id_cargo) REFERENCES cargo_trabajador (id_cargo)
                    """);
        }
    }

    private void asegurarTurnoOpcional(Connection connection, Statement statement) throws Exception {
        if (!tableExists(connection, "trabajador") || columnNullable(connection, "trabajador", "id_turno")) {
            return;
        }
        statement.execute("ALTER TABLE trabajador MODIFY COLUMN id_turno INT NULL");
        log.info("Columna trabajador.id_turno ahora permite trabajadores sin turno");
    }

    private void asegurarSucursalOpcional(Connection connection, Statement statement) throws Exception {
        if (!tableExists(connection, "trabajador") || columnNullable(connection, "trabajador", "id_sucursal")) {
            return;
        }
        statement.execute("ALTER TABLE trabajador MODIFY COLUMN id_sucursal INT NULL");
        log.info("Columna trabajador.id_sucursal ahora permite rotativos sin sucursal base");
    }

    private void asegurarTrabajadorRotativo(Connection connection, Statement statement) throws Exception {
        if (!tableExists(connection, "trabajador") || columnExists(connection, "trabajador", "rotativo")) {
            return;
        }
        statement.execute("ALTER TABLE trabajador ADD COLUMN rotativo TINYINT(1) NOT NULL DEFAULT 0 AFTER id_turno");
        log.info("Columna trabajador.rotativo creada");
    }

    private void asegurarUsuarioTrabajador(Connection connection, Statement statement) throws Exception {
        if (!tableExists(connection, "trabajador") || !tableExists(connection, "usuario")) {
            return;
        }
        if (!columnExists(connection, "trabajador", "id_usuario")) {
            statement.execute("ALTER TABLE trabajador ADD COLUMN id_usuario INT NULL AFTER id_turno");
            log.info("Columna trabajador.id_usuario creada");
        }
        if (!indexExists(connection, "trabajador", "uk_trabajador_usuario")) {
            statement.execute("ALTER TABLE trabajador ADD UNIQUE KEY uk_trabajador_usuario (id_usuario)");
        }
        if (!constraintExists(connection, "trabajador", "fk_trabajador_usuario")) {
            statement.execute("""
                    ALTER TABLE trabajador
                    ADD CONSTRAINT fk_trabajador_usuario
                    FOREIGN KEY (id_usuario) REFERENCES usuario (id_usuario)
                    """);
        }
    }

    private void crearDispositivo(Connection connection, Statement statement) throws Exception {
        if (tableExists(connection, "dispositivo_asistencia")) {
            return;
        }
        statement.execute("""
                CREATE TABLE dispositivo_asistencia (
                  id_dispositivo INT NOT NULL AUTO_INCREMENT,
                  numero_serie VARCHAR(80) NOT NULL,
                  nombre VARCHAR(100) NOT NULL,
                  id_sucursal INT NOT NULL,
                  activo TINYINT(1) NOT NULL DEFAULT 1,
                  ultima_conexion DATETIME(6) DEFAULT NULL,
                  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
                  PRIMARY KEY (id_dispositivo),
                  UNIQUE KEY uk_dispositivo_asistencia_serie (numero_serie),
                  KEY idx_dispositivo_asistencia_sucursal (id_sucursal),
                  CONSTRAINT fk_dispositivo_asistencia_sucursal FOREIGN KEY (id_sucursal) REFERENCES sucursal (id_sucursal)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
                """);
        log.info("Tabla dispositivo_asistencia creada");
    }

    private void crearMarcacion(Connection connection, Statement statement) throws Exception {
        if (tableExists(connection, "marcacion_asistencia")) {
            if (!indexExists(connection, "marcacion_asistencia", "uk_marcacion_dispositivo_codigo_fecha")) {
                statement.execute("""
                        ALTER TABLE marcacion_asistencia
                        ADD UNIQUE KEY uk_marcacion_dispositivo_codigo_fecha
                        (id_dispositivo, codigo_zkteco, fecha_hora)
                        """);
                log.info("Indice unico de marcaciones creado");
            }
            return;
        }
        statement.execute("""
                CREATE TABLE marcacion_asistencia (
                  id_marcacion BIGINT NOT NULL AUTO_INCREMENT,
                  id_dispositivo INT DEFAULT NULL,
                  id_sucursal INT NOT NULL,
                  id_trabajador INT DEFAULT NULL,
                  codigo_zkteco VARCHAR(24) NOT NULL,
                  fecha_hora DATETIME(6) NOT NULL,
                  tipo_marcacion VARCHAR(20) DEFAULT NULL,
                  tipo_verificacion VARCHAR(20) DEFAULT NULL,
                  recibido_at DATETIME(6) NOT NULL,
                  origen VARCHAR(12) NOT NULL DEFAULT 'BIOMETRICO',
                  tipo_evento VARCHAR(10) DEFAULT NULL,
                  motivo_registro VARCHAR(255) DEFAULT NULL,
                  id_usuario_registro INT DEFAULT NULL,
                  anulada_at DATETIME(6) DEFAULT NULL,
                  id_usuario_anula INT DEFAULT NULL,
                  motivo_anulacion VARCHAR(255) DEFAULT NULL,
                  PRIMARY KEY (id_marcacion),
                  UNIQUE KEY uk_marcacion_dispositivo_codigo_fecha (id_dispositivo, codigo_zkteco, fecha_hora),
                  KEY idx_marcacion_trabajador_fecha (id_trabajador, fecha_hora),
                  KEY idx_marcacion_fecha (fecha_hora),
                  KEY idx_marcacion_sucursal_fecha (id_sucursal, fecha_hora),
                  CONSTRAINT fk_marcacion_dispositivo FOREIGN KEY (id_dispositivo) REFERENCES dispositivo_asistencia (id_dispositivo),
                  CONSTRAINT fk_marcacion_sucursal FOREIGN KEY (id_sucursal) REFERENCES sucursal (id_sucursal),
                  CONSTRAINT fk_marcacion_trabajador FOREIGN KEY (id_trabajador) REFERENCES trabajador (id_trabajador),
                  CONSTRAINT fk_marcacion_usuario_registro FOREIGN KEY (id_usuario_registro) REFERENCES usuario (id_usuario),
                  CONSTRAINT fk_marcacion_usuario_anula FOREIGN KEY (id_usuario_anula) REFERENCES usuario (id_usuario)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
                """);
        log.info("Tabla marcacion_asistencia creada");
    }

    private void asegurarAuditoriaMarcacion(Connection connection, Statement statement) throws Exception {
        if (!columnExists(connection, "marcacion_asistencia", "id_sucursal")) {
            statement.execute("ALTER TABLE marcacion_asistencia ADD COLUMN id_sucursal INT NULL AFTER id_dispositivo");
            statement.execute("""
                    UPDATE marcacion_asistencia m
                    JOIN dispositivo_asistencia d ON d.id_dispositivo = m.id_dispositivo
                    SET m.id_sucursal = d.id_sucursal
                    WHERE m.id_sucursal IS NULL
                    """);
            statement.execute("ALTER TABLE marcacion_asistencia MODIFY COLUMN id_sucursal INT NOT NULL");
        }
        if (!columnNullable(connection, "marcacion_asistencia", "id_dispositivo")) {
            statement.execute("ALTER TABLE marcacion_asistencia MODIFY COLUMN id_dispositivo INT NULL");
        }
        agregarColumnaSiFalta(connection, statement, "origen", "VARCHAR(12) NOT NULL DEFAULT 'BIOMETRICO'");
        agregarColumnaSiFalta(connection, statement, "tipo_evento", "VARCHAR(10) NULL");
        agregarColumnaSiFalta(connection, statement, "motivo_registro", "VARCHAR(255) NULL");
        agregarColumnaSiFalta(connection, statement, "id_usuario_registro", "INT NULL");
        agregarColumnaSiFalta(connection, statement, "anulada_at", "DATETIME(6) NULL");
        agregarColumnaSiFalta(connection, statement, "id_usuario_anula", "INT NULL");
        agregarColumnaSiFalta(connection, statement, "motivo_anulacion", "VARCHAR(255) NULL");
        if (!indexExists(connection, "marcacion_asistencia", "idx_marcacion_sucursal_fecha")) {
            statement.execute("ALTER TABLE marcacion_asistencia ADD KEY idx_marcacion_sucursal_fecha (id_sucursal, fecha_hora)");
        }
        if (!constraintExists(connection, "marcacion_asistencia", "fk_marcacion_sucursal")) {
            statement.execute("ALTER TABLE marcacion_asistencia ADD CONSTRAINT fk_marcacion_sucursal FOREIGN KEY (id_sucursal) REFERENCES sucursal (id_sucursal)");
        }
        if (!constraintExists(connection, "marcacion_asistencia", "fk_marcacion_usuario_registro")) {
            statement.execute("ALTER TABLE marcacion_asistencia ADD CONSTRAINT fk_marcacion_usuario_registro FOREIGN KEY (id_usuario_registro) REFERENCES usuario (id_usuario)");
        }
        if (!constraintExists(connection, "marcacion_asistencia", "fk_marcacion_usuario_anula")) {
            statement.execute("ALTER TABLE marcacion_asistencia ADD CONSTRAINT fk_marcacion_usuario_anula FOREIGN KEY (id_usuario_anula) REFERENCES usuario (id_usuario)");
        }
    }

    private void agregarColumnaSiFalta(
            Connection connection, Statement statement, String columna, String definicion) throws Exception {
        if (!columnExists(connection, "marcacion_asistencia", columna)) {
            statement.execute("ALTER TABLE marcacion_asistencia ADD COLUMN " + columna + " " + definicion);
        }
    }

    private boolean tableExists(Connection connection, String table) throws Exception {
        return exists(connection, """
                SELECT COUNT(*) FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?
                """, table, null);
    }

    private boolean columnExists(Connection connection, String table, String column) throws Exception {
        return exists(connection, """
                SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?
                """, table, column);
    }

    private boolean columnNullable(Connection connection, String table, String column) throws Exception {
        return exists(connection, """
                SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ? AND IS_NULLABLE = 'YES'
                """, table, column);
    }

    private boolean indexExists(Connection connection, String table, String index) throws Exception {
        return exists(connection, """
                SELECT COUNT(*) FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ?
                """, table, index);
    }

    private boolean constraintExists(Connection connection, String table, String constraint) throws Exception {
        return exists(connection, """
                SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
                WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = ? AND CONSTRAINT_NAME = ?
                """, table, constraint);
    }

    private boolean exists(Connection connection, String sql, String first, String second) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, first);
            if (second != null) {
                statement.setString(2, second);
            }
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getInt(1) > 0;
            }
        }
    }
}
