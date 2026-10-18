package com.sistemapos.sistematextil.services.agent.payment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sistemapos.sistematextil.model.CrmWhatsappAgentEvent;
import com.sistemapos.sistematextil.model.CrmWhatsappAgentSession;
import com.sistemapos.sistematextil.model.CrmWhatsappAgentState;
import com.sistemapos.sistematextil.model.CrmWhatsappPaymentEvidence;
import com.sistemapos.sistematextil.model.CrmWhatsappPaymentEvidenceStatus;
import com.sistemapos.sistematextil.model.CrmWhatsappPaymentProcessingStatus;
import com.sistemapos.sistematextil.model.CrmWhatsappPaymentRequest;
import com.sistemapos.sistematextil.model.CrmWhatsappPaymentRequestStatus;
import com.sistemapos.sistematextil.model.CrmWhatsappPaymentReservationStatus;
import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAgentSessionRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappPaymentEvidenceRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappPaymentRequestRepository;
import com.sistemapos.sistematextil.repositories.UsuarioRepository;
import com.sistemapos.sistematextil.services.CrmWhatsappPaymentReservationService;
import com.sistemapos.sistematextil.services.agent.CrmWhatsappAgentStateMachine;
import com.sistemapos.sistematextil.services.agent.payment.AgentPaymentReceiptVisionGateway.VisionResult;
import com.sistemapos.sistematextil.services.agent.config.AgentRuntimeConfigService;
import com.sistemapos.sistematextil.services.agent.fulfillment.AgentFulfillmentService;
import com.sistemapos.sistematextil.services.agent.outbox.AgentOutboxService;
import com.sistemapos.sistematextil.util.crm.CrmWhatsappPhoneUtils;

@Service
public class AgentPaymentEvidenceAnalysisStore {
    private static final int MAX_ATTEMPTS = 3;
    private static final String AGENT_USER_EMAIL = "ia-kiments@system.local";
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("d/M/uuuu"),
            DateTimeFormatter.ofPattern("d-M-uuuu"));
    private static final List<DateTimeFormatter> TIME_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_TIME,
            DateTimeFormatter.ofPattern("H:mm"),
            DateTimeFormatter.ofPattern("H:mm:ss"));

    private final CrmWhatsappPaymentEvidenceRepository evidenceRepository;
    private final CrmWhatsappPaymentRequestRepository requestRepository;
    private final CrmWhatsappAgentSessionRepository sessionRepository;
    private final UsuarioRepository userRepository;
    private final CrmWhatsappPaymentReservationService reservationService;
    private final CrmWhatsappAgentStateMachine stateMachine;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final int minimumConfidence;
    private final AgentRuntimeConfigService runtimeConfig;
    private final AgentOutboxService outbox;
    private final AgentFulfillmentService fulfillmentService;

    @Autowired
    public AgentPaymentEvidenceAnalysisStore(
            CrmWhatsappPaymentEvidenceRepository evidenceRepository,
            CrmWhatsappPaymentRequestRepository requestRepository,
            CrmWhatsappAgentSessionRepository sessionRepository,
            UsuarioRepository userRepository,
            CrmWhatsappPaymentReservationService reservationService,
            CrmWhatsappAgentStateMachine stateMachine,
            AgentRuntimeConfigService runtimeConfig,
            AgentOutboxService outbox,
            AgentFulfillmentService fulfillmentService,
            @Value("${crm.whatsapp.agent.payment-vision.minimum-confidence:85}") int minimumConfidence,
            @Value("${crm.whatsapp.agent.payment-vision.zone-id:America/Lima}") String zoneId) {
        this(evidenceRepository, requestRepository, sessionRepository, userRepository, reservationService,
                stateMachine, new ObjectMapper().findAndRegisterModules(),
                Clock.system(ZoneId.of(zoneId)), minimumConfidence, runtimeConfig, outbox, fulfillmentService);
    }

    AgentPaymentEvidenceAnalysisStore(
            CrmWhatsappPaymentEvidenceRepository evidenceRepository,
            CrmWhatsappPaymentRequestRepository requestRepository,
            CrmWhatsappAgentSessionRepository sessionRepository,
            UsuarioRepository userRepository,
            CrmWhatsappPaymentReservationService reservationService,
            CrmWhatsappAgentStateMachine stateMachine,
            ObjectMapper objectMapper,
            Clock clock,
            int minimumConfidence) {
        this(evidenceRepository, requestRepository, sessionRepository, userRepository, reservationService,
                stateMachine, objectMapper, clock, minimumConfidence, null);
    }

    AgentPaymentEvidenceAnalysisStore(CrmWhatsappPaymentEvidenceRepository evidenceRepository,
            CrmWhatsappPaymentRequestRepository requestRepository,
            CrmWhatsappAgentSessionRepository sessionRepository, UsuarioRepository userRepository,
            CrmWhatsappPaymentReservationService reservationService, CrmWhatsappAgentStateMachine stateMachine,
            ObjectMapper objectMapper, Clock clock, int minimumConfidence,
            AgentRuntimeConfigService runtimeConfig) {
        this(evidenceRepository, requestRepository, sessionRepository, userRepository, reservationService,
                stateMachine, objectMapper, clock, minimumConfidence, runtimeConfig, null);
    }

    AgentPaymentEvidenceAnalysisStore(CrmWhatsappPaymentEvidenceRepository evidenceRepository,
            CrmWhatsappPaymentRequestRepository requestRepository,
            CrmWhatsappAgentSessionRepository sessionRepository, UsuarioRepository userRepository,
            CrmWhatsappPaymentReservationService reservationService, CrmWhatsappAgentStateMachine stateMachine,
            ObjectMapper objectMapper, Clock clock, int minimumConfidence,
            AgentRuntimeConfigService runtimeConfig, AgentOutboxService outbox) {
        this(evidenceRepository, requestRepository, sessionRepository, userRepository, reservationService,
                stateMachine, objectMapper, clock, minimumConfidence, runtimeConfig, outbox, null);
    }

    AgentPaymentEvidenceAnalysisStore(CrmWhatsappPaymentEvidenceRepository evidenceRepository,
            CrmWhatsappPaymentRequestRepository requestRepository,
            CrmWhatsappAgentSessionRepository sessionRepository, UsuarioRepository userRepository,
            CrmWhatsappPaymentReservationService reservationService, CrmWhatsappAgentStateMachine stateMachine,
            ObjectMapper objectMapper, Clock clock, int minimumConfidence,
            AgentRuntimeConfigService runtimeConfig, AgentOutboxService outbox,
            AgentFulfillmentService fulfillmentService) {
        this.evidenceRepository = evidenceRepository;
        this.requestRepository = requestRepository;
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
        this.reservationService = reservationService;
        this.stateMachine = stateMachine;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.minimumConfidence = Math.max(0, Math.min(100, minimumConfidence));
        this.runtimeConfig = runtimeConfig;
        this.outbox = outbox;
        this.fulfillmentService = fulfillmentService;
    }

    @Transactional(readOnly = true)
    public List<Long> findReadyIds(int limit) {
        return evidenceRepository.findReadyAgentEvidenceIds(
                CrmWhatsappPaymentProcessingStatus.QUEUED, now(), page(limit));
    }

    @Transactional(readOnly = true)
    public List<Long> findStaleIds(int staleMinutes, int limit) {
        return evidenceRepository.findStaleAgentEvidenceIds(
                CrmWhatsappPaymentProcessingStatus.PROCESSING,
                now().minusMinutes(Math.max(1, staleMinutes)), page(limit));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ClaimedEvidence claim(Long evidenceId) {
        Locked locked = lock(evidenceId);
        if (locked == null) return null;
        CrmWhatsappPaymentEvidence evidence = locked.evidence();
        CrmWhatsappPaymentRequest request = locked.request();
        CrmWhatsappAgentSession session = locked.session();
        LocalDateTime now = now();
        if (evidence.getProcessingStatus() != CrmWhatsappPaymentProcessingStatus.QUEUED
                || evidence.getAvailableAt() == null || evidence.getAvailableAt().isAfter(now)) return null;
        if (evidence.getAttempts() != null && evidence.getAttempts() >= MAX_ATTEMPTS) {
            finishWithoutVision(locked, AgentPaymentReceiptOutcome.OBSERVED,
                    List.of("No fue posible leer el comprobante despues de tres intentos"), now);
            return null;
        }
        boolean firstAttempt = request.getStatus() == CrmWhatsappPaymentRequestStatus.PENDING_EVIDENCE
                && session.getState() == CrmWhatsappAgentState.ESPERANDO_COMPROBANTE;
        boolean retryAttempt = request.getStatus() == CrmWhatsappPaymentRequestStatus.UNDER_REVIEW
                && session.getState() == CrmWhatsappAgentState.VALIDANDO_COMPROBANTE
                && evidence.getAttempts() != null && evidence.getAttempts() > 0;
        boolean anotherEvidenceIsBeingValidated = request.getStatus() == CrmWhatsappPaymentRequestStatus.UNDER_REVIEW
                && session.getState() == CrmWhatsappAgentState.VALIDANDO_COMPROBANTE
                && (evidence.getAttempts() == null || evidence.getAttempts() == 0);
        if (anotherEvidenceIsBeingValidated) return null;
        if (!firstAttempt && !retryAttempt) {
            markUnprocessableEvidence(evidence, "La solicitud ya avanzo con otro comprobante");
            return null;
        }
        if (request.getReservationStatus() != CrmWhatsappPaymentReservationStatus.ACTIVE
                || request.getExpiresAt() == null
                || !receivedBeforeDeadline(evidence, request)) {
            expireOrRejectUnavailable(locked, now);
            return null;
        }
        if (firstAttempt) {
            applyEvent(session, CrmWhatsappAgentEvent.COMPROBANTE_RECIBIDO, now);
            request.setStatus(CrmWhatsappPaymentRequestStatus.UNDER_REVIEW);
            request.setReviewExpiresAt(request.getExpiresAt());
            requestRepository.save(request);
        }
        evidence.setProcessingStatus(CrmWhatsappPaymentProcessingStatus.PROCESSING);
        evidence.setAttempts((evidence.getAttempts() == null ? 0 : evidence.getAttempts()) + 1);
        evidence.setLastError(null);
        if (evidence.getAgentValidationKey() == null || evidence.getAgentValidationKey().isBlank()) {
            evidence.setAgentValidationKey(validationKey(evidence));
        }
        evidenceRepository.save(evidence);
        Long connectionId = session.getConnection() == null ? null : session.getConnection().getIdConnection();
        return new ClaimedEvidence(evidence.getIdPaymentEvidence(), connectionId,
                evidence.getStoragePath(), evidence.getMimeType(), evidence.getAttempts());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AnalysisResult complete(Long evidenceId, VisionResult vision) {
        Locked locked = lock(evidenceId);
        if (locked == null || locked.evidence().getProcessingStatus() != CrmWhatsappPaymentProcessingStatus.PROCESSING) {
            return null;
        }
        CrmWhatsappPaymentEvidence evidence = locked.evidence();
        AgentPaymentReceiptExtraction extraction = Objects.requireNonNull(vision.extraction(), "Extraccion requerida");
        persistExtraction(evidence, vision);
        Validation validation = validate(locked, extraction);
        applyOutcome(locked, validation, now());
        return result(locked, validation);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AnalysisResult fail(Long evidenceId, RuntimeException error, boolean retryable) {
        Locked locked = lock(evidenceId);
        if (locked == null || locked.evidence().getProcessingStatus() != CrmWhatsappPaymentProcessingStatus.PROCESSING) {
            return null;
        }
        CrmWhatsappPaymentEvidence evidence = locked.evidence();
        String message = truncate(error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage(), 1000);
        evidence.setLastError(message);
        int attempts = evidence.getAttempts() == null ? 1 : evidence.getAttempts();
        if (retryable && attempts < MAX_ATTEMPTS && reservationCanWait(locked.request(), evidence)) {
            evidence.setProcessingStatus(CrmWhatsappPaymentProcessingStatus.QUEUED);
            evidence.setAvailableAt(now().plusSeconds((long) attempts * 5L));
            evidenceRepository.save(evidence);
            return new AnalysisResult(evidenceId, null, "RETRY_SCHEDULED", List.of(message),
                    locked.session().getState().name());
        }
        Validation validation = new Validation(AgentPaymentReceiptOutcome.OBSERVED,
                List.of("No se pudo leer el comprobante: " + message), null, null, BigDecimal.ZERO);
        applyOutcome(locked, validation, now());
        evidence.setProcessingStatus(CrmWhatsappPaymentProcessingStatus.FAILED);
        evidenceRepository.save(evidence);
        return result(locked, validation);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean recoverStale(Long evidenceId) {
        Locked locked = lock(evidenceId);
        if (locked == null || locked.evidence().getProcessingStatus() != CrmWhatsappPaymentProcessingStatus.PROCESSING) {
            return false;
        }
        CrmWhatsappPaymentEvidence evidence = locked.evidence();
        evidence.setProcessingStatus(CrmWhatsappPaymentProcessingStatus.QUEUED);
        evidence.setAvailableAt(now());
        evidence.setLastError("Procesamiento recuperado despues de reiniciar el backend");
        evidenceRepository.save(evidence);
        return true;
    }

    private Validation validate(Locked locked, AgentPaymentReceiptExtraction extraction) {
        CrmWhatsappPaymentEvidence evidence = locked.evidence();
        CrmWhatsappPaymentRequest request = locked.request();
        List<String> warnings = new ArrayList<>();
        Integer companyId = request.getConnection() == null || request.getConnection().getEmpresa() == null
                ? null : request.getConnection().getEmpresa().getIdEmpresa();
        evidence.setDuplicateOfId(null);

        BigDecimal amount = parseAmount(extraction.amount());
        LocalDateTime operationAt = parseOperationAt(extraction.operationDate(), extraction.operationTime());
        String operation = normalizeOperation(extraction.operationNumber());
        if (amount == null) warnings.add("No se pudo leer el monto");
        if (operationAt == null) warnings.add("No se pudo leer la fecha de operación");
        if (!warnings.isEmpty()) return observed(warnings);

        BigDecimal expected = request.getExpectedAmount() == null ? null
                : request.getExpectedAmount().setScale(2, RoundingMode.HALF_UP);
        BigDecimal includedFilm = expected != null && expected.compareTo(amount) == 0
                ? BigDecimal.ZERO.setScale(2)
                : expected != null && expected.add(new BigDecimal("2.00")).compareTo(amount) == 0
                        ? new BigDecimal("2.00") : null;
        if (includedFilm == null) {
            return rejected("El monto no coincide con el total exacto del pedido", warnings);
        }
        if (!validOperationDate(request, locked.evidence(), operationAt)) {
            return rejected("La fecha del pago no corresponde a la solicitud", warnings);
        }
        CrmWhatsappPaymentEvidence operationDuplicate = companyId == null || operation.isBlank() ? null
                : evidenceRepository
                        .findOtherByOperation(evidence.getIdPaymentEvidence(), operation, companyId, PageRequest.of(0, 1))
                        .stream().findFirst().orElse(null);
        if (operationDuplicate != null) {
            evidence.setDuplicateOfId(operationDuplicate.getIdPaymentEvidence());
            return rejected("El numero de operacion ya fue utilizado", warnings);
        }
        return new Validation(AgentPaymentReceiptOutcome.APPROVED_INITIAL, warnings, amount, operationAt,
                includedFilm);
    }

    private void applyOutcome(Locked locked, Validation validation, LocalDateTime now) {
        CrmWhatsappPaymentEvidence evidence = locked.evidence();
        CrmWhatsappPaymentRequest request = locked.request();
        CrmWhatsappAgentSession session = locked.session();
        evidence.setWarningsJson(write(validation.warnings()));
        evidence.setProcessingStatus(CrmWhatsappPaymentProcessingStatus.COMPLETED);
        switch (validation.outcome()) {
            case APPROVED_INITIAL -> {
                request.setIncludedFilmAmount(validation.includedFilmAmount());
                boolean combinedFilm = validation.includedFilmAmount().signum() > 0;
                if (combinedFilm) {
                    session.setPreferredFulfillmentMethod(
                            com.sistemapos.sistematextil.model.CrmWhatsappFulfillmentMethod.SHALOM);
                }
                evidence.setValidationStatus(CrmWhatsappPaymentEvidenceStatus.ACEPTABLE);
                request.setStatus(CrmWhatsappPaymentRequestStatus.UNDER_REVIEW);
                request.setEvidenceAcceptedAt(now);
                request.setReviewExpiresAt(now.plusHours(24));
                applyEvent(session, combinedFilm
                        ? CrmWhatsappAgentEvent.COMPROBANTE_COMBINADO_APROBADO
                        : CrmWhatsappAgentEvent.COMPROBANTE_APROBADO_INICIALMENTE, now);
                if (!combinedFilm && hasCompleteCustomer(session)) {
                    request.setCliente(session.getCliente());
                    applyEvent(session, CrmWhatsappAgentEvent.DATOS_CLIENTE_COMPLETADOS, now);
                } else if (combinedFilm) {
                    if (hasCompleteCustomer(session)) request.setCliente(session.getCliente());
                    requestRepository.save(request);
                    if (fulfillmentService != null) fulfillmentService.initializeCombinedShalom(
                            session.getIdAgentSession(),
                            "combined-main-payment:" + evidence.getIdPaymentEvidence());
                }
            }
            case OBSERVED -> {
                evidence.setValidationStatus(CrmWhatsappPaymentEvidenceStatus.OBSERVADO);
                restoreOrExpireRequest(locked, CrmWhatsappAgentEvent.COMPROBANTE_OBSERVADO, now);
            }
            case REJECTED -> {
                evidence.setValidationStatus(CrmWhatsappPaymentEvidenceStatus.RECHAZADO);
                restoreOrExpireRequest(locked, CrmWhatsappAgentEvent.COMPROBANTE_RECHAZADO_INICIALMENTE, now);
            }
        }
        evidenceRepository.save(evidence);
        requestRepository.save(request);
        notifyCustomer(locked, validation);
    }

    private void notifyCustomer(Locked locked, Validation validation) {
        if (outbox == null || locked.evidence().getCustomerNotifiedAt() != null) return;
        String message = switch (validation.outcome()) {
            case APPROVED_INITIAL -> approvedCustomerMessage(locked.session(), locked.request());
            case OBSERVED -> "⚠️ No pude validar el comprobante.\n"
                    + String.join("\n", validation.warnings())
                    + "\n\nEnvía una nueva captura antes de que venza la reserva.";
            case REJECTED -> "❌ El comprobante no es válido para este pedido.\n"
                    + String.join("\n", validation.warnings())
                    + "\n\nEnvía un comprobante correcto antes de que venza la reserva.";
        };
        Long replyTo = locked.evidence().getMessage() == null
                ? null : locked.evidence().getMessage().getIdMessage();
        int ordinal = Math.floorMod(Objects.hash("PAYMENT_OUTCOME", locked.evidence().getIdPaymentEvidence()),
                Integer.MAX_VALUE);
        outbox.enqueueText(locked.session().getIdAgentSession(), null, replyTo, ordinal, message);
        locked.evidence().setCustomerNotifiedAt(now());
        evidenceRepository.save(locked.evidence());
    }

    String approvedCustomerMessage(CrmWhatsappAgentSession session) {
        return approvedCustomerMessage(session, null);
    }

    String approvedCustomerMessage(CrmWhatsappAgentSession session, CrmWhatsappPaymentRequest request) {
        boolean combinedFilm = request != null && request.getIncludedFilmAmount() != null
                && request.getIncludedFilmAmount().signum() > 0;
        if (combinedFilm) {
            return combinedPaymentMessage(request)
                    + "\n\nPara registrar tu compra y coordinar el envío, envíame:\n"
                    + "☑️ Nombres y apellidos\n"
                    + "☑️ DNI\n"
                    + "☑️ Celular\n"
                    + "☑️ Agencia de Shalom\n\n"
                    + "Puedes enviarlos juntos o por partes; guardaré cada dato válido y te indicaré únicamente lo que falte.";
        }
        if (hasCompleteCustomer(session)) {
            return "✅ Comprobante validado correctamente.\n\n¿Cómo deseas recibir tu pedido?\n"
                    + "🏬 Recojo en tienda\n🚌 Envío por Shalom";
        }
        if (!conversationPhone(session).isBlank()) {
            return "✅ Comprobante validado correctamente.\n\nPara registrar tu compra, envíame tu nombre, por favor.";
        }
        return "✅ Comprobante validado correctamente.\n\nPara registrar tu compra, envíame:\n"
                + "☑️ Tu nombre\n☑️ Tu número de celular";
    }

    private String combinedPaymentMessage(CrmWhatsappPaymentRequest request) {
        String method = request.getMetodoPago() == null ? "METODO SELECCIONADO"
                : clean(request.getMetodoPago().getNombre());
        return "✅ Pago confirmado correctamente, hermosa 💛\n"
                + "🧾 Pago de prendas: S/" + moneyText(request.getExpectedAmount()) + "\n"
                + "📦 Film protector: S/2.00 — Pagado\n"
                + "💳 Metodo: " + method + "\n"
                + "🚌 Tipo de entrega: Envio por Shalom";
    }

    private String moneyText(BigDecimal value) {
        return value == null ? "0.00" : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String conversationPhone(CrmWhatsappAgentSession session) {
        if (session == null || session.getConversation() == null) return "";
        String phone = CrmWhatsappPhoneUtils.normalizePeruvianMobile(session.getConversation().getPhoneNumber());
        return phone.isBlank()
                ? CrmWhatsappPhoneUtils.normalizePeruvianMobile(session.getConversation().getPhone()) : phone;
    }

    private boolean hasCompleteCustomer(CrmWhatsappAgentSession session) {
        return session.getCliente() != null && session.getCliente().getIdCliente() != null
                && clean(session.getCliente().getNombres()).length() >= 2
                && !clean(session.getCliente().getNombres()).toUpperCase(Locale.ROOT).startsWith("CLIENTE ")
                && !clean(session.getCliente().getTelefono()).isBlank();
    }

    private void restoreOrExpireRequest(Locked locked, CrmWhatsappAgentEvent event, LocalDateTime now) {
        CrmWhatsappPaymentRequest request = locked.request();
        LocalDateTime originalDeadline = request.getReviewExpiresAt() == null
                ? request.getExpiresAt() : request.getReviewExpiresAt();
        if (originalDeadline == null || !originalDeadline.isAfter(now)) {
            expireRequest(locked, now);
            return;
        }
        request.setStatus(CrmWhatsappPaymentRequestStatus.PENDING_EVIDENCE);
        request.setExpiresAt(originalDeadline);
        request.setReviewExpiresAt(null);
        applyEvent(locked.session(), event, now);
    }

    private void expireOrRejectUnavailable(Locked locked, LocalDateTime now) {
        List<String> warnings = List.of("La reserva del pedido no esta vigente");
        locked.evidence().setWarningsJson(write(warnings));
        locked.evidence().setValidationStatus(CrmWhatsappPaymentEvidenceStatus.RECHAZADO);
        locked.evidence().setProcessingStatus(CrmWhatsappPaymentProcessingStatus.COMPLETED);
        if (locked.request().getReservationStatus() == CrmWhatsappPaymentReservationStatus.ACTIVE) {
            expireRequest(locked, now);
        } else if (stateMachine.canTransition(
                locked.session().getState(), CrmWhatsappAgentEvent.SOLICITUD_PAGO_EXPIRADA)) {
            applyEvent(locked.session(), CrmWhatsappAgentEvent.SOLICITUD_PAGO_EXPIRADA, now);
        }
        evidenceRepository.save(locked.evidence());
    }

    private void markUnprocessableEvidence(CrmWhatsappPaymentEvidence evidence, String reason) {
        evidence.setWarningsJson(write(List.of(reason)));
        evidence.setValidationStatus(CrmWhatsappPaymentEvidenceStatus.OBSERVADO);
        evidence.setProcessingStatus(CrmWhatsappPaymentProcessingStatus.COMPLETED);
        evidenceRepository.save(evidence);
    }

    private void expireRequest(Locked locked, LocalDateTime now) {
        CrmWhatsappPaymentRequest request = locked.request();
        if (request.getReservationStatus() == CrmWhatsappPaymentReservationStatus.ACTIVE) {
            reservationService.release(request, requireAgentUser(), "Comprobante no validado antes del vencimiento");
        }
        request.setStatus(CrmWhatsappPaymentRequestStatus.EXPIRED);
        request.setReviewExpiresAt(null);
        requestRepository.save(request);
        CrmWhatsappAgentSession session = locked.session();
        if (stateMachine.canTransition(session.getState(), CrmWhatsappAgentEvent.SOLICITUD_PAGO_EXPIRADA)) {
            applyEvent(session, CrmWhatsappAgentEvent.SOLICITUD_PAGO_EXPIRADA, now);
        }
    }

    private void finishWithoutVision(Locked locked, AgentPaymentReceiptOutcome outcome,
            List<String> warnings, LocalDateTime now) {
        applyOutcome(locked, new Validation(outcome, warnings, null, null, BigDecimal.ZERO), now);
    }

    private Locked lock(Long evidenceId) {
        if (evidenceId == null) return null;
        CrmWhatsappPaymentEvidence snapshot = evidenceRepository.findById(evidenceId).orElse(null);
        if (snapshot == null || snapshot.getAgentSession() == null || snapshot.getPaymentRequest() == null) return null;
        Long sessionId = snapshot.getAgentSession().getIdAgentSession();
        Long requestId = snapshot.getPaymentRequest().getIdPaymentRequest();
        CrmWhatsappAgentSession session = sessionRepository.findForUpdate(sessionId).orElse(null);
        CrmWhatsappPaymentRequest request = requestRepository.findForUpdate(requestId).orElse(null);
        CrmWhatsappPaymentEvidence evidence = evidenceRepository.findForUpdate(evidenceId).orElse(null);
        if (session == null || request == null || evidence == null
                || evidence.getAgentSession() == null
                || !sessionId.equals(evidence.getAgentSession().getIdAgentSession())
                || evidence.getPaymentRequest() == null
                || !requestId.equals(evidence.getPaymentRequest().getIdPaymentRequest())) return null;
        return new Locked(evidence, request, session);
    }

    private void persistExtraction(CrmWhatsappPaymentEvidence evidence, VisionResult vision) {
        AgentPaymentReceiptExtraction extraction = vision.extraction();
        evidence.setDetectedProvider(truncate(clean(extraction.paymentMethod()), 40));
        evidence.setDetectedAmount(parseAmount(extraction.amount()));
        evidence.setDetectedCurrency(currency(extraction.currency()));
        evidence.setOperationCode(blankToNull(normalizeOperation(extraction.operationNumber())));
        evidence.setOperationAt(parseOperationAt(extraction.operationDate(), extraction.operationTime()));
        evidence.setRecipient(truncate(blankToNull(extraction.recipient()), 180));
        evidence.setConfidence(extraction.confidence());
        evidence.setExtractedText(truncate(blankToNull(extraction.visibleText()), 65000));
        evidence.setExtractionJson(write(vision));
    }

    private boolean receivedBeforeDeadline(CrmWhatsappPaymentEvidence evidence, CrmWhatsappPaymentRequest request) {
        LocalDateTime receivedAt = evidence.getCreatedAt();
        return receivedAt == null || !receivedAt.isAfter(request.getExpiresAt());
    }

    private boolean reservationCanWait(CrmWhatsappPaymentRequest request, CrmWhatsappPaymentEvidence evidence) {
        LocalDateTime originalDeadline = request.getReviewExpiresAt() == null
                ? request.getExpiresAt() : request.getReviewExpiresAt();
        return request.getReservationStatus() == CrmWhatsappPaymentReservationStatus.ACTIVE
                && receivedBeforeDeadline(evidence, request)
                && originalDeadline != null;
    }

    private boolean validOperationDate(CrmWhatsappPaymentRequest request,
            CrmWhatsappPaymentEvidence evidence, LocalDateTime operationAt) {
        LocalDate start = Optional.ofNullable(request.getCreatedAt())
                .orElseGet(() -> Optional.ofNullable(request.getReservedAt()).orElse(now().minusMinutes(4)))
                .toLocalDate();
        LocalDate received = Optional.ofNullable(evidence.getCreatedAt()).orElse(now()).toLocalDate();
        LocalDate operationDate = operationAt.toLocalDate();
        return !operationDate.isBefore(start) && !operationDate.isAfter(received);
    }

    private BigDecimal parseAmount(String value) {
        String normalized = clean(value).replace(" ", "").replaceAll("[^0-9,.-]", "");
        if (normalized.isBlank()) return null;
        if (normalized.contains(",") && normalized.contains(".")) {
            normalized = normalized.lastIndexOf(',') > normalized.lastIndexOf('.')
                    ? normalized.replace(".", "").replace(',', '.')
                    : normalized.replace(",", "");
        } else {
            normalized = normalized.replace(',', '.');
        }
        try {
            return new BigDecimal(normalized).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException error) {
            return null;
        }
    }

    private LocalDateTime parseOperationAt(String date, String time) {
        LocalDate parsedDate = parseDate(date);
        LocalTime parsedTime = parseTime(time);
        return parsedDate == null ? null : LocalDateTime.of(parsedDate,
                parsedTime == null ? LocalTime.MIDNIGHT : parsedTime);
    }

    private LocalDate parseDate(String value) {
        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try {
                return LocalDate.parse(clean(value), formatter);
            } catch (DateTimeParseException ignored) {
                // Try the next accepted representation.
            }
        }
        return null;
    }

    private LocalTime parseTime(String value) {
        for (DateTimeFormatter formatter : TIME_FORMATS) {
            try {
                return LocalTime.parse(clean(value), formatter);
            } catch (DateTimeParseException ignored) {
                // Try the next accepted representation.
            }
        }
        return null;
    }

    private Validation rejected(String reason, List<String> warnings) {
        warnings.add(reason);
        return new Validation(AgentPaymentReceiptOutcome.REJECTED, warnings, null, null, BigDecimal.ZERO);
    }

    private Validation observed(List<String> warnings) {
        return new Validation(AgentPaymentReceiptOutcome.OBSERVED, warnings, null, null, BigDecimal.ZERO);
    }

    private AnalysisResult result(Locked locked, Validation validation) {
        return new AnalysisResult(locked.evidence().getIdPaymentEvidence(), validation.outcome(),
                locked.evidence().getValidationStatus().name(), List.copyOf(validation.warnings()),
                locked.session().getState().name());
    }

    private void applyEvent(CrmWhatsappAgentSession session, CrmWhatsappAgentEvent event, LocalDateTime time) {
        session.setState(stateMachine.transition(session.getState(), event));
        session.setLastEvent(event);
        session.setLastActivityAt(time);
        session.setStateChangedAt(time);
        sessionRepository.save(session);
    }

    private Usuario requireAgentUser() {
        return userRepository.findByCorreoAndDeletedAtIsNull(AGENT_USER_EMAIL)
                .or(() -> userRepository.findByCorreoAndDeletedAtIsNull("sistema@gmail.com"))
                .orElseThrow(() -> new IllegalStateException("No existe un usuario tecnico para liberar la reserva"));
    }

    private String validationKey(CrmWhatsappPaymentEvidence evidence) {
        String value = "vision:" + evidence.getAgentSession().getIdAgentSession() + ":"
                + evidence.getIdPaymentEvidence() + ":" + evidence.getSha256();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes()));
        } catch (Exception error) {
            throw new IllegalStateException("No se pudo crear la clave de validacion", error);
        }
    }

    private String normalizeOperation(String value) {
        return clean(value).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    private String currency(String value) {
        String normalized = clean(value).toUpperCase(Locale.ROOT);
        if (normalized.equals("S/") || normalized.equals("SOLES")) return "PEN";
        return normalized.matches("[A-Z]{3}") ? normalized : null;
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("No se pudo guardar el analisis del comprobante", error);
        }
    }

    private PageRequest page(int limit) {
        return PageRequest.of(0, Math.max(1, Math.min(limit, 200)));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private String blankToNull(String value) {
        String clean = clean(value);
        return clean.isBlank() ? null : clean;
    }

    private String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    private record Locked(CrmWhatsappPaymentEvidence evidence, CrmWhatsappPaymentRequest request,
            CrmWhatsappAgentSession session) {
    }

    private record Validation(AgentPaymentReceiptOutcome outcome, List<String> warnings,
            BigDecimal amount, LocalDateTime operationAt, BigDecimal includedFilmAmount) {
    }

    public record ClaimedEvidence(Long evidenceId, Long connectionId, String storagePath,
            String mimeType, int attempt) {
    }

    public record AnalysisResult(Long evidenceId, AgentPaymentReceiptOutcome outcome,
            String validationStatus, List<String> warnings, String agentState) {
    }
}
