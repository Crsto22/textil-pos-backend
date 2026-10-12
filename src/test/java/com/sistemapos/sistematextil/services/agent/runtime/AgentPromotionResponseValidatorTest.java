package com.sistemapos.sistematextil.services.agent.runtime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AgentPromotionResponseValidatorTest {
    private final AgentPromotionResponseValidator validator = new AgentPromotionResponseValidator();

    @Test
    void rejectsAFalseNoPromotionsAnswerWhenMysqlReturnedResults() {
        String evidence = """
                {"promotions":{"promotions":[
                  {"name":"combo 30","comboPrice":125.00},
                  {"name":"combo 49","comboPrice":145.00}
                ]}}
                """;

        assertFalse(validator.validate(evidence,
                "Por el momento no contamos con promociones vigentes.").valid());
    }

    @Test
    void acceptsNaturalWordingGroundedInARealPromotionAndPrice() {
        String evidence = """
                {"promotions":{"promotions":[
                  {"name":"combo 30","comboPrice":125.00}
                ]}}
                """;

        assertTrue(validator.validate(evidence,
                "Sí, bella: tenemos el combo 30 por S/125.00. Incluye Lyana y Emma.").valid());
    }

    @Test
    void allowsNoPromotionsOnlyWhenTheVerifiedListIsEmpty() {
        String evidence = "{\"promotions\":{\"promotions\":[]}}";

        assertTrue(validator.validate(evidence,
                "Ahora mismo no tenemos promociones vigentes.").valid());
    }

    @Test
    void doesNotConfuseComboThreeWithComboThirty() {
        String evidence = """
                {"promotions":{"promotions":[
                  {"name":"combo 3","comboPrice":125.00}
                ]}}
                """;

        assertFalse(validator.validate(evidence,
                "Tenemos el combo 30 por S/125.00.").valid());
    }
}
