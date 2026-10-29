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
@Order(Ordered.LOWEST_PRECEDENCE - 20)
public class CrmWhatsappSpecialMessageMigration implements ApplicationRunner {
    private final DataSource dataSource;

    public CrmWhatsappSpecialMessageMigration(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            addColumn(connection, statement, "location_latitude", "DOUBLE NULL");
            addColumn(connection, statement, "location_longitude", "DOUBLE NULL");
            addColumn(connection, statement, "location_name", "VARCHAR(255) NULL");
            addColumn(connection, statement, "location_address", "VARCHAR(500) NULL");

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS crm_whatsapp_message_reaction (
                      id_reaction BIGINT NOT NULL AUTO_INCREMENT,
                      id_message BIGINT NOT NULL,
                      direction VARCHAR(20) NOT NULL,
                      emoji VARCHAR(32) NOT NULL,
                      whatsapp_reaction_id VARCHAR(120) NULL,
                      event_at DATETIME NULL,
                      deleted_at DATETIME NULL,
                      created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      PRIMARY KEY (id_reaction),
                      UNIQUE KEY uk_crm_message_reaction_direction (id_message, direction),
                      KEY idx_crm_message_reaction_active (id_message, deleted_at),
                      CONSTRAINT fk_crm_message_reaction_message FOREIGN KEY (id_message)
                        REFERENCES crm_whatsapp_message (id_message) ON DELETE CASCADE
                    )
                    """);
            if (!columnExists(connection, "crm_whatsapp_message_reaction", "event_at")) {
                statement.execute("ALTER TABLE crm_whatsapp_message_reaction ADD COLUMN event_at DATETIME NULL");
            }

            statement.executeUpdate("""
                    UPDATE crm_whatsapp_message
                    SET message_type = 'LOCATION',
                        body = COALESCE(NULLIF(body, ''), 'Ubicación compartida'),
                        location_latitude = CAST(JSON_UNQUOTE(JSON_EXTRACT(baileys_message_json,
                          '$.message.locationMessage.degreesLatitude')) AS DECIMAL(12,8)),
                        location_longitude = CAST(JSON_UNQUOTE(JSON_EXTRACT(baileys_message_json,
                          '$.message.locationMessage.degreesLongitude')) AS DECIMAL(12,8)),
                        location_name = NULLIF(JSON_UNQUOTE(JSON_EXTRACT(baileys_message_json,
                          '$.message.locationMessage.name')), 'null'),
                        location_address = NULLIF(JSON_UNQUOTE(JSON_EXTRACT(baileys_message_json,
                          '$.message.locationMessage.address')), 'null')
                    WHERE deleted_at IS NULL
                      AND JSON_EXTRACT(baileys_message_json, '$.message.locationMessage') IS NOT NULL
                    """);

            statement.executeUpdate("""
                    UPDATE crm_whatsapp_message
                    SET message_type = 'UNSUPPORTED', body = 'Mensaje no compatible'
                    WHERE deleted_at IS NULL
                      AND JSON_EXTRACT(baileys_message_json, '$.message.secretEncryptedMessage') IS NOT NULL
                    """);
            statement.executeUpdate("""
                    UPDATE crm_whatsapp_message
                    SET message_type = 'UNSUPPORTED', body = 'Mensaje no compatible'
                    WHERE deleted_at IS NULL
                      AND message_type = 'TEXT'
                      AND COALESCE(TRIM(body), '') = ''
                      AND media_storage_path IS NULL
                      AND JSON_LENGTH(JSON_REMOVE(JSON_EXTRACT(baileys_message_json, '$.message'),
                        '$.messageContextInfo', '$.senderKeyDistributionMessage', '$.conversation')) > 0
                      AND JSON_EXTRACT(baileys_message_json, '$.message.protocolMessage') IS NULL
                      AND JSON_EXTRACT(baileys_message_json, '$.message.albumMessage') IS NULL
                      AND JSON_EXTRACT(baileys_message_json, '$.message.reactionMessage') IS NULL
                      AND JSON_EXTRACT(baileys_message_json, '$.message.locationMessage') IS NULL
                      AND JSON_EXTRACT(baileys_message_json, '$.message.extendedTextMessage') IS NULL
                      AND JSON_EXTRACT(baileys_message_json, '$.message.imageMessage') IS NULL
                      AND JSON_EXTRACT(baileys_message_json, '$.message.videoMessage') IS NULL
                      AND JSON_EXTRACT(baileys_message_json, '$.message.audioMessage') IS NULL
                      AND JSON_EXTRACT(baileys_message_json, '$.message.documentMessage') IS NULL
                      AND JSON_EXTRACT(baileys_message_json, '$.message.stickerMessage') IS NULL
                      AND JSON_EXTRACT(baileys_message_json, '$.message.viewOnceMessage') IS NULL
                      AND JSON_EXTRACT(baileys_message_json, '$.message.viewOnceMessageV2') IS NULL
                      AND JSON_EXTRACT(baileys_message_json, '$.message.ephemeralMessage') IS NULL
                    """);

            statement.executeUpdate("""
                    INSERT INTO crm_whatsapp_message_reaction
                      (id_message, direction, emoji, whatsapp_reaction_id, event_at, deleted_at, created_at, updated_at)
                    SELECT target.id_message, source.direction,
                           COALESCE(JSON_UNQUOTE(JSON_EXTRACT(source.baileys_message_json,
                             '$.message.reactionMessage.text')), ''),
                           source.whatsapp_message_id, source.created_at,
                           CASE WHEN COALESCE(JSON_UNQUOTE(JSON_EXTRACT(source.baileys_message_json,
                             '$.message.reactionMessage.text')), '') = '' THEN source.created_at ELSE NULL END,
                           source.created_at, source.created_at
                    FROM crm_whatsapp_message source
                    JOIN crm_whatsapp_message target
                      ON target.whatsapp_message_id = JSON_UNQUOTE(JSON_EXTRACT(source.baileys_message_json,
                        '$.message.reactionMessage.key.id'))
                    WHERE JSON_EXTRACT(source.baileys_message_json, '$.message.reactionMessage') IS NOT NULL
                      AND NOT EXISTS (
                        SELECT 1 FROM crm_whatsapp_message newer
                        WHERE newer.id_message > source.id_message
                          AND newer.direction = source.direction
                          AND JSON_UNQUOTE(JSON_EXTRACT(newer.baileys_message_json,
                            '$.message.reactionMessage.key.id')) = target.whatsapp_message_id
                      )
                    ON DUPLICATE KEY UPDATE
                      emoji = VALUES(emoji), whatsapp_reaction_id = VALUES(whatsapp_reaction_id),
                      event_at = VALUES(event_at), deleted_at = VALUES(deleted_at), updated_at = VALUES(updated_at)
                    """);

            statement.execute("DROP TEMPORARY TABLE IF EXISTS tmp_crm_technical_messages");
            statement.execute("""
                    CREATE TEMPORARY TABLE tmp_crm_technical_messages AS
                    SELECT id_message, id_conversation, direction
                    FROM crm_whatsapp_message
                    WHERE deleted_at IS NULL AND (
                      JSON_UNQUOTE(JSON_EXTRACT(baileys_message_json, '$.message.protocolMessage.type'))
                        IN ('EPHEMERAL_SETTING', 'EPHEMERAL_SYNC_RESPONSE', '3', '4')
                      OR (JSON_EXTRACT(baileys_message_json, '$.message.albumMessage') IS NOT NULL
                          AND COALESCE(TRIM(body), '') = ''
                          AND media_storage_path IS NULL
                          AND message_type = 'TEXT')
                      OR JSON_EXTRACT(baileys_message_json, '$.message.reactionMessage') IS NOT NULL
                    )
                    """);
            statement.executeUpdate("""
                    UPDATE crm_whatsapp_message m JOIN tmp_crm_technical_messages t ON t.id_message = m.id_message
                    SET m.deleted_at = COALESCE(m.deleted_at, CURRENT_TIMESTAMP)
                    """);
            statement.executeUpdate("""
                    UPDATE crm_whatsapp_conversation c
                    JOIN (SELECT id_conversation, SUM(direction = 'INCOMING') removed
                          FROM tmp_crm_technical_messages GROUP BY id_conversation) x
                      ON x.id_conversation = c.id_conversation
                    SET c.unread_count = GREATEST(0, c.unread_count - x.removed)
                    """);
            statement.executeUpdate("""
                    UPDATE crm_whatsapp_conversation c
                    JOIN (SELECT DISTINCT id_conversation FROM tmp_crm_technical_messages) affected
                      ON affected.id_conversation = c.id_conversation
                    LEFT JOIN crm_whatsapp_message latest ON latest.id_message = (
                      SELECT m2.id_message FROM crm_whatsapp_message m2
                      WHERE m2.id_conversation = c.id_conversation AND m2.deleted_at IS NULL
                      ORDER BY m2.created_at DESC, m2.id_message DESC LIMIT 1
                    )
                    SET c.last_message = latest.body,
                        c.last_message_type = latest.message_type,
                        c.last_message_at = latest.created_at
                    """);
            statement.execute("DROP TEMPORARY TABLE IF EXISTS tmp_crm_technical_messages");
        }
    }

    private void addColumn(Connection connection, Statement statement, String column, String definition) throws Exception {
        if (!columnExists(connection, "crm_whatsapp_message", column)) {
            statement.execute("ALTER TABLE crm_whatsapp_message ADD COLUMN " + column + " " + definition);
        }
    }

    private boolean columnExists(Connection connection, String table, String column) throws Exception {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet result = metadata.getColumns(connection.getCatalog(), null, table, column)) {
            return result.next();
        }
    }
}
