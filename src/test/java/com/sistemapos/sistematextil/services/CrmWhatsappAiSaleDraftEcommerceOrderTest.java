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
import com.sistemapos.sistematextil.model.CrmWhatsappConnection;
import com.sistemapos.sistematextil.model.CrmWhatsappConversation;
import com.sistemapos.sistematextil.model.Empresa;
import com.sistemapos.sistematextil.model.ProductoVariante;
import com.sistemapos.sistematextil.model.Sucursal;
import com.sistemapos.sistematextil.model.SucursalStock;
import com.sistemapos.sistematextil.repositories.*;
import com.sistemapos.sistematextil.services.CrmWhatsappAiCommercialQueryService.CatalogResult;
import com.sistemapos.sistematextil.services.CrmWhatsappAiCommercialQueryService.ProductResult;
import com.sistemapos.sistematextil.services.CrmWhatsappAiCommercialQueryService.VariantResult;
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
        assertEquals(1, result.draft().items().size());
        verify(promotions).calcular(anyList());
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
