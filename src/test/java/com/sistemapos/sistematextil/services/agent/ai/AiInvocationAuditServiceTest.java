package com.sistemapos.sistematextil.services.agent.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class AiInvocationAuditServiceTest {
    @Test
    void fingerprintsCredentialsIrreversiblyAndDeterministically() {
        String first = AiInvocationAuditService.fingerprint("secret-key");
        assertEquals(64, first.length());
        assertEquals(first, AiInvocationAuditService.fingerprint("secret-key"));
        assertNotEquals("secret-key", first);
        assertNotEquals(first, AiInvocationAuditService.fingerprint("another-key"));
        assertNull(AiInvocationAuditService.fingerprint(""));
    }
}
