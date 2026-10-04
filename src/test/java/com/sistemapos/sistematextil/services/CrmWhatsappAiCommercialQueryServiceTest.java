package com.sistemapos.sistematextil.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;

import com.sistemapos.sistematextil.model.Cliente;
import com.sistemapos.sistematextil.model.CrmWhatsappConnection;
import com.sistemapos.sistematextil.model.CrmWhatsappConversation;
import com.sistemapos.sistematextil.model.Empresa;
import com.sistemapos.sistematextil.model.Sucursal;
import com.sistemapos.sistematextil.model.Venta;
import com.sistemapos.sistematextil.repositories.ProductoColorImagenRepository;
import com.sistemapos.sistematextil.repositories.SucursalMetodoPagoConfigRepository;
import com.sistemapos.sistematextil.repositories.SucursalStockRepository;
import com.sistemapos.sistematextil.repositories.SucursalStockRepository.EcommerceProductNameView;
import com.sistemapos.sistematextil.repositories.VentaRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappBusinessHoursRepository;
import com.sistemapos.sistematextil.util.ecommerce.EcommerceInicioComboResponse;
import com.sistemapos.sistematextil.util.paginacion.PagedResponse;

class CrmWhatsappAiCommercialQueryServiceTest {

    private final SucursalStockRepository stocks = mock(SucursalStockRepository.class);
    private final ProductoColorImagenRepository images = mock(ProductoColorImagenRepository.class);
    private final SucursalMetodoPagoConfigRepository payments = mock(SucursalMetodoPagoConfigRepository.class);
    private final VentaRepository sales = mock(VentaRepository.class);
    private final CrmWhatsappBusinessHoursRepository businessHours = mock(CrmWhatsappBusinessHoursRepository.class);
    private final PrecioOfertaService prices = mock(PrecioOfertaService.class);
    private final EcommercePromocionComboService promotions = mock(EcommercePromocionComboService.class);
    private final S3StorageService storage = mock(S3StorageService.class);
    private final CrmWhatsappAiCommercialQueryService service = new CrmWhatsappAiCommercialQueryService(
            stocks, images, payments, sales, businessHours, prices, promotions, storage);

    @Test
    void historialUsaSoloSucursalYClienteVinculados() {
        CrmWhatsappConversation conversation = conversation(1, 3, 9);
        Venta sale = new Venta();
        sale.setIdVenta(40);
        sale.setFecha(LocalDateTime.of(2026, 9, 20, 10, 0));
        sale.setTipoComprobante("BOLETA");
        sale.setSerie("B001");
        sale.setCorrelativo(25);
        sale.setMoneda("PEN");
        sale.setTotal(BigDecimal.valueOf(120));
        sale.setEstado("EMITIDA");
        when(sales.buscarConFiltros(isNull(), eq(3), isNull(), eq(9), isNull(), isNull(), isNull(), any()))
                .thenReturn(new PageImpl<>(List.of(sale)));

        var result = service.clientSales(conversation, "");

        assertEquals(1, result.sales().size());
        assertEquals("B001-25", result.sales().getFirst().receipt());
        verify(sales).buscarConFiltros(isNull(), eq(3), isNull(), eq(9), isNull(), isNull(), isNull(), any());
    }

    @Test
    void rechazaClienteDeOtraEmpresa() {
        CrmWhatsappConversation conversation = conversation(1, 3, 9);
        Empresa other = new Empresa();
        other.setIdEmpresa(2);
        conversation.getCliente().setEmpresa(other);

        assertThrows(IllegalStateException.class, () -> service.currentClient(conversation));
    }

    @Test
    void horarioSiempreRequiereConfirmacionHumana() {
        var result = service.branchDetails(conversation(1, 3, null));

        assertEquals(true, result.scheduleRequiresAdvisor());
        assertEquals("El horario comercial debe confirmarse con un asesor", result.scheduleMessage());
    }

    @Test
    void corrigeNombreDeProductoConUnaLetraDiferente() {
        EcommerceProductNameView melissa = mock(EcommerceProductNameView.class);
        EcommerceProductNameView belinda = mock(EcommerceProductNameView.class);
        when(melissa.getProductId()).thenReturn(20);
        when(melissa.getProductName()).thenReturn("MELISSA");
        when(belinda.getProductId()).thenReturn(21);
        when(belinda.getProductName()).thenReturn("BELINDA");

        var match = CrmWhatsappAiCommercialQueryService.closestProductName(
                "Quiero Melisa color beige XS", List.of(melissa, belinda));

        assertEquals(true, match.isPresent());
        assertEquals(20, match.orElseThrow().productId());
        assertEquals("MELISSA", match.orElseThrow().productName());
    }

    @Test
    void resuelveNombreCompuestoExactoSinConfundirOtroModelo() {
        var result = CrmWhatsappAiCommercialQueryService.resolveProductName(
                "Tablas de medidas de Alessia Entero",
                List.of(productName(30, "ALESSIA ENTERO"), productName(31, "ALESSIA RAYAS"),
                        productName(32, "ALICE LISO")));

        assertEquals("EXACT", result.status());
        assertEquals(30, result.matches().getFirst().productId());
    }

    @Test
    void reconoceErrorDeEscrituraEnNombreCompuesto() {
        var result = CrmWhatsappAiCommercialQueryService.resolveProductName(
                "Guia de tallas de Alissia entero",
                List.of(productName(30, "ALESSIA ENTERO"), productName(31, "ALESSIA RAYAS"),
                        productName(32, "ALICE LISO")));

        assertEquals("FUZZY", result.status());
        assertEquals(30, result.matches().getFirst().productId());
    }

    @Test
    void solicitaPrecisionCuandoElNombreCorrespondeAVariosModelos() {
        var result = CrmWhatsappAiCommercialQueryService.resolveProductName(
                "Guia de tallas de Alessia",
                List.of(productName(30, "ALESSIA ENTERO"), productName(31, "ALESSIA RAYAS"),
                        productName(32, "ALICE LISO")));

        assertEquals("AMBIGUOUS", result.status());
        assertEquals(List.of("ALESSIA ENTERO", "ALESSIA RAYAS"),
                result.matches().stream().map(match -> match.productName()).toList());
    }

    @Test
    void resuelveUnaParteDistintivaDelNombreCompuesto() {
        var result = CrmWhatsappAiCommercialQueryService.resolveProductName(
                "Entero",
                List.of(productName(30, "ALESSIA ENTERO"), productName(31, "ALESSIA RAYAS"),
                        productName(32, "ALICE LISO")));

        assertEquals("FUZZY", result.status());
        assertEquals(30, result.matches().getFirst().productId());
    }

    private EcommerceProductNameView productName(int id, String name) {
        EcommerceProductNameView product = mock(EcommerceProductNameView.class);
        when(product.getProductId()).thenReturn(id);
        when(product.getProductName()).thenReturn(name);
        return product;
    }

    @Test
    void promocionesIncluyenSoloCombosConProductosDisponiblesEnLaSucursal() {
        when(stocks.listarIdsProductosEcommerceDisponiblesParaIa(eq(3), any()))
                .thenReturn(List.of(10, 11));
        EcommerceInicioComboResponse valid = combo(1, "Combo disponible", 10, 11);
        EcommerceInicioComboResponse unavailable = combo(2, "Combo sin stock", 10, 12);
        when(promotions.listarPublicas(0, 24)).thenReturn(new PagedResponse<>(
                List.of(valid, unavailable), 0, 24, 1, 2, 2, true, true, false));

        var result = service.promotions(conversation(1, 3, null), "", 0);

        assertEquals(1, result.promotions().size());
        assertEquals("Combo disponible", result.promotions().getFirst().name());
    }

    private EcommerceInicioComboResponse combo(int id, String name, int firstProduct, int secondProduct) {
        return new EcommerceInicioComboResponse(id, name, "1 + 1", BigDecimal.valueOf(120),
                BigDecimal.valueOf(140), BigDecimal.valueOf(20), List.of(
                        new EcommerceInicioComboResponse.Item(firstProduct, "Producto A", "a", 1, null, null),
                        new EcommerceInicioComboResponse.Item(secondProduct, "Producto B", "b", 1, null, null)));
    }

    private CrmWhatsappConversation conversation(int companyId, int branchId, Integer clientId) {
        Empresa company = new Empresa();
        company.setIdEmpresa(companyId);
        Sucursal branch = new Sucursal();
        branch.setIdSucursal(branchId);
        branch.setNombre("Kiments Centro");
        branch.setEstado("ACTIVO");
        branch.setEmpresa(company);
        CrmWhatsappConnection connection = new CrmWhatsappConnection();
        connection.setEmpresa(company);
        connection.setSucursal(branch);
        CrmWhatsappConversation conversation = new CrmWhatsappConversation();
        conversation.setConnection(connection);
        if (clientId != null) {
            Cliente client = new Cliente();
            client.setIdCliente(clientId);
            client.setEmpresa(company);
            client.setNombres("Cliente seguro");
            conversation.setCliente(client);
        }
        return conversation;
    }
}
