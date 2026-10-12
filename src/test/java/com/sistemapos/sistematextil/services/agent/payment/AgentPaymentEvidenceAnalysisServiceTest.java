package com.sistemapos.sistematextil.services.agent.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.sistemapos.sistematextil.services.S3StorageService;
import com.sistemapos.sistematextil.services.agent.payment.AgentPaymentEvidenceAnalysisStore.AnalysisResult;
import com.sistemapos.sistematextil.services.agent.payment.AgentPaymentEvidenceAnalysisStore.ClaimedEvidence;
import com.sistemapos.sistematextil.services.agent.payment.AgentPaymentReceiptVisionGateway.VisionResult;
import com.sistemapos.sistematextil.services.ai.AiProviderException;

class AgentPaymentEvidenceAnalysisServiceTest {
    @Test
    void delegatesAValidFileToVisionOutsideTheTransactionalStore() {
        AgentPaymentEvidenceAnalysisStore store = mock(AgentPaymentEvidenceAnalysisStore.class);
        AgentPaymentReceiptVisionGateway gateway = mock(AgentPaymentReceiptVisionGateway.class);
        S3StorageService storage = mock(S3StorageService.class);
        AgentPaymentEvidenceAnalysisService service = new AgentPaymentEvidenceAnalysisService(store, gateway, storage);
        ClaimedEvidence claimed = new ClaimedEvidence(91L, 7L, "/storage/proof.png", "image/png", 1);
        byte[] bytes = new byte[] { 1, 2, 3 };
        VisionResult vision = new VisionResult("GEMINI", "model", "TEST", 10L,
                new AgentPaymentReceiptExtraction(true, "10.00", "PEN", "2026-10-08", "14:00:00",
                        "Kiments", "999", "123", "YAPE", "pago", 99));
        AnalysisResult completed = new AnalysisResult(91L, AgentPaymentReceiptOutcome.APPROVED_INITIAL,
                "ACEPTABLE", java.util.List.of(), "ESPERANDO_METODO_ENTREGA");
        when(store.claim(91L)).thenReturn(claimed);
        when(storage.readBytes("/storage/proof.png")).thenReturn(bytes);
        when(gateway.extract(7L, bytes, "image/png")).thenReturn(vision);
        when(store.complete(91L, vision)).thenReturn(completed);

        assertEquals(completed, service.process(91L));
        verify(store).complete(91L, vision);
    }

    @Test
    void schedulesRetryOnlyForRetryableProviderFailures() {
        AgentPaymentEvidenceAnalysisStore store = mock(AgentPaymentEvidenceAnalysisStore.class);
        AgentPaymentReceiptVisionGateway gateway = mock(AgentPaymentReceiptVisionGateway.class);
        S3StorageService storage = mock(S3StorageService.class);
        AgentPaymentEvidenceAnalysisService service = new AgentPaymentEvidenceAnalysisService(store, gateway, storage);
        ClaimedEvidence claimed = new ClaimedEvidence(91L, 7L, "/storage/proof.png", "image/png", 1);
        byte[] bytes = new byte[] { 1 };
        AiProviderException failure = new AiProviderException("timeout", true);
        when(store.claim(91L)).thenReturn(claimed);
        when(storage.readBytes("/storage/proof.png")).thenReturn(bytes);
        when(gateway.extract(7L, bytes, "image/png")).thenThrow(failure);

        service.process(91L);

        verify(store).fail(91L, failure, true);
    }
}
