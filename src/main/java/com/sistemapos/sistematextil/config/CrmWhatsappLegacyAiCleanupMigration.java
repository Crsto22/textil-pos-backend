package com.sistemapos.sistematextil.config;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Retira el runtime conversacional anterior exclusivamente de una base MySQL local.
 * Las tablas de conocimiento se conservan para el agente futuro.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class CrmWhatsappLegacyAiCleanupMigration implements ApplicationRunner {

    private static final String[] RUNTIME_TABLES = {
            "crm_whatsapp_ai_feedback",
            "crm_whatsapp_ai_product_query",
            "crm_whatsapp_ai_audit_event",
            "crm_whatsapp_ai_delivery",
            "crm_whatsapp_ai_memory_item",
            "crm_whatsapp_ai_memory",
            "crm_whatsapp_ai_sale_draft_promotion",
            "crm_whatsapp_ai_sale_draft_item",
            "crm_whatsapp_ai_sale_draft",
            "crm_whatsapp_ai_run",
            "crm_whatsapp_ai_job"
    };

    private static final String[] CONFIG_COLUMNS = {
            "modo", "zona_horaria", "dias_atencion", "hora_inicio", "hora_fin",
            "espera_respuesta_segundos", "tono", "instrucciones_personalizadas",
            "intenciones_permitidas", "max_respuestas_automaticas", "confianza_minima",
            "transferir_baja_confianza", "transferir_solicitud_humana",
            "transferir_asunto_sensible", "transferir_imagenes_asesora",
            "mostrar_productos_nuevos", "mandar_catalogo_imagenes",
            "sugerir_promociones_carrito", "daily_token_limit", "monthly_token_limit",
            "monthly_budget_usd", "input_cost_per_million_usd", "output_cost_per_million_usd",
            "automatic_rollout_percent", "natural_response_enabled",
            "natural_response_rollout_percent", "operational_status", "operational_reason",
            "operational_changed_at", "operational_changed_by",
            "guia_tallas_intent_initialized", "ecommerce_link_intent_initialized"
    };

    private final DataSource dataSource;
    private final boolean enabled;

    public CrmWhatsappLegacyAiCleanupMigration(
            DataSource dataSource,
            @Value("${crm.whatsapp.legacy-ai-cleanup-enabled:true}") boolean enabled) {
        this.dataSource = dataSource;
        this.enabled = enabled;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!enabled) return;
        try (Connection connection = dataSource.getConnection()) {
            if (!isLocalDatabase(connection.getMetaData().getURL())) return;
            try (Statement statement = connection.createStatement()) {
                String legacyModeCondition = columnExists(connection, "crm_whatsapp_conversation", "ai_attention_mode")
                        ? " OR ai_attention_mode = 'AUTOMATICA'"
                        : "";
                statement.executeUpdate("""
                        UPDATE crm_whatsapp_conversation
                        SET attention_queue = 'AGENT_ACTIVE',
                            waiting_reason = NULL,
                            waiting_detail = NULL
                        WHERE assigned_user_id IS NULL
                          AND status <> 'RESUELTO'
                          AND (attention_queue = 'AI_ACTIVE'%s)
                        """.formatted(legacyModeCondition));

                dropForeignKeysForColumn(connection, statement, "crm_whatsapp_payment_request", "id_ai_sale_draft");
                dropColumnIfPresent(connection, statement, "crm_whatsapp_payment_request", "id_ai_sale_draft");
                dropColumnIfPresent(connection, statement, "crm_whatsapp_payment_request", "ai_sale_draft_version");

                statement.execute("SET FOREIGN_KEY_CHECKS = 0");
                try {
                    for (String table : RUNTIME_TABLES) statement.execute("DROP TABLE IF EXISTS " + table);
                } finally {
                    statement.execute("SET FOREIGN_KEY_CHECKS = 1");
                }

                dropColumnIfPresent(connection, statement, "crm_whatsapp_conversation", "ai_attention_mode");
                dropColumnIfPresent(connection, statement, "crm_whatsapp_conversation", "ai_attention_mode_explicit");
                for (String column : CONFIG_COLUMNS) {
                    dropForeignKeysForColumn(connection, statement, "crm_whatsapp_ai_config", column);
                    dropColumnIfPresent(connection, statement, "crm_whatsapp_ai_config", column);
                }
            }
        }
    }

    private boolean isLocalDatabase(String url) {
        String normalized = url == null ? "" : url.toLowerCase(Locale.ROOT);
        return normalized.contains("//localhost:")
                || normalized.contains("//127.0.0.1:")
                || normalized.contains("//[::1]:");
    }

    private void dropForeignKeysForColumn(
            Connection connection, Statement statement, String table, String column) throws Exception {
        Set<String> names = new LinkedHashSet<>();
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet keys = metadata.getImportedKeys(connection.getCatalog(), null, table)) {
            while (keys.next()) {
                if (column.equalsIgnoreCase(keys.getString("FKCOLUMN_NAME"))) {
                    String name = keys.getString("FK_NAME");
                    if (name != null && !name.isBlank()) names.add(name);
                }
            }
        }
        for (String name : names) statement.execute("ALTER TABLE " + table + " DROP FOREIGN KEY `" + name + "`");
    }

    private void dropColumnIfPresent(
            Connection connection, Statement statement, String table, String column) throws Exception {
        if (columnExists(connection, table, column)) {
            statement.execute("ALTER TABLE " + table + " DROP COLUMN " + column);
        }
    }

    private boolean columnExists(Connection connection, String table, String column) throws Exception {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet columns = metadata.getColumns(connection.getCatalog(), null, table, column)) {
            return columns.next();
        }
    }
}
