package com.sistemapos.sistematextil.services;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sistemapos.sistematextil.model.CrmWhatsappAiSaleDraftItem;
import com.sistemapos.sistematextil.model.CrmWhatsappPaymentRequest;
import com.sistemapos.sistematextil.model.CrmWhatsappPaymentRequestItem;
import com.sistemapos.sistematextil.model.CrmWhatsappPaymentReservationStatus;
import com.sistemapos.sistematextil.model.HistorialStock;
import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.repositories.CrmWhatsappPaymentRequestItemRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappPaymentRequestRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CrmWhatsappPaymentReservationService {
    private final CrmWhatsappPaymentRequestRepository requestRepository;
    private final CrmWhatsappPaymentRequestItemRepository itemRepository;
    private final StockMovimientoService stockMovimientoService;

    @Transactional
    public void reserve(CrmWhatsappPaymentRequest request, List<CrmWhatsappAiSaleDraftItem> draftItems, Usuario actor) {
        if (request == null || request.getIdPaymentRequest() == null || draftItems == null || draftItems.isEmpty()) {
            throw new IllegalArgumentException("La solicitud no contiene productos para reservar");
        }
        if (request.getReservationStatus() == CrmWhatsappPaymentReservationStatus.ACTIVE) return;
        if (request.getReservationStatus() != CrmWhatsappPaymentReservationStatus.LEGACY_NONE) {
            throw new IllegalStateException("La reserva ya fue procesada");
        }

        Integer branchId = request.getSucursal().getIdSucursal();
        List<CrmWhatsappAiSaleDraftItem> ordered = draftItems.stream()
                .sorted(Comparator.comparing(CrmWhatsappAiSaleDraftItem::getVariantId))
                .toList();
        List<StockMovimientoService.StockContexto> locked = new ArrayList<>();
        for (CrmWhatsappAiSaleDraftItem item : ordered) {
            var context = stockMovimientoService.obtenerContextoConBloqueo(branchId, item.getVariantId());
            if (context.stockActual() < item.getQuantity()) {
                throw new IllegalStateException("Stock insuficiente para " + item.getProductName()
                        + ". Disponible: " + context.stockActual());
            }
            locked.add(context);
        }

        for (int index = 0; index < ordered.size(); index++) {
            CrmWhatsappAiSaleDraftItem source = ordered.get(index);
            var variant = locked.get(index).sucursalStock().getProductoVariante();
            stockMovimientoService.descontar(branchId, source.getVariantId(), source.getQuantity(),
                    HistorialStock.TipoMovimiento.RESERVA,
                    "Reserva pago WhatsApp #" + request.getIdPaymentRequest(), actor);
            CrmWhatsappPaymentRequestItem item = new CrmWhatsappPaymentRequestItem();
            item.setPaymentRequest(request);
            item.setProductoVariante(variant);
            item.setCantidad(source.getQuantity());
            item.setUnitPrice(source.getUnitPrice());
            itemRepository.save(item);
        }
        request.setReservationStatus(CrmWhatsappPaymentReservationStatus.ACTIVE);
        request.setReservedAt(LocalDateTime.now());
        requestRepository.save(request);
    }

    @Transactional
    public boolean release(CrmWhatsappPaymentRequest request, Usuario actor, String reason) {
        if (request == null || request.getIdPaymentRequest() == null) return false;
        CrmWhatsappPaymentRequest locked = requestRepository.findForUpdate(request.getIdPaymentRequest()).orElse(null);
        if (locked == null || locked.getReservationStatus() != CrmWhatsappPaymentReservationStatus.ACTIVE) return false;
        for (CrmWhatsappPaymentRequestItem item : itemRepository
                .findByPaymentRequest_IdPaymentRequestOrderByIdPaymentRequestItemAsc(locked.getIdPaymentRequest())) {
            stockMovimientoService.incrementar(locked.getSucursal().getIdSucursal(),
                    item.getProductoVariante().getIdProductoVariante(), item.getCantidad(),
                    HistorialStock.TipoMovimiento.LIBERACION,
                    "Liberacion pago WhatsApp #" + locked.getIdPaymentRequest() + ": " + safe(reason), actor);
        }
        locked.setReservationStatus(CrmWhatsappPaymentReservationStatus.RELEASED);
        locked.setReleasedAt(LocalDateTime.now());
        locked.setReleaseReason(limit(reason, 500));
        requestRepository.save(locked);
        return true;
    }

    @Transactional
    public void consume(CrmWhatsappPaymentRequest request) {
        CrmWhatsappPaymentRequest locked = requestRepository.findForUpdate(request.getIdPaymentRequest())
                .orElseThrow(() -> new IllegalStateException("Solicitud de pago no encontrada"));
        if (locked.getReservationStatus() == CrmWhatsappPaymentReservationStatus.CONSUMED) return;
        if (locked.getReservationStatus() != CrmWhatsappPaymentReservationStatus.ACTIVE) {
            throw new IllegalStateException("La reserva de stock ya no esta activa");
        }
        locked.setReservationStatus(CrmWhatsappPaymentReservationStatus.CONSUMED);
        requestRepository.save(locked);
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "sin motivo" : value.trim();
    }

    private String limit(String value, int max) {
        String safe = safe(value);
        return safe.length() <= max ? safe : safe.substring(0, max);
    }
}
