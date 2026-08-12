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
public class EcommerceConfigSchemaMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EcommerceConfigSchemaMigration.class);

    private final DataSource dataSource;

    public EcommerceConfigSchemaMigration(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS ecommerce_config (
                      id_ecommerce_config INT NOT NULL PRIMARY KEY,
                      whatsapp_celular VARCHAR(9) DEFAULT NULL,
                      created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                      updated_at DATETIME(6) DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP(6)
                    )
                    """);
            log.info("Tabla ecommerce_config verificada");
        }
    }
}
