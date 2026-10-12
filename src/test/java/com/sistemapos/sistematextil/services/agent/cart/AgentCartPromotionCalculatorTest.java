package com.sistemapos.sistematextil.services.agent.cart;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.sistemapos.sistematextil.model.EcommercePromocionCombo;
import com.sistemapos.sistematextil.model.EcommercePromocionComboItem;
import com.sistemapos.sistematextil.model.Producto;
import com.sistemapos.sistematextil.services.agent.cart.AgentCartPromotionCalculator.PricedLine;

class AgentCartPromotionCalculatorTest {

    @Test
    void appliesTwoUnitsOfSameProduct() {
        Producto emma = product(10, "EMMA");
        EcommercePromocionCombo promotion = promotion(28, "combo 28", "120.00", item(emma, 2));

        var result = new AgentCartPromotionCalculator().calculate(
                List.of(new PricedLine(101, 10, 2, new BigDecimal("65.00"))),
                List.of(promotion), Map.of(10, 8));

        assertEquals(new BigDecimal("130.00"), result.subtotal());
        assertEquals(new BigDecimal("10.00"), result.discount());
        assertEquals(new BigDecimal("120.00"), result.total());
        assertEquals(28, result.promotions().getFirst().promotionId());
    }

    @Test
    void appliesPromotionToTwentyUnitsWithoutCombinatorialExplosion() {
        Producto emma = product(10, "EMMA");
        EcommercePromocionCombo promotion = promotion(28, "combo 28", "120.00", item(emma, 2));

        var result = new AgentCartPromotionCalculator().calculate(
                List.of(new PricedLine(101, 10, 20, new BigDecimal("65.00"))),
                List.of(promotion), Map.of(10, 42));

        assertEquals(new BigDecimal("1300.00"), result.subtotal());
        assertEquals(new BigDecimal("100.00"), result.discount());
        assertEquals(new BigDecimal("1200.00"), result.total());
        assertEquals(10, result.promotions().size());
    }

    @Test
    void choosesBestNonOverlappingCombinationForMixedProducts() {
        Producto emma = product(10, "EMMA");
        Producto alice = product(20, "ALICE");
        Producto lia = product(30, "LIA");
        EcommercePromocionCombo emmaAlice = promotion(42, "combo 42", "145.00", item(emma, 1), item(alice, 1));
        EcommercePromocionCombo aliceLia = promotion(43, "combo 43", "140.00", item(alice, 1), item(lia, 1));

        var result = new AgentCartPromotionCalculator().calculate(List.of(
                new PricedLine(101, 10, 1, new BigDecimal("65.00")),
                new PricedLine(201, 20, 1, new BigDecimal("95.00")),
                new PricedLine(301, 30, 1, new BigDecimal("75.00"))),
                List.of(emmaAlice, aliceLia), Map.of(10, 5, 20, 5, 30, 5));

        assertEquals(1, result.promotions().size());
        assertEquals(43, result.promotions().getFirst().promotionId());
        assertEquals(new BigDecimal("30.00"), result.discount());
    }

    private Producto product(int id, String name) {
        Producto product = new Producto();
        product.setIdProducto(id);
        product.setNombre(name);
        return product;
    }

    private EcommercePromocionComboItem item(Producto product, int quantity) {
        EcommercePromocionComboItem item = new EcommercePromocionComboItem();
        item.setProducto(product);
        item.setCantidadRequerida(quantity);
        return item;
    }

    private EcommercePromocionCombo promotion(int id, String name, String price,
            EcommercePromocionComboItem... items) {
        EcommercePromocionCombo promotion = new EcommercePromocionCombo();
        promotion.setIdEcommercePromocionCombo(id);
        promotion.setNombre(name);
        promotion.setPrecioCombo(new BigDecimal(price));
        promotion.setItems(new ArrayList<>());
        for (EcommercePromocionComboItem item : items) promotion.addItem(item);
        return promotion;
    }
}
