package com.sistemapos.sistematextil.config;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Crea o actualiza el esquema aditivo del agente nuevo en MySQL.
 * No inserta, actualiza ni elimina datos comerciales.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 100)
public class CrmWhatsappAgentSchemaMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CrmWhatsappAgentSchemaMigration.class);

    private final DataSource dataSource;
    private final boolean enabled;

    public CrmWhatsappAgentSchemaMigration(
            DataSource dataSource,
            @Value("${crm.whatsapp.agent-schema-enabled:true}") boolean enabled) {
        this.dataSource = dataSource;
        this.enabled = enabled;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!enabled) return;
        try (Connection connection = dataSource.getConnection()) {
            String jdbcUrl = connection.getMetaData().getURL();
            if (!isMysqlDatabase(jdbcUrl)) {
                log.warn("Esquema del agente omitido: la base configurada no es MySQL");
                return;
            }
            try (Statement statement = connection.createStatement()) {
                createSessionTable(statement);
                normalizeInactiveSessionSlots(statement);
                adaptSessionPaymentSelection(connection, statement);
                createCartTables(statement);
                createRuntimeTables(statement);
                createAiUsageTable(statement);
                adaptAgentToolCallTable(connection, statement);
                adaptAgentJobTable(connection, statement);
                adaptAgentOutboxTable(connection, statement);
                createShalomAgencyTables(statement);
                createFulfillmentAndSupportTables(statement);
                createFilmTables(statement);
                adaptFilmTables(connection, statement);
                adaptShalomFulfillment(connection, statement);
                adaptPaymentTables(connection, statement);
                adaptPaymentReviewConversationReference(connection, statement);
                migrateActiveSessionsToPaymentFirstFlow(statement);
            }
            log.info("Esquema del agente de WhatsApp verificado");
        }
    }

    private void createFilmTables(Statement statement) throws SQLException {
        statement.execute("""
                CREATE TABLE IF NOT EXISTS crm_whatsapp_film_addon (
                  id_film_addon BIGINT NOT NULL AUTO_INCREMENT,
                  id_agent_session BIGINT NOT NULL,
                  id_fulfillment BIGINT NOT NULL,
                  id_conversation BIGINT NOT NULL,
                  id_metodo_pago_cuenta INT NULL,
                  id_venta INT NULL,
                  idempotency_key VARCHAR(64) NOT NULL,
                  amount DECIMAL(12,2) NOT NULL DEFAULT 2.00,
                  status VARCHAR(40) NOT NULL,
                  offered_at DATETIME(6) NOT NULL,
                  payment_started_at DATETIME(6) NULL,
                  expires_at DATETIME(6) NULL,
                  paid_at DATETIME(6) NULL,
                  resolved_at DATETIME(6) NULL,
                  resolved_by INT NULL,
                  resolution_source VARCHAR(40) NULL,
                  covered_by_sale_id INT NULL,
                  final_message_sent_at DATETIME(6) NULL,
                  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                  PRIMARY KEY (id_film_addon),
                  UNIQUE KEY uk_crm_film_session (id_agent_session),
                  UNIQUE KEY uk_crm_film_fulfillment (id_fulfillment),
                  UNIQUE KEY uk_crm_film_idempotency (idempotency_key),
                  KEY idx_crm_film_status_expiry (status, expires_at),
                  KEY idx_crm_film_sale (id_venta),
                  KEY idx_crm_film_resolved_by (resolved_by),
                  KEY idx_crm_film_covered_sale (covered_by_sale_id),
                  CONSTRAINT fk_crm_film_session FOREIGN KEY (id_agent_session) REFERENCES crm_whatsapp_agent_session(id_agent_session) ON DELETE CASCADE,
                  CONSTRAINT fk_crm_film_fulfillment FOREIGN KEY (id_fulfillment) REFERENCES crm_whatsapp_fulfillment(id_fulfillment) ON DELETE CASCADE,
                  CONSTRAINT fk_crm_film_conversation FOREIGN KEY (id_conversation) REFERENCES crm_whatsapp_conversation(id_conversation) ON DELETE CASCADE,
                  CONSTRAINT fk_crm_film_account FOREIGN KEY (id_metodo_pago_cuenta) REFERENCES metodo_pago_cuenta(id_metodo_pago_cuenta) ON DELETE SET NULL,
                  CONSTRAINT fk_crm_film_sale FOREIGN KEY (id_venta) REFERENCES venta(id_venta) ON DELETE SET NULL,
                  CONSTRAINT fk_crm_film_resolved_by FOREIGN KEY (resolved_by) REFERENCES usuario(id_usuario) ON DELETE SET NULL,
                  CONSTRAINT fk_crm_film_covered_sale FOREIGN KEY (covered_by_sale_id) REFERENCES venta(id_venta) ON DELETE SET NULL
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
        statement.execute("""
                CREATE TABLE IF NOT EXISTS crm_whatsapp_film_evidence (
                  id_film_evidence BIGINT NOT NULL AUTO_INCREMENT,
                  id_film_addon BIGINT NOT NULL,
                  id_message BIGINT NOT NULL,
                  reviewed_by INT NULL,
                  storage_path VARCHAR(500) NOT NULL,
                  mime_type VARCHAR(120) NOT NULL,
                  file_name VARCHAR(255) NULL,
                  sha256 VARCHAR(64) NOT NULL,
                  detected_provider VARCHAR(40) NULL,
                  detected_amount DECIMAL(12,2) NULL,
                  detected_currency VARCHAR(3) NULL,
                  operation_code VARCHAR(120) NULL,
                  operation_at DATETIME(6) NULL,
                  recipient VARCHAR(180) NULL,
                  confidence INT NULL,
                  extracted_text TEXT NULL,
                  extraction_json TEXT NULL,
                  warnings_json TEXT NULL,
                  validation_status VARCHAR(30) NOT NULL,
                  processing_status VARCHAR(20) NOT NULL,
                  attempts INT NOT NULL DEFAULT 0,
                  available_at DATETIME(6) NOT NULL,
                  last_error VARCHAR(1000) NULL,
                  review_note VARCHAR(500) NULL,
                  reviewed_at DATETIME(6) NULL,
                  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                  PRIMARY KEY (id_film_evidence),
                  UNIQUE KEY uk_crm_film_evidence_message (id_message),
                  KEY idx_crm_film_evidence_ready (processing_status, available_at),
                  KEY idx_crm_film_evidence_operation (operation_code),
                  CONSTRAINT fk_crm_film_evidence_addon FOREIGN KEY (id_film_addon) REFERENCES crm_whatsapp_film_addon(id_film_addon) ON DELETE CASCADE,
                  CONSTRAINT fk_crm_film_evidence_message FOREIGN KEY (id_message) REFERENCES crm_whatsapp_message(id_message) ON DELETE CASCADE,
                  CONSTRAINT fk_crm_film_evidence_reviewer FOREIGN KEY (reviewed_by) REFERENCES usuario(id_usuario) ON DELETE SET NULL
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
        statement.execute("""
                CREATE TABLE IF NOT EXISTS crm_whatsapp_film_payment (
                  id_film_payment BIGINT NOT NULL AUTO_INCREMENT,
                  id_film_addon BIGINT NOT NULL,
                  id_venta INT NOT NULL,
                  id_film_evidence BIGINT NULL,
                  confirmed_by INT NULL,
                  confirmation_source VARCHAR(40) NOT NULL DEFAULT 'RECEIPT_AI',
                  amount DECIMAL(12,2) NOT NULL,
                  currency VARCHAR(3) NOT NULL,
                  provider VARCHAR(40) NOT NULL,
                  operation_code VARCHAR(120) NULL,
                  paid_at DATETIME(6) NOT NULL,
                  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                  PRIMARY KEY (id_film_payment),
                  UNIQUE KEY uk_crm_film_payment_addon (id_film_addon),
                  UNIQUE KEY uk_crm_film_payment_evidence (id_film_evidence),
                  UNIQUE KEY uk_crm_film_payment_operation (provider, operation_code),
                  KEY idx_crm_film_payment_sale (id_venta),
                  KEY idx_crm_film_payment_reviewer (confirmed_by),
                  CONSTRAINT fk_crm_film_payment_addon FOREIGN KEY (id_film_addon) REFERENCES crm_whatsapp_film_addon(id_film_addon),
                  CONSTRAINT fk_crm_film_payment_sale FOREIGN KEY (id_venta) REFERENCES venta(id_venta),
                  CONSTRAINT fk_crm_film_payment_evidence FOREIGN KEY (id_film_evidence) REFERENCES crm_whatsapp_film_evidence(id_film_evidence),
                  CONSTRAINT fk_crm_film_payment_reviewer FOREIGN KEY (confirmed_by) REFERENCES usuario(id_usuario) ON DELETE SET NULL
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
    }

    private void adaptFilmTables(Connection connection, Statement statement) throws Exception {
        addColumnIfMissing(connection, statement, "crm_whatsapp_film_addon", "resolved_by", "INT NULL");
        addColumnIfMissing(connection, statement, "crm_whatsapp_film_addon", "resolution_source", "VARCHAR(40) NULL");
        addColumnIfMissing(connection, statement, "crm_whatsapp_film_addon", "covered_by_sale_id", "INT NULL");
        addIndexIfMissing(connection, statement, "crm_whatsapp_film_addon", "idx_crm_film_resolved_by", "(resolved_by)");
        addIndexIfMissing(connection, statement, "crm_whatsapp_film_addon", "idx_crm_film_covered_sale", "(covered_by_sale_id)");
        addForeignKeyIfMissing(connection, statement, "crm_whatsapp_film_addon", "fk_crm_film_resolved_by",
                "FOREIGN KEY (resolved_by) REFERENCES usuario(id_usuario) ON DELETE SET NULL");
        addForeignKeyIfMissing(connection, statement, "crm_whatsapp_film_addon", "fk_crm_film_covered_sale",
                "FOREIGN KEY (covered_by_sale_id) REFERENCES venta(id_venta) ON DELETE SET NULL");
        addColumnIfMissing(connection, statement, "crm_whatsapp_film_payment", "confirmed_by", "INT NULL");
        addColumnIfMissing(connection, statement, "crm_whatsapp_film_payment", "confirmation_source",
                "VARCHAR(40) NOT NULL DEFAULT 'RECEIPT_AI'");
        addColumnIfMissing(connection, statement, "crm_whatsapp_film_payment", "id_payment_evidence", "BIGINT NULL");
        addColumnIfMissing(connection, statement, "crm_whatsapp_film_payment", "id_metodo_pago", "INT NULL");
        addColumnIfMissing(connection, statement, "crm_whatsapp_film_payment", "id_metodo_pago_cuenta", "INT NULL");
        statement.execute("ALTER TABLE crm_whatsapp_film_payment MODIFY COLUMN id_film_evidence BIGINT NULL");
        statement.execute("ALTER TABLE crm_whatsapp_film_payment MODIFY COLUMN operation_code VARCHAR(120) NULL");
        addIndexIfMissing(connection, statement, "crm_whatsapp_film_payment", "idx_crm_film_payment_reviewer",
                "(confirmed_by)");
        addForeignKeyIfMissing(connection, statement, "crm_whatsapp_film_payment", "fk_crm_film_payment_reviewer",
                "FOREIGN KEY (confirmed_by) REFERENCES usuario(id_usuario) ON DELETE SET NULL");
        addUniqueIndexIfMissing(connection, statement, "crm_whatsapp_film_payment",
                "uk_crm_film_payment_main_evidence", "(id_payment_evidence)");
        addForeignKeyIfMissing(connection, statement, "crm_whatsapp_film_payment", "fk_crm_film_payment_main_evidence",
                "FOREIGN KEY (id_payment_evidence) REFERENCES crm_whatsapp_payment_evidence(id_payment_evidence) ON DELETE SET NULL");
        addForeignKeyIfMissing(connection, statement, "crm_whatsapp_film_payment", "fk_crm_film_payment_method",
                "FOREIGN KEY (id_metodo_pago) REFERENCES metodo_pago_config(id_metodo_pago) ON DELETE SET NULL");
        addForeignKeyIfMissing(connection, statement, "crm_whatsapp_film_payment", "fk_crm_film_payment_account",
                "FOREIGN KEY (id_metodo_pago_cuenta) REFERENCES metodo_pago_cuenta(id_metodo_pago_cuenta) ON DELETE SET NULL");
    }

    static boolean isMysqlDatabase(String url) {
        String normalized = url == null ? "" : url.toLowerCase(Locale.ROOT);
        return normalized.startsWith("jdbc:mysql:");
    }

    private void createSessionTable(Statement statement) throws SQLException {
        statement.execute("""
                CREATE TABLE IF NOT EXISTS crm_whatsapp_agent_session (
                  id_agent_session BIGINT NOT NULL AUTO_INCREMENT,
                  id_conversation BIGINT NULL,
                  id_connection BIGINT NOT NULL,
                  id_cliente INT NULL,
                  selected_payment_method_id INT NULL,
                  selected_payment_account_id INT NULL,
                  last_customer_message_id BIGINT NULL,
                  session_key VARCHAR(64) NOT NULL,
                  state VARCHAR(50) NOT NULL DEFAULT 'IDLE',
                  resume_state VARCHAR(50) NULL,
                  preferred_fulfillment_method VARCHAR(20) NULL,
                  active_slot TINYINT(1) NULL DEFAULT 1,
                  summary TEXT NULL,
                  catalog_preview_sent_at DATETIME(6) NULL,
                  last_event VARCHAR(60) NULL,
                  lock_version BIGINT NOT NULL DEFAULT 0,
                  started_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                  last_activity_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                  state_changed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                  completed_at DATETIME(6) NULL,
                  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                  PRIMARY KEY (id_agent_session),
                  UNIQUE KEY uk_crm_agent_session_key (session_key),
                  UNIQUE KEY uk_crm_agent_session_active (id_conversation, active_slot),
                  KEY idx_crm_agent_session_state_activity (state, last_activity_at),
                  KEY idx_crm_agent_session_connection (id_connection, created_at),
                  KEY idx_crm_agent_session_cliente (id_cliente),
                  KEY idx_crm_agent_session_payment_method (selected_payment_method_id),
                  KEY idx_crm_agent_session_payment_account (selected_payment_account_id),
                  KEY idx_crm_agent_session_last_message (last_customer_message_id),
                  CONSTRAINT fk_crm_agent_session_conversation
                    FOREIGN KEY (id_conversation) REFERENCES crm_whatsapp_conversation (id_conversation) ON DELETE CASCADE,
                  CONSTRAINT fk_crm_agent_session_connection
                    FOREIGN KEY (id_connection) REFERENCES crm_whatsapp_connection (id_connection),
                  CONSTRAINT fk_crm_agent_session_cliente
                    FOREIGN KEY (id_cliente) REFERENCES cliente (id_cliente) ON DELETE SET NULL,
                  CONSTRAINT fk_crm_agent_session_payment_method
                    FOREIGN KEY (selected_payment_method_id) REFERENCES metodo_pago_config (id_metodo_pago) ON DELETE SET NULL,
                  CONSTRAINT fk_crm_agent_session_payment_account
                    FOREIGN KEY (selected_payment_account_id) REFERENCES metodo_pago_cuenta (id_metodo_pago_cuenta) ON DELETE SET NULL,
                  CONSTRAINT fk_crm_agent_session_last_message
                    FOREIGN KEY (last_customer_message_id) REFERENCES crm_whatsapp_message (id_message) ON DELETE SET NULL
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
    }

    private void adaptSessionPaymentSelection(Connection connection, Statement statement) throws Exception {
        addColumnIfMissing(connection, statement, "crm_whatsapp_agent_session", "preferred_fulfillment_method",
                "VARCHAR(20) NULL");
        addColumnIfMissing(connection, statement, "crm_whatsapp_agent_session", "selected_payment_method_id",
                "INT NULL");
        addColumnIfMissing(connection, statement, "crm_whatsapp_agent_session", "selected_payment_account_id",
                "INT NULL");
        addColumnIfMissing(connection, statement, "crm_whatsapp_agent_session", "catalog_preview_sent_at",
                "DATETIME(6) NULL");
        addIndexIfMissing(connection, statement, "crm_whatsapp_agent_session",
                "idx_crm_agent_session_payment_method", "(selected_payment_method_id)");
        addIndexIfMissing(connection, statement, "crm_whatsapp_agent_session",
                "idx_crm_agent_session_payment_account", "(selected_payment_account_id)");
        addForeignKeyIfMissing(connection, statement, "crm_whatsapp_agent_session",
                "fk_crm_agent_session_payment_method",
                "FOREIGN KEY (selected_payment_method_id) REFERENCES metodo_pago_config (id_metodo_pago) ON DELETE SET NULL");
        addForeignKeyIfMissing(connection, statement, "crm_whatsapp_agent_session",
                "fk_crm_agent_session_payment_account",
                "FOREIGN KEY (selected_payment_account_id) REFERENCES metodo_pago_cuenta (id_metodo_pago_cuenta) ON DELETE SET NULL");
    }

    private void normalizeInactiveSessionSlots(Statement statement) throws SQLException {
        statement.executeUpdate("""
                UPDATE crm_whatsapp_agent_session
                SET active_slot = NULL
                WHERE active_slot = 0
                """);
    }

    private void createCartTables(Statement statement) throws SQLException {
        statement.execute("""
                CREATE TABLE IF NOT EXISTS crm_whatsapp_agent_cart (
                  id_agent_cart BIGINT NOT NULL AUTO_INCREMENT,
                  id_agent_session BIGINT NOT NULL,
                  idempotency_key VARCHAR(64) NOT NULL,
                  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
                  active_slot TINYINT(1) NULL DEFAULT 1,
                  currency VARCHAR(3) NOT NULL DEFAULT 'PEN',
                  subtotal DECIMAL(12,2) NOT NULL DEFAULT 0.00,
                  discount_total DECIMAL(12,2) NOT NULL DEFAULT 0.00,
                  total DECIMAL(12,2) NOT NULL DEFAULT 0.00,
                  lock_version BIGINT NOT NULL DEFAULT 0,
                  confirmed_at DATETIME(6) NULL,
                  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                  PRIMARY KEY (id_agent_cart),
                  UNIQUE KEY uk_crm_agent_cart_idempotency (idempotency_key),
                  UNIQUE KEY uk_crm_agent_cart_active (id_agent_session, active_slot),
                  KEY idx_crm_agent_cart_session_status (id_agent_session, status),
                  CONSTRAINT fk_crm_agent_cart_session
                    FOREIGN KEY (id_agent_session) REFERENCES crm_whatsapp_agent_session (id_agent_session) ON DELETE CASCADE
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);

        statement.execute("""
                CREATE TABLE IF NOT EXISTS crm_whatsapp_agent_cart_item (
                  id_agent_cart_item BIGINT NOT NULL AUTO_INCREMENT,
                  id_agent_cart BIGINT NOT NULL,
                  id_producto_variante INT NOT NULL,
                  quantity INT NOT NULL,
                  product_name_snapshot VARCHAR(150) NOT NULL,
                  color_snapshot VARCHAR(50) NOT NULL,
                  size_snapshot VARCHAR(20) NOT NULL,
                  unit_price DECIMAL(12,2) NOT NULL,
                  unit_discount DECIMAL(12,2) NOT NULL DEFAULT 0.00,
                  line_total DECIMAL(12,2) NOT NULL,
                  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                  PRIMARY KEY (id_agent_cart_item),
                  UNIQUE KEY uk_crm_agent_cart_item_variant (id_agent_cart, id_producto_variante),
                  KEY idx_crm_agent_cart_item_variant (id_producto_variante),
                  CONSTRAINT fk_crm_agent_cart_item_cart
                    FOREIGN KEY (id_agent_cart) REFERENCES crm_whatsapp_agent_cart (id_agent_cart) ON DELETE CASCADE,
                  CONSTRAINT fk_crm_agent_cart_item_variant
                    FOREIGN KEY (id_producto_variante) REFERENCES producto_variante (id_producto_variante)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);

        statement.execute("""
                CREATE TABLE IF NOT EXISTS crm_whatsapp_agent_cart_promotion (
                  id_agent_cart_promotion BIGINT NOT NULL AUTO_INCREMENT,
                  id_agent_cart BIGINT NOT NULL,
                  id_ecommerce_promocion_combo INT NOT NULL,
                  application_order INT NOT NULL DEFAULT 1,
                  quantity INT NOT NULL DEFAULT 1,
                  promotion_name_snapshot VARCHAR(150) NOT NULL,
                  combo_price_snapshot DECIMAL(12,2) NOT NULL,
                  discount_amount DECIMAL(12,2) NOT NULL,
                  application_key VARCHAR(64) NOT NULL,
                  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                  PRIMARY KEY (id_agent_cart_promotion),
                  UNIQUE KEY uk_crm_agent_cart_promo_application (application_key),
                  UNIQUE KEY uk_crm_agent_cart_promo_order
                    (id_agent_cart, id_ecommerce_promocion_combo, application_order),
                  KEY idx_crm_agent_cart_promo_combo (id_ecommerce_promocion_combo),
                  CONSTRAINT fk_crm_agent_cart_promo_cart
                    FOREIGN KEY (id_agent_cart) REFERENCES crm_whatsapp_agent_cart (id_agent_cart) ON DELETE CASCADE,
                  CONSTRAINT fk_crm_agent_cart_promo_combo
                    FOREIGN KEY (id_ecommerce_promocion_combo)
                    REFERENCES ecommerce_promocion_combo (id_ecommerce_promocion_combo)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);

        statement.execute("""
                CREATE TABLE IF NOT EXISTS crm_whatsapp_agent_pending_promotion (
                  id_agent_session BIGINT NOT NULL,
                  id_ecommerce_promocion_combo INT NOT NULL,
                  promotion_name VARCHAR(150) NOT NULL,
                  selections_json JSON NOT NULL,
                  source_job_id BIGINT NULL,
                  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                  PRIMARY KEY (id_agent_session),
                  KEY idx_crm_agent_pending_promo_combo (id_ecommerce_promocion_combo),
                  CONSTRAINT fk_crm_agent_pending_promo_session
                    FOREIGN KEY (id_agent_session) REFERENCES crm_whatsapp_agent_session (id_agent_session)
                    ON DELETE CASCADE,
                  CONSTRAINT fk_crm_agent_pending_promo_combo
                    FOREIGN KEY (id_ecommerce_promocion_combo)
                    REFERENCES ecommerce_promocion_combo (id_ecommerce_promocion_combo)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
    }

    private void createRuntimeTables(Statement statement) throws SQLException {
        statement.execute("""
                CREATE TABLE IF NOT EXISTS crm_whatsapp_agent_job (
                  id_agent_job BIGINT NOT NULL AUTO_INCREMENT,
                  id_agent_session BIGINT NOT NULL,
                  id_anchor_message BIGINT NULL,
                  idempotency_key VARCHAR(64) NOT NULL,
                  job_type VARCHAR(30) NOT NULL DEFAULT 'PROCESS_MESSAGE',
                  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                  active_slot TINYINT(1) NULL DEFAULT 1,
                  attempts INT NOT NULL DEFAULT 0,
                  available_at DATETIME(6) NOT NULL,
                  lease_until DATETIME(6) NULL,
                  locked_by VARCHAR(100) NULL,
                  obsolete_at DATETIME(6) NULL,
                  obsolete_reason VARCHAR(500) NULL,
                  last_error VARCHAR(1000) NULL,
                  started_at DATETIME(6) NULL,
                  completed_at DATETIME(6) NULL,
                  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                  PRIMARY KEY (id_agent_job),
                  UNIQUE KEY uk_crm_agent_job_idempotency (idempotency_key),
                  UNIQUE KEY uk_crm_agent_job_anchor (id_anchor_message),
                  UNIQUE KEY uk_crm_agent_job_active (id_agent_session, active_slot),
                  KEY idx_crm_agent_job_ready (status, available_at, lease_until),
                  KEY idx_crm_agent_job_session (id_agent_session, created_at),
                  CONSTRAINT fk_crm_agent_job_session
                    FOREIGN KEY (id_agent_session) REFERENCES crm_whatsapp_agent_session (id_agent_session) ON DELETE CASCADE,
                  CONSTRAINT fk_crm_agent_job_anchor
                    FOREIGN KEY (id_anchor_message) REFERENCES crm_whatsapp_message (id_message) ON DELETE SET NULL
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);

        statement.execute("""
                CREATE TABLE IF NOT EXISTS crm_whatsapp_agent_tool_call (
                  id_agent_tool_call BIGINT NOT NULL AUTO_INCREMENT,
                  id_agent_session BIGINT NOT NULL,
                  id_agent_job BIGINT NULL,
                  idempotency_key VARCHAR(64) NOT NULL,
                  tool_name VARCHAR(100) NOT NULL,
                  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                  request_json MEDIUMTEXT NULL,
                  response_json MEDIUMTEXT NULL,
                  error_code VARCHAR(80) NULL,
                  error_message VARCHAR(1000) NULL,
                  started_at DATETIME(6) NULL,
                  completed_at DATETIME(6) NULL,
                  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                  PRIMARY KEY (id_agent_tool_call),
                  UNIQUE KEY uk_crm_agent_tool_idempotency (idempotency_key),
                  KEY idx_crm_agent_tool_session (id_agent_session, created_at),
                  KEY idx_crm_agent_tool_job (id_agent_job),
                  KEY idx_crm_agent_tool_status (status, created_at),
                  CONSTRAINT fk_crm_agent_tool_session
                    FOREIGN KEY (id_agent_session) REFERENCES crm_whatsapp_agent_session (id_agent_session) ON DELETE CASCADE,
                  CONSTRAINT fk_crm_agent_tool_job
                    FOREIGN KEY (id_agent_job) REFERENCES crm_whatsapp_agent_job (id_agent_job) ON DELETE SET NULL
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);

        statement.execute("""
                CREATE TABLE IF NOT EXISTS crm_whatsapp_agent_outbox (
                  id_agent_outbox BIGINT NOT NULL AUTO_INCREMENT,
                  id_agent_session BIGINT NOT NULL,
                  id_agent_job BIGINT NULL,
                  id_conversation BIGINT NOT NULL,
                  reply_to_message_id BIGINT NULL,
                  idempotency_key VARCHAR(64) NOT NULL,
                  payload_hash VARCHAR(64) NOT NULL,
                  sequence_no BIGINT NOT NULL,
                  message_type VARCHAR(20) NOT NULL,
                  body TEXT NULL,
                  media_storage_path VARCHAR(500) NULL,
                  media_file_name VARCHAR(255) NULL,
                  media_mime_type VARCHAR(120) NULL,
                  caption TEXT NULL,
                  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                  attempts INT NOT NULL DEFAULT 0,
                  available_at DATETIME(6) NOT NULL,
                  lease_until DATETIME(6) NULL,
                  locked_by VARCHAR(120) NULL,
                  whatsapp_message_id VARCHAR(120) NULL,
                  id_message BIGINT NULL,
                  sent_at DATETIME(6) NULL,
                  failed_at DATETIME(6) NULL,
                  last_error VARCHAR(1000) NULL,
                  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                  PRIMARY KEY (id_agent_outbox),
                  UNIQUE KEY uk_crm_agent_outbox_idempotency (idempotency_key),
                  UNIQUE KEY uk_crm_agent_outbox_sequence (id_agent_session, sequence_no),
                  UNIQUE KEY uk_crm_agent_outbox_wamid (whatsapp_message_id),
                  KEY idx_crm_agent_outbox_ready (status, available_at, lease_until),
                  KEY idx_crm_agent_outbox_conversation (id_conversation, sequence_no),
                  KEY idx_crm_agent_outbox_job (id_agent_job),
                  KEY idx_crm_agent_outbox_reply (reply_to_message_id),
                  CONSTRAINT fk_crm_agent_outbox_session
                    FOREIGN KEY (id_agent_session) REFERENCES crm_whatsapp_agent_session (id_agent_session) ON DELETE CASCADE,
                  CONSTRAINT fk_crm_agent_outbox_job
                    FOREIGN KEY (id_agent_job) REFERENCES crm_whatsapp_agent_job (id_agent_job) ON DELETE SET NULL,
                  CONSTRAINT fk_crm_agent_outbox_conversation
                    FOREIGN KEY (id_conversation) REFERENCES crm_whatsapp_conversation (id_conversation) ON DELETE CASCADE,
                  CONSTRAINT fk_crm_agent_outbox_reply
                    FOREIGN KEY (reply_to_message_id) REFERENCES crm_whatsapp_message (id_message) ON DELETE SET NULL,
                  CONSTRAINT fk_crm_agent_outbox_message
                    FOREIGN KEY (id_message) REFERENCES crm_whatsapp_message (id_message) ON DELETE SET NULL
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
    }

    private void createAiUsageTable(Statement statement) throws SQLException {
        statement.execute("""
                CREATE TABLE IF NOT EXISTS crm_whatsapp_agent_ai_usage (
                  id_ai_usage BIGINT NOT NULL AUTO_INCREMENT,
                  id_connection BIGINT NOT NULL,
                  id_agent_session BIGINT NULL,
                  id_agent_job BIGINT NULL,
                  id_conversation BIGINT NULL,
                  phase VARCHAR(30) NOT NULL,
                  provider VARCHAR(30) NOT NULL,
                  model VARCHAR(120) NULL,
                  credential_fingerprint VARCHAR(64) NULL,
                  input_tokens INT NULL,
                  output_tokens INT NULL,
                  cached_tokens BIGINT NULL,
                  total_tokens INT NULL,
                  latency_ms BIGINT NOT NULL,
                  tool_calls INT NOT NULL DEFAULT 0,
                  status VARCHAR(20) NOT NULL,
                  error_code VARCHAR(80) NULL,
                  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                  PRIMARY KEY (id_ai_usage),
                  KEY idx_crm_ai_usage_connection_date (id_connection, created_at),
                  KEY idx_crm_ai_usage_session (id_agent_session, created_at),
                  KEY idx_crm_ai_usage_job (id_agent_job, created_at),
                  KEY idx_crm_ai_usage_phase (phase, created_at),
                  CONSTRAINT fk_crm_ai_usage_connection FOREIGN KEY (id_connection)
                    REFERENCES crm_whatsapp_connection(id_connection) ON DELETE CASCADE,
                  CONSTRAINT fk_crm_ai_usage_session FOREIGN KEY (id_agent_session)
                    REFERENCES crm_whatsapp_agent_session(id_agent_session) ON DELETE SET NULL,
                  CONSTRAINT fk_crm_ai_usage_job FOREIGN KEY (id_agent_job)
                    REFERENCES crm_whatsapp_agent_job(id_agent_job) ON DELETE SET NULL,
                  CONSTRAINT fk_crm_ai_usage_conversation FOREIGN KEY (id_conversation)
                    REFERENCES crm_whatsapp_conversation(id_conversation) ON DELETE SET NULL
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
    }

    private void adaptAgentOutboxTable(Connection connection, Statement statement) throws Exception {
        addColumnIfMissing(connection, statement, "crm_whatsapp_agent_outbox", "payload_hash", "VARCHAR(64) NULL");
        statement.executeUpdate("""
                UPDATE crm_whatsapp_agent_outbox
                SET payload_hash = SHA2(CONCAT_WS('|', message_type, TRIM(COALESCE(body, '')),
                    TRIM(COALESCE(media_storage_path, '')), TRIM(COALESCE(media_file_name, '')),
                    TRIM(COALESCE(media_mime_type, '')), TRIM(COALESCE(caption, '')),
                    COALESCE(CAST(reply_to_message_id AS CHAR), 'null')), 256)
                WHERE payload_hash IS NULL OR payload_hash = ''
                """);
        statement.execute("ALTER TABLE crm_whatsapp_agent_outbox MODIFY COLUMN payload_hash VARCHAR(64) NOT NULL");
        addColumnIfMissing(connection, statement, "crm_whatsapp_agent_outbox", "locked_by", "VARCHAR(120) NULL");
        addColumnIfMissing(connection, statement, "crm_whatsapp_agent_outbox", "id_message", "BIGINT NULL");
        addColumnIfMissing(connection, statement, "crm_whatsapp_agent_outbox", "failed_at", "DATETIME(6) NULL");
        addIndexIfMissing(connection, statement, "crm_whatsapp_agent_outbox",
                "idx_crm_agent_outbox_order", "(id_conversation, sequence_no, status)");
        addIndexIfMissing(connection, statement, "crm_whatsapp_agent_outbox",
                "idx_crm_agent_outbox_message", "(id_message)");
        addForeignKeyIfMissing(connection, statement, "crm_whatsapp_agent_outbox",
                "fk_crm_agent_outbox_message",
                "FOREIGN KEY (id_message) REFERENCES crm_whatsapp_message (id_message) ON DELETE SET NULL");
    }

    private void adaptAgentToolCallTable(Connection connection, Statement statement) throws Exception {
        addColumnIfMissing(connection, statement, "crm_whatsapp_agent_tool_call", "request_fingerprint",
                "VARCHAR(64) NULL");
    }

    private void adaptAgentJobTable(Connection connection, Statement statement) throws Exception {
        addColumnIfMissing(connection, statement, "crm_whatsapp_agent_job", "active_slot", "TINYINT(1) NULL");
        addColumnIfMissing(connection, statement, "crm_whatsapp_agent_job", "response_text", "TEXT NULL");
        addColumnIfMissing(connection, statement, "crm_whatsapp_agent_job", "conversation_summary", "TEXT NULL");
        addColumnIfMissing(connection, statement, "crm_whatsapp_agent_job", "requires_human_help",
                "TINYINT(1) NOT NULL DEFAULT 0");
        addColumnIfMissing(connection, statement, "crm_whatsapp_agent_job", "provider", "VARCHAR(30) NULL");
        addColumnIfMissing(connection, statement, "crm_whatsapp_agent_job", "model", "VARCHAR(100) NULL");
        addColumnIfMissing(connection, statement, "crm_whatsapp_agent_job", "latency_ms", "BIGINT NULL");
        addColumnIfMissing(connection, statement, "crm_whatsapp_agent_job", "tool_calls", "INT NULL");
        addColumnIfMissing(connection, statement, "crm_whatsapp_agent_job", "context_fingerprint", "VARCHAR(64) NULL");
        addColumnIfMissing(connection, statement, "crm_whatsapp_agent_job", "generated_at", "DATETIME(6) NULL");
        addColumnIfMissing(connection, statement, "crm_whatsapp_agent_job", "media_json", "TEXT NULL");
        statement.executeUpdate("UPDATE crm_whatsapp_agent_job SET active_slot = NULL");
        statement.executeUpdate("""
                UPDATE crm_whatsapp_agent_job j
                INNER JOIN (
                  SELECT id_agent_session, MAX(id_agent_job) AS keep_job_id
                  FROM crm_whatsapp_agent_job
                  WHERE obsolete_at IS NULL
                    AND status IN ('PENDING', 'RUNNING', 'PROCESSING')
                  GROUP BY id_agent_session
                ) current_job ON current_job.id_agent_session = j.id_agent_session
                SET j.status = 'OBSOLETE',
                    j.obsolete_at = COALESCE(j.obsolete_at, CURRENT_TIMESTAMP(6)),
                    j.obsolete_reason = COALESCE(j.obsolete_reason, 'Job duplicado normalizado al iniciar')
                WHERE j.id_agent_job <> current_job.keep_job_id
                  AND j.obsolete_at IS NULL
                  AND j.status IN ('PENDING', 'RUNNING', 'PROCESSING')
                """);
        statement.executeUpdate("""
                UPDATE crm_whatsapp_agent_job
                SET active_slot = 1
                WHERE obsolete_at IS NULL
                  AND status IN ('PENDING', 'RUNNING', 'PROCESSING')
                """);
        addUniqueIndexIfMissing(connection, statement, "crm_whatsapp_agent_job",
                "uk_crm_agent_job_active", "(id_agent_session, active_slot)");
    }

    private void createFulfillmentAndSupportTables(Statement statement) throws SQLException {
        statement.execute("""
                CREATE TABLE IF NOT EXISTS crm_whatsapp_fulfillment (
                  id_fulfillment BIGINT NOT NULL AUTO_INCREMENT,
                  id_agent_session BIGINT NULL,
                  id_conversation BIGINT NULL,
                  id_payment_request BIGINT NULL,
                  id_venta INT NULL,
                  id_pickup_branch INT NULL,
                  id_shalom_agency BIGINT NULL,
                  idempotency_key VARCHAR(64) NOT NULL,
                  method VARCHAR(20) NOT NULL,
                  status VARCHAR(40) NOT NULL DEFAULT 'DRAFT',
                  recipient_name VARCHAR(180) NULL,
                  document_type VARCHAR(10) NULL,
                  document_number VARCHAR(20) NULL,
                  phone VARCHAR(30) NULL,
                  shalom_agency VARCHAR(180) NULL,
                  shalom_destination VARCHAR(180) NULL,
                  shalom_agency_address VARCHAR(500) NULL,
                  shalom_candidate_ids VARCHAR(500) NULL,
                  notes VARCHAR(500) NULL,
                  confirmed_at DATETIME(6) NULL,
                  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                  PRIMARY KEY (id_fulfillment),
                  UNIQUE KEY uk_crm_fulfillment_session (id_agent_session),
                  UNIQUE KEY uk_crm_fulfillment_idempotency (idempotency_key),
                  KEY idx_crm_fulfillment_shalom_agency (id_shalom_agency),
                  KEY idx_crm_fulfillment_conversation (id_conversation, created_at),
                  KEY idx_crm_fulfillment_payment (id_payment_request),
                  KEY idx_crm_fulfillment_sale (id_venta),
                  KEY idx_crm_fulfillment_status (status, created_at),
                  CONSTRAINT fk_crm_fulfillment_session
                    FOREIGN KEY (id_agent_session) REFERENCES crm_whatsapp_agent_session (id_agent_session) ON DELETE SET NULL,
                  CONSTRAINT fk_crm_fulfillment_conversation
                    FOREIGN KEY (id_conversation) REFERENCES crm_whatsapp_conversation (id_conversation) ON DELETE SET NULL,
                  CONSTRAINT fk_crm_fulfillment_payment
                    FOREIGN KEY (id_payment_request) REFERENCES crm_whatsapp_payment_request (id_payment_request) ON DELETE SET NULL,
                  CONSTRAINT fk_crm_fulfillment_sale
                    FOREIGN KEY (id_venta) REFERENCES venta (id_venta),
                  CONSTRAINT fk_crm_fulfillment_pickup
                    FOREIGN KEY (id_pickup_branch) REFERENCES sucursal (id_sucursal),
                  CONSTRAINT fk_crm_fulfillment_shalom_agency
                    FOREIGN KEY (id_shalom_agency) REFERENCES shalom_agency (id_shalom_agency) ON DELETE SET NULL
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);

        statement.execute("""
                CREATE TABLE IF NOT EXISTS crm_whatsapp_support_case (
                  id_support_case BIGINT NOT NULL AUTO_INCREMENT,
                  id_agent_session BIGINT NULL,
                  id_conversation BIGINT NULL,
                  id_payment_request BIGINT NULL,
                  id_venta INT NULL,
                  assigned_user_id INT NULL,
                  idempotency_key VARCHAR(64) NOT NULL,
                  category VARCHAR(40) NOT NULL,
                  reason VARCHAR(160) NOT NULL,
                  summary TEXT NULL,
                  priority VARCHAR(15) NOT NULL DEFAULT 'NORMAL',
                  status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
                  resolution_note VARCHAR(500) NULL,
                  opened_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                  assigned_at DATETIME(6) NULL,
                  resolved_at DATETIME(6) NULL,
                  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                  PRIMARY KEY (id_support_case),
                  UNIQUE KEY uk_crm_support_case_idempotency (idempotency_key),
                  KEY idx_crm_support_case_queue (status, priority, opened_at),
                  KEY idx_crm_support_case_session (id_agent_session, opened_at),
                  KEY idx_crm_support_case_conversation (id_conversation, opened_at),
                  KEY idx_crm_support_case_payment (id_payment_request),
                  KEY idx_crm_support_case_sale (id_venta),
                  KEY idx_crm_support_case_assignee (assigned_user_id, status),
                  CONSTRAINT fk_crm_support_case_session
                    FOREIGN KEY (id_agent_session) REFERENCES crm_whatsapp_agent_session (id_agent_session) ON DELETE SET NULL,
                  CONSTRAINT fk_crm_support_case_conversation
                    FOREIGN KEY (id_conversation) REFERENCES crm_whatsapp_conversation (id_conversation) ON DELETE SET NULL,
                  CONSTRAINT fk_crm_support_case_payment
                    FOREIGN KEY (id_payment_request) REFERENCES crm_whatsapp_payment_request (id_payment_request) ON DELETE SET NULL,
                  CONSTRAINT fk_crm_support_case_sale
                    FOREIGN KEY (id_venta) REFERENCES venta (id_venta),
                  CONSTRAINT fk_crm_support_case_assignee
                    FOREIGN KEY (assigned_user_id) REFERENCES usuario (id_usuario)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);

        statement.execute("""
                CREATE TABLE IF NOT EXISTS crm_whatsapp_payment_review_audit (
                  id_review_audit BIGINT NOT NULL AUTO_INCREMENT,
                  id_payment_evidence BIGINT NOT NULL,
                  id_payment_request BIGINT NOT NULL,
                  id_agent_session BIGINT NULL,
                  id_conversation BIGINT NULL,
                  id_venta INT NOT NULL,
                  reviewed_by INT NOT NULL,
                  action VARCHAR(20) NOT NULL,
                  reason VARCHAR(500) NULL,
                  expected_amount DECIMAL(12,2) NOT NULL,
                  detected_amount DECIMAL(12,2) NULL,
                  operation_code VARCHAR(120) NULL,
                  sale_status_before VARCHAR(20) NOT NULL,
                  sale_status_after VARCHAR(20) NOT NULL,
                  agent_state_before VARCHAR(50) NOT NULL,
                  agent_state_after VARCHAR(50) NOT NULL,
                  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                  PRIMARY KEY (id_review_audit),
                  UNIQUE KEY uk_crm_payment_review_evidence (id_payment_evidence),
                  KEY idx_crm_payment_review_request (id_payment_request),
                  KEY idx_crm_payment_review_session (id_agent_session),
                  KEY idx_crm_payment_review_conversation (id_conversation, created_at),
                  KEY idx_crm_payment_review_sale (id_venta),
                  KEY idx_crm_payment_review_actor (reviewed_by, created_at),
                  CONSTRAINT fk_crm_payment_review_evidence
                    FOREIGN KEY (id_payment_evidence) REFERENCES crm_whatsapp_payment_evidence (id_payment_evidence) ON DELETE CASCADE,
                  CONSTRAINT fk_crm_payment_review_request
                    FOREIGN KEY (id_payment_request) REFERENCES crm_whatsapp_payment_request (id_payment_request),
                  CONSTRAINT fk_crm_payment_review_session
                    FOREIGN KEY (id_agent_session) REFERENCES crm_whatsapp_agent_session (id_agent_session) ON DELETE SET NULL,
                  CONSTRAINT fk_crm_payment_review_conversation
                    FOREIGN KEY (id_conversation) REFERENCES crm_whatsapp_conversation (id_conversation) ON DELETE SET NULL,
                  CONSTRAINT fk_crm_payment_review_sale
                    FOREIGN KEY (id_venta) REFERENCES venta (id_venta),
                  CONSTRAINT fk_crm_payment_review_actor
                    FOREIGN KEY (reviewed_by) REFERENCES usuario (id_usuario)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
    }

    private void createShalomAgencyTables(Statement statement) throws SQLException {
        statement.execute("""
                CREATE TABLE IF NOT EXISTS shalom_agency (
                  id_shalom_agency BIGINT NOT NULL AUTO_INCREMENT,
                  ter_id INT NOT NULL,
                  abbreviation VARCHAR(30) NULL,
                  name VARCHAR(300) NOT NULL,
                  department VARCHAR(120) NULL,
                  province VARCHAR(120) NULL,
                  zone VARCHAR(180) NULL,
                  place VARCHAR(180) NULL,
                  address VARCHAR(500) NULL,
                  phone VARCHAR(100) NULL,
                  attention_hours VARCHAR(300) NULL,
                  latitude DECIMAL(12,8) NULL,
                  longitude DECIMAL(12,8) NULL,
                  ubigeo_id INT NULL,
                  origin_enabled TINYINT(1) NOT NULL DEFAULT 0,
                  destination_enabled TINYINT(1) NOT NULL DEFAULT 0,
                  delivery_enabled TINYINT(1) NOT NULL DEFAULT 0,
                  principal TINYINT(1) NOT NULL DEFAULT 0,
                  service_status VARCHAR(120) NULL,
                  normalized_search TEXT NOT NULL,
                  raw_payload JSON NOT NULL,
                  active TINYINT(1) NOT NULL DEFAULT 1,
                  last_seen_at DATETIME(6) NOT NULL,
                  synced_at DATETIME(6) NOT NULL,
                  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                  PRIMARY KEY (id_shalom_agency),
                  UNIQUE KEY uk_shalom_agency_ter_id (ter_id),
                  KEY idx_shalom_agency_available (active, destination_enabled),
                  KEY idx_shalom_agency_location (department, province),
                  KEY idx_shalom_agency_sync (last_seen_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
        statement.execute("""
                CREATE TABLE IF NOT EXISTS shalom_agency_sync (
                  id_shalom_agency_sync BIGINT NOT NULL AUTO_INCREMENT,
                  status VARCHAR(20) NOT NULL,
                  source_total INT NULL,
                  received_count INT NOT NULL DEFAULT 0,
                  created_count INT NOT NULL DEFAULT 0,
                  updated_count INT NOT NULL DEFAULT 0,
                  deactivated_count INT NOT NULL DEFAULT 0,
                  error_message VARCHAR(1000) NULL,
                  started_at DATETIME(6) NOT NULL,
                  completed_at DATETIME(6) NULL,
                  PRIMARY KEY (id_shalom_agency_sync),
                  KEY idx_shalom_agency_sync_status (status, started_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
        statement.execute("""
                CREATE TABLE IF NOT EXISTS crm_whatsapp_shalom_profile (
                  id_shalom_profile BIGINT NOT NULL AUTO_INCREMENT,
                  id_cliente INT NOT NULL,
                  id_shalom_agency BIGINT NOT NULL,
                  recipient_name VARCHAR(180) NOT NULL,
                  document_number VARCHAR(20) NOT NULL,
                  phone VARCHAR(30) NOT NULL,
                  agency_name_snapshot VARCHAR(300) NOT NULL,
                  agency_address_snapshot VARCHAR(500) NULL,
                  confirmed_at DATETIME(6) NOT NULL,
                  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                  PRIMARY KEY (id_shalom_profile),
                  UNIQUE KEY uk_crm_shalom_profile_customer (id_cliente),
                  KEY idx_crm_shalom_profile_agency (id_shalom_agency),
                  CONSTRAINT fk_crm_shalom_profile_customer
                    FOREIGN KEY (id_cliente) REFERENCES cliente (id_cliente) ON DELETE CASCADE,
                  CONSTRAINT fk_crm_shalom_profile_agency
                    FOREIGN KEY (id_shalom_agency) REFERENCES shalom_agency (id_shalom_agency)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
    }

    private void adaptShalomFulfillment(Connection connection, Statement statement) throws Exception {
        // AWAITING_AGENCY_CONFIRMATION tiene 28 caracteres. Las instalaciones
        // creadas antes del flujo de confirmacion conservan VARCHAR(20), por lo
        // que Hibernate falla antes de poder devolver los candidatos encontrados.
        statement.execute("""
                ALTER TABLE crm_whatsapp_fulfillment
                MODIFY COLUMN status VARCHAR(40) NOT NULL DEFAULT 'DRAFT'
                """);
        addColumnIfMissing(connection, statement, "crm_whatsapp_fulfillment", "id_shalom_agency", "BIGINT NULL");
        addColumnIfMissing(connection, statement, "crm_whatsapp_fulfillment", "shalom_agency_address", "VARCHAR(500) NULL");
        addColumnIfMissing(connection, statement, "crm_whatsapp_fulfillment", "shalom_candidate_ids", "VARCHAR(500) NULL");
        addIndexIfMissing(connection, statement, "crm_whatsapp_fulfillment", "idx_crm_fulfillment_shalom_agency",
                "(id_shalom_agency)");
        addForeignKeyIfMissing(connection, statement, "crm_whatsapp_fulfillment",
                "fk_crm_fulfillment_shalom_agency",
                "FOREIGN KEY (id_shalom_agency) REFERENCES shalom_agency (id_shalom_agency) ON DELETE SET NULL");
        if (foreignKeyExists(connection, "crm_whatsapp_fulfillment", "fk_crm_fulfillment_screenshot")) {
            statement.execute("ALTER TABLE crm_whatsapp_fulfillment DROP FOREIGN KEY fk_crm_fulfillment_screenshot");
        }
        if (indexExists(connection, "crm_whatsapp_fulfillment", "uk_crm_fulfillment_shalom_message")) {
            statement.execute("ALTER TABLE crm_whatsapp_fulfillment DROP INDEX uk_crm_fulfillment_shalom_message");
        }
        dropColumnIfPresent(connection, statement, "crm_whatsapp_fulfillment", "shalom_screenshot_message_id");
        dropColumnIfPresent(connection, statement, "crm_whatsapp_fulfillment", "shalom_screenshot_path");
        statement.executeUpdate("""
                UPDATE crm_whatsapp_agent_session
                SET state='ESPERANDO_DATOS_SHALOM', updated_at=CURRENT_TIMESTAMP(6)
                WHERE active_slot=TRUE AND state='ESPERANDO_CAPTURA_SHALOM'
                """);
        statement.executeUpdate("""
                UPDATE crm_whatsapp_fulfillment
                SET status='DRAFT', confirmed_at=NULL
                WHERE method='SHALOM' AND status='AWAITING_SCREENSHOT'
                """);
    }

    private void adaptPaymentTables(Connection connection, Statement statement) throws Exception {
        addColumnIfMissing(connection, statement, "crm_whatsapp_payment_request", "id_agent_session", "BIGINT NULL");
        addColumnIfMissing(connection, statement, "crm_whatsapp_payment_request", "included_film_amount",
                "DECIMAL(12,2) NOT NULL DEFAULT 0.00");
        addColumnIfMissing(connection, statement, "crm_whatsapp_payment_request", "agent_idempotency_key",
                "VARCHAR(64) NULL");
        addColumnIfMissing(connection, statement, "crm_whatsapp_payment_request", "evidence_accepted_at",
                "DATETIME(6) NULL");
        addIndexIfMissing(connection, statement, "crm_whatsapp_payment_request", "idx_crm_payment_request_agent",
                "(id_agent_session, status, created_at)");
        addUniqueIndexIfMissing(connection, statement, "crm_whatsapp_payment_request",
                "uk_crm_payment_request_agent_idempotency", "(agent_idempotency_key)");
        addForeignKeyIfMissing(connection, statement, "crm_whatsapp_payment_request",
                "fk_crm_payment_request_agent_session",
                "FOREIGN KEY (id_agent_session) REFERENCES crm_whatsapp_agent_session (id_agent_session) ON DELETE SET NULL");

        addColumnIfMissing(connection, statement, "crm_whatsapp_payment_evidence", "id_agent_session", "BIGINT NULL");
        addColumnIfMissing(connection, statement, "crm_whatsapp_payment_evidence", "agent_validation_key",
                "VARCHAR(64) NULL");
        addIndexIfMissing(connection, statement, "crm_whatsapp_payment_evidence", "idx_crm_payment_evidence_agent",
                "(id_agent_session, created_at)");
        addIndexIfMissing(connection, statement, "crm_whatsapp_payment_evidence", "idx_crm_payment_evidence_agent_queue",
                "(id_agent_session, processing_status, available_at)");
        addIndexIfMissing(connection, statement, "crm_whatsapp_payment_evidence", "idx_crm_payment_evidence_operation",
                "(operation_code)");
        addUniqueIndexIfMissing(connection, statement, "crm_whatsapp_payment_evidence",
                "uk_crm_payment_evidence_agent_validation", "(agent_validation_key)");
        addForeignKeyIfMissing(connection, statement, "crm_whatsapp_payment_evidence",
                "fk_crm_payment_evidence_agent_session",
                "FOREIGN KEY (id_agent_session) REFERENCES crm_whatsapp_agent_session (id_agent_session) ON DELETE SET NULL");
    }

    private void adaptPaymentReviewConversationReference(Connection connection, Statement statement)
            throws Exception {
        if (!columnExists(connection, "crm_whatsapp_payment_review_audit", "id_conversation")) return;
        statement.execute("""
                ALTER TABLE crm_whatsapp_payment_review_audit
                MODIFY COLUMN id_conversation BIGINT NULL
                """);
        Integer deleteRule = foreignKeyDeleteRule(connection, "crm_whatsapp_payment_review_audit",
                "fk_crm_payment_review_conversation");
        if (deleteRule != null && deleteRule != DatabaseMetaData.importedKeySetNull) {
            statement.execute("""
                    ALTER TABLE crm_whatsapp_payment_review_audit
                    DROP FOREIGN KEY fk_crm_payment_review_conversation
                    """);
            deleteRule = null;
        }
        if (deleteRule == null) {
            statement.execute("""
                    ALTER TABLE crm_whatsapp_payment_review_audit
                    ADD CONSTRAINT fk_crm_payment_review_conversation
                    FOREIGN KEY (id_conversation)
                    REFERENCES crm_whatsapp_conversation (id_conversation)
                    ON DELETE SET NULL
                    """);
        }
    }

    private void migrateActiveSessionsToPaymentFirstFlow(Statement statement) throws SQLException {
        statement.executeUpdate("""
                UPDATE crm_whatsapp_agent_session s
                JOIN crm_whatsapp_film_addon a ON a.id_agent_session=s.id_agent_session
                SET s.state='ESPERANDO_PAGO_FILM', s.last_event='FILM_ACEPTADO',
                    s.state_changed_at=CURRENT_TIMESTAMP(6), s.updated_at=CURRENT_TIMESTAMP(6)
                WHERE s.active_slot=TRUE AND s.state='VENTA_PENDIENTE_REVISION'
                  AND a.status='AWAITING_PAYMENT' AND a.expires_at>CURRENT_TIMESTAMP(6)
                """);
        statement.executeUpdate("""
                UPDATE crm_whatsapp_agent_session s
                SET s.state='ESPERANDO_METODO_PAGO', s.updated_at=CURRENT_TIMESTAMP(6)
                WHERE s.active_slot=TRUE AND s.state='ESPERANDO_DATOS_CLIENTE'
                  AND NOT EXISTS (
                    SELECT 1 FROM crm_whatsapp_payment_request r
                    WHERE r.id_agent_session=s.id_agent_session AND r.evidence_accepted_at IS NOT NULL)
                """);
        statement.executeUpdate("""
                UPDATE crm_whatsapp_agent_session s
                SET s.state='ESPERANDO_DATOS_CLIENTE', s.updated_at=CURRENT_TIMESTAMP(6)
                WHERE s.active_slot=TRUE AND s.state='ESPERANDO_METODO_ENTREGA' AND s.id_cliente IS NULL
                  AND EXISTS (
                    SELECT 1 FROM crm_whatsapp_payment_request r
                    WHERE r.id_agent_session=s.id_agent_session AND r.evidence_accepted_at IS NOT NULL)
                """);
    }

    private void addColumnIfMissing(
            Connection connection, Statement statement, String table, String column, String definition) throws Exception {
        if (!columnExists(connection, table, column)) {
            statement.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        }
    }

    private void addIndexIfMissing(
            Connection connection, Statement statement, String table, String index, String columns) throws Exception {
        if (!indexExists(connection, table, index)) {
            statement.execute("ALTER TABLE " + table + " ADD KEY " + index + " " + columns);
        }
    }

    private void addUniqueIndexIfMissing(
            Connection connection, Statement statement, String table, String index, String columns) throws Exception {
        if (!indexExists(connection, table, index)) {
            statement.execute("ALTER TABLE " + table + " ADD UNIQUE KEY " + index + " " + columns);
        }
    }

    private void addForeignKeyIfMissing(
            Connection connection, Statement statement, String table, String name, String definition) throws Exception {
        if (!foreignKeyExists(connection, table, name)) {
            statement.execute("ALTER TABLE " + table + " ADD CONSTRAINT " + name + " " + definition);
        }
    }

    private void dropColumnIfPresent(Connection connection, Statement statement, String table, String column)
            throws Exception {
        if (columnExists(connection, table, column)) {
            statement.execute("ALTER TABLE " + table + " DROP COLUMN " + column);
        }
    }

    private boolean columnExists(Connection connection, String table, String column) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        for (String candidate : new String[] { table, table.toUpperCase(Locale.ROOT) }) {
            try (ResultSet columns = metadata.getColumns(connection.getCatalog(), null, candidate, column)) {
                if (columns.next()) return true;
            }
            try (ResultSet columns = metadata.getColumns(
                    connection.getCatalog(), null, candidate, column.toUpperCase(Locale.ROOT))) {
                if (columns.next()) return true;
            }
        }
        return false;
    }

    private boolean indexExists(Connection connection, String table, String index) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        for (String candidate : new String[] { table, table.toUpperCase(Locale.ROOT) }) {
            try (ResultSet indexes = metadata.getIndexInfo(connection.getCatalog(), null, candidate, false, false)) {
                while (indexes.next()) {
                    if (index.equalsIgnoreCase(indexes.getString("INDEX_NAME"))) return true;
                }
            }
        }
        return false;
    }

    private boolean foreignKeyExists(Connection connection, String table, String name) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        for (String candidate : new String[] { table, table.toUpperCase(Locale.ROOT) }) {
            try (ResultSet keys = metadata.getImportedKeys(connection.getCatalog(), null, candidate)) {
                while (keys.next()) {
                    if (name.equalsIgnoreCase(keys.getString("FK_NAME"))) return true;
                }
            }
        }
        return false;
    }

    private Integer foreignKeyDeleteRule(Connection connection, String table, String name) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        for (String candidate : new String[] { table, table.toUpperCase(Locale.ROOT) }) {
            try (ResultSet keys = metadata.getImportedKeys(connection.getCatalog(), null, candidate)) {
                while (keys.next()) {
                    if (name.equalsIgnoreCase(keys.getString("FK_NAME"))) {
                        return keys.getInt("DELETE_RULE");
                    }
                }
            }
        }
        return null;
    }
}
