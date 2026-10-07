package com.sistemapos.sistematextil.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class CrmWhatsappEcommerceOrderParserTest {
    private final CrmWhatsappEcommerceOrderParser parser = new CrmWhatsappEcommerceOrderParser();

    @Test
    void parsesCartWithSeveralProductsAndCompoundNames() {
        var order = parser.parse("""
                Hola KIMENTS, quiero comprar por WhatsApp:

                1. CIELO
                Color: TOPO | Talla: L
                Cantidad: 1 x S/ 70.00 = S/ 70.00

                2. EMMA
                Color: Negro | Talla: L
                Cantidad: 1 x S/ 65.00 = S/ 65.00

                3. MIRANDA - RAYAS
                Color: VINO | Talla: L
                Cantidad: 1 x S/ 70.00 = S/ 70.00

                Subtotal: S/ 205.00
                Total: S/ 205.00
                """);

        assertTrue(order.recognized());
        assertEquals(3, order.items().size());
        assertEquals("MIRANDA - RAYAS", order.items().get(2).productName());
        assertEquals("VINO", order.items().get(2).color());
        assertEquals(1, order.items().get(2).quantity());
        assertEquals(new BigDecimal("205.00"), order.informedTotal());
    }

    @Test
    void parsesSingleProductWithCompoundColor() {
        var order = parser.parse("""
                Hola, quiero comprar EMMA
                Color: GRIS OSCURO
                Talla: L
                Cantidad: 3
                """);

        assertTrue(order.recognized());
        assertEquals(1, order.items().size());
        assertEquals("EMMA", order.items().getFirst().productName());
        assertEquals("GRIS OSCURO", order.items().getFirst().color());
        assertEquals("L", order.items().getFirst().size());
        assertEquals(3, order.items().getFirst().quantity());
    }

    @Test
    void parsesCompactOrderWithExplicitColor() {
        var order = parser.parse(
                "Hola, quiero comprar ALESSIA RAYAS Color: AZUL talla L Cantidad: 1");

        assertTrue(order.recognized());
        assertEquals("ALESSIA RAYAS", order.items().getFirst().productName());
        assertEquals("AZUL", order.items().getFirst().color());
        assertEquals("L", order.items().getFirst().size());
        assertEquals(1, order.items().getFirst().quantity());
    }

    @Test
    void parsesCompactOrderAndLeavesImplicitColorForCatalogResolution() {
        var order = parser.parse("alessia rayas azul talla l cantidad 1");

        assertTrue(order.recognized());
        assertEquals("alessia rayas azul", order.items().getFirst().productName());
        assertEquals("", order.items().getFirst().color());
        assertEquals("l", order.items().getFirst().size());
        assertEquals(1, order.items().getFirst().quantity());
    }

    @Test
    void parsesOneProductWithTwoSizesAsTwoCartLines() {
        var order = parser.parse("Agrega EMMA chocolate talla S y talla XS");

        assertTrue(order.recognized());
        assertEquals(2, order.items().size());
        assertEquals("EMMA chocolate", order.items().getFirst().productName());
        assertEquals("S", order.items().getFirst().size());
        assertEquals("XS", order.items().get(1).size());
        assertEquals(1, order.items().getFirst().quantity());
        assertEquals(1, order.items().get(1).quantity());
    }

    @Test
    void ignoresOrdinaryWhatsappMessages() {
        assertFalse(parser.parse("Hola, tienes vestidos disponibles?").recognized());
    }
}
