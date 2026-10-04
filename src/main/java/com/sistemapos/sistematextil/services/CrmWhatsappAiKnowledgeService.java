package com.sistemapos.sistematextil.services;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sistemapos.sistematextil.model.*;
import com.sistemapos.sistematextil.repositories.*;
import com.sistemapos.sistematextil.services.ai.AiEmbeddingProvider;
import com.sistemapos.sistematextil.services.ai.AiModelProvider;
import com.sistemapos.sistematextil.services.ai.AiModelProvider.DraftRequest;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CrmWhatsappAiKnowledgeService {

    private static final int MAX_CHUNK_CHARS = 900;
    private static final int CHUNK_OVERLAP = 120;
    private static final int MAX_CONTEXT_CHARS = 3_500;
    private static final Set<String> STOP_WORDS = Set.of(
            "a", "al", "como", "con", "cual", "cuales", "de", "del", "el", "en", "es", "esta",
            "hay", "la", "las", "lo", "los", "me", "para", "por", "que", "se", "su", "tienen", "tiene", "un", "una", "y");

    private final CrmWhatsappAiKnowledgeArticleRepository articleRepository;
    private final CrmWhatsappAiKnowledgeChunkRepository chunkRepository;
    private final CrmWhatsappAiCredentialService credentialService;
    private final CrmWhatsappConnectionRepository connectionRepository;
    private final UsuarioRepository usuarioRepository;
    private final CrmWhatsappAiAuditService auditService;
    private final AiEmbeddingProvider embeddingProvider;
    private final AiModelProvider modelProvider;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final Map<String, List<Float>> queryEmbeddingCache = new ConcurrentHashMap<>();

    @Transactional(readOnly = true)
    public List<ArticleResponse> list(String query, String category, Usuario sessionUser) {
        Long connectionId = credentialService.requireAdminConnectionId(sessionUser);
        String normalizedQuery = normalize(query);
        CrmWhatsappAiKnowledgeCategory normalizedCategory = parseOptionalCategory(category);
        return articleRepository.findByConnection_IdConnectionAndDeletedAtIsNullOrderByUpdatedAtDesc(connectionId)
                .stream()
                .filter(article -> normalizedCategory == null || article.getCategory() == normalizedCategory)
                .filter(article -> normalizedQuery.isBlank()
                        || normalize(article.getTitle() + " " + article.getKeywords() + " " + article.getContent())
                                .contains(normalizedQuery))
                .map(this::response)
                .toList();
    }

    @Transactional
    public ArticleResponse create(ArticleRequest request, Usuario sessionUser) {
        ActorContext context = requireAdminContext(sessionUser);
        ValidatedArticle values = validate(request);
        CrmWhatsappAiKnowledgeArticle article = new CrmWhatsappAiKnowledgeArticle();
        article.setConnection(context.connection());
        apply(article, values);
        article.setActiveVersion(0);
        article.setPendingVersion(values.active() ? 1 : 0);
        article.setStatus(values.active()
                ? CrmWhatsappAiKnowledgeStatus.INDEXANDO
                : CrmWhatsappAiKnowledgeStatus.BORRADOR);
        article = articleRepository.save(article);
        auditService.record(context.connection(), null, null, context.actor(), "KNOWLEDGE_CREATED", "INFO",
                "Articulo de conocimiento creado", Map.of("articleId", article.getIdKnowledgeArticle()));
        return response(article);
    }

    @Transactional
    public ArticleResponse update(Long articleId, ArticleRequest request, Usuario sessionUser) {
        ActorContext context = requireAdminContext(sessionUser);
        CrmWhatsappAiKnowledgeArticle article = requireArticle(articleId, context.connection().getIdConnection());
        ValidatedArticle values = validate(request);
        apply(article, values);
        if (values.active()) {
            article.setPendingVersion(Math.max(article.getActiveVersion(), article.getPendingVersion()) + 1);
            article.setStatus(CrmWhatsappAiKnowledgeStatus.INDEXANDO);
            article.setLastError(null);
        } else {
            article.setStatus(CrmWhatsappAiKnowledgeStatus.BORRADOR);
        }
        article = articleRepository.save(article);
        auditService.record(context.connection(), null, null, context.actor(), "KNOWLEDGE_UPDATED", "INFO",
                "Articulo de conocimiento actualizado", Map.of("articleId", articleId));
        return response(article);
    }

    @Transactional
    public void delete(Long articleId, Usuario sessionUser) {
        ActorContext context = requireAdminContext(sessionUser);
        CrmWhatsappAiKnowledgeArticle article = requireArticle(articleId, context.connection().getIdConnection());
        article.setDeletedAt(LocalDateTime.now());
        article.setStatus(CrmWhatsappAiKnowledgeStatus.BORRADOR);
        articleRepository.save(article);
        auditService.record(context.connection(), null, null, context.actor(), "KNOWLEDGE_DELETED", "WARN",
                "Articulo de conocimiento eliminado", Map.of("articleId", articleId));
    }

    @Transactional
    public ArticleResponse reindex(Long articleId, Usuario sessionUser) {
        ActorContext context = requireAdminContext(sessionUser);
        CrmWhatsappAiKnowledgeArticle article = requireArticle(articleId, context.connection().getIdConnection());
        article.setPendingVersion(Math.max(article.getActiveVersion(), article.getPendingVersion()) + 1);
        article.setStatus(CrmWhatsappAiKnowledgeStatus.INDEXANDO);
        article.setLastError(null);
        return response(articleRepository.save(article));
    }

    @Transactional(readOnly = true)
    public TestResponse test(TestRequest request, Usuario sessionUser) {
        Long connectionId = credentialService.requireAdminConnectionId(sessionUser);
        String question = clean(request == null ? null : request.question());
        if (question.length() < 3 || question.length() > 500) {
            throw badRequest("La pregunta debe tener entre 3 y 500 caracteres");
        }
        SearchResult search = search(connectionId, question);
        if (search.shippingPriceRequiresAdvisor()) {
            return new TestResponse(shippingPriceResponse(), search.sources());
        }
        if (search.sources().isEmpty()) {
            return new TestResponse("No encontre informacion suficiente. Un asesor debe confirmar este dato.", List.of());
        }
        Map<String, Object> toolResult = modelResult(search);
        var draft = modelProvider.generateDraft(new DraftRequest(
                connectionId,
                knowledgeSystemInstruction(),
                "Cliente: " + question,
                "INFORMACION_NEGOCIO",
                List.of(toolResult)));
        String answer = clean(draft.response());
        if (draft.requiresHuman() || answer.isBlank()) {
            answer = "No encontre informacion suficiente. Un asesor debe confirmar este dato.";
        }
        return new TestResponse(answer, search.sources());
    }

    @Transactional(readOnly = true)
    public SearchResult search(Long connectionId, String query) {
        String cleanQuery = clean(query);
        boolean shippingPrice = asksShippingPrice(cleanQuery);
        List<CrmWhatsappAiKnowledgeChunk> chunks = chunkRepository.findSearchable(connectionId, List.of(
                CrmWhatsappAiKnowledgeStatus.ACTIVO,
                CrmWhatsappAiKnowledgeStatus.INDEXANDO,
                CrmWhatsappAiKnowledgeStatus.ERROR));
        if (chunks.isEmpty()) return new SearchResult(List.of(), shippingPrice);

        List<ScoredChunk> keywordRanked = chunks.stream()
                .map(chunk -> new ScoredChunk(chunk, keywordScore(cleanQuery, chunk)))
                .filter(item -> item.score() > 0)
                .sorted(Comparator.comparingDouble(ScoredChunk::score).reversed())
                .toList();
        List<ScoredChunk> ranked;
        if (!keywordRanked.isEmpty() && keywordRanked.getFirst().score() >= 3.0d) {
            ranked = keywordRanked;
        } else {
            try {
                List<Float> queryEmbedding = cachedQueryEmbedding(connectionId, cleanQuery);
                ranked = chunks.stream()
                        .map(chunk -> new ScoredChunk(chunk,
                                Math.max(keywordScore(cleanQuery, chunk), cosine(queryEmbedding, readEmbedding(chunk)))))
                        .filter(item -> item.score() >= 0.35d)
                        .sorted(Comparator.comparingDouble(ScoredChunk::score).reversed())
                        .toList();
            } catch (RuntimeException error) {
                ranked = keywordRanked;
            }
        }

        int chars = 0;
        List<SourceResponse> sources = new ArrayList<>();
        Set<Long> seenChunks = new HashSet<>();
        for (ScoredChunk item : ranked) {
            if (sources.size() == 3 || !seenChunks.add(item.chunk().getIdKnowledgeChunk())) break;
            String content = clean(item.chunk().getContent());
            if (content.isBlank()) continue;
            int remaining = MAX_CONTEXT_CHARS - chars;
            if (remaining <= 0) break;
            if (content.length() > remaining) content = content.substring(0, remaining);
            CrmWhatsappAiKnowledgeArticle article = item.chunk().getArticle();
            sources.add(new SourceResponse(article.getIdKnowledgeArticle(), item.chunk().getIdKnowledgeChunk(),
                    article.getTitle(), article.getCategory().name(), content,
                    Math.round(item.score() * 1000d) / 1000d));
            chars += content.length();
        }
        return new SearchResult(List.copyOf(sources), shippingPrice);
    }

    public Map<String, Object> modelResult(SearchResult result) {
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("tool", "consultar_informacion_negocio");
        model.put("shippingPriceRequiresAdvisor", result.shippingPriceRequiresAdvisor());
        model.put("sources", result.sources().stream().map(source -> Map.of(
                "title", source.title(),
                "category", source.category(),
                "content", sanitizeForModel(source))).toList());
        return model;
    }

    private String sanitizeForModel(SourceResponse source) {
        if (!CrmWhatsappAiKnowledgeCategory.ENVIOS.name().equals(source.category())) return source.content();
        return source.content()
                .replaceAll("(?i)(S/\\.?\\s*|USD\\s*|US\\$\\s*|\\$\\s*)\\d+(?:[.,]\\d+)?", "[costo por confirmar]")
                .replaceAll("(?i)\\b\\d+(?:[.,]\\d+)?\\s*soles\\b", "[costo por confirmar]");
    }

    @Transactional
    public void indexNextBatch() {
        List<Long> ids = articleRepository.findByStatusAndDeletedAtIsNullOrderByUpdatedAtAsc(
                CrmWhatsappAiKnowledgeStatus.INDEXANDO, PageRequest.of(0, 3)).stream()
                .map(CrmWhatsappAiKnowledgeArticle::getIdKnowledgeArticle)
                .toList();
        ids.forEach(this::indexArticle);
    }

    public void indexArticle(Long articleId) {
        CrmWhatsappAiKnowledgeArticle article = articleRepository.findById(articleId).orElse(null);
        if (article == null || article.getDeletedAt() != null
                || article.getStatus() != CrmWhatsappAiKnowledgeStatus.INDEXANDO) return;
        int version = article.getPendingVersion();
        try {
            List<String> pieces = splitIntoChunks(article.getContent());
            List<CrmWhatsappAiKnowledgeChunk> indexed = new ArrayList<>();
            for (int index = 0; index < pieces.size(); index++) {
                CrmWhatsappAiKnowledgeChunk chunk = new CrmWhatsappAiKnowledgeChunk();
                chunk.setArticle(article);
                chunk.setArticleVersion(version);
                chunk.setChunkOrder(index);
                chunk.setContent(pieces.get(index));
                chunk.setEmbeddingJson(objectMapper.writeValueAsString(embeddingProvider.embedDocument(
                        article.getConnection().getIdConnection(), article.getTitle(), pieces.get(index))));
                chunk.setEmbeddingModel(embeddingProvider.embeddingModel());
                indexed.add(chunk);
            }
            chunkRepository.deleteVersion(articleId, version);
            chunkRepository.saveAll(indexed);
            article.setActiveVersion(version);
            article.setStatus(CrmWhatsappAiKnowledgeStatus.ACTIVO);
            article.setIndexedAt(LocalDateTime.now());
            article.setLastError(null);
            articleRepository.save(article);
            chunkRepository.deleteOtherVersions(articleId, version);
        } catch (Exception error) {
            article.setStatus(CrmWhatsappAiKnowledgeStatus.ERROR);
            article.setLastError(truncate(error.getMessage(), 500));
            articleRepository.save(article);
        }
    }

    public String shippingPriceResponse() {
        return "🚚 El costo de envío debe ser confirmado por el personal encargado según el destino y la modalidad elegida.";
    }

    private List<Float> cachedQueryEmbedding(Long connectionId, String query) {
        String key = connectionId + ":" + normalize(query);
        if (queryEmbeddingCache.size() > 200) queryEmbeddingCache.clear();
        return queryEmbeddingCache.computeIfAbsent(key, ignored -> embeddingProvider.embedQuery(connectionId, query));
    }

    private List<Float> readEmbedding(CrmWhatsappAiKnowledgeChunk chunk) {
        try {
            return objectMapper.readValue(chunk.getEmbeddingJson(), new TypeReference<List<Float>>() {});
        } catch (Exception error) {
            return List.of();
        }
    }

    private double keywordScore(String query, CrmWhatsappAiKnowledgeChunk chunk) {
        Set<String> terms = terms(query);
        if (terms.isEmpty()) return 0d;
        String title = normalize(chunk.getArticle().getTitle());
        String keywords = normalize(chunk.getArticle().getKeywords());
        String content = normalize(chunk.getContent());
        double score = 0d;
        for (String term : terms) {
            if (title.contains(term)) score += 2d;
            if (keywords.contains(term)) score += 1.5d;
            if (content.contains(term)) score += 1d;
        }
        return score;
    }

    private double cosine(List<Float> left, List<Float> right) {
        if (left == null || right == null || left.isEmpty() || left.size() != right.size()) return 0d;
        double dot = 0d, leftNorm = 0d, rightNorm = 0d;
        for (int index = 0; index < left.size(); index++) {
            double a = left.get(index);
            double b = right.get(index);
            dot += a * b;
            leftNorm += a * a;
            rightNorm += b * b;
        }
        return leftNorm == 0d || rightNorm == 0d ? 0d : dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    private List<String> splitIntoChunks(String content) {
        String value = clean(content).replace("\r\n", "\n");
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < value.length()) {
            int end = Math.min(value.length(), start + MAX_CHUNK_CHARS);
            if (end < value.length()) {
                int paragraph = value.lastIndexOf("\n\n", end);
                int sentence = value.lastIndexOf(". ", end);
                int split = Math.max(paragraph, sentence);
                if (split > start + 350) end = split + (split == sentence ? 1 : 0);
            }
            String chunk = value.substring(start, end).trim();
            if (!chunk.isBlank()) chunks.add(chunk);
            if (end >= value.length()) break;
            start = Math.max(start + 1, end - CHUNK_OVERLAP);
        }
        return chunks;
    }

    private boolean asksShippingPrice(String query) {
        String value = normalize(query);
        boolean shipping = value.contains("envio") || value.contains("delivery") || value.contains("mandar")
                || value.contains("enviar");
        boolean price = value.contains("precio") || value.contains("costo") || value.contains("cuanto")
                || value.contains("tarifa");
        return shipping && price;
    }

    private Set<String> terms(String value) {
        Set<String> result = new LinkedHashSet<>();
        for (String term : normalize(value).split("\\s+")) {
            if (term.length() >= 3 && !STOP_WORDS.contains(term)) result.add(term);
        }
        return result;
    }

    private ValidatedArticle validate(ArticleRequest request) {
        if (request == null) throw badRequest("Ingrese los datos del articulo");
        String title = clean(request.title());
        String content = clean(request.content());
        String keywords = clean(request.keywords());
        if (title.length() < 3 || title.length() > 160) throw badRequest("El titulo debe tener entre 3 y 160 caracteres");
        if (content.length() < 20 || content.length() > 20_000) throw badRequest("El contenido debe tener entre 20 y 20000 caracteres");
        if (keywords.length() > 500) throw badRequest("Las palabras clave no deben superar 500 caracteres");
        CrmWhatsappAiKnowledgeCategory category;
        try {
            category = CrmWhatsappAiKnowledgeCategory.valueOf(clean(request.category()).toUpperCase(Locale.ROOT));
        } catch (Exception error) {
            throw badRequest("Categoria de conocimiento invalida");
        }
        return new ValidatedArticle(title, category, content, keywords, Boolean.TRUE.equals(request.active()));
    }

    private void apply(CrmWhatsappAiKnowledgeArticle article, ValidatedArticle values) {
        article.setTitle(values.title());
        article.setCategory(values.category());
        article.setContent(values.content());
        article.setKeywords(values.keywords().isBlank() ? null : values.keywords());
    }

    private ActorContext requireAdminContext(Usuario sessionUser) {
        Long connectionId = credentialService.requireAdminConnectionId(sessionUser);
        CrmWhatsappConnection connection = connectionRepository.findById(connectionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Conexion no configurada"));
        Usuario actor = usuarioRepository.findByIdUsuarioAndDeletedAtIsNull(sessionUser.getIdUsuario())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autorizado"));
        return new ActorContext(connection, actor);
    }

    private CrmWhatsappAiKnowledgeArticle requireArticle(Long articleId, Long connectionId) {
        return articleRepository.findByIdKnowledgeArticleAndConnection_IdConnectionAndDeletedAtIsNull(articleId, connectionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Articulo no encontrado"));
    }

    private CrmWhatsappAiKnowledgeCategory parseOptionalCategory(String value) {
        if (clean(value).isBlank()) return null;
        try {
            return CrmWhatsappAiKnowledgeCategory.valueOf(clean(value).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            throw badRequest("Categoria de conocimiento invalida");
        }
    }

    private ArticleResponse response(CrmWhatsappAiKnowledgeArticle article) {
        return new ArticleResponse(article.getIdKnowledgeArticle(), article.getTitle(), article.getCategory().name(),
                article.getContent(), clean(article.getKeywords()), article.getStatus().name(), article.getActiveVersion(),
                article.getPendingVersion(), clean(article.getLastError()), article.getIndexedAt(), article.getUpdatedAt());
    }

    private String knowledgeSystemInstruction() {
        return "Responde brevemente usando solo consultar_informacion_negocio. No inventes informacion. "
                + "Nunca calcules ni prometas costos de envio; esos montos los confirma el personal encargado.";
    }

    private String normalize(String value) {
        String clean = clean(value).toLowerCase(Locale.ROOT);
        return Normalizer.normalize(clean, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String clean(String value) { return value == null ? "" : value.trim(); }
    private String truncate(String value, int max) {
        String result = clean(value);
        return result.length() <= max ? result : result.substring(0, max);
    }
    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    public record ArticleRequest(String title, String category, String content, String keywords, Boolean active) {}
    public record TestRequest(String question) {}
    public record ArticleResponse(Long id, String title, String category, String content, String keywords,
            String status, Integer activeVersion, Integer pendingVersion, String lastError,
            LocalDateTime indexedAt, LocalDateTime updatedAt) {}
    public record SourceResponse(Long articleId, Long chunkId, String title, String category, String content, double score) {}
    public record TestResponse(String answer, List<SourceResponse> sources) {}
    public record SearchResult(List<SourceResponse> sources, boolean shippingPriceRequiresAdvisor) {}
    private record ValidatedArticle(String title, CrmWhatsappAiKnowledgeCategory category, String content,
            String keywords, boolean active) {}
    private record ActorContext(CrmWhatsappConnection connection, Usuario actor) {}
    private record ScoredChunk(CrmWhatsappAiKnowledgeChunk chunk, double score) {}
}
