package com.sistemapos.sistematextil.config;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import javax.sql.DataSource;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class GuiaRemisionCatalogosSchemaMigration implements ApplicationRunner {

    private final DataSource dataSource;

    public GuiaRemisionCatalogosSchemaMigration(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            if (!tableExists(connection, "empresa")) {
                return;
            }

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS guia_remision_catalogo_conductor (
                      id_catalogo_conductor INT NOT NULL AUTO_INCREMENT,
                      id_empresa INT NOT NULL,
                      tipo_documento VARCHAR(1) NOT NULL DEFAULT '1',
                      nro_documento VARCHAR(20) NOT NULL,
                      nombres VARCHAR(100) NOT NULL,
                      apellidos VARCHAR(100) NOT NULL,
                      licencia VARCHAR(20) NOT NULL,
                      es_principal TINYINT(1) NOT NULL DEFAULT 1,
                      activo TINYINT(1) NOT NULL DEFAULT 1,
                      created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                      updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
                      deleted_at DATETIME(6) DEFAULT NULL,
                      PRIMARY KEY (id_catalogo_conductor),
                      KEY idx_gr_catalogo_conductor_empresa (id_empresa, deleted_at),
                      KEY idx_gr_catalogo_conductor_doc (id_empresa, nro_documento, deleted_at),
                      CONSTRAINT fk_gr_catalogo_conductor_empresa
                        FOREIGN KEY (id_empresa) REFERENCES empresa (id_empresa)
                        ON DELETE RESTRICT ON UPDATE RESTRICT
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
                    """);

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS guia_remision_catalogo_transportista (
                      id_catalogo_transportista INT NOT NULL AUTO_INCREMENT,
                      id_empresa INT NOT NULL,
                      transportista_tipo_doc VARCHAR(1) NOT NULL DEFAULT '6',
                      transportista_nro_doc VARCHAR(20) NOT NULL,
                      transportista_razon_social VARCHAR(255) NOT NULL,
                      transportista_registro_mtc VARCHAR(20) DEFAULT NULL,
                      activo TINYINT(1) NOT NULL DEFAULT 1,
                      created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                      updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
                      deleted_at DATETIME(6) DEFAULT NULL,
                      PRIMARY KEY (id_catalogo_transportista),
                      KEY idx_gr_catalogo_transportista_empresa (id_empresa, deleted_at),
                      KEY idx_gr_catalogo_transportista_doc (id_empresa, transportista_nro_doc, deleted_at),
                      CONSTRAINT fk_gr_catalogo_transportista_empresa
                        FOREIGN KEY (id_empresa) REFERENCES empresa (id_empresa)
                        ON DELETE RESTRICT ON UPDATE RESTRICT
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
                    """);

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS guia_remision_catalogo_vehiculo (
                      id_catalogo_vehiculo INT NOT NULL AUTO_INCREMENT,
                      id_empresa INT NOT NULL,
                      placa VARCHAR(10) NOT NULL,
                      es_principal TINYINT(1) NOT NULL DEFAULT 1,
                      activo TINYINT(1) NOT NULL DEFAULT 1,
                      created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                      updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
                      deleted_at DATETIME(6) DEFAULT NULL,
                      PRIMARY KEY (id_catalogo_vehiculo),
                      KEY idx_gr_catalogo_vehiculo_empresa (id_empresa, deleted_at),
                      KEY idx_gr_catalogo_vehiculo_placa (id_empresa, placa, deleted_at),
                      CONSTRAINT fk_gr_catalogo_vehiculo_empresa
                        FOREIGN KEY (id_empresa) REFERENCES empresa (id_empresa)
                        ON DELETE RESTRICT ON UPDATE RESTRICT
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
                    """);
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
