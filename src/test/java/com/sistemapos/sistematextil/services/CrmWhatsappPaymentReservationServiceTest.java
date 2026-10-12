package com.sistemapos.sistematextil.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.sistemapos.sistematextil.model.CrmWhatsappPaymentRequest;
import com.sistemapos.sistematextil.model.CrmWhatsappPaymentReservationStatus;
import com.sistemapos.sistematextil.repositories.CrmWhatsappPaymentRequestItemRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappPaymentRequestRepository;

class CrmWhatsappPaymentReservationServiceTest {
    @Test
    void marksConsumedReservationAsReversedAfterOfficialSaleCancellation() {
        CrmWhatsappPaymentRequestRepository requests = mock(CrmWhatsappPaymentRequestRepository.class);
        CrmWhatsappPaymentRequestItemRepository items = mock(CrmWhatsappPaymentRequestItemRepository.class);
        StockMovimientoService stock = mock(StockMovimientoService.class);
        CrmWhatsappPaymentRequest request = new CrmWhatsappPaymentRequest();
        request.setIdPaymentRequest(77L);
        request.setReservationStatus(CrmWhatsappPaymentReservationStatus.CONSUMED);
        when(requests.findForUpdate(77L)).thenReturn(Optional.of(request));

        CrmWhatsappPaymentReservationService service =
                new CrmWhatsappPaymentReservationService(requests, items, stock);
        service.markReversedAfterSaleCancellation(request, "Pago rechazado por revision humana");

        assertEquals(CrmWhatsappPaymentReservationStatus.REVERSED, request.getReservationStatus());
        assertEquals("Pago rechazado por revision humana", request.getReleaseReason());
        verify(requests).save(request);
    }
}
