package com.sistemapos.sistematextil.services;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import com.sistemapos.sistematextil.repositories.CrmWhatsappAiJobRepository;

class CrmWhatsappAiJobRunnerTest {

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void unDeadlockAlTomarTrabajoNoInterrumpeElWorker() {
        CrmWhatsappAiJobRepository repository = mock(CrmWhatsappAiJobRepository.class);
        CrmWhatsappAiEngineService engine = mock(CrmWhatsappAiEngineService.class);
        TransactionTemplate transactions = mock(TransactionTemplate.class);
        CrmWhatsappAiJobRunner runner = new CrmWhatsappAiJobRunner(repository, engine, transactions);
        when(repository.findReadyJobIds(any(), any(), any(Pageable.class))).thenReturn(List.of(47L));
        when(transactions.execute(any(TransactionCallback.class)))
                .thenThrow(new CannotAcquireLockException("deadlock"));

        assertDoesNotThrow(runner::processQueue);

        verify(transactions, times(3)).execute(any(TransactionCallback.class));
        verifyNoInteractions(engine);
    }
}
