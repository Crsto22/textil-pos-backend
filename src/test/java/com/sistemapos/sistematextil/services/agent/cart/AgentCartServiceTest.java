package com.sistemapos.sistematextil.services.agent.cart;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sistemapos.sistematextil.model.Color;
import com.sistemapos.sistematextil.model.CrmWhatsappAgentSession;
import com.sistemapos.sistematextil.model.CrmWhatsappAgentState;
import com.sistemapos.sistematextil.model.CrmWhatsappConnection;
import com.sistemapos.sistematextil.model.EcommercePromocionCombo;
import com.sistemapos.sistematextil.model.EcommercePromocionComboItem;
import com.sistemapos.sistematextil.model.Producto;
import com.sistemapos.sistematextil.model.ProductoVariante;
import com.sistemapos.sistematextil.model.Sucursal;
import com.sistemapos.sistematextil.model.SucursalStock;
import com.sistemapos.sistematextil.model.Talla;
import com.sistemapos.sistematextil.repositories.AgentCatalogReadRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAgentSessionRepository;
import com.sistemapos.sistematextil.repositories.EcommercePromocionComboRepository;
import com.sistemapos.sistematextil.services.PrecioOfertaService;
import com.sistemapos.sistematextil.services.agent.CrmWhatsappAgentStateMachine;
import com.sistemapos.sistematextil.services.agent.cart.AgentCartService.CartResult;
import com.sistemapos.sistematextil.services.agent.cart.AgentCartStore.CartItemRow;
import com.sistemapos.sistematextil.services.agent.cart.AgentCartStore.CartRow;
import com.sistemapos.sistematextil.services.agent.cart.AgentCartStore.OperationRow;
import com.sistemapos.sistematextil.services.agent.cart.AgentCartStore.PricedCartItem;

class AgentCartServiceTest {
    private CrmWhatsappAgentSessionRepository sessionRepository;
    private AgentCatalogReadRepository catalogRepository;
    private EcommercePromocionComboRepository promotionRepository;
    private PrecioOfertaService priceService;
    private AgentCartStore store;
    private ObjectMapper mapper;
    private AgentCartService service;
    private CrmWhatsappAgentSession session;
    private SucursalStock stock;
    private CartRow cart;
    private CartItemRow persistedItem;

    @BeforeEach
    void setUp() {
        sessionRepository = mock(CrmWhatsappAgentSessionRepository.class);
        catalogRepository = mock(AgentCatalogReadRepository.class);
        promotionRepository = mock(EcommercePromocionComboRepository.class);
        priceService = mock(PrecioOfertaService.class);
        store = mock(AgentCartStore.class);
        mapper = new ObjectMapper();
        service = new AgentCartService(sessionRepository, new CrmWhatsappAgentStateMachine(), catalogRepository,
                promotionRepository, priceService, new AgentCartPromotionCalculator(), store, mapper);
        session = session(41L, 7, CrmWhatsappAgentState.ARMANDO_CARRITO);
        stock = stock(10, "EMMA", 101, 65, 8);
        cart = new CartRow(5L, 41L, "DRAFT", "PEN", zero(), zero(), zero(), 0L);
        persistedItem = new CartItemRow(9L, 101, 2, "EMMA", "NEGRO", "M",
                new BigDecimal("60.00"), new BigDecimal("5.00"), new BigDecimal("120.00"));
        when(store.findOperation(anyString())).thenReturn(Optional.empty());
        when(store.claimOperation(any(), anyString(), anyString(), anyString(), any())).thenReturn(true);
        when(sessionRepository.findForUpdate(41L)).thenReturn(Optional.of(session));
        when(store.lockActiveCart(41L)).thenReturn(Optional.of(cart));
        when(catalogRepository.findVariantByBranch(7, 101)).thenReturn(List.of(stock));
        when(catalogRepository.findVariantsByBranch(7, List.of(101))).thenReturn(List.of(stock));
        when(catalogRepository.findCatalogByBranch(7)).thenReturn(List.of(stock));
        when(promotionRepository.listarActivas(any())).thenReturn(List.of());
        when(priceService.resolverPrecioVigente(stock.getProductoVariante(), 7)).thenReturn(60.0);
    }

    @Test
    void obtainsPriceFromBackendAndPersistsRecalculatedCart() {
        when(store.findItems(5L)).thenReturn(List.of(), List.of(persistedItem), List.of(persistedItem));

        CartResult result = service.addProduct(41L, 101, 2, "op-add-1");

        ArgumentCaptor<PricedCartItem> captured = ArgumentCaptor.forClass(PricedCartItem.class);
        verify(store, org.mockito.Mockito.atLeastOnce()).upsertItem(org.mockito.Mockito.eq(5L), captured.capture(), any());
        PricedCartItem last = captured.getAllValues().getLast();
        assertEquals(new BigDecimal("60.00"), last.unitPrice());
        assertEquals(new BigDecimal("5.00"), last.unitDiscount());
        assertEquals(new BigDecimal("120.00"), result.total());
        verify(priceService, org.mockito.Mockito.atLeastOnce()).resolverPrecioVigente(stock.getProductoVariante(), 7);
    }

    @Test
    void confirmationRevalidatesAndAdvancesState() {
        when(store.findItems(5L)).thenReturn(List.of(persistedItem), List.of(persistedItem));

        CartResult result = service.confirm(41L, "op-confirm-1");

        assertEquals("CONFIRMED", result.status());
        assertEquals(CrmWhatsappAgentState.ESPERANDO_METODO_PAGO, session.getState());
        verify(store).confirm(org.mockito.Mockito.eq(5L), any());
    }

    @Test
    void completedOperationIsReplayedWithoutMutatingCartAgain() throws Exception {
        CartResult prior = new CartResult(5L, "DRAFT", "PEN", new BigDecimal("65.00"), zero(),
                new BigDecimal("65.00"), List.of(), List.of());
        String operationKey = sha256("cart:41:retry-1");
        String fingerprint = sha256("agregar_producto_carrito:101:1");
        when(store.findOperation(operationKey)).thenReturn(Optional.of(new OperationRow(operationKey,
                "agregar_producto_carrito", "COMPLETED", fingerprint, mapper.writeValueAsString(prior))));

        CartResult replayed = service.addProduct(41L, 101, 1, "retry-1");

        assertEquals(prior, replayed);
        verify(sessionRepository, never()).findForUpdate(any());
        verify(store, never()).upsertItem(any(), any(), any());
    }

    @Test
    void refusesAQuantityGreaterThanRealBranchStock() {
        when(store.findItems(5L)).thenReturn(List.of());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.addProduct(41L, 101, 9, "op-no-stock"));

        assertEquals("Stock insuficiente; disponible: 8", error.getMessage());
        verify(store, never()).upsertItem(any(), any(), any());
    }

    @Test
    void changesSizeAndColorAtomicallyWithoutChangingTheProductOrQuantity() {
        SucursalStock replacement = stock(10, "EMMA", 102, 65, 5, "CAMEL", "L");
        CartItemRow replacementItem = new CartItemRow(10L, 102, 2, "EMMA", "CAMEL", "L",
                new BigDecimal("60.00"), new BigDecimal("5.00"), new BigDecimal("120.00"));
        when(catalogRepository.findVariantByBranch(7, 102)).thenReturn(List.of(replacement));
        when(catalogRepository.findVariantsByBranch(7, List.of(102))).thenReturn(List.of(replacement));
        when(catalogRepository.findCatalogByBranch(7)).thenReturn(List.of(replacement));
        when(priceService.resolverPrecioVigente(replacement.getProductoVariante(), 7)).thenReturn(60.0);
        when(store.deleteItem(5L, 101)).thenReturn(1);
        when(store.findItems(5L)).thenReturn(
                List.of(persistedItem), List.of(replacementItem), List.of(replacementItem));

        CartResult result = service.changeVariant(41L, 101, 102, "op-change-variant");

        assertEquals(1, result.items().size());
        assertEquals(102, result.items().getFirst().variantId());
        assertEquals("CAMEL", result.items().getFirst().color());
        assertEquals("L", result.items().getFirst().size());
        assertEquals(2, result.items().getFirst().quantity());
        verify(store).deleteItem(5L, 101);
        verify(store, org.mockito.Mockito.atLeastOnce()).upsertItem(org.mockito.Mockito.eq(5L),
                org.mockito.ArgumentMatchers.argThat(item -> item.variantId().equals(102) && item.quantity() == 2),
                any());
    }

    @Test
    void replacesAProductWithAnotherModelAtomicallyAndPreservesQuantity() {
        SucursalStock replacement = stock(20, "ANGUIE LISO", 202, 75, 5, "GRIS OSCURO", "M");
        CartItemRow replacementItem = new CartItemRow(10L, 202, 2, "ANGUIE LISO", "GRIS OSCURO", "M",
                new BigDecimal("75.00"), BigDecimal.ZERO, new BigDecimal("150.00"));
        when(catalogRepository.findVariantByBranch(7, 202)).thenReturn(List.of(replacement));
        when(catalogRepository.findVariantsByBranch(7, List.of(202))).thenReturn(List.of(replacement));
        when(catalogRepository.findCatalogByBranch(7)).thenReturn(List.of(stock, replacement));
        when(priceService.resolverPrecioVigente(replacement.getProductoVariante(), 7)).thenReturn(75.0);
        when(store.deleteItem(5L, 101)).thenReturn(1);
        when(store.findItems(5L)).thenReturn(
                List.of(persistedItem), List.of(replacementItem), List.of(replacementItem));

        CartResult result = service.replaceProduct(41L, 101, 202, "op-replace-product");

        assertEquals(1, result.items().size());
        assertEquals("ANGUIE LISO", result.items().getFirst().product());
        assertEquals(202, result.items().getFirst().variantId());
        assertEquals(2, result.items().getFirst().quantity());
        verify(store).deleteItem(5L, 101);
        verify(store, org.mockito.Mockito.atLeastOnce()).upsertItem(org.mockito.Mockito.eq(5L),
                org.mockito.ArgumentMatchers.argThat(item -> item.variantId().equals(202) && item.quantity() == 2),
                any());
    }

    @Test
    void addingComboReusesAnUnpromotedMatchingItemAlreadyInTheCart() {
        SucursalStock lyanaStock = stock(60, "LYANA", 202, 67, 10, "AZUL", "L");
        CartItemRow existingEmma = new CartItemRow(9L, 101, 1, "EMMA", "NEGRO", "M",
                new BigDecimal("65.00"), zero(), new BigDecimal("65.00"));
        CartItemRow addedLyana = new CartItemRow(10L, 202, 1, "LYANA", "AZUL", "L",
                new BigDecimal("67.00"), zero(), new BigDecimal("67.00"));
        EcommercePromocionCombo combo = combo(56, "combo 30", new BigDecimal("125.00"),
                List.of(new RequiredProduct(10, "EMMA", 1), new RequiredProduct(60, "LYANA", 1)));

        when(catalogRepository.findVariantByBranch(7, 202)).thenReturn(List.of(lyanaStock));
        when(catalogRepository.findVariantsByBranch(7, List.of(101))).thenReturn(List.of(stock));
        when(catalogRepository.findVariantsByBranch(7, List.of(101, 202))).thenReturn(List.of(stock, lyanaStock));
        when(catalogRepository.findCatalogByBranch(7)).thenReturn(List.of(stock, lyanaStock));
        when(priceService.resolverPrecioVigente(lyanaStock.getProductoVariante(), 7)).thenReturn(67.0);
        when(promotionRepository.listarActivas(any())).thenReturn(List.of(combo));
        when(store.findItems(5L)).thenReturn(
                List.of(existingEmma),
                List.of(existingEmma),
                List.of(existingEmma),
                List.of(existingEmma, addedLyana),
                List.of(existingEmma, addedLyana));

        CartResult result = service.addCombo(41L, 56,
                List.of(new AgentCartService.ComboSelection(101, 1),
                        new AgentCartService.ComboSelection(202, 1)),
                "op-combo-reuse");

        assertEquals(new BigDecimal("125.00"), result.total());
        assertEquals(1, result.promotions().size());
        assertEquals(56, result.promotions().getFirst().promotionId());
        ArgumentCaptor<PricedCartItem> persisted = ArgumentCaptor.forClass(PricedCartItem.class);
        verify(store, org.mockito.Mockito.atLeastOnce()).upsertItem(org.mockito.Mockito.eq(5L),
                persisted.capture(), any());
        long duplicatedEmma = persisted.getAllValues().stream()
                .filter(item -> item.variantId().equals(101) && item.quantity() > 1)
                .count();
        assertEquals(0, duplicatedEmma);
        org.junit.jupiter.api.Assertions.assertTrue(persisted.getAllValues().stream()
                .anyMatch(item -> item.variantId().equals(202) && item.quantity() == 1));
    }

    @Test
    void addingTheSecondProductIndividuallyAppliesTheAvailableCombo() {
        SucursalStock lyanaStock = stock(60, "LYANA", 202, 67, 10, "BEIGE CLARO", "XS");
        CartItemRow existingEmma = new CartItemRow(9L, 101, 1, "EMMA", "NEGRO", "XS",
                new BigDecimal("65.00"), zero(), new BigDecimal("65.00"));
        CartItemRow addedLyana = new CartItemRow(10L, 202, 1, "LYANA", "BEIGE CLARO", "XS",
                new BigDecimal("67.00"), zero(), new BigDecimal("67.00"));
        EcommercePromocionCombo combo = combo(56, "combo 30", new BigDecimal("125.00"),
                List.of(new RequiredProduct(10, "EMMA", 1), new RequiredProduct(60, "LYANA", 1)));

        when(catalogRepository.findVariantByBranch(7, 202)).thenReturn(List.of(lyanaStock));
        when(catalogRepository.findVariantsByBranch(7, List.of(101, 202))).thenReturn(List.of(stock, lyanaStock));
        when(catalogRepository.findCatalogByBranch(7)).thenReturn(List.of(stock, lyanaStock));
        when(priceService.resolverPrecioVigente(lyanaStock.getProductoVariante(), 7)).thenReturn(67.0);
        when(promotionRepository.listarActivas(any())).thenReturn(List.of(combo));
        when(store.findItems(5L)).thenReturn(
                List.of(existingEmma),
                List.of(existingEmma, addedLyana),
                List.of(existingEmma, addedLyana));

        CartResult result = service.addProduct(41L, 202, 1, "op-add-lyana-to-combo");

        assertEquals(new BigDecimal("125.00"), result.total());
        assertEquals(1, result.promotions().size());
        assertEquals(56, result.promotions().getFirst().promotionId());
    }

    @Test
    void removesAnUnavailableOldLineWithoutBlockingANewValidProduct() {
        SucursalStock staleStock = stock(60, "LYANA", 303, 70, 0, "VINO", "S");
        CartItemRow staleItem = new CartItemRow(8L, 303, 1, "LYANA", "VINO", "S",
                new BigDecimal("70.00"), zero(), new BigDecimal("70.00"));
        CartItemRow newItem = new CartItemRow(9L, 101, 1, "EMMA", "NEGRO", "M",
                new BigDecimal("60.00"), new BigDecimal("5.00"), new BigDecimal("60.00"));
        when(store.findItems(5L)).thenReturn(
                List.of(staleItem),
                List.of(staleItem, newItem),
                List.of(newItem));
        when(catalogRepository.findVariantsByBranch(7, List.of(303, 101)))
                .thenReturn(List.of(staleStock, stock));
        when(catalogRepository.findCatalogByBranch(7)).thenReturn(List.of(staleStock, stock));

        CartResult result = service.addProduct(41L, 101, 1, "op-replace-stale");

        assertEquals(1, result.items().size());
        assertEquals(101, result.items().getFirst().variantId());
        verify(store).deleteItem(5L, 303);
    }

    private EcommercePromocionCombo combo(int id, String name, BigDecimal price,
            List<RequiredProduct> products) {
        EcommercePromocionCombo promotion = new EcommercePromocionCombo();
        promotion.setIdEcommercePromocionCombo(id);
        promotion.setNombre(name);
        promotion.setPrecioCombo(price);
        for (RequiredProduct required : products) {
            Producto product = new Producto();
            product.setIdProducto(required.id());
            product.setNombre(required.name());
            EcommercePromocionComboItem item = new EcommercePromocionComboItem();
            item.setProducto(product);
            item.setCantidadRequerida(required.quantity());
            promotion.addItem(item);
        }
        return promotion;
    }

    private record RequiredProduct(int id, String name, int quantity) {
    }

    private CrmWhatsappAgentSession session(long id, int branchId, CrmWhatsappAgentState state) {
        Sucursal branch = new Sucursal();
        branch.setIdSucursal(branchId);
        CrmWhatsappConnection connection = new CrmWhatsappConnection();
        connection.setIdConnection(3L);
        connection.setSucursal(branch);
        CrmWhatsappAgentSession value = new CrmWhatsappAgentSession();
        value.setIdAgentSession(id);
        value.setConnection(connection);
        value.setActiveSlot(true);
        value.setState(state);
        return value;
    }

    private SucursalStock stock(int productId, String productName, int variantId, double price, int quantity) {
        return stock(productId, productName, variantId, price, quantity, "NEGRO", "M");
    }

    private SucursalStock stock(int productId, String productName, int variantId, double price, int quantity,
            String colorName, String sizeName) {
        Producto product = new Producto();
        product.setIdProducto(productId);
        product.setNombre(productName);
        ProductoVariante variant = new ProductoVariante();
        variant.setIdProductoVariante(variantId);
        variant.setProducto(product);
        variant.setPrecio(price);
        Color color = new Color();
        color.setNombre(colorName);
        variant.setColor(color);
        Talla size = new Talla();
        size.setNombre(sizeName);
        variant.setTalla(size);
        SucursalStock value = new SucursalStock();
        value.setProductoVariante(variant);
        value.setCantidad(quantity);
        return value;
    }

    private BigDecimal zero() {
        return BigDecimal.ZERO.setScale(2);
    }

    private String sha256(String value) throws Exception {
        return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
}
