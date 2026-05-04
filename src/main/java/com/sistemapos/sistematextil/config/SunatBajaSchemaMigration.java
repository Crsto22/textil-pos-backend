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
public class SunatBajaSchemaMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SunatBajaSchemaMigration.class);

    private final DataSource dataSource;

    public SunatBajaSchemaMigration(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            crearTablaSunatBajaLote(statement);
            crearTablaSunatBajaItem(statement);
            asegurarColumnasSunatBajaItem(connection, statement);
            asegurarColumnasVenta(connection, statement);
            asegurarColumnasNotaCredito(connection, statement);
        }
    }

    private void crearTablaSunatBajaLote(Statement statement) throws Exception {
        statement.execute("""
                CREATE TABLE IF NOT EXISTS sunat_baja_lote (
                  id_sunat_baja_lote INT(11) NOT NULL AUTO_INCREMENT,
                  id_empresa INT(11) NOT NULL,
                  tipo_envio VARCHAR(10) NOT NULL,
                  fecha_documento DATE NOT NULL,
                  fecha_generacion DATE NOT NULL,
                  correlativo INT(11) NOT NULL,
                  estado VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE_ENVIO',
                  codigo VARCHAR(20) DEFAULT NULL,
                  mensaje VARCHAR(500) DEFAULT NULL,
                  ticket_sunat VARCHAR(120) DEFAULT NULL,
                  sunat_hash VARCHAR(120) DEFAULT NULL,
                  sunat_xml_nombre VARCHAR(180) DEFAULT NULL,
                  sunat_xml_key VARCHAR(600) DEFAULT NULL,
                  sunat_zip_nombre VARCHAR(180) DEFAULT NULL,
                  sunat_cdr_nombre VARCHAR(180) DEFAULT NULL,
                  sunat_cdr_key VARCHAR(600) DEFAULT NULL,
                  sunat_enviado_at DATETIME(6) DEFAULT NULL,
                  sunat_respondido_at DATETIME(6) DEFAULT NULL,
                  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
                  deleted_at DATETIME(6) DEFAULT NULL,
                  PRIMARY KEY (id_sunat_baja_lote),
                  KEY idx_sunat_baja_lote_estado (estado, fecha_generacion),
                  KEY idx_sunat_baja_lote_empresa_fecha (id_empresa, tipo_envio, fecha_documento, fecha_generacion),
                  CONSTRAINT fk_sunat_baja_lote_empresa
                    FOREIGN KEY (id_empresa) REFERENCES empresa (id_empresa)
                    ON DELETE RESTRICT ON UPDATE RESTRICT
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
                """);
    }

    private void crearTablaSunatBajaItem(Statement statement) throws Exception {
        statement.execute("""
                CREATE TABLE IF NOT EXISTS sunat_baja_item (
                  id_sunat_baja_item INT(11) NOT NULL AUTO_INCREMENT,
                  id_sunat_baja_lote INT(11) NOT NULL,
                  id_venta INT(11) DEFAULT NULL,
                  id_nota_credito INT(11) DEFAULT NULL,
                  tipo_comprobante VARCHAR(20) NOT NULL,
                  serie VARCHAR(10) NOT NULL,
                  correlativo INT(11) NOT NULL,
                  fecha_documento DATE NOT NULL,
                  motivo VARCHAR(255) NOT NULL,
                  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
                  deleted_at DATETIME(6) DEFAULT NULL,
                  PRIMARY KEY (id_sunat_baja_item),
                  KEY idx_sunat_baja_item_lote (id_sunat_baja_lote),
                  KEY idx_sunat_baja_item_venta (id_venta),
                  KEY idx_sunat_baja_item_nota_credito (id_nota_credito),
                  CONSTRAINT fk_sunat_baja_item_lote
                    FOREIGN KEY (id_sunat_baja_lote) REFERENCES sunat_baja_lote (id_sunat_baja_lote)
                    ON DELETE RESTRICT ON UPDATE RESTRICT,
                  CONSTRAINT fk_sunat_baja_item_venta
                    FOREIGN KEY (id_venta) REFERENCES venta (id_venta)
                    ON DELETE RESTRICT ON UPDATE RESTRICT
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
                """);
    }

    private void asegurarColumnasSunatBajaItem(Connection connection, Statement statement) throws Exception {
        ensureColumn(connection, statement, "sunat_baja_item", "id_nota_credito",
                "ALTER TABLE sunat_baja_item ADD COLUMN id_nota_credito INT(11) DEFAULT NULL AFTER id_venta");
        if (columnExists(connection, "sunat_baja_item", "id_venta")) {
            statement.execute("ALTER TABLE sunat_baja_item MODIFY COLUMN id_venta INT(11) DEFAULT NULL");
        }
        ensureIndex(connection, statement, "sunat_baja_item", "idx_sunat_baja_item_nota_credito",
                "ALTER TABLE sunat_baja_item ADD KEY idx_sunat_baja_item_nota_credito (id_nota_credito)");
        if (tableExists(connection, "nota_credito")
                && !foreignKeyExists(connection, "sunat_baja_item", "fk_sunat_baja_item_nota_credito")) {
            statement.execute("""
                    ALTER TABLE sunat_baja_item
                    ADD CONSTRAINT fk_sunat_baja_item_nota_credito
                    FOREIGN KEY (id_nota_credito) REFERENCES nota_credito (id_nota_credito)
                    ON DELETE RESTRICT ON UPDATE RESTRICT
                    """);
            log.info("FK fk_sunat_baja_item_nota_credito creada");
        }
    }

    private void asegurarColumnasVenta(Connection connection, Statement statement) throws Exception {
        ensureColumn(connection, statement, "venta", "sunat_baja_estado",
                "ALTER TABLE venta ADD COLUMN sunat_baja_estado VARCHAR(20) DEFAULT NULL AFTER sunat_respondido_at");
        ensureColumn(connection, statement, "venta", "sunat_baja_codigo",
                "ALTER TABLE venta ADD COLUMN sunat_baja_codigo VARCHAR(20) DEFAULT NULL AFTER sunat_baja_estado");
        ensureColumn(connection, statement, "venta", "sunat_baja_mensaje",
                "ALTER TABLE venta ADD COLUMN sunat_baja_mensaje VARCHAR(500) DEFAULT NULL AFTER sunat_baja_codigo");
        ensureColumn(connection, statement, "venta", "sunat_baja_ticket",
                "ALTER TABLE venta ADD COLUMN sunat_baja_ticket VARCHAR(120) DEFAULT NULL AFTER sunat_baja_mensaje");
        ensureColumn(connection, statement, "venta", "sunat_baja_tipo",
                "ALTER TABLE venta ADD COLUMN sunat_baja_tipo VARCHAR(10) DEFAULT NULL AFTER sunat_baja_ticket");
        ensureColumn(connection, statement, "venta", "sunat_baja_lote_id",
                "ALTER TABLE venta ADD COLUMN sunat_baja_lote_id INT(11) DEFAULT NULL AFTER sunat_baja_tipo");
        ensureColumn(connection, statement, "venta", "sunat_baja_solicitada_at",
                "ALTER TABLE venta ADD COLUMN sunat_baja_solicitada_at DATETIME(6) DEFAULT NULL AFTER sunat_baja_lote_id");
        ensureColumn(connection, statement, "venta", "sunat_baja_respondida_at",
                "ALTER TABLE venta ADD COLUMN sunat_baja_respondida_at DATETIME(6) DEFAULT NULL AFTER sunat_baja_solicitada_at");

        if (!foreignKeyExists(connection, "venta", "fk_venta_sunat_baja_lote")) {
            statement.execute("""
                    ALTER TABLE venta
                    ADD CONSTRAINT fk_venta_sunat_baja_lote
                    FOREIGN KEY (sunat_baja_lote_id) REFERENCES sunat_baja_lote (id_sunat_baja_lote)
                    ON DELETE RESTRICT ON UPDATE RESTRICT
                    """);
            log.info("FK fk_venta_sunat_baja_lote creada");
        }
    }

    private void asegurarColumnasNotaCredito(Connection connection, Statement statement) throws Exception {
        if (!tableExists(connection, "nota_credito")) {
            return;
        }
        ensureColumn(connection, statement, "nota_credito", "tipo_anulacion",
                "ALTER TABLE nota_credito ADD COLUMN tipo_anulacion VARCHAR(20) DEFAULT NULL AFTER sunat_respondido_at");
        ensureColumn(connection, statement, "nota_credito", "motivo_anulacion",
                "ALTER TABLE nota_credito ADD COLUMN motivo_anulacion VARCHAR(255) DEFAULT NULL AFTER tipo_anulacion");
        ensureColumn(connection, statement, "nota_credito", "anulado_at",
                "ALTER TABLE nota_credito ADD COLUMN anulado_at DATETIME(6) DEFAULT NULL AFTER motivo_anulacion");
        ensureColumn(connection, statement, "nota_credito", "id_usuario_anulacion",
                "ALTER TABLE nota_credito ADD COLUMN id_usuario_anulacion INT(11) DEFAULT NULL AFTER anulado_at");
        ensureColumn(connection, statement, "nota_credito", "sunat_baja_estado",
                "ALTER TABLE nota_credito ADD COLUMN sunat_baja_estado VARCHAR(20) DEFAULT NULL AFTER id_usuario_anulacion");
        ensureColumn(connection, statement, "nota_credito", "sunat_baja_codigo",
                "ALTER TABLE nota_credito ADD COLUMN sunat_baja_codigo VARCHAR(20) DEFAULT NULL AFTER sunat_baja_estado");
        ensureColumn(connection, statement, "nota_credito", "sunat_baja_mensaje",
                "ALTER TABLE nota_credito ADD COLUMN sunat_baja_mensaje VARCHAR(500) DEFAULT NULL AFTER sunat_baja_codigo");
        ensureColumn(connection, statement, "nota_credito", "sunat_baja_ticket",
                "ALTER TABLE nota_credito ADD COLUMN sunat_baja_ticket VARCHAR(120) DEFAULT NULL AFTER sunat_baja_mensaje");
        ensureColumn(connection, statement, "nota_credito", "sunat_baja_tipo",
                "ALTER TABLE nota_credito ADD COLUMN sunat_baja_tipo VARCHAR(10) DEFAULT NULL AFTER sunat_baja_ticket");
        ensureColumn(connection, statement, "nota_credito", "sunat_baja_lote_id",
                "ALTER TABLE nota_credito ADD COLUMN sunat_baja_lote_id INT(11) DEFAULT NULL AFTER sunat_baja_tipo");
        ensureColumn(connection, statement, "nota_credito", "sunat_baja_solicitada_at",
                "ALTER TABLE nota_credito ADD COLUMN sunat_baja_solicitada_at DATETIME(6) DEFAULT NULL AFTER sunat_baja_lote_id");
        ensureColumn(connection, statement, "nota_credito", "sunat_baja_respondida_at",
                "ALTER TABLE nota_credito ADD COLUMN sunat_baja_respondida_at DATETIME(6) DEFAULT NULL AFTER sunat_baja_solicitada_at");

        if (!foreignKeyExists(connection, "nota_credito", "fk_nota_credito_usuario_anulacion")) {
            statement.execute("""
                    ALTER TABLE nota_credito
                    ADD CONSTRAINT fk_nota_credito_usuario_anulacion
                    FOREIGN KEY (id_usuario_anulacion) REFERENCES usuario (id_usuario)
                    ON DELETE RESTRICT ON UPDATE RESTRICT
                    """);
            log.info("FK fk_nota_credito_usuario_anulacion creada");
        }
        if (!foreignKeyExists(connection, "nota_credito", "fk_nota_credito_sunat_baja_lote")) {
            statement.execute("""
                    ALTER TABLE nota_credito
                    ADD CONSTRAINT fk_nota_credito_sunat_baja_lote
                    FOREIGN KEY (sunat_baja_lote_id) REFERENCES sunat_baja_lote (id_sunat_baja_lote)
                    ON DELETE RESTRICT ON UPDATE RESTRICT
                    """);
            log.info("FK fk_nota_credito_sunat_baja_lote creada");
        }
    }

    private void ensureColumn(
            Connection connection,
            Statement statement,
            String table,
            String column,
            String sql) throws Exception {
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

    private boolean tableExists(Connection connection, String tableName) throws Exception {
        try (ResultSet tables = connection.getMetaData()
                .getTables(connection.getCatalog(), null, tableName, null)) {
            return tables.next();
        }
    }

    private void ensureIndex(Connection connection, Statement statement, String table, String index, String sql) throws Exception {
        try (ResultSet indexes = connection.getMetaData().getIndexInfo(connection.getCatalog(), null, table, false, false)) {
            while (indexes.next()) {
                if (index.equalsIgnoreCase(indexes.getString("INDEX_NAME"))) {
                    return;
                }
            }
        }
        statement.execute(sql);
        log.info("Indice {}.{} creado", table, index);
    }

    private boolean foreignKeyExists(Connection connection, String tableName, String fkName) throws Exception {
        try (ResultSet importedKeys = connection.getMetaData()
                .getImportedKeys(connection.getCatalog(), null, tableName)) {
            while (importedKeys.next()) {
                if (fkName.equalsIgnoreCase(importedKeys.getString("FK_NAME"))) {
                    return true;
                }
            }
            return false;
        }
    }
}
