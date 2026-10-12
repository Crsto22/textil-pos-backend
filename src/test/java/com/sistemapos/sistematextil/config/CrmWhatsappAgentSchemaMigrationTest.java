package com.sistemapos.sistematextil.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CrmWhatsappAgentSchemaMigrationTest {

    @Test
    void acceptsOnlyLoopbackMysqlUrls() {
        assertTrue(CrmWhatsappAgentSchemaMigration.isLocalDatabase(
                "jdbc:mysql://localhost:3306/kiments"));
        assertTrue(CrmWhatsappAgentSchemaMigration.isLocalDatabase(
                "jdbc:mysql://127.0.0.1:3306/kiments?useSSL=false"));
        assertTrue(CrmWhatsappAgentSchemaMigration.isLocalDatabase(
                "jdbc:mysql://[::1]:3306/kiments"));

        assertFalse(CrmWhatsappAgentSchemaMigration.isLocalDatabase(
                "jdbc:mysql://db.production.internal:3306/kiments"));
        assertFalse(CrmWhatsappAgentSchemaMigration.isLocalDatabase(
                "jdbc:mysql://10.0.0.20:3306/kiments"));
        assertFalse(CrmWhatsappAgentSchemaMigration.isLocalDatabase(null));
    }
}
