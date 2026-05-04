package com.sistemapos.sistematextil.config;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class SunatJobSchemaMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SunatJobSchemaMigration.class);

    private final DataSource dataSource;

    public SunatJobSchemaMigration(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS sunat_job (
                      id_sunat_job BIGINT NOT NULL AUTO_INCREMENT,
                      tipo_documento VARCHAR(30) NOT NULL,
                      documento_id INT NOT NULL,
                      estado VARCHAR(30) NOT NULL DEFAULT 'PENDIENTE_ENVIO',
                      intentos INT NOT NULL DEFAULT 0,
                      max_intentos INT NOT NULL DEFAULT 10,
                      ultimo_error VARCHAR(1000) DEFAULT NULL,
                      ultimo_codigo VARCHAR(40) DEFAULT NULL,
                      ticket_sunat VARCHAR(120) DEFAULT NULL,
                      next_retry_at DATETIME(6) DEFAULT NULL,
                      locked_at DATETIME(6) DEFAULT NULL,
                      last_attempt_at DATETIME(6) DEFAULT NULL,
                      processed_at DATETIME(6) DEFAULT NULL,
                      created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                      updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
                      PRIMARY KEY (id_sunat_job),
                      UNIQUE KEY uk_sunat_job_documento (tipo_documento, documento_id),
                      KEY idx_sunat_job_estado_retry (estado, next_retry_at),
                      KEY idx_sunat_job_locked_at (locked_at)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
                    """);
            ensureColumn(statement, connection, "sunat_job", "ticket_sunat",
                    "ALTER TABLE sunat_job ADD COLUMN ticket_sunat VARCHAR(120) DEFAULT NULL AFTER ultimo_codigo");
        }
    }

    private void ensureColumn(Statement statement, Connection connection, String table, String column, String sql)
            throws Exception {
        if (!columnExists(connection, table, column)) {
            statement.execute(sql);
            log.info("Columna {}.{} creada", table, column);
        }
    }

    private boolean columnExists(Connection connection, String tableName, String columnName) throws Exception {
        try (ResultSet columns = connection.getMetaData()
                .getColumns(connection.getCatalog(), null, tableName, columnName)) {
            return columns.next();
        }
    }
}
