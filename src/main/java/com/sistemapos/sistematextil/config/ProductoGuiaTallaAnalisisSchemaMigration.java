package com.sistemapos.sistematextil.config;

import java.sql.Connection;
import java.sql.Statement;

import javax.sql.DataSource;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class ProductoGuiaTallaAnalisisSchemaMigration implements ApplicationRunner {

    private final DataSource dataSource;

    public ProductoGuiaTallaAnalisisSchemaMigration(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS producto_guia_talla (
                      id_guia BIGINT AUTO_INCREMENT PRIMARY KEY,
                      producto_id INT NOT NULL,
                      source_url VARCHAR(600) NOT NULL,
                      source_hash CHAR(64) NOT NULL,
                      version INT NOT NULL,
                      estado VARCHAR(20) NOT NULL,
                      tipo_medicion VARCHAR(20) NOT NULL DEFAULT 'INDETERMINADO',
                      unidad VARCHAR(10) NOT NULL DEFAULT 'CM',
                      source_unit VARCHAR(10) NOT NULL DEFAULT 'CM',
                      descripcion VARCHAR(1000) NULL,
                      confianza INT NULL,
                      provider VARCHAR(30) NULL,
                      model VARCHAR(100) NULL,
                      credential_source VARCHAR(30) NULL,
                      latency_ms BIGINT NULL,
                      analyzed_at DATETIME(6) NULL,
                      created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                      updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
                      CONSTRAINT fk_producto_guia_talla_producto FOREIGN KEY (producto_id)
                        REFERENCES producto(producto_id),
                      CONSTRAINT uk_producto_guia_talla_hash UNIQUE (producto_id, source_hash),
                      INDEX idx_producto_guia_talla_activa (producto_id, estado),
                      INDEX idx_producto_guia_talla_hash (source_hash)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS producto_guia_talla_componente (
                      id_componente BIGINT AUTO_INCREMENT PRIMARY KEY,
                      id_guia BIGINT NOT NULL,
                      nombre VARCHAR(100) NOT NULL,
                      nombre_normalizado VARCHAR(100) NOT NULL,
                      descripcion VARCHAR(500) NULL,
                      orden INT NOT NULL DEFAULT 0,
                      created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                      CONSTRAINT fk_guia_componente_guia FOREIGN KEY (id_guia)
                        REFERENCES producto_guia_talla(id_guia) ON DELETE CASCADE,
                      CONSTRAINT uk_guia_componente UNIQUE (id_guia, nombre_normalizado),
                      INDEX idx_guia_componente_orden (id_guia, orden)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS guia_talla_tipo_medida (
                      id_tipo_medida BIGINT AUTO_INCREMENT PRIMARY KEY,
                      codigo VARCHAR(100) NOT NULL,
                      nombre VARCHAR(150) NOT NULL,
                      aliases VARCHAR(500) NULL,
                      activo TINYINT(1) NOT NULL DEFAULT 1,
                      created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                      updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
                      CONSTRAINT uk_guia_tipo_medida_codigo UNIQUE (codigo)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS producto_guia_talla_medida (
                      id_medida BIGINT AUTO_INCREMENT PRIMARY KEY,
                      id_componente BIGINT NOT NULL,
                      talla_id INT NULL,
                      talla_etiqueta VARCHAR(30) NOT NULL,
                      id_tipo_medida BIGINT NOT NULL,
                      valor_referencia_cm DECIMAL(8,2) NULL,
                      valor_min_cm DECIMAL(8,2) NULL,
                      valor_max_cm DECIMAL(8,2) NULL,
                      tolerancia_cm DECIMAL(8,2) NULL,
                      valor_original VARCHAR(100) NULL,
                      created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                      CONSTRAINT fk_guia_medida_componente FOREIGN KEY (id_componente)
                        REFERENCES producto_guia_talla_componente(id_componente) ON DELETE CASCADE,
                      CONSTRAINT fk_guia_medida_talla FOREIGN KEY (talla_id) REFERENCES tallas(talla_id),
                      CONSTRAINT fk_guia_medida_tipo FOREIGN KEY (id_tipo_medida)
                        REFERENCES guia_talla_tipo_medida(id_tipo_medida),
                      CONSTRAINT uk_guia_medida UNIQUE (id_componente, talla_etiqueta, id_tipo_medida),
                      INDEX idx_guia_medida_talla (talla_id),
                      INDEX idx_guia_medida_consulta (id_componente, id_tipo_medida, talla_etiqueta)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS producto_guia_talla_job (
                      id_job BIGINT AUTO_INCREMENT PRIMARY KEY,
                      producto_id INT NOT NULL,
                      id_connection BIGINT NULL,
                      source_url VARCHAR(600) NOT NULL,
                      source_hash CHAR(64) NOT NULL,
                      status VARCHAR(20) NOT NULL,
                      attempts INT NOT NULL DEFAULT 0,
                      available_at DATETIME(6) NOT NULL,
                      locked_by VARCHAR(100) NULL,
                      lease_until DATETIME(6) NULL,
                      last_error VARCHAR(1000) NULL,
                      created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                      updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
                      completed_at DATETIME(6) NULL,
                      CONSTRAINT fk_guia_job_producto FOREIGN KEY (producto_id) REFERENCES producto(producto_id),
                      CONSTRAINT fk_guia_job_connection FOREIGN KEY (id_connection)
                        REFERENCES crm_whatsapp_connection(id_connection),
                      CONSTRAINT uk_guia_job_hash UNIQUE (producto_id, source_hash),
                      INDEX idx_guia_job_claim (status, available_at, lease_until)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                    """);
        }
    }
}
