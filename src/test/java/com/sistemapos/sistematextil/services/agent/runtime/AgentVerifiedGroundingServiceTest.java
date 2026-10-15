package com.sistemapos.sistematextil.services.agent.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import com.sistemapos.sistematextil.services.agent.ai.AgentConversationDecision;
import com.sistemapos.sistematextil.services.agent.ai.AgentConversationDecision.PromotionSelection;
import com.sistemapos.sistematextil.services.agent.ai.AgentCartAction;
import com.sistemapos.sistematextil.services.agent.ai.AgentCommercialAction;
import com.sistemapos.sistematextil.services.agent.ai.AgentDecisionGateway;
import com.sistemapos.sistematextil.services.agent.ai.AgentDecisionIntent;
import com.sistemapos.sistematextil.services.agent.ai.AgentInterpretationResult;
import com.sistemapos.sistematextil.services.agent.ai.AgentMediaRequest.MediaType;
import com.sistemapos.sistematextil.services.agent.catalog.AgentCatalogQueryService;
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
import com.sistemapos.sistematextil.services.agent.knowledge.AgentKnowledgeRetriever;
import com.sistemapos.sistematextil.services.agent.promotion.AgentPromotionQueryService;
import com.sistemapos.sistematextil.services.agent.promotion.AgentPromotionQueryService.PromotionDetail;
import com.sistemapos.sistematextil.services.agent.sizeguide.ProductSizeGuideQueryService;
import com.sistemapos.sistematextil.services.agent.promotion.AgentPromotionQueryService.PromotionItem;
import com.sistemapos.sistematextil.services.agent.promotion.AgentPromotionQueryService.PromotionSearchResult;
import com.sistemapos.sistematextil.services.agent.promotion.AgentPromotionQueryService.PromotionSummary;

class AgentVerifiedGroundingServiceTest {
    @Test
    void wholesaleActionAlwaysRequiresHumanHelpWithoutRunningCommerceTools() {
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

        assertTrue(grounded.decision().requiresHumanHelp());
        assertEquals(0, grounded.verifiedToolCalls());
        org.mockito.Mockito.verifyNoInteractions(catalog, promotions, knowledge, cart);

        AgentConversationDecision ordinaryMultiItem = wholesale
                .withCommercialAction(AgentCommercialAction.ADD_PRODUCT)
                .withRequiresHumanHelp(false);
        assertFalse(service.enforceHumanHandoffRules(ordinaryMultiItem).requiresHumanHelp());
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

        assertEquals("Para completar *combo 49*, indícame el color y la talla de *ALICE RAYAS*, *EMMA*.",
                grounded.customerMessage());
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
    void completesMissingComboSelectionFromTheExistingCart() {
        AgentDecisionGateway decisions = mock(AgentDecisionGateway.class);
        AgentCatalogQueryService catalog = mock(AgentCatalogQueryService.class);
        AgentPromotionQueryService promotions = mock(AgentPromotionQueryService.class);
        AgentCartService cart = mock(AgentCartService.class);
        AgentConversationDecision decision = new AgentConversationDecision(
                AgentDecisionIntent.CART_CHANGE, "completar combo 30", "LYANA", "AZUL", "L", 1,
                "combo 30", List.of(new PromotionSelection("LYANA", "AZUL", "L", 1)),
                AgentCartAction.ADD_PROMOTION, AgentCommercialAction.CHANGE_CART,
                "", "", "", "", "", "", "", "",
                com.sistemapos.sistematextil.services.agent.ai.AgentCatalogAvailability.ALL, 99, false);
        when(decisions.interpret(4L, "azul talla L")).thenReturn(
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
        CartItemView emma = new CartItemView(1L, 1535, "EMMA", "Negro", "L", 1,
                BigDecimal.valueOf(65), BigDecimal.ZERO, BigDecimal.valueOf(65));
        when(cart.recalculate(9L, "agent-job-30:promotion-cart-context")).thenReturn(
                new CartResult(70L, "DRAFT", "PEN", BigDecimal.valueOf(65), BigDecimal.ZERO,
                        BigDecimal.valueOf(65), List.of(emma), List.of()));
        when(catalog.getVariants(9L, 59, "Negro", "L")).thenReturn(new VariantResult(59, "EMMA",
                "Negro", "L", List.of(new VariantSummary(1535, "EM-NEG-L", "Negro", "L",
                        65D, 65D, 10, true))));
        when(catalog.getVariants(9L, 60, "AZUL", "L")).thenReturn(new VariantResult(60, "LYANA",
                "AZUL", "L", List.of(new VariantSummary(1599, "LY-AZU-L", "AZUL", "L",
                        67D, 67D, 10, true))));
        when(catalog.getStock(9L, 1535, 1)).thenReturn(
                new StockResult(1535, 59, "EMMA", "Negro", "L", 1, 10, true, 1, true));
        when(catalog.getStock(9L, 1599, 1)).thenReturn(
                new StockResult(1599, 60, "LYANA", "AZUL", "L", 1, 10, true, 1, true));
        CartResult result = new CartResult(70L, "DRAFT", "PEN", BigDecimal.valueOf(132),
                BigDecimal.valueOf(7), BigDecimal.valueOf(125), List.of(emma),
                List.of(new CartPromotionView(56, "combo 30", 1, BigDecimal.valueOf(132),
                        BigDecimal.valueOf(125), BigDecimal.valueOf(7))));
        when(cart.addCombo(9L, 56, List.of(new ComboSelection(1535, 1), new ComboSelection(1599, 1)),
                "agent-job-30:verified-add-promotion")).thenReturn(result);

        AgentVerifiedGroundingService service = new AgentVerifiedGroundingService(decisions, catalog,
                promotions, mock(AgentKnowledgeRetriever.class), cart, passThroughAudit());
        var grounded = service.ground(4L, 9L, 30L, "azul talla L");

        assertTrue(grounded.mutationExecuted());
        verify(cart).addCombo(9L, 56,
                List.of(new ComboSelection(1535, 1), new ComboSelection(1599, 1)),
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

        assertEquals("Para completar *combo 30*, indícame el color y la talla de *EMMA*, *LYANA*.",
                grounded.customerMessage());
        assertTrue(!grounded.mutationExecuted());
        org.mockito.Mockito.verify(cart, org.mockito.Mockito.never())
                .addCombo(anyLong(), anyInt(), any(), anyString());
    }

    @Test
    void persistsAValidPartialComboChoiceBeforeAskingForTheOtherProduct() {
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
        when(cart.recalculate(9L, "agent-job-30:promotion-cart-context")).thenReturn(
                new CartResult(70L, "DRAFT", "PEN", BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, List.of(), List.of()));
        when(catalog.getVariants(9L, 59, "Negro", "XS")).thenReturn(new VariantResult(59, "EMMA",
                "Negro", "XS", List.of(new VariantSummary(1538, "EM-NEG-XS", "Negro", "XS",
                        65D, 65D, 10, true))));
        when(catalog.getStock(9L, 1538, 1)).thenReturn(
                new StockResult(1538, 59, "EMMA", "Negro", "XS", 1, 10, true, 1, true));
        CartItemView emma = new CartItemView(1L, 1538, "EMMA", "Negro", "XS", 1,
                BigDecimal.valueOf(65), BigDecimal.ZERO, BigDecimal.valueOf(65));
        CartResult partial = new CartResult(70L, "DRAFT", "PEN", BigDecimal.valueOf(65),
                BigDecimal.ZERO, BigDecimal.valueOf(65), List.of(emma), List.of());
        when(cart.addProduct(9L, 1538, 1, "agent-job-30:promotion-selection:1538"))
                .thenReturn(partial);

        AgentVerifiedGroundingService service = new AgentVerifiedGroundingService(decisions, catalog,
                promotions, mock(AgentKnowledgeRetriever.class), cart, passThroughAudit());
        var grounded = service.ground(4L, 9L, 30L, "quiero negro xs");

        assertEquals("Para completar *combo 30*, indícame el color y la talla de *LYANA*.",
                grounded.customerMessage());
        verify(cart).addProduct(9L, 1538, 1, "agent-job-30:promotion-selection:1538");
        assertTrue(grounded.evidenceJson().contains("partialPromotionSelectionsPersisted"));
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
            context.registerBean(AgentToolCallAuditService.class, () -> mock(AgentToolCallAuditService.class));
            context.register(AgentVerifiedGroundingService.class);
            context.refresh();

            assertNotNull(context.getBean(AgentVerifiedGroundingService.class));
        }
    }

    @Test
    void groundsAnExactProductAndAddsOnlyTheVerifiedProductImage() {
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
        when(catalog.getProductImage(9L, 20, "rosa")).thenReturn(new ProductImageResult(20,
                "ANNIE RAYAS", "rosa", "/productos/annie.webp", null,
                List.of(new ProductImage("ROSA", "/productos/annie-rosa.webp", null, true))));

        AgentVerifiedGroundingService service = new AgentVerifiedGroundingService(decisions, catalog,
                mock(AgentPromotionQueryService.class), mock(AgentKnowledgeRetriever.class),
                mock(com.sistemapos.sistematextil.services.agent.cart.AgentCartService.class), audit);
        var result = service.ground(4L, 9L, 30L, "Annie rayas color rosa");

        assertEquals(3, result.verifiedToolCalls());
        assertEquals(1, result.media().size());
        assertEquals(MediaType.PRODUCT_IMAGE, result.media().getFirst().type());
        assertEquals(null, result.customerMessage());
        assertTrue(result.evidenceJson().contains("ANNIE RAYAS"));
        assertTrue(result.evidenceJson().contains("annie-rosa.webp"));
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
        assertTrue(result.media().isEmpty());
    }

    @Test
    void addsGlobalImagesForUpToThreeAmbiguousProductsWhenCustomerRequestsPhotos() {
        AgentDecisionGateway decisions = mock(AgentDecisionGateway.class);
        AgentCatalogQueryService catalog = mock(AgentCatalogQueryService.class);
        AgentConversationDecision decision = new AgentConversationDecision(
                AgentDecisionIntent.PRODUCT_DETAILS, "mostrar fotos de Alessia", "Alessia", "", "", 0, "",
                AgentCartAction.NONE, AgentCommercialAction.SHOW_PRODUCT_IMAGES,
                "", "", "", "", "", "", "", "",
                com.sistemapos.sistematextil.services.agent.ai.AgentCatalogAvailability.ALL, 99, false);
        when(decisions.interpret(4L, "Si, pasame fotos de Alessia")).thenReturn(
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
        AgentVerifiedGroundingService service = new AgentVerifiedGroundingService(decisions, catalog,
                mock(AgentPromotionQueryService.class), mock(AgentKnowledgeRetriever.class),
                mock(AgentCartService.class), passThroughAudit());

        var result = service.ground(4L, 9L, 30L, "Si, pasame fotos de Alessia");

        assertEquals(3, result.verifiedToolCalls());
        assertEquals(2, result.media().size());
        assertEquals(68, result.media().get(0).productId());
        assertEquals(77, result.media().get(1).productId());
        assertTrue(result.evidenceJson().contains("matchingProductImages"));
        assertTrue(result.evidenceJson().contains("\"verifiedMediaCount\":2"));
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
        CartResult cartResult = new CartResult(70L, "DRAFT", "PEN", BigDecimal.valueOf(75),
                BigDecimal.ZERO, BigDecimal.valueOf(75), List.of(), List.of());
        when(cart.addProduct(9L, 4501, 1, "agent-job-30:verified-add-product")).thenReturn(cartResult);
        AgentVerifiedGroundingService service = new AgentVerifiedGroundingService(decisions, catalog,
                mock(AgentPromotionQueryService.class), mock(AgentKnowledgeRetriever.class), cart,
                passThroughAudit());

        var result = service.ground(4L, 9L, 30L, "Si deseo 1 unidad por favor");

        assertTrue(result.mutationExecuted());
        assertEquals(5, result.verifiedToolCalls());
        assertEquals(List.of(new com.sistemapos.sistematextil.services.agent.ai.AgentMediaRequest(
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

        assertEquals(4, result.verifiedToolCalls());
        assertEquals(null, result.customerMessage());
        assertEquals(3, result.media().size());
        assertEquals(3, result.media().stream().map(
                com.sistemapos.sistematextil.services.agent.ai.AgentMediaRequest::productId).distinct().count());
        assertTrue(result.media().stream().allMatch(
                com.sistemapos.sistematextil.services.agent.ai.AgentMediaRequest::catalogPreview));
        assertTrue(result.evidenceJson().contains("READY_STOCK_ONLY"));
        verify(catalog).searchProducts(9L, "", 20,
                com.sistemapos.sistematextil.services.agent.ai.AgentCatalogAvailability.READY_STOCK_ONLY);
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

    private AgentToolCallAuditService passThroughAudit() {
        AgentToolCallAuditService audit = mock(AgentToolCallAuditService.class);
        when(audit.execute(anyLong(), anyLong(), anyInt(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> invocation.<java.util.function.Supplier<String>>getArgument(5).get());
        return audit;
    }
}
