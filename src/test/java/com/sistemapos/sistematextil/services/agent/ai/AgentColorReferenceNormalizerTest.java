package com.sistemapos.sistematextil.services.agent.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

class AgentColorReferenceNormalizerTest {

    @Test
    void canonicalizesPeruvianMorochoColorAsBlack() {
        AgentConversationDecision decision = decision(" morocho ", List.of());

        assertEquals("NEGRO", decision.colorReference());
    }

    @Test
    void canonicalizesMorochoInsidePromotionSelections() {
        AgentConversationDecision decision = decision("", List.of(
                new AgentConversationDecision.PromotionSelection("MELINA", "MOROCHO", "M", 1)));

        assertEquals("NEGRO", decision.promotionSelections().getFirst().colorReference());
    }

    @Test
    void preservesOtherCatalogColors() {
        AgentConversationDecision decision = decision("CHOCOLATE", List.of());

        assertEquals("CHOCOLATE", decision.colorReference());
    }

    private AgentConversationDecision decision(String color,
            List<AgentConversationDecision.PromotionSelection> selections) {
        return new AgentConversationDecision(
                AgentDecisionIntent.CART_CHANGE, "comprar", "MELINA", color, "M", 1, "",
                selections, AgentCartAction.ADD_PRODUCT, AgentCommercialAction.ADD_PRODUCT,
                "", "", "", "", "", "", "", "", AgentCatalogAvailability.ALL,
                100, false);
    }
}
