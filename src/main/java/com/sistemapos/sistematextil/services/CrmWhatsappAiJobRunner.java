package com.sistemapos.sistematextil.services;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.sistemapos.sistematextil.repositories.CrmWhatsappAiJobRepository;
import com.sistemapos.sistematextil.services.CrmWhatsappAiEngineService.PreparedJob;
import com.sistemapos.sistematextil.services.CrmWhatsappAiEngineService.ProcessingResult;
import com.sistemapos.sistematextil.services.ai.AiProviderException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CrmWhatsappAiJobRunner {

    private static final Logger log = LoggerFactory.getLogger(CrmWhatsappAiJobRunner.class);

    private final CrmWhatsappAiJobRepository jobRepository;
    private final CrmWhatsappAiEngineService engineService;
    private final TransactionTemplate transactionTemplate;

    @Value("${crm.whatsapp.ai.worker.batch-size:5}")
    private int batchSize;

    @Value("${crm.whatsapp.ai.worker.lock-minutes:5}")
    private int lockMinutes;

    @Scheduled(fixedDelayString = "${crm.whatsapp.ai.worker.fixed-delay-ms:2000}")
    public void processQueue() {
        LocalDateTime now = LocalDateTime.now();
        List<Long> ids;
        try {
            ids = jobRepository.findReadyJobIds(now, now.minusMinutes(lockMinutes),
                    PageRequest.of(0, Math.max(1, Math.min(batchSize, 20))));
        } catch (TransientDataAccessException error) {
            log.debug("La cola IA esta ocupada; se intentara nuevamente en el siguiente ciclo: {}", error.getMessage());
            return;
        } catch (RuntimeException error) {
            if (missingTable(error)) return;
            throw error;
        }
        for (Long id : ids) processOne(id);
    }

    private void processOne(Long id) {
        LocalDateTime now = LocalDateTime.now();
        Integer claimed = claimWithRetry(id, now);
        if (claimed == null || claimed == 0) return;
        try {
            engineService.notifyProcessing(id);
            PreparedJob prepared = engineService.prepare(id);
            ProcessingResult result = engineService.execute(prepared);
            engineService.complete(id, result);
        } catch (AiProviderException error) {
            log.warn("Gemini no pudo procesar el trabajo IA {}: {}. Causa tecnica: {}",
                    id, error.getMessage(), rootCauseSummary(error));
            engineService.fail(id, error, error.isRetryable());
        } catch (RuntimeException error) {
            if (missingJob(error)) {
                log.info("Trabajo IA {} descartado porque su mensaje o conversacion ya no existe", id);
                return;
            }
            log.error("Error procesando trabajo IA {}: {}", id, error.getMessage(), error);
            engineService.fail(id, error, false);
        }
    }

    private Integer claimWithRetry(Long id, LocalDateTime now) {
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                return transactionTemplate.execute(status -> jobRepository.claim(
                        id, now, now.minusMinutes(lockMinutes)));
            } catch (TransientDataAccessException error) {
                if (attempt == 3) {
                    log.debug("Trabajo IA {} ocupado tras {} intentos; queda pendiente para el siguiente ciclo",
                            id, attempt);
                    return 0;
                }
                try {
                    Thread.sleep(25L * attempt);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return 0;
                }
            }
        }
        return 0;
    }

    private boolean missingTable(Throwable error) {
        Throwable current = error;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.toLowerCase().contains("crm_whatsapp_ai_job")
                    && (message.toLowerCase().contains("doesn't exist")
                        || message.toLowerCase().contains("does not exist"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean missingJob(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof IllegalStateException
                    && "Trabajo de IA no encontrado".equals(current.getMessage())) return true;
            current = current.getCause();
        }
        return false;
    }

    private String rootCauseSummary(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage();
        if (message == null || message.isBlank()) {
            return root.getClass().getSimpleName();
        }
        String singleLine = message.replace('\r', ' ').replace('\n', ' ').trim();
        if (singleLine.length() > 300) {
            singleLine = singleLine.substring(0, 300) + "...";
        }
        return root.getClass().getSimpleName() + ": " + singleLine;
    }
}
