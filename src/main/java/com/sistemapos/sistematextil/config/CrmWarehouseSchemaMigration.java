package com.sistemapos.sistematextil.config;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;

import javax.sql.DataSource;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.LOWEST_PRECEDENCE - 25)
public class CrmWarehouseSchemaMigration implements ApplicationRunner {
    private final DataSource dataSource;

    public CrmWarehouseSchemaMigration(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    UPDATE crm_whatsapp_conversation c
                    JOIN crm_whatsapp_connection wc ON wc.client_id = 'kiments-sales'
                    SET c.id_connection = wc.id_connection
                    WHERE c.id_connection IS NULL
                    """);

            if (indexExists(connection, "crm_whatsapp_conversation", "uk_crm_whatsapp_conversation_phone")) {
                statement.execute("ALTER TABLE crm_whatsapp_conversation DROP INDEX uk_crm_whatsapp_conversation_phone");
            }
            if (!indexExists(connection, "crm_whatsapp_conversation", "uk_crm_whatsapp_conversation_connection_phone")) {
                statement.execute("""
                        ALTER TABLE crm_whatsapp_conversation
                        ADD UNIQUE KEY uk_crm_whatsapp_conversation_connection_phone (id_connection, phone)
                        """);
            }

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS crm_warehouse_order (
                      id_warehouse_order BIGINT NOT NULL AUTO_INCREMENT,
                      id_venta INT NOT NULL,
                      id_sales_conversation BIGINT NULL,
                      id_shipping_conversation BIGINT NULL,
                      status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                      updated_by INT NULL,
                      started_at DATETIME NULL,
                      completed_at DATETIME NULL,
                      created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      PRIMARY KEY (id_warehouse_order),
                      UNIQUE KEY uk_crm_warehouse_order_sale (id_venta),
                      KEY idx_crm_warehouse_order_status (status, updated_at),
                      KEY idx_crm_warehouse_order_sales_chat (id_sales_conversation),
                      KEY idx_crm_warehouse_order_shipping_chat (id_shipping_conversation),
                      CONSTRAINT fk_crm_warehouse_order_sale FOREIGN KEY (id_venta) REFERENCES venta (id_venta),
                      CONSTRAINT fk_crm_warehouse_order_sales_chat FOREIGN KEY (id_sales_conversation)
                        REFERENCES crm_whatsapp_conversation (id_conversation),
                      CONSTRAINT fk_crm_warehouse_order_shipping_chat FOREIGN KEY (id_shipping_conversation)
                        REFERENCES crm_whatsapp_conversation (id_conversation),
                      CONSTRAINT fk_crm_warehouse_order_updated_by FOREIGN KEY (updated_by) REFERENCES usuario (id_usuario)
                    )
                    """);

            // Existing sales are historical. New sales are inserted by VentaService as PENDING.
            statement.executeUpdate("""
                    INSERT IGNORE INTO crm_warehouse_order
                        (id_venta, status, completed_at, created_at, updated_at)
                    SELECT v.id_venta, 'COMPLETED', COALESCE(v.updated_at, v.fecha),
                           COALESCE(v.fecha, CURRENT_TIMESTAMP), COALESCE(v.updated_at, v.fecha, CURRENT_TIMESTAMP)
                    FROM venta v
                    WHERE v.deleted_at IS NULL
                    """);
            statement.executeUpdate("""
                    UPDATE crm_warehouse_order w
                    JOIN (
                      SELECT f.id_venta, MAX(f.id_conversation) AS id_conversation
                      FROM crm_whatsapp_fulfillment f
                      WHERE f.id_venta IS NOT NULL AND f.id_conversation IS NOT NULL
                      GROUP BY f.id_venta
                    ) x ON x.id_venta = w.id_venta
                    SET w.id_sales_conversation = x.id_conversation
                    WHERE w.id_sales_conversation IS NULL
                    """);
            statement.executeUpdate("""
                    UPDATE crm_warehouse_order w
                    JOIN (
                      SELECT m.related_sale_id AS id_venta, MAX(m.id_conversation) AS id_conversation
                      FROM crm_whatsapp_message m
                      WHERE m.related_sale_id IS NOT NULL
                      GROUP BY m.related_sale_id
                    ) x ON x.id_venta = w.id_venta
                    SET w.id_sales_conversation = x.id_conversation
                    WHERE w.id_sales_conversation IS NULL
                    """);
        }
    }

    private boolean indexExists(Connection connection, String table, String index) throws Exception {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet result = metadata.getIndexInfo(connection.getCatalog(), null, table, false, false)) {
            while (result.next()) {
                if (index.equalsIgnoreCase(result.getString("INDEX_NAME"))) return true;
            }
        }
        return false;
    }
}
