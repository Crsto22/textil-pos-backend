package com.sistemapos.sistematextil.services;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;

class CrmWhatsappAiKnowledgeIndexRunnerTest {

    @Test
    void waitsUntilApplicationIsReadyBeforeQueryingKnowledgeTables() {
        CrmWhatsappAiKnowledgeService knowledge = mock(CrmWhatsappAiKnowledgeService.class);
        CrmWhatsappAiKnowledgeIndexRunner runner = new CrmWhatsappAiKnowledgeIndexRunner(knowledge);

        runner.indexPendingArticles();
        verifyNoInteractions(knowledge);

        runner.onApplicationReady();
        runner.indexPendingArticles();
        verify(knowledge).indexNextBatch();
    }
}
