package com.sistemapos.sistematextil.config;

import java.sql.Connection;
import java.sql.Statement;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class EcommercePortadaSchemaMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EcommercePortadaSchemaMigration.class);

    private final DataSource dataSource;

    public EcommercePortadaSchemaMigration(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS ecommerce_portada (
                      id_ecommerce_portada INT AUTO_INCREMENT PRIMARY KEY,
                      desktop_url VARCHAR(600) NOT NULL,
                      desktop_thumb_url VARCHAR(600) DEFAULT NULL,
                      mobile_url VARCHAR(600) NOT NULL,
                      mobile_thumb_url VARCHAR(600) DEFAULT NULL,
                      orden INT NOT NULL DEFAULT 0,
                      estado VARCHAR(20) NOT NULL DEFAULT 'ACTIVO',
                      created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                      updated_at DATETIME(6) DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP(6),
                      deleted_at DATETIME(6) DEFAULT NULL,
                      INDEX idx_ecommerce_portada_publica (estado, deleted_at, orden),
                      INDEX idx_ecommerce_portada_deleted_at (deleted_at)
                    )
                    """);
            log.info("Tabla ecommerce_portada verificada");
        }
    }
}
