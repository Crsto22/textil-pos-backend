package com.sistemapos.sistematextil.services.agent.runtime;

import java.text.Normalizer;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sistemapos.sistematextil.services.agent.ai.AgentConversationDecision;
import com.sistemapos.sistematextil.services.agent.ai.AgentConversationDecision.PromotionSelection;
import com.sistemapos.sistematextil.services.agent.ai.AgentCartAction;
import com.sistemapos.sistematextil.services.agent.ai.AgentCommercialAction;
import com.sistemapos.sistematextil.services.agent.ai.AgentDecisionGateway;
import com.sistemapos.sistematextil.services.agent.ai.AgentDecisionIntent;
import com.sistemapos.sistematextil.services.agent.ai.AgentMediaRequest;
import com.sistemapos.sistematextil.services.agent.ai.AgentMediaRequest.MediaType;
import com.sistemapos.sistematextil.model.CrmWhatsappAgentState;
import com.sistemapos.sistematextil.services.agent.catalog.AgentCatalogQueryService;
import com.sistemapos.sistematextil.services.agent.catalog.AgentCatalogQueryService.ProductDetail;
import com.sistemapos.sistematextil.services.agent.catalog.AgentCatalogQueryService.ProductImage;
import com.sistemapos.sistematextil.services.agent.catalog.AgentCatalogQueryService.ProductImageResult;
import com.sistemapos.sistematextil.services.agent.catalog.AgentCatalogQueryService.ProductSearchResult;
import com.sistemapos.sistematextil.services.agent.catalog.AgentCatalogQueryService.StockResult;
import com.sistemapos.sistematextil.services.agent.catalog.AgentCatalogQueryService.VariantResult;
import com.sistemapos.sistematextil.services.agent.cart.AgentCartService;
import com.sistemapos.sistematextil.services.agent.cart.AgentCartService.CartItemView;
import com.sistemapos.sistematextil.services.agent.cart.AgentCartService.CartResult;
import com.sistemapos.sistematextil.services.agent.cart.AgentCartService.ComboSelection;
import com.sistemapos.sistematextil.services.agent.cart.AgentCartService.PendingPromotionDraft;
import com.sistemapos.sistematextil.services.agent.cart.AgentCartService.PendingPromotionSelection;
import com.sistemapos.sistematextil.services.agent.knowledge.AgentKnowledgeRetriever;
import com.sistemapos.sistematextil.services.agent.knowledge.AgentKnowledgeRetriever.KnowledgeSearchResult;
import com.sistemapos.sistematextil.services.agent.promotion.AgentPromotionQueryService;
import com.sistemapos.sistematextil.services.agent.promotion.AgentPromotionQueryService.PromotionSearchResult;
import com.sistemapos.sistematextil.services.agent.promotion.AgentPromotionQueryService.PromotionDetail;
import com.sistemapos.sistematextil.services.agent.promotion.AgentPromotionQueryService.PromotionItem;
import com.sistemapos.sistematextil.services.agent.promotion.AgentPromotionQueryService.PromotionSummary;
import com.sistemapos.sistematextil.services.agent.sizeguide.ProductSizeGuideQueryService;
import com.sistemapos.sistematextil.services.agent.sizeguide.ProductSizeGuideQueryService.BodyMeasurement;
import com.sistemapos.sistematextil.services.agent.sizeguide.ProductSizeGuideQueryService.SizeGuideResult;
import com.sistemapos.sistematextil.services.agent.sizeguide.ProductSizeGuideQueryService.SizeRecommendation;

/**
 * Obtains server-verified evidence before the language model is allowed to
 * answer factual business questions. The model classifies meaning; Java owns
 * every database lookup and records it in the tool audit.
 */
@Service
public class AgentVerifiedGroundingService {
    private static final int AUDIT_ORDINAL_BASE = 10_000;
    private static final int MAX_CART_COLOR_IMAGES = 5;
    private static final int MAX_PRODUCT_COLOR_GALLERY_IMAGES = 5;
    private static final int MAX_INITIAL_CATALOG_IMAGES = 3;
    private static final int MAX_KNOWLEDGE_QUESTION_LENGTH = 500;
    private static final Pattern COMPONENT_WITH_SIZE = Pattern.compile(
            "(?iu)\\b(blazer|saco|chaqueta|chaleco|top|blusa|camisa|polo|casaca|pantal[oó]n|falda|short|"
                    + "parte\\s+de\\s+arriba|parte\\s+superior|parte\\s+de\\s+abajo|parte\\s+inferior)\\b"
                    + "(?:(?!\\b(?:y|con|pero)\\b|[,;/|\\n]).){0,24}?\\b(XXS|XS|S|M|L|XL|XXL|[2-5]XL)\\b");
    private static final Pattern SIZE_WITH_COMPONENT = Pattern.compile(
            "(?iu)\\b(XXS|XS|S|M|L|XL|XXL|[2-5]XL)\\b(?:(?!\\b(?:y|con|pero)\\b|[,;/|\\n]).){0,24}?\\b"
                    + "(blazer|saco|chaqueta|chaleco|top|blusa|camisa|polo|casaca|pantal[oó]n|falda|short|"
                    + "parte\\s+de\\s+arriba|parte\\s+superior|parte\\s+de\\s+abajo|parte\\s+inferior)\\b");
    private static final DateTimeFormatter DELIVERY_DATE = DateTimeFormatter.ofPattern(
            "d 'de' MMMM 'de' uuuu", Locale.forLanguageTag("es-PE"));

    private final AgentDecisionGateway decisions;
    private final AgentCatalogQueryService catalog;
    private final AgentPromotionQueryService promotions;
    private final AgentKnowledgeRetriever knowledge;
    private final AgentCartService cart;
    private final AgentDeterministicCommerceService commerce;
    private final ProductSizeGuideQueryService sizeGuides;
    private final AgentOrderDeliveryQueryService orderDelivery;
    private final AgentToolCallAuditService audit;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Autowired
    public AgentVerifiedGroundingService(AgentDecisionGateway decisions,
            AgentCatalogQueryService catalog,
            AgentPromotionQueryService promotions,
            AgentKnowledgeRetriever knowledge,
            AgentCartService cart,
            AgentDeterministicCommerceService commerce,
            ProductSizeGuideQueryService sizeGuides,
            AgentOrderDeliveryQueryService orderDelivery,
            AgentToolCallAuditService audit) {
        this.decisions = decisions;
        this.catalog = catalog;
        this.promotions = promotions;
        this.knowledge = knowledge;
        this.cart = cart;
        this.commerce = commerce;
        this.sizeGuides = sizeGuides;
        this.orderDelivery = orderDelivery;
        this.audit = audit;
    }

    AgentVerifiedGroundingService(AgentDecisionGateway decisions,
            AgentCatalogQueryService catalog,
            AgentPromotionQueryService promotions,
            AgentKnowledgeRetriever knowledge,
            AgentCartService cart,
            AgentDeterministicCommerceService commerce,
            ProductSizeGuideQueryService sizeGuides,
            AgentToolCallAuditService audit) {
        this(decisions, catalog, promotions, knowledge, cart, commerce, sizeGuides, null, audit);
    }

    AgentVerifiedGroundingService(AgentDecisionGateway decisions, AgentCatalogQueryService catalog,
            AgentPromotionQueryService promotions, AgentKnowledgeRetriever knowledge,
            AgentCartService cart, AgentToolCallAuditService audit) {
        this(decisions, catalog, promotions, knowledge, cart, null, null, null, audit);
    }

    public Grounding ground(Long connectionId, Long sessionId, Long jobId, String conversation) {
        return ground(connectionId, sessionId, jobId, conversation, stateFromConversation(conversation));
    }

    public Grounding ground(Long connectionId, Long sessionId, Long jobId, String conversation,
            CrmWhatsappAgentState state) {
        AgentConversationDecision decision = decisions.interpret(connectionId, conversation).decision();
        if (decision == null || decision.intent() == null) {
            throw new IllegalStateException("Gemini no clasifico la solicitud actual");
        }
        decision = enforceHumanHandoffRules(decision);
        decision = normalizeFreshRetailPurchase(decision, conversation, state);
        if (commerce != null) {
            decision = commerce.normalizeShalomAgencyRefinement(sessionId, decision);
        }

        AtomicInteger ordinal = new AtomicInteger(AUDIT_ORDINAL_BASE);
        Map<String, Object> evidence = new LinkedHashMap<>();
        decision = reconcileExplicitCatalogReferences(
                sessionId, jobId, decision, conversation, ordinal, evidence);
        decision = normalizeIncompleteProductSelection(decision);
        decision = normalizeAvailabilitySelection(decision);
        boolean earlyDeliveryPreference = commerce != null
                && commerce.rememberEarlyDeliveryPreference(sessionId, decision);
        evidence.put("decision", decision);
        List<AgentMediaRequest> media = new ArrayList<>();
        boolean mutationExecuted = false;
        String customerMessage = null;

        boolean deliveryTiming = decision.commercialAction() == AgentCommercialAction.SHARE_DELIVERY_TIMING_CONTACT
                || decision.commercialAction() == AgentCommercialAction.REFER_EXISTING_ORDER_DELIVERY;
        if (deliveryTiming) {
            customerMessage = groundDeliveryTiming(sessionId, jobId, decision, ordinal, evidence);
        }

        var commercial = commerce == null || deliveryTiming
                ? AgentDeterministicCommerceService.Outcome.notHandled()
                : commerce.execute(sessionId, jobId, decision);
        if (commercial.handled()) {
            evidence.put("commercialOperation", commercial);
            mutationExecuted = commercial.mutationExecuted();
            customerMessage = commercial.customerMessage();
        }

        boolean concreteCartEdit = decision.cartAction() == AgentCartAction.ADD_PROMOTION
                || decision.commercialAction() == AgentCommercialAction.CHANGE_CART
                && decision.cartAction() != null && decision.cartAction() != AgentCartAction.NONE;
        if (concreteCartEdit) {
            CartMutation mutation = groundCartChange(
                    sessionId, jobId, decision, conversation, ordinal, evidence, media);
            mutationExecuted = mutationExecuted || mutation.executed();
            customerMessage = mutation.message();
        } else if (!commercial.handled() && decision.commercialAction() == AgentCommercialAction.ADD_PRODUCT) {
            CartMutation mutation = groundCartChange(
                    sessionId, jobId, decision, conversation, ordinal, evidence, media);
            mutationExecuted = mutation.executed();
            customerMessage = mutation.message();
        } else if (!commercial.handled()
                && decision.commercialAction() == AgentCommercialAction.RECOMMEND_SIZE) {
            groundSizeRecommendation(sessionId, jobId, decision, conversation, ordinal, evidence);
        } else if (!commercial.handled()
                && decision.commercialAction() == AgentCommercialAction.SHOW_SIZE_GUIDE) {
            customerMessage = groundSizeGuide(
                    sessionId, jobId, decision, conversation, ordinal, evidence, media);
        } else if (!commercial.handled()
                && (decision.commercialAction() == AgentCommercialAction.SHOW_PROMOTIONS
                || decision.intent() == AgentDecisionIntent.PROMOTION_SEARCH)) {
            groundPromotions(sessionId, jobId, decision, conversation, ordinal, evidence);
        } else if (!commercial.handled()
                && (decision.commercialAction() == AgentCommercialAction.SHOW_PRODUCTS
                || decision.commercialAction() == AgentCommercialAction.SHOW_PRODUCT
                || decision.commercialAction() == AgentCommercialAction.SHOW_PRODUCT_IMAGES)) {
            groundCatalog(sessionId, jobId, decision, conversation, ordinal, evidence, media);
        } else if (!commercial.handled()
                && decision.commercialAction() == AgentCommercialAction.SHOW_PRODUCT_AVAILABILITY) {
            groundProductAvailability(sessionId, jobId, decision, ordinal, evidence);
        } else if (!commercial.handled()) switch (decision.intent()) {
            case PRODUCT_SEARCH, PRODUCT_DETAILS -> groundCatalog(
                    sessionId, jobId, decision, conversation, ordinal, evidence, media);
            case PROMOTION_SEARCH -> groundPromotions(
                    sessionId, jobId, decision, conversation, ordinal, evidence);
            case BUSINESS_INFORMATION -> {
                String question = knowledgeQuestion(decision, conversation);
                KnowledgeSearchResult result = verified(sessionId, jobId, ordinal,
                        "consultar_informacion_negocio", Map.of("pregunta", question),
                        KnowledgeSearchResult.class, () -> knowledge.searchForSession(sessionId, question));
                evidence.put("businessInformation", result);
            }
            case CART_CHANGE -> {
                CartMutation mutation = groundCartChange(
                        sessionId, jobId, decision, conversation, ordinal, evidence, media);
                mutationExecuted = mutation.executed();
                customerMessage = mutation.message();
            }
            default -> {
                // Conversational and transactional turns remain in the normal tool loop.
            }
        }

        boolean catalogIntent = decision.intent() == AgentDecisionIntent.PRODUCT_SEARCH
                || decision.intent() == AgentDecisionIntent.PRODUCT_DETAILS;
        if (catalogIntent && ordinal.get() == AUDIT_ORDINAL_BASE && !mutationExecuted
                && !evidence.containsKey("unresolvedImageProductReference")) {
            // Una consulta comercial nunca debe llegar a redaccion sin datos reales,
            // incluso cuando la decision semantica venga parcialmente incoherente.
            groundCatalog(sessionId, jobId, decision, conversation, ordinal, evidence, media);
            customerMessage = null;
        }
        if (earlyDeliveryPreference && mutationExecuted && customerMessage != null) {
            customerMessage = commerce.appendEarlyDeliveryPreference(customerMessage);
        }
        evidence.put("verifiedMediaCount", media.size());
        int verifiedCalls = ordinal.get() - AUDIT_ORDINAL_BASE;
        return new Grounding(decision, writeCustomerEvidence(evidence), List.copyOf(media), verifiedCalls,
                mutationExecuted, customerMessage);
    }

    AgentConversationDecision enforceHumanHandoffRules(AgentConversationDecision decision) {
        boolean ordinaryCommercialQuestion = decision.intent() == AgentDecisionIntent.PRODUCT_SEARCH
                || decision.intent() == AgentDecisionIntent.PRODUCT_DETAILS
                || decision.intent() == AgentDecisionIntent.PROMOTION_SEARCH
                || decision.intent() == AgentDecisionIntent.BUSINESS_INFORMATION;
        if ((ordinaryCommercialQuestion
                || decision.commercialAction() == AgentCommercialAction.REQUEST_WHOLESALE_SUPPORT
                || decision.commercialAction() == AgentCommercialAction.SHARE_DELIVERY_TIMING_CONTACT
                || decision.commercialAction() == AgentCommercialAction.REFER_EXISTING_ORDER_DELIVERY)
                && decision.requiresHumanHelp()) {
            return decision.withRequiresHumanHelp(false);
        }
        return decision;
    }

    AgentConversationDecision normalizeFreshRetailPurchase(AgentConversationDecision decision,
            String conversation, CrmWhatsappAgentState state) {
        boolean freshCommerceState = state == CrmWhatsappAgentState.IDLE
                || state == CrmWhatsappAgentState.EXPLORANDO_PRODUCTOS
                || state == CrmWhatsappAgentState.ARMANDO_CARRITO;
        boolean completeSelection = !normalize(decision.productReference()).isBlank()
                && !normalize(decision.colorReference()).isBlank()
                && !normalize(decision.sizeReference()).isBlank();
        String input = normalize(currentGroupedInput(conversation));
        boolean explicitRegisteredOrder = input.matches(".*\\b(pedido|venta|compra)\\s*(numero|nro|n|#)?\\s*\\d{3,}\\b.*")
                || input.matches(".*\\b(pedido|venta|compra)\\s+(anterior|registrad[oa]|confirmad[oa]|pagad[oa])\\b.*")
                || input.matches(".*\\b(de|en)\\s+mi\\s+(pedido|venta|compra)\\b.*");
        if (freshCommerceState && completeSelection && !explicitRegisteredOrder
                && (decision.requiresHumanHelp()
                || decision.commercialAction() == AgentCommercialAction.REQUEST_SUPPORT)) {
            return decision.asNewRetailPurchase();
        }
        return decision;
    }

    private CrmWhatsappAgentState stateFromConversation(String conversation) {
        String value = conversation == null ? "" : conversation.toUpperCase(Locale.ROOT);
        for (CrmWhatsappAgentState candidate : CrmWhatsappAgentState.values()) {
            if (value.contains("\"STATE\":\"" + candidate.name() + "\"")
                    || value.contains("ESTADO: " + candidate.name())
                    || value.contains("ESTADO_ACTUAL: " + candidate.name())) {
                return candidate;
            }
        }
        return null;
    }

    private String groundDeliveryTiming(Long sessionId, Long jobId, AgentConversationDecision decision,
            AtomicInteger ordinal, Map<String, Object> evidence) {
        if (decision.commercialAction() == AgentCommercialAction.REFER_EXISTING_ORDER_DELIVERY) {
            AgentOrderDeliveryQueryService.OrderDeliveryGuidance guidance = orderDelivery == null
                    ? new AgentOrderDeliveryQueryService.OrderDeliveryGuidance(false, null,
                            AgentOrderDeliveryQueryService.DeliveryPolicy.NOT_LINKED_TO_AGENT, List.of())
                    : orderDelivery.findForAgentSession(sessionId);
            evidence.put("orderDeliveryGuidance", Map.of(
                    "agentSaleFound", guidance.agentSaleFound(),
                    "saleId", guidance.saleId() == null ? "" : guidance.saleId(),
                    "policy", guidance.policy().name(),
                    "products", guidance.products(),
                    "readyStockDispatch", "1 a 3 días según el volumen de pedidos y el orden de atención",
                    "preorderDispatch", "según la fecha indicada en la preventa",
                    "advisorPhone", "933918047",
                    "shippingAreaWillContact", guidance.agentSaleFound()));
            return null;
        }
        String generic = deliveryTimingPolicy(decision.shalomDestination());
        String reference = decision.productReference() == null ? "" : decision.productReference().trim();
        if (reference.isBlank()) return generic;

        ProductSearchResult search = verified(sessionId, jobId, ordinal,
                "buscar_productos", Map.of("consulta", reference, "limite", 10), ProductSearchResult.class,
                () -> catalog.searchProducts(sessionId, reference, 10));
        evidence.put("deliveryTimingCatalog", search);
        AgentCatalogQueryService.ProductSummary product = unambiguousProduct(search, reference);
        if (product == null) return generic;
        if (!product.preorder() || product.preorderShippingDate() == null) {
            return "Hermosa, el conjunto " + product.name() + " tiene disponibilidad inmediata. Su despacho desde "
                    + "Kiments demora aproximadamente de 1 a 4 días, según el volumen de pedidos. Después del "
                    + "despacho, el tiempo de llegada depende de Shalom, por eso no podemos confirmar un día exacto "
                    + "de entrega. 💛";
        }

        return "Hermosa, el conjunto " + product.name() + " está en preventa y sus despachos comienzan a partir "
                + "del " + DELIVERY_DATE.format(product.preorderShippingDate()) + ", según el volumen de pedidos. "
                + "No es posible enviarlo antes de esa fecha. Después del despacho, el tiempo de llegada depende "
                + "de Shalom, por eso no podemos confirmar un día exacto de entrega. 💛";
    }

    private String deliveryTimingPolicy(String destination) {
        String place = destination == null || destination.isBlank()
                ? "por Shalom"
                : "a " + destination.trim() + " por Shalom";
        return "Sí realizamos envíos " + place + ", hermosa 💛\n"
                + "La fecha de despacho depende del producto:\n"
                + "• Disponibilidad inmediata: despachamos aproximadamente en 1 a 4 días, según el volumen de pedidos.\n"
                + "• Preventa: el despacho comienza desde la fecha indicada para el producto.\n\n"
                + "Después del despacho, el tiempo de llegada depende de Shalom, por eso no podemos confirmar un día "
                + "exacto de entrega.";
    }

    private AgentConversationDecision normalizeIncompleteProductSelection(AgentConversationDecision decision) {
        boolean productInquiry = decision.intent() == AgentDecisionIntent.PRODUCT_DETAILS
                || decision.intent() == AgentDecisionIntent.PRODUCT_SEARCH;
        boolean productKnown = decision.productReference() != null && !decision.productReference().isBlank();
        boolean variantIncomplete = decision.sizeReference() == null || decision.sizeReference().isBlank();
        boolean accidentalCartTransition = decision.commercialAction() == AgentCommercialAction.CHANGE_CART
                && (decision.cartAction() == null || decision.cartAction() == AgentCartAction.NONE);
        if (productInquiry && productKnown && variantIncomplete && accidentalCartTransition) {
            return decision.withCommercialAction(AgentCommercialAction.SHOW_PRODUCT);
        }
        return decision;
    }

    private AgentConversationDecision normalizeAvailabilitySelection(AgentConversationDecision decision) {
        boolean hasRequestedSize = decision.sizeReference() != null && !decision.sizeReference().isBlank();
        if (decision.commercialAction() == AgentCommercialAction.SHOW_PRODUCT && hasRequestedSize) {
            return decision.withCommercialAction(AgentCommercialAction.SHOW_PRODUCT_AVAILABILITY);
        }
        return decision;
    }

    private void groundProductAvailability(Long sessionId, Long jobId, AgentConversationDecision decision,
            AtomicInteger ordinal, Map<String, Object> evidence) {
        List<String> references = decision.productReferences() == null
                ? new ArrayList<>() : new ArrayList<>(decision.productReferences());
        if (references.isEmpty() && decision.productReference() != null
                && !decision.productReference().isBlank()) {
            references.add(decision.productReference().trim());
        }
        if (references.isEmpty()) return;

        Map<Integer, AgentCatalogQueryService.ProductSummary> products = new LinkedHashMap<>();
        List<Map<String, Object>> searches = new ArrayList<>();
        List<String> unresolved = new ArrayList<>();
        for (String reference : references) {
            ProductSearchResult search = verified(sessionId, jobId, ordinal,
                    "buscar_productos", Map.of("consulta", reference, "limite", 20,
                            "disponibilidad", decision.catalogAvailability().name()),
                    ProductSearchResult.class,
                    () -> catalog.searchProducts(sessionId, reference, 20, decision.catalogAvailability()));
            List<AgentCatalogQueryService.ProductSummary> resolved = new ArrayList<>();
            var exact = unambiguousProduct(search, reference);
            if (exact != null) resolved.add(exact);
            else resolved.addAll(requestedProducts(search, List.of(reference)));
            searches.add(Map.of("reference", reference, "resolvedProducts",
                    resolved.stream().map(AgentCatalogQueryService.ProductSummary::name).toList()));
            if (resolved.isEmpty()) unresolved.add(reference);
            resolved.forEach(product -> products.putIfAbsent(product.productId(), product));
        }
        evidence.put("availabilitySearches", searches);

        List<ProductAvailabilityView> availability = new ArrayList<>();
        for (var product : products.values()) {
            VariantResult variants = verified(sessionId, jobId, ordinal,
                    "consultar_variantes", nullableMap("productoId", product.productId(), "color",
                            decision.colorReference(), "talla", decision.sizeReference()),
                    VariantResult.class, () -> catalog.getVariants(sessionId, product.productId(),
                            decision.colorReference(), decision.sizeReference()));
            List<AgentCatalogQueryService.VariantSummary> availableVariants = variants.variants() == null
                    ? List.of() : variants.variants().stream()
                            .filter(AgentCatalogQueryService.VariantSummary::available)
                            .filter(variant -> variant.stock() > 0)
                            .toList();
            Double price = availableVariants.stream()
                    .map(AgentCatalogQueryService.VariantSummary::effectivePrice)
                    .filter(Objects::nonNull)
                    .min(Double::compareTo)
                    .orElse(product.priceFrom());
            availability.add(new ProductAvailabilityView(
                    product.productId(), product.name(), price, product.productPageUrl(), availableVariants));
        }
        evidence.put("requestedVariantAvailability", Map.of(
                "requestedSize", clean(decision.sizeReference()),
                "requestedColor", clean(decision.colorReference()),
                "products", availability,
                "unresolvedReferences", unresolved));
        // La evidencia verificada queda estructurada; Gemini redacta la respuesta final.
    }

    private void groundSizeRecommendation(Long sessionId, Long jobId, AgentConversationDecision decision,
            String conversation, AtomicInteger ordinal, Map<String, Object> evidence) {
        if (sizeGuides == null) return;
        String query = firstPresent(decision.productReference(), decision.customerGoal(), conversation);
        ProductSearchResult search = verified(sessionId, jobId, ordinal,
                "buscar_productos", Map.of("consulta", query, "limite", 10), ProductSearchResult.class,
                () -> catalog.searchProducts(sessionId, query, 10));
        evidence.put("productSearch", search);
        var selected = unambiguousProduct(search, decision.productReference());
        if (selected == null) return;
        List<BodyMeasurement> measurements = decision.bodyMeasurements().stream()
                .map(item -> new BodyMeasurement(item.name(), item.value(), item.unit())).toList();
        if (measurements.isEmpty()) {
            SizeGuideResult guide = verified(sessionId, jobId, ordinal,
                    "obtener_guia_tallas", Map.of("productoId", selected.productId()),
                    SizeGuideResult.class, () -> sizeGuides.getGuide(sessionId, selected.productId()));
            evidence.put("sizeGuide", guide);
            evidence.put("sizeRecommendationMissingInput",
                    "Solicita una medida corporal concreta disponible en la guia; no recomiendes una talla aun");
            return;
        }
        SizeRecommendation recommendation = verified(sessionId, jobId, ordinal,
                "recomendar_talla", nullableMap("productoId", selected.productId(), "componente",
                        decision.sizeGuideComponent(), "medidasCorporales", measurements,
                        "preferenciaAjuste", decision.fitPreference(), "color", decision.colorReference()),
                SizeRecommendation.class,
                () -> sizeGuides.recommend(sessionId, selected.productId(), decision.sizeGuideComponent(),
                        measurements, decision.fitPreference(), decision.colorReference()));
        evidence.put("sizeRecommendation", recommendation);
        if (recommendation.components() != null && recommendation.components().size() > 1) {
            evidence.put("sizeRecommendationBusinessRule",
                    "Este producto se vende como conjunto completo en una sola talla. "
                    + "Comunica una unica talla para todo el conjunto; nunca ofrezcas combinar tallas entre piezas. "
                    + "Si la talla orientada no tiene stock en el color elegido, indicalo sin sustituirla por otra talla.");
        }
    }

    private String groundSizeGuide(Long sessionId, Long jobId, AgentConversationDecision decision,
            String conversation, AtomicInteger ordinal, Map<String, Object> evidence,
            List<AgentMediaRequest> media) {
        String currentInput = currentGroupedInput(conversation);
        List<String> references = requestedProductReferences(decision, currentInput);
        String query = references.isEmpty()
                ? firstPresent(decision.customerGoal(), currentInput, conversation)
                : String.join(" ", references);
        ProductSearchResult search = verified(sessionId, jobId, ordinal,
                "buscar_productos", Map.of("consulta", query, "limite", 10), ProductSearchResult.class,
                () -> catalog.searchProducts(sessionId, query, 10));
        evidence.put("productSearch", search);
        List<AgentCatalogQueryService.ProductSummary> selected = requestedProducts(search, references);
        if (selected.isEmpty()) {
            var single = unambiguousProduct(search, decision.productReference());
            if (single != null) selected = List.of(single);
        }
        if (selected.isEmpty()) return null;

        List<String> sent = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        List<Map<String, Object>> guideEvidence = new ArrayList<>();
        for (var product : selected) {
            ProductDetail detail = verified(sessionId, jobId, ordinal,
                    "obtener_producto", Map.of("productoId", product.productId()),
                    ProductDetail.class, () -> catalog.getProduct(sessionId, product.productId()));
            boolean available = detail.sizeGuideUrl() != null && !detail.sizeGuideUrl().isBlank();
            guideEvidence.add(Map.of("productId", product.productId(), "productName", product.name(),
                    "sizeGuideAvailable", available));
            if (available) {
                media.add(new AgentMediaRequest(MediaType.SIZE_GUIDE, product.productId(), null, false));
                sent.add(product.name());
            } else {
                missing.add(product.name());
            }
        }
        evidence.put("requestedSizeGuides", guideEvidence);
        evidence.put("sizeGuideRepeatSuppression", false);
        evidence.put("sizeGuideDelivery", Map.of(
                "sent", List.copyOf(sent),
                "missing", List.copyOf(missing)));
        return null;
    }

    private void groundPromotions(Long sessionId, Long jobId, AgentConversationDecision decision,
            String conversation, AtomicInteger ordinal, Map<String, Object> evidence) {
        // La decision semantica ya determino que se solicitan promociones. La frase
        // completa del cliente no es un filtro de catalogo: solo filtramos cuando
        // Gemini extrajo una promocion o un producto concretos. Sin referencia, la
        // herramienta devuelve las promociones vigentes de la sucursal.
        String query = firstPresent(decision.promotionReference(), decision.productReference());
        PromotionSearchResult result = verified(sessionId, jobId, ordinal,
                "buscar_promociones", Map.of("consulta", query, "limite", 10),
                PromotionSearchResult.class, () -> promotions.searchPromotions(sessionId, query, 10));
        evidence.put("promotions", result);
    }

    private CartMutation groundCartChange(Long sessionId, Long jobId, AgentConversationDecision decision,
            String conversation, AtomicInteger ordinal, Map<String, Object> evidence,
            List<AgentMediaRequest> media) {
        String currentInput = currentGroupedInput(conversation);
        if (requestsDifferentSizesForSetPieces(currentInput)) {
            evidence.put("cartOperation", Map.of("executed", false,
                    "reason", "El conjunto fue solicitado con tallas diferentes por pieza",
                    "requestedComponentSizes", componentSizes(currentInput)));
            return new CartMutation(false,
                    "Preciosa, nuestros conjuntos se venden completos, por lo que todas sus prendas deben ser "
                    + "de la misma talla. No es posible combinar tallas diferentes. Indícame una sola talla "
                    + "para todo el conjunto. 💛");
        }
        if (commerce != null) commerce.prepareCartMutation(sessionId);
        AgentCartAction action = decision.cartAction() == null ? AgentCartAction.NONE : decision.cartAction();
        if (action == AgentCartAction.ADD_PROMOTION) {
            return groundPromotionAdd(sessionId, jobId, decision, conversation, ordinal, evidence);
        }
        if (action == AgentCartAction.REPLACE_CART) {
            return groundCartReplacement(sessionId, jobId, decision, ordinal, evidence, media);
        }
        if (action == AgentCartAction.ADD_PRODUCT && decision.promotionSelections() != null
                && decision.promotionSelections().size() > 1) {
            return groundMultipleProductAdd(sessionId, jobId, decision, ordinal, evidence, media);
        }
        if (action == AgentCartAction.CLEAR_CART) {
            CartResult result = verified(sessionId, jobId, ordinal,
                    "vaciar_carrito", Map.of(), CartResult.class,
                    () -> cart.emptyCart(sessionId, "agent-job-" + jobId + ":clear-cart"));
            evidence.put("cartOperation", Map.of("executed", true, "result", result));
            return new CartMutation(true, "Tu carrito quedó vacío. ¿Qué producto deseas elegir?");
        }
        if (action == AgentCartAction.CHANGE_QUANTITY
                || action == AgentCartAction.KEEP_ONLY_PRODUCT
                || action == AgentCartAction.CHANGE_VARIANT
                || action == AgentCartAction.REPLACE_PRODUCT
                || action == AgentCartAction.REMOVE_PRODUCT) {
            return groundExistingCartChange(sessionId, jobId, decision, ordinal, evidence, action);
        }
        if (action != AgentCartAction.ADD_PRODUCT) {
            evidence.put("cartOperation", Map.of("executed", false,
                    "reason", "La accion o la cantidad todavia requiere aclaracion"));
            return new CartMutation(false,
                    "Indícame qué producto del carrito deseas cambiar y cuál debe ser el resultado final.");
        }
        int quantity = decision.quantity() == null || decision.quantity() < 1 ? 1 : decision.quantity();
        String query = firstPresent(decision.productReference(), decision.customerGoal(), conversation);
        ProductSearchResult search = verified(sessionId, jobId, ordinal,
                "buscar_productos", Map.of("consulta", query, "limite", 10),
                ProductSearchResult.class, () -> catalog.searchProducts(sessionId, query, 10));
        evidence.put("productSearch", search);
        var selected = unambiguousProduct(search, decision.productReference());
        if (selected == null) {
            evidence.put("cartOperation", Map.of("executed", false,
                    "reason", "No existe un unico producto validado"));
            return new CartMutation(false, null);
        }
        VariantResult variants = verified(sessionId, jobId, ordinal,
                "consultar_variantes", nullableMap("productoId", selected.productId(), "color",
                        decision.colorReference(), "talla", decision.sizeReference()),
                VariantResult.class, () -> catalog.getVariants(sessionId, selected.productId(),
                        decision.colorReference(), decision.sizeReference()));
        evidence.put("variants", variants);
        if (variants.variants() == null || variants.variants().size() != 1) {
            evidence.put("cartOperation", Map.of("executed", false,
                    "reason", "Falta elegir una variante unica de color y talla"));
            return new CartMutation(false, null);
        }
        Integer variantId = variants.variants().getFirst().variantId();
        StockResult stock = verified(sessionId, jobId, ordinal,
                "consultar_stock", Map.of("varianteId", variantId, "cantidad", quantity),
                StockResult.class, () -> catalog.getStock(sessionId, variantId, quantity));
        evidence.put("stock", stock);
        if (!Boolean.TRUE.equals(stock.sufficient())) {
            evidence.put("cartOperation", Map.of("executed", false,
                    "reason", "El stock no alcanza para la cantidad solicitada"));
            return new CartMutation(false, null);
        }
        String operationId = "agent-job-" + jobId + ":verified-add-product";
        CartResult result = verified(sessionId, jobId, ordinal,
                "agregar_producto_carrito", Map.of("varianteId", variantId, "cantidad", quantity),
                CartResult.class, () -> cart.addProduct(sessionId, variantId, quantity, operationId));
        evidence.put("cartOperation", Map.of("executed", true, "result", result));
        queueVerifiedCartColorImage(sessionId, jobId, selected.productId(), selected.name(),
                variants.variants().getFirst().color(), ordinal, evidence, media);
        String message = commerce == null
                ? "Producto agregado correctamente al carrito."
                : commerce.cartTemplate(sessionId, result, true);
        return new CartMutation(true, message);
    }

    private CartMutation groundMultipleProductAdd(Long sessionId,Long jobId,AgentConversationDecision decision,
            AtomicInteger ordinal,Map<String,Object> evidence,List<AgentMediaRequest> media){
        CartResult result=null;int index=0;List<CartImageCandidate> imageCandidates=new ArrayList<>();
        for(PromotionSelection selection:decision.promotionSelections()){
            int quantity=selection.quantity()==null||selection.quantity()<1?1:selection.quantity();
            ProductSearchResult search=verified(sessionId,jobId,ordinal,"buscar_productos",
                    Map.of("consulta",selection.productReference(),"limite",10),ProductSearchResult.class,
                    ()->catalog.searchProducts(sessionId,selection.productReference(),10));
            var product=unambiguousProduct(search,selection.productReference());
            if(product==null)return new CartMutation(false,null);
            VariantResult variants=verified(sessionId,jobId,ordinal,"consultar_variantes",
                    nullableMap("productoId",product.productId(),"color",selection.colorReference(),"talla",selection.sizeReference()),
                    VariantResult.class,()->catalog.getVariants(sessionId,product.productId(),selection.colorReference(),selection.sizeReference()));
            if(variants.variants()==null||variants.variants().size()!=1)return new CartMutation(false,null);
            Integer variantId=variants.variants().getFirst().variantId();
            StockResult stock=verified(sessionId,jobId,ordinal,"consultar_stock",Map.of("varianteId",variantId,"cantidad",quantity),
                    StockResult.class,()->catalog.getStock(sessionId,variantId,quantity));
            if(!Boolean.TRUE.equals(stock.sufficient()))return new CartMutation(false,null);
            String operation="agent-job-"+jobId+":verified-add-product:"+(index++);
            result=verified(sessionId,jobId,ordinal,"agregar_producto_carrito",Map.of("varianteId",variantId,"cantidad",quantity),
                    CartResult.class,()->cart.addProduct(sessionId,variantId,quantity,operation));
            imageCandidates.add(new CartImageCandidate(product.productId(),product.name(),variants.variants().getFirst().color()));
        }
        evidence.put("cartOperation",Map.of("executed",true,"result",result));
        for(CartImageCandidate candidate:imageCandidates){
            queueVerifiedCartColorImage(sessionId,jobId,candidate.productId(),candidate.productName(),candidate.color(),
                    ordinal,evidence,media);
        }
        return new CartMutation(true,commerce==null?"Productos agregados correctamente al carrito.":commerce.cartTemplate(sessionId,result,true));
    }

    private void queueVerifiedCartColorImage(Long sessionId, Long jobId, Integer productId,
            String productName, String color, AtomicInteger ordinal, Map<String, Object> evidence,
            List<AgentMediaRequest> media) {
        String requestedColor = color == null ? "" : color.trim();
        if (productId == null || requestedColor.isBlank() || media.size() >= MAX_CART_COLOR_IMAGES) return;
        boolean duplicate = media.stream().anyMatch(item -> item.type() == MediaType.PRODUCT_IMAGE
                && productId.equals(item.productId())
                && normalize(item.color()).equals(normalize(requestedColor)));
        if (duplicate) return;
        ProductImageResult images = verified(sessionId, jobId, ordinal,
                "obtener_imagen_producto", nullableMap("productoId", productId, "color", requestedColor),
                ProductImageResult.class,
                () -> catalog.getProductImage(sessionId, productId, requestedColor));
        evidence.put("cartProductImage" + productId + normalize(requestedColor), nullableMap(
                "productId", productId, "productName", productName, "color", requestedColor,
                "exactColorImageAvailable", images != null && images.colorImages() != null
                        && !images.colorImages().isEmpty()));
        if (images != null && images.colorImages() != null && !images.colorImages().isEmpty()) {
            media.add(new AgentMediaRequest(MediaType.PRODUCT_IMAGE, productId, requestedColor, true));
        }
    }

    private CartMutation groundPromotionAdd(Long sessionId, Long jobId, AgentConversationDecision decision,
            String conversation, AtomicInteger ordinal, Map<String, Object> evidence) {
        String query = firstPresent(decision.promotionReference(), decision.customerGoal(), conversation);
        PromotionSearchResult search = verified(sessionId, jobId, ordinal,
                "buscar_promociones", Map.of("consulta", query, "limite", 20),
                PromotionSearchResult.class, () -> promotions.searchPromotions(sessionId, query, 20));
        evidence.put("promotionSearch", search);
        PromotionSummary selected = unambiguousPromotion(search,
                decision.promotionReference(), decision.customerGoal(), query);
        if (selected == null) {
            evidence.put("cartOperation", Map.of("executed", false,
                    "reason", "No existe una unica promocion validada"));
            return new CartMutation(false, null);
        }

        PromotionDetail detail = verified(sessionId, jobId, ordinal,
                "obtener_promocion", Map.of("promocionId", selected.promotionId()),
                PromotionDetail.class, () -> promotions.getPromotion(sessionId, selected.promotionId()));
        evidence.put("promotion", detail);

        List<PromotionSelection> incoming = decision.promotionSelections() == null
                ? List.of() : decision.promotionSelections();
        List<PromotionSelection> requested = mergePendingPromotionSelections(
                cart.pendingPromotion(sessionId).orElse(null), detail, incoming);
        Map<Integer, RequiredPromotionProduct> requirements = new LinkedHashMap<>();
        for (PromotionItem item : detail.items()) {
            requirements.merge(item.productId(),
                    new RequiredPromotionProduct(item.productId(), item.productName(), item.requiredQuantity()),
                    (left, right) -> new RequiredPromotionProduct(left.productId(), left.productName(),
                            left.quantity() + right.quantity()));
        }
        List<ComboSelection> resolved = new ArrayList<>();
        List<PendingPromotionSelection> pending = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (RequiredPromotionProduct required : requirements.values()) {
            List<PromotionSelection> productSelections = requested.stream()
                    .filter(selection -> productMatches(selection.productReference(), required.productName()))
                    .toList();
            int selectedQuantity = productSelections.stream()
                    .mapToInt(selection -> selection.quantity() == null || selection.quantity() < 1
                            ? 1 : selection.quantity())
                    .sum();
            if (selectedQuantity != required.quantity()) {
                for (PromotionSelection selection : productSelections) {
                    pending.add(pendingSelection(required, selection, null));
                }
                missing.add(required.quantity() + " x " + required.productName());
                continue;
            }
            boolean validProduct = true;
            for (PromotionSelection selection : productSelections) {
                int quantity = selection.quantity() == null || selection.quantity() < 1 ? 1 : selection.quantity();
                if (selection.colorReference().isBlank() || selection.sizeReference().isBlank()) {
                    pending.add(pendingSelection(required, selection, null));
                    validProduct = false;
                    break;
                }
                VariantResult variants = verified(sessionId, jobId, ordinal,
                        "consultar_variantes", nullableMap("productoId", required.productId(), "color",
                                selection.colorReference(), "talla", selection.sizeReference()),
                        VariantResult.class, () -> catalog.getVariants(sessionId, required.productId(),
                                selection.colorReference(), selection.sizeReference()));
                if (variants.variants() == null || variants.variants().size() != 1) {
                    pending.add(pendingSelection(required, selection, null));
                    validProduct = false;
                    break;
                }
                Integer variantId = variants.variants().getFirst().variantId();
                StockResult stock = verified(sessionId, jobId, ordinal,
                        "consultar_stock", Map.of("varianteId", variantId, "cantidad", quantity),
                        StockResult.class, () -> catalog.getStock(sessionId, variantId, quantity));
                if (!Boolean.TRUE.equals(stock.sufficient())) {
                    pending.add(pendingSelection(required, selection, variantId));
                    validProduct = false;
                    break;
                }
                resolved.add(new ComboSelection(variantId, quantity));
                pending.add(pendingSelection(required, selection, variantId));
            }
            if (!validProduct) missing.add(required.quantity() + " x " + required.productName());
        }
        if (!missing.isEmpty()) {
            cart.savePendingPromotion(sessionId, detail.promotionId(), detail.name(), pending, jobId);
            evidence.put("pendingPromotion", Map.of("promotionId", detail.promotionId(),
                    "name", detail.name(), "selections", pending));
            evidence.put("cartOperation", Map.of("executed", false,
                    "reason", "Faltan variantes validas para completar la promocion", "missing", missing));
            return new CartMutation(false, null);
        }

        String operationId = "agent-job-" + jobId + ":verified-add-promotion";
        CartResult result = verified(sessionId, jobId, ordinal,
                "agregar_combo_carrito", Map.of("promocionId", detail.promotionId(), "selecciones", resolved),
                CartResult.class, () -> cart.addCombo(sessionId, detail.promotionId(), resolved, operationId));
        evidence.put("cartOperation", Map.of("executed", true, "result", result));
        String message = commerce == null ? "Promocion agregada correctamente al carrito."
                : commerce.cartTemplate(sessionId, result, true);
        return new CartMutation(true, message);
    }

    private List<PromotionSelection> mergePendingPromotionSelections(PendingPromotionDraft existing,
            PromotionDetail detail, List<PromotionSelection> incoming) {
        List<PromotionSelection> merged = new ArrayList<>();
        if (existing != null && Objects.equals(existing.promotionId(), detail.promotionId())) {
            existing.selections().forEach(selection -> merged.add(new PromotionSelection(
                    selection.productName(), selection.colorReference(), selection.sizeReference(),
                    selection.quantity())));
        }
        for (PromotionItem required : detail.items()) {
            List<PromotionSelection> replacements = incoming.stream()
                    .filter(selection -> productMatches(selection.productReference(), required.productName()))
                    .toList();
            if (replacements.isEmpty()) continue;
            merged.removeIf(selection -> productMatches(selection.productReference(), required.productName()));
            merged.addAll(replacements);
        }
        return merged;
    }

    private PendingPromotionSelection pendingSelection(RequiredPromotionProduct required,
            PromotionSelection selection, Integer variantId) {
        int quantity = selection.quantity() == null || selection.quantity() < 1 ? 1 : selection.quantity();
        return new PendingPromotionSelection(required.productId(), required.productName(),
                selection.colorReference(), selection.sizeReference(), quantity, variantId);
    }

    private int selectedQuantity(List<PromotionSelection> selections, String productName) {
        return selections.stream()
                .filter(selection -> productMatches(selection.productReference(), productName))
                .mapToInt(selection -> selection.quantity() == null || selection.quantity() < 1
                        ? 1 : selection.quantity())
                .sum();
    }

    private CartMutation groundCartReplacement(Long sessionId, Long jobId,
            AgentConversationDecision decision, AtomicInteger ordinal, Map<String, Object> evidence,
            List<AgentMediaRequest> media) {
        List<PromotionSelection> requested = decision.promotionSelections() == null
                ? List.of() : decision.promotionSelections();
        if (requested.isEmpty()) {
            evidence.put("cartOperation", Map.of("executed", false,
                    "reason", "El pedido final no contiene productos estructurados"));
            return new CartMutation(false, null);
        }

        List<ComboSelection> resolved = new ArrayList<>();
        List<CartImageCandidate> images = new ArrayList<>();
        for (PromotionSelection selection : requested) {
            int quantity = selection.quantity() == null || selection.quantity() < 1 ? 1 : selection.quantity();
            ProductSearchResult search = verified(sessionId, jobId, ordinal,
                    "buscar_productos", Map.of("consulta", selection.productReference(), "limite", 10),
                    ProductSearchResult.class,
                    () -> catalog.searchProducts(sessionId, selection.productReference(), 10));
            var product = unambiguousProduct(search, selection.productReference());
            if (product == null) {
                evidence.put("cartOperation", Map.of("executed", false,
                        "reason", "Un producto del pedido final es ambiguo",
                        "productReference", selection.productReference()));
                return new CartMutation(false, null);
            }
            VariantResult variants = verified(sessionId, jobId, ordinal,
                    "consultar_variantes", nullableMap("productoId", product.productId(),
                            "color", selection.colorReference(), "talla", selection.sizeReference()),
                    VariantResult.class, () -> catalog.getVariants(sessionId, product.productId(),
                            selection.colorReference(), selection.sizeReference()));
            if (variants.variants() == null || variants.variants().size() != 1) {
                evidence.put("cartOperation", Map.of("executed", false,
                        "reason", "Una variante del pedido final es ambigua",
                        "product", product.name()));
                return new CartMutation(false, null);
            }
            Integer variantId = variants.variants().getFirst().variantId();
            StockResult stock = verified(sessionId, jobId, ordinal,
                    "consultar_stock", Map.of("varianteId", variantId, "cantidad", quantity),
                    StockResult.class, () -> catalog.getStock(sessionId, variantId, quantity));
            if (!Boolean.TRUE.equals(stock.sufficient())) {
                evidence.put("cartOperation", Map.of("executed", false,
                        "reason", "Una variante del pedido final no tiene stock suficiente",
                        "product", product.name()));
                return new CartMutation(false, null);
            }
            resolved.add(new ComboSelection(variantId, quantity));
            images.add(new CartImageCandidate(product.productId(), product.name(),
                    variants.variants().getFirst().color()));
        }

        Integer expectedPromotionId = null;
        if (decision.promotionReference() != null && !decision.promotionReference().isBlank()) {
            PromotionSearchResult search = verified(sessionId, jobId, ordinal,
                    "buscar_promociones", Map.of("consulta", decision.promotionReference(), "limite", 20),
                    PromotionSearchResult.class,
                    () -> promotions.searchPromotions(sessionId, decision.promotionReference(), 20));
            PromotionSummary promotion = unambiguousPromotion(search, decision.promotionReference(),
                    decision.customerGoal(), decision.promotionReference());
            if (promotion == null) {
                evidence.put("cartOperation", Map.of("executed", false,
                        "reason", "La promocion indicada para el pedido final es ambigua"));
                return new CartMutation(false, null);
            }
            expectedPromotionId = promotion.promotionId();
        }

        Integer promotionId = expectedPromotionId;
        CartResult result = verified(sessionId, jobId, ordinal,
                "reemplazar_carrito", nullableMap("selecciones", resolved,
                        "promocionId", promotionId), CartResult.class,
                () -> cart.replaceCart(sessionId, resolved, promotionId,
                        "agent-job-" + jobId + ":verified-replace-cart"));
        evidence.put("cartOperation", Map.of("executed", true, "result", result));
        for (CartImageCandidate image : images.stream().limit(MAX_CART_COLOR_IMAGES).toList()) {
            queueVerifiedCartColorImage(sessionId, jobId, image.productId(), image.productName(),
                    image.color(), ordinal, evidence, media);
        }
        String message = commerce == null ? "Carrito reemplazado correctamente."
                : commerce.cartTemplate(sessionId, result, true);
        return new CartMutation(true, message);
    }

    private CartMutation groundExistingCartChange(Long sessionId, Long jobId,
            AgentConversationDecision decision, AtomicInteger ordinal, Map<String, Object> evidence,
            AgentCartAction action) {
        CartResult current = verified(sessionId, jobId, ordinal,
                "recalcular_carrito", Map.of(), CartResult.class,
                () -> cart.recalculate(sessionId, "agent-job-" + jobId + ":read-cart"));
        CartItemView selected = action == AgentCartAction.REPLACE_PRODUCT
                ? selectCartItemForReplacement(current, decision)
                : selectCartItem(current, decision.productReference());
        if (selected == null) {
            evidence.put("cartOperation", Map.of("executed", false,
                    "reason", "No se pudo identificar un unico producto del carrito"));
            return new CartMutation(false, currentCartClarification(sessionId, current,
                    "Indícame exactamente cuál producto deseas modificar."));
        }

        String operation = "agent-job-" + jobId + ":" + action.name().toLowerCase(Locale.ROOT);
        CartResult result;
        if (action == AgentCartAction.REMOVE_PRODUCT) {
            result = verified(sessionId, jobId, ordinal,
                    "eliminar_producto_carrito", Map.of("varianteId", selected.variantId()),
                    CartResult.class, () -> cart.removeProduct(sessionId, selected.variantId(), operation));
        } else if (action == AgentCartAction.KEEP_ONLY_PRODUCT) {
            result = verified(sessionId, jobId, ordinal,
                    "conservar_solo_producto_carrito",
                    nullableMap("varianteId", selected.variantId(), "cantidad", decision.quantity()),
                    CartResult.class,
                    () -> cart.keepOnlyProduct(sessionId, selected.variantId(), decision.quantity(), operation));
        } else if (action == AgentCartAction.CHANGE_QUANTITY) {
            if (decision.quantity() == null || decision.quantity() < 1) {
                evidence.put("cartOperation", Map.of("executed", false,
                        "reason", "Falta indicar la nueva cantidad", "item", selected));
                return new CartMutation(false, currentCartClarification(sessionId, current,
                        "Indícame la cantidad final que deseas para " + selected.product() + "."));
            }
            result = verified(sessionId, jobId, ordinal,
                    "cambiar_cantidad_carrito",
                    Map.of("varianteId", selected.variantId(), "cantidad", decision.quantity()),
                    CartResult.class,
                    () -> cart.changeQuantity(sessionId, selected.variantId(), decision.quantity(), operation));
        } else {
            StockResult currentStock = verified(sessionId, jobId, ordinal,
                    "consultar_stock", Map.of("varianteId", selected.variantId(), "cantidad", selected.quantity()),
                    StockResult.class,
                    () -> catalog.getStock(sessionId, selected.variantId(), selected.quantity()));
            Integer targetProductId = currentStock.productId();
            if (action == AgentCartAction.REPLACE_PRODUCT) {
                ProductSearchResult search = verified(sessionId, jobId, ordinal,
                        "buscar_productos", Map.of("consulta", decision.productReference(), "limite", 10),
                        ProductSearchResult.class,
                        () -> catalog.searchProducts(sessionId, decision.productReference(), 10));
                evidence.put("replacementProductSearch", search);
                var replacement = unambiguousProduct(search, decision.productReference());
                if (replacement == null || Objects.equals(replacement.productId(), currentStock.productId())) {
                    evidence.put("cartOperation", Map.of("executed", false,
                            "reason", "Falta validar un modelo nuevo diferente", "item", selected));
                    return new CartMutation(false, currentCartClarification(sessionId, current,
                            "Indícame el nuevo modelo, color y talla que deseas."));
                }
                targetProductId = replacement.productId();
            }
            String color = firstPresent(decision.colorReference(), selected.color());
            String size = firstPresent(decision.sizeReference(), selected.size());
            Integer verifiedTargetProductId = targetProductId;
            VariantResult variants = verified(sessionId, jobId, ordinal,
                    "consultar_variantes", nullableMap("productoId", verifiedTargetProductId, "color", color,
                            "talla", size),
                    VariantResult.class,
                    () -> catalog.getVariants(sessionId, verifiedTargetProductId, color, size));
            if (variants.variants() == null || variants.variants().size() != 1) {
                evidence.put("cartOperation", Map.of("executed", false,
                        "reason", "Falta validar una unica variante nueva", "item", selected,
                        "variants", variants));
                return new CartMutation(false, currentCartClarification(sessionId, current,
                        "Indícame un color y una talla disponibles para realizar el cambio."));
            }
            Integer newVariantId = variants.variants().getFirst().variantId();
            if (Objects.equals(newVariantId, selected.variantId())) {
                evidence.put("cartOperation", Map.of("executed", false,
                        "reason", "La variante solicitada ya es la variante actual", "item", selected));
                return new CartMutation(false, currentCartClarification(sessionId, current,
                        "Ese producto ya tiene el color y la talla indicados."));
            }
            if (action == AgentCartAction.REPLACE_PRODUCT) {
                result = verified(sessionId, jobId, ordinal,
                        "reemplazar_producto_carrito",
                        Map.of("varianteActualId", selected.variantId(), "varianteNuevaId", newVariantId),
                        CartResult.class,
                        () -> cart.replaceProduct(sessionId, selected.variantId(), newVariantId, operation));
            } else {
                result = verified(sessionId, jobId, ordinal,
                        "cambiar_variante_carrito",
                        Map.of("varianteActualId", selected.variantId(), "varianteNuevaId", newVariantId),
                        CartResult.class,
                        () -> cart.changeVariant(sessionId, selected.variantId(), newVariantId, operation));
            }
        }
        evidence.put("cartOperation", Map.of("executed", true, "result", result));
        String message = result.items().isEmpty()
                ? "Tu carrito quedó vacío. ¿Qué producto deseas elegir?"
                : commerce == null ? "Carrito actualizado correctamente."
                : commerce.cartTemplate(sessionId, result, true);
        return new CartMutation(true, message);
    }

    private String currentCartClarification(Long sessionId, CartResult current, String clarification) {
        if (current == null || current.items() == null || current.items().isEmpty()) return clarification;
        String summary = commerce == null ? "El carrito no fue modificado."
                : commerce.cartTemplate(sessionId, current, false);
        return summary + "\n\n" + clarification;
    }

    private CartItemView selectCartItem(CartResult cartResult, String productReference) {
        if (cartResult == null || cartResult.items() == null || cartResult.items().isEmpty()) return null;
        String reference = normalize(productReference);
        if (reference.isBlank()) return cartResult.items().size() == 1 ? cartResult.items().getFirst() : null;
        List<CartItemView> matches = cartResult.items().stream()
                .filter(item -> normalize(item.product()).equals(reference)
                        || normalize(item.product()).contains(reference)
                        || reference.contains(normalize(item.product())))
                .toList();
        return matches.size() == 1 ? matches.getFirst() : null;
    }

    private CartItemView selectCartItemForReplacement(CartResult cartResult,
            AgentConversationDecision decision) {
        if (cartResult == null || cartResult.items() == null || cartResult.items().isEmpty()) return null;
        if (cartResult.items().size() == 1) return cartResult.items().getFirst();
        List<String> references = decision.productReferences() == null
                ? List.of() : decision.productReferences();
        List<CartItemView> matches = cartResult.items().stream()
                .filter(item -> references.stream().anyMatch(reference -> productMatches(reference, item.product())))
                .toList();
        return matches.size() == 1 ? matches.getFirst() : null;
    }

    private void groundCatalog(Long sessionId, Long jobId, AgentConversationDecision decision,
            String conversation, AtomicInteger ordinal, Map<String, Object> evidence,
            List<AgentMediaRequest> media) {
        boolean browsingCatalog = decision.commercialAction() == AgentCommercialAction.SHOW_PRODUCTS;
        boolean imagesRequested = decision.commercialAction() == AgentCommercialAction.SHOW_PRODUCT_IMAGES;
        String multipleReferences = decision.productReferences() == null
                ? "" : String.join(" ", decision.productReferences());
        String query = browsingCatalog ? firstPresent(decision.productReference())
                : firstPresent(multipleReferences, decision.productReference(), decision.customerGoal(), conversation);
        ProductSearchResult search = verified(sessionId, jobId, ordinal,
                "buscar_productos", Map.of("consulta", query, "limite", 20,
                        "disponibilidad", decision.catalogAvailability().name()),
                ProductSearchResult.class,
                () -> catalog.searchProducts(sessionId, query, 20, decision.catalogAvailability()));
        evidence.put("productSearch", search);

        if (search.products().isEmpty()) {
            ProductSearchResult available = verified(sessionId, jobId, ordinal,
                    "buscar_productos", Map.of("consulta", "", "limite", 20,
                            "disponibilidad", decision.catalogAvailability().name()),
                    ProductSearchResult.class,
                    () -> catalog.searchProducts(sessionId, "", 20, decision.catalogAvailability()));
            evidence.put("availableProductsWhenNoMatch", available);
            return;
        }

        var selected = unambiguousProduct(search, decision.productReference());
        if (selected == null) {
            if (browsingCatalog) {
                queueInitialCatalogImages(sessionId, jobId, search, ordinal, evidence, media);
            }
            boolean requestedSpecificColor = decision.colorReference() != null
                    && !decision.colorReference().isBlank();
            if (imagesRequested && !requestedSpecificColor) {
                List<Map<String, Object>> imageEvidence = new ArrayList<>();
                for (var candidate : search.products().stream().limit(5).toList()) {
                    Integer candidateId = candidate.productId();
                    ProductImageResult candidateImages = verified(sessionId, jobId, ordinal,
                            "obtener_imagen_producto", nullableMap("productoId", candidateId, "color", ""),
                            ProductImageResult.class,
                            () -> catalog.getProductImage(sessionId, candidateId, ""));
                    boolean hasGlobalImage = candidateImages.globalImageUrl() != null
                            && !candidateImages.globalImageUrl().isBlank();
                    imageEvidence.add(Map.of(
                            "productId", candidateId,
                            "productName", candidate.name(),
                            "globalImageAvailable", hasGlobalImage));
                    if (hasGlobalImage) {
                        media.add(new AgentMediaRequest(MediaType.PRODUCT_IMAGE, candidateId, ""));
                    }
                }
                evidence.put("matchingProductImages", imageEvidence);
                evidence.put("matchingProductImagesTruncated", search.products().size() > 5);
            } else if (imagesRequested) {
                evidence.put("productSelectionRequiredBeforeColorImage", true);
            }
            return;
        }

        Integer productId = selected.productId();
        ProductDetail detail = verified(sessionId, jobId, ordinal,
                "obtener_producto", Map.of("productoId", productId),
                ProductDetail.class, () -> catalog.getProduct(sessionId, productId));
        boolean requestedSpecificColor = decision.colorReference() != null
                && !decision.colorReference().isBlank();
        String availableRequestedColor = requestedSpecificColor
                ? matchingAvailableColor(detail.colors(), decision.colorReference()) : "";
        String imageColor = requestedSpecificColor && !availableRequestedColor.isBlank()
                ? availableRequestedColor : decision.colorReference();
        ProductImageResult images = verified(sessionId, jobId, ordinal,
                "obtener_imagen_producto", nullableMap("productoId", productId, "color", imageColor),
                ProductImageResult.class,
                () -> catalog.getProductImage(sessionId, productId, imageColor));
        evidence.put("selectedProduct", detail);
        evidence.put("selectedProductImages", images);

        if (imagesRequested && !requestedSpecificColor) {
            queueAvailableColorGallery(productId, detail, images, evidence, media);
            return;
        }

        boolean hasRequestedColorImage = images.colorImages() != null && !images.colorImages().isEmpty();
        boolean hasGlobalImage = images.globalImageUrl() != null && !images.globalImageUrl().isBlank();
        boolean requestedColorAvailable = !requestedSpecificColor || !availableRequestedColor.isBlank();
        if (requestedSpecificColor) {
            evidence.put("requestedColorAvailability", Map.of(
                    "requestedColor", decision.colorReference(),
                    "available", requestedColorAvailable,
                    "matchedColor", availableRequestedColor,
                    "availableColors", detail.colors()));
        }
        if (requestedColorAvailable
                && (hasRequestedColorImage || (!requestedSpecificColor && hasGlobalImage))) {
            media.add(new AgentMediaRequest(MediaType.PRODUCT_IMAGE, productId, imageColor));
        }
    }

    private void queueAvailableColorGallery(Integer productId, ProductDetail detail, ProductImageResult images,
            Map<String, Object> evidence, List<AgentMediaRequest> media) {
        List<String> availableColors = detail.colors() == null ? List.of() : detail.colors().stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(color -> !color.isBlank())
                .distinct()
                .toList();
        List<ProductImage> configuredImages = images.colorImages() == null ? List.of() : images.colorImages();
        List<String> displayedColors = new ArrayList<>();
        List<String> colorsWithoutImage = new ArrayList<>();

        for (String availableColor : availableColors) {
            ProductImage configured = configuredImages.stream()
                    .filter(image -> image != null && image.url() != null && !image.url().isBlank())
                    .filter(image -> sameColor(image.color(), availableColor))
                    .findFirst().orElse(null);
            if (configured == null) {
                colorsWithoutImage.add(availableColor);
                continue;
            }
            if (displayedColors.size() >= MAX_PRODUCT_COLOR_GALLERY_IMAGES) continue;
            displayedColors.add(availableColor);
            media.add(new AgentMediaRequest(MediaType.PRODUCT_IMAGE, productId, availableColor));
        }

        evidence.put("productColorGallery", Map.of(
                "productId", productId,
                "productName", detail.name(),
                "availableColors", availableColors,
                "displayedColors", displayedColors,
                "colorsWithoutImage", colorsWithoutImage,
                "imageLimit", MAX_PRODUCT_COLOR_GALLERY_IMAGES,
                "hasMoreColors", availableColors.size() > displayedColors.size()));
    }

    private String matchingAvailableColor(List<String> availableColors, String requestedColor) {
        if (availableColors == null || requestedColor == null || requestedColor.isBlank()) return "";
        String requested = normalize(requestedColor);
        return availableColors.stream()
                .filter(Objects::nonNull)
                .filter(color -> sameColor(color, requested))
                .findFirst().orElse("");
    }

    private boolean sameColor(String left, String right) {
        String normalizedLeft = normalize(left);
        String normalizedRight = normalize(right);
        return !normalizedLeft.isBlank() && !normalizedRight.isBlank()
                && (normalizedLeft.equals(normalizedRight)
                || normalizedLeft.contains(normalizedRight)
                || normalizedRight.contains(normalizedLeft));
    }

    private void queueInitialCatalogImages(Long sessionId, Long jobId, ProductSearchResult search,
            AtomicInteger ordinal, Map<String, Object> evidence, List<AgentMediaRequest> media) {
        List<AgentCatalogQueryService.ProductSummary> candidates = new ArrayList<>(search.products());
        java.util.Collections.shuffle(candidates, new Random(Objects.hash(sessionId, jobId)));
        List<Map<String, Object>> imageEvidence = new ArrayList<>();
        for (var candidate : candidates) {
            if (media.size() >= MAX_INITIAL_CATALOG_IMAGES) break;
            Integer productId = candidate.productId();
            ProductImageResult images = verified(sessionId, jobId, ordinal,
                    "obtener_imagen_producto", nullableMap("productoId", productId, "color", ""),
                    ProductImageResult.class, () -> catalog.getProductImage(sessionId, productId, ""));
            boolean available = images != null && images.globalImageUrl() != null
                    && !images.globalImageUrl().isBlank();
            imageEvidence.add(Map.of("productId", productId, "productName", candidate.name(),
                    "globalImageAvailable", available));
            if (available) {
                media.add(new AgentMediaRequest(MediaType.PRODUCT_IMAGE, productId, "", false, true));
            }
        }
        evidence.put("initialCatalogPreviewImages", imageEvidence);
        evidence.put("initialCatalogPreviewImageCount", media.size());
    }

    private AgentConversationDecision reconcileExplicitCatalogReferences(
            Long sessionId, Long jobId, AgentConversationDecision decision, String conversation,
            AtomicInteger ordinal, Map<String, Object> evidence) {
        String currentInput = currentGroupedInput(conversation);
        if (currentInput.isBlank() || !usesCatalogProductReference(decision)) return decision;

        List<String> interpreted = new ArrayList<>(decision.productReferences());
        if (interpreted.isEmpty() && decision.productReference() != null
                && !decision.productReference().isBlank()) {
            interpreted.add(decision.productReference());
        }
        boolean allReflectedInCurrentMessage = !interpreted.isEmpty() && interpreted.stream()
                .allMatch(reference -> containsPhrase(normalize(currentInput), normalize(reference)));
        if (allReflectedInCurrentMessage) return decision;

        ProductSearchResult search = verified(sessionId, jobId, ordinal,
                "buscar_productos", Map.of("consulta", currentInput, "limite", 20,
                        "disponibilidad", decision.catalogAvailability().name()),
                ProductSearchResult.class,
                () -> catalog.searchProducts(sessionId, currentInput, 20, decision.catalogAvailability()));
        List<String> exactReferences = exactCatalogReferences(search, currentInput);
        evidence.put("explicitProductReferenceSearch", search);
        evidence.put("explicitProductReferences", exactReferences);
        if (exactReferences.isEmpty()) {
            if (containsImageMarker(currentInput) && !interpreted.isEmpty()) {
                evidence.put("unresolvedImageProductReference",
                        "La imagen no se analiza visualmente y el texto actual no nombra ningun producto del catalogo. "
                        + "No menciones el producto supuesto por la decision. Explica brevemente que no puedes "
                        + "identificar el modelo desde la imagen, responde solo lo escrito que sea verificable y "
                        + "solicita el nombre del modelo si la consulta depende de la foto.");
                return decision.withProductReferences("", List.of());
            }
            return decision;
        }
        return decision.withProductReferences(exactReferences.getFirst(), exactReferences);
    }

    private boolean containsImageMarker(String value) {
        return value != null && normalize(value).contains("imagen adjunta");
    }

    private boolean usesCatalogProductReference(AgentConversationDecision decision) {
        if (decision.commercialAction() == AgentCommercialAction.SHOW_PRODUCTS
                || decision.commercialAction() == AgentCommercialAction.SHOW_PROMOTIONS
                || decision.intent() == AgentDecisionIntent.PROMOTION_SEARCH) return false;
        return decision.intent() == AgentDecisionIntent.PRODUCT_SEARCH
                || decision.intent() == AgentDecisionIntent.PRODUCT_DETAILS
                || decision.intent() == AgentDecisionIntent.CART_CHANGE
                || decision.commercialAction() == AgentCommercialAction.SHOW_PRODUCT
                || decision.commercialAction() == AgentCommercialAction.SHOW_PRODUCT_AVAILABILITY
                || decision.commercialAction() == AgentCommercialAction.SHOW_PRODUCT_IMAGES
                || decision.commercialAction() == AgentCommercialAction.SHOW_SIZE_GUIDE
                || decision.commercialAction() == AgentCommercialAction.RECOMMEND_SIZE
                || decision.commercialAction() == AgentCommercialAction.ADD_PRODUCT
                || decision.commercialAction() == AgentCommercialAction.CHANGE_CART
                || decision.commercialAction() == AgentCommercialAction.SHARE_DELIVERY_TIMING_CONTACT;
    }

    private List<String> exactCatalogReferences(ProductSearchResult search, String currentInput) {
        if (search == null || search.products() == null || search.products().isEmpty()) return List.of();
        String message = normalize(currentInput);
        Map<String, List<PrefixMatch>> byFirstToken = new LinkedHashMap<>();
        for (var product : search.products()) {
            List<String> tokens = List.of(normalize(product.name()).split("\\s+"));
            if (tokens.isEmpty() || tokens.getFirst().isBlank()) continue;
            int matchedTokens = longestContainedPrefix(message, tokens);
            if (matchedTokens < 1) continue;
            byFirstToken.computeIfAbsent(tokens.getFirst(), ignored -> new ArrayList<>())
                    .add(new PrefixMatch(product, tokens, matchedTokens));
        }

        Map<String, String> references = new LinkedHashMap<>();
        for (List<PrefixMatch> familyMatches : byFirstToken.values()) {
            int longest = familyMatches.stream().mapToInt(PrefixMatch::matchedTokens).max().orElse(0);
            boolean hasCompleteProductName = familyMatches.stream()
                    .anyMatch(match -> match.matchedTokens() == match.tokens().size());
            for (PrefixMatch match : familyMatches) {
                boolean include = hasCompleteProductName
                        ? match.matchedTokens() == match.tokens().size()
                        : match.matchedTokens() == longest;
                if (!include) continue;
                String reference = String.join(" ", match.tokens().subList(0, match.matchedTokens()));
                references.putIfAbsent(reference, reference.toUpperCase(Locale.ROOT));
            }
        }
        return references.values().stream().toList();
    }

    private int longestContainedPrefix(String message, List<String> productNameTokens) {
        for (int length = productNameTokens.size(); length >= 1; length--) {
            String prefix = String.join(" ", productNameTokens.subList(0, length));
            if (containsPhrase(message, prefix)) return length;
        }
        return 0;
    }

    private List<String> requestedProductReferences(
            AgentConversationDecision decision, String currentInput) {
        Map<String, String> unique = new LinkedHashMap<>();
        for (String reference : decision.productReferences()) {
            if (reference != null && !reference.isBlank()) unique.putIfAbsent(normalize(reference), reference.trim());
        }
        if (decision.productReference() != null && !decision.productReference().isBlank()) {
            unique.putIfAbsent(normalize(decision.productReference()), decision.productReference().trim());
        }
        if (currentInput != null && !currentInput.isBlank()) {
            unique.putIfAbsent(normalize(currentInput), currentInput.trim());
        }
        return unique.values().stream().toList();
    }

    private List<AgentCatalogQueryService.ProductSummary> requestedProducts(
            ProductSearchResult search, List<String> references) {
        if (search == null || search.products() == null || search.products().isEmpty()) return List.of();
        String requested = normalize(String.join(" ", references));
        if (requested.isBlank()) return List.of();

        Map<String, List<AgentCatalogQueryService.ProductSummary>> byFamily = new LinkedHashMap<>();
        for (var product : search.products()) {
            String normalizedName = normalize(product.name());
            String family = normalizedName.contains(" ")
                    ? normalizedName.substring(0, normalizedName.indexOf(' ')) : normalizedName;
            byFamily.computeIfAbsent(family, ignored -> new ArrayList<>()).add(product);
        }

        Map<Integer, AgentCatalogQueryService.ProductSummary> selected = new LinkedHashMap<>();
        for (var family : byFamily.entrySet()) {
            List<AgentCatalogQueryService.ProductSummary> exactVariants = family.getValue().stream()
                    .filter(product -> containsPhrase(requested, normalize(product.name())))
                    .toList();
            if (!exactVariants.isEmpty()) {
                exactVariants.forEach(product -> selected.putIfAbsent(product.productId(), product));
            } else if (containsWord(requested, family.getKey())) {
                family.getValue().forEach(product -> selected.putIfAbsent(product.productId(), product));
            }
        }
        return selected.values().stream().toList();
    }

    private String currentGroupedInput(String conversation) {
        if (conversation == null || conversation.isBlank()) return "";
        String marker = "LOTE_ACTUAL_AGRUPADO:";
        int markerIndex = conversation.lastIndexOf(marker);
        return markerIndex < 0 ? conversation.trim()
                : conversation.substring(markerIndex + marker.length()).trim();
    }

    private String knowledgeQuestion(AgentConversationDecision decision, String conversation) {
        String currentInput = currentGroupedInput(conversation).replaceAll("\\s+", " ").trim();
        String question = firstPresent(currentInput, decision.customerGoal()).replaceAll("\\s+", " ").trim();
        if (question.length() <= MAX_KNOWLEDGE_QUESTION_LENGTH) return question;
        return question.substring(question.length() - MAX_KNOWLEDGE_QUESTION_LENGTH).trim();
    }

    static boolean requestsDifferentSizesForSetPieces(String input) {
        Map<String, String> selections = componentSizes(input);
        return selections.size() > 1 && selections.values().stream().distinct().count() > 1;
    }

    private static Map<String, String> componentSizes(String input) {
        if (input == null || input.isBlank()) return Map.of();
        Map<String, String> selections = new LinkedHashMap<>();
        collectComponentSizes(COMPONENT_WITH_SIZE.matcher(input), selections, 1, 2);
        collectComponentSizes(SIZE_WITH_COMPONENT.matcher(input), selections, 2, 1);
        return Map.copyOf(selections);
    }

    private static void collectComponentSizes(Matcher matcher, Map<String, String> selections,
            int componentGroup, int sizeGroup) {
        while (matcher.find()) {
            String component = Normalizer.normalize(matcher.group(componentGroup), Normalizer.Form.NFD)
                    .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
            String size = matcher.group(sizeGroup).toUpperCase(Locale.ROOT);
            selections.putIfAbsent(component, size);
        }
    }

    private boolean containsWord(String value, String word) {
        String normalizedValue = normalize(value);
        String normalizedWord = normalize(word);
        return !normalizedWord.isBlank()
                && (" " + normalizedValue + " ").contains(" " + normalizedWord + " ");
    }

    private AgentCatalogQueryService.ProductSummary unambiguousProduct(
            ProductSearchResult search, String productReference) {
        if (search.products().size() == 1) return search.products().getFirst();
        String reference = normalize(productReference);
        if (reference.isBlank()) return null;
        var exact = search.products().stream()
                .filter(item -> normalize(item.name()).equals(reference))
                .toList();
        return exact.size() == 1 ? exact.getFirst() : null;
    }

    private PromotionSummary unambiguousPromotion(PromotionSearchResult search, String... referenceValues) {
        if (search == null || search.promotions() == null || search.promotions().isEmpty()) return null;
        for (String referenceValue : referenceValues) {
            String reference = normalize(referenceValue);
            if (reference.isBlank()) continue;
            List<PromotionSummary> exact = search.promotions().stream()
                    .filter(item -> normalize(item.name()).equals(reference))
                    .toList();
            if (exact.size() == 1) return exact.getFirst();
            List<PromotionSummary> mentioned = search.promotions().stream()
                    .filter(item -> containsPhrase(reference, normalize(item.name())))
                    .toList();
            if (mentioned.size() == 1) return mentioned.getFirst();
        }
        return search.promotions().size() == 1 ? search.promotions().getFirst() : null;
    }

    private boolean containsPhrase(String text, String phrase) {
        return !phrase.isBlank() && (" " + text + " ").contains(" " + phrase + " ");
    }

    private boolean productMatches(String referenceValue, String productName) {
        String reference = normalize(referenceValue);
        String product = normalize(productName);
        return !reference.isBlank() && (reference.equals(product)
                || product.contains(reference) || reference.contains(product));
    }

    private <T> T verified(Long sessionId, Long jobId, AtomicInteger ordinal, String toolName,
            Map<String, Object> request, Class<T> responseType, Supplier<T> operation) {
        String requestJson = write(request);
        String responseJson = audit.execute(sessionId, jobId, ordinal.incrementAndGet(), toolName,
                requestJson, () -> write(operation.get()));
        try {
            return objectMapper.readValue(responseJson, responseType);
        } catch (Exception error) {
            throw new IllegalStateException("La evidencia verificada de " + toolName + " no es valida", error);
        }
    }

    private Map<String, Object> nullableMap(String firstKey, Object firstValue, String secondKey, Object secondValue) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(firstKey, firstValue);
        result.put(secondKey, secondValue);
        return result;
    }

    private Map<String, Object> nullableMap(String firstKey, Object firstValue, String secondKey, Object secondValue,
            String thirdKey, Object thirdValue) {
        Map<String, Object> result = nullableMap(firstKey, firstValue, secondKey, secondValue);
        result.put(thirdKey, thirdValue);
        return result;
    }

    private Map<String, Object> nullableMap(String firstKey, Object firstValue, String secondKey, Object secondValue,
            String thirdKey, Object thirdValue, String fourthKey, Object fourthValue) {
        Map<String, Object> result = nullableMap(firstKey, firstValue, secondKey, secondValue,
                thirdKey, thirdValue);
        result.put(fourthKey, fourthValue);
        return result;
    }

    private Map<String, Object> nullableMap(String firstKey, Object firstValue, String secondKey, Object secondValue,
            String thirdKey, Object thirdValue, String fourthKey, Object fourthValue,
            String fifthKey, Object fifthValue) {
        Map<String, Object> result = nullableMap(firstKey, firstValue, secondKey, secondValue,
                thirdKey, thirdValue, fourthKey, fourthValue);
        result.put(fifthKey, fifthValue);
        return result;
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception error) {
            throw new IllegalStateException("No se pudo serializar la evidencia del agente", error);
        }
    }

    private String writeCustomerEvidence(Object value) {
        try {
            JsonNode evidence = objectMapper.valueToTree(value);
            removeInventoryQuantities(evidence);
            return objectMapper.writeValueAsString(evidence);
        } catch (Exception error) {
            throw new IllegalStateException("No se pudo serializar la evidencia publica del agente", error);
        }
    }

    private void removeInventoryQuantities(JsonNode node) {
        if (node == null) return;
        if (node.isArray()) {
            node.forEach(this::removeInventoryQuantities);
            return;
        }
        if (!node.isObject()) return;
        ObjectNode object = (ObjectNode) node;
        List<String> fieldsToRemove = new ArrayList<>();
        object.fields().forEachRemaining(entry -> {
            String field = entry.getKey().toLowerCase(Locale.ROOT);
            if (field.contains("stock") || field.contains("inventoryquantity")
                    || field.contains("existencias")) {
                fieldsToRemove.add(entry.getKey());
            } else {
                removeInventoryQuantities(entry.getValue());
            }
        });
        fieldsToRemove.forEach(object::remove);
    }

    private String firstPresent(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value.trim();
        return "";
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    private record CartMutation(boolean executed, String message) {
    }

    private record CartImageCandidate(Integer productId, String productName, String color) {
    }

    private record ProductAvailabilityView(
            Integer productId,
            String name,
            Double price,
            String productPageUrl,
            List<AgentCatalogQueryService.VariantSummary> variants) {
        private ProductAvailabilityView {
            productPageUrl = productPageUrl == null ? "" : productPageUrl;
            variants = variants == null ? List.of() : List.copyOf(variants);
        }
    }

    private record RequiredPromotionProduct(Integer productId, String productName, int quantity) {
    }

    private record PrefixMatch(
            AgentCatalogQueryService.ProductSummary product, List<String> tokens, int matchedTokens) {
    }

    public record Grounding(
            AgentConversationDecision decision,
            String evidenceJson,
            List<AgentMediaRequest> media,
            int verifiedToolCalls,
            boolean mutationExecuted,
            String customerMessage) {
        public Grounding {
            Objects.requireNonNull(decision, "decision");
            evidenceJson = evidenceJson == null ? "{}" : evidenceJson;
            media = media == null ? List.of() : List.copyOf(media);
        }

        public Grounding(AgentConversationDecision decision, String evidenceJson,
                List<AgentMediaRequest> media, int verifiedToolCalls, boolean mutationExecuted) {
            this(decision, evidenceJson, media, verifiedToolCalls, mutationExecuted, null);
        }
    }
}
