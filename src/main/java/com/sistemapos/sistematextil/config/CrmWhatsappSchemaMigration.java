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
                      ai_attention_mode VARCHAR(20) NOT NULL DEFAULT 'AUTOMATICA',
                      ai_attention_mode_explicit BOOLEAN NOT NULL DEFAULT FALSE,
                      id_cliente INT NULL,
                      assigned_user_id INT NULL,
                      assigned_at DATETIME NULL,
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
                      message_type VARCHAR(20) NOT NULL DEFAULT 'TEXT',
                      body TEXT NULL,
                      whatsapp_message_id VARCHAR(120) NULL,
                      message_status VARCHAR(20) NOT NULL DEFAULT 'sent',
                      media_mime_type VARCHAR(120) NULL,
                      media_file_name VARCHAR(255) NULL,
                      media_storage_path VARCHAR(500) NULL,
                      message_key_json TEXT NULL,
                      baileys_message_json MEDIUMTEXT NULL,
                      reply_to_message_id BIGINT NULL,
                      deleted_at DATETIME NULL,
                      created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      PRIMARY KEY (id_message),
                      UNIQUE KEY uk_crm_whatsapp_message_wamid (whatsapp_message_id),
                      KEY idx_crm_whatsapp_message_conversation_created (id_conversation, created_at),
                      CONSTRAINT fk_crm_whatsapp_message_conversation
                        FOREIGN KEY (id_conversation) REFERENCES crm_whatsapp_conversation (id_conversation)
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
                    CREATE TABLE IF NOT EXISTS crm_whatsapp_connection (
                      id_connection BIGINT NOT NULL AUTO_INCREMENT,
                      client_id VARCHAR(80) NOT NULL,
                      id_empresa INT NOT NULL,
                      id_sucursal INT NOT NULL,
                      connected_number VARCHAR(100) NULL,
                      approved_number VARCHAR(100) NULL,
                      connection_status VARCHAR(30) NOT NULL DEFAULT 'UNKNOWN',
                      phone_changed_at DATETIME NULL,
                      disconnected_at DATETIME NULL,
                      created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      PRIMARY KEY (id_connection),
                      UNIQUE KEY uk_crm_whatsapp_connection_client (client_id),
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
            addColumnIfMissing(connection, statement, "crm_whatsapp_connection", "connection_status",
                    "VARCHAR(30) NOT NULL DEFAULT 'UNKNOWN'");
            addColumnIfMissing(connection, statement, "crm_whatsapp_connection", "phone_changed_at",
                    "DATETIME NULL");
            addColumnIfMissing(connection, statement, "crm_whatsapp_connection", "disconnected_at",
                    "DATETIME NULL");
            statement.executeUpdate("""
                    UPDATE crm_whatsapp_connection
                    SET approved_number = connected_number
                    WHERE approved_number IS NULL AND connected_number IS NOT NULL
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS crm_whatsapp_ai_config (
                      id_ai_config BIGINT NOT NULL AUTO_INCREMENT,
                      id_connection BIGINT NOT NULL,
                      modo VARCHAR(20) NOT NULL DEFAULT 'DESACTIVADA',
                      zona_horaria VARCHAR(60) NOT NULL DEFAULT 'America/Lima',
                      dias_atencion VARCHAR(100) NOT NULL DEFAULT 'LUNES,MARTES,MIERCOLES,JUEVES,VIERNES,SABADO',
                      hora_inicio TIME NOT NULL DEFAULT '09:00:00',
                      hora_fin TIME NOT NULL DEFAULT '19:00:00',
                      espera_respuesta_segundos INT NOT NULL DEFAULT 5,
                      tono VARCHAR(20) NOT NULL DEFAULT 'CERCANO',
                      instrucciones_personalizadas VARCHAR(1000) NULL,
                      intenciones_permitidas VARCHAR(500) NOT NULL DEFAULT 'SALUDO,PRODUCTOS,PRECIO,STOCK,COLORES_TALLAS,OFERTAS,UBICACION_HORARIOS,METODOS_PAGO,INTENCION_COMPRA,MODIFICAR_CARRITO,CONFIRMAR_PEDIDO,CANCELAR_PEDIDO',
                      max_respuestas_automaticas INT NOT NULL DEFAULT 5,
                      confianza_minima INT NOT NULL DEFAULT 75,
                      transferir_baja_confianza BOOLEAN NOT NULL DEFAULT TRUE,
                      transferir_solicitud_humana BOOLEAN NOT NULL DEFAULT TRUE,
                      transferir_asunto_sensible BOOLEAN NOT NULL DEFAULT TRUE,
                      created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      PRIMARY KEY (id_ai_config),
                      UNIQUE KEY uk_crm_whatsapp_ai_config_connection (id_connection),
                      CONSTRAINT fk_crm_whatsapp_ai_config_connection
                        FOREIGN KEY (id_connection) REFERENCES crm_whatsapp_connection (id_connection)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS crm_whatsapp_ai_job (
                      id_ai_job BIGINT NOT NULL AUTO_INCREMENT,
                      id_message BIGINT NOT NULL,
                      id_conversation BIGINT NOT NULL,
                      status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                      trigger_type VARCHAR(20) NOT NULL,
                      attempts INT NOT NULL DEFAULT 0,
                      max_attempts INT NOT NULL DEFAULT 3,
                      available_at DATETIME NOT NULL,
                      locked_at DATETIME NULL,
                      processed_at DATETIME NULL,
                      last_error VARCHAR(1000) NULL,
                      created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      PRIMARY KEY (id_ai_job),
                      UNIQUE KEY uk_crm_whatsapp_ai_job_message (id_message),
                      KEY idx_crm_whatsapp_ai_job_ready (status, available_at),
                      KEY idx_crm_whatsapp_ai_job_conversation (id_conversation, status),
                      CONSTRAINT fk_crm_whatsapp_ai_job_message
                        FOREIGN KEY (id_message) REFERENCES crm_whatsapp_message (id_message) ON DELETE CASCADE,
                      CONSTRAINT fk_crm_whatsapp_ai_job_conversation
                        FOREIGN KEY (id_conversation) REFERENCES crm_whatsapp_conversation (id_conversation) ON DELETE CASCADE
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS crm_whatsapp_ai_run (
                      id_ai_run BIGINT NOT NULL AUTO_INCREMENT,
                      id_ai_job BIGINT NOT NULL,
                      id_message BIGINT NOT NULL,
                      id_conversation BIGINT NOT NULL,
                      attempt_number INT NOT NULL,
                      outcome VARCHAR(30) NOT NULL,
                      intent VARCHAR(50) NULL,
                      confidence INT NULL,
                      requires_human BOOLEAN NOT NULL DEFAULT FALSE,
                      reason VARCHAR(500) NULL,
                      tool_trace_json TEXT NULL,
                      evidence_json TEXT NULL,
                      draft_response TEXT NULL,
                      suggested_media_json TEXT NULL,
                      provider VARCHAR(30) NOT NULL DEFAULT 'GEMINI',
                      model VARCHAR(100) NULL,
                      prompt_version VARCHAR(100) NOT NULL DEFAULT 'v1',
                      input_tokens INT NULL,
                      output_tokens INT NULL,
                      total_tokens INT NULL,
                      latency_ms BIGINT NULL,
                      error_detail VARCHAR(1000) NULL,
                      created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      PRIMARY KEY (id_ai_run),
                      KEY idx_crm_whatsapp_ai_run_job (id_ai_job, created_at),
                      KEY idx_crm_whatsapp_ai_run_conversation (id_conversation, created_at),
                      CONSTRAINT fk_crm_whatsapp_ai_run_job
                        FOREIGN KEY (id_ai_job) REFERENCES crm_whatsapp_ai_job (id_ai_job) ON DELETE CASCADE,
                      CONSTRAINT fk_crm_whatsapp_ai_run_message
                        FOREIGN KEY (id_message) REFERENCES crm_whatsapp_message (id_message) ON DELETE CASCADE,
                      CONSTRAINT fk_crm_whatsapp_ai_run_conversation
                        FOREIGN KEY (id_conversation) REFERENCES crm_whatsapp_conversation (id_conversation) ON DELETE CASCADE
                    )
                    """);
            if (columnSize(connection, "crm_whatsapp_ai_run", "prompt_version") < 100) {
                statement.execute("""
                        ALTER TABLE crm_whatsapp_ai_run
                        MODIFY COLUMN prompt_version VARCHAR(100) NOT NULL DEFAULT 'v1'
                        """);
            }
            if (!columnExists(connection, "crm_whatsapp_ai_run", "suggested_media_json")) {
                statement.execute("""
                        ALTER TABLE crm_whatsapp_ai_run
                        ADD COLUMN suggested_media_json TEXT NULL AFTER draft_response
                        """);
            }
            if (!columnExists(connection, "crm_whatsapp_ai_run", "evidence_json")) {
                statement.execute("""
                        ALTER TABLE crm_whatsapp_ai_run
                        ADD COLUMN evidence_json TEXT NULL AFTER tool_trace_json
                        """);
            }
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS crm_whatsapp_ai_feedback (
                      id_ai_feedback BIGINT NOT NULL AUTO_INCREMENT,
                      id_ai_run BIGINT NOT NULL,
                      id_conversation BIGINT NOT NULL,
                      id_reviewer INT NOT NULL,
                      id_outgoing_message BIGINT NULL,
                      decision VARCHAR(20) NOT NULL,
                      original_text TEXT NOT NULL,
                      final_text TEXT NULL,
                      similarity_percentage DECIMAL(5,2) NULL,
                      changed_characters INT NULL,
                      discard_reason VARCHAR(300) NULL,
                      send_status VARCHAR(20) NOT NULL DEFAULT 'NOT_SENT',
                      reviewed_at DATETIME NOT NULL,
                      created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      PRIMARY KEY (id_ai_feedback),
                      UNIQUE KEY uk_crm_whatsapp_ai_feedback_run (id_ai_run),
                      UNIQUE KEY uk_crm_whatsapp_ai_feedback_message (id_outgoing_message),
                      KEY idx_crm_whatsapp_ai_feedback_conversation (id_conversation, reviewed_at),
                      CONSTRAINT fk_crm_whatsapp_ai_feedback_run
                        FOREIGN KEY (id_ai_run) REFERENCES crm_whatsapp_ai_run (id_ai_run) ON DELETE CASCADE,
                      CONSTRAINT fk_crm_whatsapp_ai_feedback_conversation
                        FOREIGN KEY (id_conversation) REFERENCES crm_whatsapp_conversation (id_conversation) ON DELETE CASCADE,
                      CONSTRAINT fk_crm_whatsapp_ai_feedback_reviewer
                        FOREIGN KEY (id_reviewer) REFERENCES usuario (id_usuario),
                      CONSTRAINT fk_crm_whatsapp_ai_feedback_message
                        FOREIGN KEY (id_outgoing_message) REFERENCES crm_whatsapp_message (id_message) ON DELETE SET NULL
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
                    CREATE TABLE IF NOT EXISTS crm_whatsapp_ai_memory (
                      id_ai_memory BIGINT NOT NULL AUTO_INCREMENT,
                      id_conversation BIGINT NOT NULL,
                      attention_state VARCHAR(20) NOT NULL DEFAULT 'AUTOMATICA',
                      current_intent VARCHAR(50) NULL,
                      id_product INT NULL,
                      id_variant INT NULL,
                      product_name VARCHAR(180) NULL,
                        color VARCHAR(100) NULL,
                        size VARCHAR(60) NULL,
                        quantity INT NULL,
                        pending_question VARCHAR(30) NOT NULL DEFAULT 'NONE',
                        last_pending_reply_message_id BIGINT NULL,
                        last_pending_reply_intent VARCHAR(50) NULL,
                        last_pending_reply_response VARCHAR(2000) NULL,
                        last_incoming_message_id BIGINT NULL,
                      last_ai_message_id BIGINT NULL,
                      greeting_sent_at DATETIME NULL,
                      consecutive_auto_responses INT NOT NULL DEFAULT 0,
                      expires_at DATETIME NOT NULL,
                      created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      PRIMARY KEY (id_ai_memory),
                      UNIQUE KEY uk_crm_whatsapp_ai_memory_conversation (id_conversation),
                      CONSTRAINT fk_crm_whatsapp_ai_memory_conversation
                        FOREIGN KEY (id_conversation) REFERENCES crm_whatsapp_conversation (id_conversation) ON DELETE CASCADE
                      )
                      """);
              addColumnIfMissing(connection, statement, "crm_whatsapp_ai_memory", "pending_question",
                      "VARCHAR(30) NOT NULL DEFAULT 'NONE'");
              addColumnIfMissing(connection, statement, "crm_whatsapp_ai_memory", "last_pending_reply_message_id",
                      "BIGINT NULL");
              addColumnIfMissing(connection, statement, "crm_whatsapp_ai_memory", "last_pending_reply_intent",
                      "VARCHAR(50) NULL");
              addColumnIfMissing(connection, statement, "crm_whatsapp_ai_memory", "last_pending_reply_response",
                      "VARCHAR(2000) NULL");
              addColumnIfMissing(connection, statement, "crm_whatsapp_ai_memory", "greeting_sent_at",
                      "DATETIME NULL");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS crm_whatsapp_ai_memory_item (
                      id_ai_memory_item BIGINT NOT NULL AUTO_INCREMENT,
                      id_ai_memory BIGINT NOT NULL,
                      id_product INT NULL,
                      id_variant INT NULL,
                      product_name VARCHAR(180) NOT NULL,
                      color VARCHAR(100) NULL,
                      size VARCHAR(60) NULL,
                      quantity INT NOT NULL DEFAULT 1,
                      created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      PRIMARY KEY (id_ai_memory_item),
                      KEY idx_crm_whatsapp_ai_memory_item_memory (id_ai_memory),
                      CONSTRAINT fk_crm_whatsapp_ai_memory_item_memory
                        FOREIGN KEY (id_ai_memory) REFERENCES crm_whatsapp_ai_memory (id_ai_memory) ON DELETE CASCADE
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS crm_whatsapp_ai_delivery (
                      id_ai_delivery BIGINT NOT NULL AUTO_INCREMENT,
                      id_ai_run BIGINT NOT NULL,
                      id_conversation BIGINT NOT NULL,
                      id_outgoing_message BIGINT NULL,
                      source_message_id BIGINT NOT NULL,
                      media_reference VARCHAR(1000) NULL,
                      media_mime_type VARCHAR(120) NULL,
                      media_file_name VARCHAR(255) NULL,
                      media_caption VARCHAR(1000) NULL,
                      delivery_type VARCHAR(30) NOT NULL DEFAULT 'AUTOMATIC_RESPONSE',
                      status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                      attempts INT NOT NULL DEFAULT 0,
                      available_at DATETIME NOT NULL,
                      locked_at DATETIME NULL,
                      sent_at DATETIME NULL,
                      failure_reason VARCHAR(1000) NULL,
                      created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      PRIMARY KEY (id_ai_delivery),
                      UNIQUE KEY uk_crm_whatsapp_ai_delivery_run (id_ai_run),
                      KEY idx_crm_whatsapp_ai_delivery_ready (status, available_at),
                      KEY idx_crm_whatsapp_ai_delivery_conversation (id_conversation, status),
                      CONSTRAINT fk_crm_whatsapp_ai_delivery_run
                        FOREIGN KEY (id_ai_run) REFERENCES crm_whatsapp_ai_run (id_ai_run) ON DELETE CASCADE,
                      CONSTRAINT fk_crm_whatsapp_ai_delivery_conversation
                        FOREIGN KEY (id_conversation) REFERENCES crm_whatsapp_conversation (id_conversation) ON DELETE CASCADE,
                      CONSTRAINT fk_crm_whatsapp_ai_delivery_message
                        FOREIGN KEY (id_outgoing_message) REFERENCES crm_whatsapp_message (id_message) ON DELETE SET NULL
                    )
                    """);
            if (!columnExists(connection, "crm_whatsapp_ai_delivery", "delivery_type")) {
                statement.execute("""
                        ALTER TABLE crm_whatsapp_ai_delivery
                        ADD COLUMN delivery_type VARCHAR(30) NOT NULL DEFAULT 'AUTOMATIC_RESPONSE' AFTER source_message_id
                        """);
            }
            addColumnIfMissing(connection, statement, "crm_whatsapp_ai_delivery", "media_reference", "VARCHAR(1000) NULL");
            addColumnIfMissing(connection, statement, "crm_whatsapp_ai_delivery", "media_mime_type", "VARCHAR(120) NULL");
            addColumnIfMissing(connection, statement, "crm_whatsapp_ai_delivery", "media_file_name", "VARCHAR(255) NULL");
            addColumnIfMissing(connection, statement, "crm_whatsapp_ai_delivery", "media_caption", "VARCHAR(1000) NULL");
            addColumnIfMissing(connection, statement, "crm_whatsapp_ai_delivery", "text_body", "VARCHAR(2000) NULL");
            addColumnIfMissing(connection, statement, "crm_whatsapp_ai_delivery", "idempotency_key", "VARCHAR(160) NULL");
            statement.execute("ALTER TABLE crm_whatsapp_ai_delivery MODIFY id_ai_run BIGINT NULL");
            statement.execute("ALTER TABLE crm_whatsapp_ai_delivery MODIFY source_message_id BIGINT NULL");
            if (!indexExists(connection, "crm_whatsapp_ai_delivery", "uk_crm_whatsapp_ai_delivery_idempotency")) {
                statement.execute("""
                        ALTER TABLE crm_whatsapp_ai_delivery
                        ADD UNIQUE KEY uk_crm_whatsapp_ai_delivery_idempotency (idempotency_key)
                        """);
            }
            statement.executeUpdate("""
                    UPDATE crm_whatsapp_ai_delivery
                    SET status = 'CANCELLED',
                        failure_reason = 'Aviso comercial manual retirado'
                    WHERE status = 'AWAITING_HUMAN'
                      AND delivery_type IN ('PAYMENT_REJECTED', 'SALE_COMPLETED')
                    """);
            addColumnIfMissing(connection, statement, "crm_whatsapp_ai_config",
                    "guia_tallas_intent_initialized", "BOOLEAN NOT NULL DEFAULT FALSE");
            statement.executeUpdate("""
                    UPDATE crm_whatsapp_ai_config
                    SET intenciones_permitidas = CONCAT(intenciones_permitidas, ',GUIA_TALLAS')
                    WHERE guia_tallas_intent_initialized = FALSE
                      AND FIND_IN_SET('GUIA_TALLAS', intenciones_permitidas) = 0
                    """);
            statement.executeUpdate("""
                    UPDATE crm_whatsapp_ai_config
                    SET guia_tallas_intent_initialized = TRUE
                    WHERE guia_tallas_intent_initialized = FALSE
                    """);
            addColumnIfMissing(connection, statement, "crm_whatsapp_ai_config",
                    "ecommerce_link_intent_initialized", "BOOLEAN NOT NULL DEFAULT FALSE");
            statement.executeUpdate("""
                    UPDATE crm_whatsapp_ai_config
                    SET intenciones_permitidas = CONCAT(intenciones_permitidas, ',ENLACE_ECOMMERCE')
                    WHERE ecommerce_link_intent_initialized = FALSE
                      AND FIND_IN_SET('ENLACE_ECOMMERCE', intenciones_permitidas) = 0
                    """);
            statement.executeUpdate("""
                    UPDATE crm_whatsapp_ai_config
                    SET ecommerce_link_intent_initialized = TRUE
                    WHERE ecommerce_link_intent_initialized = FALSE
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
            if (!columnExists(connection, "crm_whatsapp_conversation", "ai_attention_mode")) {
                statement.execute("""
                        ALTER TABLE crm_whatsapp_conversation
                        ADD COLUMN ai_attention_mode VARCHAR(20) NOT NULL DEFAULT 'AUTOMATICA'
                        """);
                statement.execute("""
                        UPDATE crm_whatsapp_conversation
                        SET ai_attention_mode = 'HUMANA'
                        WHERE assigned_user_id IS NOT NULL
                        """);
            }
            if (!columnExists(connection, "crm_whatsapp_conversation", "ai_attention_mode_explicit")) {
                statement.execute("""
                        ALTER TABLE crm_whatsapp_conversation
                        ADD COLUMN ai_attention_mode_explicit BOOLEAN NOT NULL DEFAULT FALSE
                        """);
                statement.execute("""
                        UPDATE crm_whatsapp_conversation
                        SET ai_attention_mode_explicit = TRUE
                        WHERE assigned_user_id IS NOT NULL
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
            if (!columnExists(connection, "crm_whatsapp_payment_request", "id_ai_sale_draft")) {
                statement.execute("""
                        ALTER TABLE crm_whatsapp_payment_request
                        ADD COLUMN id_ai_sale_draft BIGINT NULL,
                        ADD COLUMN ai_sale_draft_version INT NULL,
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
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS crm_whatsapp_ai_sale_draft (
                      id_ai_sale_draft BIGINT NOT NULL AUTO_INCREMENT,
                      id_conversation BIGINT NOT NULL,
                      id_connection BIGINT NOT NULL,
                      id_sucursal INT NOT NULL,
                      id_metodo_pago INT NULL,
                      id_venta INT NULL,
                      status VARCHAR(30) NOT NULL DEFAULT 'BUILDING',
                      version INT NOT NULL DEFAULT 1,
                      confirmed_version INT NULL,
                      customer_confirmed_at DATETIME NULL,
                      confirmation_message_id BIGINT NULL,
                      subtotal DECIMAL(12,2) NOT NULL DEFAULT 0,
                      total DECIMAL(12,2) NOT NULL DEFAULT 0,
                      expires_at DATETIME NOT NULL,
                      created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      PRIMARY KEY (id_ai_sale_draft),
                      KEY idx_crm_ai_sale_draft_conversation_status (id_conversation, status, created_at),
                      KEY idx_crm_ai_sale_draft_expires (status, expires_at),
                      CONSTRAINT fk_crm_ai_sale_draft_conversation FOREIGN KEY (id_conversation) REFERENCES crm_whatsapp_conversation (id_conversation) ON DELETE CASCADE,
                      CONSTRAINT fk_crm_ai_sale_draft_connection FOREIGN KEY (id_connection) REFERENCES crm_whatsapp_connection (id_connection),
                      CONSTRAINT fk_crm_ai_sale_draft_sucursal FOREIGN KEY (id_sucursal) REFERENCES sucursal (id_sucursal),
                      CONSTRAINT fk_crm_ai_sale_draft_metodo FOREIGN KEY (id_metodo_pago) REFERENCES metodo_pago_config (id_metodo_pago),
                      CONSTRAINT fk_crm_ai_sale_draft_venta FOREIGN KEY (id_venta) REFERENCES venta (id_venta)
                    )
                    """);
            if (foreignKeyExists(connection, "crm_whatsapp_payment_request", "fk_crm_payment_request_ai_draft")
                    && !foreignKeyUsesDeleteRule(connection, "crm_whatsapp_payment_request",
                            "fk_crm_payment_request_ai_draft", DatabaseMetaData.importedKeySetNull)) {
                statement.execute("""
                        ALTER TABLE crm_whatsapp_payment_request
                        DROP FOREIGN KEY fk_crm_payment_request_ai_draft
                        """);
            }
            if (!foreignKeyExists(connection, "crm_whatsapp_payment_request", "fk_crm_payment_request_ai_draft")) {
                statement.execute("""
                        ALTER TABLE crm_whatsapp_payment_request
                        ADD CONSTRAINT fk_crm_payment_request_ai_draft
                          FOREIGN KEY (id_ai_sale_draft) REFERENCES crm_whatsapp_ai_sale_draft (id_ai_sale_draft) ON DELETE SET NULL
                        """);
            }
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS crm_whatsapp_ai_sale_draft_item (
                      id_ai_sale_draft_item BIGINT NOT NULL AUTO_INCREMENT,
                      id_ai_sale_draft BIGINT NOT NULL,
                      id_product INT NOT NULL,
                      id_variant INT NOT NULL,
                      product_name VARCHAR(180) NOT NULL,
                      sku VARCHAR(100) NULL,
                      color VARCHAR(100) NULL,
                      size VARCHAR(60) NULL,
                      quantity INT NOT NULL,
                      unit_price DECIMAL(12,2) NOT NULL,
                      stock_snapshot INT NOT NULL,
                      image_url VARCHAR(500) NULL,
                      preventa TINYINT(1) NOT NULL DEFAULT 0,
                      fecha_envio_preventa DATE NULL,
                      created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      PRIMARY KEY (id_ai_sale_draft_item),
                      UNIQUE KEY uk_crm_ai_sale_draft_variant (id_ai_sale_draft, id_variant),
                      CONSTRAINT fk_crm_ai_sale_draft_item_draft FOREIGN KEY (id_ai_sale_draft) REFERENCES crm_whatsapp_ai_sale_draft (id_ai_sale_draft) ON DELETE CASCADE
                    )
                    """);
            addColumnIfMissing(connection, statement, "crm_whatsapp_ai_sale_draft", "promotion_discount",
                    "DECIMAL(12,2) NOT NULL DEFAULT 0");
            addColumnIfMissing(connection, statement, "crm_whatsapp_ai_sale_draft", "pending_customer_name",
                    "VARCHAR(150) NULL");
            addColumnIfMissing(connection, statement, "crm_whatsapp_ai_sale_draft", "pending_customer_phone",
                    "VARCHAR(9) NULL");
            addColumnIfMissing(connection, statement, "crm_whatsapp_ai_sale_draft", "customer_name_current",
                    "VARCHAR(150) NULL");
            addColumnIfMissing(connection, statement, "crm_whatsapp_ai_sale_draft", "customer_name_suggested",
                    "VARCHAR(150) NULL");
            addColumnIfMissing(connection, statement, "crm_whatsapp_ai_sale_draft", "customer_name_suggestion_status",
                    "VARCHAR(20) NULL");
            addColumnIfMissing(connection, statement, "crm_whatsapp_ai_sale_draft", "pending_promotion_id",
                    "INT NULL");
            addColumnIfMissing(connection, statement, "crm_whatsapp_ai_sale_draft", "last_ecommerce_message_id",
                    "BIGINT NULL");
            addColumnIfMissing(connection, statement, "crm_whatsapp_ai_sale_draft_item", "regular_unit_price",
                    "DECIMAL(12,2) NULL");
            addColumnIfMissing(connection, statement, "crm_whatsapp_ai_sale_draft_item", "preventa",
                    "TINYINT(1) NOT NULL DEFAULT 0");
            addColumnIfMissing(connection, statement, "crm_whatsapp_ai_sale_draft_item", "fecha_envio_preventa",
                    "DATE NULL");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS crm_whatsapp_ai_sale_draft_promotion (
                      id_ai_sale_draft_promotion BIGINT NOT NULL AUTO_INCREMENT,
                      id_ai_sale_draft BIGINT NOT NULL,
                      id_promocion_combo INT NOT NULL,
                      name VARCHAR(150) NOT NULL,
                      rule_description VARCHAR(300) NOT NULL,
                      regular_price DECIMAL(12,2) NOT NULL,
                      combo_price DECIMAL(12,2) NOT NULL,
                      discount DECIMAL(12,2) NOT NULL,
                      created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      PRIMARY KEY (id_ai_sale_draft_promotion),
                      KEY idx_crm_ai_sale_draft_promotion_draft (id_ai_sale_draft),
                      CONSTRAINT fk_crm_ai_sale_draft_promotion_draft
                        FOREIGN KEY (id_ai_sale_draft) REFERENCES crm_whatsapp_ai_sale_draft (id_ai_sale_draft) ON DELETE CASCADE
                    )
                    """);
            statement.execute("""
                    UPDATE crm_whatsapp_ai_config
                    SET intenciones_permitidas = CONCAT(intenciones_permitidas, ',INTENCION_COMPRA,MODIFICAR_CARRITO,CONFIRMAR_PEDIDO,CANCELAR_PEDIDO')
                    WHERE FIND_IN_SET('INTENCION_COMPRA', intenciones_permitidas) = 0
                    """);
            statement.execute("""
                    UPDATE crm_whatsapp_ai_config
                    SET intenciones_permitidas = CONCAT(intenciones_permitidas, ',PROMOCIONES')
                    WHERE FIND_IN_SET('PROMOCIONES', intenciones_permitidas) = 0
                    """);
            if (!columnExists(connection, "metodo_pago_cuenta", "titular")) {
                statement.execute("ALTER TABLE metodo_pago_cuenta ADD COLUMN titular VARCHAR(150) NULL");
            }
            if (!columnExists(connection, "metodo_pago_cuenta", "aliases_validacion")) {
                statement.execute("ALTER TABLE metodo_pago_cuenta ADD COLUMN aliases_validacion VARCHAR(500) NULL");
            }
            if (!columnExists(connection, "metodo_pago_cuenta", "activo")) {
                statement.execute("ALTER TABLE metodo_pago_cuenta ADD COLUMN activo TINYINT(1) NOT NULL DEFAULT 1");
            }
            addColumnIfMissing(connection, statement, "crm_whatsapp_ai_config", "daily_token_limit",
                    "BIGINT NULL");
            addColumnIfMissing(connection, statement, "crm_whatsapp_ai_config", "monthly_token_limit",
                    "BIGINT NULL");
            addColumnIfMissing(connection, statement, "crm_whatsapp_ai_config", "monthly_budget_usd",
                    "DECIMAL(12,4) NULL");
            addColumnIfMissing(connection, statement, "crm_whatsapp_ai_config", "input_cost_per_million_usd",
                    "DECIMAL(12,6) NULL");
            addColumnIfMissing(connection, statement, "crm_whatsapp_ai_config", "output_cost_per_million_usd",
                    "DECIMAL(12,6) NULL");
            addColumnIfMissing(connection, statement, "crm_whatsapp_ai_config", "automatic_rollout_percent",
                    "INT NOT NULL DEFAULT 0");
            addColumnIfMissing(connection, statement, "crm_whatsapp_ai_config", "operational_status",
                    "VARCHAR(30) NOT NULL DEFAULT 'ACTIVE'");
            addColumnIfMissing(connection, statement, "crm_whatsapp_ai_config", "operational_reason",
                    "VARCHAR(300) NULL");
            addColumnIfMissing(connection, statement, "crm_whatsapp_ai_config", "operational_changed_at",
                    "DATETIME NULL");
            addColumnIfMissing(connection, statement, "crm_whatsapp_ai_config", "operational_changed_by",
                    "INT NULL");
            addColumnIfMissing(connection, statement, "crm_whatsapp_ai_run", "input_cost_per_million_usd",
                    "DECIMAL(12,6) NULL");
            addColumnIfMissing(connection, statement, "crm_whatsapp_ai_run", "output_cost_per_million_usd",
                    "DECIMAL(12,6) NULL");
            addColumnIfMissing(connection, statement, "crm_whatsapp_ai_run", "estimated_cost_usd",
                    "DECIMAL(14,8) NULL");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS crm_whatsapp_ai_audit_event (
                      id_audit_event BIGINT NOT NULL AUTO_INCREMENT,
                      id_connection BIGINT NOT NULL,
                      id_conversation BIGINT NULL,
                      id_ai_run BIGINT NULL,
                      id_actor INT NULL,
                      event_type VARCHAR(50) NOT NULL,
                      severity VARCHAR(20) NOT NULL,
                      reason VARCHAR(500) NOT NULL,
                      metadata_json TEXT NULL,
                      created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      PRIMARY KEY (id_audit_event),
                      KEY idx_crm_ai_audit_connection_date (id_connection, created_at),
                      KEY idx_crm_ai_audit_type_date (event_type, created_at),
                      CONSTRAINT fk_crm_ai_audit_connection FOREIGN KEY (id_connection) REFERENCES crm_whatsapp_connection (id_connection),
                      CONSTRAINT fk_crm_ai_audit_conversation FOREIGN KEY (id_conversation) REFERENCES crm_whatsapp_conversation (id_conversation) ON DELETE SET NULL,
                      CONSTRAINT fk_crm_ai_audit_run FOREIGN KEY (id_ai_run) REFERENCES crm_whatsapp_ai_run (id_ai_run) ON DELETE SET NULL,
                      CONSTRAINT fk_crm_ai_audit_actor FOREIGN KEY (id_actor) REFERENCES usuario (id_usuario)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS crm_whatsapp_ai_product_query (
                      id_product_query BIGINT NOT NULL AUTO_INCREMENT,
                      id_ai_run BIGINT NOT NULL,
                      id_conversation BIGINT NOT NULL,
                      id_producto INT NOT NULL,
                      created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      PRIMARY KEY (id_product_query),
                      UNIQUE KEY uk_crm_ai_product_query_run_product (id_ai_run, id_producto),
                      KEY idx_crm_ai_product_query_product_date (id_producto, created_at),
                      CONSTRAINT fk_crm_ai_product_query_run FOREIGN KEY (id_ai_run) REFERENCES crm_whatsapp_ai_run (id_ai_run) ON DELETE CASCADE,
                      CONSTRAINT fk_crm_ai_product_query_conversation FOREIGN KEY (id_conversation) REFERENCES crm_whatsapp_conversation (id_conversation) ON DELETE CASCADE,
                      CONSTRAINT fk_crm_ai_product_query_product FOREIGN KEY (id_producto) REFERENCES producto (producto_id)
                    )
                    """);
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
                    INSERT IGNORE INTO crm_whatsapp_ai_knowledge_article
                      (id_connection, title, category, content, keywords, status, active_version, pending_version, source_key)
                    SELECT c.id_connection,
                           'Ubicacion de la tienda',
                           'UBICACION',
                           CONCAT('Direccion: ', COALESCE(NULLIF(TRIM(s.direccion), ''), 'por confirmar'),
                                  '. Ciudad: ', COALESCE(NULLIF(TRIM(s.ciudad), ''), 'por confirmar'),
                                  '. Telefono: ', COALESCE(NULLIF(TRIM(s.telefono), ''), 'por confirmar'), '.'),
                           'direccion, ubicacion, tienda, referencia, telefono',
                           'INDEXANDO', 0, 1, 'MIGRACION_UBICACION'
                    FROM crm_whatsapp_connection c
                    JOIN sucursal s ON s.id_sucursal = c.id_sucursal
                    WHERE COALESCE(TRIM(s.direccion), '') <> ''
                       OR COALESCE(TRIM(s.ciudad), '') <> ''
                       OR COALESCE(TRIM(s.telefono), '') <> ''
                    """);
            statement.execute("""
                    INSERT IGNORE INTO crm_whatsapp_ai_knowledge_article
                      (id_connection, title, category, content, keywords, status, active_version, pending_version, source_key)
                    SELECT c.id_connection,
                           'Horarios comerciales',
                           'HORARIOS',
                           CONCAT('Horarios de atencion: ', GROUP_CONCAT(
                             CONCAT(h.day_of_week, ': ', IF(h.closed, 'cerrado',
                               CONCAT(TIME_FORMAT(h.opens_at, '%H:%i'), ' a ', TIME_FORMAT(h.closes_at, '%H:%i'))))
                             ORDER BY FIELD(h.day_of_week, 'LUNES','MARTES','MIERCOLES','JUEVES','VIERNES','SABADO','DOMINGO')
                             SEPARATOR '. '), '.'),
                           'horario, atencion, apertura, cierre, abierto',
                           'INDEXANDO', 0, 1, 'MIGRACION_HORARIOS'
                    FROM crm_whatsapp_connection c
                    JOIN crm_whatsapp_business_hours h ON h.id_connection = c.id_connection
                    GROUP BY c.id_connection
                    """);
            for (String intent : new String[] {
                    "ENVIOS", "TIENDAS", "POLITICAS", "CUIDADOS", "FAQ", "INSTITUCIONAL", "INFORMACION_NEGOCIO"
            }) {
                statement.execute("UPDATE crm_whatsapp_ai_config "
                        + "SET intenciones_permitidas = CONCAT(intenciones_permitidas, '," + intent + "') "
                        + "WHERE FIND_IN_SET('" + intent + "', intenciones_permitidas) = 0");
            }
            statement.execute("""
                    DELETE FROM crm_whatsapp_message
                    WHERE direction = 'OUTGOING'
                      AND message_status = 'failed'
                    """);
            statement.execute("""
                    UPDATE crm_whatsapp_conversation c
                    SET c.status = 'ESPERA'
                    WHERE c.status = 'ATENDIDO'
                      AND c.assigned_user_id IS NULL
                    """);
            addColumnIfMissing(connection, statement, "crm_whatsapp_conversation", "attention_queue",
                    "VARCHAR(30) NOT NULL DEFAULT 'AI_ACTIVE'");
            addColumnIfMissing(connection, statement, "crm_whatsapp_conversation", "waiting_reason",
                    "VARCHAR(30) NULL");
            addColumnIfMissing(connection, statement, "crm_whatsapp_message", "related_sale_id", "INT NULL");
            addColumnIfMissing(connection, statement, "crm_whatsapp_message", "receipt_format", "VARCHAR(10) NULL");
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
                      WHEN waiting_reason = 'PAYMENT_VERIFICATION' THEN 'PAYMENT_VERIFICATION'
                      WHEN waiting_reason = 'AI_DISABLED' THEN 'ADVISOR_REQUIRED'
                      WHEN ai_attention_mode = 'HUMANA' THEN 'ADVISOR_REQUIRED'
                      ELSE 'AI_ACTIVE'
                    END,
                    waiting_reason = CASE
                      WHEN status = 'ESPERA' AND assigned_user_id IS NULL
                           AND waiting_reason = 'PAYMENT_VERIFICATION' THEN 'PAYMENT_VERIFICATION'
                      WHEN status = 'ESPERA' AND assigned_user_id IS NULL
                           AND waiting_reason = 'AI_DISABLED' THEN 'AI_DISABLED'
                      WHEN status = 'ESPERA' AND assigned_user_id IS NULL AND ai_attention_mode = 'HUMANA'
                        THEN 'ADVISOR_REQUIRED'
                      ELSE NULL
                    END
                    """);
            statement.executeUpdate("""
                    UPDATE crm_whatsapp_conversation c
                    JOIN crm_whatsapp_ai_config cfg ON cfg.id_connection = c.id_connection
                    SET c.attention_queue = 'ADVISOR_REQUIRED',
                        c.waiting_reason = 'AI_DISABLED'
                    WHERE cfg.modo = 'DESACTIVADA'
                      AND c.status <> 'RESUELTO'
                      AND c.assigned_user_id IS NULL
                      AND c.ai_attention_mode = 'AUTOMATICA'
                      AND (c.waiting_reason IS NULL OR c.waiting_reason = 'AI_DISABLED')
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
