package com.sistemapos.sistematextil.services.agent.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sistemapos.sistematextil.model.CrmWhatsappAiKnowledgeArticle;
import com.sistemapos.sistematextil.model.CrmWhatsappAiKnowledgeCategory;
import com.sistemapos.sistematextil.model.CrmWhatsappAiKnowledgeChunk;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAiKnowledgeChunkRepository;
import com.sistemapos.sistematextil.services.agent.catalog.AgentCatalogScopeResolver;
import com.sistemapos.sistematextil.services.agent.catalog.AgentCatalogScopeResolver.CatalogScope;

@ExtendWith(MockitoExtension.class)
class AgentKnowledgeRetrieverTest {
    @Mock CrmWhatsappAiKnowledgeChunkRepository chunkRepository;
    @Mock AgentCatalogScopeResolver scopeResolver;
    @Mock AgentKnowledgeEmbeddingGateway embeddingGateway;

    private AgentKnowledgeRetriever retriever;

    @BeforeEach
    void setUp() {
        retriever = new AgentKnowledgeRetriever(
                chunkRepository,
                scopeResolver,
                embeddingGateway,
                new ObjectMapper());
    }

    @Test
    void retrievesSemanticInformationalContentAndSanitizesMonetaryValues() {
        when(scopeResolver.resolve(33L)).thenReturn(new CatalogScope(33L, 7L, 2));
        when(embeddingGateway.embedQuery(7L, "¿Cuándo puedo visitarlas?"))
                .thenReturn(List.of(1f, 0f, 0f));
        when(chunkRepository.findSearchableByCategories(eq(7L), anyList(), anyList()))
                .thenReturn(List.of(chunk(91L, CrmWhatsappAiKnowledgeCategory.HORARIOS,
                        "Atendemos de lunes a sábado. Referencia S/20.00", "[1.0,0.0,0.0]")));

        var result = retriever.searchForSession(33L, "¿Cuándo puedo visitarlas?");

        assertTrue(result.eligible());
        assertEquals("INFORMATION_FOUND", result.reason());
        assertEquals(1, result.sources().size());
        assertEquals("HORARIOS", result.sources().getFirst().category());
        assertTrue(result.sources().getFirst().content().contains("[dato comercial omitido]"));
        assertFalse(result.sources().getFirst().content().contains("S/20"));
    }

    @Test
    void commercialQuestionNeverReadsRagOrRequestsAnEmbedding() {
        when(scopeResolver.resolve(33L)).thenReturn(new CatalogScope(33L, 7L, 2));

        var result = retriever.searchForSession(33L, "¿Cuál es el precio y stock de Emma?");

        assertFalse(result.eligible());
        assertEquals("COMMERCIAL_DATA_REQUIRES_VERIFIED_TOOLS", result.reason());
        assertTrue(result.sources().isEmpty());
        verifyNoInteractions(chunkRepository, embeddingGateway);
    }

    @Test
    void fallsBackToLexicalRetrievalWhenEmbeddingProviderFails() {
        when(embeddingGateway.embedQuery(7L, "¿Cuál es el horario de atención?"))
                .thenThrow(new IllegalStateException("provider unavailable"));
        when(chunkRepository.findSearchableByCategories(eq(7L), anyList(), anyList()))
                .thenReturn(List.of(chunk(92L, CrmWhatsappAiKnowledgeCategory.HORARIOS,
                        "Nuestro horario de atención es de lunes a sábado.", "[]")));

        var result = retriever.search(7L, "¿Cuál es el horario de atención?");

        assertTrue(result.eligible());
        assertEquals("INFORMATION_FOUND", result.reason());
        assertEquals(92L, result.sources().getFirst().chunkId());
    }

    @Test
    @SuppressWarnings("unchecked")
    void repositoryReceivesOnlyTheInformationalAllowlist() {
        when(chunkRepository.findSearchableByCategories(eq(7L), anyList(), anyList()))
                .thenReturn(List.of());

        retriever.search(7L, "¿Dónde están ubicados?");

        ArgumentCaptor<List<CrmWhatsappAiKnowledgeCategory>> categories = ArgumentCaptor.forClass(List.class);
        verify(chunkRepository).findSearchableByCategories(eq(7L), anyList(), categories.capture());
        assertEquals(AgentKnowledgeRetriever.allowedCategories(), Set.copyOf(categories.getValue()));
        assertFalse(categories.getValue().contains(CrmWhatsappAiKnowledgeCategory.TIENDAS));
        assertFalse(categories.getValue().contains(CrmWhatsappAiKnowledgeCategory.INSTITUCIONAL));
        verifyNoInteractions(embeddingGateway);
    }

    private CrmWhatsappAiKnowledgeChunk chunk(
            Long id,
            CrmWhatsappAiKnowledgeCategory category,
            String content,
            String embeddingJson) {
        CrmWhatsappAiKnowledgeArticle article = new CrmWhatsappAiKnowledgeArticle();
        article.setIdKnowledgeArticle(id + 1000);
        article.setTitle("Información " + category.name());
        article.setCategory(category);
        article.setKeywords(category.name());
        CrmWhatsappAiKnowledgeChunk chunk = new CrmWhatsappAiKnowledgeChunk();
        chunk.setIdKnowledgeChunk(id);
        chunk.setArticle(article);
        chunk.setContent(content);
        chunk.setEmbeddingJson(embeddingJson);
        return chunk;
    }
}
