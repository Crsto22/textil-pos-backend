package com.sistemapos.sistematextil.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.sistemapos.sistematextil.model.CrmWhatsappConnection;
import com.sistemapos.sistematextil.model.CrmWhatsappConversation;
import com.sistemapos.sistematextil.model.Empresa;
import com.sistemapos.sistematextil.model.Sucursal;
import com.sistemapos.sistematextil.services.CrmWhatsappAiCommercialQueryService.CatalogResult;
import com.sistemapos.sistematextil.services.CrmWhatsappAiCommercialQueryService.ProductResult;
import com.sistemapos.sistematextil.services.CrmWhatsappAiCommercialQueryService.PaymentMethodItem;
import com.sistemapos.sistematextil.services.CrmWhatsappAiCommercialQueryService.PaymentResult;
import com.sistemapos.sistematextil.services.CrmWhatsappAiCommercialQueryService.ProductCandidate;
import com.sistemapos.sistematextil.services.CrmWhatsappAiCommercialQueryService.VariantResult;
import com.sistemapos.sistematextil.services.ai.AiModelProvider.ToolCall;

class CrmWhatsappAiToolServiceTest {

    private final CrmWhatsappAiCommercialQueryService commercial = mock(CrmWhatsappAiCommercialQueryService.class);
    private final CrmWhatsappAiKnowledgeService knowledge = mock(CrmWhatsappAiKnowledgeService.class);
    private final CrmWhatsappAiToolService service = new CrmWhatsappAiToolService(commercial, knowledge);

    @Test
    void ignoraIdsProporcionadosPorElModeloYAuditaSinDatosComerciales() {
        CrmWhatsappConversation conversation = conversation();
        VariantResult variant = new VariantResult(
                20, "SKU-20", "123", "AZUL", "M", 4, true,
                BigDecimal.valueOf(100), BigDecimal.valueOf(80), BigDecimal.valueOf(80),
                BigDecimal.valueOf(70), "Confirmar con asesor",
                "https://cdn.test/producto.jpg", "https://cdn.test/producto-thumb.jpg");
        CatalogResult catalog = new CatalogResult(
                3, "Kiments Centro", "polo", "", false,
                List.of(new ProductResult(10, "Polo", "Polos", "polo", "https://kiments.com.pe/productos/polo",
                        true, LocalDate.of(2026, 11, 15), "", "",
                        List.of("AZUL", "NEGRO"), List.of("S", "M"), List.of(variant))));
        when(commercial.searchProducts(conversation, "polo")).thenReturn(catalog);

        var result = service.execute(conversation, List.of(new ToolCall(
                "buscar_productos",
                Map.of("q", "polo", "idSucursal", 999, "idCliente", 888, "sql", "DROP TABLE venta"))));

        verify(commercial).searchProducts(conversation, "polo");
        assertEquals(1, result.modelResults().size());
        assertEquals(3, result.auditTrace().getFirst().get("branchId"));
        assertFalse(result.auditTrace().getFirst().toString().contains("SKU-20"));
        assertFalse(result.auditTrace().getFirst().toString().contains("DROP TABLE"));
        assertFalse(result.modelResults().toString().contains("Kiments Centro"));
        assertFalse(result.modelResults().toString().contains("Polos"));
        assertTrue(result.modelResults().toString().contains("preventa=true"));
        assertTrue(result.modelResults().toString().contains("fechaEnvioPreventa=2026-11-15"));
        assertTrue(result.evidence().toString().contains("ecommerceOnly=true"));
        assertEquals(1, result.mediaCandidates().size());
    }

    @Test
    void rechazaHerramientaFueraDeLaListaAutorizada() {
        var result = service.execute(conversation(), List.of(
                new ToolCall("ejecutar_sql", Map.of("q", "SELECT * FROM cliente"))));

        assertTrue(result.requiresHuman());
        assertEquals("DENIED", result.auditTrace().getFirst().get("status"));
    }

    @Test
    void exponeOpcionesCuandoElNombreDelProductoEsAmbiguo() {
        CrmWhatsappConversation conversation = conversation();
        CatalogResult catalog = new CatalogResult(3, "Kiments Centro", "Alessia", "", false,
                "AMBIGUOUS", List.of(
                        new ProductCandidate(30, "ALESSIA ENTERO"),
                        new ProductCandidate(31, "ALESSIA RAYAS")), List.of());
        when(commercial.searchProducts(conversation, "Alessia")).thenReturn(catalog);

        var result = service.execute(conversation,
                List.of(new ToolCall("buscar_productos", Map.of("q", "Alessia"))));

        assertEquals("AMBIGUOUS", result.modelResults().getFirst().get("resolution"));
        assertTrue(result.modelResults().getFirst().get("candidates").toString().contains("ALESSIA ENTERO"));
        assertTrue(result.modelResults().getFirst().get("candidates").toString().contains("ALESSIA RAYAS"));
    }

    @Test
    void evidenciaDePagosNoGuardaNumerosDeCuenta() {
        CrmWhatsappConversation conversation = conversation();
        when(commercial.paymentMethods(conversation)).thenReturn(new PaymentResult(
                3,
                "Kiments Centro",
                List.of(new PaymentMethodItem(8, "Yape", "Pago movil", true, true, false,
                        List.of("191-999999999"))),
                "No confirma pagos"));

        var result = service.execute(conversation, List.of(
                new ToolCall("consultar_metodos_pago", Map.of())));

        assertTrue(result.modelResults().toString().contains("191-999999999"));
        assertFalse(result.modelResults().toString().contains("Kiments Centro"));
        assertFalse(result.evidence().toString().contains("191-999999999"));
        assertTrue(result.evidence().toString().contains("Yape"));
    }

    private CrmWhatsappConversation conversation() {
        Empresa company = new Empresa();
        company.setIdEmpresa(1);
        Sucursal branch = new Sucursal();
        branch.setIdSucursal(3);
        branch.setNombre("Kiments Centro");
        branch.setEmpresa(company);
        branch.setEstado("ACTIVO");
        CrmWhatsappConnection connection = new CrmWhatsappConnection();
        connection.setIdConnection(7L);
        connection.setEmpresa(company);
        connection.setSucursal(branch);
        CrmWhatsappConversation conversation = new CrmWhatsappConversation();
        conversation.setIdConversation(12L);
        conversation.setConnection(connection);
        return conversation;
    }
}
