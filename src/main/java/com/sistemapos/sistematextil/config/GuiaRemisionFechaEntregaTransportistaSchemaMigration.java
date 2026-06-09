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
public class GuiaRemisionFechaEntregaTransportistaSchemaMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory
            .getLogger(GuiaRemisionFechaEntregaTransportistaSchemaMigration.class);
    private static final String TABLE_GUIA = "guia_remision";
    private static final String COLUMN_FECHA_ENTREGA_TRANSPORTISTA = "fecha_entrega_transportista";

    private final DataSource dataSource;

    public GuiaRemisionFechaEntregaTransportistaSchemaMigration(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            if (!tableExists(connection, TABLE_GUIA)
                    || columnExists(connection, TABLE_GUIA, COLUMN_FECHA_ENTREGA_TRANSPORTISTA)) {
                return;
            }

            statement.execute("""
                    ALTER TABLE guia_remision
                    ADD COLUMN fecha_entrega_transportista DATE DEFAULT NULL
                    AFTER fecha_inicio_traslado
                    """);
            log.info("Columna {}.{} agregada", TABLE_GUIA, COLUMN_FECHA_ENTREGA_TRANSPORTISTA);
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
}
