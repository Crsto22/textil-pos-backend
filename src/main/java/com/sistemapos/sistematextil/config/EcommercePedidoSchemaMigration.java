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
@Order(0)
public class EcommercePedidoSchemaMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EcommercePedidoSchemaMigration.class);

    private final DataSource dataSource;

    public EcommercePedidoSchemaMigration(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS ecommerce_pedido (
                      id_ecommerce_pedido INT NOT NULL AUTO_INCREMENT,
                      codigo VARCHAR(30) NOT NULL,
                      id_sucursal INT NOT NULL,
                      id_metodo_pago INT NOT NULL,
                      estado VARCHAR(40) NOT NULL,
                      cliente_dni VARCHAR(20) NOT NULL,
                      cliente_nombres VARCHAR(100) NOT NULL,
                      cliente_apellidos VARCHAR(100) NOT NULL,
                      cliente_correo VARCHAR(150) NOT NULL,
                      cliente_telefono VARCHAR(20) NOT NULL,
                      desea_factura TINYINT(1) NOT NULL DEFAULT 0,
                      factura_ruc VARCHAR(11) DEFAULT NULL,
                      envio_tipo VARCHAR(20) NOT NULL,
                      envio_direccion VARCHAR(255) DEFAULT NULL,
                      envio_referencia VARCHAR(255) DEFAULT NULL,
                      envio_departamento VARCHAR(100) DEFAULT NULL,
                      envio_provincia VARCHAR(100) DEFAULT NULL,
                      envio_distrito VARCHAR(100) DEFAULT NULL,
                      envio_tarifa VARCHAR(40) DEFAULT NULL,
                      subtotal DECIMAL(10,2) NOT NULL DEFAULT 0.00,
                      total DECIMAL(10,2) NOT NULL DEFAULT 0.00,
                      reserva_expira_at DATETIME(6) NOT NULL,
                      comprobante_url VARCHAR(600) DEFAULT NULL,
                      id_venta INT DEFAULT NULL,
                      id_usuario_aceptacion INT DEFAULT NULL,
                      aceptado_at DATETIME(6) DEFAULT NULL,
                      comprobante_token_hash VARCHAR(64) NOT NULL,
                      comprobante_token_expira_at DATETIME(6) NOT NULL,
                      fecha DATETIME(6) NOT NULL,
                      updated_at DATETIME(6) NOT NULL,
                      PRIMARY KEY (id_ecommerce_pedido),
                      UNIQUE KEY uk_ecommerce_pedido_codigo (codigo),
                      UNIQUE KEY uk_ecommerce_pedido_comprobante_token_hash (comprobante_token_hash),
                      KEY idx_ecommerce_pedido_estado_expira (estado, reserva_expira_at),
                      KEY idx_ecommerce_pedido_fecha (fecha),
                      KEY idx_ecommerce_pedido_estado_fecha (estado, fecha),
                      KEY fk_ecommerce_pedido_sucursal (id_sucursal),
                      KEY fk_ecommerce_pedido_metodo_pago (id_metodo_pago),
                      KEY fk_ecommerce_pedido_venta (id_venta),
                      KEY fk_ecommerce_pedido_usuario_aceptacion (id_usuario_aceptacion),
                      CONSTRAINT fk_ecommerce_pedido_sucursal FOREIGN KEY (id_sucursal) REFERENCES sucursal(id_sucursal),
                      CONSTRAINT fk_ecommerce_pedido_metodo_pago FOREIGN KEY (id_metodo_pago) REFERENCES metodo_pago_config(id_metodo_pago),
                      CONSTRAINT fk_ecommerce_pedido_venta FOREIGN KEY (id_venta) REFERENCES venta(id_venta),
                      CONSTRAINT fk_ecommerce_pedido_usuario_aceptacion FOREIGN KEY (id_usuario_aceptacion) REFERENCES usuario(id_usuario)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS ecommerce_pedido_detalle (
                      id_ecommerce_pedido_detalle INT NOT NULL AUTO_INCREMENT,
                      id_ecommerce_pedido INT NOT NULL,
                      id_producto_variante INT NOT NULL,
                      descripcion VARCHAR(255) DEFAULT NULL,
                      cantidad INT NOT NULL,
                      precio_unitario DECIMAL(10,2) NOT NULL,
                      subtotal DECIMAL(10,2) NOT NULL,
                      created_at DATETIME(6) NOT NULL,
                      PRIMARY KEY (id_ecommerce_pedido_detalle),
                      KEY fk_ecommerce_pedido_detalle_pedido (id_ecommerce_pedido),
                      KEY fk_ecommerce_pedido_detalle_variante (id_producto_variante),
                      CONSTRAINT fk_ecommerce_pedido_detalle_pedido FOREIGN KEY (id_ecommerce_pedido) REFERENCES ecommerce_pedido(id_ecommerce_pedido),
                      CONSTRAINT fk_ecommerce_pedido_detalle_variante FOREIGN KEY (id_producto_variante) REFERENCES producto_variante(id_producto_variante)
                    )
                    """);
            ensureTokenColumns(connection, statement);
            ensureAdminColumns(connection, statement);
            ensureListIndexes(connection, statement);
            ensureVentaOrigenColumn(connection, statement);
            log.info("Tablas ecommerce_pedido listas");
        }
    }

    private void ensureTokenColumns(Connection connection, Statement statement) throws Exception {
        if (!columnExists(connection, "ecommerce_pedido", "comprobante_token_hash")) {
            statement.execute("""
                    ALTER TABLE ecommerce_pedido
                    ADD COLUMN comprobante_token_hash VARCHAR(64) DEFAULT NULL AFTER comprobante_url
                    """);
        }
        if (!columnExists(connection, "ecommerce_pedido", "comprobante_token_expira_at")) {
            statement.execute("""
                    ALTER TABLE ecommerce_pedido
                    ADD COLUMN comprobante_token_expira_at DATETIME(6) DEFAULT NULL AFTER comprobante_token_hash
                    """);
        }
        statement.execute("""
                UPDATE ecommerce_pedido
                SET comprobante_token_hash = SHA2(CONCAT(UUID(), '-', id_ecommerce_pedido), 256)
                WHERE comprobante_token_hash IS NULL OR comprobante_token_hash = ''
                """);
        statement.execute("""
                UPDATE ecommerce_pedido
                SET comprobante_token_expira_at = reserva_expira_at
                WHERE comprobante_token_expira_at IS NULL
                """);
        statement.execute("ALTER TABLE ecommerce_pedido MODIFY COLUMN comprobante_token_hash VARCHAR(64) NOT NULL");
        statement.execute("ALTER TABLE ecommerce_pedido MODIFY COLUMN comprobante_token_expira_at DATETIME(6) NOT NULL");
        if (!indexExists(connection, "ecommerce_pedido", "uk_ecommerce_pedido_comprobante_token_hash")) {
            statement.execute("""
                    ALTER TABLE ecommerce_pedido
                    ADD UNIQUE KEY uk_ecommerce_pedido_comprobante_token_hash (comprobante_token_hash)
                    """);
        }
    }

    private void ensureAdminColumns(Connection connection, Statement statement) throws Exception {
        if (!columnExists(connection, "ecommerce_pedido", "id_venta")) {
            statement.execute("ALTER TABLE ecommerce_pedido ADD COLUMN id_venta INT DEFAULT NULL AFTER comprobante_url");
        }
        if (!columnExists(connection, "ecommerce_pedido", "id_usuario_aceptacion")) {
            statement.execute("ALTER TABLE ecommerce_pedido ADD COLUMN id_usuario_aceptacion INT DEFAULT NULL AFTER id_venta");
        }
        if (!columnExists(connection, "ecommerce_pedido", "aceptado_at")) {
            statement.execute("ALTER TABLE ecommerce_pedido ADD COLUMN aceptado_at DATETIME(6) DEFAULT NULL AFTER id_usuario_aceptacion");
        }
        if (!indexExists(connection, "ecommerce_pedido", "fk_ecommerce_pedido_venta")) {
            statement.execute("ALTER TABLE ecommerce_pedido ADD KEY fk_ecommerce_pedido_venta (id_venta)");
        }
        if (!indexExists(connection, "ecommerce_pedido", "fk_ecommerce_pedido_usuario_aceptacion")) {
            statement.execute("ALTER TABLE ecommerce_pedido ADD KEY fk_ecommerce_pedido_usuario_aceptacion (id_usuario_aceptacion)");
        }
        if (!foreignKeyExists(connection, "ecommerce_pedido", "fk_ecommerce_pedido_venta")) {
            statement.execute("""
                    ALTER TABLE ecommerce_pedido
                    ADD CONSTRAINT fk_ecommerce_pedido_venta FOREIGN KEY (id_venta) REFERENCES venta(id_venta)
                    """);
        }
        if (!foreignKeyExists(connection, "ecommerce_pedido", "fk_ecommerce_pedido_usuario_aceptacion")) {
            statement.execute("""
                    ALTER TABLE ecommerce_pedido
                    ADD CONSTRAINT fk_ecommerce_pedido_usuario_aceptacion
                    FOREIGN KEY (id_usuario_aceptacion) REFERENCES usuario(id_usuario)
                    """);
        }
    }

    private void ensureVentaOrigenColumn(Connection connection, Statement statement) throws Exception {
        if (!columnExists(connection, "venta", "origen")) {
            statement.execute("ALTER TABLE venta ADD COLUMN origen VARCHAR(20) NOT NULL DEFAULT 'POS' AFTER estado");
        }
    }

    private void ensureListIndexes(Connection connection, Statement statement) throws Exception {
        if (!indexExists(connection, "ecommerce_pedido", "idx_ecommerce_pedido_fecha")) {
            statement.execute("ALTER TABLE ecommerce_pedido ADD KEY idx_ecommerce_pedido_fecha (fecha)");
        }
        if (!indexExists(connection, "ecommerce_pedido", "idx_ecommerce_pedido_estado_fecha")) {
            statement.execute("ALTER TABLE ecommerce_pedido ADD KEY idx_ecommerce_pedido_estado_fecha (estado, fecha)");
        }
    }

    private boolean columnExists(Connection connection, String tableName, String columnName) throws Exception {
        String sql = """
                SELECT 1
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND COLUMN_NAME = ?
                LIMIT 1
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tableName);
            statement.setString(2, columnName);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private boolean indexExists(Connection connection, String tableName, String indexName) throws Exception {
        String sql = """
                SELECT 1
                FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND INDEX_NAME = ?
                LIMIT 1
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tableName);
            statement.setString(2, indexName);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private boolean foreignKeyExists(Connection connection, String tableName, String constraintName) throws Exception {
        String sql = """
                SELECT 1
                FROM information_schema.TABLE_CONSTRAINTS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND CONSTRAINT_NAME = ?
                  AND CONSTRAINT_TYPE = 'FOREIGN KEY'
                LIMIT 1
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tableName);
            statement.setString(2, constraintName);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }
}
