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
public class VentaAnulacionSchemaMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(VentaAnulacionSchemaMigration.class);

    private final DataSource dataSource;

    public VentaAnulacionSchemaMigration(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            limpiarTablasBaja(connection, statement);
            migrarVenta(connection, statement);
            asegurarNotaCredito(connection, statement);
            asegurarNotaCreditoDetalle(connection, statement);
            asegurarConfiguracionesComprobante(connection, statement);
            asegurarIndicesGlobalesDocumentos(connection, statement);
        }
    }

    private void limpiarTablasBaja(Connection connection, Statement statement) throws Exception {
        if (tableExists(connection, "comunicacion_baja_detalle")) {
            statement.execute("DROP TABLE comunicacion_baja_detalle");
            log.info("Tabla comunicacion_baja_detalle eliminada");
        }
        if (tableExists(connection, "comunicacion_baja")) {
            statement.execute("DROP TABLE comunicacion_baja");
            log.info("Tabla comunicacion_baja eliminada");
        }
    }

    private void migrarVenta(Connection connection, Statement statement) throws Exception {
        if (!tableExists(connection, "venta")) {
            return;
        }

        if (!columnExists(connection, "venta", "forma_pago")) {
            statement.execute("""
                    ALTER TABLE venta
                    ADD COLUMN forma_pago VARCHAR(10) NOT NULL DEFAULT 'CONTADO' AFTER moneda
                    """);
            log.info("Columna venta.forma_pago creada");
        }

        migrarColumnaVenta(connection, statement, "anulacion_tipo", "tipo_anulacion", "VARCHAR(20) DEFAULT NULL");
        migrarColumnaVenta(connection, statement, "anulacion_motivo", "motivo_anulacion", "VARCHAR(255) DEFAULT NULL");
        migrarColumnaVenta(connection, statement, "anulacion_fecha", "anulado_at", "DATETIME(6) DEFAULT NULL");

        if (!columnExists(connection, "venta", "tipo_anulacion")) {
            statement.execute("""
                    ALTER TABLE venta
                    ADD COLUMN tipo_anulacion VARCHAR(20) DEFAULT NULL AFTER estado
                    """);
            log.info("Columna venta.tipo_anulacion creada");
        }
        if (!columnExists(connection, "venta", "motivo_anulacion")) {
            statement.execute("""
                    ALTER TABLE venta
                    ADD COLUMN motivo_anulacion VARCHAR(255) DEFAULT NULL AFTER tipo_anulacion
                    """);
            log.info("Columna venta.motivo_anulacion creada");
        }
        if (!columnExists(connection, "venta", "anulado_at")) {
            statement.execute("""
                    ALTER TABLE venta
                    ADD COLUMN anulado_at DATETIME(6) DEFAULT NULL AFTER motivo_anulacion
                    """);
            log.info("Columna venta.anulado_at creada");
        }
        if (!columnExists(connection, "venta", "id_usuario_anulacion")) {
            statement.execute("""
                    ALTER TABLE venta
                    ADD COLUMN id_usuario_anulacion INT(11) DEFAULT NULL AFTER anulado_at
                    """);
            log.info("Columna venta.id_usuario_anulacion creada");
        }
        if (columnExists(connection, "venta", "sunat_zip_key")) {
            statement.execute("ALTER TABLE venta DROP COLUMN sunat_zip_key");
            log.info("Columna venta.sunat_zip_key eliminada");
        }

        statement.executeUpdate("""
                UPDATE venta
                SET estado = 'ANULADA'
                WHERE estado = 'ANULACION_PENDIENTE'
                """);

        statement.executeUpdate("""
                UPDATE venta
                SET estado = 'ANULADA'
                WHERE estado = 'NC_EMITIDA'
                """);

        statement.execute("""
                ALTER TABLE venta
                MODIFY COLUMN estado ENUM('EMITIDA','ANULADA','NC_EMITIDA') NOT NULL DEFAULT 'EMITIDA'
                """);

        if (!foreignKeyExists(connection, "venta", "fk_venta_usuario_anulacion")) {
            statement.execute("""
                    ALTER TABLE venta
                    ADD CONSTRAINT fk_venta_usuario_anulacion
                    FOREIGN KEY (id_usuario_anulacion) REFERENCES usuario (id_usuario)
                    ON DELETE RESTRICT ON UPDATE RESTRICT
                    """);
            log.info("FK fk_venta_usuario_anulacion creada");
        }
    }

    private void migrarColumnaVenta(
            Connection connection,
            Statement statement,
            String columnaAntigua,
            String columnaNueva,
            String definicionNueva) throws Exception {
        boolean existeNueva = columnExists(connection, "venta", columnaNueva);
        boolean existeAntigua = columnExists(connection, "venta", columnaAntigua);

        if (!existeAntigua) {
            return;
        }

        if (!existeNueva) {
            statement.execute("ALTER TABLE venta CHANGE COLUMN " + columnaAntigua + " " + columnaNueva + " " + definicionNueva);
            log.info("Columna venta.{} renombrada a {}", columnaAntigua, columnaNueva);
            return;
        }

        statement.execute(
                "UPDATE venta SET " + columnaNueva + " = COALESCE(" + columnaNueva + ", " + columnaAntigua + ") "
                        + "WHERE " + columnaAntigua + " IS NOT NULL");
        statement.execute("ALTER TABLE venta DROP COLUMN " + columnaAntigua);
        log.info("Columna antigua venta.{} consolidada y eliminada", columnaAntigua);
    }

    private void asegurarNotaCredito(Connection connection, Statement statement) throws Exception {
        statement.execute("""
                CREATE TABLE IF NOT EXISTS nota_credito (
                  id_nota_credito INT(11) NOT NULL AUTO_INCREMENT,
                  id_venta_referencia INT(11) NOT NULL,
                  id_sucursal INT(11) NOT NULL,
                  id_usuario INT(11) NOT NULL,
                  id_cliente INT(11) DEFAULT NULL,
                  tipo_comprobante ENUM('NOTA_CREDITO_BOLETA','NOTA_CREDITO_FACTURA') NOT NULL DEFAULT 'NOTA_CREDITO_BOLETA',
                  serie VARCHAR(10) NOT NULL,
                  correlativo INT(11) NOT NULL,
                  moneda CHAR(3) NOT NULL DEFAULT 'PEN',
                  codigo_motivo VARCHAR(5) NOT NULL,
                  descripcion_motivo VARCHAR(255) NOT NULL,
                  tipo_documento_ref VARCHAR(2) NOT NULL,
                  serie_ref VARCHAR(10) NOT NULL,
                  correlativo_ref INT(11) NOT NULL,
                  igv_porcentaje DECIMAL(5,2) NOT NULL DEFAULT 18.00,
                  subtotal DECIMAL(10,2) NOT NULL DEFAULT 0.00,
                  descuento_total DECIMAL(10,2) NOT NULL DEFAULT 0.00,
                  igv DECIMAL(10,2) NOT NULL DEFAULT 0.00,
                  total DECIMAL(10,2) NOT NULL DEFAULT 0.00,
                  estado VARCHAR(20) NOT NULL DEFAULT 'EMITIDA',
                  sunat_estado VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
                  sunat_codigo VARCHAR(20) DEFAULT NULL,
                  sunat_mensaje VARCHAR(500) DEFAULT NULL,
                  sunat_hash VARCHAR(120) DEFAULT NULL,
                  sunat_ticket VARCHAR(120) DEFAULT NULL,
                  sunat_xml_nombre VARCHAR(180) DEFAULT NULL,
                  sunat_xml_key VARCHAR(600) DEFAULT NULL,
                  sunat_zip_nombre VARCHAR(180) DEFAULT NULL,
                  sunat_cdr_nombre VARCHAR(180) DEFAULT NULL,
                  sunat_cdr_key VARCHAR(600) DEFAULT NULL,
                  sunat_enviado_at DATETIME(6) DEFAULT NULL,
                  sunat_respondido_at DATETIME(6) DEFAULT NULL,
                  stock_devuelto TINYINT(1) NOT NULL DEFAULT 0,
                  activo TINYINT(1) NOT NULL DEFAULT 1,
                  fecha DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
                  deleted_at DATETIME(6) DEFAULT NULL,
                  PRIMARY KEY (id_nota_credito),
                  KEY idx_nota_credito_venta_ref (id_venta_referencia),
                  KEY idx_nota_credito_sucursal (id_sucursal),
                  KEY idx_nota_credito_usuario (id_usuario),
                  KEY idx_nota_credito_cliente (id_cliente),
                  UNIQUE KEY uk_nota_credito_numero_comprobante (id_sucursal, tipo_comprobante, serie, correlativo),
                  UNIQUE KEY uk_nota_credito_tipo_serie_correlativo (tipo_comprobante, serie, correlativo),
                  CONSTRAINT fk_nc_venta_ref
                    FOREIGN KEY (id_venta_referencia) REFERENCES venta (id_venta)
                    ON DELETE RESTRICT ON UPDATE RESTRICT,
                  CONSTRAINT fk_nc_sucursal
                    FOREIGN KEY (id_sucursal) REFERENCES sucursal (id_sucursal)
                    ON DELETE RESTRICT ON UPDATE RESTRICT,
                  CONSTRAINT fk_nc_usuario
                    FOREIGN KEY (id_usuario) REFERENCES usuario (id_usuario)
                    ON DELETE RESTRICT ON UPDATE RESTRICT,
                  CONSTRAINT fk_nc_cliente
                    FOREIGN KEY (id_cliente) REFERENCES cliente (id_cliente)
                    ON DELETE RESTRICT ON UPDATE RESTRICT
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
                """);

        if (columnExists(connection, "nota_credito", "sunat_zip_key")) {
            statement.execute("ALTER TABLE nota_credito DROP COLUMN sunat_zip_key");
            log.info("Columna nota_credito.sunat_zip_key eliminada");
        }

        statement.executeUpdate("""
                UPDATE nota_credito
                SET tipo_comprobante = CASE
                    WHEN UPPER(TRIM(tipo_comprobante)) = 'NOTA_CREDITO_BOLETA' THEN 'NOTA_CREDITO_BOLETA'
                    WHEN UPPER(TRIM(tipo_comprobante)) = 'NOTA_CREDITO_FACTURA' THEN 'NOTA_CREDITO_FACTURA'
                    WHEN tipo_documento_ref = '03' THEN 'NOTA_CREDITO_BOLETA'
                    WHEN tipo_documento_ref = '01' THEN 'NOTA_CREDITO_FACTURA'
                    ELSE tipo_comprobante
                  END
                WHERE tipo_comprobante IS NOT NULL
                  AND tipo_comprobante NOT IN ('NOTA_CREDITO_BOLETA', 'NOTA_CREDITO_FACTURA')
                """);

        statement.execute("""
                ALTER TABLE nota_credito
                MODIFY COLUMN tipo_comprobante ENUM('NOTA_CREDITO_BOLETA','NOTA_CREDITO_FACTURA') NOT NULL DEFAULT 'NOTA_CREDITO_BOLETA'
                """);

        if (!indexExists(connection, "nota_credito", "uk_nota_credito_tipo_serie_correlativo")) {
            try (Statement uniqueStatement = connection.createStatement()) {
                uniqueStatement.execute("""
                        ALTER TABLE nota_credito
                        ADD UNIQUE KEY uk_nota_credito_tipo_serie_correlativo
                        (tipo_comprobante, serie, correlativo)
                        """);
                log.info("Indice unico global de nota_credito creado");
            }
        }
    }

    private void asegurarNotaCreditoDetalle(Connection connection, Statement statement) throws Exception {
        statement.execute("""
                CREATE TABLE IF NOT EXISTS nota_credito_detalle (
                  id_nota_credito_detalle INT(11) NOT NULL AUTO_INCREMENT,
                  id_nota_credito INT(11) NOT NULL,
                  id_producto_variante INT(11) NOT NULL,
                  id_venta_detalle_ref INT(11) DEFAULT NULL,
                  descripcion VARCHAR(255) DEFAULT NULL,
                  cantidad INT(11) NOT NULL,
                  unidad_medida VARCHAR(3) NOT NULL DEFAULT 'NIU',
                  codigo_tipo_afectacion_igv VARCHAR(2) NOT NULL DEFAULT '10',
                  precio_unitario DECIMAL(10,2) NOT NULL,
                  descuento DECIMAL(10,2) NOT NULL DEFAULT 0.00,
                  igv_detalle DECIMAL(10,2) NOT NULL DEFAULT 0.00,
                  subtotal DECIMAL(10,2) NOT NULL,
                  total_detalle DECIMAL(10,2) DEFAULT NULL,
                  activo TINYINT(1) NOT NULL DEFAULT 1,
                  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
                  deleted_at DATETIME(6) DEFAULT NULL,
                  PRIMARY KEY (id_nota_credito_detalle),
                  KEY idx_ncd_nota_credito (id_nota_credito),
                  KEY idx_ncd_producto_variante (id_producto_variante),
                  KEY idx_ncd_venta_detalle_ref (id_venta_detalle_ref),
                  CONSTRAINT fk_ncd_nota_credito
                    FOREIGN KEY (id_nota_credito) REFERENCES nota_credito (id_nota_credito)
                    ON DELETE RESTRICT ON UPDATE RESTRICT,
                  CONSTRAINT fk_ncd_producto_variante
                    FOREIGN KEY (id_producto_variante) REFERENCES producto_variante (id_producto_variante)
                    ON DELETE RESTRICT ON UPDATE RESTRICT,
                  CONSTRAINT fk_ncd_venta_detalle_ref
                    FOREIGN KEY (id_venta_detalle_ref) REFERENCES venta_detalle (id_venta_detalle)
                    ON DELETE RESTRICT ON UPDATE RESTRICT
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
                """);

        if (!columnExists(connection, "nota_credito_detalle", "id_venta_detalle_ref")) {
            statement.execute("""
                    ALTER TABLE nota_credito_detalle
                    ADD COLUMN id_venta_detalle_ref INT(11) DEFAULT NULL AFTER id_producto_variante
                    """);
            log.info("Columna nota_credito_detalle.id_venta_detalle_ref creada");
        }

        if (!indexExists(connection, "nota_credito_detalle", "idx_ncd_venta_detalle_ref")) {
            statement.execute("""
                    ALTER TABLE nota_credito_detalle
                    ADD KEY idx_ncd_venta_detalle_ref (id_venta_detalle_ref)
                    """);
            log.info("Indice idx_ncd_venta_detalle_ref creado");
        }

        if (!foreignKeyExists(connection, "nota_credito_detalle", "fk_ncd_venta_detalle_ref")) {
            statement.execute("""
                    ALTER TABLE nota_credito_detalle
                    ADD CONSTRAINT fk_ncd_venta_detalle_ref
                    FOREIGN KEY (id_venta_detalle_ref) REFERENCES venta_detalle (id_venta_detalle)
                    ON DELETE RESTRICT ON UPDATE RESTRICT
                    """);
            log.info("FK fk_ncd_venta_detalle_ref creada");
        }
    }

    private void asegurarConfiguracionesComprobante(Connection connection, Statement statement) throws Exception {
        if (!tableExists(connection, "comprobante_config")) {
            return;
        }

        statement.execute("""
                ALTER TABLE comprobante_config
                MODIFY COLUMN tipo_comprobante ENUM('NOTA DE VENTA','BOLETA','FACTURA','NOTA_CREDITO_BOLETA','NOTA_CREDITO_FACTURA','COTIZACION') NOT NULL
                """);

        statement.executeUpdate("""
                UPDATE comprobante_config
                SET habilitado_venta = CASE
                    WHEN tipo_comprobante IN ('NOTA DE VENTA', 'BOLETA', 'FACTURA') THEN 1
                    ELSE 0
                  END
                """);

        eliminarForeignKeySiExiste(connection, statement, "comprobante_config", "fk_comprobante_config_sucursal");
        eliminarIndiceSiExiste(connection, statement, "comprobante_config", "uk_comprobante_config_sucursal_tipo");
        eliminarIndiceSiExiste(connection, statement, "comprobante_config", "uk_comprobante_config_sucursal_tipo_serie");

        if (columnExists(connection, "comprobante_config", "id_sucursal")) {
            eliminarIndiceSiExiste(connection, statement, "comprobante_config", "idx_comprobante_config_lookup");
            statement.execute("ALTER TABLE comprobante_config DROP COLUMN id_sucursal");
            log.info("Columna comprobante_config.id_sucursal eliminada");
        }

        asegurarIndiceUnico(
                connection,
                statement,
                "comprobante_config",
                "uk_comprobante_config_tipo_serie",
                "(tipo_comprobante, serie)");
        asegurarIndiceSimple(
                connection,
                statement,
                "comprobante_config",
                "idx_comprobante_config_lookup",
                "(tipo_comprobante, serie, activo, deleted_at)");
    }

    private void asegurarIndicesGlobalesDocumentos(Connection connection, Statement statement) throws Exception {
        if (tableExists(connection, "venta")) {
            eliminarIndiceSiExiste(connection, statement, "venta", "uk_venta_numero_comprobante");
            asegurarIndiceUnico(
                    connection,
                    statement,
                    "venta",
                    "uk_venta_tipo_serie_correlativo",
                    "(tipo_comprobante, serie, correlativo)");
        }

        if (tableExists(connection, "nota_credito")) {
            eliminarIndiceSiExiste(connection, statement, "nota_credito", "uk_nota_credito_numero_comprobante");
            asegurarIndiceUnico(
                    connection,
                    statement,
                    "nota_credito",
                    "uk_nota_credito_tipo_serie_correlativo",
                    "(tipo_comprobante, serie, correlativo)");
        }

        if (tableExists(connection, "cotizacion")) {
            eliminarIndiceSiExiste(connection, statement, "cotizacion", "uk_cotizacion_numero");
            asegurarIndiceUnico(
                    connection,
                    statement,
                    "cotizacion",
                    "uk_cotizacion_serie_correlativo",
                    "(serie, correlativo)");
        }
    }

    private void asegurarIndiceUnico(
            Connection connection,
            Statement statement,
            String tableName,
            String indexName,
            String columnsSql) throws Exception {
        if (indexExists(connection, tableName, indexName)) {
            return;
        }
        statement.execute("ALTER TABLE " + tableName + " ADD UNIQUE KEY " + indexName + " " + columnsSql);
        log.info("Indice unico {} creado en {}", indexName, tableName);
    }

    private void asegurarIndiceSimple(
            Connection connection,
            Statement statement,
            String tableName,
            String indexName,
            String columnsSql) throws Exception {
        if (indexExists(connection, tableName, indexName)) {
            return;
        }
        statement.execute("ALTER TABLE " + tableName + " ADD KEY " + indexName + " " + columnsSql);
        log.info("Indice {} creado en {}", indexName, tableName);
    }

    private void eliminarIndiceSiExiste(
            Connection connection,
            Statement statement,
            String tableName,
            String indexName) throws Exception {
        if (!indexExists(connection, tableName, indexName)) {
            return;
        }
        statement.execute("ALTER TABLE " + tableName + " DROP INDEX " + indexName);
        log.info("Indice {} eliminado de {}", indexName, tableName);
    }

    private void eliminarForeignKeySiExiste(
            Connection connection,
            Statement statement,
            String tableName,
            String constraintName) throws Exception {
        if (!foreignKeyExists(connection, tableName, constraintName)) {
            return;
        }
        statement.execute("ALTER TABLE " + tableName + " DROP FOREIGN KEY " + constraintName);
        log.info("FK {} eliminada de {}", constraintName, tableName);
    }

    private String serieCotizacionPorSucursalSql(String idSucursalExpression) {
        return seriePorSucursalSql("COT", idSucursalExpression, 2);
    }

    private String serieNotaCreditoBoletaPorSucursalSql(String idSucursalExpression) {
        return seriePorSucursalSql("BC", idSucursalExpression, 2);
    }

    private String serieNotaCreditoFacturaPorSucursalSql(String idSucursalExpression) {
        return seriePorSucursalSql("FC", idSucursalExpression, 2);
    }

    private String seriePorSucursalSql(String prefijo, String idSucursalExpression, int longitudNumerica) {
        return "CONCAT('" + prefijo + "', LPAD(" + idSucursalExpression + ", " + longitudNumerica + ", '0'))";
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

    private boolean foreignKeyExists(Connection connection, String tableName, String constraintName) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COUNT(*)
                FROM information_schema.REFERENTIAL_CONSTRAINTS
                WHERE CONSTRAINT_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND CONSTRAINT_NAME = ?
                """)) {
            statement.setString(1, tableName);
            statement.setString(2, constraintName);
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
