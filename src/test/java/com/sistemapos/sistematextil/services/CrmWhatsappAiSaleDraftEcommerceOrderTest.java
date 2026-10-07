package com.sistemapos.sistematextil.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import com.sistemapos.sistematextil.model.CrmWhatsappAiSaleDraft;
import com.sistemapos.sistematextil.model.CrmWhatsappAiSaleDraftItem;
import com.sistemapos.sistematextil.model.CrmWhatsappAiSaleDraftStatus;
import com.sistemapos.sistematextil.model.CrmWhatsappConnection;
import com.sistemapos.sistematextil.model.CrmWhatsappConversation;
import com.sistemapos.sistematextil.model.Empresa;
import com.sistemapos.sistematextil.model.ProductoVariante;
import com.sistemapos.sistematextil.model.Sucursal;
import com.sistemapos.sistematextil.model.SucursalStock;
import com.sistemapos.sistematextil.repositories.*;
import com.sistemapos.sistematextil.services.CrmWhatsappAiCommercialQueryService.CatalogResult;
import com.sistemapos.sistematextil.services.CrmWhatsappAiCommercialQueryService.ProductResult;
import com.sistemapos.sistematextil.services.CrmWhatsappAiCommercialQueryService.PromotionCatalogResult;
import com.sistemapos.sistematextil.services.CrmWhatsappAiCommercialQueryService.PromotionProductResult;
import com.sistemapos.sistematextil.services.CrmWhatsappAiCommercialQueryService.PromotionResult;
import com.sistemapos.sistematextil.services.CrmWhatsappAiCommercialQueryService.VariantResult;
import com.sistemapos.sistematextil.services.ai.AiModelProvider.SaleActionResult;
import com.sistemapos.sistematextil.services.ai.AiModelProvider.Usage;
import com.sistemapos.sistematextil.util.ecommerce.EcommerceCarritoResumenResponse;

class CrmWhatsappAiSaleDraftEcommerceOrderTest {
    private final CrmWhatsappAiSaleDraftRepository drafts = mock(CrmWhatsappAiSaleDraftRepository.class);
    private final SucursalStockRepository stocks = mock(SucursalStockRepository.class);
    private final CrmWhatsappAiCommercialQueryService commercial = mock(CrmWhatsappAiCommercialQueryService.class);
    private final EcommercePromocionComboService promotions = mock(EcommercePromocionComboService.class);
    private final CrmWhatsappEventService events = mock(CrmWhatsappEventService.class);
    private final CrmWhatsappAiSaleDraftService service = new CrmWhatsappAiSaleDraftService(
            drafts,
            mock(CrmWhatsappConversationRepository.class),
            stocks,
            mock(SucursalMetodoPagoConfigRepository.class),
            mock(UsuarioRepository.class),
            mock(ClienteRepository.class),
            mock(VentaRepository.class),
            commercial,
            mock(CrmWhatsappAiPaymentService.class),
            mock(PrecioOfertaService.class),
            promotions,
            events,
            mock(ApplicationEventPublisher.class),
            mock(CrmWhatsappAiAuditService.class),
            mock(CrmWhatsappAiClientWriter.class));
    private final CrmWhatsappEcommerceOrderParser parser = new CrmWhatsappEcommerceOrderParser();

    @BeforeEach
    void setup() {
        when(drafts.findFirstByConversation_IdConversationAndStatusInOrderByCreatedAtDesc(any(), any()))
                .thenReturn(Optional.empty());
        when(drafts.save(any())).thenAnswer(invocation -> {
            CrmWhatsappAiSaleDraft draft = invocation.getArgument(0);
            if (draft.getIdAiSaleDraft() == null) draft.setIdAiSaleDraft(90L);
            if (draft.getStatus() == null) draft.setStatus(CrmWhatsappAiSaleDraftStatus.BUILDING);
            if (draft.getVersion() == null) draft.setVersion(1);
            if (draft.getSubtotal() == null) draft.setSubtotal(BigDecimal.ZERO);
            if (draft.getPromotionDiscount() == null) draft.setPromotionDiscount(BigDecimal.ZERO);
            if (draft.getTotal() == null) draft.setTotal(BigDecimal.ZERO);
            if (draft.getExpiresAt() == null) draft.setExpiresAt(LocalDateTime.now().plusHours(24));
            return draft;
        });
        ProductoVariante variant = mock(ProductoVariante.class);
        SucursalStock stock = mock(SucursalStock.class);
        when(stock.getProductoVariante()).thenReturn(variant);
        when(stocks.findBySucursalIdSucursalAndProductoVarianteIdProductoVariante(3, 101))
                .thenReturn(Optional.of(stock));
        when(promotions.itemsDesdeVariantes(anyList(), anyMap(), eq(3))).thenReturn(List.of());
        when(promotions.calcular(anyList())).thenReturn(new EcommerceCarritoResumenResponse(
                new BigDecimal("65.00"), new BigDecimal("5.00"), new BigDecimal("60.00"),
                List.of(new EcommerceCarritoResumenResponse.ComboAplicado(
                        8, "Combo web", "EMMA x1", new BigDecimal("65.00"),
                        new BigDecimal("60.00"), new BigDecimal("5.00"))),
                List.of()));
    }

    @Test
    void addsValidItemsReportsUnavailableAndRecalculatesPromotions() {
        ProductResult emma = new ProductResult(11, "EMMA", "", "emma", "https://kiments.com.pe/productos/emma",
                false, null, "", "", List.of("Negro"), List.of("L"),
                List.of(new VariantResult(101, "EM-L-N", "", "Negro", "L", 5, true,
                        new BigDecimal("65.00"), new BigDecimal("65.00"), null, null, "", "", "")));
        when(commercial.searchProducts(any(), eq("EMMA")))
                .thenReturn(new CatalogResult(3, "Centro", "EMMA", "", false, List.of(emma)));
        when(commercial.searchProducts(any(), eq("CIELO")))
                .thenReturn(new CatalogResult(3, "Centro", "CIELO", "", false, List.of()));
        var order = parser.parse("""
                Hola KIMENTS, quiero comprar por WhatsApp:
                1. EMMA
                Color: Negro | Talla: L
                Cantidad: 1 x S/ 65.00 = S/ 65.00
                2. CIELO
                Color: TOPO | Talla: L
                Cantidad: 1 x S/ 70.00 = S/ 70.00
                Total: S/ 135.00
                """);

        var result = service.applyEcommerceOrder(conversation(), 77L, order);

        assertTrue(result.response().contains("1 x EMMA Negro, talla L"));
        assertTrue(result.response().contains("CIELO no esta disponible"));
        assertTrue(result.response().contains("Combo web"));
        assertTrue(result.response().contains("Total actualizado: S/60.00"));
        assertTrue(result.response().contains("*CONFIRMAR PEDIDO*"));
        assertTrue(result.response().contains("¿Qué otro producto deseas agregar?"));
        assertEquals(1, result.draft().items().size());
        verify(promotions).calcular(anyList());
    }

    @Test
    void resumenDelPedidoSeparaProductoTotalesYAccionesConIconos() {
        ProductResult alessia = new ProductResult(12, "ALESSIA RAYAS", "", "alessia-rayas",
                "https://kiments.com.pe/productos/alessia-rayas",
                false, null, "", "", List.of("CHOCOLATE"), List.of("L"),
                List.of(new VariantResult(101, "AR-L-C", "", "CHOCOLATE", "L", 4, true,
                        new BigDecimal("75.00"), new BigDecimal("75.00"), null, null, "", "", "")));
        when(commercial.searchProducts(any(), eq("ALESSIA RAYAS")))
                .thenReturn(new CatalogResult(3, "Centro", "ALESSIA RAYAS", "ALESSIA RAYAS",
                        false, List.of(alessia)));
        when(promotions.calcular(anyList())).thenReturn(new EcommerceCarritoResumenResponse(
                new BigDecimal("75.00"), BigDecimal.ZERO, new BigDecimal("75.00"), List.of(), List.of()));

        var result = service.applyAiAction(conversation(), new SaleActionResult(
                "ADD", "ALESSIA RAYAS", null, "CHOCOLATE", "L", 1,
                "", 100, "pedido", Usage.empty()));

        assertTrue(result.response().contains("🛍️ *Así quedaría tu pedido*"), result.response());
        assertTrue(result.response().contains("👗 *ALESSIA RAYAS*"), result.response());
        assertTrue(result.response().contains("🎨 Color: CHOCOLATE"), result.response());
        assertTrue(result.response().contains("📏 Talla: L"), result.response());
        assertTrue(result.response().contains("🔢 Cantidad: 1"), result.response());
        assertTrue(result.response().contains("💰 *Resumen*"), result.response());
        assertTrue(result.response().contains("*Total estimado: S/75.00*"), result.response());
        assertTrue(result.response().contains("*CONFIRMAR PEDIDO*"), result.response());
        assertTrue(result.response().contains("➕ ¿Qué otro producto deseas agregar?"), result.response());
        assertTrue(!result.response().contains("¿En qué más puedo ayudarte?"), result.response());
    }

    @Test
    void resolvesProductAndImplicitColorFromCompactOrderUsingCatalog() {
        ProductResult alessia = new ProductResult(12, "ALESSIA RAYAS", "", "alessia-rayas",
                "https://kiments.com.pe/productos/alessia-rayas",
                false, null, "", "", List.of("AZUL"), List.of("L"),
                List.of(new VariantResult(101, "AR-L-A", "", "AZUL", "L", 4, true,
                        new BigDecimal("75.00"), new BigDecimal("75.00"), null, null, "", "", "")));
        when(commercial.searchProducts(any(), eq("alessia rayas azul")))
                .thenReturn(new CatalogResult(3, "Centro", "alessia rayas azul", "ALESSIA RAYAS",
                        false, List.of(alessia)));
        when(promotions.calcular(anyList())).thenReturn(new EcommerceCarritoResumenResponse(
                new BigDecimal("75.00"), BigDecimal.ZERO, new BigDecimal("75.00"), List.of(), List.of()));

        var result = service.applyEcommerceOrder(conversation(), 78L,
                parser.parse("alessia rayas azul talla l cantidad 1"));

        assertTrue(result.response().contains("1 x ALESSIA RAYAS AZUL, talla L"));
        assertEquals(1, result.draft().items().size());
        assertEquals("AZUL", result.draft().items().getFirst().color());
    }

    @Test
    void colorInexistenteMuestraLosColoresRealesSinAgregarElProducto() {
        ProductResult alessia = new ProductResult(12, "ALESSIA ENTERO", "", "alessia-entero",
                "https://kiments.com.pe/productos/alessia-entero",
                false, null, "", "", List.of("MARRON", "NEGRO"), List.of("M"),
                List.of(
                        new VariantResult(101, "AE-M-M", "", "MARRON", "M", 4, true,
                                new BigDecimal("75.00"), new BigDecimal("75.00"), null, null, "", "", ""),
                        new VariantResult(102, "AE-M-N", "", "NEGRO", "M", 3, true,
                                new BigDecimal("75.00"), new BigDecimal("75.00"), null, null, "", "", "")));
        when(commercial.searchProducts(any(), eq("ALESSIA ENTERO")))
                .thenReturn(new CatalogResult(3, "Centro", "ALESSIA ENTERO", "ALESSIA ENTERO",
                        false, List.of(alessia)));

        var result = service.applyAiAction(conversation(), new SaleActionResult(
                "ADD", "ALESSIA ENTERO", null, "morocho", "M", 1,
                "", 100, "pedido", Usage.empty()));

        assertTrue(result.response().contains("morocho no está disponible"));
        assertTrue(result.response().contains("MARRON, NEGRO"));
        assertTrue(result.draft().items().isEmpty());
    }

    @Test
    void repetirLaUnicaUnidadYaAgregadaDevuelveElPedidoSinErrorDeStock() {
        CrmWhatsappConversation conversation = conversation();
        CrmWhatsappAiSaleDraft draft = new CrmWhatsappAiSaleDraft();
        draft.setIdAiSaleDraft(90L);
        draft.setConversation(conversation);
        draft.setConnection(conversation.getConnection());
        draft.setSucursal(conversation.getConnection().getSucursal());
        draft.setStatus(CrmWhatsappAiSaleDraftStatus.AWAITING_CUSTOMER);
        draft.setVersion(1);
        draft.setSubtotal(new BigDecimal("75.00"));
        draft.setPromotionDiscount(BigDecimal.ZERO);
        draft.setTotal(new BigDecimal("75.00"));
        draft.setExpiresAt(LocalDateTime.now().plusHours(24));
        CrmWhatsappAiSaleDraftItem existing = item(
                draft, 13, 101, "LIA RAYAS", "PLATA", "XS", "75.00");
        existing.setStockSnapshot(1);
        draft.getItems().add(existing);
        when(drafts.findFirstByConversation_IdConversationAndStatusInOrderByCreatedAtDesc(any(), any()))
                .thenReturn(Optional.of(draft));
        ProductResult lia = new ProductResult(13, "LIA RAYAS", "", "lia-rayas", "",
                false, null, "", "", List.of("PLATA"), List.of("XS"),
                List.of(new VariantResult(101, "LR-PL-XS", "", "PLATA", "XS", 1, true,
                        new BigDecimal("75.00"), new BigDecimal("75.00"), null, null, "", "", "")));
        when(commercial.searchProducts(any(), eq("LIA RAYAS")))
                .thenReturn(new CatalogResult(3, "Centro", "LIA RAYAS", "LIA RAYAS",
                        false, List.of(lia)));

        var result = service.applyAiAction(conversation, new SaleActionResult(
                "ADD", "LIA RAYAS", null, "PLATA", "XS", 1,
                "", 100, "confirmar una unidad", Usage.empty()));

        assertEquals(1, draft.getItems().getFirst().getQuantity());
        assertTrue(result.response().contains("LIA RAYAS"), result.response());
        assertTrue(!result.response().contains("No puedes superar el stock"), result.response());
    }

    @Test
    void pedidoCompactoConProductoAmbiguoSolicitaElModeloExacto() {
        when(commercial.searchProducts(any(), eq("Alessia"))).thenReturn(new CatalogResult(
                3, "Centro", "Alessia", "", false, "AMBIGUOUS",
                List.of(
                        new CrmWhatsappAiCommercialQueryService.ProductCandidate(12, "ALESSIA ENTERO"),
                        new CrmWhatsappAiCommercialQueryService.ProductCandidate(13, "ALESSIA RAYAS")),
                List.of()));

        var result = service.applyEcommerceOrder(conversation(), 79L,
                parser.parse("Quiero Alessia color morocho talla M cantidad 1"));

        assertTrue(result.response().contains("ALESSIA ENTERO"));
        assertTrue(result.response().contains("ALESSIA RAYAS"));
        assertTrue(result.response().contains("Cuál de estos modelos"));
        assertTrue(result.draft().items().isEmpty());
    }

    @Test
    void removeProductAndColorDeletesAllMatchingSizesOnly() {
        CrmWhatsappConversation conversation = conversation();
        CrmWhatsappAiSaleDraft draft = new CrmWhatsappAiSaleDraft();
        draft.setIdAiSaleDraft(90L);
        draft.setConversation(conversation);
        draft.setConnection(conversation.getConnection());
        draft.setSucursal(conversation.getConnection().getSucursal());
        draft.setStatus(CrmWhatsappAiSaleDraftStatus.AWAITING_CUSTOMER);
        draft.setVersion(3);
        draft.setSubtotal(new BigDecimal("197.00"));
        draft.setPromotionDiscount(BigDecimal.ZERO);
        draft.setTotal(new BigDecimal("197.00"));
        draft.setExpiresAt(LocalDateTime.now().plusHours(24));
        draft.getItems().add(item(draft, 11, 101, "EMMA", "CHOCOLATE", "S", "65.00"));
        draft.getItems().add(item(draft, 11, 102, "EMMA", "CHOCOLATE", "XS", "65.00"));
        draft.getItems().add(item(draft, 12, 103, "LYANA", "PERLA", "XS", "67.00"));
        when(drafts.findFirstByConversation_IdConversationAndStatusInOrderByCreatedAtDesc(any(), any()))
                .thenReturn(Optional.of(draft));

        var result = service.applyAiAction(conversation, new SaleActionResult(
                "REMOVE", "EMMA CHOCOLATE", null, "", "", null,
                "", 100, "eliminar variantes", Usage.empty()));

        assertEquals(1, draft.getItems().size());
        assertEquals("LYANA", draft.getItems().getFirst().getProductName());
        assertTrue(!result.response().contains("EMMA CHOCOLATE"));
    }

    @Test
    void selectedComboIsResolvedExactlyAndPromptsForItsFirstProduct() {
        CrmWhatsappConversation conversation = conversation();
        PromotionResult combo = new PromotionResult(
                142, "combo 42", "ALICE RAYAS + LIA RAYAS", new BigDecimal("155.00"),
                new BigDecimal("170.00"), new BigDecimal("15.00"),
                List.of(new PromotionProductResult(12, "ALICE RAYAS", 1, "", ""),
                        new PromotionProductResult(13, "LIA RAYAS", 1, "", "")));
        PromotionCatalogResult exact = new PromotionCatalogResult(
                3, "", 0, 10, 1, false, List.of(combo), combo, combo);
        when(commercial.promotions(conversation, "", 0, null, 142)).thenReturn(exact);
        ProductResult alice = new ProductResult(12, "ALICE RAYAS", "", "alice-rayas", "", false,
                null, "", "", List.of("NEGRO", "BEIGE"), List.of("S", "M"), List.of());
        when(commercial.searchProducts(conversation, "ALICE RAYAS"))
                .thenReturn(new CatalogResult(3, "Centro", "ALICE RAYAS", "", false, List.of(alice)));

        var result = service.applyAiAction(conversation, new SaleActionResult(
                "ADD_COMBO", "combo 42", 142, "", "", null,
                "", 100, "combo elegido", Usage.empty()));

        assertTrue(result.response().contains("combo 42"), result.response());
        assertTrue(result.response().contains("ALICE RAYAS"), result.response());
        assertTrue(result.response().contains("NEGRO, BEIGE"), result.response());
        assertTrue(result.response().contains("S, M"), result.response());
    }

    private CrmWhatsappAiSaleDraftItem item(
            CrmWhatsappAiSaleDraft draft, int productId, int variantId,
            String product, String color, String size, String price) {
        CrmWhatsappAiSaleDraftItem item = new CrmWhatsappAiSaleDraftItem();
        item.setDraft(draft);
        item.setProductId(productId);
        item.setVariantId(variantId);
        item.setProductName(product);
        item.setSku(product + "-" + color + "-" + size);
        item.setColor(color);
        item.setSize(size);
        item.setQuantity(1);
        item.setUnitPrice(new BigDecimal(price));
        item.setRegularUnitPrice(new BigDecimal(price));
        item.setStockSnapshot(5);
        item.setPreventa(false);
        return item;
    }

    private CrmWhatsappConversation conversation() {
        Empresa company = new Empresa();
        company.setIdEmpresa(1);
        Sucursal branch = new Sucursal();
        branch.setIdSucursal(3);
        branch.setNombre("Centro");
        branch.setEmpresa(company);
        CrmWhatsappConnection connection = new CrmWhatsappConnection();
        connection.setIdConnection(7L);
        connection.setEmpresa(company);
        connection.setSucursal(branch);
        CrmWhatsappConversation conversation = new CrmWhatsappConversation();
        conversation.setIdConversation(10L);
        conversation.setConnection(connection);
        conversation.setStatus("ESPERA");
        return conversation;
    }
}
