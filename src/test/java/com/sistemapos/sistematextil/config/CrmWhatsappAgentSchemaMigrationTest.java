package com.sistemapos.sistematextil.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CrmWhatsappAgentSchemaMigrationTest {

    @Test
    void acceptsLocalAndRemoteMysqlUrls() {
        assertTrue(CrmWhatsappAgentSchemaMigration.isMysqlDatabase(
                "jdbc:mysql://localhost:3306/kiments"));
        assertTrue(CrmWhatsappAgentSchemaMigration.isMysqlDatabase(
                "jdbc:mysql://127.0.0.1:3306/kiments?useSSL=false"));
        assertTrue(CrmWhatsappAgentSchemaMigration.isMysqlDatabase(
                "jdbc:mysql://[::1]:3306/kiments"));
        assertTrue(CrmWhatsappAgentSchemaMigration.isMysqlDatabase(
                "jdbc:mysql://db.production.internal:3306/kiments"));
        assertTrue(CrmWhatsappAgentSchemaMigration.isMysqlDatabase(
                "jdbc:mysql://10.0.0.20:3306/kiments"));

        assertFalse(CrmWhatsappAgentSchemaMigration.isMysqlDatabase(
                "jdbc:postgresql://db.production.internal:5432/kiments"));
        assertFalse(CrmWhatsappAgentSchemaMigration.isMysqlDatabase(null));
    }
}
