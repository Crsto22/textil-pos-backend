package com.sistemapos.sistematextil.config;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CrmWhatsappSchemaMigration implements ApplicationRunner {

    private final DataSource dataSource;

    public CrmWhatsappSchemaMigration(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS crm_whatsapp_conversation (
                      id_conversation BIGINT NOT NULL AUTO_INCREMENT,
                      phone VARCHAR(80) NOT NULL,
                      phone_number VARCHAR(30) NULL,
                      whatsapp_username VARCHAR(120) NULL,
                      contact_name VARCHAR(150) NULL,
                      status VARCHAR(20) NOT NULL DEFAULT 'ESPERA',
                      last_message TEXT NULL,
                      last_message_type VARCHAR(20) NULL,
                      last_message_at DATETIME NULL,
                      unread_count INT NOT NULL DEFAULT 0,
                      id_cliente INT NULL,
                      assigned_user_id INT NULL,
                      assigned_at DATETIME NULL,
                      phone_number_version INT NOT NULL DEFAULT 0,
                      created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      PRIMARY KEY (id_conversation),
                      UNIQUE KEY uk_crm_whatsapp_conversation_phone (phone),
                      KEY idx_crm_whatsapp_conversation_assigned_status (assigned_user_id, status),
                      KEY idx_crm_whatsapp_conversation_cliente (id_cliente),
                      KEY idx_crm_whatsapp_conversation_status_last (status, last_message_at)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS crm_whatsapp_message (
                      id_message BIGINT NOT NULL AUTO_INCREMENT,
                      id_conversation BIGINT NOT NULL,
                      direction VARCHAR(20) NOT NULL,
                      origin VARCHAR(30) NOT NULL DEFAULT 'EXTERNAL',
                      actor_user_id INT NULL,
                      actor_name VARCHAR(180) NULL,
                      activity_session_id VARCHAR(100) NULL,
                      message_type VARCHAR(20) NOT NULL DEFAULT 'TEXT',
                      body TEXT NULL,
                      whatsapp_message_id VARCHAR(120) NULL,
                      message_status VARCHAR(20) NOT NULL DEFAULT 'sent',
                      media_mime_type VARCHAR(120) NULL,
                      media_file_name VARCHAR(255) NULL,
                      media_storage_path VARCHAR(500) NULL,
                      media_duration_seconds INT NULL,
                      audio_transcription TEXT NULL,
                      audio_transcription_status VARCHAR(20) NULL,
                      audio_transcription_language VARCHAR(20) NULL,
                      audio_transcription_confidence INT NULL,
                      message_key_json TEXT NULL,
                      baileys_message_json MEDIUMTEXT NULL,
                      reply_to_message_id BIGINT NULL,
                      deleted_at DATETIME NULL,
                      created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      PRIMARY KEY (id_message),
                      UNIQUE KEY uk_crm_whatsapp_message_wamid (whatsapp_message_id),
                      UNIQUE KEY uk_crm_message_activity_session (id_conversation, actor_user_id, activity_session_id),
                      KEY idx_crm_whatsapp_message_conversation_created (id_conversation, created_at),
                      CONSTRAINT fk_crm_whatsapp_message_conversation
                        FOREIGN KEY (id_conversation) REFERENCES crm_whatsapp_conversation (id_conversation),
                      CONSTRAINT fk_crm_whatsapp_message_actor
                        FOREIGN KEY (actor_user_id) REFERENCES usuario (id_usuario) ON DELETE SET NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS crm_whatsapp_tag (
                      id_tag BIGINT NOT NULL AUTO_INCREMENT,
                      id_empresa INT NOT NULL,
                      nombre VARCHAR(80) NOT NULL,
                      color VARCHAR(20) NOT NULL DEFAULT '#3b82f6',
                      created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      deleted_at DATETIME NULL,
                      PRIMARY KEY (id_tag),
                      KEY idx_crm_whatsapp_tag_empresa_deleted (id_empresa, deleted_at),
                      KEY idx_crm_whatsapp_tag_nombre (nombre),
                      CONSTRAINT fk_crm_whatsapp_tag_empresa
                        FOREIGN KEY (id_empresa) REFERENCES empresa (id_empresa)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS crm_whatsapp_conversation_tag (
                      id_conversation_tag BIGINT NOT NULL AUTO_INCREMENT,
                      id_conversation BIGINT NOT NULL,
                      id_tag BIGINT NOT NULL,
                      created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      deleted_at DATETIME NULL,
                      PRIMARY KEY (id_conversation_tag),
                      KEY idx_crm_whatsapp_conversation_tag_conversation (id_conversation, deleted_at),
                      KEY idx_crm_whatsapp_conversation_tag_tag (id_tag, deleted_at),
                      CONSTRAINT fk_crm_whatsapp_conversation_tag_conversation
                        FOREIGN KEY (id_conversation) REFERENCES crm_whatsapp_conversation (id_conversation),
                      CONSTRAINT fk_crm_whatsapp_conversation_tag_tag
                        FOREIGN KEY (id_tag) REFERENCES crm_whatsapp_tag (id_tag)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS crm_quick_message (
                      id_quick_message BIGINT NOT NULL AUTO_INCREMENT,
                      id_empresa INT NOT NULL,
                      created_by INT NOT NULL,
                      keyword VARCHAR(40) NOT NULL,
                      created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      deleted_at DATETIME NULL,
                      PRIMARY KEY (id_quick_message),
                      KEY idx_crm_quick_message_company (id_empresa, deleted_at, keyword),
                      CONSTRAINT fk_crm_quick_message_company FOREIGN KEY (id_empresa) REFERENCES empresa (id_empresa),
                      CONSTRAINT fk_crm_quick_message_creator FOREIGN KEY (created_by) REFERENCES usuario (id_usuario)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS crm_quick_message_block (
                      id_block BIGINT NOT NULL AUTO_INCREMENT,
                      id_quick_message BIGINT NOT NULL,
                      block_type VARCHAR(10) NOT NULL,
                      position INT NOT NULL,
                      body TEXT NULL,
                      media_storage_path VARCHAR(500) NULL,
                      media_file_name VARCHAR(255) NULL,
                      media_mime_type VARCHAR(120) NULL,
                      created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      deleted_at DATETIME NULL,
                      PRIMARY KEY (id_block),
                      KEY idx_crm_quick_message_block_order (id_quick_message, deleted_at, position),
                      CONSTRAINT fk_crm_quick_message_block_command
                        FOREIGN KEY (id_quick_message) REFERENCES crm_quick_message (id_quick_message)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS crm_whatsapp_connection (
                      id_connection BIGINT NOT NULL AUTO_INCREMENT,
                      client_id VARCHAR(80) NOT NULL,
                      area VARCHAR(20) NOT NULL DEFAULT 'SALES',
                      id_empresa INT NOT NULL,
                      id_sucursal INT NULL,
                      connected_number VARCHAR(100) NULL,
                      approved_number VARCHAR(100) NULL,
                      connection_status VARCHAR(30) NOT NULL DEFAULT 'UNKNOWN',
                      phone_changed_at DATETIME NULL,
                      phone_number_version INT NOT NULL DEFAULT 0,
                      disconnected_at DATETIME NULL,
                      created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      PRIMARY KEY (id_connection),
                      UNIQUE KEY uk_crm_whatsapp_connection_client (client_id),
                      UNIQUE KEY uk_crm_whatsapp_connection_empresa_area (id_empresa, area),
                      KEY idx_crm_whatsapp_connection_empresa (id_empresa),
                      KEY idx_crm_whatsapp_connection_sucursal (id_sucursal),
                      CONSTRAINT fk_crm_whatsapp_connection_empresa
                        FOREIGN KEY (id_empresa) REFERENCES empresa (id_empresa),
                      CONSTRAINT fk_crm_whatsapp_connection_sucursal
                        FOREIGN KEY (id_sucursal) REFERENCES sucursal (id_sucursal)
                    )
                    """);
            addColumnIfMissing(connection, statement, "crm_whatsapp_connection", "approved_number",
                    "VARCHAR(100) NULL");
            addColumnIfMissing(connection, statement, "crm_whatsapp_connection", "area",
                    "VARCHAR(20) NOT NULL DEFAULT 'SALES'");
            addColumnIfMissing(connection, statement, "crm_whatsapp_connection", "connection_status",
                    "VARCHAR(30) NOT NULL DEFAULT 'UNKNOWN'");
            addColumnIfMissing(connection, statement, "crm_whatsapp_connection", "phone_changed_at",
                    "DATETIME NULL");
            addColumnIfMissing(connection, statement, "crm_whatsapp_connection", "phone_number_version",
                    "INT NOT NULL DEFAULT 0");
            addColumnIfMissing(connection, statement, "crm_whatsapp_connection", "disconnected_at",
                    "DATETIME NULL");
            statement.execute("ALTER TABLE crm_whatsapp_connection MODIFY id_sucursal INT NULL");
            statement.executeUpdate("""
                    UPDATE crm_whatsapp_connection
                    SET area = 'SALES'
                    WHERE area IS NULL OR area = ''
                    """);
            statement.executeUpdate("""
                    UPDATE IGNORE crm_whatsapp_connection
                    SET client_id = 'kiments-sales'
                    WHERE client_id = 'kiments-main'
                    """);
            if (!indexExists(connection, "crm_whatsapp_connection", "uk_crm_whatsapp_connection_empresa_area")) {
                statement.execute("""
                        ALTER TABLE crm_whatsapp_connection
                        ADD UNIQUE KEY uk_crm_whatsapp_connection_empresa_area (id_empresa, area)
                        """);
            }
            statement.executeUpdate("""
                    UPDATE crm_whatsapp_connection
                    SET approved_number = connected_number
                    WHERE approved_number IS NULL AND connected_number IS NOT NULL
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS crm_whatsapp_ai_config (
                      id_ai_config BIGINT NOT NULL AUTO_INCREMENT,
                      id_connection BIGINT NOT NULL,
                      gemini_api_key_ciphertext TEXT NULL,
                      gemini_api_key_nonce VARCHAR(64) NULL,
                      gemini_model VARCHAR(100) NULL,
                      agent_enabled BOOLEAN NOT NULL DEFAULT FALSE,
                      activation_stage VARCHAR(30) NOT NULL DEFAULT 'LOCAL_TEST',
                      test_phone_numbers VARCHAR(1000) NULL,
                      limited_conversations_per_hour INT NOT NULL DEFAULT 10,
                      message_grouping_ms INT NOT NULL DEFAULT 5000,
                      payment_reservation_seconds INT NOT NULL DEFAULT 360,
                      receipt_minimum_confidence INT NOT NULL DEFAULT 85,
                      advisor_attention_enabled BOOLEAN NOT NULL DEFAULT FALSE,
                      first_image_to_advisor_enabled BOOLEAN NOT NULL DEFAULT FALSE,
                      job_max_attempts INT NOT NULL DEFAULT 3,
                      outbox_max_attempts INT NOT NULL DEFAULT 5,
                      max_messages_per_batch INT NOT NULL DEFAULT 15,
                      max_tool_calls_per_run INT NOT NULL DEFAULT 12,
                      max_output_messages_per_run INT NOT NULL DEFAULT 6,
                      welcome_message TEXT NULL,
                      agent_behavior_instructions TEXT NULL,
                      api_key_updated_at DATETIME NULL,
                      api_key_updated_by INT NULL,
                      created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      PRIMARY KEY (id_ai_config),
                      UNIQUE KEY uk_crm_whatsapp_ai_config_connection (id_connection),
                      KEY idx_crm_whatsapp_ai_config_key_user (api_key_updated_by),
                      CONSTRAINT fk_crm_whatsapp_ai_config_connection
                        FOREIGN KEY (id_connection) REFERENCES crm_whatsapp_connection (id_connection),
                      CONSTRAINT fk_crm_whatsapp_ai_config_key_user
                        FOREIGN KEY (api_key_updated_by) REFERENCES usuario (id_usuario)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS crm_whatsapp_business_hours (
                      id_business_hours BIGINT NOT NULL AUTO_INCREMENT,
                      id_connection BIGINT NOT NULL,
                      day_of_week VARCHAR(12) NOT NULL,
                      closed BOOLEAN NOT NULL DEFAULT FALSE,
                      opens_at TIME NULL,
                      closes_at TIME NULL,
                      created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      PRIMARY KEY (id_business_hours),
                      UNIQUE KEY uk_crm_whatsapp_business_hours_day (id_connection, day_of_week),
                      CONSTRAINT fk_crm_whatsapp_business_hours_connection
                        FOREIGN KEY (id_connection) REFERENCES crm_whatsapp_connection (id_connection) ON DELETE CASCADE
                    )
                    """);
            statement.execute("""
                    ALTER TABLE crm_whatsapp_conversation
                    MODIFY phone VARCHAR(80) NOT NULL
                    """);
            if (!columnExists(connection, "crm_whatsapp_conversation", "phone_number")) {
                statement.execute("""
                        ALTER TABLE crm_whatsapp_conversation
                        ADD COLUMN phone_number VARCHAR(30) NULL
                        """);
            }
            addColumnIfMissing(connection, statement, "crm_whatsapp_conversation", "phone_number_version",
                    "INT NOT NULL DEFAULT 0");
            if (!columnExists(connection, "crm_whatsapp_ai_config", "gemini_api_key_ciphertext")) {
                statement.execute("""
                        ALTER TABLE crm_whatsapp_ai_config
                        ADD COLUMN gemini_api_key_ciphertext TEXT NULL
                        """);
            }
            if (!columnExists(connection, "crm_whatsapp_ai_config", "gemini_api_key_nonce")) {
                statement.execute("""
                        ALTER TABLE crm_whatsapp_ai_config
                        ADD COLUMN gemini_api_key_nonce VARCHAR(64) NULL
                        """);
            }
            if (!columnExists(connection, "crm_whatsapp_ai_config", "gemini_model")) {
                statement.execute("""
                        ALTER TABLE crm_whatsapp_ai_config
                        ADD COLUMN gemini_model VARCHAR(100) NULL
                        """);
            }
            addColumnIfMissing(connection, statement, "crm_whatsapp_ai_config", "agent_enabled",
                    "BOOLEAN NOT NULL DEFAULT FALSE");
            addColumnIfMissing(connection, statement, "crm_whatsapp_ai_config", "activation_stage",
                    "VARCHAR(30) NOT NULL DEFAULT 'LOCAL_TEST'");
            addColumnIfMissing(connection, statement, "crm_whatsapp_ai_config", "test_phone_numbers",
                    "VARCHAR(1000) NULL");
            addColumnIfMissing(connection, statement, "crm_whatsapp_ai_config", "limited_conversations_per_hour",
                    "INT NOT NULL DEFAULT 10");
            addColumnIfMissing(connection, statement, "crm_whatsapp_ai_config", "message_grouping_ms",
                    "INT NOT NULL DEFAULT 5000");
            addColumnIfMissing(connection, statement, "crm_whatsapp_ai_config", "payment_reservation_seconds",
                    "INT NOT NULL DEFAULT 360");
            statement.execute("""
                    ALTER TABLE crm_whatsapp_ai_config
                    MODIFY payment_reservation_seconds INT NOT NULL DEFAULT 360
                    """);
            statement.executeUpdate("""
                    UPDATE crm_whatsapp_ai_config
                    SET payment_reservation_seconds = 360
                    WHERE payment_reservation_seconds = 240
                    """);
            addColumnIfMissing(connection, statement, "crm_whatsapp_ai_config", "receipt_minimum_confidence",
                    "INT NOT NULL DEFAULT 85");
            addColumnIfMissing(connection, statement, "crm_whatsapp_ai_config", "advisor_attention_enabled",
                    "BOOLEAN NOT NULL DEFAULT FALSE");
            addColumnIfMissing(connection, statement, "crm_whatsapp_ai_config", "first_image_to_advisor_enabled",
                    "BOOLEAN NOT NULL DEFAULT FALSE");
            addColumnIfMissing(connection, statement, "crm_whatsapp_ai_config", "job_max_attempts",
                    "INT NOT NULL DEFAULT 3");
            addColumnIfMissing(connection, statement, "crm_whatsapp_ai_config", "outbox_max_attempts",
                    "INT NOT NULL DEFAULT 5");
            addColumnIfMissing(connection, statement, "crm_whatsapp_ai_config", "max_messages_per_batch",
                    "INT NOT NULL DEFAULT 15");
            addColumnIfMissing(connection, statement, "crm_whatsapp_ai_config", "max_tool_calls_per_run",
                    "INT NOT NULL DEFAULT 12");
            addColumnIfMissing(connection, statement, "crm_whatsapp_ai_config", "max_output_messages_per_run",
                    "INT NOT NULL DEFAULT 6");
            addColumnIfMissing(connection, statement, "crm_whatsapp_ai_config", "welcome_message",
                    "TEXT NULL");
            addColumnIfMissing(connection, statement, "crm_whatsapp_ai_config", "agent_behavior_instructions",
                    "TEXT NULL");
            statement.executeUpdate("""
                    UPDATE crm_whatsapp_ai_config
                    SET welcome_message = REPLACE(welcome_message,
                        'penalidad de S/20.00 por cada cambio de prenda',
                        'penalidad de S/10.00 por cada cambio de prenda')
                    WHERE welcome_message LIKE '%penalidad de S/20.00 por cada cambio de prenda%'
                    """);
            if (!columnExists(connection, "crm_whatsapp_ai_config", "api_key_updated_at")) {
                statement.execute("""
                        ALTER TABLE crm_whatsapp_ai_config
                        ADD COLUMN api_key_updated_at DATETIME NULL
                        """);
            }
            if (!columnExists(connection, "crm_whatsapp_ai_config", "api_key_updated_by")) {
                statement.execute("""
                        ALTER TABLE crm_whatsapp_ai_config
                        ADD COLUMN api_key_updated_by INT NULL
                        """);
            }
            if (!indexExists(connection, "crm_whatsapp_ai_config", "idx_crm_whatsapp_ai_config_key_user")) {
                statement.execute("""
                        ALTER TABLE crm_whatsapp_ai_config
                        ADD KEY idx_crm_whatsapp_ai_config_key_user (api_key_updated_by)
                        """);
            }
            if (!foreignKeyExists(connection, "crm_whatsapp_ai_config", "fk_crm_whatsapp_ai_config_key_user")) {
                statement.execute("""
                        ALTER TABLE crm_whatsapp_ai_config
                        ADD CONSTRAINT fk_crm_whatsapp_ai_config_key_user
                          FOREIGN KEY (api_key_updated_by) REFERENCES usuario (id_usuario)
                        """);
            }
            if (!columnExists(connection, "crm_whatsapp_conversation", "whatsapp_username")) {
                statement.execute("""
                        ALTER TABLE crm_whatsapp_conversation
                        ADD COLUMN whatsapp_username VARCHAR(120) NULL
                        """);
            }
            if (!columnExists(connection, "crm_whatsapp_conversation", "last_message_type")) {
                statement.execute("""
                        ALTER TABLE crm_whatsapp_conversation
                        ADD COLUMN last_message_type VARCHAR(20) NULL
                        """);
            }
            if (!columnExists(connection, "crm_whatsapp_conversation", "assigned_user_id")) {
                statement.execute("""
                        ALTER TABLE crm_whatsapp_conversation
                        ADD COLUMN assigned_user_id INT NULL
                        """);
            }
            if (!columnExists(connection, "crm_whatsapp_conversation", "id_cliente")) {
                statement.execute("""
                        ALTER TABLE crm_whatsapp_conversation
                        ADD COLUMN id_cliente INT NULL
                        """);
            }
            if (!indexExists(connection, "crm_whatsapp_conversation", "idx_crm_whatsapp_conversation_cliente")) {
                statement.execute("""
                        ALTER TABLE crm_whatsapp_conversation
                        ADD KEY idx_crm_whatsapp_conversation_cliente (id_cliente)
                        """);
            }
            if (!foreignKeyExists(connection, "crm_whatsapp_conversation", "fk_crm_whatsapp_conversation_cliente")) {
                statement.execute("""
                        ALTER TABLE crm_whatsapp_conversation
                        ADD CONSTRAINT fk_crm_whatsapp_conversation_cliente
                          FOREIGN KEY (id_cliente) REFERENCES cliente (id_cliente)
                        """);
            }
            if (!columnExists(connection, "crm_whatsapp_conversation", "assigned_at")) {
                statement.execute("""
                        ALTER TABLE crm_whatsapp_conversation
                        ADD COLUMN assigned_at DATETIME NULL
                        """);
            }
            if (!columnExists(connection, "crm_whatsapp_conversation", "id_connection")) {
                statement.execute("""
                        ALTER TABLE crm_whatsapp_conversation
                        ADD COLUMN id_connection BIGINT NULL
                        """);
            }
            if (!indexExists(connection, "crm_whatsapp_conversation", "idx_crm_whatsapp_conversation_connection")) {
                statement.execute("""
                        ALTER TABLE crm_whatsapp_conversation
                        ADD KEY idx_crm_whatsapp_conversation_connection (id_connection)
                        """);
            }
            if (!foreignKeyExists(connection, "crm_whatsapp_conversation", "fk_crm_whatsapp_conversation_connection")) {
                statement.execute("""
                        ALTER TABLE crm_whatsapp_conversation
                        ADD CONSTRAINT fk_crm_whatsapp_conversation_connection
                          FOREIGN KEY (id_connection) REFERENCES crm_whatsapp_connection (id_connection)
                        """);
            }
            if (!indexExists(connection, "crm_whatsapp_conversation", "idx_crm_whatsapp_conversation_assigned_status")) {
                statement.execute("""
                        ALTER TABLE crm_whatsapp_conversation
                        ADD KEY idx_crm_whatsapp_conversation_assigned_status (assigned_user_id, status)
                        """);
            }
            if (!foreignKeyExists(connection, "crm_whatsapp_conversation", "fk_crm_whatsapp_conversation_assigned_user")) {
                statement.execute("""
                        ALTER TABLE crm_whatsapp_conversation
                        ADD CONSTRAINT fk_crm_whatsapp_conversation_assigned_user
                          FOREIGN KEY (assigned_user_id) REFERENCES usuario (id_usuario)
                        """);
            }
            if (!columnExists(connection, "crm_whatsapp_message", "message_key_json")) {
                statement.execute("""
                        ALTER TABLE crm_whatsapp_message
                        ADD COLUMN message_key_json TEXT NULL
                        """);
            }
            if (!columnExists(connection, "crm_whatsapp_message", "baileys_message_json")) {
                statement.execute("""
                        ALTER TABLE crm_whatsapp_message
                        ADD COLUMN baileys_message_json MEDIUMTEXT NULL
                        """);
            }
            if (!columnExists(connection, "crm_whatsapp_message", "origin")) {
                statement.execute("""
                        ALTER TABLE crm_whatsapp_message
                        ADD COLUMN origin VARCHAR(30) NOT NULL DEFAULT 'EXTERNAL' AFTER direction
                        """);
            }
            if (!columnExists(connection, "crm_whatsapp_message", "reply_to_message_id")) {
                statement.execute("""
                        ALTER TABLE crm_whatsapp_message
                        ADD COLUMN reply_to_message_id BIGINT NULL
                        """);
            }
            if (!columnExists(connection, "crm_whatsapp_message", "deleted_at")) {
                statement.execute("""
                        ALTER TABLE crm_whatsapp_message
                        ADD COLUMN deleted_at DATETIME NULL
                        """);
            }
            if (!indexExists(connection, "crm_whatsapp_message", "idx_crm_whatsapp_message_reply")) {
                statement.execute("""
                        ALTER TABLE crm_whatsapp_message
                        ADD KEY idx_crm_whatsapp_message_reply (reply_to_message_id)
                        """);
            }
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS crm_whatsapp_payment_request (
                      id_payment_request BIGINT NOT NULL AUTO_INCREMENT,
                      id_conversation BIGINT NULL,
                      id_connection BIGINT NOT NULL,
                      id_sucursal INT NOT NULL,
                      id_cliente INT NULL,
                      id_metodo_pago INT NOT NULL,
                      id_metodo_pago_cuenta INT NULL,
                      id_venta INT NULL,
                      created_by INT NOT NULL,
                      expected_amount DECIMAL(12,2) NOT NULL,
                      currency VARCHAR(3) NOT NULL DEFAULT 'PEN',
                      sale_request_json MEDIUMTEXT NOT NULL,
                      status VARCHAR(30) NOT NULL DEFAULT 'PENDING_EVIDENCE',
                      expires_at DATETIME NOT NULL,
                      completed_at DATETIME NULL,
                      created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      PRIMARY KEY (id_payment_request),
                      KEY idx_crm_payment_request_conversation_status (id_conversation, status, created_at),
                      KEY idx_crm_payment_request_expires (status, expires_at),
                      CONSTRAINT fk_crm_payment_request_conversation FOREIGN KEY (id_conversation) REFERENCES crm_whatsapp_conversation (id_conversation) ON DELETE SET NULL,
                      CONSTRAINT fk_crm_payment_request_connection FOREIGN KEY (id_connection) REFERENCES crm_whatsapp_connection (id_connection),
                      CONSTRAINT fk_crm_payment_request_sucursal FOREIGN KEY (id_sucursal) REFERENCES sucursal (id_sucursal),
                      CONSTRAINT fk_crm_payment_request_cliente FOREIGN KEY (id_cliente) REFERENCES cliente (id_cliente),
                      CONSTRAINT fk_crm_payment_request_metodo FOREIGN KEY (id_metodo_pago) REFERENCES metodo_pago_config (id_metodo_pago),
                      CONSTRAINT fk_crm_payment_request_cuenta FOREIGN KEY (id_metodo_pago_cuenta) REFERENCES metodo_pago_cuenta (id_metodo_pago_cuenta),
                      CONSTRAINT fk_crm_payment_request_venta FOREIGN KEY (id_venta) REFERENCES venta (id_venta),
                      CONSTRAINT fk_crm_payment_request_user FOREIGN KEY (created_by) REFERENCES usuario (id_usuario)
                    )
                    """);
            if (!columnExists(connection, "crm_whatsapp_payment_request", "reservation_status")) {
                statement.execute("""
                        ALTER TABLE crm_whatsapp_payment_request
                        ADD COLUMN reservation_status VARCHAR(20) NOT NULL DEFAULT 'LEGACY_NONE',
                        ADD COLUMN reserved_at DATETIME NULL,
                        ADD COLUMN review_expires_at DATETIME NULL,
                        ADD COLUMN released_at DATETIME NULL,
                        ADD COLUMN release_reason VARCHAR(500) NULL,
                        ADD KEY idx_crm_payment_request_reservation (reservation_status, expires_at)
                        """);
                statement.execute("""
                        UPDATE crm_whatsapp_payment_request
                        SET status = 'EXPIRED', release_reason = 'Flujo anterior sin reserva de stock'
                        WHERE reservation_status = 'LEGACY_NONE'
                          AND status IN ('PENDING_EVIDENCE', 'UNDER_REVIEW')
                        """);
            }
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS crm_whatsapp_payment_request_item (
                      id_payment_request_item BIGINT NOT NULL AUTO_INCREMENT,
                      id_payment_request BIGINT NOT NULL,
                      id_producto_variante INT NOT NULL,
                      cantidad INT NOT NULL,
                      unit_price DECIMAL(12,2) NOT NULL,
                      created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      PRIMARY KEY (id_payment_request_item),
                      UNIQUE KEY uk_crm_payment_request_item_variant (id_payment_request, id_producto_variante),
                      CONSTRAINT fk_crm_payment_request_item_request FOREIGN KEY (id_payment_request) REFERENCES crm_whatsapp_payment_request (id_payment_request),
                      CONSTRAINT fk_crm_payment_request_item_variant FOREIGN KEY (id_producto_variante) REFERENCES producto_variante (id_producto_variante)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS crm_whatsapp_payment_evidence (
                      id_payment_evidence BIGINT NOT NULL AUTO_INCREMENT,
                      id_message BIGINT NULL,
                      id_conversation BIGINT NULL,
                      id_payment_request BIGINT NULL,
                      reviewed_by INT NULL,
                      storage_path VARCHAR(500) NOT NULL,
                      mime_type VARCHAR(120) NOT NULL,
                      file_name VARCHAR(255) NULL,
                      sha256 VARCHAR(64) NOT NULL,
                      perceptual_hash VARCHAR(32) NULL,
                      detected_provider VARCHAR(40) NULL,
                      detected_amount DECIMAL(12,2) NULL,
                      detected_currency VARCHAR(3) NULL,
                      operation_code VARCHAR(120) NULL,
                      operation_at DATETIME NULL,
                      recipient VARCHAR(180) NULL,
                      confidence INT NULL,
                      extracted_text TEXT NULL,
                      extraction_json TEXT NULL,
                      warnings_json TEXT NULL,
                      duplicate_of_id BIGINT NULL,
                      validation_status VARCHAR(30) NOT NULL DEFAULT 'PENDIENTE_VALIDACION',
                      processing_status VARCHAR(20) NOT NULL DEFAULT 'QUEUED',
                      attempts INT NOT NULL DEFAULT 0,
                      available_at DATETIME NOT NULL,
                      last_error VARCHAR(1000) NULL,
                      review_note VARCHAR(500) NULL,
                      reviewed_at DATETIME NULL,
                      created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      PRIMARY KEY (id_payment_evidence),
                      UNIQUE KEY uk_crm_payment_evidence_message (id_message),
                      KEY idx_crm_payment_evidence_ready (processing_status, available_at),
                      KEY idx_crm_payment_evidence_conversation (id_conversation, created_at),
                      KEY idx_crm_payment_evidence_sha (sha256),
                      KEY idx_crm_payment_evidence_operation (operation_code),
                      CONSTRAINT fk_crm_payment_evidence_message FOREIGN KEY (id_message) REFERENCES crm_whatsapp_message (id_message) ON DELETE SET NULL,
                      CONSTRAINT fk_crm_payment_evidence_conversation FOREIGN KEY (id_conversation) REFERENCES crm_whatsapp_conversation (id_conversation) ON DELETE SET NULL,
                      CONSTRAINT fk_crm_payment_evidence_request FOREIGN KEY (id_payment_request) REFERENCES crm_whatsapp_payment_request (id_payment_request),
                      CONSTRAINT fk_crm_payment_evidence_reviewer FOREIGN KEY (reviewed_by) REFERENCES usuario (id_usuario)
                    )
                    """);
            if (!columnExists(connection, "crm_whatsapp_payment_evidence", "customer_notified_at")) {
                statement.execute("""
                        ALTER TABLE crm_whatsapp_payment_evidence
                        ADD COLUMN customer_notified_at DATETIME NULL
                        """);
            }
            if (!columnExists(connection, "metodo_pago_cuenta", "titular")) {
                statement.execute("ALTER TABLE metodo_pago_cuenta ADD COLUMN titular VARCHAR(150) NULL");
            }
            if (!columnExists(connection, "metodo_pago_cuenta", "aliases_validacion")) {
                statement.execute("ALTER TABLE metodo_pago_cuenta ADD COLUMN aliases_validacion VARCHAR(500) NULL");
            }
            if (!columnExists(connection, "metodo_pago_cuenta", "activo")) {
                statement.execute("ALTER TABLE metodo_pago_cuenta ADD COLUMN activo TINYINT(1) NOT NULL DEFAULT 1");
            }
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS crm_whatsapp_ai_knowledge_article (
                      id_knowledge_article BIGINT NOT NULL AUTO_INCREMENT,
                      id_connection BIGINT NOT NULL,
                      title VARCHAR(160) NOT NULL,
                      category VARCHAR(30) NOT NULL,
                      content TEXT NOT NULL,
                      keywords VARCHAR(500) NULL,
                      status VARCHAR(20) NOT NULL DEFAULT 'BORRADOR',
                      active_version INT NOT NULL DEFAULT 0,
                      pending_version INT NOT NULL DEFAULT 0,
                      source_key VARCHAR(80) NULL,
                      last_error VARCHAR(500) NULL,
                      indexed_at DATETIME NULL,
                      created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      deleted_at DATETIME NULL,
                      PRIMARY KEY (id_knowledge_article),
                      UNIQUE KEY uk_crm_ai_knowledge_source (id_connection, source_key),
                      KEY idx_crm_ai_knowledge_connection_status (id_connection, status, deleted_at),
                      CONSTRAINT fk_crm_ai_knowledge_connection
                        FOREIGN KEY (id_connection) REFERENCES crm_whatsapp_connection (id_connection)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS crm_whatsapp_ai_knowledge_chunk (
                      id_knowledge_chunk BIGINT NOT NULL AUTO_INCREMENT,
                      id_knowledge_article BIGINT NOT NULL,
                      article_version INT NOT NULL,
                      chunk_order INT NOT NULL,
                      content TEXT NOT NULL,
                      embedding_json MEDIUMTEXT NOT NULL,
                      embedding_model VARCHAR(100) NOT NULL,
                      created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      PRIMARY KEY (id_knowledge_chunk),
                      UNIQUE KEY uk_crm_ai_knowledge_chunk_version (id_knowledge_article, article_version, chunk_order),
                      KEY idx_crm_ai_knowledge_chunk_article (id_knowledge_article, article_version),
                      CONSTRAINT fk_crm_ai_knowledge_chunk_article
                        FOREIGN KEY (id_knowledge_article) REFERENCES crm_whatsapp_ai_knowledge_article (id_knowledge_article) ON DELETE CASCADE
                    )
                    """);
            statement.execute("""
                    UPDATE crm_whatsapp_conversation c
                    SET c.status = 'ESPERA'
                    WHERE c.status = 'ATENDIDO'
                      AND c.assigned_user_id IS NULL
                    """);
            addColumnIfMissing(connection, statement, "crm_whatsapp_conversation", "attention_queue",
                    "VARCHAR(30) NOT NULL DEFAULT 'ADVISOR_REQUIRED'");
            addColumnIfMissing(connection, statement, "crm_whatsapp_conversation", "waiting_reason",
                    "VARCHAR(30) NULL");
            addColumnIfMissing(connection, statement, "crm_whatsapp_conversation", "waiting_detail",
                    "VARCHAR(500) NULL");
            addColumnIfMissing(connection, statement, "crm_whatsapp_message", "related_sale_id", "INT NULL");
            addColumnIfMissing(connection, statement, "crm_whatsapp_message", "receipt_format", "VARCHAR(10) NULL");
            addColumnIfMissing(connection, statement, "crm_whatsapp_message", "media_duration_seconds", "INT NULL");
            addColumnIfMissing(connection, statement, "crm_whatsapp_message", "audio_transcription", "TEXT NULL");
            addColumnIfMissing(connection, statement, "crm_whatsapp_message", "audio_transcription_status",
                    "VARCHAR(20) NULL");
            addColumnIfMissing(connection, statement, "crm_whatsapp_message", "audio_transcription_language",
                    "VARCHAR(20) NULL");
            addColumnIfMissing(connection, statement, "crm_whatsapp_message", "audio_transcription_confidence",
                    "INT NULL");
            addColumnIfMissing(connection, statement, "crm_whatsapp_message", "actor_user_id", "INT NULL");
            addColumnIfMissing(connection, statement, "crm_whatsapp_message", "actor_name", "VARCHAR(180) NULL");
            addColumnIfMissing(connection, statement, "crm_whatsapp_message", "activity_session_id", "VARCHAR(100) NULL");
            if (!indexExists(connection, "crm_whatsapp_message", "uk_crm_message_activity_session")) {
                statement.execute("""
                        ALTER TABLE crm_whatsapp_message
                        ADD UNIQUE KEY uk_crm_message_activity_session
                          (id_conversation, actor_user_id, activity_session_id)
                        """);
            }
            if (!foreignKeyExists(connection, "crm_whatsapp_message", "fk_crm_whatsapp_message_actor")) {
                statement.execute("""
                        ALTER TABLE crm_whatsapp_message
                        ADD CONSTRAINT fk_crm_whatsapp_message_actor
                          FOREIGN KEY (actor_user_id) REFERENCES usuario (id_usuario) ON DELETE SET NULL
                        """);
            }
            if (!indexExists(connection, "crm_whatsapp_message", "idx_crm_message_sale_receipt")) {
                statement.execute("""
                        ALTER TABLE crm_whatsapp_message
                        ADD KEY idx_crm_message_sale_receipt (id_conversation, related_sale_id, receipt_format)
                        """);
            }
            statement.execute("""
                    UPDATE crm_whatsapp_conversation
                    SET attention_queue = CASE
                      WHEN status = 'RESUELTO' THEN 'RESOLVED'
                      WHEN assigned_user_id IS NOT NULL OR status = 'ATENDIDO' THEN 'HUMAN_ACTIVE'
                      WHEN waiting_reason = 'PAYMENT_VERIFICATION' THEN 'AGENT_ACTIVE'
                      WHEN attention_queue IN ('AGENT_ACTIVE', 'AI_ACTIVE') THEN 'AGENT_ACTIVE'
                      ELSE 'ADVISOR_REQUIRED'
                    END,
                    waiting_reason = CASE
                      WHEN status = 'ESPERA' AND assigned_user_id IS NULL
                           AND waiting_reason = 'PAYMENT_VERIFICATION' THEN NULL
                      WHEN status = 'ESPERA' AND assigned_user_id IS NULL
                           AND attention_queue = 'AGENT_ACTIVE' THEN NULL
                      WHEN status = 'ESPERA' AND assigned_user_id IS NULL THEN 'ADVISOR_REQUIRED'
                      ELSE NULL
                    END
                    """);
        }
    }

    private boolean columnExists(Connection connection, String table, String column) throws Exception {
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet resultSet = metaData.getColumns(null, null, table, column)) {
            if (resultSet.next()) {
                return true;
            }
        }
        try (ResultSet resultSet = metaData.getColumns(null, null, table.toUpperCase(), column.toUpperCase())) {
            return resultSet.next();
        }
    }

    private int columnSize(Connection connection, String table, String column) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet resultSet = metaData.getColumns(null, null, table, column)) {
            if (resultSet.next()) return resultSet.getInt("COLUMN_SIZE");
        }
        try (ResultSet resultSet = metaData.getColumns(null, null, table.toUpperCase(), column.toUpperCase())) {
            return resultSet.next() ? resultSet.getInt("COLUMN_SIZE") : 0;
        }
    }

    private void addColumnIfMissing(Connection connection, Statement statement, String table, String column,
            String definition) throws Exception {
        if (!columnExists(connection, table, column)) {
            statement.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        }
    }

    private boolean indexExists(Connection connection, String table, String indexName) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet resultSet = metaData.getIndexInfo(null, null, table, false, false)) {
            while (resultSet.next()) {
                if (indexName.equalsIgnoreCase(resultSet.getString("INDEX_NAME"))) {
                    return true;
                }
            }
        }
        try (ResultSet resultSet = metaData.getIndexInfo(null, null, table.toUpperCase(), false, false)) {
            while (resultSet.next()) {
                if (indexName.equalsIgnoreCase(resultSet.getString("INDEX_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean foreignKeyExists(Connection connection, String table, String foreignKeyName) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet resultSet = metaData.getImportedKeys(null, null, table)) {
            while (resultSet.next()) {
                if (foreignKeyName.equalsIgnoreCase(resultSet.getString("FK_NAME"))) {
                    return true;
                }
            }
        }
        try (ResultSet resultSet = metaData.getImportedKeys(null, null, table.toUpperCase())) {
            while (resultSet.next()) {
                if (foreignKeyName.equalsIgnoreCase(resultSet.getString("FK_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean foreignKeyUsesDeleteRule(
            Connection connection, String table, String foreignKeyName, int expectedRule) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        for (String candidate : new String[] { table, table.toUpperCase() }) {
            try (ResultSet resultSet = metaData.getImportedKeys(null, null, candidate)) {
                while (resultSet.next()) {
                    if (foreignKeyName.equalsIgnoreCase(resultSet.getString("FK_NAME"))) {
                        return resultSet.getShort("DELETE_RULE") == expectedRule;
                    }
                }
            }
        }
        return false;
    }
}
