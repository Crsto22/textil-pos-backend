package com.sistemapos.sistematextil.services.agent.promotion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sistemapos.sistematextil.model.Color;
import com.sistemapos.sistematextil.model.EcommercePromocionCombo;
import com.sistemapos.sistematextil.model.EcommercePromocionComboItem;
import com.sistemapos.sistematextil.model.Producto;
import com.sistemapos.sistematextil.model.ProductoVariante;
import com.sistemapos.sistematextil.model.SucursalStock;
import com.sistemapos.sistematextil.model.Talla;
import com.sistemapos.sistematextil.repositories.AgentCatalogReadRepository;
import com.sistemapos.sistematextil.repositories.EcommercePromocionComboRepository;
import com.sistemapos.sistematextil.services.agent.catalog.AgentCatalogScopeResolver;
import com.sistemapos.sistematextil.services.agent.catalog.AgentCatalogScopeResolver.CatalogScope;
import com.sistemapos.sistematextil.services.agent.context.AgentContext.Cart;
import com.sistemapos.sistematextil.services.agent.context.AgentContext.CartItem;
import com.sistemapos.sistematextil.services.agent.context.AgentContextDataSource;

class AgentPromotionQueryServiceTest {
    private AgentCatalogScopeResolver scopeResolver;
    private AgentCatalogReadRepository catalogRepository;
    private EcommercePromocionComboRepository promotionRepository;
    private AgentContextDataSource contextDataSource;
    private AgentPromotionQueryService service;
    private Producto emma;
    private Producto alice;
    private SucursalStock emmaStock;
    private SucursalStock aliceStock;

    @BeforeEach
    void setUp() {
        scopeResolver = mock(AgentCatalogScopeResolver.class);
        catalogRepository = mock(AgentCatalogReadRepository.class);
        promotionRepository = mock(EcommercePromocionComboRepository.class);
        contextDataSource = mock(AgentContextDataSource.class);
        service = new AgentPromotionQueryService(scopeResolver, catalogRepository, promotionRepository,
                contextDataSource);
        when(scopeResolver.resolve(41L)).thenReturn(new CatalogScope(41L, 3L, 7));
        emma = product(10, "EMMA");
        alice = product(20, "ALICE RAYAS");
        emmaStock = stock(emma, 101, 65, 8);
        aliceStock = stock(alice, 201, 95, 5);
        when(catalogRepository.findCatalogByBranch(7)).thenReturn(List.of(emmaStock, aliceStock));
    }

    @Test
    void twoEmmaQuestionPrioritizesSameProductPromotion() {
        EcommercePromocionCombo sameEmma = promotion(28, "combo 28", new BigDecimal("120.00"), item(emma, 2));
        EcommercePromocionCombo mixed = promotion(49, "combo 49", new BigDecimal("145.00"),
                item(emma, 1), item(alice, 1));
        when(promotionRepository.listarActivas(any(LocalDateTime.class))).thenReturn(List.of(mixed, sameEmma));

        var result = service.searchPromotions(41L, "si llevo dos Emma hay descuento", 10);

        assertEquals(2, result.promotions().size());
        assertEquals(28, result.promotions().getFirst().promotionId());
        assertEquals("TWO_SAME_PRODUCT", result.promotions().getFirst().type());
    }

    @Test
    void resolvesCombo42ByCommercialNameEvenWhenDatabaseIdDiffers() {
        EcommercePromocionCombo combo42 = promotion(900, "combo 42", new BigDecimal("145.00"),
                item(emma, 1), item(alice, 1));
        EcommercePromocionCombo combo41 = promotion(42, "combo 41", new BigDecimal("150.00"),
                item(emma, 1), item(alice, 1));
        when(promotionRepository.listarActivas(any(LocalDateTime.class))).thenReturn(List.of(combo41, combo42));

        var result = service.searchPromotions(41L, "quiero el combo 42", 10);

        assertEquals(900, result.promotions().getFirst().promotionId());
        assertEquals("combo 42", result.promotions().getFirst().name());
    }

    @Test
    void hidesPromotionWhenBranchStockCannotSatisfyItsRule() {
        emmaStock.setCantidad(1);
        EcommercePromocionCombo sameEmma = promotion(28, "combo 28", new BigDecimal("120.00"), item(emma, 2));
        when(promotionRepository.listarActivas(any(LocalDateTime.class))).thenReturn(List.of(sameEmma));

        var result = service.searchPromotions(41L, "dos Emma", 10);

        assertEquals(0, result.promotions().size());
        assertThrows(IllegalArgumentException.class, () -> service.getPromotion(41L, 28));
    }

    @Test
    void validatesTwoUnitsOfSameProductInActiveCart() {
        EcommercePromocionCombo sameEmma = promotion(28, "combo 28", new BigDecimal("120.00"), item(emma, 2));
        when(promotionRepository.listarActivas(any(LocalDateTime.class))).thenReturn(List.of(sameEmma));
        when(contextDataSource.findActiveCart(41L)).thenReturn(Optional.of(cart(
                new CartItem(1L, 101, "EMMA", "CAMEL", "S", 2, new BigDecimal("65.00"),
                        new BigDecimal("130.00")))));
        when(catalogRepository.findVariantsByBranch(7, List.of(101))).thenReturn(List.of(emmaStock));

        var result = service.validateCart(41L, 28);

        assertEquals("ELIGIBLE", result.status());
        assertEquals(28, result.eligiblePromotions().getFirst().promotionId());
        assertEquals(new BigDecimal("10.00"), result.eligiblePromotions().getFirst().savingsPerApplication());
    }

    @Test
    void validatesMixedProductComboAndReportsMissingSecondProduct() {
        EcommercePromocionCombo mixed = promotion(42, "combo 42", new BigDecimal("145.00"),
                item(emma, 1), item(alice, 1));
        when(promotionRepository.listarActivas(any(LocalDateTime.class))).thenReturn(List.of(mixed));
        when(contextDataSource.findActiveCart(41L)).thenReturn(Optional.of(cart(
                new CartItem(1L, 101, "EMMA", "CAMEL", "S", 1, new BigDecimal("65.00"),
                        new BigDecimal("65.00")))));
        when(catalogRepository.findVariantsByBranch(7, List.of(101))).thenReturn(List.of(emmaStock));

        var result = service.validateCart(41L, 42);

        assertEquals("INCOMPLETE", result.status());
        assertEquals(20, result.incompletePromotions().getFirst().items().getFirst().productId());
        assertEquals(1, result.incompletePromotions().getFirst().items().getFirst().missingQuantity());
        verify(catalogRepository).findVariantsByBranch(7, List.of(101));
    }

    private Producto product(int id, String name) {
        Producto product = new Producto();
        product.setIdProducto(id);
        product.setNombre(name);
        product.setEstado("ACTIVO");
        product.setActivo("ACTIVO");
        product.setPublicarEcommerce(true);
        return product;
    }

    private SucursalStock stock(Producto product, int variantId, double price, int quantity) {
        ProductoVariante variant = new ProductoVariante();
        variant.setIdProductoVariante(variantId);
        variant.setProducto(product);
        variant.setPrecio(price);
        Color color = new Color();
        color.setNombre("NEGRO");
        variant.setColor(color);
        Talla size = new Talla();
        size.setNombre("M");
        variant.setTalla(size);
        SucursalStock stock = new SucursalStock();
        stock.setProductoVariante(variant);
        stock.setCantidad(quantity);
        return stock;
    }

    private EcommercePromocionComboItem item(Producto product, int quantity) {
        EcommercePromocionComboItem item = new EcommercePromocionComboItem();
        item.setProducto(product);
        item.setCantidadRequerida(quantity);
        return item;
    }

    private EcommercePromocionCombo promotion(int id, String name, BigDecimal price,
            EcommercePromocionComboItem... items) {
        EcommercePromocionCombo promotion = new EcommercePromocionCombo();
        promotion.setIdEcommercePromocionCombo(id);
        promotion.setNombre(name);
        promotion.setPrecioCombo(price);
        promotion.setEstado("ACTIVO");
        promotion.setItems(new ArrayList<>());
        for (EcommercePromocionComboItem item : items) promotion.addItem(item);
        return promotion;
    }

    private Cart cart(CartItem... items) {
        BigDecimal subtotal = List.of(items).stream().map(CartItem::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Cart(5L, "ACTIVE", "PEN", subtotal, BigDecimal.ZERO, subtotal, List.of(items), List.of());
    }
}
