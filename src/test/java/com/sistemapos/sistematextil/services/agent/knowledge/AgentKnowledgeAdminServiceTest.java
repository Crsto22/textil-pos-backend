package com.sistemapos.sistematextil.services.agent.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sistemapos.sistematextil.model.CrmWhatsappAiKnowledgeArticle;
import com.sistemapos.sistematextil.model.CrmWhatsappAiKnowledgeChunk;
import com.sistemapos.sistematextil.model.CrmWhatsappAiKnowledgeStatus;
import com.sistemapos.sistematextil.model.CrmWhatsappConnection;
import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAiKnowledgeArticleRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAiKnowledgeChunkRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappConnectionRepository;
import com.sistemapos.sistematextil.services.CrmWhatsappAiCredentialService;
import com.sistemapos.sistematextil.services.agent.knowledge.AgentKnowledgeAdminService.ArticleRequest;

class AgentKnowledgeAdminServiceTest {
    private CrmWhatsappAiCredentialService credentials;
    private CrmWhatsappConnectionRepository connections;
    private CrmWhatsappAiKnowledgeArticleRepository articles;
    private CrmWhatsappAiKnowledgeChunkRepository chunks;
    private AgentKnowledgeEmbeddingGateway embeddings;
    private AgentKnowledgeAdminService service;
    private Usuario actor;

    @BeforeEach
    void setUp() {
        credentials = mock(CrmWhatsappAiCredentialService.class);
        connections = mock(CrmWhatsappConnectionRepository.class);
        articles = mock(CrmWhatsappAiKnowledgeArticleRepository.class);
        chunks = mock(CrmWhatsappAiKnowledgeChunkRepository.class);
        embeddings = mock(AgentKnowledgeEmbeddingGateway.class);
        actor = mock(Usuario.class);
        when(credentials.requireAdminConnectionId(actor)).thenReturn(7L);
        CrmWhatsappConnection connection = new CrmWhatsappConnection();
        connection.setIdConnection(7L);
        when(connections.findById(7L)).thenReturn(Optional.of(connection));
        when(articles.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(articles.saveAndFlush(any())).thenAnswer(invocation -> {
            CrmWhatsappAiKnowledgeArticle article = invocation.getArgument(0);
            article.setIdKnowledgeArticle(11L);
            return article;
        });
        service = new AgentKnowledgeAdminService(credentials, connections, articles, chunks, embeddings,
                mock(AgentKnowledgeRetriever.class));
    }

    @Test
    void savesDraftWithoutCallingGemini() {
        var result = service.save(new ArticleRequest(null, "Cambios", "POLITICAS",
                "No se realizan cambios de talla.", "cambio,talla", false), actor);

        assertFalse(result.active());
        assertEquals("BORRADOR", result.status());
        verify(embeddings, never()).embedDocument(any(), any());
    }

    @Test
    void publishesOnlyAfterCreatingDocumentEmbeddings() {
        when(credentials.hasUsableCredential(7L)).thenReturn(true);
        when(embeddings.embedDocument(any(), any())).thenReturn(List.of(0.1f, 0.2f, 0.3f));

        var result = service.save(new ArticleRequest(null, "Horarios", "HORARIOS",
                "Atendemos de lunes a sábado de nueve de la mañana a seis de la tarde.", null, true), actor);

        assertTrue(result.active());
        assertEquals("ACTIVO", result.status());
        assertEquals(1, result.version());
        verify(chunks).save(any(CrmWhatsappAiKnowledgeChunk.class));
        verify(chunks).deleteOtherVersions(11L, 1);
    }
}
