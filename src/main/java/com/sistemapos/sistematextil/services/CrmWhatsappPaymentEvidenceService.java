package com.sistemapos.sistematextil.services;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sistemapos.sistematextil.model.CrmWhatsappConversation;
import com.sistemapos.sistematextil.model.CrmWhatsappMessage;
import com.sistemapos.sistematextil.model.CrmWhatsappPaymentEvidence;
import com.sistemapos.sistematextil.model.CrmWhatsappPaymentEvidenceStatus;
import com.sistemapos.sistematextil.model.CrmWhatsappPaymentProcessingStatus;
import com.sistemapos.sistematextil.model.CrmWhatsappPaymentRequest;
import com.sistemapos.sistematextil.model.CrmWhatsappPaymentRequestStatus;
import com.sistemapos.sistematextil.model.CrmWhatsappPaymentReservationStatus;
import com.sistemapos.sistematextil.model.CrmWhatsappAiAttentionMode;
import com.sistemapos.sistematextil.model.CrmWhatsappAiDeliveryType;
import com.sistemapos.sistematextil.model.CrmWhatsappWaitingReason;
import com.sistemapos.sistematextil.model.MetodoPagoCuenta;
import com.sistemapos.sistematextil.model.SucursalMetodoPagoConfig;
import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.repositories.CrmWhatsappConversationRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappMessageRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappPaymentEvidenceRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappPaymentRequestRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAiConfigRepository;
import com.sistemapos.sistematextil.repositories.MetodoPagoCuentaRepository;
import com.sistemapos.sistematextil.repositories.SucursalMetodoPagoConfigRepository;
import com.sistemapos.sistematextil.repositories.UsuarioRepository;
import com.sistemapos.sistematextil.repositories.VentaRepository;
import com.sistemapos.sistematextil.services.CrmWhatsappChatService.CrmSaleCreateRequest;
import com.sistemapos.sistematextil.services.CrmWhatsappChatService.VentaCrmResponse;
import com.sistemapos.sistematextil.services.ai.AiModelProvider;
import com.sistemapos.sistematextil.services.ai.AiModelProvider.PaymentEvidenceExtraction;
import com.sistemapos.sistematextil.services.ai.AiModelProvider.PaymentEvidenceRequest;
import com.sistemapos.sistematextil.util.usuario.Rol;
import com.sistemapos.sistematextil.util.venta.VentaPagoCreateItem;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CrmWhatsappPaymentEvidenceService {
    private static final long MAX_FILE_BYTES = 10L * 1024L * 1024L;
    private static final List<CrmWhatsappPaymentRequestStatus> ACTIVE_REQUESTS = List.of(
            CrmWhatsappPaymentRequestStatus.PENDING_EVIDENCE,
            CrmWhatsappPaymentRequestStatus.UNDER_REVIEW,
            CrmWhatsappPaymentRequestStatus.READY_FOR_SALE);
    private static final List<CrmWhatsappPaymentRequestStatus> ACCEPTING_EVIDENCE = List.of(
            CrmWhatsappPaymentRequestStatus.PENDING_EVIDENCE,
            CrmWhatsappPaymentRequestStatus.UNDER_REVIEW);

    private final CrmWhatsappPaymentRequestRepository requestRepository;
    private final CrmWhatsappPaymentEvidenceRepository evidenceRepository;
    private final CrmWhatsappConversationRepository conversationRepository;
    private final CrmWhatsappMessageRepository messageRepository;
    private final SucursalMetodoPagoConfigRepository branchPaymentRepository;
    private final MetodoPagoCuentaRepository accountRepository;
    private final UsuarioRepository usuarioRepository;
    private final VentaRepository ventaRepository;
    private final S3StorageService storageService;
    private final AiModelProvider aiModelProvider;
    private final CrmWhatsappEventService eventService;
    private final ObjectProvider<CrmWhatsappChatService> chatServiceProvider;
    private final CrmWhatsappAiSaleDraftService aiSaleDraftService;
    private final CrmWhatsappAiConfigRepository aiConfigRepository;
    private final CrmWhatsappPaymentReservationService reservationService;
    private final CrmWhatsappAiDeliveryService deliveryService;
    private final CrmWhatsappAiMemoryService aiMemoryService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public PaymentRequestResponse createRequest(Long conversationId, PaymentRequestCreateRequest body, Usuario sessionUser) {
        Usuario actor = requireCrmUser(sessionUser);
        CrmWhatsappConversation conversation = requireConversation(conversationId);
        requireCanOperate(conversation, actor);
        if (body == null || body.sale() == null) throw badRequest("Datos de venta requeridos");
        if (conversation.getConnection() == null || conversation.getConnection().getSucursal() == null) {
            throw badRequest("La conexion de WhatsApp no tiene sucursal configurada");
        }
        CrmSaleCreateRequest sale = body.sale();
        Integer branchId = conversation.getConnection().getSucursal().getIdSucursal();
        if (sale.idSucursal() != null && !branchId.equals(sale.idSucursal())) {
            throw badRequest("La sucursal no coincide con la conexion de WhatsApp");
        }
        if (sale.pagos() == null || sale.pagos().size() != 1) {
            throw badRequest("La solicitud pendiente admite un metodo de pago");
        }
        VentaPagoCreateItem payment = sale.pagos().get(0);
        aiSaleDraftService.validateSale(conversationId, sale.aiSaleDraftId(), sale.aiSaleDraftVersion(),
                sale.detalles(), payment.idMetodoPago(), sale.descuentoTotal(), sale.tipoDescuento());
        SucursalMetodoPagoConfig config = branchPaymentRepository
                .findBySucursal_IdSucursalAndMetodoPago_IdMetodoPagoAndDeletedAtIsNull(branchId, payment.idMetodoPago())
                .filter(item -> "ACTIVO".equalsIgnoreCase(item.getEstado()))
                .orElseThrow(() -> badRequest("El metodo de pago no esta habilitado para la sucursal"));
        MetodoPagoCuenta account = null;
        if (body.idMetodoPagoCuenta() != null) {
            account = accountRepository.findById(body.idMetodoPagoCuenta())
                    .filter(item -> item.getMetodoPago() != null
                            && item.getMetodoPago().getIdMetodoPago().equals(payment.idMetodoPago())
                            && !Boolean.FALSE.equals(item.getActivo()))
                    .orElseThrow(() -> badRequest("La cuenta no pertenece al metodo de pago"));
        }
        BigDecimal amount = decimal(payment.monto());
        if (amount.signum() <= 0) throw badRequest("El monto debe ser mayor a cero");

        requestRepository.findByConversation_IdConversationAndStatusInOrderByCreatedAtDesc(conversationId, ACTIVE_REQUESTS)
                .forEach(existing -> {
                    reservationService.release(existing, actor, "Solicitud reemplazada manualmente");
                    existing.setStatus(CrmWhatsappPaymentRequestStatus.CANCELLED);
                    requestRepository.save(existing);
                });

        CrmWhatsappPaymentRequest request = new CrmWhatsappPaymentRequest();
        request.setConversation(conversation);
        request.setConnection(conversation.getConnection());
        request.setSucursal(conversation.getConnection().getSucursal());
        request.setCliente(conversation.getCliente());
        request.setMetodoPago(config.getMetodoPago());
        request.setCuenta(account);
        request.setCreatedBy(actor);
        request.setExpectedAmount(amount);
        request.setCurrency(clean(sale.moneda()).isBlank() ? "PEN" : clean(sale.moneda()).toUpperCase(Locale.ROOT));
        request.setSaleRequestJson(writeJson(new CrmSaleCreateRequest(
                branchId, sale.idComprobante(), request.getCurrency(), sale.formaPago(), sale.igvPorcentaje(),
                sale.descuentoTotal(), sale.tipoDescuento(), sale.detalles(), sale.pagos(), sale.telefonoRespaldo(),
                sale.aiSaleDraftId(), sale.aiSaleDraftVersion())));
        request.setStatus(CrmWhatsappPaymentRequestStatus.PENDING_EVIDENCE);
        request.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        request = requestRepository.save(request);
        aiSaleDraftService.markPaymentPending(sale.aiSaleDraftId());
        publish("payment.request.updated", conversation, toResponse(request));
        return toResponse(request);
    }

    @Transactional(readOnly = true)
    public PaymentRequestResponse getActiveRequest(Long conversationId, Usuario sessionUser) {
        Usuario actor = requireCrmUser(sessionUser);
        CrmWhatsappConversation conversation = requireConversation(conversationId);
        requireCanRead(conversation, actor);
        return requestRepository.findFirstByConversation_IdConversationAndStatusInOrderByCreatedAtDesc(
                conversationId, ACTIVE_REQUESTS).map(this::toResponse).orElse(null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registerIncomingMedia(Long messageId) {
        if (messageId == null) return;
        CrmWhatsappMessage message = messageRepository.findForUpdate(messageId).orElse(null);
        if (evidenceRepository.findByMessage_IdMessage(messageId).isPresent()) return;
        if (message == null || !"INCOMING".equals(message.getDirection()) || message.getDeletedAt() != null
                || !isSupported(message)) return;
        var active = requestRepository.findFirstByConversation_IdConversationAndStatusInOrderByCreatedAtDesc(
                message.getConversation().getIdConversation(), ACCEPTING_EVIDENCE);
        if (active.isEmpty()) return;
        createEvidence(message, active.orElse(null), true);
    }

    @Transactional
    public PaymentEvidenceResponse analyzeMessage(Long messageId, Usuario sessionUser) {
        Usuario actor = requireCrmUser(sessionUser);
        CrmWhatsappMessage message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mensaje no encontrado"));
        requireCanOperate(message.getConversation(), actor);
        if (aiStopped(message.getConversation())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La IA esta detenida por un administrador");
        }
        CrmWhatsappPaymentEvidence evidence = evidenceRepository.findByMessage_IdMessage(messageId).orElse(null);
        CrmWhatsappPaymentRequest activeRequest = requestRepository
                .findFirstByConversation_IdConversationAndStatusInOrderByCreatedAtDesc(
                        message.getConversation().getIdConversation(), ACCEPTING_EVIDENCE).orElse(null);
        if (evidence == null) {
            if (activeRequest == null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "No existe una solicitud de pago activa para este archivo");
            }
            evidence = createEvidence(message, activeRequest, false);
        }
        if (evidence.getPaymentRequest() == null) {
            if (activeRequest == null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "La evidencia no tiene una solicitud de pago vinculada");
            }
            evidence.setPaymentRequest(activeRequest);
            evidenceRepository.save(evidence);
        }
        if (evidence.getProcessingStatus() == CrmWhatsappPaymentProcessingStatus.FAILED) {
            evidence.setProcessingStatus(CrmWhatsappPaymentProcessingStatus.QUEUED);
            evidence.setAvailableAt(LocalDateTime.now());
            evidence.setLastError(null);
            evidence = evidenceRepository.save(evidence);
        }
        return toResponse(evidence);
    }

    @Transactional(readOnly = true)
    public List<PaymentEvidenceResponse> list(Long conversationId, Usuario sessionUser) {
        Usuario actor = requireCrmUser(sessionUser);
        CrmWhatsappConversation conversation = requireConversation(conversationId);
        requireCanRead(conversation, actor);
        return evidenceRepository.findByConversation_IdConversationOrderByCreatedAtDesc(conversationId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public PaymentEvidenceResponse get(Long evidenceId, Usuario sessionUser) {
        Usuario actor = requireCrmUser(sessionUser);
        CrmWhatsappPaymentEvidence evidence = evidenceRepository.findById(evidenceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Evidencia no encontrada"));
        requireCanRead(evidence.getConversation(), actor);
        return toResponse(evidence);
    }

    @Transactional(readOnly = true)
    public PaymentEvidenceDownload download(Long evidenceId, Usuario sessionUser) {
        Usuario actor = requireCrmUser(sessionUser);
        CrmWhatsappPaymentEvidence evidence = evidenceRepository.findById(evidenceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Evidencia no encontrada"));
        if (evidence.getConversation() != null) requireCanRead(evidence.getConversation(), actor);
        else if (!actor.getRol().esAdministrador()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sin acceso a la evidencia");
        return new PaymentEvidenceDownload(storageService.readBytes(evidence.getStoragePath()), evidence.getMimeType(),
                clean(evidence.getFileName()).isBlank() ? "evidencia-pago" + extension(evidence.getMimeType()) : evidence.getFileName());
    }

    @Transactional
    public PaymentDecisionResponse decide(Long evidenceId, PaymentDecisionRequest body, Usuario sessionUser) {
        Usuario actor = requireCrmUser(sessionUser);
        CrmWhatsappPaymentEvidence evidence = evidenceRepository.findForUpdate(evidenceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Evidencia no encontrada"));
        String action = clean(body == null ? null : body.action()).toUpperCase(Locale.ROOT);
        if ("ACCEPT".equals(action) || "REJECT".equals(action)) {
            CrmWhatsappConversation assignedConversation = chatServiceProvider.getObject()
                    .asignarParaOperacion(evidence.getConversation().getIdConversation(), actor);
            evidence.setConversation(assignedConversation);
        } else {
            requireCanOperate(evidence.getConversation(), actor);
        }
        if (evidence.getValidationStatus() == CrmWhatsappPaymentEvidenceStatus.VALIDADO
                || evidence.getValidationStatus() == CrmWhatsappPaymentEvidenceStatus.RECHAZADO) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La evidencia ya fue revisada");
        }
        if ("ACCEPT".equals(action)) {
            if (evidence.getProcessingStatus() != CrmWhatsappPaymentProcessingStatus.COMPLETED
                    || evidence.getValidationStatus() != CrmWhatsappPaymentEvidenceStatus.ACEPTABLE
                    || evidence.getPaymentRequest() == null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "El comprobante aun no esta listo para cargar la venta");
            }
            CrmWhatsappPaymentRequest request = requestRepository
                    .findForUpdate(evidence.getPaymentRequest().getIdPaymentRequest()).orElse(null);
            if (request == null || request.getStatus() != CrmWhatsappPaymentRequestStatus.READY_FOR_SALE
                    || request.getReservationStatus() != CrmWhatsappPaymentReservationStatus.ACTIVE
                    || request.getExpiresAt() == null || request.getExpiresAt().isBefore(LocalDateTime.now())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "La reserva del pedido ya no esta disponible");
            }
            review(evidence, actor, CrmWhatsappPaymentEvidenceStatus.ACEPTABLE, body == null ? null : body.note());
            publish("payment.evidence.updated", evidence.getConversation(), toResponse(evidence));
            sendCustomerNotice(evidence.getConversation(), "PEDIDO REGISTRADO ✅");
            return decisionResponse(evidence, null);
        }
        if ("OBSERVE".equals(action)) {
            review(evidence, actor, CrmWhatsappPaymentEvidenceStatus.OBSERVADO, body.note());
            publish("payment.evidence.updated", evidence.getConversation(), toResponse(evidence));
            return decisionResponse(evidence, null);
        }
        if ("REJECT".equals(action)) {
            review(evidence, actor, CrmWhatsappPaymentEvidenceStatus.RECHAZADO, body.note());
            if (evidence.getPaymentRequest() != null) {
                CrmWhatsappPaymentRequest request = requestRepository
                        .findForUpdate(evidence.getPaymentRequest().getIdPaymentRequest()).orElse(null);
                if (request != null && ACTIVE_REQUESTS.contains(request.getStatus())) {
                    reservationService.release(request, actor, "Comprobante rechazado por el asesor");
                    request.setStatus(CrmWhatsappPaymentRequestStatus.CANCELLED);
                    requestRepository.save(request);
                    aiSaleDraftService.markPaymentRejected(request.getAiSaleDraft() == null
                            ? null : request.getAiSaleDraft().getIdAiSaleDraft());
                    publish("payment.request.updated", evidence.getConversation(), toResponse(request));
                }
            }
            publish("payment.evidence.updated", evidence.getConversation(), toResponse(evidence));
            sendCustomerNotice(evidence.getConversation(), "PEDIDO RECHAZADO");
            return decisionResponse(evidence, null);
        }
        if (!"VALIDATE".equals(action)) throw badRequest("Accion permitida: ACCEPT, OBSERVE, REJECT o VALIDATE");
        if (!Boolean.TRUE.equals(body.confirmedExternal())) {
            throw badRequest("Confirma que verificaste el abono por un canal externo");
        }
        if (evidence.getProcessingStatus() != CrmWhatsappPaymentProcessingStatus.COMPLETED) {
            throw badRequest("La evidencia aun no termino de procesarse");
        }
        if (evidence.getPaymentRequest() == null) throw badRequest("Vincula una solicitud de pago antes de validar");
        CrmWhatsappPaymentRequest request = requestRepository.findForUpdate(evidence.getPaymentRequest().getIdPaymentRequest())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Solicitud no disponible"));
        if (!ACTIVE_REQUESTS.contains(request.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La solicitud ya no esta pendiente");
        }
        if (request.getExpiresAt().isBefore(LocalDateTime.now())) {
            request.setStatus(CrmWhatsappPaymentRequestStatus.EXPIRED);
            requestRepository.save(request);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La solicitud de pago expiro");
        }
        CrmSaleCreateRequest sale = readJson(request.getSaleRequestJson(), CrmSaleCreateRequest.class);
        LocalDateTime paidAt = evidence.getOperationAt() == null ? LocalDateTime.now() : evidence.getOperationAt();
        CrmSaleCreateRequest validatedSale = new CrmSaleCreateRequest(
                sale.idSucursal(), sale.idComprobante(), sale.moneda(), sale.formaPago(), sale.igvPorcentaje(),
                sale.descuentoTotal(), sale.tipoDescuento(), sale.detalles(),
                List.of(new VentaPagoCreateItem(request.getMetodoPago().getIdMetodoPago(),
                        request.getExpectedAmount().doubleValue(), blankToNull(evidence.getOperationCode()), paidAt)),
                sale.telefonoRespaldo(), sale.aiSaleDraftId(), sale.aiSaleDraftVersion());
        try {
            boolean reserved = request.getReservationStatus() == CrmWhatsappPaymentReservationStatus.ACTIVE;
            VentaCrmResponse result = reserved
                    ? chatServiceProvider.getObject().registrarVentaReservadaDesdeCrm(
                            request.getConversation().getIdConversation(), validatedSale, actor)
                    : chatServiceProvider.getObject().registrarVentaDesdeCrm(
                            request.getConversation().getIdConversation(), validatedSale, actor);
            if (reserved) reservationService.consume(request);
            request.setVenta(ventaRepository.findById(result.venta().idVenta()).orElse(null));
            request.setStatus(CrmWhatsappPaymentRequestStatus.COMPLETED);
            request.setCompletedAt(LocalDateTime.now());
            requestRepository.save(request);
            evidence.setPaymentRequest(request);
            review(evidence, actor, CrmWhatsappPaymentEvidenceStatus.VALIDADO, body.note());
            publish("payment.request.completed", evidence.getConversation(), toResponse(request));
            publish("payment.evidence.updated", evidence.getConversation(), toResponse(evidence));
            return decisionResponse(evidence, result);
        } catch (RuntimeException error) {
            evidence.setValidationStatus(CrmWhatsappPaymentEvidenceStatus.OBSERVADO);
            evidence.setReviewNote(truncate("No se emitio la venta: " + safeMessage(error), 500));
            evidenceRepository.save(evidence);
            throw error;
        }
    }

    @Transactional
    public VentaCrmResponse completeReservedSale(
            Long requestId, CrmSaleCreateRequest body, Usuario sessionUser) {
        Usuario actor = requireCrmUser(sessionUser);
        if (body == null || body.idComprobante() == null) throw badRequest("Selecciona un comprobante comercial");
        if (body.pagos() == null || body.pagos().size() != 1) throw badRequest("Selecciona un metodo de pago");

        CrmWhatsappPaymentRequest request = requestRepository.findForUpdate(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Solicitud de pago no encontrada"));
        requireCanOperate(request.getConversation(), actor);
        if (request.getStatus() != CrmWhatsappPaymentRequestStatus.READY_FOR_SALE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El comprobante aun no esta listo para registrar la venta");
        }
        if (request.getReservationStatus() != CrmWhatsappPaymentReservationStatus.ACTIVE
                || request.getExpiresAt() == null || request.getExpiresAt().isBefore(LocalDateTime.now())) {
            reservationService.release(request, actor, "La reserva vencio antes de completar la venta");
            request.setStatus(CrmWhatsappPaymentRequestStatus.EXPIRED);
            requestRepository.save(request);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La reserva de stock vencio");
        }

        CrmWhatsappPaymentEvidence evidence = evidenceRepository
                .findFirstByPaymentRequest_IdPaymentRequestAndValidationStatusOrderByCreatedAtDesc(
                        requestId, CrmWhatsappPaymentEvidenceStatus.ACEPTABLE)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "No existe un comprobante aceptable"));
        if (evidence.getDetectedAmount() == null
                || request.getExpectedAmount().compareTo(evidence.getDetectedAmount()) != 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El monto del comprobante no coincide con el pedido");
        }

        VentaPagoCreateItem selectedPayment = body.pagos().get(0);
        branchPaymentRepository.findBySucursal_IdSucursalAndMetodoPago_IdMetodoPagoAndDeletedAtIsNull(
                request.getSucursal().getIdSucursal(), selectedPayment.idMetodoPago())
                .filter(item -> "ACTIVO".equalsIgnoreCase(item.getEstado()))
                .orElseThrow(() -> badRequest("El metodo de pago no esta habilitado para la sucursal"));

        CrmSaleCreateRequest stored = readJson(request.getSaleRequestJson(), CrmSaleCreateRequest.class);
        CrmSaleCreateRequest sale = new CrmSaleCreateRequest(
                request.getSucursal().getIdSucursal(), body.idComprobante(),
                request.getCurrency(), body.formaPago(), body.igvPorcentaje(),
                stored.descuentoTotal(), stored.tipoDescuento(), stored.detalles(),
                List.of(new VentaPagoCreateItem(selectedPayment.idMetodoPago(),
                        request.getExpectedAmount().doubleValue(),
                        clean(evidence.getOperationCode()).isBlank()
                                ? selectedPayment.codigoOperacion() : evidence.getOperationCode(),
                        evidence.getOperationAt() == null ? selectedPayment.fecha() : evidence.getOperationAt())),
                body.telefonoRespaldo(), stored.aiSaleDraftId(), stored.aiSaleDraftVersion());

        try {
            VentaCrmResponse result = chatServiceProvider.getObject().registrarVentaReservadaDesdeCrm(
                    request.getConversation().getIdConversation(), sale, actor);
            reservationService.consume(request);
            request.setVenta(ventaRepository.findById(result.venta().idVenta()).orElse(null));
            request.setStatus(CrmWhatsappPaymentRequestStatus.COMPLETED);
            request.setCompletedAt(LocalDateTime.now());
            requestRepository.save(request);
            evidence.setValidationStatus(CrmWhatsappPaymentEvidenceStatus.VALIDADO);
            evidence.setReviewedBy(actor);
            evidence.setReviewedAt(LocalDateTime.now());
            evidenceRepository.save(evidence);
            publish("payment.request.completed", request.getConversation(), toResponse(request));
            publish("payment.evidence.updated", request.getConversation(), toResponse(evidence));
            return result;
        } catch (RuntimeException error) {
            request.setReleaseReason(truncate("No se pudo emitir la venta: " + safeMessage(error), 500));
            requestRepository.save(request);
            throw error;
        }
    }

    @Transactional(readOnly = true)
    public List<Long> readyEvidenceIds() {
        return evidenceRepository.findReady(
                CrmWhatsappPaymentProcessingStatus.QUEUED, LocalDateTime.now(), PageRequest.of(0, 5))
                .stream().map(CrmWhatsappPaymentEvidence::getIdPaymentEvidence).toList();
    }

    @Transactional(readOnly = true)
    public List<Long> unregisteredIncomingMediaIds() {
        return messageRepository.findUnregisteredPaymentMedia(PageRequest.of(0, 10));
    }

    @Transactional(readOnly = true)
    public List<Long> observedExactAmountEvidenceIds() {
        return evidenceRepository.findObservedWithExactAmount(
                CrmWhatsappPaymentEvidenceStatus.OBSERVADO,
                CrmWhatsappPaymentProcessingStatus.COMPLETED,
                ACCEPTING_EVIDENCE,
                CrmWhatsappPaymentReservationStatus.ACTIVE,
                LocalDateTime.now(),
                PageRequest.of(0, 10));
    }

    @Transactional
    public void promoteObservedExactAmount(Long evidenceId) {
        CrmWhatsappPaymentEvidence evidence = evidenceRepository.findForUpdate(evidenceId).orElse(null);
        if (evidence == null
                || evidence.getValidationStatus() != CrmWhatsappPaymentEvidenceStatus.OBSERVADO
                || evidence.getProcessingStatus() != CrmWhatsappPaymentProcessingStatus.COMPLETED
                || evidence.getDuplicateOfId() != null
                || evidence.getDetectedAmount() == null
                || evidence.getPaymentRequest() == null) return;

        CrmWhatsappPaymentRequest request = requestRepository
                .findForUpdate(evidence.getPaymentRequest().getIdPaymentRequest()).orElse(null);
        LocalDateTime now = LocalDateTime.now();
        if (request == null
                || !ACCEPTING_EVIDENCE.contains(request.getStatus())
                || request.getReservationStatus() != CrmWhatsappPaymentReservationStatus.ACTIVE
                || request.getExpiresAt() == null
                || request.getExpiresAt().isBefore(now)
                || request.getExpectedAmount().compareTo(evidence.getDetectedAmount()) != 0) return;

        evidence.setValidationStatus(CrmWhatsappPaymentEvidenceStatus.ACEPTABLE);
        evidence.setWarningsJson(writeJson(readStringList(evidence.getWarningsJson()).stream()
                .filter(warning -> !isLegacyBlockingWarning(warning))
                .distinct()
                .toList()));
        evidence.setCustomerNotifiedAt(null);
        evidence = evidenceRepository.save(evidence);

        request.setStatus(CrmWhatsappPaymentRequestStatus.READY_FOR_SALE);
        request.setReviewExpiresAt(now.plusHours(24));
        request.setExpiresAt(request.getReviewExpiresAt());
        requestRepository.save(request);
        markPaymentVerification(request.getConversation());
        aiSaleDraftService.extendPaymentReview(request.getAiSaleDraft() == null
                ? null : request.getAiSaleDraft().getIdAiSaleDraft(), request.getReviewExpiresAt());

        publish("payment.evidence.updated", evidence.getConversation(), toResponse(evidence));
        publish("payment.request.updated", evidence.getConversation(), toResponse(request));
        notifyCustomer(evidence);
    }

    @Transactional
    public void expireRequests() {
        Usuario systemUser = usuarioRepository.findByCorreoAndDeletedAtIsNull("sistema@gmail.com").orElse(null);
        requestRepository.findByStatusInAndExpiresAtBefore(ACTIVE_REQUESTS, LocalDateTime.now()).forEach(request -> {
            if (systemUser != null) reservationService.release(request, systemUser, "Solicitud de pago vencida");
            request.setStatus(CrmWhatsappPaymentRequestStatus.EXPIRED);
            requestRepository.save(request);
            aiSaleDraftService.markPaymentExpired(request.getAiSaleDraft() == null
                    ? null : request.getAiSaleDraft().getIdAiSaleDraft());
            if (request.getConversation() != null) publish("payment.request.updated", request.getConversation(), toResponse(request));
            if (request.getConversation() != null) {
                sendCustomerNotice(request.getConversation(),
                        "La reserva del pedido venció y el stock fue liberado. Si deseas continuar, confirma nuevamente tu pedido.");
            }
        });
    }

    @Transactional
    public void processOne(Long evidenceId) {
        CrmWhatsappPaymentEvidence evidence = evidenceRepository.findForUpdate(evidenceId).orElse(null);
        if (evidence == null || evidence.getProcessingStatus() != CrmWhatsappPaymentProcessingStatus.QUEUED) return;
        if (aiStopped(evidence.getConversation())) return;
        evidence.setProcessingStatus(CrmWhatsappPaymentProcessingStatus.PROCESSING);
        evidence.setAttempts(evidence.getAttempts() + 1);
        evidenceRepository.saveAndFlush(evidence);
        publish("payment.evidence.processing", evidence.getConversation(), toResponse(evidence));
        try {
            byte[] bytes = storageService.readBytes(evidence.getStoragePath());
            PaymentEvidenceExtraction extraction = aiModelProvider.extractPaymentEvidence(new PaymentEvidenceRequest(
                    evidence.getConversation().getConnection().getIdConnection(), bytes, evidence.getMimeType(), evidence.getFileName()));
            applyExtraction(evidence, extraction);
            compare(evidence);
            evidence.setProcessingStatus(CrmWhatsappPaymentProcessingStatus.COMPLETED);
            evidence.setLastError(null);
            if (evidence.getPaymentRequest() != null
                    && evidence.getValidationStatus() == CrmWhatsappPaymentEvidenceStatus.ACEPTABLE) {
                CrmWhatsappPaymentRequest request = requestRepository
                        .findForUpdate(evidence.getPaymentRequest().getIdPaymentRequest()).orElse(null);
                if (request != null && ACCEPTING_EVIDENCE.contains(request.getStatus())) {
                    request.setStatus(CrmWhatsappPaymentRequestStatus.READY_FOR_SALE);
                    request.setReviewExpiresAt(LocalDateTime.now().plusHours(24));
                    request.setExpiresAt(request.getReviewExpiresAt());
                    requestRepository.save(request);
                    markPaymentVerification(request.getConversation());
                    aiSaleDraftService.extendPaymentReview(request.getAiSaleDraft() == null
                            ? null : request.getAiSaleDraft().getIdAiSaleDraft(), request.getReviewExpiresAt());
                    publish("payment.request.updated", evidence.getConversation(), toResponse(request));
                }
            }
        } catch (RuntimeException error) {
            evidence.setLastError(truncate(safeMessage(error), 1000));
            if (evidence.getAttempts() < 3) {
                evidence.setProcessingStatus(CrmWhatsappPaymentProcessingStatus.QUEUED);
                evidence.setAvailableAt(LocalDateTime.now().plusSeconds((long) Math.pow(2, evidence.getAttempts()) * 15));
            } else {
                evidence.setProcessingStatus(CrmWhatsappPaymentProcessingStatus.FAILED);
                evidence.setValidationStatus(CrmWhatsappPaymentEvidenceStatus.OBSERVADO);
            }
        }
        evidence = evidenceRepository.save(evidence);
        publish("payment.evidence.updated", evidence.getConversation(), toResponse(evidence));
        notifyCustomer(evidence);
    }

    private boolean aiStopped(CrmWhatsappConversation conversation) {
        return conversation != null && conversation.getConnection() != null
                && aiConfigRepository.findByConnection_IdConnection(conversation.getConnection().getIdConnection())
                        .map(config -> CrmWhatsappAiOperationsService.EMERGENCY_STOP.equals(config.getOperationalStatus()))
                        .orElse(false);
    }

    private void markPaymentVerification(CrmWhatsappConversation conversation) {
        if (conversation == null || conversation.getAssignedUser() != null) return;
        conversation.setStatus("ESPERA");
        conversation.setAiAttentionMode(CrmWhatsappAiAttentionMode.HUMANA);
        conversation.setAiAttentionModeExplicit(true);
        conversation.setWaitingReason(CrmWhatsappWaitingReason.PAYMENT_VERIFICATION);
        conversationRepository.save(conversation);
        aiMemoryService.pauseForHuman(conversation,
                "Comprobante registrado; requiere validacion de una asesora");
    }

    private CrmWhatsappPaymentEvidence createEvidence(
            CrmWhatsappMessage message, CrmWhatsappPaymentRequest request, boolean automatic) {
        if (!isSupported(message)) throw badRequest("El archivo no es una imagen o PDF compatible");
        byte[] bytes = storageService.readBytes(message.getMediaStoragePath());
        if (bytes.length > MAX_FILE_BYTES) throw badRequest("La evidencia supera el maximo de 10 MB");
        String sha = sha256(bytes);
        String extension = extension(message.getMediaMimeType());
        String storagePath = storageService.upload(bytes,
                "crm/payment-evidence/%s/%s%s".formatted(message.getConversation().getIdConversation(), UUID.randomUUID(), extension),
                message.getMediaMimeType());
        CrmWhatsappPaymentEvidence evidence = new CrmWhatsappPaymentEvidence();
        evidence.setMessage(message);
        evidence.setConversation(message.getConversation());
        evidence.setPaymentRequest(request);
        evidence.setStoragePath(storagePath);
        evidence.setMimeType(message.getMediaMimeType());
        evidence.setFileName(message.getMediaFileName());
        evidence.setSha256(sha);
        evidence.setPerceptualHash(perceptualHash(bytes, message.getMediaMimeType()));
        Integer companyId = message.getConversation().getConnection() == null
                ? null : message.getConversation().getConnection().getEmpresa().getIdEmpresa();
        CrmWhatsappPaymentEvidence duplicateByHash = companyId == null ? null : evidenceRepository
                .findDuplicatesByHash(sha, companyId, PageRequest.of(0, 1)).stream().findFirst().orElse(null);
        if (duplicateByHash != null) evidence.setDuplicateOfId(duplicateByHash.getIdPaymentEvidence());
        if (evidence.getDuplicateOfId() == null && evidence.getPerceptualHash() != null && companyId != null) {
            String visualHash = evidence.getPerceptualHash();
            CrmWhatsappPaymentEvidence visualDuplicate = evidenceRepository
                    .findRecentVisualHashes(companyId, PageRequest.of(0, 100)).stream()
                    .filter(other -> hammingDistance(visualHash, other.getPerceptualHash()) <= 5)
                    .findFirst()
                    .orElse(null);
            if (visualDuplicate != null) evidence.setDuplicateOfId(visualDuplicate.getIdPaymentEvidence());
        }
        if (evidence.getDuplicateOfId() != null) evidence.setValidationStatus(CrmWhatsappPaymentEvidenceStatus.OBSERVADO);
        try {
            evidence = evidenceRepository.save(evidence);
        } catch (DataIntegrityViolationException error) {
            storageService.deleteByKey(storagePath);
            throw error;
        }
        publish("payment.evidence.updated", message.getConversation(), toResponse(evidence));
        return evidence;
    }

    private void applyExtraction(CrmWhatsappPaymentEvidence evidence, PaymentEvidenceExtraction value) {
        evidence.setDetectedProvider(blankToNull(value.provider()));
        evidence.setDetectedAmount(parseAmount(value.amount()));
        evidence.setDetectedCurrency(clean(value.currency()).isBlank() ? "PEN" : clean(value.currency()).toUpperCase(Locale.ROOT));
        evidence.setOperationCode(blankToNull(value.operationCode()));
        evidence.setOperationAt(parseDateTime(value.operationDateTime()));
        evidence.setRecipient(null);
        evidence.setConfidence(value.confidence());
        evidence.setExtractedText(truncate(value.extractedText(), 10000));
        evidence.setExtractionJson(writeJson(Map.of(
                "paymentEvidence", value.paymentEvidence(),
                "fieldConfidences", value.fieldConfidences(),
                "usage", value.usage())));
        evidence.setWarningsJson(writeJson(value.warnings()));
        if (!value.paymentEvidence()) {
            evidence.setValidationStatus(CrmWhatsappPaymentEvidenceStatus.OBSERVADO);
            List<String> warnings = readStringList(evidence.getWarningsJson());
            warnings.add("El archivo no parece ser un comprobante de pago legible");
            evidence.setWarningsJson(writeJson(warnings.stream().distinct().toList()));
        }
    }

    private void compare(CrmWhatsappPaymentEvidence evidence) {
        List<String> warnings = readStringList(evidence.getWarningsJson());
        List<String> blockingWarnings = new ArrayList<>();
        CrmWhatsappPaymentRequest request = evidence.getPaymentRequest();
        if (request == null) blockingWarnings.add("No existe una solicitud de pago vinculada");
        if (request != null && evidence.getDetectedAmount() == null) {
            blockingWarnings.add("No se pudo detectar el importe del comprobante");
        }
        if (request != null && evidence.getDetectedAmount() != null
                && request.getExpectedAmount().compareTo(evidence.getDetectedAmount()) != 0) {
            blockingWarnings.add("El monto detectado no coincide con el monto esperado");
        }
        if (evidence.getOperationCode() != null) {
            Integer companyId = evidence.getConversation().getConnection().getEmpresa().getIdEmpresa();
            evidenceRepository.findDuplicatesByOperation(evidence.getOperationCode(), companyId, PageRequest.of(0, 2)).stream()
                    .filter(other -> !other.getIdPaymentEvidence().equals(evidence.getIdPaymentEvidence()))
                    .findFirst()
                    .ifPresent(other -> {
                        evidence.setDuplicateOfId(other.getIdPaymentEvidence());
                        blockingWarnings.add("El codigo de operacion ya fue registrado");
                    });
        }
        if (evidence.getDuplicateOfId() != null) blockingWarnings.add("La evidencia puede estar duplicada");
        warnings.addAll(blockingWarnings);
        evidence.setValidationStatus(blockingWarnings.isEmpty()
                ? CrmWhatsappPaymentEvidenceStatus.ACEPTABLE
                : CrmWhatsappPaymentEvidenceStatus.OBSERVADO);
        evidence.setWarningsJson(writeJson(warnings.stream().distinct().toList()));
    }

    private boolean isLegacyBlockingWarning(String warning) {
        String normalized = clean(warning).toLowerCase(Locale.ROOT);
        return normalized.contains("moneda")
                || normalized.contains("codigo")
                || normalized.contains("operacion")
                || normalized.contains("fecha")
                || normalized.contains("hora")
                || normalized.contains("destinatario")
                || normalized.contains("confianza")
                || normalized.contains("no parece ser un comprobante");
    }

    private void notifyCustomer(CrmWhatsappPaymentEvidence evidence) {
        if (evidence.getCustomerNotifiedAt() != null || evidence.getConversation() == null) return;
        String message;
        if (evidence.getProcessingStatus() == CrmWhatsappPaymentProcessingStatus.FAILED) {
            message = "No pudimos leer el comprobante. Por favor envía una captura clara y completa para revisarla nuevamente.";
        } else if (evidence.getValidationStatus() == CrmWhatsappPaymentEvidenceStatus.ACEPTABLE) {
            deliveryService.enqueueNotice(evidence.getConversation(), CrmWhatsappAiDeliveryType.PAYMENT_REGISTERED,
                    "payment-registered:" + evidence.getIdPaymentEvidence(),
                    "✅ Comprobante registrado correctamente. En unos momentos validaremos tu pedido.");
            return;
        } else if (readStringList(evidence.getWarningsJson()).stream()
                .anyMatch(item -> item.toLowerCase(Locale.ROOT).contains("duplicad")
                        || item.toLowerCase(Locale.ROOT).contains("ya fue registrado"))) {
            message = "Esta operacion ya fue utilizada. Envia un comprobante diferente para continuar.";
        } else if (readStringList(evidence.getWarningsJson()).stream()
                .anyMatch(item -> item.toLowerCase(Locale.ROOT).contains("monto")
                        || item.toLowerCase(Locale.ROOT).contains("codigo")
                        || item.toLowerCase(Locale.ROOT).contains("fecha"))) {
            message = "Los datos del comprobante no coinciden o estan incompletos. Envia otra captura clara y completa.";
        } else {
            message = "No pudimos identificar un comprobante valido. Envia una captura clara y completa para continuar.";
        }
        BigDecimal expectedAmount = evidence.getPaymentRequest() == null
                ? null : evidence.getPaymentRequest().getExpectedAmount();
        String retryMessage = expectedAmount == null
                ? "No se puede procesar tu pago. Intenta otra vez enviando una captura clara del comprobante."
                : "No se puede procesar tu pago. Intenta otra vez enviando una captura clara con el monto exacto de S/"
                        + expectedAmount.setScale(2, RoundingMode.HALF_UP).toPlainString() + ".";
        if (evidence.getProcessingStatus() == CrmWhatsappPaymentProcessingStatus.FAILED) {
            retryMessage = message;
        }
        deliveryService.enqueueNotice(evidence.getConversation(), CrmWhatsappAiDeliveryType.PAYMENT_RETRY,
                "payment-retry:" + evidence.getIdPaymentEvidence(), retryMessage);
    }

    private void sendCustomerNotice(CrmWhatsappConversation conversation, String message) {
        if (conversation == null || clean(message).isBlank()) return;
        Long conversationId = conversation.getIdConversation();
        runAfterCommit(() -> {
            try {
                chatServiceProvider.getObject().enviarAvisoSistemaAutomatico(conversationId, message);
            } catch (RuntimeException ignored) {
                // La decision financiera ya quedo auditada aunque WhatsApp no este disponible.
            }
        });
    }

    private void runAfterCommit(Runnable operation) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            operation.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                operation.run();
            }
        });
    }

    private void review(CrmWhatsappPaymentEvidence evidence, Usuario actor,
            CrmWhatsappPaymentEvidenceStatus status, String note) {
        evidence.setValidationStatus(status);
        evidence.setReviewedBy(actor);
        evidence.setReviewedAt(LocalDateTime.now());
        evidence.setReviewNote(blankToNull(truncate(clean(note), 500)));
        evidenceRepository.save(evidence);
    }

    private PaymentRequestResponse toResponse(CrmWhatsappPaymentRequest request) {
        CrmWhatsappPaymentEvidence accepted = evidenceRepository
                .findFirstByPaymentRequest_IdPaymentRequestAndValidationStatusOrderByCreatedAtDesc(
                        request.getIdPaymentRequest(), CrmWhatsappPaymentEvidenceStatus.ACEPTABLE)
                .orElse(null);
        return new PaymentRequestResponse(request.getIdPaymentRequest(),
                request.getConversation() == null ? null : request.getConversation().getIdConversation(),
                request.getSucursal().getIdSucursal(), request.getSucursal().getNombre(),
                request.getMetodoPago().getIdMetodoPago(), request.getMetodoPago().getNombre(),
                request.getCuenta() == null ? null : request.getCuenta().getIdMetodoPagoCuenta(),
                request.getCuenta() == null ? null : request.getCuenta().getNumeroCuenta(),
                request.getCuenta() == null ? null : request.getCuenta().getTitular(),
                request.getExpectedAmount(), request.getCurrency(), request.getStatus().name(), request.getExpiresAt(),
                request.getReservationStatus().name(), request.getReservedAt(), request.getReviewExpiresAt(),
                request.getReleaseReason(),
                request.getVenta() == null ? null : request.getVenta().getIdVenta(),
                accepted == null ? null : accepted.getIdPaymentEvidence(),
                accepted == null || accepted.getMessage() == null ? null : accepted.getMessage().getIdMessage(),
                accepted == null ? null : accepted.getValidationStatus().name(),
                accepted != null && accepted.getReviewedAt() != null,
                accepted == null ? null : accepted.getOperationCode(),
                accepted == null ? null : accepted.getOperationAt(),
                accepted == null ? null : accepted.getDetectedAmount(),
                accepted == null ? null : accepted.getDetectedProvider());
    }

    private PaymentEvidenceResponse toResponse(CrmWhatsappPaymentEvidence evidence) {
        return new PaymentEvidenceResponse(evidence.getIdPaymentEvidence(),
                evidence.getMessage() == null ? null : evidence.getMessage().getIdMessage(),
                evidence.getConversation() == null ? null : evidence.getConversation().getIdConversation(),
                evidence.getPaymentRequest() == null ? null : evidence.getPaymentRequest().getIdPaymentRequest(),
                evidence.getValidationStatus().name(), evidence.getProcessingStatus().name(),
                evidence.getMimeType(), evidence.getFileName(), evidence.getDetectedProvider(), evidence.getDetectedAmount(),
                evidence.getDetectedCurrency(), evidence.getOperationCode(), evidence.getOperationAt(), evidence.getRecipient(),
                evidence.getConfidence(), readStringList(evidence.getWarningsJson()), evidence.getDuplicateOfId(),
                evidence.getReviewNote(), evidence.getCreatedAt(),
                evidence.getPaymentRequest() == null ? null : evidence.getPaymentRequest().getExpectedAmount(),
                evidence.getPaymentRequest() == null ? null : evidence.getPaymentRequest().getCurrency(),
                evidence.getPaymentRequest() == null ? null : evidence.getPaymentRequest().getMetodoPago().getNombre(),
                evidence.getPaymentRequest() == null || evidence.getPaymentRequest().getCuenta() == null
                        ? null : evidence.getPaymentRequest().getCuenta().getNumeroCuenta(),
                evidence.getPaymentRequest() == null || evidence.getPaymentRequest().getCuenta() == null
                        ? null : evidence.getPaymentRequest().getCuenta().getTitular(),
                "/api/crm/whatsapp/payment-evidences/" + evidence.getIdPaymentEvidence() + "/media");
    }

    private PaymentDecisionResponse decisionResponse(CrmWhatsappPaymentEvidence evidence, VentaCrmResponse sale) {
        CrmWhatsappPaymentRequest request = evidence.getPaymentRequest();
        return new PaymentDecisionResponse(
                toResponse(evidence),
                request == null ? null : toResponse(request),
                chatServiceProvider.getObject().conversationResponse(evidence.getConversation()),
                sale);
    }

    private void publish(String type, CrmWhatsappConversation conversation, Object data) {
        Map<String, Object> payload = Map.of("type", type, "conversationId", conversation.getIdConversation(), "data", data);
        Integer assigned = conversation.getAssignedUser() == null ? null : conversation.getAssignedUser().getIdUsuario();
        eventService.publishAfterCommit(payload, payload, assigned,
                "ESPERA".equals(conversation.getStatus()) && assigned == null);
    }

    private Usuario requireCrmUser(Usuario user) {
        if (user == null || user.getIdUsuario() == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autenticado");
        Usuario actor = usuarioRepository.findByIdUsuarioAndDeletedAtIsNull(user.getIdUsuario())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autenticado"));
        if (!(actor.getRol() == Rol.ADMINISTRADOR || actor.getRol() == Rol.SISTEMA || Boolean.TRUE.equals(actor.getAccesoCrm()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sin acceso al CRM");
        }
        return actor;
    }

    private CrmWhatsappConversation requireConversation(Long id) {
        return conversationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversacion no encontrada"));
    }

    private void requireCanRead(CrmWhatsappConversation conversation, Usuario actor) {
        if (actor.getRol().esAdministrador()) return;
        Integer assigned = conversation.getAssignedUser() == null ? null : conversation.getAssignedUser().getIdUsuario();
        if (assigned != null && assigned.equals(actor.getIdUsuario())) return;
        if (assigned == null && "ESPERA".equals(conversation.getStatus())) return;
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puedes acceder a esta conversacion");
    }

    private void requireCanOperate(CrmWhatsappConversation conversation, Usuario actor) {
        if (actor.getRol().esAdministrador()) return;
        Integer assigned = conversation.getAssignedUser() == null ? null : conversation.getAssignedUser().getIdUsuario();
        if (assigned == null || !assigned.equals(actor.getIdUsuario())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acepta el chat antes de operar");
        }
    }

    private boolean isSupported(CrmWhatsappMessage message) {
        String mime = clean(message.getMediaMimeType()).toLowerCase(Locale.ROOT);
        return message.getMediaStoragePath() != null
                && (mime.equals("image/jpeg") || mime.equals("image/png") || mime.equals("image/webp")
                        || mime.equals("application/pdf"));
    }

    private BigDecimal decimal(Double value) {
        return value == null ? BigDecimal.ZERO : BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal parseAmount(String value) {
        try {
            String normalized = clean(value).replaceAll("[^0-9,.-]", "").replace(",", ".");
            return normalized.isBlank() ? null : new BigDecimal(normalized).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException error) {
            return null;
        }
    }

    private LocalDateTime parseDateTime(String value) {
        try { return clean(value).isBlank() ? null : LocalDateTime.parse(clean(value)); }
        catch (DateTimeParseException error) { return null; }
    }

    private String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (Exception error) { throw new IllegalStateException("No se pudo calcular la huella del archivo", error); }
    }

    private String perceptualHash(byte[] bytes, String mime) {
        if (!clean(mime).startsWith("image/")) return null;
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(bytes));
            if (source == null) return null;
            BufferedImage scaled = new BufferedImage(8, 8, BufferedImage.TYPE_BYTE_GRAY);
            Graphics2D graphics = scaled.createGraphics();
            graphics.drawImage(source.getScaledInstance(8, 8, Image.SCALE_SMOOTH), 0, 0, null);
            graphics.dispose();
            int[] values = new int[64];
            int sum = 0;
            for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
                int value = scaled.getRaster().getSample(x, y, 0); values[y * 8 + x] = value; sum += value;
            }
            int average = sum / 64;
            long hash = 0;
            for (int i = 0; i < 64; i++) if (values[i] >= average) hash |= (1L << i);
            return String.format("%016x", hash);
        } catch (Exception ignored) { return null; }
    }

    private int hammingDistance(String first, String second) {
        try { return Long.bitCount(Long.parseUnsignedLong(first, 16) ^ Long.parseUnsignedLong(second, 16)); }
        catch (RuntimeException error) { return Integer.MAX_VALUE; }
    }

    private String extension(String mime) {
        return switch (clean(mime).toLowerCase(Locale.ROOT)) {
            case "image/png" -> ".png"; case "image/webp" -> ".webp"; case "application/pdf" -> ".pdf"; default -> ".jpg";
        };
    }

    private List<String> readStringList(String json) {
        if (clean(json).isBlank()) return new ArrayList<>();
        try { return new ArrayList<>(objectMapper.readValue(json, new TypeReference<List<String>>() {})); }
        catch (Exception error) { return new ArrayList<>(); }
    }

    private String writeJson(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception error) { throw new IllegalStateException("No se pudo serializar la operacion", error); }
    }

    private <T> T readJson(String json, Class<T> type) {
        try { return objectMapper.readValue(json, type); }
        catch (Exception error) { throw new IllegalStateException("La solicitud pendiente esta dañada", error); }
    }

    private String clean(String value) { return value == null ? "" : value.trim(); }
    private String blankToNull(String value) { return clean(value).isBlank() ? null : clean(value); }
    private String truncate(String value, int max) { return value == null || value.length() <= max ? value : value.substring(0, max); }
    private String safeMessage(Throwable error) { return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage(); }
    private ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }

    public record PaymentRequestCreateRequest(CrmSaleCreateRequest sale, Integer idMetodoPagoCuenta) {}
    public record PaymentDecisionRequest(String action, Boolean confirmedExternal, String note) {}
    public record PaymentDecisionResponse(PaymentEvidenceResponse evidence, PaymentRequestResponse request,
            CrmWhatsappChatService.ConversationResponse conversation, VentaCrmResponse sale) {}
    public record PaymentEvidenceDownload(byte[] bytes, String contentType, String fileName) {}
    public record PaymentRequestResponse(Long idPaymentRequest, Long conversationId, Integer branchId, String branchName,
            Integer paymentMethodId, String paymentMethod, Integer accountId, String accountNumber, String accountHolder,
            BigDecimal expectedAmount, String currency, String status, LocalDateTime expiresAt,
            String reservationStatus, LocalDateTime reservedAt, LocalDateTime reviewExpiresAt, String releaseReason,
            Integer saleId, Long evidenceId, Long evidenceMessageId, String evidenceStatus, boolean advisorAccepted,
            String operationCode, LocalDateTime operationAt,
            BigDecimal detectedAmount, String detectedProvider) {}
    public record PaymentEvidenceResponse(Long idPaymentEvidence, Long messageId, Long conversationId, Long paymentRequestId,
            String validationStatus, String processingStatus, String mimeType, String fileName, String provider,
            BigDecimal amount, String currency, String operationCode, LocalDateTime operationAt, String recipient,
            Integer confidence, List<String> warnings, Long duplicateOfId, String reviewNote, LocalDateTime createdAt,
            BigDecimal expectedAmount, String expectedCurrency, String expectedPaymentMethod,
            String expectedAccountNumber, String expectedAccountHolder,
            String mediaUrl) {}
}
