package com.sistemapos.sistematextil.services.agent.runtime;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sistemapos.sistematextil.services.agent.ai.AgentConversationDecision;
import com.sistemapos.sistematextil.services.agent.ai.AgentConversationDecision.PromotionSelection;
import com.sistemapos.sistematextil.services.agent.ai.AgentCartAction;
import com.sistemapos.sistematextil.services.agent.ai.AgentCommercialAction;
import com.sistemapos.sistematextil.services.agent.ai.AgentDecisionGateway;
import com.sistemapos.sistematextil.services.agent.ai.AgentDecisionIntent;
import com.sistemapos.sistematextil.services.agent.ai.AgentMediaRequest;
import com.sistemapos.sistematextil.services.agent.ai.AgentMediaRequest.MediaType;
import com.sistemapos.sistematextil.services.agent.catalog.AgentCatalogQueryService;
import com.sistemapos.sistematextil.services.agent.catalog.AgentCatalogQueryService.ProductDetail;
import com.sistemapos.sistematextil.services.agent.catalog.AgentCatalogQueryService.ProductImageResult;
import com.sistemapos.sistematextil.services.agent.catalog.AgentCatalogQueryService.ProductSearchResult;
import com.sistemapos.sistematextil.services.agent.catalog.AgentCatalogQueryService.StockResult;
import com.sistemapos.sistematextil.services.agent.catalog.AgentCatalogQueryService.VariantResult;
import com.sistemapos.sistematextil.services.agent.cart.AgentCartService;
import com.sistemapos.sistematextil.services.agent.cart.AgentCartService.CartItemView;
import com.sistemapos.sistematextil.services.agent.cart.AgentCartService.CartResult;
import com.sistemapos.sistematextil.services.agent.cart.AgentCartService.ComboSelection;
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

    private final AgentDecisionGateway decisions;
    private final AgentCatalogQueryService catalog;
    private final AgentPromotionQueryService promotions;
    private final AgentKnowledgeRetriever knowledge;
    private final AgentCartService cart;
    private final AgentDeterministicCommerceService commerce;
    private final ProductSizeGuideQueryService sizeGuides;
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
            AgentToolCallAuditService audit) {
        this.decisions = decisions;
        this.catalog = catalog;
        this.promotions = promotions;
        this.knowledge = knowledge;
        this.cart = cart;
        this.commerce = commerce;
        this.sizeGuides = sizeGuides;
        this.audit = audit;
    }

    AgentVerifiedGroundingService(AgentDecisionGateway decisions, AgentCatalogQueryService catalog,
            AgentPromotionQueryService promotions, AgentKnowledgeRetriever knowledge,
            AgentCartService cart, AgentToolCallAuditService audit) {
        this(decisions, catalog, promotions, knowledge, cart, null, null, audit);
    }

    public Grounding ground(Long connectionId, Long sessionId, Long jobId, String conversation) {
        AgentConversationDecision decision = decisions.interpret(connectionId, conversation).decision();
        if (decision == null || decision.intent() == null) {
            throw new IllegalStateException("Gemini no clasifico la solicitud actual");
        }
        decision = enforceHumanHandoffRules(decision);
        if (commerce != null) {
            decision = commerce.normalizeShalomAgencyRefinement(sessionId, decision);
        }
        decision = normalizeIncompleteProductSelection(decision);
        boolean earlyDeliveryPreference = commerce != null
                && commerce.rememberEarlyDeliveryPreference(sessionId, decision);

        AtomicInteger ordinal = new AtomicInteger(AUDIT_ORDINAL_BASE);
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("decision", decision);
        List<AgentMediaRequest> media = new ArrayList<>();
        boolean mutationExecuted = false;
        String customerMessage = null;

        var commercial = commerce == null
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
                    sessionId, jobId, decision, conversation, ordinal, evidence);
            mutationExecuted = mutationExecuted || mutation.executed();
            customerMessage = mutation.message();
        } else if (!commercial.handled() && decision.commercialAction() == AgentCommercialAction.ADD_PRODUCT) {
            CartMutation mutation = groundCartChange(
                    sessionId, jobId, decision, conversation, ordinal, evidence);
            mutationExecuted = mutation.executed();
            customerMessage = mutation.message();
        } else if (!commercial.handled()
                && decision.commercialAction() == AgentCommercialAction.RECOMMEND_SIZE) {
            groundSizeRecommendation(sessionId, jobId, decision, conversation, ordinal, evidence);
        } else if (!commercial.handled()
                && decision.commercialAction() == AgentCommercialAction.SHOW_SIZE_GUIDE) {
            groundSizeGuide(sessionId, jobId, decision, conversation, ordinal, evidence, media);
        } else if (!commercial.handled()
                && (decision.commercialAction() == AgentCommercialAction.SHOW_PROMOTIONS
                || decision.intent() == AgentDecisionIntent.PROMOTION_SEARCH)) {
            groundPromotions(sessionId, jobId, decision, conversation, ordinal, evidence);
        } else if (!commercial.handled()
                && (decision.commercialAction() == AgentCommercialAction.SHOW_PRODUCTS
                || decision.commercialAction() == AgentCommercialAction.SHOW_PRODUCT
                || decision.commercialAction() == AgentCommercialAction.SHOW_PRODUCT_IMAGES)) {
            groundCatalog(sessionId, jobId, decision, conversation, ordinal, evidence, media);
        } else if (!commercial.handled()) switch (decision.intent()) {
            case PRODUCT_SEARCH, PRODUCT_DETAILS -> groundCatalog(
                    sessionId, jobId, decision, conversation, ordinal, evidence, media);
            case PROMOTION_SEARCH -> groundPromotions(
                    sessionId, jobId, decision, conversation, ordinal, evidence);
            case BUSINESS_INFORMATION -> {
                String question = firstPresent(decision.customerGoal(), conversation);
                KnowledgeSearchResult result = verified(sessionId, jobId, ordinal,
                        "consultar_informacion_negocio", Map.of("pregunta", question),
                        KnowledgeSearchResult.class, () -> knowledge.searchForSession(sessionId, question));
                evidence.put("businessInformation", result);
            }
            case CART_CHANGE -> {
                CartMutation mutation = groundCartChange(
                        sessionId, jobId, decision, conversation, ordinal, evidence);
                mutationExecuted = mutation.executed();
                customerMessage = mutation.message();
            }
            default -> {
                // Conversational and transactional turns remain in the normal tool loop.
            }
        }

        boolean catalogIntent = decision.intent() == AgentDecisionIntent.PRODUCT_SEARCH
                || decision.intent() == AgentDecisionIntent.PRODUCT_DETAILS;
        if (catalogIntent && ordinal.get() == AUDIT_ORDINAL_BASE && !mutationExecuted) {
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
        return new Grounding(decision, write(evidence), List.copyOf(media), verifiedCalls,
                mutationExecuted, customerMessage);
    }

    AgentConversationDecision enforceHumanHandoffRules(AgentConversationDecision decision) {
        if (decision.commercialAction() == AgentCommercialAction.REQUEST_WHOLESALE_SUPPORT
                && !decision.requiresHumanHelp()) {
            return decision.withRequiresHumanHelp(true);
        }
        return decision;
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

    private void groundSizeGuide(Long sessionId, Long jobId, AgentConversationDecision decision,
            String conversation, AtomicInteger ordinal, Map<String, Object> evidence,
            List<AgentMediaRequest> media) {
        if (sizeGuides == null) return;
        String query = firstPresent(decision.productReference(), decision.customerGoal(), conversation);
        ProductSearchResult search = verified(sessionId, jobId, ordinal,
                "buscar_productos", Map.of("consulta", query, "limite", 10), ProductSearchResult.class,
                () -> catalog.searchProducts(sessionId, query, 10));
        evidence.put("productSearch", search);
        var selected = unambiguousProduct(search, decision.productReference());
        if (selected == null) return;
        SizeGuideResult guide = verified(sessionId, jobId, ordinal,
                "obtener_guia_tallas", Map.of("productoId", selected.productId()),
                SizeGuideResult.class, () -> sizeGuides.getGuide(sessionId, selected.productId()));
        evidence.put("sizeGuide", guide);
        media.add(new AgentMediaRequest(MediaType.SIZE_GUIDE, selected.productId(), null));
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
            String conversation, AtomicInteger ordinal, Map<String, Object> evidence) {
        if (commerce != null) commerce.prepareCartMutation(sessionId);
        AgentCartAction action = decision.cartAction() == null ? AgentCartAction.NONE : decision.cartAction();
        if (action == AgentCartAction.ADD_PROMOTION) {
            return groundPromotionAdd(sessionId, jobId, decision, conversation, ordinal, evidence);
        }
        if (action == AgentCartAction.ADD_PRODUCT && decision.promotionSelections() != null
                && decision.promotionSelections().size() > 1) {
            return groundMultipleProductAdd(sessionId, jobId, decision, ordinal, evidence);
        }
        if (action == AgentCartAction.CLEAR_CART) {
            CartResult result = verified(sessionId, jobId, ordinal,
                    "vaciar_carrito", Map.of(), CartResult.class,
                    () -> cart.emptyCart(sessionId, "agent-job-" + jobId + ":clear-cart"));
            evidence.put("cartOperation", Map.of("executed", true, "result", result));
            return new CartMutation(true, "Tu carrito quedó vacío. ¿Qué producto deseas elegir?");
        }
        if (action == AgentCartAction.CHANGE_QUANTITY
                || action == AgentCartAction.CHANGE_VARIANT
                || action == AgentCartAction.REMOVE_PRODUCT) {
            return groundExistingCartChange(sessionId, jobId, decision, ordinal, evidence, action);
        }
        if (action != AgentCartAction.ADD_PRODUCT) {
            evidence.put("cartOperation", Map.of("executed", false,
                    "reason", "La accion o la cantidad todavia requiere aclaracion"));
            return new CartMutation(false, null);
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
        String message = commerce == null
                ? "Producto agregado correctamente al carrito."
                : commerce.cartTemplate(sessionId, result, true);
        return new CartMutation(true, message);
    }

    private CartMutation groundMultipleProductAdd(Long sessionId,Long jobId,AgentConversationDecision decision,
            AtomicInteger ordinal,Map<String,Object> evidence){
        CartResult result=null;int index=0;
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
        }
        evidence.put("cartOperation",Map.of("executed",true,"result",result));
        return new CartMutation(true,commerce==null?"Productos agregados correctamente al carrito.":commerce.cartTemplate(sessionId,result,true));
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

        List<PromotionSelection> requested = decision.promotionSelections() == null
                ? new ArrayList<>() : new ArrayList<>(decision.promotionSelections());
        Map<Integer, RequiredPromotionProduct> requirements = new LinkedHashMap<>();
        for (PromotionItem item : detail.items()) {
            requirements.merge(item.productId(),
                    new RequiredPromotionProduct(item.productId(), item.productName(), item.requiredQuantity()),
                    (left, right) -> new RequiredPromotionProduct(left.productId(), left.productName(),
                            left.quantity() + right.quantity()));
        }
        CartResult currentCart = completePromotionSelectionsFromCart(
                sessionId, jobId, ordinal, evidence, requirements, requested);
        List<ComboSelection> resolved = new ArrayList<>();
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
                missing.add(required.quantity() + " x " + required.productName());
                continue;
            }
            boolean validProduct = true;
            for (PromotionSelection selection : productSelections) {
                int quantity = selection.quantity() == null || selection.quantity() < 1 ? 1 : selection.quantity();
                if (selection.colorReference().isBlank() || selection.sizeReference().isBlank()) {
                    validProduct = false;
                    break;
                }
                VariantResult variants = verified(sessionId, jobId, ordinal,
                        "consultar_variantes", nullableMap("productoId", required.productId(), "color",
                                selection.colorReference(), "talla", selection.sizeReference()),
                        VariantResult.class, () -> catalog.getVariants(sessionId, required.productId(),
                                selection.colorReference(), selection.sizeReference()));
                if (variants.variants() == null || variants.variants().size() != 1) {
                    validProduct = false;
                    break;
                }
                Integer variantId = variants.variants().getFirst().variantId();
                StockResult stock = verified(sessionId, jobId, ordinal,
                        "consultar_stock", Map.of("varianteId", variantId, "cantidad", quantity),
                        StockResult.class, () -> catalog.getStock(sessionId, variantId, quantity));
                if (!Boolean.TRUE.equals(stock.sufficient())) {
                    validProduct = false;
                    break;
                }
                resolved.add(new ComboSelection(variantId, quantity));
            }
            if (!validProduct) missing.add(required.quantity() + " x " + required.productName());
        }
        if (!missing.isEmpty()) {
            persistPartialPromotionSelections(
                    sessionId, jobId, ordinal, evidence, resolved, currentCart);
            evidence.put("cartOperation", Map.of("executed", false,
                    "reason", "Faltan variantes validas para completar la promocion", "missing", missing));
            return new CartMutation(false, missingPromotionSelectionMessage(detail.name(), missing));
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

    private CartResult completePromotionSelectionsFromCart(Long sessionId, Long jobId, AtomicInteger ordinal,
            Map<String, Object> evidence, Map<Integer, RequiredPromotionProduct> requirements,
            List<PromotionSelection> requested) {
        boolean incomplete = requirements.values().stream().anyMatch(required ->
                selectedQuantity(requested, required.productName()) < required.quantity());
        if (!incomplete) return null;

        CartResult current = verified(sessionId, jobId, ordinal,
                "recalcular_carrito", Map.of("motivo", "completar_promocion"), CartResult.class,
                () -> cart.recalculate(sessionId, "agent-job-" + jobId + ":promotion-cart-context"));
        evidence.put("currentCartForPromotion", current);
        if (current == null || current.items() == null || current.items().isEmpty()) return current;

        for (RequiredPromotionProduct required : requirements.values()) {
            int missing = required.quantity() - selectedQuantity(requested, required.productName());
            if (missing <= 0) continue;
            for (CartItemView item : current.items()) {
                if (missing <= 0) break;
                if (!productMatches(item.product(), required.productName())) continue;
                int reused = Math.min(missing, item.quantity());
                requested.add(new PromotionSelection(item.product(), item.color(), item.size(), reused));
                missing -= reused;
            }
        }
        return current;
    }

    private void persistPartialPromotionSelections(Long sessionId, Long jobId, AtomicInteger ordinal,
            Map<String, Object> evidence, List<ComboSelection> resolved, CartResult currentCart) {
        if (resolved.isEmpty()) return;
        Map<Integer, Integer> existing = new LinkedHashMap<>();
        if (currentCart != null && currentCart.items() != null) {
            currentCart.items().forEach(item -> existing.merge(item.variantId(), item.quantity(), Integer::sum));
        }
        List<CartResult> persisted = new ArrayList<>();
        for (ComboSelection selection : resolved) {
            int alreadyPresent = existing.getOrDefault(selection.variantId(), 0);
            int quantityToAdd = Math.max(0, selection.quantity() - alreadyPresent);
            if (quantityToAdd == 0) continue;
            String operation = "agent-job-" + jobId + ":promotion-selection:"
                    + selection.variantId();
            CartResult result = verified(sessionId, jobId, ordinal,
                    "agregar_producto_carrito",
                    Map.of("varianteId", selection.variantId(), "cantidad", quantityToAdd,
                            "motivo", "seleccion_parcial_promocion"),
                    CartResult.class,
                    () -> cart.addProduct(sessionId, selection.variantId(), quantityToAdd, operation));
            persisted.add(result);
            existing.merge(selection.variantId(), quantityToAdd, Integer::sum);
        }
        if (!persisted.isEmpty()) evidence.put("partialPromotionSelectionsPersisted", persisted);
    }

    private int selectedQuantity(List<PromotionSelection> selections, String productName) {
        return selections.stream()
                .filter(selection -> productMatches(selection.productReference(), productName))
                .mapToInt(selection -> selection.quantity() == null || selection.quantity() < 1
                        ? 1 : selection.quantity())
                .sum();
    }

    private String missingPromotionSelectionMessage(String promotionName, List<String> missing) {
        List<String> products = missing.stream()
                .map(value -> value.replaceFirst("^\\d+\\s*x\\s*", ""))
                .distinct()
                .toList();
        String names = products.size() == 1
                ? "*" + products.getFirst() + "*"
                : products.stream().map(name -> "*" + name + "*")
                        .collect(java.util.stream.Collectors.joining(", "));
        return "Para completar *" + promotionName + "*, indícame el color y la talla de " + names + ".";
    }

    private CartMutation groundExistingCartChange(Long sessionId, Long jobId,
            AgentConversationDecision decision, AtomicInteger ordinal, Map<String, Object> evidence,
            AgentCartAction action) {
        CartResult current = verified(sessionId, jobId, ordinal,
                "recalcular_carrito", Map.of(), CartResult.class,
                () -> cart.recalculate(sessionId, "agent-job-" + jobId + ":read-cart"));
        CartItemView selected = selectCartItem(current, decision.productReference());
        if (selected == null) {
            evidence.put("cartOperation", Map.of("executed", false,
                    "reason", "No se pudo identificar un unico producto del carrito"));
            return new CartMutation(false, null);
        }

        String operation = "agent-job-" + jobId + ":" + action.name().toLowerCase(Locale.ROOT);
        CartResult result;
        if (action == AgentCartAction.REMOVE_PRODUCT) {
            result = verified(sessionId, jobId, ordinal,
                    "eliminar_producto_carrito", Map.of("varianteId", selected.variantId()),
                    CartResult.class, () -> cart.removeProduct(sessionId, selected.variantId(), operation));
        } else if (action == AgentCartAction.CHANGE_QUANTITY) {
            if (decision.quantity() == null || decision.quantity() < 1) {
                evidence.put("cartOperation", Map.of("executed", false,
                        "reason", "Falta indicar la nueva cantidad", "item", selected));
                return new CartMutation(false, null);
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
            String color = firstPresent(decision.colorReference(), selected.color());
            String size = firstPresent(decision.sizeReference(), selected.size());
            VariantResult variants = verified(sessionId, jobId, ordinal,
                    "consultar_variantes", nullableMap("productoId", currentStock.productId(), "color", color,
                            "talla", size),
                    VariantResult.class,
                    () -> catalog.getVariants(sessionId, currentStock.productId(), color, size));
            if (variants.variants() == null || variants.variants().size() != 1) {
                evidence.put("cartOperation", Map.of("executed", false,
                        "reason", "Falta validar una unica variante nueva", "item", selected,
                        "variants", variants));
                return new CartMutation(false, null);
            }
            Integer newVariantId = variants.variants().getFirst().variantId();
            if (Objects.equals(newVariantId, selected.variantId())) {
                evidence.put("cartOperation", Map.of("executed", false,
                        "reason", "La variante solicitada ya es la variante actual", "item", selected));
                return new CartMutation(false, null);
            }
            result = verified(sessionId, jobId, ordinal,
                    "cambiar_variante_carrito",
                    Map.of("varianteActualId", selected.variantId(), "varianteNuevaId", newVariantId),
                    CartResult.class,
                    () -> cart.changeVariant(sessionId, selected.variantId(), newVariantId, operation));
        }
        evidence.put("cartOperation", Map.of("executed", true, "result", result));
        String message = result.items().isEmpty()
                ? "Tu carrito quedó vacío. ¿Qué producto deseas elegir?"
                : commerce == null ? "Carrito actualizado correctamente."
                : commerce.cartTemplate(sessionId, result, true);
        return new CartMutation(true, message);
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

    private void groundCatalog(Long sessionId, Long jobId, AgentConversationDecision decision,
            String conversation, AtomicInteger ordinal, Map<String, Object> evidence,
            List<AgentMediaRequest> media) {
        boolean browsingCatalog = decision.commercialAction() == AgentCommercialAction.SHOW_PRODUCTS;
        boolean imagesRequested = decision.commercialAction() == AgentCommercialAction.SHOW_PRODUCT_IMAGES;
        String query = browsingCatalog ? firstPresent(decision.productReference())
                : firstPresent(decision.productReference(), decision.customerGoal(), conversation);
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
            boolean requestedSpecificColor = decision.colorReference() != null
                    && !decision.colorReference().isBlank();
            if (imagesRequested && !requestedSpecificColor) {
                List<Map<String, Object>> imageEvidence = new ArrayList<>();
                for (var candidate : search.products().stream().limit(3).toList()) {
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
                evidence.put("matchingProductImagesTruncated", search.products().size() > 3);
            } else if (imagesRequested) {
                evidence.put("productSelectionRequiredBeforeColorImage", true);
            }
            return;
        }

        Integer productId = selected.productId();
        ProductDetail detail = verified(sessionId, jobId, ordinal,
                "obtener_producto", Map.of("productoId", productId),
                ProductDetail.class, () -> catalog.getProduct(sessionId, productId));
        ProductImageResult images = verified(sessionId, jobId, ordinal,
                "obtener_imagen_producto", nullableMap("productoId", productId, "color", decision.colorReference()),
                ProductImageResult.class,
                () -> catalog.getProductImage(sessionId, productId, decision.colorReference()));
        evidence.put("selectedProduct", detail);
        evidence.put("selectedProductImages", images);

        boolean hasRequestedColorImage = images.colorImages() != null && !images.colorImages().isEmpty();
        boolean hasGlobalImage = images.globalImageUrl() != null && !images.globalImageUrl().isBlank();
        boolean requestedSpecificColor = decision.colorReference() != null
                && !decision.colorReference().isBlank();
        if (hasRequestedColorImage || (!requestedSpecificColor && hasGlobalImage)) {
            media.add(new AgentMediaRequest(MediaType.PRODUCT_IMAGE, productId, decision.colorReference()));
        }
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

    private String firstPresent(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value.trim();
        return "";
    }

    private String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    private record CartMutation(boolean executed, String message) {
    }

    private record RequiredPromotionProduct(Integer productId, String productName, int quantity) {
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
