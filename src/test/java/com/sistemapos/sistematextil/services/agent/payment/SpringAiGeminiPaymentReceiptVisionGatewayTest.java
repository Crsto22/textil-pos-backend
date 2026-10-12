package com.sistemapos.sistematextil.services.agent.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

import com.sistemapos.sistematextil.services.CrmWhatsappAiCredentialService;

class SpringAiGeminiPaymentReceiptVisionGatewayTest {

    private final SpringAiGeminiPaymentReceiptVisionGateway gateway =
            new SpringAiGeminiPaymentReceiptVisionGateway(
                    mock(CrmWhatsappAiCredentialService.class), 60_000, 1_200);

    @Test
    void acceptsMissingOptionalOcrTextWithoutLeavingNulls() {
        AgentPaymentReceiptExtraction normalized = gateway.normalize(
                new AgentPaymentReceiptExtraction(true, "75.00", " PEN ", null, null,
                        null, null, " 1254873692 ", "YAPE", null, 95));

        assertEquals("75.00", normalized.amount());
        assertEquals("PEN", normalized.currency());
        assertEquals("", normalized.operationDate());
        assertEquals("", normalized.operationTime());
        assertEquals("", normalized.recipient());
        assertEquals("", normalized.destinationAccount());
        assertEquals("1254873692", normalized.operationNumber());
        assertEquals("", normalized.visibleText());
    }

    @Test
    void preservesNullExtractionForTheExistingProviderValidation() {
        assertNull(gateway.normalize(null));
    }
}
