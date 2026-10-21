package com.sistemapos.sistematextil.services.agent.sale;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sistemapos.sistematextil.model.ComprobanteConfig;
import com.sistemapos.sistematextil.model.CrmWhatsappAgentEvent;
import com.sistemapos.sistematextil.model.CrmWhatsappAgentSession;
import com.sistemapos.sistematextil.model.CrmWhatsappAgentState;
import com.sistemapos.sistematextil.model.CrmWhatsappFulfillment;
import com.sistemapos.sistematextil.model.CrmWhatsappFulfillmentStatus;
import com.sistemapos.sistematextil.model.CrmWhatsappPaymentEvidence;
import com.sistemapos.sistematextil.model.CrmWhatsappPaymentEvidenceStatus;
import com.sistemapos.sistematextil.model.CrmWhatsappPaymentProcessingStatus;
import com.sistemapos.sistematextil.model.CrmWhatsappPaymentRequest;
import com.sistemapos.sistematextil.model.CrmWhatsappPaymentRequestStatus;
import com.sistemapos.sistematextil.model.CrmWhatsappPaymentReservationStatus;
import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.model.Venta;
import com.sistemapos.sistematextil.repositories.ComprobanteConfigRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAgentSessionRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappFulfillmentRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappPaymentEvidenceRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappPaymentRequestRepository;
import com.sistemapos.sistematextil.repositories.UsuarioRepository;
import com.sistemapos.sistematextil.repositories.VentaRepository;
import com.sistemapos.sistematextil.services.CrmWhatsappPaymentReservationService;
import com.sistemapos.sistematextil.services.VentaService;
import com.sistemapos.sistematextil.services.agent.CrmWhatsappAgentStateMachine;
import com.sistemapos.sistematextil.services.agent.cart.AgentCartStore;
import com.sistemapos.sistematextil.services.agent.cart.AgentCartStore.OperationRow;
import com.sistemapos.sistematextil.services.CrmWhatsappChatService.CrmSaleCreateRequest;
import com.sistemapos.sistematextil.util.venta.VentaCreateRequest;
import com.sistemapos.sistematextil.util.venta.VentaPagoCreateItem;
import com.sistemapos.sistematextil.util.venta.VentaResponse;

@Service
public class AgentSaleService {
    public static final String RECEIPT_TYPE = "NOTA DE VENTA";
    private static final String AGENT_USER_EMAIL = "ia-kiments@system.local";
    private static final List<CrmWhatsappPaymentRequestStatus> REVIEWABLE_REQUESTS = List.of(
            CrmWhatsappPaymentRequestStatus.UNDER_REVIEW,
            CrmWhatsappPaymentRequestStatus.READY_FOR_SALE);

    private final CrmWhatsappAgentSessionRepository sessionRepository;
    private final CrmWhatsappPaymentRequestRepository paymentRepository;
    private final CrmWhatsappPaymentEvidenceRepository evidenceRepository;
    private final CrmWhatsappFulfillmentRepository fulfillmentRepository;
    private final ComprobanteConfigRepository receiptRepository;
    private final VentaRepository saleRepository;
    private final UsuarioRepository userRepository;
    private final VentaService saleService;
    private final CrmWhatsappPaymentReservationService reservationService;
    private final AgentCartStore operationStore;
    private final CrmWhatsappAgentStateMachine stateMachine;
    private final AgentPaymentReviewQueueService reviewQueueService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public AgentSaleService(
            CrmWhatsappAgentSessionRepository sessionRepository,
            CrmWhatsappPaymentRequestRepository paymentRepository,
            CrmWhatsappPaymentEvidenceRepository evidenceRepository,
            CrmWhatsappFulfillmentRepository fulfillmentRepository,
            ComprobanteConfigRepository receiptRepository,
            VentaRepository saleRepository,
            UsuarioRepository userRepository,
            VentaService saleService,
            CrmWhatsappPaymentReservationService reservationService,
            AgentCartStore operationStore,
            CrmWhatsappAgentStateMachine stateMachine,
            AgentPaymentReviewQueueService reviewQueueService) {
        this(sessionRepository, paymentRepository, evidenceRepository, fulfillmentRepository, receiptRepository,
                saleRepository, userRepository, saleService, reservationService, operationStore, stateMachine,
                reviewQueueService, new ObjectMapper().findAndRegisterModules(),
                Clock.system(ZoneId.of("America/Lima")));
    }

    AgentSaleService(
            CrmWhatsappAgentSessionRepository sessionRepository,
            CrmWhatsappPaymentRequestRepository paymentRepository,
            CrmWhatsappPaymentEvidenceRepository evidenceRepository,
            CrmWhatsappFulfillmentRepository fulfillmentRepository,
            ComprobanteConfigRepository receiptRepository,
            VentaRepository saleRepository,
            UsuarioRepository userRepository,
            VentaService saleService,
            CrmWhatsappPaymentReservationService reservationService,
            AgentCartStore operationStore,
            CrmWhatsappAgentStateMachine stateMachine,
            AgentPaymentReviewQueueService reviewQueueService,
            ObjectMapper objectMapper,
            Clock clock) {
        this.sessionRepository = sessionRepository;
        this.paymentRepository = paymentRepository;
        this.evidenceRepository = evidenceRepository;
        this.fulfillmentRepository = fulfillmentRepository;
        this.receiptRepository = receiptRepository;
        this.saleRepository = saleRepository;
        this.userRepository = userRepository;
        this.saleService = saleService;
        this.reservationService = reservationService;
        this.operationStore = operationStore;
        this.stateMachine = stateMachine;
        this.reviewQueueService = reviewQueueService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public SaleResult createSalesNote(Long sessionId, String operationId) {
        return execute(sessionId, operationId, "generar_nota_venta", "ready-fulfillment", () -> create(sessionId));
    }

    private SaleResult create(Long sessionId) {
        CrmWhatsappAgentSession session = lockSession(sessionId);
        CrmWhatsappPaymentEvidence evidence = evidenceRepository
                .findFirstByAgentSession_IdAgentSessionAndValidationStatusOrderByCreatedAtDesc(
                        sessionId, CrmWhatsappPaymentEvidenceStatus.ACEPTABLE)
                .orElseThrow(() -> new IllegalStateException("No existe un comprobante aprobado inicialmente"));
        CrmWhatsappPaymentRequest payment = lockPayment(session, evidence);
        CrmWhatsappFulfillment fulfillment = fulfillmentRepository.findForUpdateBySessionId(sessionId)
                .orElseThrow(() -> new IllegalStateException("La modalidad de entrega no esta registrada"));

        if (payment.getVenta() != null) {
            return existingResult(session, payment, fulfillment);
        }
        validateReady(session, payment, evidence, fulfillment);

        ComprobanteConfig receipt = requireSalesNoteConfig();
        CrmSaleCreateRequest snapshot = read(payment.getSaleRequestJson(), CrmSaleCreateRequest.class);
        validateSnapshot(snapshot, payment);
        LocalDateTime paidAt = evidence.getOperationAt() == null ? now() : evidence.getOperationAt();
        VentaCreateRequest request = new VentaCreateRequest(
                null,
                payment.getSucursal().getIdSucursal(),
                payment.getCliente().getIdCliente(),
                RECEIPT_TYPE,
                receipt.getSerie(),
                null,
                currency(payment.getCurrency()),
                "CONTADO",
                snapshot.igvPorcentaje() == null ? 0D : snapshot.igvPorcentaje(),
                snapshot.descuentoTotal() == null ? 0D : snapshot.descuentoTotal(),
                clean(snapshot.tipoDescuento()).isBlank() ? null : snapshot.tipoDescuento(),
                snapshot.detalles(),
                List.of(new VentaPagoCreateItem(
                        payment.getMetodoPago().getIdMetodoPago(),
                        money(payment.getExpectedAmount()).doubleValue(),
                        blankToNull(evidence.getOperationCode()),
                        paidAt)),
                null);

        Usuario actor = requireAgentUser();
        VentaResponse created = saleService.registrarVentaReservadaDesdeCrm(request, actor);
        validateCreatedSale(created, payment);
        Venta sale = saleRepository.findById(created.idVenta())
                .orElseThrow(() -> new IllegalStateException("La Nota de Venta creada no pudo relacionarse"));

        reservationService.consume(payment);
        payment.setVenta(sale);
        paymentRepository.save(payment);
        fulfillment.setVenta(sale);
        fulfillmentRepository.save(fulfillment);
        applyEvent(session, CrmWhatsappAgentEvent.NOTA_VENTA_CREADA, now());
        reviewQueueService.enqueue(payment.getConversation(), sale.getIdVenta());
        return result(session, payment, fulfillment, created);
    }

    private CrmWhatsappAgentSession lockSession(Long sessionId) {
        if (sessionId == null) throw new IllegalArgumentException("La sesion es obligatoria");
        CrmWhatsappAgentSession session = sessionRepository.findForUpdate(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("La sesion del agente no existe"));
        if (!Boolean.TRUE.equals(session.getActiveSlot())) {
            throw new IllegalStateException("La sesion del agente ya no esta activa");
        }
        return session;
    }

    private CrmWhatsappPaymentRequest lockPayment(
            CrmWhatsappAgentSession session, CrmWhatsappPaymentEvidence evidence) {
        if (evidence.getPaymentRequest() == null || evidence.getPaymentRequest().getIdPaymentRequest() == null) {
            throw new IllegalStateException("El comprobante aprobado no tiene una solicitud de pago");
        }
        CrmWhatsappPaymentRequest payment = paymentRepository
                .findForUpdate(evidence.getPaymentRequest().getIdPaymentRequest())
                .orElseThrow(() -> new IllegalStateException("La solicitud de pago ya no existe"));
        if (payment.getAgentSession() == null
                || !Objects.equals(payment.getAgentSession().getIdAgentSession(), session.getIdAgentSession())) {
            throw new IllegalStateException("La solicitud de pago no pertenece a la sesion");
        }
        return payment;
    }

    private void validateReady(
            CrmWhatsappAgentSession session,
            CrmWhatsappPaymentRequest payment,
            CrmWhatsappPaymentEvidence evidence,
            CrmWhatsappFulfillment fulfillment) {
        if (session.getState() != CrmWhatsappAgentState.LISTO_PARA_VENTA) {
            throw new IllegalStateException("La sesion aun no esta lista para generar la Nota de Venta");
        }
        if (!REVIEWABLE_REQUESTS.contains(payment.getStatus())) {
            throw new IllegalStateException("La solicitud de pago no esta pendiente de revision");
        }
        if (payment.getReservationStatus() != CrmWhatsappPaymentReservationStatus.ACTIVE) {
            throw new IllegalStateException("La reserva de stock ya no esta activa");
        }
        if (payment.getEvidenceAcceptedAt() == null
                || payment.getReviewExpiresAt() == null || payment.getReviewExpiresAt().isBefore(now())) {
            throw new IllegalStateException("La reserva aprobada vencio antes de generar la venta");
        }
        if (evidence.getValidationStatus() != CrmWhatsappPaymentEvidenceStatus.ACEPTABLE
                || evidence.getProcessingStatus() != CrmWhatsappPaymentProcessingStatus.COMPLETED) {
            throw new IllegalStateException("El comprobante aun no fue aprobado inicialmente");
        }
        if (fulfillment.getStatus() != CrmWhatsappFulfillmentStatus.READY
                || fulfillment.getPaymentRequest() == null
                || !Objects.equals(fulfillment.getPaymentRequest().getIdPaymentRequest(), payment.getIdPaymentRequest())) {
            throw new IllegalStateException("La modalidad de entrega aun no esta completa");
        }
        if (payment.getSucursal() == null || payment.getSucursal().getIdSucursal() == null
                || payment.getCliente() == null || payment.getCliente().getIdCliente() == null
                || payment.getMetodoPago() == null || payment.getMetodoPago().getIdMetodoPago() == null
                || payment.getConversation() == null || payment.getConversation().getIdConversation() == null
                || session.getConversation() == null
                || !Objects.equals(payment.getConversation().getIdConversation(),
                        session.getConversation().getIdConversation())) {
            throw new IllegalStateException("La solicitud no tiene todas las relaciones requeridas para la venta");
        }
    }

    private void validateSnapshot(CrmSaleCreateRequest snapshot, CrmWhatsappPaymentRequest payment) {
        if (snapshot == null || snapshot.detalles() == null || snapshot.detalles().isEmpty()) {
            throw new IllegalStateException("La solicitud de pago no contiene productos");
        }
        if (snapshot.idSucursal() != null
                && !Objects.equals(snapshot.idSucursal(), payment.getSucursal().getIdSucursal())) {
            throw new IllegalStateException("La sucursal del pedido no coincide con la reserva");
        }
        if (payment.getExpectedAmount() == null || payment.getExpectedAmount().signum() <= 0) {
            throw new IllegalStateException("La solicitud de pago no tiene un monto valido");
        }
    }

    private ComprobanteConfig requireSalesNoteConfig() {
        return receiptRepository
                .findTopByTipoComprobanteAndDeletedAtIsNullAndActivoOrderByIdComprobanteAsc(
                        RECEIPT_TYPE, "ACTIVO")
                .filter(item -> Boolean.TRUE.equals(item.getHabilitadoVenta()))
                .filter(item -> !clean(item.getSerie()).isBlank())
                .orElseThrow(() -> new IllegalStateException(
                        "No existe una serie activa y habilitada para NOTA DE VENTA"));
    }

    private Usuario requireAgentUser() {
        return userRepository.findByCorreoAndDeletedAtIsNull(AGENT_USER_EMAIL)
                .filter(user -> "ACTIVO".equalsIgnoreCase(user.getEstado()))
                .orElseThrow(() -> new IllegalStateException("El usuario tecnico de IA no esta disponible"));
    }

    private void validateCreatedSale(VentaResponse sale, CrmWhatsappPaymentRequest payment) {
        if (sale == null || sale.idVenta() == null) {
            throw new IllegalStateException("No se pudo crear la Nota de Venta");
        }
        if (!RECEIPT_TYPE.equalsIgnoreCase(clean(sale.tipoComprobante()))) {
            throw new IllegalStateException("El comprobante creado no es una Nota de Venta");
        }
        if (!"CRM_WHATSAPP".equalsIgnoreCase(clean(sale.origen()))) {
            throw new IllegalStateException("La venta no fue marcada con origen CRM");
        }
        if (sale.total() == null || money(sale.total()).compareTo(money(payment.getExpectedAmount())) != 0) {
            throw new IllegalStateException("El total de la venta no coincide con el pago validado");
        }
    }

    private SaleResult existingResult(CrmWhatsappAgentSession session, CrmWhatsappPaymentRequest payment,
            CrmWhatsappFulfillment fulfillment) {
        Venta sale = payment.getVenta();
        if (fulfillment.getVenta() == null) {
            fulfillment.setVenta(sale);
            fulfillmentRepository.save(fulfillment);
        } else if (!Objects.equals(fulfillment.getVenta().getIdVenta(), sale.getIdVenta())) {
            throw new IllegalStateException("La entrega esta relacionada con otra venta");
        }
        if (session.getState() == CrmWhatsappAgentState.LISTO_PARA_VENTA) {
            applyEvent(session, CrmWhatsappAgentEvent.NOTA_VENTA_CREADA, now());
        } else if (session.getState() != CrmWhatsappAgentState.VENTA_PENDIENTE_REVISION) {
            throw new IllegalStateException("La sesion ya contiene una venta en un estado incompatible");
        }
        return new SaleResult(sale.getIdVenta(), sale.getTipoComprobante(), sale.getSerie(), sale.getCorrelativo(),
                sale.getTotal(), sale.getOrigen(), payment.getIdPaymentRequest(), fulfillment.getIdFulfillment(),
                payment.getConversation().getIdConversation(), payment.getReservationStatus().name(),
                session.getState().name(), true);
    }

    private SaleResult result(CrmWhatsappAgentSession session, CrmWhatsappPaymentRequest payment,
            CrmWhatsappFulfillment fulfillment, VentaResponse sale) {
        return new SaleResult(sale.idVenta(), sale.tipoComprobante(), sale.serie(), sale.correlativo(), sale.total(),
                sale.origen(), payment.getIdPaymentRequest(), fulfillment.getIdFulfillment(),
                payment.getConversation().getIdConversation(), CrmWhatsappPaymentReservationStatus.CONSUMED.name(),
                session.getState().name(), false);
    }

    private void applyEvent(CrmWhatsappAgentSession session, CrmWhatsappAgentEvent event, LocalDateTime time) {
        session.setState(stateMachine.transition(session.getState(), event));
        session.setLastEvent(event);
        session.setLastActivityAt(time);
        session.setStateChangedAt(time);
        sessionRepository.save(session);
    }

    private SaleResult execute(Long sessionId, String operationId, String toolName, String request,
            Supplier<SaleResult> action) {
        String key = operationKey(sessionId, operationId);
        String fingerprint = sha256(toolName + ":" + request);
        Optional<OperationRow> existing = operationStore.findOperation(key);
        if (existing.isPresent()) return replay(existing.get(), toolName, fingerprint);
        LocalDateTime startedAt = now();
        if (!operationStore.claimOperation(sessionId, key, toolName, fingerprint, startedAt)) {
            OperationRow concurrent = operationStore.findOperation(key)
                    .orElseThrow(() -> new IllegalStateException("La generacion de la venta esta en curso"));
            return replay(concurrent, toolName, fingerprint);
        }
        SaleResult result = action.get();
        operationStore.completeOperation(key, write(result), now());
        return result;
    }

    private SaleResult replay(OperationRow operation, String toolName, String fingerprint) {
        if (!toolName.equals(operation.toolName()) || !fingerprint.equals(operation.requestFingerprint())) {
            throw new IllegalStateException("El identificador de operacion ya fue usado con otra solicitud");
        }
        if (!"COMPLETED".equals(operation.status()) || operation.responseJson() == null) {
            throw new IllegalStateException("La generacion de la venta aun esta en curso");
        }
        try {
            return objectMapper.readValue(operation.responseJson(), SaleResult.class);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("No se pudo recuperar el resultado idempotente de la venta", error);
        }
    }

    private String operationKey(Long sessionId, String operationId) {
        if (sessionId == null) throw new IllegalArgumentException("La sesion es obligatoria");
        if (operationId == null || operationId.isBlank()) {
            throw new IllegalStateException("Falta el identificador seguro de la operacion del agente");
        }
        return sha256("sale:" + sessionId + ":" + operationId.trim());
    }

    private <T> T read(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("La solicitud de venta almacenada no es valida", error);
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("No se pudo serializar la operacion de venta", error);
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 no esta disponible", error);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private String currency(String value) {
        String normalized = clean(value).toUpperCase(Locale.ROOT);
        return normalized.matches("[A-Z]{3}") ? normalized : "PEN";
    }

    private String blankToNull(String value) {
        String normalized = clean(value);
        return normalized.isBlank() ? null : normalized;
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    public record SaleResult(
            Integer saleId,
            String receiptType,
            String series,
            Integer sequentialNumber,
            BigDecimal total,
            String origin,
            Long paymentRequestId,
            Long fulfillmentId,
            Long conversationId,
            String reservationStatus,
            String agentState,
            boolean alreadyCreated) {
    }
}
