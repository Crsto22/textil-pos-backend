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
public class UsuarioSecuritySchemaMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(UsuarioSecuritySchemaMigration.class);

    private final DataSource dataSource;

    public UsuarioSecuritySchemaMigration(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            ensureColumn(statement, connection, "usuario", "refresh_token_version",
                    "ALTER TABLE usuario ADD COLUMN refresh_token_version INT NOT NULL DEFAULT 0 AFTER deleted_at");
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
