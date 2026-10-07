package com.sistemapos.sistematextil.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sistemapos.sistematextil.model.CrmWhatsappAiKnowledgeArticle;
import com.sistemapos.sistematextil.model.CrmWhatsappAiKnowledgeCategory;
import com.sistemapos.sistematextil.model.CrmWhatsappAiKnowledgeChunk;
import com.sistemapos.sistematextil.model.CrmWhatsappConnection;
import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAiKnowledgeArticleRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAiKnowledgeChunkRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappConnectionRepository;
import com.sistemapos.sistematextil.repositories.UsuarioRepository;
import com.sistemapos.sistematextil.services.ai.AiEmbeddingProvider;
import com.sistemapos.sistematextil.services.ai.AiModelProvider;

@ExtendWith(MockitoExtension.class)
class CrmWhatsappAiKnowledgeServiceTest {

    @Mock private CrmWhatsappAiKnowledgeArticleRepository articleRepository;
    @Mock private CrmWhatsappAiKnowledgeChunkRepository chunkRepository;
    @Mock private CrmWhatsappAiCredentialService credentialService;
    @Mock private CrmWhatsappConnectionRepository connectionRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private CrmWhatsappAiAuditService auditService;
    @Mock private AiEmbeddingProvider embeddingProvider;
    @Mock private AiModelProvider modelProvider;

    @InjectMocks private CrmWhatsappAiKnowledgeService service;

    @Test
    void keywordMatchAvoidsEmbeddingAndProtectsShippingPrice() {
        CrmWhatsappAiKnowledgeArticle article = new CrmWhatsappAiKnowledgeArticle();
        article.setIdKnowledgeArticle(7L);
        article.setTitle("Informacion de envios");
        article.setCategory(CrmWhatsappAiKnowledgeCategory.ENVIOS);
        article.setKeywords("envio, delivery, costo");

        CrmWhatsappAiKnowledgeChunk chunk = new CrmWhatsappAiKnowledgeChunk();
        chunk.setIdKnowledgeChunk(11L);
        chunk.setArticle(article);
        chunk.setContent("Realizamos envios a provincias. Una referencia anterior fue S/15, pero el costo lo confirma el personal encargado.");

        when(chunkRepository.findSearchable(eq(3L), anyList())).thenReturn(List.of(chunk));

        var result = service.search(3L, "Cuanto cuesta el envio a provincia?");

        assertThat(result.shippingPriceRequiresAdvisor()).isTrue();
        assertThat(result.sources()).hasSize(1);
        assertThat(result.sources().getFirst().articleId()).isEqualTo(7L);
        var modelResult = service.modelResult(result);
        assertThat(modelResult.get("shippingPriceRequiresAdvisor")).isEqualTo(true);
        assertThat(modelResult.toString()).doesNotContain("S/15").contains("costo por confirmar");
        verify(embeddingProvider, never()).embedQuery(eq(3L), eq("Cuanto cuesta el envio a provincia?"));
    }

    @Test
    void returnsEmptyResultWhenNoKnowledgeIsPublished() {
        when(chunkRepository.findSearchable(eq(9L), anyList())).thenReturn(List.of());

        var result = service.search(9L, "Cual es la politica de cambios?");

        assertThat(result.sources()).isEmpty();
        assertThat(result.shippingPriceRequiresAdvisor()).isFalse();
    }

    @Test
    void noUsaCuidadosComoRespuestaCuandoPreguntanPorMaterial() {
        CrmWhatsappAiKnowledgeArticle article = new CrmWhatsappAiKnowledgeArticle();
        article.setIdKnowledgeArticle(8L);
        article.setTitle("Cuidados de las prendas");
        article.setCategory(CrmWhatsappAiKnowledgeCategory.CUIDADOS);
        article.setKeywords("lavado, secado, planchado, prendas");

        CrmWhatsappAiKnowledgeChunk chunk = new CrmWhatsappAiKnowledgeChunk();
        chunk.setIdKnowledgeChunk(12L);
        chunk.setArticle(article);
        chunk.setContent("Lava las prendas a mano con agua fria y secalas a la sombra.");
        chunk.setEmbeddingJson("[0.0,1.0]");
        when(chunkRepository.findSearchable(eq(4L), anyList())).thenReturn(List.of(chunk));
        when(embeddingProvider.embedQuery(4L, "Que material son las prendas")).thenReturn(List.of(1.0f, 0.0f));

        var result = service.search(4L, "Que material son las prendas");

        assertThat(result.sources()).isEmpty();
    }

    @Test
    void eliminarArticuloRetiraSusFragmentosYNoPermiteQueElIndexadorLoReactive() {
        Usuario user = new Usuario();
        user.setIdUsuario(5);
        CrmWhatsappConnection connection = new CrmWhatsappConnection();
        connection.setIdConnection(3L);
        CrmWhatsappAiKnowledgeArticle article = new CrmWhatsappAiKnowledgeArticle();
        article.setIdKnowledgeArticle(7L);
        article.setConnection(connection);
        when(credentialService.requireAdminConnectionId(user)).thenReturn(3L);
        when(connectionRepository.findById(3L)).thenReturn(Optional.of(connection));
        when(usuarioRepository.findByIdUsuarioAndDeletedAtIsNull(5)).thenReturn(Optional.of(user));
        when(articleRepository.findByIdKnowledgeArticleAndConnection_IdConnectionAndDeletedAtIsNull(7L, 3L))
                .thenReturn(Optional.of(article));
        when(articleRepository.softDelete(eq(7L), eq(3L), any(),
                eq(com.sistemapos.sistematextil.model.CrmWhatsappAiKnowledgeStatus.BORRADOR))).thenReturn(1);

        service.delete(7L, user);

        verify(articleRepository).softDelete(eq(7L), eq(3L), any(),
                eq(com.sistemapos.sistematextil.model.CrmWhatsappAiKnowledgeStatus.BORRADOR));
        verify(chunkRepository).deleteAllByArticleId(7L);
    }
}
