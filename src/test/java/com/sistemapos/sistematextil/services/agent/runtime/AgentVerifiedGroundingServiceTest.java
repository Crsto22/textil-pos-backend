package com.sistemapos.sistematextil.services.agent.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import com.sistemapos.sistematextil.model.CrmWhatsappAgentState;
import com.sistemapos.sistematextil.services.agent.ai.AgentConversationDecision;
import com.sistemapos.sistematextil.services.agent.ai.AgentConversationDecision.PromotionSelection;
import com.sistemapos.sistematextil.services.agent.ai.AgentCartAction;
import com.sistemapos.sistematextil.services.agent.ai.AgentCommercialAction;
import com.sistemapos.sistematextil.services.agent.ai.AgentDecisionGateway;
import com.sistemapos.sistematextil.services.agent.ai.AgentDecisionIntent;
import com.sistemapos.sistematextil.services.agent.ai.AgentInterpretationResult;
import com.sistemapos.sistematextil.services.agent.ai.AgentMediaRequest.MediaType;
import com.sistemapos.sistematextil.services.agent.catalog.AgentCatalogQueryService;
import com.sistemapos.sistematextil.services.agent.catalog.AgentCatalogQueryService.ColorAvailability;
import com.sistemapos.sistematextil.services.agent.catalog.AgentCatalogQueryService.ProductDetail;
import com.sistemapos.sistematextil.services.agent.catalog.AgentCatalogQueryService.ProductImage;
import com.sistemapos.sistematextil.services.agent.catalog.AgentCatalogQueryService.ProductImageResult;
import com.sistemapos.sistematextil.services.agent.catalog.AgentCatalogQueryService.ProductSearchResult;
import com.sistemapos.sistematextil.services.agent.catalog.AgentCatalogQueryService.ProductSummary;
import com.sistemapos.sistematextil.services.agent.catalog.AgentCatalogQueryService.StockResult;
import com.sistemapos.sistematextil.services.agent.catalog.AgentCatalogQueryService.VariantResult;
import com.sistemapos.sistematextil.services.agent.catalog.AgentCatalogQueryService.VariantSummary;
import com.sistemapos.sistematextil.services.agent.cart.AgentCartService;
import com.sistemapos.sistematextil.services.agent.cart.AgentCartService.CartItemView;
import com.sistemapos.sistematextil.services.agent.cart.AgentCartService.CartResult;
import com.sistemapos.sistematextil.services.agent.cart.AgentCartService.CartPromotionView;
import com.sistemapos.sistematextil.services.agent.cart.AgentCartService.ComboSelection;
import com.sistemapos.sistematextil.services.agent.cart.AgentCartService.PendingPromotionDraft;
import com.sistemapos.sistematextil.services.agent.cart.AgentCartService.PendingPromotionSelection;
import com.sistemapos.sistematextil.services.agent.knowledge.AgentKnowledgeRetriever;
import com.sistemapos.sistematextil.services.agent.knowledge.AgentKnowledgeRetriever.KnowledgeSearchResult;
import com.sistemapos.sistematextil.services.agent.promotion.AgentPromotionQueryService;
import com.sistemapos.sistematextil.services.agent.promotion.AgentPromotionQueryService.PromotionDetail;
import com.sistemapos.sistematextil.services.agent.sizeguide.ProductSizeGuideQueryService;
import com.sistemapos.sistematextil.services.agent.promotion.AgentPromotionQueryService.PromotionItem;
import com.sistemapos.sistematextil.services.agent.promotion.AgentPromotionQueryService.PromotionSearchResult;
import com.sistemapos.sistematextil.services.agent.promotion.AgentPromotionQueryService.PromotionSummary;

class AgentVerifiedGroundingServiceTest {
    @Test
    void redirectsGenericLiveProductRequestToTheProductsPageEvenIfGeminiCallsItAPromotion() {
        AgentDecisionGateway decisions = mock(AgentDecisionGateway.class);
        AgentPromotionQueryService promotions = mock(AgentPromotionQueryService.class);
        AgentConversationDecision mistaken = new AgentConversationDecision(
                AgentDecisionIntent.PROMOTION_SEARCH, "ver conjunto del live", "", "", "", 0, "",
                List.of(), AgentCartAction.NONE, AgentCommercialAction.SHOW_PROMOTIONS,
                "", "", "", "", "", "", "", "",
                com.sistemapos.sistematextil.services.agent.ai.AgentCatalogAvailability.ALL, 90, false);
        String conversation = "Buenas noches, quisiera el conjunto que estan transmitiendo en vivo";
        when(decisions.interpret(4L, conversation)).thenReturn(
                new AgentInterpretationResult("GEMINI", "model", "DB", 10, 0, mistaken));
        AgentVerifiedGroundingService service = new AgentVerifiedGroundingService(
                decisions, mock(AgentCatalogQueryService.class), promotions,
                mock(AgentKnowledgeRetriever.class), mock(AgentCartService.class), passThroughAudit());

        var result = service.ground(4L, 9L, 30L, conversation);

        assertEquals(AgentDecisionIntent.OTHER, result.decision().intent());
        assertEquals(AgentCommercialAction.NONE, result.decision().commercialAction());
        assertTrue(result.customerMessage().contains("https://kiments.com.pe/productos"));
        assertTrue(result.customerMessage().contains("Dime el nombre del conjunto"));
        verifyNoInteractions(promotions);
    }

    @Test
    void keepsExplicitLivePromotionRequestsAsPromotions() {
        AgentVerifiedGroundingService service = new AgentVerifiedGroundingService(
                mock(AgentDecisionGateway.class), mock(AgentCatalogQueryService.class),
                mock(AgentPromotionQueryService.class), mock(AgentKnowledgeRetriever.class),
                mock(AgentCartService.class), passThroughAudit());

        assertFalse(service.isGenericLiveProductLookup(
                "Quiero ver las promociones que mostraron en el live de TikTok"));
        assertTrue(service.isGenericLiveProductLookup("Quiero el conjunto del live"));
    }

    @Test
    void ordinaryCommercialQuestionsNeverRequireHumanHandoff() {
        AgentConversationDecision mistaken = new AgentConversationDecision(
                AgentDecisionIntent.BUSINESS_INFORMATION, "precio de chaqueta por separado", "", "", "", 0, "",
                List.of(), AgentCartAction.NONE, AgentCommercialAction.REQUEST_SUPPORT,
                "", "", "", "", "", "", "", "",
                com.sistemapos.sistematextil.services.agent.ai.AgentCatalogAvailability.ALL, 90, true);
        AgentVerifiedGroundingService service = new AgentVerifiedGroundingService(
                mock(AgentDecisionGateway.class), mock(AgentCatalogQueryService.class),
                mock(AgentPromotionQueryService.class), mock(AgentKnowledgeRetriever.class),
                mock(AgentCartService.class), passThroughAudit());

        AgentConversationDecision normalized = service.enforceHumanHandoffRules(mistaken);

        assertFalse(normalized.requiresHumanHelp());
    }

    @Test
    void businessInformationUsesOnlyCurrentCustomerMessageForRag() {
        AgentDecisionGateway decisions = mock(AgentDecisionGateway.class);
        AgentKnowledgeRetriever knowledge = mock(AgentKnowledgeRetriever.class);
        AgentConversationDecision decision = new AgentConversationDecision(
                AgentDecisionIntent.BUSINESS_INFORMATION, "", "", "", "", 0, "",
                AgentCartAction.NONE, "", 95, false);
        String customerMessage = "mañana a qué hora tienen atención presencial?";
        String conversation = "Resuelve primero MENSAJE_ACTUAL_CLIENTE.\n"
                + "CONTEXTO_CONFIABLE_Y_CONVERSACION_NO_CONFIABLE:\n"
                + "x".repeat(900) + "\n\nLOTE_ACTUAL_AGRUPADO:\n" + customerMessage;
        when(decisions.interpret(4L, conversation)).thenReturn(
                new AgentInterpretationResult("GEMINI", "model", "DB", 10, 0, decision));
        when(knowledge.searchForSession(9L, customerMessage)).thenReturn(
                new KnowledgeSearchResult(true, "ok", List.of()));
        AgentVerifiedGroundingService service = new AgentVerifiedGroundingService(
                decisions, mock(AgentCatalogQueryService.class), mock(AgentPromotionQueryService.class),
                knowledge, mock(AgentCartService.class), passThroughAudit());

        service.ground(4L, 9L, 30L, conversation);

        verify(knowledge).searchForSession(9L, customerMessage);
    }

    @Test
    void businessInformationBoundsAnUnusuallyLongCustomerMessageForRag() {
        AgentDecisionGateway decisions = mock(AgentDecisionGateway.class);
        AgentKnowledgeRetriever knowledge = mock(AgentKnowledgeRetriever.class);
        AgentConversationDecision decision = new AgentConversationDecision(
                AgentDecisionIntent.BUSINESS_INFORMATION, "", "", "", "", 0, "",
                AgentCartAction.NONE, "", 95, false);
        String customerMessage = "consulta " + "larga ".repeat(100) + "sobre el horario";
        String expectedQuestion = customerMessage.trim()
                .substring(customerMessage.trim().length() - 500).trim();
        String conversation = "CONTEXTO INTERNO EXTENSO\nLOTE_ACTUAL_AGRUPADO:\n" + customerMessage;
        when(decisions.interpret(4L, conversation)).thenReturn(
                new AgentInterpretationResult("GEMINI", "model", "DB", 10, 0, decision));
        when(knowledge.searchForSession(9L, expectedQuestion)).thenReturn(
                new KnowledgeSearchResult(true, "ok", List.of()));
        AgentVerifiedGroundingService service = new AgentVerifiedGroundingService(
                decisions, mock(AgentCatalogQueryService.class), mock(AgentPromotionQueryService.class),
                knowledge, mock(AgentCartService.class), passThroughAudit());

        service.ground(4L, 9L, 30L, conversation);

        assertEquals(500, expectedQuestion.length());
        verify(knowledge).searchForSession(9L, expectedQuestion);
    }

    @Test
    void convertsACompleteSelectionIntoANewPurchaseInAFreshSession() {
        AgentConversationDecision mistaken = new AgentConversationDecision(
                AgentDecisionIntent.SUPPORT_REQUEST, "adicionar Alessia", "ALESSIA RAYAS",
                List.of("ALESSIA RAYAS"), "GUINDA", "M", 1, "", List.of(),
                AgentCartAction.NONE, AgentCommercialAction.REQUEST_SUPPORT,
                "", "", "", "", "", "", "", "", List.of(),
                com.sistemapos.sistematextil.services.agent.ai.AgentCatalogAvailability.ALL,
                "", List.of(), "", 95, true);
        AgentVerifiedGroundingService service = new AgentVerifiedGroundingService(
                mock(AgentDecisionGateway.class), mock(AgentCatalogQueryService.class),
                mock(AgentPromotionQueryService.class), mock(AgentKnowledgeRetriever.class),
                mock(AgentCartService.class), passThroughAudit());

        AgentConversationDecision normalized = service.normalizeFreshRetailPurchase(mistaken,
                "LOTE_ACTUAL_AGRUPADO:\nQuiero adicionar Alessia rayas guinda talla M",
                CrmWhatsappAgentState.IDLE);

        assertEquals(AgentCommercialAction.ADD_PRODUCT, normalized.commercialAction());
        assertEquals(AgentCartAction.ADD_PRODUCT, normalized.cartAction());
        assertFalse(normalized.requiresHumanHelp());
    }

    @Test
    void preservesSupportForAnExplicitlyRegisteredPreviousOrder() {
        AgentConversationDecision mistaken = new AgentConversationDecision(
                AgentDecisionIntent.SUPPORT_REQUEST, "cambiar pedido anterior", "ALESSIA RAYAS",
                List.of("ALESSIA RAYAS"), "GUINDA", "M", 1, "", List.of(),
                AgentCartAction.NONE, AgentCommercialAction.REQUEST_SUPPORT,
                "", "", "", "", "", "", "", "", List.of(),
                com.sistemapos.sistematextil.services.agent.ai.AgentCatalogAvailability.ALL,
                "", List.of(), "", 95, true);
        AgentVerifiedGroundingService service = new AgentVerifiedGroundingService(
                mock(AgentDecisionGateway.class), mock(AgentCatalogQueryService.class),
                mock(AgentPromotionQueryService.class), mock(AgentKnowledgeRetriever.class),
                mock(AgentCartService.class), passThroughAudit());

        AgentConversationDecision normalized = service.normalizeFreshRetailPurchase(mistaken,
                "LOTE_ACTUAL_AGRUPADO:\nQuiero cambiar mi pedido anterior a Alessia rayas guinda talla M",
                CrmWhatsappAgentState.IDLE);

        assertEquals(AgentCommercialAction.REQUEST_SUPPORT, normalized.commercialAction());
        assertTrue(normalized.requiresHumanHelp());
    }

    @Test
    void rejectsDifferentSizesForPiecesOfTheSameSetWithoutChangingTheCart() {
        AgentDecisionGateway decisions = mock(AgentDecisionGateway.class);
        AgentCatalogQueryService catalog = mock(AgentCatalogQueryService.class);
        AgentCartService cart = mock(AgentCartService.class);
        AgentConversationDecision decision = new AgentConversationDecision(
                AgentDecisionIntent.CART_CHANGE, "usar blazer S y pantalón M", "ALICE RAYAS",
                List.of("ALICE RAYAS"), "CHOCOLATE", "M", 1, "", List.of(),
                AgentCartAction.CHANGE_VARIANT, AgentCommercialAction.CHANGE_CART,
                "", "", "", "", "", "", "", "", List.of(),
                com.sistemapos.sistematextil.services.agent.ai.AgentCatalogAvailability.ALL,
                "", List.of(), "", 98, false);
        String conversation = "HISTORIAL: carrito Alice Rayas chocolate S\n\n"
                + "LOTE_ACTUAL_AGRUPADO:\n¿Puede ser blazer S y pantalón M?";
        when(decisions.interpret(4L, conversation)).thenReturn(
                new AgentInterpretationResult("GEMINI", "model", "DB", 10, 0, decision));
        AgentVerifiedGroundingService service = new AgentVerifiedGroundingService(decisions, catalog,
                mock(AgentPromotionQueryService.class), mock(AgentKnowledgeRetriever.class), cart,
                passThroughAudit());

        var result = service.ground(4L, 9L, 30L, conversation);

        assertFalse(result.mutationExecuted());
        assertTrue(result.customerMessage().contains("misma talla"));
        assertTrue(result.customerMessage().contains("No es posible combinar tallas diferentes"));
        verifyNoInteractions(cart);
    }

    @Test
    void detectsDifferentSetPieceSizesInEitherNaturalOrder() {
        assertTrue(AgentVerifiedGroundingService.requestsDifferentSizesForSetPieces(
                "Quiero blazer S y pantalón M"));
        assertTrue(AgentVerifiedGroundingService.requestsDifferentSizesForSetPieces(
                "Talla S para el saco y M para el pantalón"));
        assertTrue(AgentVerifiedGroundingService.requestsDifferentSizesForSetPieces(
                "Blazer S pantalón M"));
        assertFalse(AgentVerifiedGroundingService.requestsDifferentSizesForSetPieces(
                "Quiero blazer y pantalón en talla M"));
    }

    @Test
    void preservesExplicitLiaFamilyInsteadOfUsingLyanaFromTheDecision() {
        AgentDecisionGateway decisions = mock(AgentDecisionGateway.class);
        AgentCatalogQueryService catalog = mock(AgentCatalogQueryService.class);
        AgentConversationDecision wrongDecision = new AgentConversationDecision(
                AgentDecisionIntent.PRODUCT_DETAILS, "consultar Lyana", "LYANA", List.of("LYANA"),
                "", "", 0, "", List.of(), AgentCartAction.NONE, AgentCommercialAction.SHOW_PRODUCT,
                "", "", "", "", "", "", "", "", List.of(),
                com.sistemapos.sistematextil.services.agent.ai.AgentCatalogAvailability.ALL,
                "", List.of(), "", 95, false);
        String conversation = "HISTORIAL: antes consultó Lyana\n\nLOTE_ACTUAL_AGRUPADO:\n¿Y el modelo Lía?";
        when(decisions.interpret(4L, conversation)).thenReturn(
                new AgentInterpretationResult("GEMINI", "model", "DB", 10, 0, wrongDecision));
        ProductSummary liaLiso = new ProductSummary(38, "LIA LISO", "", "CONJUNTO",
                false, null, 80D, 10, List.of("NEGRO"), List.of("S"));
        ProductSummary liaRayas = new ProductSummary(40, "LIA RAYAS", "", "CONJUNTO",
                false, null, 80D, 10, List.of("NEGRO"), List.of("S"));
        ProductSummary lyana = new ProductSummary(60, "LYANA", "", "CONJUNTO",
                false, null, 70D, 10, List.of("NEGRO"), List.of("S"));
        when(catalog.searchProducts(9L, "¿Y el modelo Lía?", 20,
                com.sistemapos.sistematextil.services.agent.ai.AgentCatalogAvailability.ALL))
                .thenReturn(new ProductSearchResult("¿Y el modelo Lía?", 1,
                        List.of(liaLiso, liaRayas, lyana), false));
        when(catalog.searchProducts(9L, "LIA", 20,
                com.sistemapos.sistematextil.services.agent.ai.AgentCatalogAvailability.ALL))
                .thenReturn(new ProductSearchResult("LIA", 1, List.of(liaLiso, liaRayas), false));

        AgentVerifiedGroundingService service = new AgentVerifiedGroundingService(decisions, catalog,
                mock(AgentPromotionQueryService.class), mock(AgentKnowledgeRetriever.class),
                mock(AgentCartService.class), passThroughAudit());

        var result = service.ground(4L, 9L, 30L, conversation);

        assertEquals("LIA", result.decision().productReference());
        assertTrue(result.evidenceJson().contains("LIA LISO"));
        assertTrue(result.evidenceJson().contains("LIA RAYAS"));
        assertFalse(result.evidenceJson().contains("\"query\":\"LYANA\""));
    }

    @Test
    void rejectsAProductGuessedFromAnImageCaptionWithoutAnExplicitModel() {
        AgentDecisionGateway decisions = mock(AgentDecisionGateway.class);
        AgentCatalogQueryService catalog = mock(AgentCatalogQueryService.class);
        AgentConversationDecision wrongDecision = new AgentConversationDecision(
                AgentDecisionIntent.PRODUCT_DETAILS, "consultar Alessia en XS", "ALESSIA",
                List.of("ALESSIA"), "", "XS", 0, "", List.of(), AgentCartAction.NONE,
                AgentCommercialAction.SHOW_PRODUCT_AVAILABILITY, "", "", "", "", "", "", "", "",
                List.of(), com.sistemapos.sistematextil.services.agent.ai.AgentCatalogAvailability.ALL,
                "", List.of(), "", 90, false);
        String conversation = "LOTE_ACTUAL_AGRUPADO:\nTiene es esos modelos en xs?\n[IMAGEN ADJUNTA]";
        when(decisions.interpret(4L, conversation)).thenReturn(
                new AgentInterpretationResult("GEMINI", "model", "DB", 10, 0, wrongDecision));
        when(catalog.searchProducts(9L, "Tiene es esos modelos en xs?\n[IMAGEN ADJUNTA]", 20,
                com.sistemapos.sistematextil.services.agent.ai.AgentCatalogAvailability.ALL))
                .thenReturn(new ProductSearchResult("Tiene es esos modelos en xs?", 1, List.of(), false));
        AgentVerifiedGroundingService service = new AgentVerifiedGroundingService(decisions, catalog,
                mock(AgentPromotionQueryService.class), mock(AgentKnowledgeRetriever.class),
                mock(AgentCartService.class), passThroughAudit());

        var result = service.ground(4L, 9L, 30L, conversation);

        assertTrue(result.decision().productReferences().isEmpty());
        assertEquals("", result.decision().productReference());
        assertTrue(result.evidenceJson().contains("unresolvedImageProductReference"));
        assertFalse(result.evidenceJson().contains("ALESSIA ENTERO"));
    }

    @Test
    void resolvesAnUncodedFutureProductFamilyFromTheCatalog() {
        AgentDecisionGateway decisions = mock(AgentDecisionGateway.class);
        AgentCatalogQueryService catalog = mock(AgentCatalogQueryService.class);
        AgentConversationDecision wrongDecision = new AgentConversationDecision(
                AgentDecisionIntent.PRODUCT_DETAILS, "consultar Norena", "NORENA", List.of("NORENA"),
                "", "", 0, "", List.of(), AgentCartAction.NONE, AgentCommercialAction.SHOW_PRODUCT,
                "", "", "", "", "", "", "", "", List.of(),
                com.sistemapos.sistematextil.services.agent.ai.AgentCatalogAvailability.ALL,
                "", List.of(), "", 95, false);
        String conversation = "LOTE_ACTUAL_AGRUPADO:\n¿Tienes el modelo Nora?";
        when(decisions.interpret(4L, conversation)).thenReturn(
                new AgentInterpretationResult("GEMINI", "model", "DB", 10, 0, wrongDecision));
        ProductSummary noraLiso = new ProductSummary(101, "NORA LISO", "", "CONJUNTO",
                false, null, 80D, 10, List.of(), List.of("S"));
        ProductSummary noraRayas = new ProductSummary(102, "NORA RAYAS", "", "CONJUNTO",
                false, null, 80D, 10, List.of(), List.of("S"));
        ProductSummary norena = new ProductSummary(103, "NORENA", "", "CONJUNTO",
                false, null, 80D, 10, List.of(), List.of("S"));
        when(catalog.searchProducts(9L, "¿Tienes el modelo Nora?", 20,
                com.sistemapos.sistematextil.services.agent.ai.AgentCatalogAvailability.ALL))
                .thenReturn(new ProductSearchResult("Nora", 1, List.of(noraLiso, noraRayas, norena), false));
        when(catalog.searchProducts(9L, "NORA", 20,
                com.sistemapos.sistematextil.services.agent.ai.AgentCatalogAvailability.ALL))
                .thenReturn(new ProductSearchResult("NORA", 1, List.of(noraLiso, noraRayas), false));

        AgentVerifiedGroundingService service = new AgentVerifiedGroundingService(decisions, catalog,
                mock(AgentPromotionQueryService.class), mock(AgentKnowledgeRetriever.class),
                mock(AgentCartService.class), passThroughAudit());
        var result = service.ground(4L, 9L, 30L, conversation);

        assertEquals("NORA", result.decision().productReference());
        assertTrue(result.evidenceJson().contains("NORA LISO"));
        assertTrue(result.evidenceJson().contains("NORA RAYAS"));
    }

    @Test
    void queuesEveryRequestedSizeGuideForMultipleProductFamilies() {
        AgentDecisionGateway decisions = mock(AgentDecisionGateway.class);
        AgentCatalogQueryService catalog = mock(AgentCatalogQueryService.class);
        AgentConversationDecision decision = new AgentConversationDecision(
                AgentDecisionIntent.PRODUCT_DETAILS, "ver guías", "Emma", List.of("Emma", "Annie"),
                "", "", 0, "", List.of(), AgentCartAction.NONE, AgentCommercialAction.SHOW_SIZE_GUIDE,
                "", "", "", "", "", "", "", "", List.of(),
                com.sistemapos.sistematextil.services.agent.ai.AgentCatalogAvailability.ALL,
                "", List.of(), "", 99, false);
        String conversation = "LOTE_ACTUAL_AGRUPADO:\nTabla de tallas para medirme de Emma y Annie";
        when(decisions.interpret(4L, conversation)).thenReturn(
                new AgentInterpretationResult("GEMINI", "model", "DB", 10, 0, decision));
        ProductSummary emma = new ProductSummary(59, "EMMA", "", "CONJUNTO",
                false, null, 67D, 10, List.of(), List.of("S"));
        ProductSummary annieEntero = new ProductSummary(61, "ANNIE ENTERO", "", "CONJUNTO",
                false, null, 75D, 10, List.of(), List.of("S"));
        ProductSummary annieRayas = new ProductSummary(66, "ANNIE RAYAS", "", "CONJUNTO",
                false, null, 75D, 10, List.of(), List.of("S"));
        when(catalog.searchProducts(eq(9L), anyString(), eq(10))).thenReturn(
                new ProductSearchResult("Emma Annie", 1, List.of(emma, annieEntero, annieRayas), false));
        when(catalog.getProduct(9L, 59)).thenReturn(productWithGuide(59, "EMMA", "/guias/emma.webp"));
        when(catalog.getProduct(9L, 61)).thenReturn(
                productWithGuide(61, "ANNIE ENTERO", "/guias/annie-entero.webp"));
        when(catalog.getProduct(9L, 66)).thenReturn(
                productWithGuide(66, "ANNIE RAYAS", "/guias/annie-rayas.webp"));

        AgentVerifiedGroundingService service = new AgentVerifiedGroundingService(decisions, catalog,
                mock(AgentPromotionQueryService.class), mock(AgentKnowledgeRetriever.class),
                mock(AgentCartService.class), passThroughAudit());

        var result = service.ground(4L, 9L, 30L, conversation);

        assertEquals(List.of(59, 61, 66), result.media().stream().map(item -> item.productId()).toList());
        assertTrue(result.media().stream().allMatch(item -> item.type() == MediaType.SIZE_GUIDE));
        assertTrue(result.media().stream().noneMatch(item -> item.suppressIfRecentlySent()));
        assertNull(result.customerMessage());
        assertTrue(result.evidenceJson().contains("sizeGuideDelivery"));
        assertTrue(result.evidenceJson().contains("EMMA"));
        assertTrue(result.evidenceJson().contains("ANNIE ENTERO"));
        assertTrue(result.evidenceJson().contains("ANNIE RAYAS"));
        assertEquals(4, result.verifiedToolCalls());
    }

    @Test
    void sendsAvailableGuidesAndNamesTheProductWithoutAGuide() {
        AgentDecisionGateway decisions = mock(AgentDecisionGateway.class);
        AgentCatalogQueryService catalog = mock(AgentCatalogQueryService.class);
        AgentConversationDecision decision = new AgentConversationDecision(
                AgentDecisionIntent.PRODUCT_DETAILS, "ver guías", "Emma", List.of("Emma", "Annie Rayas"),
                "", "", 0, "", List.of(), AgentCartAction.NONE, AgentCommercialAction.SHOW_SIZE_GUIDE,
                "", "", "", "", "", "", "", "", List.of(),
                com.sistemapos.sistematextil.services.agent.ai.AgentCatalogAvailability.ALL,
                "", List.of(), "", 99, false);
        String conversation = "LOTE_ACTUAL_AGRUPADO:\nGuías de Emma y Annie Rayas";
        when(decisions.interpret(4L, conversation)).thenReturn(
                new AgentInterpretationResult("GEMINI", "model", "DB", 10, 0, decision));
        ProductSummary emma = new ProductSummary(59, "EMMA", "", "CONJUNTO",
                false, null, 67D, 10, List.of(), List.of("S"));
        ProductSummary annieRayas = new ProductSummary(66, "ANNIE RAYAS", "", "CONJUNTO",
                false, null, 75D, 10, List.of(), List.of("S"));
        when(catalog.searchProducts(eq(9L), anyString(), eq(10))).thenReturn(
                new ProductSearchResult("Emma Annie Rayas", 1, List.of(emma, annieRayas), false));
        when(catalog.getProduct(9L, 59)).thenReturn(productWithGuide(59, "EMMA", "/guias/emma.webp"));
        when(catalog.getProduct(9L, 66)).thenReturn(productWithGuide(66, "ANNIE RAYAS", ""));

        AgentVerifiedGroundingService service = new AgentVerifiedGroundingService(decisions, catalog,
                mock(AgentPromotionQueryService.class), mock(AgentKnowledgeRetriever.class),
                mock(AgentCartService.class), passThroughAudit());

        var result = service.ground(4L, 9L, 30L, conversation);

        assertEquals(List.of(59), result.media().stream().map(item -> item.productId()).toList());
        assertNull(result.customerMessage());
        assertTrue(result.evidenceJson().contains("sizeGuideDelivery"));
        assertTrue(result.evidenceJson().contains("EMMA"));
        assertTrue(result.evidenceJson().contains("ANNIE RAYAS"));
        assertTrue(result.evidenceJson().contains("missing"));
    }

    @Test
    void buildsCompleteAvailabilityForEveryRequestedProductAndSizeWithoutGeminiRedaction() {
        AgentDecisionGateway decisions = mock(AgentDecisionGateway.class);
        AgentCatalogQueryService catalog = mock(AgentCatalogQueryService.class);
        AgentConversationDecision decision = new AgentConversationDecision(
                AgentDecisionIntent.PRODUCT_DETAILS, "colores disponibles en talla S", "ALICE LISO",
                List.of("ALICE LISO", "LYANA", "EMMA"), "", "S", 0, "", List.of(),
                AgentCartAction.NONE, AgentCommercialAction.SHOW_PRODUCT,
                "", "", "", "", "", "", "", "", List.of(),
                com.sistemapos.sistematextil.services.agent.ai.AgentCatalogAvailability.ALL,
                "", List.of(), "", 99, false);
        String conversation = "LOTE_ACTUAL_AGRUPADO:\n"
                + "Que colores le queda en Alice Liso y Lyana y Emma talla S";
        when(decisions.interpret(4L, conversation)).thenReturn(
                new AgentInterpretationResult("GEMINI", "model", "DB", 10, 0, decision));

        ProductSummary alice = new ProductSummary(46, "ALICE LISO", "", "CONJUNTO",
                false, null, 90D, 12, List.of("GUINDA", "MARRON", "PERLA", "PLATA"), List.of("S"));
        ProductSummary lyana = new ProductSummary(60, "LYANA", "", "CONJUNTO",
                false, null, 70D, 14,
                List.of("AZUL", "CAMEL", "GRIS OSCURO", "HUESO", "MARRON", "NUDE", "PERLA"),
                List.of("S"));
        ProductSummary emma = new ProductSummary(59, "EMMA", "", "CONJUNTO",
                false, null, 67D, 20,
                List.of("AZUL", "BEIGE CLARO", "CAMEL CLARO", "CHOCOLATE", "GRIS OSCURO"),
                List.of("S"));
        ProductSummary aliceRayas = new ProductSummary(58, "ALICE RAYAS", "", "CONJUNTO",
                false, null, 90D, 20, List.of("AZUL"), List.of("S"));

        when(catalog.searchProducts(9L, "ALICE LISO", 20,
                com.sistemapos.sistematextil.services.agent.ai.AgentCatalogAvailability.ALL)).thenReturn(
                new ProductSearchResult("ALICE LISO", 1, List.of(alice, aliceRayas), false));
        when(catalog.searchProducts(9L, "LYANA", 20,
                com.sistemapos.sistematextil.services.agent.ai.AgentCatalogAvailability.ALL)).thenReturn(
                new ProductSearchResult("LYANA", 1, List.of(lyana), false));
        when(catalog.searchProducts(9L, "EMMA", 20,
                com.sistemapos.sistematextil.services.agent.ai.AgentCatalogAvailability.ALL)).thenReturn(
                new ProductSearchResult("EMMA", 1, List.of(emma), false));
        when(catalog.getProduct(9L, 46)).thenReturn(productWithGuide(
                46, "ALICE LISO", "/guias/alice-liso.webp"));

        when(catalog.getVariants(9L, 46, "", "S")).thenReturn(new VariantResult(
                46, "ALICE LISO", "", "S", List.of(
                availableVariant(4601, "GUINDA", "S", 90D, 1),
                availableVariant(4602, "MARRON", "S", 90D, 2),
                availableVariant(4603, "PERLA", "S", 90D, 6),
                availableVariant(4604, "PLATA", "S", 90D, 3))));
        when(catalog.getVariants(9L, 60, "", "S")).thenReturn(new VariantResult(
                60, "LYANA", "", "S", List.of(
                availableVariant(6001, "AZUL", "S", 70D, 1),
                availableVariant(6002, "CAMEL", "S", 70D, 3),
                availableVariant(6003, "GRIS OSCURO", "S", 70D, 2),
                availableVariant(6004, "HUESO", "S", 70D, 2),
                availableVariant(6005, "MARRON", "S", 70D, 2),
                availableVariant(6006, "NUDE", "S", 70D, 2),
                availableVariant(6007, "PERLA", "S", 70D, 2),
                new VariantSummary(6008, "LY-NEG-S", "NEGRO", "S", 70D, 70D, 0, false))));
        when(catalog.getVariants(9L, 59, "", "S")).thenReturn(new VariantResult(
                59, "EMMA", "", "S", List.of(
                availableVariant(5901, "AZUL", "S", 67D, 10),
                availableVariant(5902, "BEIGE CLARO", "S", 67D, 2),
                availableVariant(5903, "CAMEL CLARO", "S", 67D, 9),
                availableVariant(5904, "CHOCOLATE", "S", 67D, 11),
                availableVariant(5905, "GRIS OSCURO", "S", 67D, 3))));

        AgentVerifiedGroundingService service = new AgentVerifiedGroundingService(decisions, catalog,
                mock(AgentPromotionQueryService.class), mock(AgentKnowledgeRetriever.class),
                mock(AgentCartService.class), passThroughAudit());

        var result = service.ground(4L, 9L, 30L, conversation);

        assertEquals(AgentCommercialAction.SHOW_PRODUCT_AVAILABILITY,
                result.decision().commercialAction());
        assertEquals(9, result.verifiedToolCalls());
        assertEquals(List.of(46), result.media().stream()
                .filter(item -> item.type() == MediaType.SIZE_GUIDE)
                .map(item -> item.productId()).toList());
        assertNull(result.customerMessage());
        assertTrue(result.evidenceJson().contains("requestedVariantAvailability"));
        assertTrue(result.evidenceJson().contains("ALICE LISO"));
        assertTrue(result.evidenceJson().contains("LYANA"));
        assertTrue(result.evidenceJson().contains("NUDE"));
        assertTrue(result.evidenceJson().contains("PERLA"));
        assertTrue(result.evidenceJson().contains("EMMA"));
        assertFalse(result.evidenceJson().contains("ALICE RAYAS"));
        assertFalse(result.evidenceJson().toLowerCase().contains("stock"));
    }

    @Test
    void loadsEveryAvailableVariantWhenTheRequestedCombinationIsUnavailable() {
        AgentDecisionGateway decisions = mock(AgentDecisionGateway.class);
        AgentCatalogQueryService catalog = mock(AgentCatalogQueryService.class);
        AgentConversationDecision decision = new AgentConversationDecision(
                AgentDecisionIntent.PRODUCT_DETAILS, "Melina gris claro talla S", "MELINA",
                "GRIS CLARO", "S", 0, "", List.of(), AgentCartAction.NONE,
                AgentCommercialAction.SHOW_PRODUCT_AVAILABILITY,
                "", "", "", "", "", "", "", "", List.of(),
                com.sistemapos.sistematextil.services.agent.ai.AgentCatalogAvailability.ALL,
                "", List.of(), "", 99, false);
        when(decisions.interpret(4L, "Melina gris claro talla S")).thenReturn(
                new AgentInterpretationResult("GEMINI", "model", "DB", 10, 0, decision));
        ProductSummary melina = new ProductSummary(70, "MELINA", "", "CONJUNTO",
                false, null, 85D, 10, List.of("NEGRO", "GRIS OSCURO"), List.of("XS", "S", "L"));
        when(catalog.searchProducts(9L, "MELINA", 20,
                com.sistemapos.sistematextil.services.agent.ai.AgentCatalogAvailability.ALL))
                .thenReturn(new ProductSearchResult("MELINA", 1, List.of(melina), false));
        when(catalog.getProduct(9L, 70)).thenReturn(productWithGuide(70, "MELINA", ""));
        when(catalog.getVariants(9L, 70, "GRIS CLARO", "S"))
                .thenReturn(new VariantResult(70, "MELINA", "GRIS CLARO", "S", List.of()));
        when(catalog.getVariants(9L, 70, "", "")).thenReturn(new VariantResult(
                70, "MELINA", "", "", List.of(
                        availableVariant(7001, "NEGRO", "XS", 85D, 2),
                        availableVariant(7002, "NEGRO", "L", 85D, 1),
                        availableVariant(7003, "GRIS OSCURO", "S", 85D, 3))));
        AgentVerifiedGroundingService service = new AgentVerifiedGroundingService(decisions, catalog,
                mock(AgentPromotionQueryService.class), mock(AgentKnowledgeRetriever.class),
                mock(AgentCartService.class), passThroughAudit());

        var result = service.ground(4L, 9L, 30L, "Melina gris claro talla S");

        assertTrue(result.evidenceJson().contains("\"variants\":[]"));
        assertTrue(result.evidenceJson().contains("availableAlternatives"));
        assertTrue(result.evidenceJson().contains("GRIS OSCURO"));
        assertTrue(result.evidenceJson().contains("NEGRO"));
        assertTrue(result.evidenceJson().contains("\"size\":\"XS\""));
        assertTrue(result.evidenceJson().contains("\"size\":\"L\""));
        assertTrue(result.evidenceJson().contains("\"price\":85.0"));
        verify(catalog).getVariants(9L, 70, "", "");
    }

    @Test
    void searchesTheWholeCatalogByExactColorAndSizeWhenNoModelWasNamed() {
        AgentDecisionGateway decisions = mock(AgentDecisionGateway.class);
        AgentCatalogQueryService catalog = mock(AgentCatalogQueryService.class);
        AgentConversationDecision decision = new AgentConversationDecision(
                AgentDecisionIntent.PRODUCT_SEARCH, "buscar modelos perla talla L", "", List.of(),
                "PERLA", "L", 0, "", List.of(), AgentCartAction.NONE,
                AgentCommercialAction.SHOW_PRODUCT_AVAILABILITY,
                "", "", "", "", "", "", "", "", List.of(),
                com.sistemapos.sistematextil.services.agent.ai.AgentCatalogAvailability.ALL,
                "", List.of(), "", 98, false);
        String conversation = "LOTE_ACTUAL_AGRUPADO:\n"
                + "Hola queria saber si tiene en stock algun modelo en perla L";
        when(decisions.interpret(4L, conversation)).thenReturn(
                new AgentInterpretationResult("GEMINI", "model", "DB", 10, 0, decision));

        ProductSummary aliceRayas = productWithAvailability(58, "ALICE RAYAS", false,
                List.of(new ColorAvailability("PERLA", List.of("L", "S"), 4)));
        ProductSummary annieEntero = productWithAvailability(64, "ANNIE ENTERO", true,
                List.of(new ColorAvailability("PERLA", List.of("L"), 2)));
        ProductSummary wrongPair = productWithAvailability(59, "EMMA", false,
                List.of(new ColorAvailability("PERLA", List.of("S"), 2),
                        new ColorAvailability("NEGRO", List.of("L"), 3)));
        when(catalog.searchProducts(9L, "", 20,
                com.sistemapos.sistematextil.services.agent.ai.AgentCatalogAvailability.ALL))
                .thenReturn(new ProductSearchResult("", 1,
                        com.sistemapos.sistematextil.services.agent.ai.AgentCatalogAvailability.ALL,
                        List.of(aliceRayas, annieEntero, wrongPair), false));

        AgentVerifiedGroundingService service = new AgentVerifiedGroundingService(decisions, catalog,
                mock(AgentPromotionQueryService.class), mock(AgentKnowledgeRetriever.class),
                mock(AgentCartService.class), passThroughAudit());

        var result = service.ground(4L, 9L, 30L, conversation);

        assertEquals(1, result.verifiedToolCalls());
        assertTrue(result.evidenceJson().contains("catalogAttributeAvailability"));
        assertTrue(result.evidenceJson().contains("ALICE RAYAS"));
        assertTrue(result.evidenceJson().contains("ANNIE ENTERO"));
        assertFalse(result.evidenceJson().contains("EMMA"));
        assertTrue(result.evidenceJson().contains("readyProducts"));
        assertTrue(result.evidenceJson().contains("preorderProducts"));
        assertFalse(result.evidenceJson().toLowerCase().contains("stock"));
        verify(catalog).searchProducts(9L, "", 20,
                com.sistemapos.sistematextil.services.agent.ai.AgentCatalogAvailability.ALL);
    }

    @Test
    void wholesaleActionKeepsAutomaticRetailAttentionAvailable() {
        AgentDecisionGateway decisions = mock(AgentDecisionGateway.class);
        AgentCatalogQueryService catalog = mock(AgentCatalogQueryService.class);
        AgentPromotionQueryService promotions = mock(AgentPromotionQueryService.class);
        AgentKnowledgeRetriever knowledge = mock(AgentKnowledgeRetriever.class);
        AgentCartService cart = mock(AgentCartService.class);
        AgentConversationDecision wholesale = new AgentConversationDecision(
                AgentDecisionIntent.SUPPORT_REQUEST, "cotizacion para reventa", "", "", "", 0, "",
                List.of(), AgentCartAction.NONE, AgentCommercialAction.REQUEST_WHOLESALE_SUPPORT,
                "", "", "", "", "", "", "", "",
                com.sistemapos.sistematextil.services.agent.ai.AgentCatalogAvailability.ALL, 98, false);
        when(decisions.interpret(4L, "Quiero comprar al por mayor para revender")).thenReturn(
                new AgentInterpretationResult("GEMINI", "model", "DB", 10, 0, wholesale));

        AgentVerifiedGroundingService service = new AgentVerifiedGroundingService(decisions, catalog,
                promotions, knowledge, cart, passThroughAudit());
        var grounded = service.ground(4L, 9L, 30L, "Quiero comprar al por mayor para revender");

        assertFalse(grounded.decision().requiresHumanHelp());
        assertEquals(0, grounded.verifiedToolCalls());
        org.mockito.Mockito.verifyNoInteractions(catalog, promotions, knowledge, cart);

        AgentConversationDecision ordinaryMultiItem = wholesale
                .withCommercialAction(AgentCommercialAction.ADD_PRODUCT)
                .withRequiresHumanHelp(false);
        assertFalse(service.enforceHumanHandoffRules(ordinaryMultiItem).requiresHumanHelp());
    }

    @Test
    void deliveryTimingForPreorderUsesTheVerifiedCatalogDateAndRejectsEarlierShipping() {
        AgentDecisionGateway decisions = mock(AgentDecisionGateway.class);
        AgentCatalogQueryService catalog = mock(AgentCatalogQueryService.class);
        AgentConversationDecision decision = new AgentConversationDecision(
                AgentDecisionIntent.DELIVERY_METHOD, "recibir Melina antes del sabado", "Melina", "", "", 0, "",
                List.of(), AgentCartAction.NONE, AgentCommercialAction.SHARE_DELIVERY_TIMING_CONTACT,
                "", "", "", "", "", "", "", "",
                com.sistemapos.sistematextil.services.agent.ai.AgentCatalogAvailability.ALL, 98, true);
        when(decisions.interpret(4L, "Necesito Melina para el sabado")).thenReturn(
                new AgentInterpretationResult("GEMINI", "model", "DB", 10, 0, decision));
        ProductSummary melina = new ProductSummary(10, "MELINA", "", "CONJUNTO",
                true, LocalDate.of(2026, 10, 23), 79D, 8, List.of("NEGRO"), List.of("XS"));
        when(catalog.searchProducts(9L, "Melina", 10)).thenReturn(
                new ProductSearchResult("Melina", 1, List.of(melina), false));

        AgentVerifiedGroundingService service = new AgentVerifiedGroundingService(decisions, catalog,
                mock(AgentPromotionQueryService.class), mock(AgentKnowledgeRetriever.class),
                mock(AgentCartService.class), passThroughAudit());
        var grounded = service.ground(4L, 9L, 30L, "Necesito Melina para el sabado");

        assertFalse(grounded.decision().requiresHumanHelp());
        assertTrue(grounded.customerMessage().contains("23 de octubre de 2026"));
        assertTrue(grounded.customerMessage().contains("No es posible enviarlo antes"));
        assertTrue(grounded.customerMessage().contains("tiempo de llegada depende de Shalom"));
        assertTrue(grounded.customerMessage().contains("no podemos confirmar un día exacto"));
        assertFalse(grounded.customerMessage().contains("área de envíos se comunicará"));
        verify(catalog).searchProducts(9L, "Melina", 10);
    }

    @Test
    void deliveryTimingWithoutAProductExplainsDispatchAndArrivalSeparately() {
        AgentDecisionGateway decisions = mock(AgentDecisionGateway.class);
        AgentConversationDecision decision = new AgentConversationDecision(
                AgentDecisionIntent.DELIVERY_METHOD, "enviar mañana", "", "", "", 0, "",
                List.of(), AgentCartAction.NONE, AgentCommercialAction.SHARE_DELIVERY_TIMING_CONTACT,
                "", "", "", "", "", "", "", "Arequipa",
                com.sistemapos.sistematextil.services.agent.ai.AgentCatalogAvailability.ALL, 98, false);
        when(decisions.interpret(4L, "Pueden enviarlo mañana?")).thenReturn(
                new AgentInterpretationResult("GEMINI", "model", "DB", 10, 0, decision));

        AgentVerifiedGroundingService service = new AgentVerifiedGroundingService(decisions,
                mock(AgentCatalogQueryService.class), mock(AgentPromotionQueryService.class),
                mock(AgentKnowledgeRetriever.class), mock(AgentCartService.class), passThroughAudit());
        var grounded = service.ground(4L, 9L, 30L, "Pueden enviarlo mañana?");

        assertTrue(grounded.customerMessage().contains("envíos a Arequipa por Shalom"));
        assertTrue(grounded.customerMessage().contains("normalmente al día siguiente"));
        assertTrue(grounded.customerMessage().contains("mucho volumen"));
        assertTrue(grounded.customerMessage().contains("en 2 días"));
        assertTrue(grounded.customerMessage().contains("No realizamos despachos el mismo día"));
        assertTrue(grounded.customerMessage().contains("Preventa"));
        assertTrue(grounded.customerMessage().contains("tiempo de llegada depende de Shalom"));
        assertTrue(grounded.customerMessage().contains("no podemos confirmar un día exacto"));
        assertFalse(grounded.customerMessage().contains("1 a 3"));
        assertFalse(grounded.customerMessage().contains("área de envíos"));
    }

    @Test
    void deliveryTimingForReadyStockUsesTheOneToFourDayDispatchWindow() {
        AgentDecisionGateway decisions = mock(AgentDecisionGateway.class);
        AgentCatalogQueryService catalog = mock(AgentCatalogQueryService.class);
        AgentConversationDecision decision = new AgentConversationDecision(
                AgentDecisionIntent.DELIVERY_METHOD, "cuando envían Emma", "Emma", "", "", 0, "",
                List.of(), AgentCartAction.NONE, AgentCommercialAction.SHARE_DELIVERY_TIMING_CONTACT,
                "", "", "", "", "", "", "", "Arequipa",
                com.sistemapos.sistematextil.services.agent.ai.AgentCatalogAvailability.ALL, 98, false);
        when(decisions.interpret(4L, "¿Cuándo envían Emma a Arequipa?")).thenReturn(
                new AgentInterpretationResult("GEMINI", "model", "DB", 10, 0, decision));
        ProductSummary emma = new ProductSummary(11, "EMMA", "", "CONJUNTO",
                false, null, 67D, 8, List.of("NEGRO"), List.of("S"));
        when(catalog.searchProducts(9L, "Emma", 10)).thenReturn(
                new ProductSearchResult("Emma", 1, List.of(emma), false));

        AgentVerifiedGroundingService service = new AgentVerifiedGroundingService(decisions, catalog,
                mock(AgentPromotionQueryService.class), mock(AgentKnowledgeRetriever.class),
                mock(AgentCartService.class), passThroughAudit());
        var grounded = service.ground(4L, 9L, 30L, "¿Cuándo envían Emma a Arequipa?");

        assertTrue(grounded.customerMessage().contains("EMMA tiene disponibilidad inmediata"));
        assertTrue(grounded.customerMessage().contains("normalmente al día siguiente"));
        assertTrue(grounded.customerMessage().contains("en 2 días"));
        assertTrue(grounded.customerMessage().contains("tiempo de llegada depende de Shalom"));
        assertFalse(grounded.customerMessage().contains("1 a 3"));
    }

    @Test
    void promotionActionAlwaysUsesPromotionDataEvenWhenSecondaryIntentWasMisclassified() {
        AgentDecisionGateway decisions = mock(AgentDecisionGateway.class);
        AgentCatalogQueryService catalog = mock(AgentCatalogQueryService.class);
        AgentPromotionQueryService promotions = mock(AgentPromotionQueryService.class);
        AgentConversationDecision decision = new AgentConversationDecision(
                AgentDecisionIntent.PRODUCT_SEARCH, "mostrar promociones vigentes", "", "", "", 0, "",
                List.of(), AgentCartAction.NONE, AgentCommercialAction.SHOW_PROMOTIONS,
                "", "", "", "", "", "", "", "",
                com.sistemapos.sistematextil.services.agent.ai.AgentCatalogAvailability.ALL, 98, false);
        String conversation = "Quiero promociones que me dijeron en el live de TikTok";
        when(decisions.interpret(4L, conversation)).thenReturn(
                new AgentInterpretationResult("GEMINI", "model", "DB", 10, 0, decision));
        PromotionSummary combo = new PromotionSummary(19, "combo 19", "MIXED_PRODUCTS",
                BigDecimal.valueOf(135), BigDecimal.valueOf(142), BigDecimal.valueOf(7),
                null, null, true, List.of());
        when(promotions.searchPromotions(9L, "", 10)).thenReturn(
                new PromotionSearchResult("", 1, List.of(combo), false));

        AgentVerifiedGroundingService service = new AgentVerifiedGroundingService(decisions, catalog,
                promotions, mock(AgentKnowledgeRetriever.class), mock(AgentCartService.class),
                passThroughAudit());
        var grounded = service.ground(4L, 9L, 30L, conversation);

        assertEquals(1, grounded.verifiedToolCalls());
        assertTrue(grounded.evidenceJson().contains("\"name\":\"combo 19\""));
        verify(promotions).searchPromotions(9L, "", 10);
        org.mockito.Mockito.verifyNoInteractions(catalog);
    }

    @Test
    void promotionIntentTakesPriorityOverAnIncorrectProductAction() {
        AgentDecisionGateway decisions = mock(AgentDecisionGateway.class);
        AgentCatalogQueryService catalog = mock(AgentCatalogQueryService.class);
        AgentPromotionQueryService promotions = mock(AgentPromotionQueryService.class);
        AgentConversationDecision decision = new AgentConversationDecision(
                AgentDecisionIntent.PROMOTION_SEARCH, "ver promociones", "", "", "", 0, "",
                List.of(), AgentCartAction.NONE, AgentCommercialAction.SHOW_PRODUCTS,
                "", "", "", "", "", "", "", "",
                com.sistemapos.sistematextil.services.agent.ai.AgentCatalogAvailability.ALL, 96, false);
        when(decisions.interpret(4L, "Que promociones tienes")).thenReturn(
                new AgentInterpretationResult("GEMINI", "model", "DB", 10, 0, decision));
        PromotionSummary combo = new PromotionSummary(30, "combo 30", "MIXED_PRODUCTS",
                BigDecimal.valueOf(125), BigDecimal.valueOf(132), BigDecimal.valueOf(7),
                null, null, true, List.of());
        when(promotions.searchPromotions(9L, "", 10)).thenReturn(
                new PromotionSearchResult("", 1, List.of(combo), false));

        AgentVerifiedGroundingService service = new AgentVerifiedGroundingService(decisions, catalog,
                promotions, mock(AgentKnowledgeRetriever.class), mock(AgentCartService.class),
                passThroughAudit());
        var grounded = service.ground(4L, 9L, 30L, "Que promociones tienes");

        assertTrue(grounded.evidenceJson().contains("\"name\":\"combo 30\""));
        verify(promotions).searchPromotions(9L, "", 10);
        org.mockito.Mockito.verifyNoInteractions(catalog);
    }

    @Test
    void resolvesTheCommercialComboNameWithoutUsingItsInternalDatabaseId() {
        AgentDecisionGateway decisions = mock(AgentDecisionGateway.class);
        AgentPromotionQueryService promotions = mock(AgentPromotionQueryService.class);
        AgentConversationDecision decision = new AgentConversationDecision(
                AgentDecisionIntent.CART_CHANGE, "desea elegir el combo 49", "", "", "", 0, "",
                List.of(), AgentCartAction.ADD_PROMOTION, AgentCommercialAction.CHANGE_CART,
                "", "", "", "", "", "", "", "",
                com.sistemapos.sistematextil.services.agent.ai.AgentCatalogAvailability.ALL, 99, false);
        when(decisions.interpret(4L, "El combo 49 deseo elegir")).thenReturn(
                new AgentInterpretationResult("GEMINI", "model", "DB", 10, 0, decision));
        var combo49Items = List.of(new PromotionItem(10, "ALICE RAYAS", 1, 10),
                new PromotionItem(20, "EMMA", 1, 10));
        PromotionSummary combo49 = new PromotionSummary(83, "combo 49", "MIXED_PRODUCTS",
                BigDecimal.valueOf(145), BigDecimal.valueOf(160), BigDecimal.valueOf(15),
                null, null, true, combo49Items);
        PromotionSummary combo23 = new PromotionSummary(57, "combo 23", "MIXED_PRODUCTS",
                BigDecimal.valueOf(135), BigDecimal.valueOf(145), BigDecimal.valueOf(10),
                null, null, true, combo49Items);
        PromotionSummary combo4 = new PromotionSummary(38, "combo 4", "MIXED_PRODUCTS",
                BigDecimal.valueOf(145), BigDecimal.valueOf(160), BigDecimal.valueOf(15),
                null, null, true, combo49Items);
        when(promotions.searchPromotions(9L, "desea elegir el combo 49", 20)).thenReturn(
                new PromotionSearchResult("desea elegir el combo 49", 1,
                        List.of(combo49, combo23, combo4), false));
        when(promotions.getPromotion(9L, 83)).thenReturn(new PromotionDetail(83, "combo 49",
                "MIXED_PRODUCTS", BigDecimal.valueOf(145), BigDecimal.valueOf(160),
                BigDecimal.valueOf(15), null, null, true, combo49Items, 1, true));

        AgentVerifiedGroundingService service = new AgentVerifiedGroundingService(decisions,
                mock(AgentCatalogQueryService.class), promotions, mock(AgentKnowledgeRetriever.class),
                mock(AgentCartService.class), passThroughAudit());
        var grounded = service.ground(4L, 9L, 30L, "El combo 49 deseo elegir");

        assertNull(grounded.customerMessage());
        assertTrue(grounded.evidenceJson().contains("\"name\":\"combo 49\""));
        verify(promotions).getPromotion(9L, 83);
    }

    @Test
    void addsAnyVerifiedComboOnlyAfterResolvingEveryRequiredVariant() {
        AgentDecisionGateway decisions = mock(AgentDecisionGateway.class);
        AgentCatalogQueryService catalog = mock(AgentCatalogQueryService.class);
        AgentPromotionQueryService promotions = mock(AgentPromotionQueryService.class);
        AgentCartService cart = mock(AgentCartService.class);
        AgentConversationDecision decision = new AgentConversationDecision(
                AgentDecisionIntent.CART_CHANGE, "comprar combo 30", "", "", "", 0, "combo 30",
                List.of(new PromotionSelection("LYANA", "MARRON", "XS", 1),
                        new PromotionSelection("EMMA", "AZUL", "XS", 1)),
                AgentCartAction.ADD_PROMOTION, AgentCommercialAction.CHANGE_CART,
                "", "", "", "", "", "", "", "",
                com.sistemapos.sistematextil.services.agent.ai.AgentCatalogAvailability.ALL, 99, false);
        when(decisions.interpret(4L, "Quiero pagar el combo 30")).thenReturn(
                new AgentInterpretationResult("GEMINI", "model", "DB", 10, 0, decision));

        var items = List.of(new PromotionItem(10, "LYANA", 1, 20),
                new PromotionItem(20, "EMMA", 1, 20));
        PromotionSummary summary = new PromotionSummary(56, "combo 30", "MIXED_PRODUCTS",
                BigDecimal.valueOf(125), BigDecimal.valueOf(132), BigDecimal.valueOf(7),
                null, null, true, items);
        when(promotions.searchPromotions(9L, "combo 30", 20)).thenReturn(
                new PromotionSearchResult("combo 30", 1, List.of(summary), false));
        when(promotions.getPromotion(9L, 56)).thenReturn(new PromotionDetail(56, "combo 30",
                "MIXED_PRODUCTS", BigDecimal.valueOf(125), BigDecimal.valueOf(132), BigDecimal.valueOf(7),
                null, null, true, items, 1, true));

        when(catalog.getVariants(9L, 10, "MARRON", "XS")).thenReturn(new VariantResult(10, "LYANA",
                "MARRON", "XS", List.of(new VariantSummary(101, "LY-MA-XS", "MARRON", "XS",
                        67D, 67D, 3, true))));
        when(catalog.getVariants(9L, 20, "AZUL", "XS")).thenReturn(new VariantResult(20, "EMMA",
                "AZUL", "XS", List.of(new VariantSummary(201, "EM-AZ-XS", "AZUL", "XS",
                        65D, 65D, 3, true))));
        when(catalog.getStock(9L, 101, 1)).thenReturn(
                new StockResult(101, 10, "LYANA", "MARRON", "XS", 1, 3, true, 1, true));
        when(catalog.getStock(9L, 201, 1)).thenReturn(
                new StockResult(201, 20, "EMMA", "AZUL", "XS", 1, 3, true, 1, true));
        CartResult result = new CartResult(70L, "DRAFT", "PEN", BigDecimal.valueOf(132),
                BigDecimal.valueOf(7), BigDecimal.valueOf(125), List.of(),
                List.of(new CartPromotionView(56, "combo 30", 1, BigDecimal.valueOf(132),
                        BigDecimal.valueOf(125), BigDecimal.valueOf(7))));
        when(cart.addCombo(9L, 56, List.of(new ComboSelection(101, 1), new ComboSelection(201, 1)),
                "agent-job-30:verified-add-promotion")).thenReturn(result);

        AgentVerifiedGroundingService service = new AgentVerifiedGroundingService(decisions, catalog,
                promotions, mock(AgentKnowledgeRetriever.class), cart, passThroughAudit());
        var grounded = service.ground(4L, 9L, 30L, "Quiero pagar el combo 30");

        assertTrue(grounded.mutationExecuted());
        assertTrue(grounded.evidenceJson().contains("\"promotionId\":56"));
        verify(cart).addCombo(9L, 56,
                List.of(new ComboSelection(101, 1), new ComboSelection(201, 1)),
                "agent-job-30:verified-add-promotion");
    }

    @Test
    void completesMissingComboSelectionFromThePendingDraftWithoutReadingTheCart() {
        AgentDecisionGateway decisions = mock(AgentDecisionGateway.class);
        AgentCatalogQueryService catalog = mock(AgentCatalogQueryService.class);
        AgentPromotionQueryService promotions = mock(AgentPromotionQueryService.class);
        AgentCartService cart = mock(AgentCartService.class);
        AgentConversationDecision decision = new AgentConversationDecision(
                AgentDecisionIntent.CART_CHANGE, "corregir Emma del combo", "EMMA", "AZUL", "M", 1,
                "combo 30", List.of(new PromotionSelection("EMMA", "AZUL", "M", 1)),
                AgentCartAction.ADD_PROMOTION, AgentCommercialAction.CHANGE_CART,
                "", "", "", "", "", "", "", "",
                com.sistemapos.sistematextil.services.agent.ai.AgentCatalogAvailability.ALL, 99, false);
        when(decisions.interpret(4L, "Emma azul talla M")).thenReturn(
                new AgentInterpretationResult("GEMINI", "model", "DB", 10, 0, decision));

        var items = List.of(new PromotionItem(59, "EMMA", 1, 20),
                new PromotionItem(60, "LYANA", 1, 20));
        PromotionSummary summary = new PromotionSummary(56, "combo 30", "MIXED_PRODUCTS",
                BigDecimal.valueOf(125), BigDecimal.valueOf(132), BigDecimal.valueOf(7),
                null, null, true, items);
        when(promotions.searchPromotions(9L, "combo 30", 20)).thenReturn(
                new PromotionSearchResult("combo 30", 1, List.of(summary), false));
        when(promotions.getPromotion(9L, 56)).thenReturn(new PromotionDetail(56, "combo 30",
                "MIXED_PRODUCTS", BigDecimal.valueOf(125), BigDecimal.valueOf(132), BigDecimal.valueOf(7),
                null, null, true, items, 1, true));
        when(cart.pendingPromotion(9L)).thenReturn(java.util.Optional.of(new PendingPromotionDraft(
                56, "combo 30", List.of(
                        new PendingPromotionSelection(59, "EMMA", "Negro", "L", 1, 1535),
                        new PendingPromotionSelection(60, "LYANA", "AZUL", "L", 1, 1599)), 29L)));
        when(catalog.getVariants(9L, 59, "AZUL", "M")).thenReturn(new VariantResult(59, "EMMA",
                "AZUL", "M", List.of(new VariantSummary(1536, "EM-AZU-M", "AZUL", "M",
                        65D, 65D, 10, true))));
        when(catalog.getVariants(9L, 60, "AZUL", "L")).thenReturn(new VariantResult(60, "LYANA",
                "AZUL", "L", List.of(new VariantSummary(1599, "LY-AZU-L", "AZUL", "L",
                        67D, 67D, 10, true))));
        when(catalog.getStock(9L, 1536, 1)).thenReturn(
                new StockResult(1536, 59, "EMMA", "AZUL", "M", 1, 10, true, 1, true));
        when(catalog.getStock(9L, 1599, 1)).thenReturn(
                new StockResult(1599, 60, "LYANA", "AZUL", "L", 1, 10, true, 1, true));
        CartResult result = new CartResult(70L, "DRAFT", "PEN", BigDecimal.valueOf(132),
                BigDecimal.valueOf(7), BigDecimal.valueOf(125), List.of(),
                List.of(new CartPromotionView(56, "combo 30", 1, BigDecimal.valueOf(132),
                        BigDecimal.valueOf(125), BigDecimal.valueOf(7))));
        when(cart.addCombo(9L, 56, List.of(new ComboSelection(1536, 1), new ComboSelection(1599, 1)),
                "agent-job-30:verified-add-promotion")).thenReturn(result);

        AgentVerifiedGroundingService service = new AgentVerifiedGroundingService(decisions, catalog,
                promotions, mock(AgentKnowledgeRetriever.class), cart, passThroughAudit());
        var grounded = service.ground(4L, 9L, 30L, "Emma azul talla M");

        assertTrue(grounded.mutationExecuted());
        verify(cart).addCombo(9L, 56,
                List.of(new ComboSelection(1536, 1), new ComboSelection(1599, 1)),
                "agent-job-30:verified-add-promotion");
    }

    @Test
    void asksOnlyForTheMissingComboVariantInsteadOfClaimingItWasAdded() {
        AgentDecisionGateway decisions = mock(AgentDecisionGateway.class);
        AgentPromotionQueryService promotions = mock(AgentPromotionQueryService.class);
        AgentCartService cart = mock(AgentCartService.class);
        AgentConversationDecision decision = new AgentConversationDecision(
                AgentDecisionIntent.CART_CHANGE, "comprar combo 30", "", "", "", 0,
                "combo 30", List.of(),
                AgentCartAction.ADD_PROMOTION, AgentCommercialAction.CHANGE_CART,
                "", "", "", "", "", "", "", "",
                com.sistemapos.sistematextil.services.agent.ai.AgentCatalogAvailability.ALL, 99, false);
        when(decisions.interpret(4L, "quiero ese combo")).thenReturn(
                new AgentInterpretationResult("GEMINI", "model", "DB", 10, 0, decision));
        var items = List.of(new PromotionItem(59, "EMMA", 1, 20),
                new PromotionItem(60, "LYANA", 1, 20));
        PromotionSummary summary = new PromotionSummary(56, "combo 30", "MIXED_PRODUCTS",
                BigDecimal.valueOf(125), BigDecimal.valueOf(132), BigDecimal.valueOf(7),
                null, null, true, items);
        when(promotions.searchPromotions(9L, "combo 30", 20)).thenReturn(
                new PromotionSearchResult("combo 30", 1, List.of(summary), false));
        when(promotions.getPromotion(9L, 56)).thenReturn(new PromotionDetail(56, "combo 30",
                "MIXED_PRODUCTS", BigDecimal.valueOf(125), BigDecimal.valueOf(132), BigDecimal.valueOf(7),
                null, null, true, items, 1, true));
        when(cart.recalculate(9L, "agent-job-30:promotion-cart-context")).thenReturn(
                new CartResult(70L, "DRAFT", "PEN", BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, List.of(), List.of()));

        AgentVerifiedGroundingService service = new AgentVerifiedGroundingService(decisions,
                mock(AgentCatalogQueryService.class), promotions, mock(AgentKnowledgeRetriever.class), cart,
                passThroughAudit());
        var grounded = service.ground(4L, 9L, 30L, "quiero ese combo");

        assertNull(grounded.customerMessage());
        assertTrue(!grounded.mutationExecuted());
        org.mockito.Mockito.verify(cart, org.mockito.Mockito.never())
                .addCombo(anyLong(), anyInt(), any(), anyString());
    }

    @Test
    void persistsAValidPartialComboChoiceOutsideTheCart() {
        AgentDecisionGateway decisions = mock(AgentDecisionGateway.class);
        AgentCatalogQueryService catalog = mock(AgentCatalogQueryService.class);
        AgentPromotionQueryService promotions = mock(AgentPromotionQueryService.class);
        AgentCartService cart = mock(AgentCartService.class);
        AgentConversationDecision decision = new AgentConversationDecision(
                AgentDecisionIntent.CART_CHANGE, "elegir Emma del combo", "EMMA", "Negro", "XS", 1,
                "combo 30", List.of(new PromotionSelection("EMMA", "Negro", "XS", 1)),
                AgentCartAction.ADD_PROMOTION, AgentCommercialAction.CHANGE_CART,
                "", "", "", "", "", "", "", "",
                com.sistemapos.sistematextil.services.agent.ai.AgentCatalogAvailability.ALL, 99, false);
        when(decisions.interpret(4L, "quiero negro xs")).thenReturn(
                new AgentInterpretationResult("GEMINI", "model", "DB", 10, 0, decision));
        var items = List.of(new PromotionItem(59, "EMMA", 1, 20),
                new PromotionItem(60, "LYANA", 1, 20));
        PromotionSummary summary = new PromotionSummary(56, "combo 30", "MIXED_PRODUCTS",
                BigDecimal.valueOf(125), BigDecimal.valueOf(132), BigDecimal.valueOf(7),
                null, null, true, items);
        when(promotions.searchPromotions(9L, "combo 30", 20)).thenReturn(
                new PromotionSearchResult("combo 30", 1, List.of(summary), false));
        when(promotions.getPromotion(9L, 56)).thenReturn(new PromotionDetail(56, "combo 30",
                "MIXED_PRODUCTS", BigDecimal.valueOf(125), BigDecimal.valueOf(132), BigDecimal.valueOf(7),
                null, null, true, items, 1, true));
        when(catalog.getVariants(9L, 59, "Negro", "XS")).thenReturn(new VariantResult(59, "EMMA",
                "Negro", "XS", List.of(new VariantSummary(1538, "EM-NEG-XS", "Negro", "XS",
                        65D, 65D, 10, true))));
        when(catalog.getStock(9L, 1538, 1)).thenReturn(
                new StockResult(1538, 59, "EMMA", "Negro", "XS", 1, 10, true, 1, true));
        AgentVerifiedGroundingService service = new AgentVerifiedGroundingService(decisions, catalog,
                promotions, mock(AgentKnowledgeRetriever.class), cart, passThroughAudit());
        var grounded = service.ground(4L, 9L, 30L, "quiero negro xs");

        assertNull(grounded.customerMessage());
        verify(cart).savePendingPromotion(9L, 56, "combo 30",
                List.of(new PendingPromotionSelection(59, "EMMA", "Negro", "XS", 1, 1538)), 30L);
        org.mockito.Mockito.verify(cart, org.mockito.Mockito.never())
                .addProduct(anyLong(), anyInt(), anyInt(), anyString());
        assertTrue(grounded.evidenceJson().contains("pendingPromotion"));
    }

    @Test
    void replacesACompleteCorrectedOrderInOneAtomicCartOperation() {
        AgentDecisionGateway decisions = mock(AgentDecisionGateway.class);
        AgentCatalogQueryService catalog = mock(AgentCatalogQueryService.class);
        AgentPromotionQueryService promotions = mock(AgentPromotionQueryService.class);
        AgentCartService cart = mock(AgentCartService.class);
        List<PromotionSelection> finalLines = List.of(
                new PromotionSelection("ANGUIE LISO", "AZUL", "L", 1),
                new PromotionSelection("MELINA", "NEGRO", "L", 1));
        AgentConversationDecision decision = new AgentConversationDecision(
                AgentDecisionIntent.CART_CHANGE, "este es mi pedido final", "", "", "", 0,
                "combo 51", finalLines, AgentCartAction.REPLACE_CART, AgentCommercialAction.CHANGE_CART,
                "", "", "", "", "", "", "", "",
                com.sistemapos.sistematextil.services.agent.ai.AgentCatalogAvailability.ALL, 99, false);
        when(decisions.interpret(4L, "este es mi pedido final")).thenReturn(
                new AgentInterpretationResult("GEMINI", "model", "DB", 10, 0, decision));

        ProductSummary anguie = new ProductSummary(79, "ANGUIE LISO", "", "CONJUNTO",
                true, null, 75D, 30, List.of("AZUL"), List.of("L"));
        ProductSummary melina = new ProductSummary(78, "MELINA", "", "CONJUNTO",
                true, null, 79D, 16, List.of("NEGRO"), List.of("L"));
        when(catalog.searchProducts(9L, "ANGUIE LISO", 10)).thenReturn(
                new ProductSearchResult("ANGUIE LISO", 1, List.of(anguie), false));
        when(catalog.searchProducts(9L, "MELINA", 10)).thenReturn(
                new ProductSearchResult("MELINA", 1, List.of(melina), false));
        when(catalog.getVariants(9L, 79, "AZUL", "L")).thenReturn(new VariantResult(79, "ANGUIE LISO",
                "AZUL", "L", List.of(new VariantSummary(1880, "ANG-AZU-L", "AZUL", "L",
                        75D, 75D, 30, true))));
        when(catalog.getVariants(9L, 78, "NEGRO", "L")).thenReturn(new VariantResult(78, "MELINA",
                "NEGRO", "L", List.of(new VariantSummary(1864, "MEL-NEG-L", "NEGRO", "L",
                        79D, 79D, 16, true))));
        when(catalog.getStock(9L, 1880, 1)).thenReturn(
                new StockResult(1880, 79, "ANGUIE LISO", "AZUL", "L", 1, 30, true, 1, true));
        when(catalog.getStock(9L, 1864, 1)).thenReturn(
                new StockResult(1864, 78, "MELINA", "NEGRO", "L", 1, 16, true, 1, true));
        var promoItems = List.of(new PromotionItem(79, "ANGUIE LISO", 1, 30),
                new PromotionItem(78, "MELINA", 1, 16));
        PromotionSummary combo = new PromotionSummary(150, "combo 51", "MIXED_PRODUCTS",
                BigDecimal.valueOf(149), BigDecimal.valueOf(154), BigDecimal.valueOf(5),
                null, null, true, promoItems);
        when(promotions.searchPromotions(9L, "combo 51", 20)).thenReturn(
                new PromotionSearchResult("combo 51", 1, List.of(combo), false));
        CartResult result = new CartResult(79L, "DRAFT", "PEN", BigDecimal.valueOf(154),
                BigDecimal.valueOf(5), BigDecimal.valueOf(149), List.of(),
                List.of(new CartPromotionView(150, "combo 51", 1, BigDecimal.valueOf(154),
                        BigDecimal.valueOf(149), BigDecimal.valueOf(5))));
        when(cart.replaceCart(9L, List.of(new ComboSelection(1880, 1), new ComboSelection(1864, 1)),
                150, "agent-job-30:verified-replace-cart")).thenReturn(result);

        AgentVerifiedGroundingService service = new AgentVerifiedGroundingService(decisions, catalog,
                promotions, mock(AgentKnowledgeRetriever.class), cart, passThroughAudit());
        var grounded = service.ground(4L, 9L, 30L, "este es mi pedido final");

        assertTrue(grounded.mutationExecuted());
        verify(cart).replaceCart(9L,
                List.of(new ComboSelection(1880, 1), new ComboSelection(1864, 1)),
                150, "agent-job-30:verified-replace-cart");
        verify(cart, org.mockito.Mockito.never()).addProduct(anyLong(), anyInt(), anyInt(), anyString());
    }

    @Test
    void springCreatesTheProductionBeanWithItsExplicitConstructor() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(AgentDecisionGateway.class, () -> mock(AgentDecisionGateway.class));
            context.registerBean(AgentCatalogQueryService.class, () -> mock(AgentCatalogQueryService.class));
            context.registerBean(AgentPromotionQueryService.class, () -> mock(AgentPromotionQueryService.class));
            context.registerBean(AgentKnowledgeRetriever.class, () -> mock(AgentKnowledgeRetriever.class));
            context.registerBean(AgentCartService.class, () -> mock(AgentCartService.class));
            context.registerBean(AgentDeterministicCommerceService.class,
                    () -> mock(AgentDeterministicCommerceService.class));
            context.registerBean(ProductSizeGuideQueryService.class,
                    () -> mock(ProductSizeGuideQueryService.class));
            context.registerBean(AgentOrderDeliveryQueryService.class,
                    () -> mock(AgentOrderDeliveryQueryService.class));
            context.registerBean(AgentToolCallAuditService.class, () -> mock(AgentToolCallAuditService.class));
            context.register(AgentVerifiedGroundingService.class);
            context.refresh();

            assertNotNull(context.getBean(AgentVerifiedGroundingService.class));
        }
    }

    @Test
    void groundsAnExactProductAndAddsItsImageAndAutomaticSizeGuide() {
        AgentDecisionGateway decisions = mock(AgentDecisionGateway.class);
        AgentCatalogQueryService catalog = mock(AgentCatalogQueryService.class);
        AgentToolCallAuditService audit = passThroughAudit();
        AgentConversationDecision decision = new AgentConversationDecision(
                AgentDecisionIntent.PRODUCT_DETAILS, "ver Annie rayas", "Annie rayas", "rosa", null,
                null, null, com.sistemapos.sistematextil.services.agent.ai.AgentCartAction.NONE,
                "mostrar detalles", 98, false);
        when(decisions.interpret(4L, "Annie rayas color rosa")).thenReturn(
                new AgentInterpretationResult("GEMINI", "model", "DB", Duration.ZERO.toMillis(), 1, decision));
        ProductSummary summary = new ProductSummary(20, "ANNIE RAYAS", "ARUBA", "CONJUNTO",
                false, null, 75D, 10, List.of("ROSA"), List.of("S", "M"));
        when(catalog.searchProducts(9L, "Annie rayas", 20,
                com.sistemapos.sistematextil.services.agent.ai.AgentCatalogAvailability.ALL)).thenReturn(
                new ProductSearchResult("Annie rayas", 1, List.of(summary), false));
        when(catalog.getProduct(9L, 20)).thenReturn(new ProductDetail(20, "ANNIE RAYAS", "ARUBA",
                "CONJUNTO", false, null, "/guias/annie.webp", null,
                List.of("ROSA"), List.of("S", "M"), 10, 75D, 2));
        when(catalog.getProductImage(9L, 20, "ROSA")).thenReturn(new ProductImageResult(20,
                "ANNIE RAYAS", "ROSA", "/productos/annie.webp", null,
                List.of(new ProductImage("ROSA", "/productos/annie-rosa.webp", null, true))));

        AgentVerifiedGroundingService service = new AgentVerifiedGroundingService(decisions, catalog,
                mock(AgentPromotionQueryService.class), mock(AgentKnowledgeRetriever.class),
                mock(com.sistemapos.sistematextil.services.agent.cart.AgentCartService.class), audit);
        var result = service.ground(4L, 9L, 30L, "Annie rayas color rosa");

        assertEquals(3, result.verifiedToolCalls());
        assertEquals(2, result.media().size());
        assertEquals(MediaType.SIZE_GUIDE, result.media().getFirst().type());
        assertTrue(result.media().getFirst().suppressIfRecentlySent());
        assertEquals(MediaType.PRODUCT_IMAGE, result.media().get(1).type());
        assertEquals(null, result.customerMessage());
        assertTrue(result.evidenceJson().contains("ANNIE RAYAS"));
        assertTrue(result.evidenceJson().contains("annie-rosa.webp"));
    }

    @Test
    void sendsUpToEightAvailableColorImagesAndKeepsEveryAvailableColorInEvidence() {
        AgentDecisionGateway decisions = mock(AgentDecisionGateway.class);
        AgentCatalogQueryService catalog = mock(AgentCatalogQueryService.class);
        AgentConversationDecision decision = new AgentConversationDecision(
                AgentDecisionIntent.PRODUCT_DETAILS, "mostrar colores de Melina en imagenes", "Melina", "", "",
                0, "", AgentCartAction.NONE, AgentCommercialAction.SHOW_PRODUCT_IMAGES,
                "", "", "", "", "", "", "", "",
                com.sistemapos.sistematextil.services.agent.ai.AgentCatalogAvailability.ALL, 99, false);
        when(decisions.interpret(4L, "Muestrame los colores de Melina en imagenes")).thenReturn(
                new AgentInterpretationResult("GEMINI", "model", "DB", 10, 0, decision));
        List<String> availableColors = List.of(
                "NEGRO", "TOPO", "PLATA", "ROSADO", "VINO", "CAMEL", "CHOCOLATE", "AZUL", "PERLA");
        ProductSummary summary = new ProductSummary(88, "MELINA", "ARUBA", "CONJUNTO",
                false, null, 79D, 50, availableColors, List.of("XS", "S", "M", "L"));
        when(catalog.searchProducts(9L, "Melina", 20,
                com.sistemapos.sistematextil.services.agent.ai.AgentCatalogAvailability.ALL)).thenReturn(
                new ProductSearchResult("Melina", 1, List.of(summary), false));
        when(catalog.getProduct(9L, 88)).thenReturn(new ProductDetail(88, "MELINA", "ARUBA", "CONJUNTO",
                false, null, null, null, availableColors, List.of("XS", "S", "M", "L"), 50, 79D, 28));
        when(catalog.getProductImage(9L, 88, "")).thenReturn(new ProductImageResult(
                88, "MELINA", "", "/productos/melina.webp", null, List.of(
                new ProductImage("NEGRO", "/productos/melina-negro.webp", null, true),
                new ProductImage("TOPO", "/productos/melina-topo.webp", null, true),
                new ProductImage("PLATA", "/productos/melina-plata.webp", null, true),
                new ProductImage("ROSADO", "/productos/melina-rosado.webp", null, true),
                new ProductImage("VINO", "/productos/melina-vino.webp", null, true),
                new ProductImage("CAMEL", "/productos/melina-camel.webp", null, true),
                new ProductImage("CHOCOLATE", "/productos/melina-chocolate.webp", null, true),
                new ProductImage("AZUL", "/productos/melina-azul.webp", null, true),
                new ProductImage("PERLA", "/productos/melina-perla.webp", null, true))));
        AgentVerifiedGroundingService service = new AgentVerifiedGroundingService(decisions, catalog,
                mock(AgentPromotionQueryService.class), mock(AgentKnowledgeRetriever.class),
                mock(AgentCartService.class), passThroughAudit());

        var result = service.ground(4L, 9L, 30L, "Muestrame los colores de Melina en imagenes");

        assertEquals(3, result.verifiedToolCalls());
        assertEquals(8, result.media().size());
        assertEquals(List.of("NEGRO", "TOPO", "PLATA", "ROSADO", "VINO", "CAMEL", "CHOCOLATE", "AZUL"),
                result.media().stream().map(media -> media.color()).toList());
        assertTrue(result.evidenceJson().contains("productColorGallery"));
        assertTrue(result.evidenceJson().contains("CHOCOLATE"));
        assertTrue(result.evidenceJson().contains("colorsWithoutImage"));
        assertTrue(result.evidenceJson().contains("\"hasMoreColors\":true"));
    }

    @Test
    void recoversAnIncompleteProductSelectionMisclassifiedAsCartChange() {
        AgentDecisionGateway decisions = mock(AgentDecisionGateway.class);
        AgentCatalogQueryService catalog = mock(AgentCatalogQueryService.class);
        AgentDeterministicCommerceService commerce = mock(AgentDeterministicCommerceService.class);
        AgentConversationDecision decision = new AgentConversationDecision(
                AgentDecisionIntent.PRODUCT_DETAILS, "elegir Annie Entero rojo", "ANNIE ENTERO", "ROJO", "",
                0, "", AgentCartAction.NONE, AgentCommercialAction.CHANGE_CART,
                "", "", "", "", "", "", "", "",
                com.sistemapos.sistematextil.services.agent.ai.AgentCatalogAvailability.ALL, 96, false);
        when(decisions.interpret(4L, "annie entero color rojo qiieeo")).thenReturn(
                new AgentInterpretationResult("GEMINI", "model", "DB", 10, 0, decision));
        when(commerce.normalizeShalomAgencyRefinement(9L, decision)).thenReturn(decision);
        when(commerce.execute(eq(9L), eq(30L), any(AgentConversationDecision.class)))
                .thenReturn(AgentDeterministicCommerceService.Outcome.notHandled());
        ProductSummary summary = new ProductSummary(61, "ANNIE ENTERO", "ARUBA", "CONJUNTO",
                false, null, 75D, 343,
                List.of("AZUL", "BEIGE CLARO", "CHOCOLATE", "NEGRO"), List.of("XS", "S", "M", "L"));
        when(catalog.searchProducts(9L, "ANNIE ENTERO", 20,
                com.sistemapos.sistematextil.services.agent.ai.AgentCatalogAvailability.ALL)).thenReturn(
                new ProductSearchResult("ANNIE ENTERO", 1, List.of(summary), false));
        when(catalog.getProduct(9L, 61)).thenReturn(new ProductDetail(61, "ANNIE ENTERO", "ARUBA",
                "CONJUNTO", false, null, null, null,
                List.of("AZUL", "BEIGE CLARO", "CHOCOLATE", "NEGRO"), List.of("XS", "S", "M", "L"),
                343, 75D, 16));
        when(catalog.getProductImage(9L, 61, "ROJO")).thenReturn(new ProductImageResult(
                61, "ANNIE ENTERO", "ROJO", "/annie-entero.webp", null, List.of()));
        AgentVerifiedGroundingService service = new AgentVerifiedGroundingService(decisions, catalog,
                mock(AgentPromotionQueryService.class), mock(AgentKnowledgeRetriever.class),
                mock(AgentCartService.class), commerce, mock(ProductSizeGuideQueryService.class),
                passThroughAudit());

        var result = service.ground(4L, 9L, 30L, "annie entero color rojo qiieeo");

        assertEquals(3, result.verifiedToolCalls());
        assertTrue(result.evidenceJson().contains("ANNIE ENTERO"));
        assertTrue(result.evidenceJson().contains("BEIGE CLARO"));
        assertTrue(result.evidenceJson().contains("requestedColorAvailability"));
        assertTrue(result.evidenceJson().contains("\"available\":false"));
        assertTrue(result.media().isEmpty());
    }

    @Test
    void showsEveryMatchingFinishWithImagesAndSizeGuidesBeforeAskingWhichOne() {
        AgentDecisionGateway decisions = mock(AgentDecisionGateway.class);
        AgentCatalogQueryService catalog = mock(AgentCatalogQueryService.class);
        AgentConversationDecision decision = new AgentConversationDecision(
                AgentDecisionIntent.PRODUCT_DETAILS, "consultar Alessia", "Alessia", "", "", 0, "",
                AgentCartAction.NONE, AgentCommercialAction.SHOW_PRODUCT,
                "", "", "", "", "", "", "", "",
                com.sistemapos.sistematextil.services.agent.ai.AgentCatalogAvailability.ALL, 99, false);
        when(decisions.interpret(4L, "Tienes Alessia?")).thenReturn(
                new AgentInterpretationResult("GEMINI", "model", "DB", 10, 0, decision));
        ProductSummary entero = new ProductSummary(68, "ALESSIA ENTERO", "ARUBA", "CONJUNTO",
                false, null, 75D, 10, List.of("NEGRO"), List.of("XS"));
        ProductSummary rayas = new ProductSummary(77, "ALESSIA RAYAS", "ARUBA", "CONJUNTO",
                false, null, 75D, 8, List.of("AZUL"), List.of("S"));
        when(catalog.searchProducts(9L, "Alessia", 20,
                com.sistemapos.sistematextil.services.agent.ai.AgentCatalogAvailability.ALL)).thenReturn(
                new ProductSearchResult("Alessia", 1, List.of(entero, rayas), false));
        when(catalog.getProductImage(9L, 68, "")).thenReturn(new ProductImageResult(
                68, "ALESSIA ENTERO", "", "/alessia-entero.webp", null, List.of()));
        when(catalog.getProductImage(9L, 77, "")).thenReturn(new ProductImageResult(
                77, "ALESSIA RAYAS", "", "/alessia-rayas.webp", null, List.of()));
        when(catalog.getProduct(9L, 68)).thenReturn(productWithGuide(68, "ALESSIA ENTERO", "/guias/alessia-entero.webp"));
        when(catalog.getProduct(9L, 77)).thenReturn(productWithGuide(77, "ALESSIA RAYAS", "/guias/alessia-rayas.webp"));
        AgentVerifiedGroundingService service = new AgentVerifiedGroundingService(decisions, catalog,
                mock(AgentPromotionQueryService.class), mock(AgentKnowledgeRetriever.class),
                mock(AgentCartService.class), passThroughAudit());

        var result = service.ground(4L, 9L, 30L, "Tienes Alessia?");

        assertEquals(5, result.verifiedToolCalls());
        assertEquals(4, result.media().size());
        assertEquals(68, result.media().get(0).productId());
        assertEquals(68, result.media().get(1).productId());
        assertEquals(77, result.media().get(2).productId());
        assertEquals(77, result.media().get(3).productId());
        assertTrue(result.media().get(1).suppressIfRecentlySent());
        assertTrue(result.media().get(3).suppressIfRecentlySent());
        assertTrue(result.evidenceJson().contains("ambiguousProductOptions"));
        assertTrue(result.evidenceJson().contains("showAllMatchingModelsBeforeAsking"));
        assertTrue(result.evidenceJson().contains("askForModelColorAndSizeAfterMedia"));
        assertTrue(result.evidenceJson().contains("\"verifiedMediaCount\":4"));
    }

    @Test
    void doesNotChooseAColorImageWhenSeveralModelsMatch() {
        AgentDecisionGateway decisions = mock(AgentDecisionGateway.class);
        AgentCatalogQueryService catalog = mock(AgentCatalogQueryService.class);
        AgentConversationDecision decision = new AgentConversationDecision(
                AgentDecisionIntent.PRODUCT_DETAILS, "mostrar Alessia negra", "Alessia", "NEGRO", "", 0, "",
                AgentCartAction.NONE, AgentCommercialAction.SHOW_PRODUCT_IMAGES,
                "", "", "", "", "", "", "", "",
                com.sistemapos.sistematextil.services.agent.ai.AgentCatalogAvailability.ALL, 99, false);
        when(decisions.interpret(4L, "Muestrame Alessia en negro")).thenReturn(
                new AgentInterpretationResult("GEMINI", "model", "DB", 10, 0, decision));
        ProductSummary entero = new ProductSummary(68, "ALESSIA ENTERO", "ARUBA", "CONJUNTO",
                false, null, 75D, 10, List.of("NEGRO"), List.of("XS"));
        ProductSummary rayas = new ProductSummary(77, "ALESSIA RAYAS", "ARUBA", "CONJUNTO",
                false, null, 75D, 8, List.of("NEGRO"), List.of("S"));
        when(catalog.searchProducts(9L, "Alessia", 20,
                com.sistemapos.sistematextil.services.agent.ai.AgentCatalogAvailability.ALL)).thenReturn(
                new ProductSearchResult("Alessia", 1, List.of(entero, rayas), false));
        AgentVerifiedGroundingService service = new AgentVerifiedGroundingService(decisions, catalog,
                mock(AgentPromotionQueryService.class), mock(AgentKnowledgeRetriever.class),
                mock(AgentCartService.class), passThroughAudit());

        var result = service.ground(4L, 9L, 30L, "Muestrame Alessia en negro");

        assertTrue(result.media().isEmpty());
        assertTrue(result.evidenceJson().contains("productSelectionRequiredBeforeColorImage"));
    }

    @Test
    void addsPreviouslySelectedVariantWhenCustomerConfirmsOneUnit() {
        AgentDecisionGateway decisions = mock(AgentDecisionGateway.class);
        AgentCatalogQueryService catalog = mock(AgentCatalogQueryService.class);
        AgentCartService cart = mock(AgentCartService.class);
        AgentConversationDecision decision = new AgentConversationDecision(
                AgentDecisionIntent.CART_CHANGE, "agregar una unidad", "Belen", "AZUL", "XS",
                1, "", AgentCartAction.ADD_PRODUCT, "agregar al carrito", 99, false);
        when(decisions.interpret(4L, "Si deseo 1 unidad por favor")).thenReturn(
                new AgentInterpretationResult("GEMINI", "model", "DB", 10, 0, decision));
        ProductSummary product = new ProductSummary(45, "BELEN", "CATANIA", "CONJUNTO",
                false, null, 75D, 214, List.of("AZUL"), List.of("XS"));
        when(catalog.searchProducts(9L, "Belen", 10)).thenReturn(
                new ProductSearchResult("Belen", 1, List.of(product), false));
        VariantSummary variant = new VariantSummary(4501, "BEL-AZU-XS", "AZUL", "XS",
                75D, 75D, 3, true);
        when(catalog.getVariants(9L, 45, "AZUL", "XS")).thenReturn(
                new VariantResult(45, "BELEN", "AZUL", "XS", List.of(variant)));
        when(catalog.getStock(9L, 4501, 1)).thenReturn(
                new StockResult(4501, 45, "BELEN", "AZUL", "XS", 1, 3, true, 1, true));
        when(catalog.getProductImage(9L, 45, "AZUL")).thenReturn(new ProductImageResult(
                45, "BELEN", "AZUL", "/productos/belen.webp", null,
                List.of(new ProductImage("AZUL", "/productos/belen-azul.webp", null, true))));
        when(catalog.getProduct(9L, 45)).thenReturn(productWithGuide(
                45, "BELEN", "/guias/belen.webp"));
        CartResult cartResult = new CartResult(70L, "DRAFT", "PEN", BigDecimal.valueOf(75),
                BigDecimal.ZERO, BigDecimal.valueOf(75), List.of(), List.of());
        when(cart.addProduct(9L, 4501, 1, "agent-job-30:verified-add-product")).thenReturn(cartResult);
        AgentVerifiedGroundingService service = new AgentVerifiedGroundingService(decisions, catalog,
                mock(AgentPromotionQueryService.class), mock(AgentKnowledgeRetriever.class), cart,
                passThroughAudit());

        var result = service.ground(4L, 9L, 30L, "Si deseo 1 unidad por favor");

        assertTrue(result.mutationExecuted());
        assertEquals(7, result.verifiedToolCalls());
        assertEquals(List.of(
                new com.sistemapos.sistematextil.services.agent.ai.AgentMediaRequest(
                        MediaType.SIZE_GUIDE, 45, null, true),
                new com.sistemapos.sistematextil.services.agent.ai.AgentMediaRequest(
                        MediaType.PRODUCT_IMAGE, 45, "AZUL", true)), result.media());
        assertTrue(result.evidenceJson().contains("\"executed\":true"));
        verify(cart).addProduct(9L, 4501, 1, "agent-job-30:verified-add-product");
    }

    @Test
    void addsAllPreviouslyProposedProductsFromCombinedConfirmationAndShippingMessage() {
        AgentDecisionGateway decisions=mock(AgentDecisionGateway.class);AgentCatalogQueryService catalog=mock(AgentCatalogQueryService.class);AgentCartService cart=mock(AgentCartService.class);
        AgentConversationDecision decision=new AgentConversationDecision(
                AgentDecisionIntent.CART_CHANGE,"confirmar ambos","BELEN","","",0,"",
                List.of(new PromotionSelection("BELEN","TOPO","S",1),new PromotionSelection("BELEN","HUESO","XS",1)),
                AgentCartAction.ADD_PRODUCT,AgentCommercialAction.ADD_PRODUCT,"","","","","SHALOM","","","",List.of(),
                com.sistemapos.sistematextil.services.agent.ai.AgentCatalogAvailability.ALL,"",List.of(),"",100,false);
        when(decisions.interpret(4L,"Si\nEnvio")).thenReturn(new AgentInterpretationResult("GEMINI","model","DB",10,0,decision));
        ProductSummary product=new ProductSummary(45,"BELEN","CATANIA","CONJUNTO",false,null,75D,20,List.of("TOPO","HUESO"),List.of("S","XS"));
        when(catalog.searchProducts(9L,"BELEN",10)).thenReturn(new ProductSearchResult("BELEN",1,List.of(product),false));
        VariantSummary topo=new VariantSummary(1417,"BEL-TOP-S","TOPO","S",75D,75D,9,true);
        VariantSummary hueso=new VariantSummary(1402,"BEL-HUE-XS","HUESO","XS",75D,75D,4,true);
        when(catalog.getVariants(9L,45,"TOPO","S")).thenReturn(new VariantResult(45,"BELEN","TOPO","S",List.of(topo)));
        when(catalog.getVariants(9L,45,"HUESO","XS")).thenReturn(new VariantResult(45,"BELEN","HUESO","XS",List.of(hueso)));
        when(catalog.getStock(9L,1417,1)).thenReturn(new StockResult(1417,45,"BELEN","TOPO","S",1,9,true,1,true));
        when(catalog.getStock(9L,1402,1)).thenReturn(new StockResult(1402,45,"BELEN","HUESO","XS",1,4,true,1,true));
        when(catalog.getProductImage(9L,45,"TOPO")).thenReturn(new ProductImageResult(45,"BELEN","TOPO","/productos/belen.webp",null,List.of(new ProductImage("TOPO","/productos/belen-topo.webp",null,true))));
        when(catalog.getProductImage(9L,45,"HUESO")).thenReturn(new ProductImageResult(45,"BELEN","HUESO","/productos/belen.webp",null,List.of(new ProductImage("HUESO","/productos/belen-hueso.webp",null,true))));
        CartResult first=new CartResult(70L,"DRAFT","PEN",BigDecimal.valueOf(75),BigDecimal.ZERO,BigDecimal.valueOf(75),List.of(),List.of());
        CartResult both=new CartResult(70L,"DRAFT","PEN",BigDecimal.valueOf(150),BigDecimal.ZERO,BigDecimal.valueOf(150),List.of(),List.of());
        when(cart.addProduct(9L,1417,1,"agent-job-30:verified-add-product:0")).thenReturn(first);
        when(cart.addProduct(9L,1402,1,"agent-job-30:verified-add-product:1")).thenReturn(both);
        AgentVerifiedGroundingService service=new AgentVerifiedGroundingService(decisions,catalog,mock(AgentPromotionQueryService.class),mock(AgentKnowledgeRetriever.class),cart,passThroughAudit());

        var result=service.ground(4L,9L,30L,"Si\nEnvio");

        assertTrue(result.mutationExecuted());verify(cart).addProduct(9L,1417,1,"agent-job-30:verified-add-product:0");verify(cart).addProduct(9L,1402,1,"agent-job-30:verified-add-product:1");
        assertEquals(2,result.media().size());
        assertEquals("TOPO",result.media().get(0).color());
        assertEquals("HUESO",result.media().get(1).color());
    }

    @Test
    void listsReadyStockWithAnEmptyCatalogQueryAndTheSemanticFilter() {
        AgentDecisionGateway decisions = mock(AgentDecisionGateway.class);
        AgentCatalogQueryService catalog = mock(AgentCatalogQueryService.class);
        AgentConversationDecision decision = new AgentConversationDecision(
                AgentDecisionIntent.PRODUCT_SEARCH, "listar productos que no son preventa", "", "", "", 0, "",
                AgentCartAction.NONE, AgentCommercialAction.SHOW_PRODUCTS,
                "", "", "", "", "", "", "", "",
                com.sistemapos.sistematextil.services.agent.ai.AgentCatalogAvailability.READY_STOCK_ONLY,
                99, false);
        when(decisions.interpret(4L, "quiero todos los productos que no son preventa")).thenReturn(
                new AgentInterpretationResult("GEMINI", "model", "DB", 10, 0, decision));
        ProductSummary alice = new ProductSummary(10, "ALICE RAYAS", "ARUBA", "CONJUNTO",
                false, null, 95D, 6, List.of("PERLA"), List.of("XS", "M"));
        ProductSummary emma = new ProductSummary(20, "EMMA", "ARUBA", "CONJUNTO",
                false, null, 65D, 3, List.of("CAMEL"), List.of("S"));
        ProductSummary lia = new ProductSummary(30, "LIA RAYAS", "ARUBA", "CONJUNTO",
                false, null, 75D, 4, List.of("NEGRO"), List.of("M"));
        ProductSummary lyana = new ProductSummary(40, "LYANA", "ARUBA", "CONJUNTO",
                false, null, 70D, 5, List.of("TOPO"), List.of("L"));
        when(catalog.searchProducts(9L, "", 20,
                com.sistemapos.sistematextil.services.agent.ai.AgentCatalogAvailability.READY_STOCK_ONLY))
                .thenReturn(new ProductSearchResult("", 7,
                        com.sistemapos.sistematextil.services.agent.ai.AgentCatalogAvailability.READY_STOCK_ONLY,
                        List.of(alice, emma, lia, lyana), false));
        when(catalog.getProductImage(9L, 10, "")).thenReturn(new ProductImageResult(
                10, "ALICE RAYAS", "", "/productos/alice-rayas.webp", null, List.of()));
        when(catalog.getProductImage(9L, 20, "")).thenReturn(new ProductImageResult(
                20, "EMMA", "", "/productos/emma.webp", null, List.of()));
        when(catalog.getProductImage(9L, 30, "")).thenReturn(new ProductImageResult(
                30, "LIA RAYAS", "", "/productos/lia-rayas.webp", null, List.of()));
        when(catalog.getProductImage(9L, 40, "")).thenReturn(new ProductImageResult(
                40, "LYANA", "", "/productos/lyana.webp", null, List.of()));
        AgentVerifiedGroundingService service = new AgentVerifiedGroundingService(decisions, catalog,
                mock(AgentPromotionQueryService.class), mock(AgentKnowledgeRetriever.class),
                mock(AgentCartService.class), passThroughAudit());

        var result = service.ground(4L, 9L, 30L, "quiero todos los productos que no son preventa");

        assertEquals(5, result.verifiedToolCalls());
        assertEquals(null, result.customerMessage());
        assertEquals(4, result.media().size());
        assertEquals(4, result.media().stream().map(
                com.sistemapos.sistematextil.services.agent.ai.AgentMediaRequest::productId).distinct().count());
        assertTrue(result.media().stream().allMatch(
                com.sistemapos.sistematextil.services.agent.ai.AgentMediaRequest::catalogPreview));
        assertTrue(result.evidenceJson().contains("READY_STOCK_ONLY"));
        verify(catalog).searchProducts(9L, "", 20,
                com.sistemapos.sistematextil.services.agent.ai.AgentCatalogAvailability.READY_STOCK_ONLY);
    }

    @Test
    void initialCatalogPreviewQueuesAtMostEightProductsInCatalogOrder() {
        AgentDecisionGateway decisions = mock(AgentDecisionGateway.class);
        AgentCatalogQueryService catalog = mock(AgentCatalogQueryService.class);
        AgentConversationDecision decision = new AgentConversationDecision(
                AgentDecisionIntent.PRODUCT_SEARCH, "mostrar catálogo", "", "", "", 0, "",
                AgentCartAction.NONE, AgentCommercialAction.SHOW_PRODUCTS,
                "", "", "", "", "", "", "", "",
                com.sistemapos.sistematextil.services.agent.ai.AgentCatalogAvailability.ALL,
                99, false);
        when(decisions.interpret(4L, "catálogo por favor")).thenReturn(
                new AgentInterpretationResult("GEMINI", "model", "DB", 10, 0, decision));
        List<ProductSummary> newestFirst = java.util.stream.IntStream.rangeClosed(1, 9)
                .mapToObj(index -> new ProductSummary(100 + index, "MODELO " + index,
                        "ARUBA", "CONJUNTO", false, null, 75D, 2,
                        List.of("NEGRO"), List.of("S")))
                .toList();
        when(catalog.searchProducts(9L, "", 20,
                com.sistemapos.sistematextil.services.agent.ai.AgentCatalogAvailability.ALL))
                .thenReturn(new ProductSearchResult("", 7,
                        com.sistemapos.sistematextil.services.agent.ai.AgentCatalogAvailability.ALL,
                        newestFirst, false));
        for (ProductSummary product : newestFirst) {
            when(catalog.getProductImage(9L, product.productId(), "")).thenReturn(new ProductImageResult(
                    product.productId(), product.name(), "",
                    "/productos/modelo-" + product.productId() + ".webp", null, List.of()));
        }
        AgentVerifiedGroundingService service = new AgentVerifiedGroundingService(decisions, catalog,
                mock(AgentPromotionQueryService.class), mock(AgentKnowledgeRetriever.class),
                mock(AgentCartService.class), passThroughAudit());

        var result = service.ground(4L, 9L, 30L, "catálogo por favor");

        assertEquals(8, result.media().size());
        assertEquals(java.util.stream.IntStream.rangeClosed(101, 108).boxed().toList(),
                result.media().stream().map(
                        com.sistemapos.sistematextil.services.agent.ai.AgentMediaRequest::productId).toList());
        assertTrue(result.media().stream().allMatch(
                com.sistemapos.sistematextil.services.agent.ai.AgentMediaRequest::catalogPreview));
        org.mockito.Mockito.verify(catalog, org.mockito.Mockito.never()).getProductImage(9L, 109, "");
    }

    @Test
    void removesTheReferencedCartItemWithoutAskingAgain() {
        AgentDecisionGateway decisions = mock(AgentDecisionGateway.class);
        AgentCatalogQueryService catalog = mock(AgentCatalogQueryService.class);
        AgentCartService cart = mock(AgentCartService.class);
        AgentConversationDecision decision = new AgentConversationDecision(
                AgentDecisionIntent.CART_CHANGE, "quitar Belen", "Belen", "", "", 0, "",
                AgentCartAction.REMOVE_PRODUCT, AgentCommercialAction.CHANGE_CART,
                "", "", "", "", "", "", "", "",
                com.sistemapos.sistematextil.services.agent.ai.AgentCatalogAvailability.ALL, 99, false);
        when(decisions.interpret(4L, "Quita Belen de mi pedido")).thenReturn(
                new AgentInterpretationResult("GEMINI", "model", "DB", 10, 0, decision));
        CartItemView item = new CartItemView(8L, 4501, "BELEN", "AZUL", "XS", 1,
                BigDecimal.valueOf(75), BigDecimal.ZERO, BigDecimal.valueOf(75));
        CartResult current = new CartResult(70L, "DRAFT", "PEN", BigDecimal.valueOf(75),
                BigDecimal.ZERO, BigDecimal.valueOf(75), List.of(item), List.of());
        CartResult empty = new CartResult(70L, "DRAFT", "PEN", BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, List.of(), List.of());
        when(cart.recalculate(9L, "agent-job-30:read-cart")).thenReturn(current);
        when(cart.removeProduct(9L, 4501, "agent-job-30:remove_product")).thenReturn(empty);
        AgentVerifiedGroundingService service = new AgentVerifiedGroundingService(decisions, catalog,
                mock(AgentPromotionQueryService.class), mock(AgentKnowledgeRetriever.class), cart,
                passThroughAudit());

        var result = service.ground(4L, 9L, 30L, "Quita Belen de mi pedido");

        assertTrue(result.mutationExecuted());
        assertTrue(result.customerMessage().contains("carrito quedó vacío"));
        verify(cart).removeProduct(9L, 4501, "agent-job-30:remove_product");
    }

    @Test
    void keepsOnlyTheRequestedCartProductInsteadOfChangingOnlyItsQuantity() {
        AgentDecisionGateway decisions = mock(AgentDecisionGateway.class);
        AgentCatalogQueryService catalog = mock(AgentCatalogQueryService.class);
        AgentCartService cart = mock(AgentCartService.class);
        AgentConversationDecision decision = new AgentConversationDecision(
                AgentDecisionIntent.CART_CHANGE, "dejar solo una Alice", "ALICE RAYAS", "", "", 1, "",
                AgentCartAction.KEEP_ONLY_PRODUCT, AgentCommercialAction.CHANGE_CART,
                "", "", "", "", "", "", "", "",
                com.sistemapos.sistematextil.services.agent.ai.AgentCatalogAvailability.ALL, 99, false);
        when(decisions.interpret(4L, "Quiero solo 1 Alice a rayas")).thenReturn(
                new AgentInterpretationResult("GEMINI", "model", "DB", 10, 0, decision));
        CartItemView alessia = new CartItemView(8L, 4401, "ALESSIA ENTERO", "GUINDA", "M", 1,
                BigDecimal.valueOf(75), BigDecimal.ZERO, BigDecimal.valueOf(75));
        CartItemView alice = new CartItemView(9L, 4501, "ALICE RAYAS", "CHOCOLATE", "M", 2,
                BigDecimal.valueOf(90), BigDecimal.ZERO, BigDecimal.valueOf(180));
        CartResult current = new CartResult(70L, "DRAFT", "PEN", BigDecimal.valueOf(255),
                BigDecimal.valueOf(100), BigDecimal.valueOf(155), List.of(alessia, alice),
                List.of(new CartPromotionView(29, "COMBO 29", 1, BigDecimal.valueOf(255),
                        BigDecimal.valueOf(155), BigDecimal.valueOf(100))));
        CartItemView onlyAlice = new CartItemView(9L, 4501, "ALICE RAYAS", "CHOCOLATE", "M", 1,
                BigDecimal.valueOf(90), BigDecimal.ZERO, BigDecimal.valueOf(90));
        CartResult result = new CartResult(70L, "DRAFT", "PEN", BigDecimal.valueOf(90),
                BigDecimal.ZERO, BigDecimal.valueOf(90), List.of(onlyAlice), List.of());
        when(cart.recalculate(9L, "agent-job-30:read-cart")).thenReturn(current);
        when(cart.keepOnlyProduct(9L, 4501, 1, "agent-job-30:keep_only_product")).thenReturn(result);
        AgentVerifiedGroundingService service = new AgentVerifiedGroundingService(decisions, catalog,
                mock(AgentPromotionQueryService.class), mock(AgentKnowledgeRetriever.class), cart,
                passThroughAudit());

        var grounded = service.ground(4L, 9L, 30L, "Quiero solo 1 Alice a rayas");

        assertTrue(grounded.mutationExecuted());
        verify(cart).keepOnlyProduct(9L, 4501, 1, "agent-job-30:keep_only_product");
    }

    @Test
    void replacesTheOnlyCartProductWithAnotherModelWithoutHumanEscalation() {
        AgentDecisionGateway decisions = mock(AgentDecisionGateway.class);
        AgentCatalogQueryService catalog = mock(AgentCatalogQueryService.class);
        AgentCartService cart = mock(AgentCartService.class);
        AgentConversationDecision decision = new AgentConversationDecision(
                AgentDecisionIntent.CART_CHANGE, "reemplazar Anguie Rayas por Anguie Liso",
                "ANGUIE LISO", List.of("ANGUIE LISO"), "GRIS OSCURO", "M", 1, "", List.of(),
                AgentCartAction.REPLACE_PRODUCT, AgentCommercialAction.CHANGE_CART,
                "", "", "", "", "", "", "", "", List.of(),
                com.sistemapos.sistematextil.services.agent.ai.AgentCatalogAvailability.ALL,
                "", List.of(), "", 99, false);
        when(decisions.interpret(4L, "Sí, cámbialo por Anguie Liso gris oscuro talla M")).thenReturn(
                new AgentInterpretationResult("GEMINI", "model", "DB", 10, 0, decision));
        CartItemView currentItem = new CartItemView(8L, 4501, "ANGUIE RAYAS", "GRIS OSCURO", "M", 1,
                BigDecimal.valueOf(75), BigDecimal.ZERO, BigDecimal.valueOf(75));
        CartItemView replacementItem = new CartItemView(9L, 4601, "ANGUIE LISO", "GRIS OSCURO", "M", 1,
                BigDecimal.valueOf(75), BigDecimal.ZERO, BigDecimal.valueOf(75));
        CartResult current = new CartResult(70L, "DRAFT", "PEN", BigDecimal.valueOf(75),
                BigDecimal.ZERO, BigDecimal.valueOf(75), List.of(currentItem), List.of());
        CartResult replaced = new CartResult(70L, "DRAFT", "PEN", BigDecimal.valueOf(75),
                BigDecimal.ZERO, BigDecimal.valueOf(75), List.of(replacementItem), List.of());
        ProductSummary product = new ProductSummary(79, "ANGUIE LISO", "", "CONJUNTO",
                true, LocalDate.of(2026, 10, 24), 75D, 13, List.of("GRIS OSCURO"), List.of("M"));
        when(cart.recalculate(9L, "agent-job-30:read-cart")).thenReturn(current);
        when(catalog.getStock(9L, 4501, 1)).thenReturn(new StockResult(
                4501, 78, "ANGUIE RAYAS", "GRIS OSCURO", "M", 7, 5, true, 1, true));
        when(catalog.searchProducts(9L, "ANGUIE LISO", 10)).thenReturn(
                new ProductSearchResult("ANGUIE LISO", 7, List.of(product), false));
        when(catalog.getVariants(9L, 79, "GRIS OSCURO", "M")).thenReturn(new VariantResult(
                79, "ANGUIE LISO", "GRIS OSCURO", "M",
                List.of(availableVariant(4601, "GRIS OSCURO", "M", 75D, 13))));
        when(cart.replaceProduct(9L, 4501, 4601, "agent-job-30:replace_product")).thenReturn(replaced);
        AgentVerifiedGroundingService service = new AgentVerifiedGroundingService(decisions, catalog,
                mock(AgentPromotionQueryService.class), mock(AgentKnowledgeRetriever.class), cart,
                passThroughAudit());

        var result = service.ground(4L, 9L, 30L,
                "Sí, cámbialo por Anguie Liso gris oscuro talla M");

        assertTrue(result.mutationExecuted());
        assertFalse(result.decision().requiresHumanHelp());
        verify(cart).replaceProduct(9L, 4501, 4601, "agent-job-30:replace_product");
    }

    private ProductDetail productWithGuide(Integer id, String name, String guideUrl) {
        return new ProductDetail(id, name, "", "CONJUNTO", false, null, guideUrl, null,
                List.of(), List.of("S"), 10, 75D, 1);
    }

    private ProductSummary productWithAvailability(Integer id, String name, boolean preorder,
            List<ColorAvailability> availability) {
        List<String> colors = availability.stream().map(ColorAvailability::color).distinct().toList();
        List<String> sizes = availability.stream().flatMap(item -> item.sizes().stream()).distinct().toList();
        int total = availability.stream().mapToInt(ColorAvailability::totalStock).sum();
        return new ProductSummary(id, name, "", "CONJUNTO", preorder,
                preorder ? LocalDate.of(2026, 10, 26) : null, 75D, total,
                colors, sizes, availability, "https://kiments.com.pe/productos/" + name.toLowerCase());
    }

    private VariantSummary availableVariant(
            Integer id, String color, String size, Double price, int stock) {
        return new VariantSummary(id, "SKU-" + id, color, size, price, price, stock, stock > 0);
    }

    private AgentToolCallAuditService passThroughAudit() {
        AgentToolCallAuditService audit = mock(AgentToolCallAuditService.class);
        when(audit.execute(anyLong(), anyLong(), anyInt(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> invocation.<java.util.function.Supplier<String>>getArgument(5).get());
        return audit;
    }
}
