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
public class GuiaRemisionDocumentoRelacionadoSchemaMigration implements ApplicationRunner {

    private final DataSource dataSource;

    public GuiaRemisionDocumentoRelacionadoSchemaMigration(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            if (!tableExists(connection, "guia_remision")) {
                return;
            }

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS guia_remision_documento_relacionado (
                      id_guia_documento_relacionado INT NOT NULL AUTO_INCREMENT,
                      id_guia_remision INT NOT NULL,
                      tipo_documento VARCHAR(2) NOT NULL,
                      serie VARCHAR(4) NOT NULL,
                      numero VARCHAR(20) NOT NULL,
                      activo TINYINT(1) NOT NULL DEFAULT 1,
                      created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                      updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
                      deleted_at DATETIME(6) DEFAULT NULL,
                      PRIMARY KEY (id_guia_documento_relacionado),
                      KEY idx_gr_doc_rel_guia (id_guia_remision, deleted_at),
                      KEY idx_gr_doc_rel_numero (tipo_documento, serie, numero, deleted_at),
                      CONSTRAINT fk_gr_doc_rel_guia
                        FOREIGN KEY (id_guia_remision) REFERENCES guia_remision (id_guia_remision)
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
