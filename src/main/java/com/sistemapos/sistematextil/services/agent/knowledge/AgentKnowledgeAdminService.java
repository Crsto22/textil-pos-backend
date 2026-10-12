package com.sistemapos.sistematextil.services.agent.knowledge;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sistemapos.sistematextil.model.CrmWhatsappAiKnowledgeArticle;
import com.sistemapos.sistematextil.model.CrmWhatsappAiKnowledgeCategory;
import com.sistemapos.sistematextil.model.CrmWhatsappAiKnowledgeChunk;
import com.sistemapos.sistematextil.model.CrmWhatsappAiKnowledgeStatus;
import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAiKnowledgeArticleRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAiKnowledgeChunkRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappConnectionRepository;
import com.sistemapos.sistematextil.services.CrmWhatsappAiCredentialService;
import com.sistemapos.sistematextil.services.ai.AiProviderException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AgentKnowledgeAdminService {
    private static final int CHUNK_SIZE = 1200;
    private static final int CHUNK_OVERLAP = 160;
    private static final String EMBEDDING_MODEL = "gemini-embedding-001";

    private final CrmWhatsappAiCredentialService credentialService;
    private final CrmWhatsappConnectionRepository connectionRepository;
    private final CrmWhatsappAiKnowledgeArticleRepository articles;
    private final CrmWhatsappAiKnowledgeChunkRepository chunks;
    private final AgentKnowledgeEmbeddingGateway embeddings;
    private final AgentKnowledgeRetriever retriever;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional(readOnly = true)
    public List<ArticleResponse> list(Usuario actor) {
        Long connectionId = credentialService.requireAdminConnectionId(actor);
        return articles.findByConnection_IdConnectionAndDeletedAtIsNullOrderByUpdatedAtDesc(connectionId)
                .stream().map(this::response).toList();
    }

    @Transactional
    public ArticleResponse save(ArticleRequest request, Usuario actor) {
        if (request == null) throw badRequest("El conocimiento es obligatorio");
        Long connectionId = credentialService.requireAdminConnectionId(actor);
        CrmWhatsappAiKnowledgeArticle article = request.id() == null
                ? create(connectionId)
                : articles.findByIdKnowledgeArticleAndConnection_IdConnectionAndDeletedAtIsNull(
                        request.id(), connectionId).orElseThrow(() -> notFound("Conocimiento no encontrado"));
        article.setTitle(required(request.title(), 160, "El titulo es obligatorio"));
        article.setContent(required(request.content(), 30000, "El contenido es obligatorio"));
        article.setKeywords(optional(request.keywords(), 500));
        article.setCategory(parseCategory(request.category()));
        boolean active = request.active() == null || request.active();
        if (!active) {
            article.setStatus(CrmWhatsappAiKnowledgeStatus.BORRADOR);
            article.setLastError(null);
            return response(articles.save(article));
        }
        if (!credentialService.hasUsableCredential(connectionId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Configura la API key de Gemini antes de publicar conocimientos");
        }
        int version = Math.max(article.getActiveVersion() == null ? 0 : article.getActiveVersion(),
                article.getPendingVersion() == null ? 0 : article.getPendingVersion()) + 1;
        article.setPendingVersion(version);
        article.setStatus(CrmWhatsappAiKnowledgeStatus.INDEXANDO);
        article.setLastError(null);
        article = articles.saveAndFlush(article);
        try {
            chunks.deleteVersion(article.getIdKnowledgeArticle(), version);
            int order = 0;
            for (String part : split(article.getContent())) {
                CrmWhatsappAiKnowledgeChunk chunk = new CrmWhatsappAiKnowledgeChunk();
                chunk.setArticle(article);
                chunk.setArticleVersion(version);
                chunk.setChunkOrder(order++);
                chunk.setContent(part);
                chunk.setEmbeddingJson(writeEmbedding(embeddings.embedDocument(connectionId, part)));
                chunk.setEmbeddingModel(EMBEDDING_MODEL);
                chunks.save(chunk);
            }
            chunks.flush();
            article.setActiveVersion(version);
            article.setStatus(CrmWhatsappAiKnowledgeStatus.ACTIVO);
            article.setIndexedAt(LocalDateTime.now());
            article.setLastError(null);
            article = articles.save(article);
            chunks.deleteOtherVersions(article.getIdKnowledgeArticle(), version);
        } catch (AiProviderException | JsonProcessingException error) {
            chunks.deleteVersion(article.getIdKnowledgeArticle(), version);
            article.setStatus(CrmWhatsappAiKnowledgeStatus.ERROR);
            article.setLastError(limit(error.getMessage(), 500));
            article = articles.save(article);
        }
        return response(article);
    }

    @Transactional
    public void delete(Long articleId, Usuario actor) {
        Long connectionId = credentialService.requireAdminConnectionId(actor);
        int updated = articles.softDelete(articleId, connectionId, LocalDateTime.now(),
                CrmWhatsappAiKnowledgeStatus.BORRADOR);
        if (updated == 0) throw notFound("Conocimiento no encontrado");
        chunks.deleteAllByArticleId(articleId);
    }

    @Transactional(readOnly = true)
    public KnowledgeTestResponse test(KnowledgeTestRequest request, Usuario actor) {
        String question = required(request == null ? null : request.question(), 1000,
                "Escribe una pregunta para probar la base de conocimiento");
        Long connectionId = credentialService.requireAdminConnectionId(actor);
        var result = retriever.search(connectionId, question);
        return new KnowledgeTestResponse(result.eligible(), result.reason(), result.sources().stream()
                .map(source -> new KnowledgeTestSource(source.articleId(), source.title(),
                        source.category(), source.content(), source.score().doubleValue()))
                .toList());
    }

    private CrmWhatsappAiKnowledgeArticle create(Long connectionId) {
        CrmWhatsappAiKnowledgeArticle article = new CrmWhatsappAiKnowledgeArticle();
        article.setConnection(connectionRepository.findById(connectionId)
                .orElseThrow(() -> notFound("Conexion de WhatsApp no encontrada")));
        article.setSourceKey("manual-" + UUID.randomUUID());
        article.setActiveVersion(0);
        article.setPendingVersion(0);
        return article;
    }

    private List<String> split(String value) {
        String text = value.replace("\r\n", "\n").trim();
        List<String> result = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(text.length(), start + CHUNK_SIZE);
            if (end < text.length()) {
                int boundary = text.lastIndexOf('\n', end);
                if (boundary <= start + CHUNK_SIZE / 2) boundary = text.lastIndexOf(' ', end);
                if (boundary > start + CHUNK_SIZE / 2) end = boundary;
            }
            result.add(text.substring(start, end).trim());
            if (end >= text.length()) break;
            start = Math.max(start + 1, end - CHUNK_OVERLAP);
        }
        return result;
    }

    private String writeEmbedding(List<Float> embedding) throws JsonProcessingException {
        if (embedding == null || embedding.isEmpty()) throw new AiProviderException("Embedding vacio", true);
        return objectMapper.writeValueAsString(embedding);
    }

    private CrmWhatsappAiKnowledgeCategory parseCategory(String value) {
        try {
            CrmWhatsappAiKnowledgeCategory category = CrmWhatsappAiKnowledgeCategory.valueOf(
                    required(value, 30, "Selecciona una categoria").toUpperCase(Locale.ROOT));
            if (!AgentKnowledgeRetriever.allowedCategories().contains(category)) {
                throw badRequest("La categoria no esta habilitada para el RAG informativo");
            }
            return category;
        } catch (IllegalArgumentException error) {
            throw badRequest("La categoria no es valida");
        }
    }

    private ArticleResponse response(CrmWhatsappAiKnowledgeArticle article) {
        return new ArticleResponse(article.getIdKnowledgeArticle(), article.getTitle(), article.getCategory().name(),
                article.getContent(), article.getKeywords(), article.getStatus().name(),
                article.getStatus() == CrmWhatsappAiKnowledgeStatus.ACTIVO,
                article.getActiveVersion(), article.getLastError(), article.getIndexedAt(), article.getUpdatedAt());
    }

    private String required(String value, int max, String message) {
        String clean = value == null ? "" : value.trim();
        if (clean.isBlank()) throw badRequest(message);
        if (clean.length() > max) throw badRequest("El valor supera el maximo de " + max + " caracteres");
        return clean;
    }
    private String optional(String value, int max) {
        String clean = value == null ? "" : value.trim();
        if (clean.length() > max) throw badRequest("Las palabras clave superan " + max + " caracteres");
        return clean.isBlank() ? null : clean;
    }
    private String limit(String value, int max) {
        String clean = value == null ? "Error no especificado" : value.trim();
        return clean.length() <= max ? clean : clean.substring(0, max);
    }
    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    public record ArticleRequest(Long id, String title, String category, String content, String keywords,
            Boolean active) {}
    public record ArticleResponse(Long id, String title, String category, String content, String keywords,
            String status, boolean active, Integer version, String lastError, LocalDateTime indexedAt,
            LocalDateTime updatedAt) {}
    public record KnowledgeTestRequest(String question) {}
    public record KnowledgeTestResponse(boolean eligible, String reason, List<KnowledgeTestSource> sources) {}
    public record KnowledgeTestSource(Long articleId, String title, String category, String content, double score) {}
}
