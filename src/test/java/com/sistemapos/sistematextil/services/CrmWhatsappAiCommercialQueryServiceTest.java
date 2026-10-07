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
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;

import com.sistemapos.sistematextil.model.Cliente;
import com.sistemapos.sistematextil.model.CrmWhatsappConnection;
import com.sistemapos.sistematextil.model.CrmWhatsappConversation;
import com.sistemapos.sistematextil.model.Empresa;
import com.sistemapos.sistematextil.model.Producto;
import com.sistemapos.sistematextil.model.ProductoVariante;
import com.sistemapos.sistematextil.model.ProductoVarianteOfertaSucursal;
import com.sistemapos.sistematextil.model.Sucursal;
import com.sistemapos.sistematextil.model.SucursalStock;
import com.sistemapos.sistematextil.model.Venta;
import com.sistemapos.sistematextil.repositories.ProductoColorImagenRepository;
import com.sistemapos.sistematextil.repositories.SucursalMetodoPagoConfigRepository;
import com.sistemapos.sistematextil.repositories.SucursalStockRepository;
import com.sistemapos.sistematextil.repositories.SucursalStockRepository.EcommerceProductNameView;
import com.sistemapos.sistematextil.repositories.VentaRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappBusinessHoursRepository;
import com.sistemapos.sistematextil.util.ecommerce.EcommerceInicioComboResponse;
import com.sistemapos.sistematextil.util.paginacion.PagedResponse;
import com.sistemapos.sistematextil.util.producto.TipoOfertaAplicada;

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
    void resuelveEnterizoAlessiaComoAlessiaEnteroAunqueCambieElOrden() {
        var result = CrmWhatsappAiCommercialQueryService.resolveProductName(
                "Enterizo Alessia",
                List.of(productName(30, "ALESSIA ENTERO"), productName(31, "ALESSIA RAYAS")));

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

    @Test
    void promocionesSePaginanDeDiezYCalculanSusExtremos() {
        when(stocks.listarIdsProductosEcommerceDisponiblesParaIa(eq(3), any()))
                .thenReturn(List.of(10, 11));
        List<EcommerceInicioComboResponse> values = java.util.stream.IntStream.rangeClosed(1, 12)
                .mapToObj(index -> combo(index, "Combo " + index, 100 + index, 10, 11))
                .toList();
        when(promotions.listarPublicas(0, 24)).thenReturn(new PagedResponse<>(
                values, 0, 24, 1, 12, 12, true, true, false));

        var first = service.promotions(conversation(1, 3, null), "", 0);
        var second = service.promotions(conversation(1, 3, null), "", 1);

        assertEquals(10, first.promotions().size());
        assertEquals(true, first.hasMore());
        assertEquals(2, second.promotions().size());
        assertEquals(false, second.hasMore());
        assertEquals("Combo 1", first.cheapest().name());
        assertEquals("Combo 12", first.mostExpensive().name());
    }

    @Test
    void filtraComboPorNumeroAunqueNoEsteEntreLosPrimerosDiez() {
        when(stocks.listarIdsProductosEcommerceDisponiblesParaIa(eq(3), any()))
                .thenReturn(List.of(10, 11));
        List<EcommerceInicioComboResponse> values = java.util.stream.IntStream.rangeClosed(1, 30)
                .mapToObj(index -> combo(index, "combo " + index, 100 + index, 10, 11))
                .toList();
        when(promotions.listarPublicas(0, 24)).thenReturn(new PagedResponse<>(
                values.subList(0, 24), 0, 24, 2, 30, 24, true, false, false));
        when(promotions.listarPublicas(1, 24)).thenReturn(new PagedResponse<>(
                values.subList(24, 30), 1, 24, 2, 30, 6, false, true, false));

        var result = service.promotions(conversation(1, 3, null), "", 0, null, 28);

        assertEquals(1, result.promotions().size());
        assertEquals("combo 28", result.promotions().getFirst().name());
    }

    @Test
    void filtraPromocionesDeDosUnidadesDelMismoProductoAntesDePaginar() {
        when(stocks.listarIdsProductosEcommerceDisponiblesParaIa(eq(3), any()))
                .thenReturn(List.of(10, 11));
        EcommerceInicioComboResponse mixed = combo(1, "Combo mixto", 10, 11);
        EcommerceInicioComboResponse sameProduct = new EcommerceInicioComboResponse(
                28, "combo 28", "2 iguales", BigDecimal.valueOf(120),
                BigDecimal.valueOf(130), BigDecimal.valueOf(10), List.of(
                        new EcommerceInicioComboResponse.Item(10, "EMMA", "emma", 2, null, null)));
        when(promotions.listarPublicas(0, 24)).thenReturn(new PagedResponse<>(
                List.of(mixed, sameProduct), 0, 24, 1, 2, 2, true, true, false));

        var result = service.promotions(conversation(1, 3, null), "", 0, 2, null);

        assertEquals(1, result.promotions().size());
        assertEquals("combo 28", result.promotions().getFirst().name());
        assertEquals(2, result.promotions().getFirst().products().getFirst().quantity());
    }

    @Test
    void encuentraPromocionExactaDeDosUnidadesDelProducto() {
        when(stocks.listarIdsProductosEcommerceDisponiblesParaIa(eq(3), any()))
                .thenReturn(List.of(10, 11));
        EcommerceInicioComboResponse mixed = combo(1, "Combo mixto", 10, 11);
        EcommerceInicioComboResponse twoEmma = new EcommerceInicioComboResponse(
                28, "combo 28", "2 iguales", BigDecimal.valueOf(120),
                BigDecimal.valueOf(130), BigDecimal.valueOf(10), List.of(
                        new EcommerceInicioComboResponse.Item(10, "EMMA", "emma", 2, null, null)));
        when(promotions.listarPublicas(0, 24)).thenReturn(new PagedResponse<>(
                List.of(mixed, twoEmma), 0, 24, 1, 2, 2, true, true, false));

        var result = service.sameProductPromotion(conversation(1, 3, null), 10, 2);

        assertEquals(28, result.promotionId());
        assertEquals(new BigDecimal("120"), result.comboPrice());
    }

    @Test
    void obtieneProductoMasEconomicoYMasCaroConPrecioVigente() {
        CrmWhatsappConversation conversation = conversation(1, 3, null);
        SucursalStock economical = stock(10, 100, "EMMA", 65d);
        SucursalStock expensive = stock(11, 101, "ALESSIA", 75d);
        when(stocks.listarIdsProductosEcommerceDisponiblesParaIa(eq(3), any()))
                .thenReturn(List.of(100, 101));
        when(stocks.listarVariantesEcommerceDisponiblesParaIa(eq(3), eq(List.of(100, 101))))
                .thenReturn(List.of(economical, expensive));
        when(prices.obtenerOfertasSucursalPorVariantes(any(), eq(3))).thenReturn(Map.of());
        when(prices.resolver(eq(economical.getProductoVariante()),
                org.mockito.ArgumentMatchers.<ProductoVarianteOfertaSucursal>isNull()))
                .thenReturn(price(65d));
        when(prices.resolver(eq(expensive.getProductoVariante()),
                org.mockito.ArgumentMatchers.<ProductoVarianteOfertaSucursal>isNull()))
                .thenReturn(price(75d));

        var cheapest = service.productPriceExtreme(conversation, false);
        var highest = service.productPriceExtreme(conversation, true);

        assertEquals("EMMA", cheapest.product().name());
        assertEquals(new BigDecimal("65.0"), cheapest.product().price());
        assertEquals("ALESSIA", highest.product().name());
        assertEquals(new BigDecimal("75.0"), highest.product().price());
    }

    @Test
    void entregaInmediataConsultaSoloNoPreventaConLaTallaSolicitada() {
        when(stocks.listarIdsProductosEcommerceEntregaInmediataParaIa(eq(3), eq("M"), any()))
                .thenReturn(List.of());

        var result = service.searchReadyStockProducts(conversation(1, 3, null), "m", 0);

        assertEquals(0, result.products().size());
        verify(stocks).listarIdsProductosEcommerceEntregaInmediataParaIa(eq(3), eq("M"), any());
    }

    private EcommerceInicioComboResponse combo(int id, String name, int firstProduct, int secondProduct) {
        return combo(id, name, 120, firstProduct, secondProduct);
    }

    private EcommerceInicioComboResponse combo(
            int id, String name, int price, int firstProduct, int secondProduct) {
        return new EcommerceInicioComboResponse(id, name, "1 + 1", BigDecimal.valueOf(price),
                BigDecimal.valueOf(140), BigDecimal.valueOf(20), List.of(
                        new EcommerceInicioComboResponse.Item(firstProduct, "Producto A", "a", 1, null, null),
                        new EcommerceInicioComboResponse.Item(secondProduct, "Producto B", "b", 1, null, null)));
    }

    private SucursalStock stock(int variantId, int productId, String name, double price) {
        Producto product = new Producto();
        product.setIdProducto(productId);
        product.setNombre(name);
        ProductoVariante variant = new ProductoVariante();
        variant.setIdProductoVariante(variantId);
        variant.setProducto(product);
        SucursalStock stock = new SucursalStock();
        stock.setProductoVariante(variant);
        stock.setCantidad(1);
        return stock;
    }

    private PrecioOfertaService.ResultadoPrecioOferta price(double value) {
        return new PrecioOfertaService.ResultadoPrecioOferta(
                value, value, TipoOfertaAplicada.NINGUNA,
                null, null, null, null);
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
