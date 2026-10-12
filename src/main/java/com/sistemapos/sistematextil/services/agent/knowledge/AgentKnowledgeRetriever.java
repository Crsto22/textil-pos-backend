package com.sistemapos.sistematextil.services.agent.knowledge;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sistemapos.sistematextil.model.CrmWhatsappAiKnowledgeCategory;
import com.sistemapos.sistematextil.model.CrmWhatsappAiKnowledgeChunk;
import com.sistemapos.sistematextil.model.CrmWhatsappAiKnowledgeStatus;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAiKnowledgeChunkRepository;
import com.sistemapos.sistematextil.services.agent.catalog.AgentCatalogScopeResolver;

@Service
public class AgentKnowledgeRetriever {
    private static final int MAX_SOURCES = 3;
    private static final int MAX_CONTEXT_CHARS = 3_500;
    private static final Set<CrmWhatsappAiKnowledgeCategory> ALLOWED_CATEGORIES = EnumSet.of(
            CrmWhatsappAiKnowledgeCategory.POLITICAS,
            CrmWhatsappAiKnowledgeCategory.HORARIOS,
            CrmWhatsappAiKnowledgeCategory.UBICACION,
            CrmWhatsappAiKnowledgeCategory.ENVIOS,
            CrmWhatsappAiKnowledgeCategory.CUIDADOS,
            CrmWhatsappAiKnowledgeCategory.FAQ);
    private static final Set<String> STOP_WORDS = Set.of(
            "a", "al", "como", "con", "cual", "cuales", "de", "del", "el", "en", "es", "esta",
            "hay", "la", "las", "lo", "los", "me", "para", "por", "que", "se", "su", "tiene",
            "un", "una", "y", "kiments", "quiero", "saber", "dime", "puedes");
    private static final List<String> COMMERCIAL_TERMS = List.of(
            "precio", "precios", "cuesta", "cuanto cuesta", "vale", "valor", "barato", "barata",
            "caro", "cara", "stock", "modelo", "modelos", "producto", "productos", "talla", "tallas",
            "color", "colores", "tela", "material", "combo", "combos", "promocion", "promociones",
            "oferta", "ofertas", "descuento", "descuentos", "carrito", "comprar", "pedido", "pago",
            "pagos", "yape", "plin", "transferencia", "comprobante", "nota de venta", "venta", "ventas",
            "preventa", "cantidad", "unidades");

    private final CrmWhatsappAiKnowledgeChunkRepository chunkRepository;
    private final AgentCatalogScopeResolver scopeResolver;
    private final AgentKnowledgeEmbeddingGateway embeddingGateway;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, List<Float>> embeddingCache = new ConcurrentHashMap<>();

    @Autowired
    public AgentKnowledgeRetriever(
            CrmWhatsappAiKnowledgeChunkRepository chunkRepository,
            AgentCatalogScopeResolver scopeResolver,
            AgentKnowledgeEmbeddingGateway embeddingGateway) {
        this(chunkRepository, scopeResolver, embeddingGateway, new ObjectMapper().findAndRegisterModules());
    }

    AgentKnowledgeRetriever(
            CrmWhatsappAiKnowledgeChunkRepository chunkRepository,
            AgentCatalogScopeResolver scopeResolver,
            AgentKnowledgeEmbeddingGateway embeddingGateway,
            ObjectMapper objectMapper) {
        this.chunkRepository = chunkRepository;
        this.scopeResolver = scopeResolver;
        this.embeddingGateway = embeddingGateway;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public KnowledgeSearchResult searchForSession(Long sessionId, String question) {
        var scope = scopeResolver.resolve(sessionId);
        return search(scope.connectionId(), question);
    }

    @Transactional(readOnly = true)
    public KnowledgeSearchResult search(Long connectionId, String question) {
        String cleanQuestion = clean(question);
        if (connectionId == null) throw new IllegalArgumentException("La conexion es obligatoria para consultar el RAG");
        if (cleanQuestion.length() < 3 || cleanQuestion.length() > 500) {
            throw new IllegalArgumentException("La consulta RAG debe tener entre 3 y 500 caracteres");
        }
        if (isCommercialQuestion(cleanQuestion)) {
            return new KnowledgeSearchResult(false, "COMMERCIAL_DATA_REQUIRES_VERIFIED_TOOLS", List.of());
        }

        List<CrmWhatsappAiKnowledgeChunk> chunks = chunkRepository.findSearchableByCategories(
                connectionId,
                List.of(
                        CrmWhatsappAiKnowledgeStatus.ACTIVO,
                        CrmWhatsappAiKnowledgeStatus.INDEXANDO,
                        CrmWhatsappAiKnowledgeStatus.ERROR),
                List.copyOf(ALLOWED_CATEGORIES));
        if (chunks.isEmpty()) return new KnowledgeSearchResult(true, "NO_RELEVANT_INFORMATION", List.of());

        List<Float> queryEmbedding = queryEmbedding(connectionId, cleanQuestion);
        List<ScoredChunk> ranked = chunks.stream()
                .map(chunk -> score(cleanQuestion, queryEmbedding, chunk))
                .filter(item -> item.score() > 0d)
                .sorted(Comparator.comparingDouble(ScoredChunk::score).reversed())
                .toList();
        if (ranked.isEmpty()) return new KnowledgeSearchResult(true, "NO_RELEVANT_INFORMATION", List.of());

        ScoredChunk best = ranked.getFirst();
        if (!best.semanticallyRelevant() && best.keywordScore() < 1d && best.categoryBoost() <= 0d) {
            return new KnowledgeSearchResult(true, "NO_RELEVANT_INFORMATION", List.of());
        }
        var bestCategory = best.chunk().getArticle().getCategory();
        double minimumScore = Math.max(0.1d, best.score() * 0.72d);
        List<KnowledgeSource> sources = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        int chars = 0;
        for (ScoredChunk item : ranked) {
            if (sources.size() >= MAX_SOURCES || chars >= MAX_CONTEXT_CHARS) break;
            if (item.score() < minimumScore || item.chunk().getArticle().getCategory() != bestCategory) continue;
            if (!seen.add(item.chunk().getIdKnowledgeChunk())) continue;
            String content = sanitize(item.chunk().getContent());
            if (content.isBlank()) continue;
            int remaining = MAX_CONTEXT_CHARS - chars;
            if (content.length() > remaining) content = content.substring(0, remaining).trim();
            var article = item.chunk().getArticle();
            sources.add(new KnowledgeSource(
                    article.getIdKnowledgeArticle(),
                    item.chunk().getIdKnowledgeChunk(),
                    article.getTitle(),
                    article.getCategory().name(),
                    content,
                    BigDecimal.valueOf(item.score()).setScale(3, java.math.RoundingMode.HALF_UP)));
            chars += content.length();
        }
        return new KnowledgeSearchResult(true,
                sources.isEmpty() ? "NO_RELEVANT_INFORMATION" : "INFORMATION_FOUND",
                List.copyOf(sources));
    }

    public static Set<CrmWhatsappAiKnowledgeCategory> allowedCategories() {
        return Set.copyOf(ALLOWED_CATEGORIES);
    }

    private ScoredChunk score(String question, List<Float> queryEmbedding, CrmWhatsappAiKnowledgeChunk chunk) {
        double keyword = keywordScore(question, chunk);
        double semantic = cosine(queryEmbedding, readEmbedding(chunk));
        double category = categoryBoost(question, chunk.getArticle().getCategory());
        double total = semantic * 4d + Math.min(keyword, 4d) * 0.5d + category;
        return new ScoredChunk(chunk, total, keyword, semantic >= 0.58d, category);
    }

    private List<Float> queryEmbedding(Long connectionId, String question) {
        String key = connectionId + ":" + normalize(question);
        if (embeddingCache.size() >= 200) embeddingCache.clear();
        try {
            return embeddingCache.computeIfAbsent(key,
                    ignored -> List.copyOf(embeddingGateway.embedQuery(connectionId, question)));
        } catch (RuntimeException error) {
            return List.of();
        }
    }

    private List<Float> readEmbedding(CrmWhatsappAiKnowledgeChunk chunk) {
        try {
            return objectMapper.readValue(chunk.getEmbeddingJson(), new TypeReference<List<Float>>() {});
        } catch (Exception error) {
            return List.of();
        }
    }

    private double keywordScore(String question, CrmWhatsappAiKnowledgeChunk chunk) {
        Set<String> terms = terms(question);
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

    private double categoryBoost(String question, CrmWhatsappAiKnowledgeCategory category) {
        String value = normalize(question);
        return switch (category) {
            case POLITICAS -> containsAny(value, "politica", "cambio", "devolucion", "garantia") ? 1.5d : 0d;
            case HORARIOS -> containsAny(value, "horario", "hora", "atienden", "abren", "cierran") ? 1.5d : 0d;
            case UBICACION -> containsAny(value, "ubicacion", "direccion", "donde", "llegar") ? 1.5d : 0d;
            case ENVIOS -> containsAny(value, "envio", "enviar", "shalom", "provincia", "delivery", "demora") ? 1.5d : 0d;
            case CUIDADOS -> containsAny(value, "cuidado", "lavar", "lavado", "planchar", "secar") ? 1.5d : 0d;
            case FAQ -> containsAny(value, "pregunta", "duda", "informacion") ? 0.5d : 0d;
            default -> 0d;
        };
    }

    private boolean isCommercialQuestion(String question) {
        String normalized = normalize(question);
        if ((normalized.contains("envio") || normalized.contains("shalom") || normalized.contains("delivery"))
                && containsAny(normalized, "costo", "precio", "tarifa", "cuanto cuesta")) return true;
        return COMMERCIAL_TERMS.stream().anyMatch(term -> containsTerm(normalized, term));
    }

    private String sanitize(String content) {
        return clean(content)
                .replaceAll("(?i)(S/\\.?\\s*|USD\\s*|US\\$\\s*|\\$\\s*)\\d+(?:[.,]\\d+)?", "[dato comercial omitido]")
                .replaceAll("(?i)\\b\\d+(?:[.,]\\d+)?\\s*soles\\b", "[dato comercial omitido]")
                .trim();
    }

    private double cosine(List<Float> left, List<Float> right) {
        if (left == null || right == null || left.isEmpty() || left.size() != right.size()) return 0d;
        double dot = 0d;
        double leftNorm = 0d;
        double rightNorm = 0d;
        for (int index = 0; index < left.size(); index++) {
            double a = left.get(index);
            double b = right.get(index);
            dot += a * b;
            leftNorm += a * a;
            rightNorm += b * b;
        }
        return leftNorm == 0d || rightNorm == 0d ? 0d : dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    private Set<String> terms(String value) {
        Set<String> result = new LinkedHashSet<>();
        for (String term : normalize(value).split("\\s+")) {
            if (term.length() >= 3 && !STOP_WORDS.contains(term)) result.add(term);
        }
        return result;
    }

    private boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) if (value.contains(candidate)) return true;
        return false;
    }

    private boolean containsTerm(String value, String term) {
        return term.contains(" ") ? value.contains(term) : (" " + value + " ").contains(" " + term + " ");
    }

    private String normalize(String value) {
        return Normalizer.normalize(clean(value).toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    public record KnowledgeSearchResult(boolean eligible, String reason, List<KnowledgeSource> sources) {}

    public record KnowledgeSource(
            Long articleId,
            Long chunkId,
            String title,
            String category,
            String content,
            BigDecimal score) {}

    private record ScoredChunk(
            CrmWhatsappAiKnowledgeChunk chunk,
            double score,
            double keywordScore,
            boolean semanticallyRelevant,
            double categoryBoost) {}
}
