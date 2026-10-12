package com.sistemapos.sistematextil.services.agent.runtime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AgentProductPriceResponseValidatorTest {
    private final AgentProductPriceResponseValidator validator = new AgentProductPriceResponseValidator();

    @Test
    void requiresTheSelectedProductAndItsVerifiedPrice() {
        String evidence = """
                {"selectedProduct":{"productId":7,"name":"BELEN","priceFrom":75.0}}
                """;

        var missing = validator.validate(evidence,
                "Belen esta disponible en tallas XS, S, M y L. ¿Cual deseas?");
        var correct = validator.validate(evidence,
                "Belen esta disponible a S/75.00 en tallas XS, S, M y L. ¿Cual deseas?");

        assertFalse(missing.valid());
        assertTrue(missing.correctionInstruction().contains("S/75.00"));
        assertTrue(correct.valid());
    }

    @Test
    void requiresARealPriceForEveryCatalogProductThatGeminiMentions() {
        String evidence = """
                {"productSearch":{"products":[
                  {"productId":1,"name":"EMMA","priceFrom":65.0},
                  {"productId":2,"name":"LYANA","priceFrom":67.0}
                ]}}
                """;

        var missingLyana = validator.validate(evidence,
                "Tenemos Emma a S/65 y tambien Lyana disponible.");
        var complete = validator.validate(evidence,
                "Tenemos Emma a S/65 y Lyana a 67 soles.");

        assertFalse(missingLyana.valid());
        assertTrue(missingLyana.correctionInstruction().contains("LYANA"));
        assertTrue(complete.valid());
    }

    @Test
    void doesNotForceCatalogProductsThatWereNotShown() {
        String evidence = """
                {"productSearch":{"products":[
                  {"productId":1,"name":"EMMA","priceFrom":65.0},
                  {"productId":2,"name":"LYANA","priceFrom":67.0}
                ]}}
                """;

        assertTrue(validator.validate(evidence,
                "Tenemos Emma a S/65. ¿Deseas conocer sus colores?").valid());
    }
}
