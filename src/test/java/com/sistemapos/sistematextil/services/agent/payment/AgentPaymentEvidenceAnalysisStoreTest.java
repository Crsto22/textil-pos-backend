package com.sistemapos.sistematextil.services.agent.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sistemapos.sistematextil.model.CrmWhatsappAgentEvent;
import com.sistemapos.sistematextil.model.CrmWhatsappAgentSession;
import com.sistemapos.sistematextil.model.CrmWhatsappAgentState;
import com.sistemapos.sistematextil.model.CrmWhatsappConnection;
import com.sistemapos.sistematextil.model.CrmWhatsappConversation;
import com.sistemapos.sistematextil.model.CrmWhatsappPaymentEvidence;
import com.sistemapos.sistematextil.model.CrmWhatsappPaymentEvidenceStatus;
import com.sistemapos.sistematextil.model.CrmWhatsappPaymentProcessingStatus;
import com.sistemapos.sistematextil.model.CrmWhatsappPaymentRequest;
import com.sistemapos.sistematextil.model.CrmWhatsappPaymentRequestStatus;
import com.sistemapos.sistematextil.model.CrmWhatsappPaymentReservationStatus;
import com.sistemapos.sistematextil.model.Cliente;
import com.sistemapos.sistematextil.model.Empresa;
import com.sistemapos.sistematextil.model.MetodoPagoConfig;
import com.sistemapos.sistematextil.model.MetodoPagoCuenta;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAgentSessionRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappPaymentEvidenceRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappPaymentRequestRepository;
import com.sistemapos.sistematextil.repositories.UsuarioRepository;
import com.sistemapos.sistematextil.services.CrmWhatsappPaymentReservationService;
import com.sistemapos.sistematextil.services.agent.CrmWhatsappAgentStateMachine;
import com.sistemapos.sistematextil.services.agent.fulfillment.AgentFulfillmentService;
import com.sistemapos.sistematextil.services.agent.payment.AgentPaymentEvidenceAnalysisStore.AnalysisResult;
import com.sistemapos.sistematextil.services.agent.payment.AgentPaymentEvidenceAnalysisStore.ClaimedEvidence;
import com.sistemapos.sistematextil.services.agent.payment.AgentPaymentReceiptVisionGateway.VisionResult;

class AgentPaymentEvidenceAnalysisStoreTest {
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-10-08T19:02:00Z"), ZoneId.of("America/Lima"));

    private CrmWhatsappPaymentEvidenceRepository evidenceRepository;
    private CrmWhatsappPaymentRequestRepository requestRepository;
    private CrmWhatsappAgentSessionRepository sessionRepository;
    private CrmWhatsappPaymentReservationService reservationService;
    private AgentFulfillmentService fulfillmentService;
    private AgentPaymentEvidenceAnalysisStore store;
    private CrmWhatsappAgentSession session;
    private CrmWhatsappPaymentRequest request;
    private CrmWhatsappPaymentEvidence evidence;

    @BeforeEach
    void setUp() {
        evidenceRepository = mock(CrmWhatsappPaymentEvidenceRepository.class);
        requestRepository = mock(CrmWhatsappPaymentRequestRepository.class);
        sessionRepository = mock(CrmWhatsappAgentSessionRepository.class);
        UsuarioRepository userRepository = mock(UsuarioRepository.class);
        reservationService = mock(CrmWhatsappPaymentReservationService.class);
        fulfillmentService = mock(AgentFulfillmentService.class);
        store = new AgentPaymentEvidenceAnalysisStore(evidenceRepository, requestRepository, sessionRepository,
                userRepository, reservationService, new CrmWhatsappAgentStateMachine(),
                new ObjectMapper().findAndRegisterModules(), CLOCK, 85, null, null, fulfillmentService);

        Empresa company = new Empresa();
        company.setIdEmpresa(3);
        CrmWhatsappConnection connection = new CrmWhatsappConnection();
        connection.setIdConnection(7L);
        connection.setEmpresa(company);
        session = new CrmWhatsappAgentSession();
        session.setIdAgentSession(41L);
        session.setConnection(connection);
        session.setState(CrmWhatsappAgentState.VALIDANDO_COMPROBANTE);
        session.setActiveSlot(true);

        MetodoPagoConfig method = new MetodoPagoConfig();
        method.setIdMetodoPago(2);
        method.setNombre("YAPE");
        MetodoPagoCuenta account = new MetodoPagoCuenta();
        account.setIdMetodoPagoCuenta(9);
        account.setMetodoPago(method);
        account.setNumeroCuenta("999000111");
        account.setTitular("Kiments SAC");
        account.setAliasesValidacion("Kiments, Modas Peru");

        request = new CrmWhatsappPaymentRequest();
        request.setIdPaymentRequest(77L);
        request.setAgentSession(session);
        request.setConnection(connection);
        request.setMetodoPago(method);
        request.setCuenta(account);
        request.setExpectedAmount(new BigDecimal("140.00"));
        request.setCurrency("PEN");
        request.setStatus(CrmWhatsappPaymentRequestStatus.UNDER_REVIEW);
        request.setReservationStatus(CrmWhatsappPaymentReservationStatus.ACTIVE);
        request.setCreatedAt(LocalDateTime.of(2026, 10, 8, 14, 0));
        request.setExpiresAt(LocalDateTime.of(2026, 10, 8, 14, 4));
        request.setReviewExpiresAt(LocalDateTime.of(2026, 10, 8, 14, 4));

        evidence = new CrmWhatsappPaymentEvidence();
        evidence.setIdPaymentEvidence(91L);
        evidence.setAgentSession(session);
        evidence.setPaymentRequest(request);
        evidence.setProcessingStatus(CrmWhatsappPaymentProcessingStatus.PROCESSING);
        evidence.setValidationStatus(CrmWhatsappPaymentEvidenceStatus.PENDIENTE_VALIDACION);
        evidence.setSha256("abc123");
        evidence.setAttempts(1);
        evidence.setCreatedAt(LocalDateTime.of(2026, 10, 8, 14, 1));

        when(evidenceRepository.findById(91L)).thenReturn(Optional.of(evidence));
        when(evidenceRepository.findForUpdate(91L)).thenReturn(Optional.of(evidence));
        when(requestRepository.findForUpdate(77L)).thenReturn(Optional.of(request));
        when(sessionRepository.findForUpdate(41L)).thenReturn(Optional.of(session));
        when(evidenceRepository.findOtherByHash(any(), anyString(), any(), any())).thenReturn(List.of());
        when(evidenceRepository.findOtherByOperation(any(), anyString(), any(), any())).thenReturn(List.of());
    }

    @Test
    void claimsEvidenceAndPausesTheFourMinuteExpiryWhileVisionRuns() {
        session.setState(CrmWhatsappAgentState.ESPERANDO_COMPROBANTE);
        request.setStatus(CrmWhatsappPaymentRequestStatus.PENDING_EVIDENCE);
        request.setReviewExpiresAt(null);
        evidence.setProcessingStatus(CrmWhatsappPaymentProcessingStatus.QUEUED);
        evidence.setAttempts(0);
        evidence.setAvailableAt(LocalDateTime.of(2026, 10, 8, 14, 1));
        evidence.setStoragePath("/storage/proof.png");
        evidence.setMimeType("image/png");

        ClaimedEvidence claimed = store.claim(91L);

        assertNotNull(claimed);
        assertEquals(CrmWhatsappPaymentProcessingStatus.PROCESSING, evidence.getProcessingStatus());
        assertEquals(1, evidence.getAttempts());
        assertEquals(CrmWhatsappPaymentRequestStatus.UNDER_REVIEW, request.getStatus());
        assertEquals(LocalDateTime.of(2026, 10, 8, 14, 4), request.getReviewExpiresAt());
        assertEquals(CrmWhatsappAgentState.VALIDANDO_COMPROBANTE, session.getState());
        assertEquals(CrmWhatsappAgentEvent.COMPROBANTE_RECIBIDO, session.getLastEvent());
    }

    @Test
    void leavesASecondEvidenceQueuedWhileAnotherOneIsBeingValidated() {
        evidence.setProcessingStatus(CrmWhatsappPaymentProcessingStatus.QUEUED);
        evidence.setAttempts(0);
        evidence.setAvailableAt(LocalDateTime.of(2026, 10, 8, 14, 1));

        assertNull(store.claim(91L));

        assertEquals(CrmWhatsappPaymentProcessingStatus.QUEUED, evidence.getProcessingStatus());
        assertEquals(CrmWhatsappPaymentRequestStatus.UNDER_REVIEW, request.getStatus());
        assertEquals(CrmWhatsappAgentState.VALIDANDO_COMPROBANTE, session.getState());
        verify(reservationService, never()).release(any(), any(), anyString());
    }

    @Test
    void approvesInitiallyOnlyWhenEveryBackendCheckMatches() {
        AnalysisResult result = store.complete(91L, vision(95, "140.00", "OP-123"));

        assertEquals(AgentPaymentReceiptOutcome.APPROVED_INITIAL, result.outcome());
        assertEquals(CrmWhatsappPaymentEvidenceStatus.ACEPTABLE, evidence.getValidationStatus());
        assertEquals(new BigDecimal("140.00"), evidence.getDetectedAmount());
        assertEquals(new BigDecimal("0.00"), request.getIncludedFilmAmount());
        assertEquals("OP123", evidence.getOperationCode());
        assertEquals(CrmWhatsappAgentState.ESPERANDO_DATOS_CLIENTE, session.getState());
        assertEquals(CrmWhatsappAgentEvent.COMPROBANTE_APROBADO_INICIALMENTE, session.getLastEvent());
        assertEquals(CrmWhatsappPaymentRequestStatus.UNDER_REVIEW, request.getStatus());
        assertEquals(LocalDateTime.of(2026, 10, 8, 14, 4), request.getExpiresAt());
        assertEquals(LocalDateTime.of(2026, 10, 8, 14, 2), request.getEvidenceAcceptedAt());
        assertEquals(LocalDateTime.of(2026, 10, 9, 14, 2), request.getReviewExpiresAt());
        verify(reservationService, never()).release(any(), any(), anyString());
    }

    @Test
    void skipsCustomerConfirmationWhenSessionAlreadyHasCompleteCustomerData() {
        Cliente customer = new Cliente();
        customer.setIdCliente(15);
        customer.setNombres("Cristhofer Leonardo");
        customer.setTelefono("932889985");
        session.setCliente(customer);

        AnalysisResult result = store.complete(91L, vision(95, "140.00", "OP-123"));

        assertEquals(AgentPaymentReceiptOutcome.APPROVED_INITIAL, result.outcome());
        assertEquals(customer, request.getCliente());
        assertEquals(CrmWhatsappAgentState.ESPERANDO_METODO_ENTREGA, session.getState());
        assertEquals(CrmWhatsappAgentEvent.DATOS_CLIENTE_COMPLETADOS, session.getLastEvent());
    }

    @Test
    void requestsOnlyTheNameWhenWhatsappAlreadyProvidesAValidPhone() {
        CrmWhatsappConversation conversation = new CrmWhatsappConversation();
        conversation.setPhone("51932889985@s.whatsapp.net");
        session.setConversation(conversation);

        String message = store.approvedCustomerMessage(session);

        assertTrue(message.contains("tu nombre"));
        assertFalse(message.contains("nombre completo"));
        assertFalse(message.contains("Número de celular"));
    }

    @Test
    void approvesWithExactAmountDateAndOperationEvenWhenInformativeFieldsAreEmpty() {
        AgentPaymentReceiptExtraction extraction = new AgentPaymentReceiptExtraction(
                true, "140.00", "", "2026-10-08", "",
                "", "", "OP-ONLY-123", "", "", 96);

        AnalysisResult result = store.complete(91L,
                new VisionResult("GEMINI", "gemini-test", "TEST", 50L, extraction));

        assertEquals(AgentPaymentReceiptOutcome.APPROVED_INITIAL, result.outcome());
        assertEquals(CrmWhatsappPaymentEvidenceStatus.ACEPTABLE, evidence.getValidationStatus());
        assertEquals(new BigDecimal("140.00"), evidence.getDetectedAmount());
        assertEquals("OPONLY123", evidence.getOperationCode());
        assertEquals(LocalDateTime.of(2026, 10, 8, 0, 0), evidence.getOperationAt());
    }

    @Test
    void resolvesAReceiptDateWithoutYearInsideThePaymentRequestWindow() {
        AgentPaymentReceiptExtraction extraction = new AgentPaymentReceiptExtraction(
                true, "140.00", "PEN", "--10-08", "14:01:00",
                "Kiments SAC", "999000111", "OP-NO-YEAR", "PLIN",
                "Pagaste con Plin 8 oct., 2:01 p. m.", 96);

        AnalysisResult result = store.complete(91L,
                new VisionResult("GEMINI", "gemini-test", "TEST", 50L, extraction));

        assertEquals(AgentPaymentReceiptOutcome.APPROVED_INITIAL, result.outcome());
        assertEquals(LocalDateTime.of(2026, 10, 8, 14, 1), evidence.getOperationAt());
    }

    @Test
    void doesNotGuessAYearWhenTheYearlessDateFallsOutsideTheRequestWindow() {
        AgentPaymentReceiptExtraction extraction = new AgentPaymentReceiptExtraction(
                true, "140.00", "PEN", "--10-07", "14:01:00",
                "Kiments SAC", "999000111", "OP-NO-YEAR-OLD", "PLIN",
                "Pagaste con Plin 7 oct., 2:01 p. m.", 96);

        AnalysisResult result = store.complete(91L,
                new VisionResult("GEMINI", "gemini-test", "TEST", 50L, extraction));

        assertEquals(AgentPaymentReceiptOutcome.OBSERVED, result.outcome());
        assertNull(evidence.getOperationAt());
    }

    @Test
    void approvesWhenVisionConfidenceIsBelowMinimumButAmountAndDateAreValid() {
        AnalysisResult result = store.complete(91L, vision(70, "140.00", "OP-123"));

        assertEquals(AgentPaymentReceiptOutcome.APPROVED_INITIAL, result.outcome());
        assertEquals(CrmWhatsappPaymentEvidenceStatus.ACEPTABLE, evidence.getValidationStatus());
    }

    @Test
    void approvesWhenOperationCodeCannotBeRead() {
        AnalysisResult result = store.complete(91L, vision(95, "140.00", ""));

        assertEquals(AgentPaymentReceiptOutcome.APPROVED_INITIAL, result.outcome());
        assertEquals(CrmWhatsappPaymentEvidenceStatus.ACEPTABLE, evidence.getValidationStatus());
        assertNull(evidence.getOperationCode());
        verify(evidenceRepository, never()).findOtherByOperation(any(), anyString(), any(), any());
    }

    @Test
    void observesWhenPaymentDateCannotBeRead() {
        AgentPaymentReceiptExtraction extraction = new AgentPaymentReceiptExtraction(
                false, "140.00", "", "", "",
                "", "", "", "", "", 10);

        AnalysisResult result = store.complete(91L,
                new VisionResult("GEMINI", "gemini-test", "TEST", 50L, extraction));

        assertEquals(AgentPaymentReceiptOutcome.OBSERVED, result.outcome());
        assertTrue(result.warnings().stream().anyMatch(value -> value.contains("fecha de operación")));
    }

    @Test
    void rejectsWhenPaymentDateDoesNotBelongToTheRequest() {
        AgentPaymentReceiptExtraction extraction = new AgentPaymentReceiptExtraction(
                false, "140.00", "", "2026-10-07", "",
                "", "", "", "", "", 10);

        AnalysisResult result = store.complete(91L,
                new VisionResult("GEMINI", "gemini-test", "TEST", 50L, extraction));

        assertEquals(AgentPaymentReceiptOutcome.REJECTED, result.outcome());
        assertTrue(result.warnings().stream().anyMatch(value -> value.contains("fecha del pago")));
    }

    @Test
    void rejectsAnAmountDifferentFromThePersistedCartTotal() {
        AnalysisResult result = store.complete(91L, vision(98, "139.90", "OP-123"));

        assertEquals(AgentPaymentReceiptOutcome.REJECTED, result.outcome());
        assertEquals(CrmWhatsappPaymentEvidenceStatus.RECHAZADO, evidence.getValidationStatus());
        assertEquals(CrmWhatsappPaymentRequestStatus.PENDING_EVIDENCE, request.getStatus());
        assertEquals(CrmWhatsappAgentState.ESPERANDO_COMPROBANTE, session.getState());
    }

    @Test
    void approvesAnExactTwoSolesSurplusAsCombinedFilmPayment() {
        AnalysisResult result = store.complete(91L, vision(98, "142.00", "OP-COMBINED-1"));

        assertEquals(AgentPaymentReceiptOutcome.APPROVED_INITIAL, result.outcome());
        assertEquals(new BigDecimal("2.00"), request.getIncludedFilmAmount());
        assertEquals(com.sistemapos.sistematextil.model.CrmWhatsappFulfillmentMethod.SHALOM,
                session.getPreferredFulfillmentMethod());
        assertEquals(new BigDecimal("142.00"), evidence.getDetectedAmount());
        assertEquals(CrmWhatsappAgentState.ESPERANDO_DATOS_SHALOM,session.getState());
        assertEquals(CrmWhatsappAgentEvent.COMPROBANTE_COMBINADO_APROBADO,session.getLastEvent());
        String message=store.approvedCustomerMessage(session,request);
        assertTrue(message.contains("Nombres y apellidos"));
        assertTrue(message.contains("DNI"));
        assertTrue(message.contains("Celular"));
        assertTrue(message.contains("Agencia de Shalom"));
        assertFalse(message.contains("Cuál opción deseas"));
        verify(fulfillmentService).initializeCombinedShalom(41L,"combined-main-payment:91");
    }

    @Test
    void approvesCombinedPaymentWhenReceiptMethodDiffersFromSelectedMethod() {
        AgentPaymentReceiptExtraction extraction = new AgentPaymentReceiptExtraction(
                false, "142.00", "PEN", "2026-10-08", "14:01:00",
                "Kiments SAC", "999000111", "OP-WRONG-METHOD", "PLIN",
                "Pago exitoso Plin S/142.00", 98);

        AnalysisResult result = store.complete(91L,
                new VisionResult("GEMINI", "gemini-test", "TEST", 50L, extraction));

        assertEquals(AgentPaymentReceiptOutcome.APPROVED_INITIAL, result.outcome());
        assertEquals(CrmWhatsappPaymentEvidenceStatus.ACEPTABLE, evidence.getValidationStatus());
    }

    @Test
    void approvesCombinedPaymentWhenVisibleDestinationAccountIsDifferent() {
        AgentPaymentReceiptExtraction extraction = new AgentPaymentReceiptExtraction(
                true, "142.00", "PEN", "2026-10-08", "14:01:00",
                "", "***123", "OP-WRONG-ACCOUNT", "YAPE",
                "Pago exitoso Yape S/142.00", 98);

        AnalysisResult result = store.complete(91L,
                new VisionResult("GEMINI", "gemini-test", "TEST", 50L, extraction));

        assertEquals(AgentPaymentReceiptOutcome.APPROVED_INITIAL, result.outcome());
        assertEquals(CrmWhatsappPaymentEvidenceStatus.ACEPTABLE, evidence.getValidationStatus());
    }

    @Test
    void rejectsAnOperationAlreadyUsedByTheCompany() {
        CrmWhatsappPaymentEvidence previous = new CrmWhatsappPaymentEvidence();
        previous.setIdPaymentEvidence(10L);
        when(evidenceRepository.findOtherByOperation(any(), anyString(), any(), any()))
                .thenReturn(List.of(previous));

        AnalysisResult result = store.complete(91L, vision(98, "140.00", "OP-123"));

        assertEquals(AgentPaymentReceiptOutcome.REJECTED, result.outcome());
        assertEquals(10L, evidence.getDuplicateOfId());
        assertEquals(CrmWhatsappPaymentEvidenceStatus.RECHAZADO, evidence.getValidationStatus());
    }

    @Test
    void acceptsARepeatedFileWhenAmountAndDateAreValidAndOperationIsUnique() {
        CrmWhatsappPaymentEvidence previous = new CrmWhatsappPaymentEvidence();
        previous.setIdPaymentEvidence(11L);
        when(evidenceRepository.findOtherByHash(any(), anyString(), any(), any()))
                .thenReturn(List.of(previous));

        AnalysisResult result = store.complete(91L, vision(98, "140.00", "OP-123"));

        assertEquals(AgentPaymentReceiptOutcome.APPROVED_INITIAL, result.outcome());
        assertEquals(CrmWhatsappPaymentEvidenceStatus.ACEPTABLE, evidence.getValidationStatus());
        assertNull(evidence.getDuplicateOfId());
    }

    @Test
    void ignoresAVisualSimilarityFlagWhenFileAndOperationAreDifferent() {
        evidence.setDuplicateOfId(25L);
        evidence.setPerceptualHash("00ffffffff6e7e00");

        AnalysisResult result = store.complete(91L, vision(98, "140.00", "NEW-OP-456"));

        assertEquals(AgentPaymentReceiptOutcome.APPROVED_INITIAL, result.outcome());
        assertEquals(CrmWhatsappPaymentEvidenceStatus.ACEPTABLE, evidence.getValidationStatus());
        assertEquals("NEWOP456", evidence.getOperationCode());
        assertNull(evidence.getDuplicateOfId());
    }

    private VisionResult vision(int confidence, String amount, String operation) {
        AgentPaymentReceiptExtraction extraction = new AgentPaymentReceiptExtraction(
                true, amount, "PEN", "2026-10-08", "14:01:00",
                "Kiments SAC", "999000111", operation, "YAPE",
                "Pago exitoso Yape Kiments SAC 999000111 S/ 140.00 OP-123", confidence);
        return new VisionResult("GEMINI", "gemini-test", "TEST", 50L, extraction);
    }
}
