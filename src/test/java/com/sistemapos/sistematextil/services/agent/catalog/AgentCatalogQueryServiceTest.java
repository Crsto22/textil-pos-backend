package com.sistemapos.sistematextil.services.agent.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sistemapos.sistematextil.model.Categoria;
import com.sistemapos.sistematextil.model.Color;
import com.sistemapos.sistematextil.model.Producto;
import com.sistemapos.sistematextil.model.ProductoVariante;
import com.sistemapos.sistematextil.model.SucursalStock;
import com.sistemapos.sistematextil.model.Talla;
import com.sistemapos.sistematextil.repositories.AgentCatalogReadRepository;
import com.sistemapos.sistematextil.repositories.ProductoColorImagenRepository;
import com.sistemapos.sistematextil.repositories.ProductoVarianteOfertaSucursalRepository;
import com.sistemapos.sistematextil.services.agent.catalog.AgentCatalogScopeResolver.CatalogScope;
import com.sistemapos.sistematextil.services.agent.ai.AgentCatalogAvailability;

class AgentCatalogQueryServiceTest {
    private AgentCatalogScopeResolver scopeResolver;
    private AgentCatalogReadRepository catalogRepository;
    private ProductoColorImagenRepository imageRepository;
    private ProductoVarianteOfertaSucursalRepository offerRepository;
    private AgentCatalogQueryService service;

    @BeforeEach
    void setUp() {
        scopeResolver = mock(AgentCatalogScopeResolver.class);
        catalogRepository = mock(AgentCatalogReadRepository.class);
        imageRepository = mock(ProductoColorImagenRepository.class);
        offerRepository = mock(ProductoVarianteOfertaSucursalRepository.class);
        service = new AgentCatalogQueryService(scopeResolver, catalogRepository, imageRepository, offerRepository);
        when(scopeResolver.resolve(41L)).thenReturn(new CatalogScope(41L, 3L, 7));
    }

    @Test
    void understandsFreeTextAndMinorProductTypos() {
        when(catalogRepository.findCatalogByBranch(7)).thenReturn(List.of(
                stock(10, "ALICE RAYAS", 101, "PERLA", "M", 4),
                stock(20, "EMMA", 201, "CAMEL", "S", 2)));

        var result = service.searchProducts(41L, "deseo ver el alicee a rayas por favor", 10);

        assertEquals(1, result.products().size());
        assertEquals(10, result.products().getFirst().productId());
        assertEquals("ALICE RAYAS", result.products().getFirst().name());
        verify(catalogRepository).findCatalogByBranch(7);
    }

    @Test
    void genericCatalogQuestionListsProductsInsteadOfReturningEmpty() {
        when(catalogRepository.findCatalogByBranch(7)).thenReturn(List.of(
                stock(10, "ALICE RAYAS", 101, "PERLA", "M", 4),
                stock(20, "EMMA", 201, "CAMEL", "S", 2)));

        var result = service.searchProducts(41L, "que productos tienes", 10);

        assertEquals(2, result.products().size());
    }

    @Test
    void filtersReadyStockAndGroupsAvailableSizesByColor() {
        SucursalStock aliceXs = stock(10, "ALICE RAYAS", 101, "PERLA", "XS", 4);
        SucursalStock aliceM = stock(10, "ALICE RAYAS", 102, "PERLA", "M", 2);
        SucursalStock aliceNoStock = stock(10, "ALICE RAYAS", 103, "NEGRO", "S", 0);
        SucursalStock preorder = stock(20, "ALESSIA ENTERO", 201, "NEGRO", "S", 5);
        preorder.getProductoVariante().getProducto().setPreventa(true);
        when(catalogRepository.findCatalogByBranch(7)).thenReturn(
                List.of(aliceXs, aliceM, aliceNoStock, preorder));

        var result = service.searchProducts(41L, "", 20, AgentCatalogAvailability.READY_STOCK_ONLY);

        assertEquals(AgentCatalogAvailability.READY_STOCK_ONLY, result.availability());
        assertEquals(1, result.products().size());
        var alice = result.products().getFirst();
        assertEquals("ALICE RAYAS", alice.name());
        assertEquals(List.of("PERLA"), alice.colors());
        assertEquals(List.of("XS", "M"), alice.availabilityByColor().getFirst().sizes());
        assertEquals(6, alice.availabilityByColor().getFirst().totalStock());
    }

    @Test
    void filtersOnlyPreorderProducts() {
        SucursalStock ready = stock(10, "ALICE RAYAS", 101, "PERLA", "M", 4);
        SucursalStock preorder = stock(20, "ALESSIA ENTERO", 201, "NEGRO", "S", 5);
        preorder.getProductoVariante().getProducto().setPreventa(true);
        when(catalogRepository.findCatalogByBranch(7)).thenReturn(List.of(ready, preorder));

        var result = service.searchProducts(41L, "", 20, AgentCatalogAvailability.PREORDER_ONLY);

        assertEquals(1, result.products().size());
        assertEquals("ALESSIA ENTERO", result.products().getFirst().name());
        assertEquals(true, result.products().getFirst().preorder());
    }

    @Test
    void expiredPreorderIsTreatedAsReadyStockAndItsOldDateIsNotExposed() {
        SucursalStock expired = stock(20, "ANNIE RAYAS", 201, "NEGRO", "S", 5);
        Producto product = expired.getProductoVariante().getProducto();
        product.setPreventa(true);
        product.setFechaEnvioPreventa(LocalDate.now().minusDays(1));
        when(catalogRepository.findCatalogByBranch(7)).thenReturn(List.of(expired));
        when(catalogRepository.findProductByBranch(7, 20)).thenReturn(List.of(expired));
        when(offerRepository
                .findByProductoVarianteIdProductoVarianteInAndSucursalIdSucursalAndDeletedAtIsNull(
                        List.of(201), 7)).thenReturn(List.of());

        var preorder = service.searchProducts(41L, "", 20, AgentCatalogAvailability.PREORDER_ONLY);
        var ready = service.searchProducts(41L, "", 20, AgentCatalogAvailability.READY_STOCK_ONLY);
        var detail = service.getProduct(41L, 20);

        assertEquals(0, preorder.products().size());
        assertEquals(1, ready.products().size());
        assertEquals(false, ready.products().getFirst().preorder());
        assertEquals(null, ready.products().getFirst().preorderShippingDate());
        assertEquals(false, detail.preorder());
        assertEquals(null, detail.preorderShippingDate());
    }

    @Test
    void preorderEndingTodayIsNoLongerAdvertisedAsPreorder() {
        SucursalStock row = stock(20, "ANNIE ENTERO", 201, "NEGRO", "S", 5);
        row.getProductoVariante().getProducto().setPreventa(true);
        row.getProductoVariante().getProducto().setFechaEnvioPreventa(LocalDate.now());
        when(catalogRepository.findCatalogByBranch(7)).thenReturn(List.of(row));

        var result = service.searchProducts(41L, "", 20, AgentCatalogAvailability.PREORDER_ONLY);

        assertEquals(0, result.products().size());
    }

    @Test
    void nonexistentProductReturnsNoCandidateInsteadOfInventingOne() {
        when(catalogRepository.findCatalogByBranch(7)).thenReturn(List.of(
                stock(10, "ALICE RAYAS", 101, "PERLA", "M", 4),
                stock(20, "EMMA", 201, "CAMEL", "S", 2),
                stock(30, "BELEN", 301, "NEGRO", "M", 3)));

        var result = service.searchProducts(41L, "quiero el modelo Brenda", 10);

        assertEquals(0, result.products().size());
    }

    @Test
    void sharedProductNameReturnsAllRealVariantsForClarification() {
        when(catalogRepository.findCatalogByBranch(7)).thenReturn(List.of(
                stock(10, "ALESSIA ENTERO", 101, "MARRON", "M", 4),
                stock(11, "ALESSIA RAYAS", 111, "PLATA", "M", 3),
                stock(20, "ALICE RAYAS", 201, "CAMEL", "S", 2)));

        var result = service.searchProducts(41L, "quiero Alessia", 10);

        assertEquals(List.of("ALESSIA ENTERO", "ALESSIA RAYAS"),
                result.products().stream().map(AgentCatalogQueryService.ProductSummary::name).toList());
    }

    @Test
    void rejectsProductOutsideConversationBranchBeforeReadingImages() {
        when(catalogRepository.findProductByBranch(7, 999)).thenReturn(List.of());

        assertThrows(IllegalArgumentException.class, () -> service.getProductImage(41L, 999, "rojo"));
        verify(catalogRepository).findProductByBranch(7, 999);
    }

    @Test
    void stockAlwaysUsesBranchResolvedFromSession() {
        SucursalStock row = stock(10, "ALICE RAYAS", 101, "PERLA", "M", 4);
        when(catalogRepository.findVariantByBranch(7, 101)).thenReturn(List.of(row));

        var result = service.getStock(41L, 101, 5);

        assertEquals(7, result.branchId());
        assertEquals(4, result.availableStock());
        assertEquals(Boolean.FALSE, result.sufficient());
        verify(catalogRepository).findVariantByBranch(7, 101);
    }

    private SucursalStock stock(int productId, String productName, int variantId, String colorName,
            String sizeName, int quantity) {
        Categoria category = new Categoria();
        category.setNombreCategoria("CONJUNTOS");
        Producto product = new Producto();
        product.setIdProducto(productId);
        product.setNombre(productName);
        product.setDescripcion("Tela Aruba");
        product.setCategoria(category);
        product.setPreventa(false);

        Color color = new Color();
        color.setNombre(colorName);
        Talla size = new Talla();
        size.setNombre(sizeName);
        ProductoVariante variant = new ProductoVariante();
        variant.setIdProductoVariante(variantId);
        variant.setProducto(product);
        variant.setColor(color);
        variant.setTalla(size);
        variant.setSku("SKU-" + variantId);
        variant.setPrecio(75.0);

        SucursalStock stock = new SucursalStock();
        stock.setProductoVariante(variant);
        stock.setCantidad(quantity);
        return stock;
    }
}
