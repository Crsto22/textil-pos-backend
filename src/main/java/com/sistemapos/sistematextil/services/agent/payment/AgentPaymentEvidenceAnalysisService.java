package com.sistemapos.sistematextil.services.agent.payment;

import org.springframework.stereotype.Service;

import com.sistemapos.sistematextil.services.S3StorageService;
import com.sistemapos.sistematextil.services.agent.payment.AgentPaymentEvidenceAnalysisStore.AnalysisResult;
import com.sistemapos.sistematextil.services.agent.payment.AgentPaymentEvidenceAnalysisStore.ClaimedEvidence;
import com.sistemapos.sistematextil.services.agent.payment.AgentPaymentReceiptVisionGateway.VisionResult;
import com.sistemapos.sistematextil.services.ai.AiProviderException;

@Service
public class AgentPaymentEvidenceAnalysisService {
    private static final int MAX_FILE_BYTES = 10 * 1024 * 1024;

    private final AgentPaymentEvidenceAnalysisStore store;
    private final AgentPaymentReceiptVisionGateway visionGateway;
    private final S3StorageService storageService;

    public AgentPaymentEvidenceAnalysisService(
            AgentPaymentEvidenceAnalysisStore store,
            AgentPaymentReceiptVisionGateway visionGateway,
            S3StorageService storageService) {
        this.store = store;
        this.visionGateway = visionGateway;
        this.storageService = storageService;
    }

    public AnalysisResult process(Long evidenceId) {
        ClaimedEvidence claimed = store.claim(evidenceId);
        if (claimed == null) return null;
        try {
            byte[] content = storageService.readBytes(claimed.storagePath());
            if (content.length == 0 || content.length > MAX_FILE_BYTES) {
                throw new IllegalArgumentException("El comprobante esta vacio o supera 10 MB");
            }
            VisionResult vision = visionGateway.extract(claimed.connectionId(), content, claimed.mimeType());
            return store.complete(claimed.evidenceId(), vision);
        } catch (AiProviderException error) {
            return store.fail(claimed.evidenceId(), error, error.isRetryable());
        } catch (RuntimeException error) {
            return store.fail(claimed.evidenceId(), error, false);
        }
    }
}
