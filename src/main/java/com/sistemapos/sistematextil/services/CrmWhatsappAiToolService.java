package com.sistemapos.sistematextil.services;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sistemapos.sistematextil.model.CrmWhatsappConversation;
import com.sistemapos.sistematextil.services.CrmWhatsappAiCommercialQueryService.CatalogResult;
import com.sistemapos.sistematextil.services.CrmWhatsappAiCommercialQueryService.BranchResult;
import com.sistemapos.sistematextil.services.CrmWhatsappAiCommercialQueryService.ClientResult;
import com.sistemapos.sistematextil.services.CrmWhatsappAiCommercialQueryService.PaymentResult;
import com.sistemapos.sistematextil.services.CrmWhatsappAiCommercialQueryService.PromotionCatalogResult;
import com.sistemapos.sistematextil.services.CrmWhatsappAiCommercialQueryService.OfferCatalogResult;
import com.sistemapos.sistematextil.services.CrmWhatsappAiCommercialQueryService.ProductResult;
import com.sistemapos.sistematextil.services.CrmWhatsappAiCommercialQueryService.SalesResult;
import com.sistemapos.sistematextil.services.CrmWhatsappAiCommercialQueryService.VariantResult;
import com.sistemapos.sistematextil.services.ai.AiModelProvider.ToolCall;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CrmWhatsappAiToolService {

    private static final Set<String> ALLOWED_TOOLS = Set.of(
            "buscar_productos",
            "consultar_ofertas",
            "consultar_promociones",
            "consultar_informacion_negocio",
            "consultar_metodos_pago",
            "consultar_cliente_actual",
            "consultar_ventas_cliente");

    private final CrmWhatsappAiCommercialQueryService commercialQueryService;
    private final CrmWhatsappAiKnowledgeService knowledgeService;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Transactional(readOnly = true)
    public ExecutionResult execute(CrmWhatsappConversation conversation, List<ToolCall> requestedTools) {
        List<Map<String, Object>> modelResults = new ArrayList<>();
        List<Map<String, Object>> auditTrace = new ArrayList<>();
        List<Map<String, Object>> evidence = new ArrayList<>();
        List<MediaReference> media = new ArrayList<>();
        if (requestedTools == null || requestedTools.isEmpty()) {
            return new ExecutionResult(modelResults, auditTrace, evidence, media);
        }

        for (ToolCall call : requestedTools.stream().limit(3).toList()) {
            String name = clean(call == null ? null : call.name()).toLowerCase();
            if (!ALLOWED_TOOLS.contains(name)) {
                modelResults.add(error(name, "Herramienta no permitida"));
                auditTrace.add(audit(name, null, 0, "DENIED", List.of()));
                continue;
            }
            try {
                ToolResult result = executeOne(conversation, name, call == null ? null : call.arguments());
                modelResults.add(result.modelResult());
                auditTrace.add(result.audit());
                evidence.add(result.evidence());
                media.addAll(result.media());
            } catch (IllegalStateException error) {
                modelResults.add(error(name, safeMessage(error)));
                auditTrace.add(audit(name, null, 0, "REQUIRES_HUMAN", List.of()));
                evidence.add(evidenceStatus(name, "REQUIRES_HUMAN"));
            } catch (RuntimeException error) {
                modelResults.add(error(name, "No se pudo consultar la informacion comercial"));
                auditTrace.add(audit(name, null, 0, "FAILED", List.of()));
                evidence.add(evidenceStatus(name, "FAILED"));
            }
        }
        return new ExecutionResult(
                List.copyOf(modelResults),
                List.copyOf(auditTrace),
                List.copyOf(evidence),
                distinctMedia(media));
    }

    private ToolResult executeOne(
            CrmWhatsappConversation conversation,
            String name,
            Map<String, Object> arguments) {
        return switch (name) {
            case "buscar_productos" -> {
                int page = integerArgument(arguments, "page");
                String query = textArgument(arguments, "q");
                String fallback = textArgument(arguments, "fallbackProduct");
                yield productResult(!fallback.isBlank()
                        ? commercialQueryService.searchProducts(conversation, query, page, fallback)
                        : page == 0
                                ? commercialQueryService.searchProducts(conversation, query)
                                : commercialQueryService.searchProducts(conversation, query, page));
            }
            case "consultar_ofertas" -> offerResult(commercialQueryService.offers(
                    conversation, textArgument(arguments, "q"), integerArgument(arguments, "page")));
            case "consultar_promociones" -> promotionResult(commercialQueryService.promotions(
                    conversation, textArgument(arguments, "q"), integerArgument(arguments, "page")));
            case "consultar_informacion_negocio" -> knowledgeResult(conversation,
                    textArgument(arguments, "q"));
            case "consultar_metodos_pago" -> paymentResult(
                    commercialQueryService.paymentMethods(conversation));
            case "consultar_cliente_actual" -> clientResult(
                    commercialQueryService.currentClient(conversation), conversation);
            case "consultar_ventas_cliente" -> salesResult(
                    commercialQueryService.clientSales(conversation, textArgument(arguments, "comprobante")));
            default -> new ToolResult(error(name, "Herramienta no disponible"),
                    audit(name, null, 0, "DENIED", List.of()), evidenceStatus(name, "DENIED"), List.of());
        };
    }

    private ToolResult knowledgeResult(CrmWhatsappConversation conversation, String query) {
        if (conversation == null || conversation.getConnection() == null) {
            throw new IllegalStateException("La conversacion no tiene una conexion configurada");
        }
        var result = knowledgeService.search(conversation.getConnection().getIdConnection(), query);
        Map<String, Object> model = knowledgeService.modelResult(result);
        List<Long> articleIds = result.sources().stream()
                .map(CrmWhatsappAiKnowledgeService.SourceResponse::articleId)
                .distinct()
                .toList();
        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("tool", "consultar_informacion_negocio");
        trace.put("resultCount", result.sources().size());
        trace.put("status", result.sources().isEmpty() ? "EMPTY" : "OK");
        trace.put("entityIds", articleIds);
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("tool", "consultar_informacion_negocio");
        evidence.put("articleIds", articleIds);
        evidence.put("categories", result.sources().stream()
                .map(CrmWhatsappAiKnowledgeService.SourceResponse::category).distinct().toList());
        evidence.put("shippingPriceRequiresAdvisor", result.shippingPriceRequiresAdvisor());
        return new ToolResult(model, trace, evidence, List.of());
    }

    private ToolResult productResult(CatalogResult result) {
        List<Map<String, Object>> products = result.products().stream().map(product -> {
            Map<String, Object> sanitized = toMap(product);
            sanitized.remove("category");
            sanitized.put("fechaEnvioPreventa",
                    product.fechaEnvioPreventa() == null ? null : product.fechaEnvioPreventa().toString());
            return sanitized;
        }).toList();
        List<Map<String, Object>> candidates = result.candidates().stream()
                .map(candidate -> Map.<String, Object>of(
                        "productId", candidate.productId(),
                        "name", clean(candidate.name())))
                .toList();
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("tool", "buscar_productos");
        model.put("query", clean(result.query()));
        model.put("interpretedProduct", clean(result.interpretedProduct()));
        model.put("corrected", result.corrected());
        model.put("resolution", clean(result.resolution()));
        model.put("candidates", candidates);
        model.put("products", products);
        List<Integer> ids = result.products().stream().map(ProductResult::productId).toList();
        List<MediaReference> media = result.products().stream()
                .filter(product -> !clean(product.globalImageUrl()).isBlank())
                .map(product -> new MediaReference("PRODUCT_GLOBAL_IMAGE", product.productId(), null,
                        product.name(), "", product.globalImageUrl(), product.globalThumbnailUrl()))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        result.products().stream()
                .flatMap(product -> product.variants().stream()
                        .filter(VariantResult::colorSpecificImage)
                        .filter(variant -> !clean(variant.imageUrl()).isBlank())
                        .map(variant -> media(product, variant)))
                .limit(20)
                .forEach(media::add);
        result.products().stream()
                .filter(product -> !clean(product.sizeGuideUrl()).isBlank())
                .map(product -> new MediaReference("SIZE_GUIDE", product.productId(), null, product.name(), "",
                        product.sizeGuideUrl(), product.sizeGuideThumbnailUrl()))
                .forEach(media::add);
        return new ToolResult(model,
                audit("buscar_productos", result.branchId(), result.products().size(), "OK", ids),
                Map.of(
                        "tool", "buscar_productos",
                        "ecommerceOnly", true,
                        "interpretedProduct", clean(result.interpretedProduct()),
                        "corrected", result.corrected(),
                        "resolution", clean(result.resolution()),
                        "candidates", candidates,
                        "products", products),
                media);
    }

    private ToolResult offerResult(OfferCatalogResult result) {
        Map<String, Object> model = toMap(result);
        model.remove("branchId");
        model.put("tool", "consultar_ofertas");
        List<Integer> ids = result.products().stream().map(item -> item.productId()).toList();
        return new ToolResult(model,
                audit("consultar_ofertas", result.branchId(), result.products().size(), "OK", ids),
                Map.of("tool", "consultar_ofertas", "products", result.products()),
                List.of());
    }

    private ToolResult promotionResult(PromotionCatalogResult result) {
        Map<String, Object> model = toMap(result);
        model.remove("branchId");
        model.put("tool", "consultar_promociones");
        List<Integer> ids = result.promotions().stream().map(item -> item.promotionId()).toList();
        return new ToolResult(model,
                audit("consultar_promociones", result.branchId(), result.promotions().size(), "OK", ids),
                Map.of("tool", "consultar_promociones", "promotions", result.promotions()),
                List.of());
    }

    private ToolResult branchResult(BranchResult result, CrmWhatsappConversation conversation) {
        Map<String, Object> model = toMap(result);
        model.remove("branchId");
        model.remove("name");
        model.put("tool", "consultar_sucursal");
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("tool", "consultar_sucursal");
        evidence.put("address", clean(result.address()));
        evidence.put("city", clean(result.city()));
        evidence.put("phone", clean(result.phone()));
        evidence.put("scheduleRequiresAdvisor", result.scheduleRequiresAdvisor());
        evidence.put("businessHours", result.businessHours());
        return new ToolResult(model,
                audit("consultar_sucursal", branchId(conversation), 1, "OK", List.of()),
                evidence, List.of());
    }

    private ToolResult paymentResult(PaymentResult result) {
        Map<String, Object> model = toMap(result);
        model.remove("branchId");
        model.remove("branch");
        model.put("tool", "consultar_metodos_pago");
        return new ToolResult(model,
                audit("consultar_metodos_pago", result.branchId(), result.methods().size(), "OK",
                        result.methods().stream().map(item -> item.paymentMethodId()).toList()),
                Map.of(
                        "tool", "consultar_metodos_pago",
                        "methods", result.methods().stream().map(item -> Map.of(
                                "name", clean(item.name()),
                                "requiresOperationCode", item.requiresOperationCode(),
                                "requiresPaymentDate", item.requiresPaymentDate(),
                                "requiresPaymentTime", item.requiresPaymentTime())).toList()),
                List.of());
    }

    private ToolResult clientResult(ClientResult result, CrmWhatsappConversation conversation) {
        Map<String, Object> model = toMap(result);
        model.put("tool", "consultar_cliente_actual");
        Integer branchId = branchId(conversation);
        List<Integer> ids = conversation.getCliente() == null
                ? List.of() : List.of(conversation.getCliente().getIdCliente());
        return new ToolResult(model,
                audit("consultar_cliente_actual", branchId, result.linked() ? 1 : 0,
                        result.linked() ? "OK" : "REQUIRES_HUMAN", ids),
                Map.of("tool", "consultar_cliente_actual", "linked", result.linked()),
                List.of());
    }

    private ToolResult salesResult(SalesResult result) {
        Map<String, Object> model = toMap(result);
        model.put("tool", "consultar_ventas_cliente");
        return new ToolResult(model,
                audit("consultar_ventas_cliente", result.branchId(), result.sales().size(),
                        result.clientLinked() ? "OK" : "REQUIRES_HUMAN",
                        result.sales().stream().map(item -> item.saleId()).toList()),
                Map.of("tool", "consultar_ventas_cliente", "sales", result.sales()),
                List.of());
    }

    private Map<String, Object> toMap(Object value) {
        return new LinkedHashMap<>(objectMapper.convertValue(value, new TypeReference<Map<String, Object>>() {}));
    }

    private MediaReference media(ProductResult product, VariantResult variant) {
        return new MediaReference("PRODUCT_COLOR_IMAGE", product.productId(), variant.variantId(), product.name(),
                variant.color(), variant.imageUrl(), variant.thumbnailUrl());
    }

    private List<MediaReference> distinctMedia(List<MediaReference> values) {
        Set<String> seen = new LinkedHashSet<>();
        return java.util.stream.Stream.concat(
                        values.stream().filter(item -> "SIZE_GUIDE".equals(item.type())),
                        values.stream().filter(item -> !"SIZE_GUIDE".equals(item.type())))
                .filter(item -> seen.add(clean(item.url())))
                .limit(20)
                .toList();
    }

    private Map<String, Object> audit(
            String tool,
            Integer branchId,
            int resultCount,
            String status,
            List<Integer> entityIds) {
        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("tool", clean(tool));
        trace.put("branchId", branchId);
        trace.put("resultCount", resultCount);
        trace.put("status", status);
        trace.put("entityIds", entityIds == null ? List.of() : entityIds.stream().limit(20).toList());
        return trace;
    }

    private Map<String, Object> error(String tool, String message) {
        return Map.of("tool", clean(tool), "error", clean(message), "requiresHuman", true);
    }

    private Map<String, Object> evidenceStatus(String tool, String status) {
        return Map.of("tool", clean(tool), "status", clean(status));
    }

    private Integer branchId(CrmWhatsappConversation conversation) {
        return conversation == null || conversation.getConnection() == null
                || conversation.getConnection().getSucursal() == null
                ? null : conversation.getConnection().getSucursal().getIdSucursal();
    }

    private String textArgument(Map<String, Object> arguments, String key) {
        if (arguments == null || arguments.get(key) == null) return "";
        String value = clean(String.valueOf(arguments.get(key)));
        return value.length() <= 80 ? value : value.substring(0, 80);
    }

    private int integerArgument(Map<String, Object> arguments, String key) {
        if (arguments == null || !(arguments.get(key) instanceof Number number)) return 0;
        return Math.max(0, Math.min(number.intValue(), 10));
    }

    private String safeMessage(Throwable error) {
        return clean(error.getMessage()).isBlank() ? "Informacion no disponible" : clean(error.getMessage());
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    public record ExecutionResult(
            List<Map<String, Object>> modelResults,
            List<Map<String, Object>> auditTrace,
            List<Map<String, Object>> evidence,
            List<MediaReference> mediaCandidates) {
        public boolean requiresHuman() {
            return modelResults != null && modelResults.stream()
                    .anyMatch(item -> Boolean.TRUE.equals(item.get("requiresHuman")));
        }
    }

    public record MediaReference(
            String type,
            Integer productId,
            Integer variantId,
            String product,
            String color,
            String url,
            String thumbnailUrl) {}

    private record ToolResult(
            Map<String, Object> modelResult,
            Map<String, Object> audit,
            Map<String, Object> evidence,
            List<MediaReference> media) {}
}
