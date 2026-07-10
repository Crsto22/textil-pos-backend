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
public class EcommercePromocionComboSchemaMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EcommercePromocionComboSchemaMigration.class);

    private final DataSource dataSource;

    public EcommercePromocionComboSchemaMigration(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            if (!tableExists(connection, "producto") || !tableExists(connection, "usuario")) {
                return;
            }
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS ecommerce_promocion_combo (
                      id_ecommerce_promocion_combo INT NOT NULL AUTO_INCREMENT,
                      nombre VARCHAR(150) NOT NULL,
                      precio_combo DECIMAL(10,2) NOT NULL,
                      estado VARCHAR(20) NOT NULL DEFAULT 'ACTIVO',
                      fecha_inicio DATETIME(6) DEFAULT NULL,
                      fecha_fin DATETIME(6) DEFAULT NULL,
                      id_usuario_creacion INT DEFAULT NULL,
                      created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                      updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
                      deleted_at DATETIME(6) DEFAULT NULL,
                      PRIMARY KEY (id_ecommerce_promocion_combo),
                      KEY idx_epc_estado_vigencia (estado, fecha_inicio, fecha_fin, deleted_at),
                      KEY fk_epc_usuario_creacion (id_usuario_creacion),
                      CONSTRAINT fk_epc_usuario_creacion FOREIGN KEY (id_usuario_creacion) REFERENCES usuario(id_usuario)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS ecommerce_promocion_combo_item (
                      id_ecommerce_promocion_combo_item INT NOT NULL AUTO_INCREMENT,
                      id_ecommerce_promocion_combo INT NOT NULL,
                      producto_id INT NOT NULL,
                      cantidad_requerida INT NOT NULL,
                      PRIMARY KEY (id_ecommerce_promocion_combo_item),
                      KEY idx_epci_producto (producto_id),
                      KEY fk_epci_combo (id_ecommerce_promocion_combo),
                      CONSTRAINT fk_epci_combo FOREIGN KEY (id_ecommerce_promocion_combo)
                        REFERENCES ecommerce_promocion_combo(id_ecommerce_promocion_combo),
                      CONSTRAINT fk_epci_producto FOREIGN KEY (producto_id) REFERENCES producto(producto_id)
                    )
                    """);
            ensurePedidoColumns(connection, statement);
            log.info("Tablas ecommerce_promocion_combo listas");
        }
    }

    private void ensurePedidoColumns(Connection connection, Statement statement) throws Exception {
        if (!tableExists(connection, "ecommerce_pedido")) {
            return;
        }
        if (!columnExists(connection, "ecommerce_pedido", "descuento_promocion")) {
            statement.execute("""
                    ALTER TABLE ecommerce_pedido
                    ADD COLUMN descuento_promocion DECIMAL(10,2) NOT NULL DEFAULT 0.00 AFTER subtotal
                    """);
        }
        if (!columnExists(connection, "ecommerce_pedido", "promocion_resumen")) {
            statement.execute("""
                    ALTER TABLE ecommerce_pedido
                    ADD COLUMN promocion_resumen VARCHAR(600) DEFAULT NULL AFTER descuento_promocion
                    """);
        }
    }

    private boolean tableExists(Connection connection, String tableName) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT 1 FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?
                LIMIT 1
                """)) {
            statement.setString(1, tableName);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private boolean columnExists(Connection connection, String tableName, String columnName) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT 1 FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?
                LIMIT 1
                """)) {
            statement.setString(1, tableName);
            statement.setString(2, columnName);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }
}
