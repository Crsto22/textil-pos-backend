package com.sistemapos.sistematextil.services;

import java.time.LocalDateTime;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sistemapos.sistematextil.model.ComprobanteConfig;
import com.sistemapos.sistematextil.model.CrmWhatsappAiSaleDraft;
import com.sistemapos.sistematextil.model.CrmWhatsappPaymentRequest;
import com.sistemapos.sistematextil.model.CrmWhatsappPaymentRequestStatus;
import com.sistemapos.sistematextil.model.MetodoPagoCuenta;
import com.sistemapos.sistematextil.model.SucursalMetodoPagoConfig;
import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.repositories.ComprobanteConfigRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappPaymentRequestRepository;
import com.sistemapos.sistematextil.repositories.SucursalMetodoPagoConfigRepository;
import com.sistemapos.sistematextil.repositories.UsuarioRepository;
import com.sistemapos.sistematextil.services.CrmWhatsappChatService.CrmSaleCreateRequest;
import com.sistemapos.sistematextil.util.crm.CrmWhatsappPhoneUtils;
import com.sistemapos.sistematextil.util.venta.VentaDetalleCreateItem;
import com.sistemapos.sistematextil.util.venta.VentaPagoCreateItem;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CrmWhatsappAiPaymentService {
    private static final List<CrmWhatsappPaymentRequestStatus> ACTIVE = List.of(
            CrmWhatsappPaymentRequestStatus.PENDING_EVIDENCE,
            CrmWhatsappPaymentRequestStatus.UNDER_REVIEW,
            CrmWhatsappPaymentRequestStatus.READY_FOR_SALE);

    private final CrmWhatsappPaymentRequestRepository requestRepository;
    private final SucursalMetodoPagoConfigRepository paymentRepository;
    private final ComprobanteConfigRepository comprobanteRepository;
    private final UsuarioRepository usuarioRepository;
    private final CrmWhatsappPaymentReservationService reservationService;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Transactional
    public PaymentInstructions create(CrmWhatsappAiSaleDraft draft, MetodoPagoCuenta account) {
        if (draft == null || draft.getConversation() == null || draft.getSucursal() == null
                || draft.getMetodoPago() == null || draft.getItems().isEmpty()) {
            throw new IllegalStateException("El pedido confirmado no esta completo");
        }
        if (draft.getConfirmedVersion() == null || !draft.getConfirmedVersion().equals(draft.getVersion())) {
            throw new IllegalStateException("El cliente debe confirmar la version actual del pedido");
        }
        Integer methodId = draft.getMetodoPago().getIdMetodoPago();
        SucursalMetodoPagoConfig payment = paymentRepository
                .findBySucursal_IdSucursalAndMetodoPago_IdMetodoPagoAndDeletedAtIsNull(
                        draft.getSucursal().getIdSucursal(), methodId)
                .filter(item -> "ACTIVO".equalsIgnoreCase(item.getEstado()))
                .orElseThrow(() -> new IllegalStateException("El metodo de pago ya no esta disponible"));
        if (account == null || account.getMetodoPago() == null
                || !methodId.equals(account.getMetodoPago().getIdMetodoPago())) {
            throw new IllegalStateException("Selecciona una cuenta valida para continuar");
        }

        Usuario systemUser = aiSystemUser();
        for (CrmWhatsappPaymentRequest existing : requestRepository
                .findByConversation_IdConversationAndStatusInOrderByCreatedAtDesc(
                        draft.getConversation().getIdConversation(), ACTIVE)) {
            reservationService.release(existing, systemUser, "Nueva solicitud de pago");
            existing.setStatus(CrmWhatsappPaymentRequestStatus.CANCELLED);
            requestRepository.save(existing);
        }

        ComprobanteConfig receipt = defaultReceipt();
        List<VentaDetalleCreateItem> details = draft.getItems().stream()
                .map(item -> new VentaDetalleCreateItem(item.getVariantId(), description(item), item.getQuantity(),
                        "NIU", "10", item.getUnitPrice().doubleValue(), 0.0))
                .toList();
        List<VentaPagoCreateItem> payments = List.of(new VentaPagoCreateItem(
                methodId, draft.getTotal().doubleValue(), null, null));
        CrmSaleCreateRequest sale = new CrmSaleCreateRequest(
                draft.getSucursal().getIdSucursal(), receipt.getIdComprobante(), "PEN", "CONTADO", 18.0,
                draft.getPromotionDiscount().doubleValue(),
                draft.getPromotionDiscount().signum() > 0 ? "MONTO" : null, details, payments,
                CrmWhatsappPhoneUtils.normalizePeruvianMobile(draft.getConversation().getPhoneNumber()),
                draft.getIdAiSaleDraft(), draft.getVersion());

        CrmWhatsappPaymentRequest request = new CrmWhatsappPaymentRequest();
        request.setConversation(draft.getConversation());
        request.setConnection(draft.getConnection());
        request.setSucursal(draft.getSucursal());
        request.setCliente(draft.getConversation().getCliente());
        request.setMetodoPago(payment.getMetodoPago());
        request.setCuenta(account);
        request.setCreatedBy(systemUser);
        request.setExpectedAmount(draft.getTotal());
        request.setCurrency("PEN");
        request.setSaleRequestJson(writeJson(sale));
        request.setAiSaleDraft(draft);
        request.setAiSaleDraftVersion(draft.getVersion());
        request.setStatus(CrmWhatsappPaymentRequestStatus.PENDING_EVIDENCE);
        request.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        request = requestRepository.saveAndFlush(request);
        reservationService.reserve(request, draft.getItems(), systemUser);
        return new PaymentInstructions(request, instructions(request));
    }

    @Transactional
    public void cancelForDraft(Long draftId, String reason) {
        if (draftId == null) return;
        CrmWhatsappPaymentRequest request = requestRepository
                .findFirstByAiSaleDraft_IdAiSaleDraftAndStatusInOrderByCreatedAtDesc(draftId, ACTIVE)
                .orElse(null);
        if (request == null) return;
        Usuario systemUser = aiSystemUser();
        reservationService.release(request, systemUser, reason);
        request.setStatus(CrmWhatsappPaymentRequestStatus.CANCELLED);
        requestRepository.save(request);
    }

    @Transactional(readOnly = true)
    public LocalDateTime activeRequestExpiry(Long draftId) {
        if (draftId == null) return null;
        return requestRepository.findFirstByAiSaleDraft_IdAiSaleDraftAndStatusInOrderByCreatedAtDesc(draftId, ACTIVE)
                .map(CrmWhatsappPaymentRequest::getExpiresAt)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public String pendingEvidenceReminder(Long conversationId) {
        if (conversationId == null) return "";
        CrmWhatsappPaymentRequest request = requestRepository
                .findFirstByConversation_IdConversationAndStatusInOrderByCreatedAtDesc(
                        conversationId, List.of(CrmWhatsappPaymentRequestStatus.PENDING_EVIDENCE))
                .orElse(null);
        if (request == null || request.getExpiresAt() == null
                || request.getExpiresAt().isBefore(LocalDateTime.now())) return "";
        return "No se puede procesar tu pago con ese mensaje. Intenta otra vez enviando una captura clara "
                + "con el monto exacto de S/"
                + request.getExpectedAmount().setScale(2, RoundingMode.HALF_UP).toPlainString() + ".";
    }

    @Transactional(readOnly = true)
    public boolean hasActivePaymentFlow(Long conversationId) {
        if (conversationId == null) return false;
        return requestRepository.findFirstByConversation_IdConversationAndStatusInOrderByCreatedAtDesc(
                        conversationId, ACTIVE)
                .filter(request -> request.getExpiresAt() == null || !request.getExpiresAt().isBefore(LocalDateTime.now()))
                .isPresent();
    }

    private ComprobanteConfig defaultReceipt() {
        return comprobanteRepository
                .findTopByTipoComprobanteAndDeletedAtIsNullAndActivoOrderByIdComprobanteAsc("NOTA DE VENTA", "ACTIVO")
                .filter(item -> Boolean.TRUE.equals(item.getHabilitadoVenta()))
                .orElseGet(() -> comprobanteRepository.buscar("ACTIVO", true).stream()
                        .filter(item -> !"FACTURA".equalsIgnoreCase(item.getTipoComprobante()))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("No existe un comprobante de venta habilitado")));
    }

    private String instructions(CrmWhatsappPaymentRequest request) {
        String method = clean(request.getMetodoPago().getNombre()).toUpperCase(Locale.ROOT);
        StringBuilder text = new StringBuilder("💳 *Pago por ").append(method).append("*\n");
        if (!clean(request.getCuenta().getTitular()).isBlank()) {
            text.append("👤 Titular: ").append(clean(request.getCuenta().getTitular())).append("\n");
        }
        text.append("🔢 Cuenta o número: ").append(clean(request.getCuenta().getNumeroCuenta())).append("\n")
                .append("💰 Importe exacto: S/").append(request.getExpectedAmount().setScale(2)).append("\n\n")
                .append("Cuando realices el pago, envíanos una fotito clara del comprobante dentro de los próximos 10 minutos, por favor. ")
                .append("La captura será analizada automáticamente para registrar el comprobante.");
        return text.toString();
    }

    private String description(com.sistemapos.sistematextil.model.CrmWhatsappAiSaleDraftItem item) {
        return (clean(item.getProductName()) + " " + clean(item.getColor()) + " " + clean(item.getSize())).trim();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception error) {
            throw new IllegalStateException("No se pudo preparar la solicitud de venta", error);
        }
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private Usuario aiSystemUser() {
        return usuarioRepository.findByCorreoAndDeletedAtIsNull("ia-kiments@system.local")
                .orElseGet(() -> usuarioRepository.findByCorreoAndDeletedAtIsNull("sistema@gmail.com")
                        .orElseThrow(() -> new IllegalStateException("Usuario IA Kiments no configurado")));
    }

    public record PaymentInstructions(CrmWhatsappPaymentRequest request, String message) {}
}
