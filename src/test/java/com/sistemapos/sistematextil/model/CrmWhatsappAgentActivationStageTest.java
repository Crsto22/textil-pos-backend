package com.sistemapos.sistematextil.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CrmWhatsappAgentActivationStageTest {
    @Test
    void advancesOnlyOneStageButCanRollbackImmediately() {
        assertTrue(CrmWhatsappAgentActivationStage.LOCAL_TEST
                .allowsTransitionTo(CrmWhatsappAgentActivationStage.WHATSAPP_TEST));
        assertFalse(CrmWhatsappAgentActivationStage.LOCAL_TEST
                .allowsTransitionTo(CrmWhatsappAgentActivationStage.LIMITED));
        assertTrue(CrmWhatsappAgentActivationStage.AUTOMATIC_SALES
                .allowsTransitionTo(CrmWhatsappAgentActivationStage.LOCAL_TEST));
    }

    @Test
    void onlyFinalStageCreatesSales() {
        assertFalse(CrmWhatsappAgentActivationStage.LIMITED.createsAutomaticSales());
        assertTrue(CrmWhatsappAgentActivationStage.AUTOMATIC_SALES.createsAutomaticSales());
    }
}
