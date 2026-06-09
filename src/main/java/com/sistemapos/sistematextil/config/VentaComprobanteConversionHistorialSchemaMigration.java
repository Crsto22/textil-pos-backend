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
public class VentaComprobanteConversionHistorialSchemaMigration implements ApplicationRunner {

    private static final Logger log =
            LoggerFactory.getLogger(VentaComprobanteConversionHistorialSchemaMigration.class);

    private final DataSource dataSource;

    public VentaComprobanteConversionHistorialSchemaMigration(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS venta_comprobante_conversion_historial (
                      id_conversion BIGINT NOT NULL AUTO_INCREMENT,
                      id_venta INT(11) NOT NULL,
                      tipo_comprobante_origen VARCHAR(20) NOT NULL,
                      serie_origen VARCHAR(10) NOT NULL,
                      correlativo_origen INT(11) NOT NULL,
                      tipo_comprobante_destino VARCHAR(20) NOT NULL,
                      serie_destino VARCHAR(10) NOT NULL,
                      correlativo_destino INT(11) NOT NULL,
                      id_cliente_origen INT(11) DEFAULT NULL,
                      id_cliente_destino INT(11) DEFAULT NULL,
                      id_usuario_conversion INT(11) NOT NULL,
                      convertido_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                      PRIMARY KEY (id_conversion),
                      UNIQUE KEY uk_venta_conversion_historial_venta (id_venta),
                      KEY idx_venta_conversion_origen (tipo_comprobante_origen, serie_origen, correlativo_origen),
                      KEY idx_venta_conversion_destino (tipo_comprobante_destino, serie_destino, correlativo_destino),
                      KEY idx_venta_conversion_usuario (id_usuario_conversion),
                      KEY idx_venta_conversion_cliente_origen (id_cliente_origen),
                      KEY idx_venta_conversion_cliente_destino (id_cliente_destino),
                      CONSTRAINT fk_venta_conversion_venta
                        FOREIGN KEY (id_venta) REFERENCES venta (id_venta)
                        ON DELETE RESTRICT ON UPDATE RESTRICT,
                      CONSTRAINT fk_venta_conversion_cliente_origen
                        FOREIGN KEY (id_cliente_origen) REFERENCES cliente (id_cliente)
                        ON DELETE RESTRICT ON UPDATE RESTRICT,
                      CONSTRAINT fk_venta_conversion_cliente_destino
                        FOREIGN KEY (id_cliente_destino) REFERENCES cliente (id_cliente)
                        ON DELETE RESTRICT ON UPDATE RESTRICT,
                      CONSTRAINT fk_venta_conversion_usuario
                        FOREIGN KEY (id_usuario_conversion) REFERENCES usuario (id_usuario)
                        ON DELETE RESTRICT ON UPDATE RESTRICT
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
                    """);

            ensureIndex(statement, connection, "venta_comprobante_conversion_historial",
                    "idx_venta_conversion_origen",
                    """
                    ALTER TABLE venta_comprobante_conversion_historial
                    ADD KEY idx_venta_conversion_origen
                      (tipo_comprobante_origen, serie_origen, correlativo_origen)
                    """);
            ensureIndex(statement, connection, "venta_comprobante_conversion_historial",
                    "idx_venta_conversion_destino",
                    """
                    ALTER TABLE venta_comprobante_conversion_historial
                    ADD KEY idx_venta_conversion_destino
                      (tipo_comprobante_destino, serie_destino, correlativo_destino)
                    """);
        }
    }

    private void ensureIndex(
            Statement statement,
            Connection connection,
            String table,
            String index,
            String sql) throws Exception {
        if (indexExists(connection, table, index)) {
            return;
        }
        statement.execute(sql);
        log.info("Indice {} creado en {}", index, table);
    }

    private boolean indexExists(Connection connection, String tableName, String indexName) throws Exception {
        try (ResultSet indexes = connection.getMetaData()
                .getIndexInfo(connection.getCatalog(), null, tableName, false, false)) {
            while (indexes.next()) {
                if (indexName.equalsIgnoreCase(indexes.getString("INDEX_NAME"))) {
                    return true;
                }
            }
            return false;
        }
    }
}
