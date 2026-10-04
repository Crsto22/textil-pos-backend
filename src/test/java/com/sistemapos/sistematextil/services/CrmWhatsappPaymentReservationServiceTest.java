package com.sistemapos.sistematextil.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sistemapos.sistematextil.model.CrmWhatsappAiSaleDraftItem;
import com.sistemapos.sistematextil.model.CrmWhatsappPaymentRequest;
import com.sistemapos.sistematextil.model.CrmWhatsappPaymentRequestItem;
import com.sistemapos.sistematextil.model.CrmWhatsappPaymentReservationStatus;
import com.sistemapos.sistematextil.model.HistorialStock;
import com.sistemapos.sistematextil.model.ProductoVariante;
import com.sistemapos.sistematextil.model.Sucursal;
import com.sistemapos.sistematextil.model.SucursalStock;
import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.repositories.CrmWhatsappPaymentRequestItemRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappPaymentRequestRepository;

@ExtendWith(MockitoExtension.class)
class CrmWhatsappPaymentReservationServiceTest {
    @Mock private CrmWhatsappPaymentRequestRepository requestRepository;
    @Mock private CrmWhatsappPaymentRequestItemRepository itemRepository;
    @Mock private StockMovimientoService stockMovimientoService;
    @InjectMocks private CrmWhatsappPaymentReservationService service;

    @Test
    void reserveDiscountsStockAndPersistsSnapshot() {
        Usuario actor = new Usuario();
        CrmWhatsappPaymentRequest request = request(10L, CrmWhatsappPaymentReservationStatus.LEGACY_NONE);
        CrmWhatsappAiSaleDraftItem draftItem = new CrmWhatsappAiSaleDraftItem();
        draftItem.setVariantId(25);
        draftItem.setProductName("Producto");
        draftItem.setQuantity(2);
        draftItem.setUnitPrice(new BigDecimal("49.90"));
        ProductoVariante variant = new ProductoVariante();
        variant.setIdProductoVariante(25);
        SucursalStock stock = new SucursalStock();
        stock.setProductoVariante(variant);
        when(stockMovimientoService.obtenerContextoConBloqueo(3, 25))
                .thenReturn(new StockMovimientoService.StockContexto(stock, 5));

        service.reserve(request, List.of(draftItem), actor);

        verify(stockMovimientoService).descontar(eq(3), eq(25), eq(2),
                eq(HistorialStock.TipoMovimiento.RESERVA), any(), eq(actor));
        verify(itemRepository).save(any(CrmWhatsappPaymentRequestItem.class));
        assertEquals(CrmWhatsappPaymentReservationStatus.ACTIVE, request.getReservationStatus());
    }

    @Test
    void releaseIsIdempotent() {
        Usuario actor = new Usuario();
        CrmWhatsappPaymentRequest request = request(11L, CrmWhatsappPaymentReservationStatus.ACTIVE);
        ProductoVariante variant = new ProductoVariante();
        variant.setIdProductoVariante(30);
        CrmWhatsappPaymentRequestItem item = new CrmWhatsappPaymentRequestItem();
        item.setProductoVariante(variant);
        item.setCantidad(3);
        when(requestRepository.findForUpdate(11L)).thenReturn(Optional.of(request));
        when(itemRepository.findByPaymentRequest_IdPaymentRequestOrderByIdPaymentRequestItemAsc(11L))
                .thenReturn(List.of(item));

        assertTrue(service.release(request, actor, "rechazado"));
        assertFalse(service.release(request, actor, "repetido"));

        verify(stockMovimientoService).incrementar(eq(3), eq(30), eq(3),
                eq(HistorialStock.TipoMovimiento.LIBERACION), any(), eq(actor));
        assertEquals(CrmWhatsappPaymentReservationStatus.RELEASED, request.getReservationStatus());
    }

    @Test
    void releaseDoesNotTouchLegacyRequests() {
        Usuario actor = new Usuario();
        CrmWhatsappPaymentRequest request = request(12L, CrmWhatsappPaymentReservationStatus.LEGACY_NONE);
        when(requestRepository.findForUpdate(12L)).thenReturn(Optional.of(request));

        assertFalse(service.release(request, actor, "legacy"));
        verify(stockMovimientoService, never()).incrementar(any(), any(), any(), any(), any(), any());
    }

    private CrmWhatsappPaymentRequest request(Long id, CrmWhatsappPaymentReservationStatus status) {
        Sucursal branch = new Sucursal();
        branch.setIdSucursal(3);
        CrmWhatsappPaymentRequest request = new CrmWhatsappPaymentRequest();
        request.setIdPaymentRequest(id);
        request.setSucursal(branch);
        request.setReservationStatus(status);
        return request;
    }
}
