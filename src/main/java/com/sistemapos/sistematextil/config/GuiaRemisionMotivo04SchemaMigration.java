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
public class GuiaRemisionMotivo04SchemaMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(GuiaRemisionMotivo04SchemaMigration.class);
    private static final String TABLE_GUIA = "guia_remision";

    private final DataSource dataSource;

    public GuiaRemisionMotivo04SchemaMigration(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            if (!tableExists(connection, TABLE_GUIA)) {
                return;
            }

            retirarColumnasCatalogoObsoletas(connection, statement);
            ocultarGuiasLegacyNoPermitidas(statement);
            normalizarDefinicionMotivo(statement);
            recrearTriggersMotivosPermitidos(statement);
        }
    }

    private void retirarColumnasCatalogoObsoletas(Connection connection, Statement statement) throws Exception {
        eliminarIndiceSiExiste(connection, statement, "guia_remision_conductor", "idx_gr_conductor_catalogo");
        eliminarIndiceSiExiste(connection, statement, "guia_remision_transportista", "idx_gr_transportista_catalogo");
        eliminarIndiceSiExiste(connection, statement, "guia_remision_vehiculo", "idx_gr_vehiculo_catalogo");

        eliminarColumnaSiExiste(connection, statement, "guia_remision_conductor", "id_catalogo_conductor");
        eliminarColumnaSiExiste(connection, statement, "guia_remision_transportista", "id_catalogo_transportista");
        eliminarColumnaSiExiste(connection, statement, "guia_remision_vehiculo", "id_catalogo_vehiculo");
    }

    private void ocultarGuiasLegacyNoPermitidas(Statement statement) throws Exception {
        int actualizadas = statement.executeUpdate("""
                UPDATE guia_remision
                SET deleted_at = COALESCE(deleted_at, CURRENT_TIMESTAMP),
                    activo = 0
                WHERE motivo_traslado NOT IN ('01','02','03','04','05','06','07','13','14','17')
                  AND deleted_at IS NULL
                """);
        if (actualizadas > 0) {
            log.info("Guias legacy fuera de motivos GRE permitidos ocultadas: {}", actualizadas);
        }
    }

    private void normalizarDefinicionMotivo(Statement statement) throws Exception {
        statement.execute("""
                ALTER TABLE guia_remision
                MODIFY COLUMN motivo_traslado ENUM('01','02','03','04','05','06','07','08','09','13','14','17','18','19')
                NOT NULL DEFAULT '04'
                """);
    }

    private void recrearTriggersMotivosPermitidos(Statement statement) throws Exception {
        statement.execute("DROP TRIGGER IF EXISTS bi_guia_remision_motivo_04");
        statement.execute("DROP TRIGGER IF EXISTS bu_guia_remision_motivo_04");

        statement.execute("""
                CREATE TRIGGER bi_guia_remision_motivo_04
                BEFORE INSERT ON guia_remision
                FOR EACH ROW
                BEGIN
                    IF NEW.motivo_traslado IS NULL OR TRIM(NEW.motivo_traslado) = '' THEN
                        SET NEW.motivo_traslado = '04';
                    END IF;
                    IF NEW.motivo_traslado NOT IN ('01','02','03','04','05','06','07','13','14','17') THEN
                        SIGNAL SQLSTATE '45000'
                        SET MESSAGE_TEXT = 'Motivo de traslado no permitido para GRE remitente';
                    END IF;
                    IF NEW.motivo_traslado = '13'
                        AND (NEW.descripcion_motivo IS NULL OR TRIM(NEW.descripcion_motivo) = '') THEN
                        SIGNAL SQLSTATE '45000'
                        SET MESSAGE_TEXT = 'descripcion_motivo es obligatoria para motivo 13';
                    END IF;
                    IF NEW.descripcion_motivo IS NULL OR TRIM(NEW.descripcion_motivo) = '' THEN
                        SET NEW.descripcion_motivo = CASE NEW.motivo_traslado
                            WHEN '01' THEN 'Venta'
                            WHEN '02' THEN 'Compra'
                            WHEN '03' THEN 'Venta con entrega a terceros'
                            WHEN '04' THEN 'Traslado entre establecimientos de la misma empresa'
                            WHEN '05' THEN 'Consignacion'
                            WHEN '06' THEN 'Devolucion'
                            WHEN '07' THEN 'Recojo de bienes transformados'
                            WHEN '14' THEN 'Venta sujeta a confirmacion del comprador'
                            WHEN '17' THEN 'Traslado de bienes para transformacion'
                            ELSE NEW.descripcion_motivo
                        END;
                    END IF;
                    SET NEW.id_cliente = NULL;
                    SET NEW.id_venta = NULL;
                END
                """);

        statement.execute("""
                CREATE TRIGGER bu_guia_remision_motivo_04
                BEFORE UPDATE ON guia_remision
                FOR EACH ROW
                BEGIN
                    IF NEW.deleted_at IS NULL THEN
                        IF NEW.motivo_traslado IS NULL OR TRIM(NEW.motivo_traslado) = '' THEN
                            SET NEW.motivo_traslado = '04';
                        END IF;
                        IF NEW.motivo_traslado NOT IN ('01','02','03','04','05','06','07','13','14','17') THEN
                            SIGNAL SQLSTATE '45000'
                            SET MESSAGE_TEXT = 'Motivo de traslado no permitido para GRE remitente';
                        END IF;
                        IF NEW.motivo_traslado = '13'
                            AND (NEW.descripcion_motivo IS NULL OR TRIM(NEW.descripcion_motivo) = '') THEN
                            SIGNAL SQLSTATE '45000'
                            SET MESSAGE_TEXT = 'descripcion_motivo es obligatoria para motivo 13';
                        END IF;
                        IF NEW.descripcion_motivo IS NULL OR TRIM(NEW.descripcion_motivo) = '' THEN
                            SET NEW.descripcion_motivo = CASE NEW.motivo_traslado
                                WHEN '01' THEN 'Venta'
                                WHEN '02' THEN 'Compra'
                                WHEN '03' THEN 'Venta con entrega a terceros'
                                WHEN '04' THEN 'Traslado entre establecimientos de la misma empresa'
                                WHEN '05' THEN 'Consignacion'
                                WHEN '06' THEN 'Devolucion'
                                WHEN '07' THEN 'Recojo de bienes transformados'
                                WHEN '14' THEN 'Venta sujeta a confirmacion del comprador'
                                WHEN '17' THEN 'Traslado de bienes para transformacion'
                                ELSE NEW.descripcion_motivo
                            END;
                        END IF;
                        SET NEW.id_cliente = NULL;
                        SET NEW.id_venta = NULL;
                    END IF;
                END
                """);
    }

    private void eliminarColumnaSiExiste(
            Connection connection,
            Statement statement,
            String tableName,
            String columnName) throws Exception {
        if (!tableExists(connection, tableName) || !columnExists(connection, tableName, columnName)) {
            return;
        }
        statement.execute("ALTER TABLE " + tableName + " DROP COLUMN " + columnName);
        log.info("Columna {}.{} eliminada", tableName, columnName);
    }

    private void eliminarIndiceSiExiste(
            Connection connection,
            Statement statement,
            String tableName,
            String indexName) throws Exception {
        if (!tableExists(connection, tableName) || !indexExists(connection, tableName, indexName)) {
            return;
        }
        statement.execute("ALTER TABLE " + tableName + " DROP INDEX " + indexName);
        log.info("Indice {} eliminado de {}", indexName, tableName);
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
}
