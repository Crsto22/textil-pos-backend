package com.sistemapos.sistematextil.services.agent.sale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sistemapos.sistematextil.model.Cliente;
import com.sistemapos.sistematextil.model.ComprobanteConfig;
import com.sistemapos.sistematextil.model.CrmWhatsappAgentEvent;
import com.sistemapos.sistematextil.model.CrmWhatsappAgentSession;
import com.sistemapos.sistematextil.model.CrmWhatsappAgentState;
import com.sistemapos.sistematextil.model.CrmWhatsappConnection;
import com.sistemapos.sistematextil.model.CrmWhatsappConversation;
import com.sistemapos.sistematextil.model.CrmWhatsappFulfillment;
import com.sistemapos.sistematextil.model.CrmWhatsappFulfillmentStatus;
import com.sistemapos.sistematextil.model.CrmWhatsappPaymentEvidence;
import com.sistemapos.sistematextil.model.CrmWhatsappPaymentEvidenceStatus;
import com.sistemapos.sistematextil.model.CrmWhatsappPaymentProcessingStatus;
import com.sistemapos.sistematextil.model.CrmWhatsappPaymentRequest;
import com.sistemapos.sistematextil.model.CrmWhatsappPaymentRequestStatus;
import com.sistemapos.sistematextil.model.CrmWhatsappPaymentReservationStatus;
import com.sistemapos.sistematextil.model.Empresa;
import com.sistemapos.sistematextil.model.MetodoPagoConfig;
import com.sistemapos.sistematextil.model.Sucursal;
import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.model.Venta;
import com.sistemapos.sistematextil.repositories.ComprobanteConfigRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAgentSessionRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappFulfillmentRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappPaymentEvidenceRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappPaymentRequestRepository;
import com.sistemapos.sistematextil.repositories.UsuarioRepository;
import com.sistemapos.sistematextil.repositories.VentaRepository;
import com.sistemapos.sistematextil.services.CrmWhatsappChatService.CrmSaleCreateRequest;
import com.sistemapos.sistematextil.services.CrmWhatsappPaymentReservationService;
import com.sistemapos.sistematextil.services.VentaService;
import com.sistemapos.sistematextil.services.agent.CrmWhatsappAgentStateMachine;
import com.sistemapos.sistematextil.services.agent.cart.AgentCartStore;
import com.sistemapos.sistematextil.services.agent.sale.AgentSaleService.SaleResult;
import com.sistemapos.sistematextil.util.usuario.Rol;
import com.sistemapos.sistematextil.util.venta.VentaCreateRequest;
import com.sistemapos.sistematextil.util.venta.VentaDetalleCreateItem;
import com.sistemapos.sistematextil.util.venta.VentaPagoCreateItem;
import com.sistemapos.sistematextil.util.venta.VentaResponse;

class AgentSaleServiceTest {
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-10-08T20:00:00Z"), ZoneId.of("America/Lima"));

    private CrmWhatsappAgentSessionRepository sessionRepository;
    private CrmWhatsappPaymentRequestRepository paymentRepository;
    private CrmWhatsappPaymentEvidenceRepository evidenceRepository;
    private CrmWhatsappFulfillmentRepository fulfillmentRepository;
    private ComprobanteConfigRepository receiptRepository;
    private VentaRepository saleRepository;
    private UsuarioRepository userRepository;
    private VentaService saleService;
    private CrmWhatsappPaymentReservationService reservationService;
    private AgentCartStore operationStore;
    private AgentPaymentReviewQueueService reviewQueueService;
    private AgentSaleService service;
    private ObjectMapper objectMapper;
    private CrmWhatsappAgentSession session;
    private CrmWhatsappPaymentRequest payment;
    private CrmWhatsappPaymentEvidence evidence;
    private CrmWhatsappFulfillment fulfillment;
    private Venta persistedSale;

    @BeforeEach
    void setUp() throws Exception {
        sessionRepository = mock(CrmWhatsappAgentSessionRepository.class);
        paymentRepository = mock(CrmWhatsappPaymentRequestRepository.class);
        evidenceRepository = mock(CrmWhatsappPaymentEvidenceRepository.class);
        fulfillmentRepository = mock(CrmWhatsappFulfillmentRepository.class);
        receiptRepository = mock(ComprobanteConfigRepository.class);
        saleRepository = mock(VentaRepository.class);
        userRepository = mock(UsuarioRepository.class);
        saleService = mock(VentaService.class);
        reservationService = mock(CrmWhatsappPaymentReservationService.class);
        operationStore = mock(AgentCartStore.class);
        reviewQueueService = mock(AgentPaymentReviewQueueService.class);
        objectMapper = new ObjectMapper().findAndRegisterModules();
        service = new AgentSaleService(sessionRepository, paymentRepository, evidenceRepository,
                fulfillmentRepository, receiptRepository, saleRepository, userRepository, saleService,
                reservationService, operationStore, new CrmWhatsappAgentStateMachine(), reviewQueueService,
                objectMapper, CLOCK);

        Empresa company = new Empresa();
        company.setIdEmpresa(3);
        Sucursal branch = new Sucursal();
        branch.setIdSucursal(1);
        branch.setEmpresa(company);
        CrmWhatsappConnection connection = new CrmWhatsappConnection();
        connection.setIdConnection(7L);
        connection.setEmpresa(company);
        connection.setSucursal(branch);
        CrmWhatsappConversation conversation = new CrmWhatsappConversation();
        conversation.setIdConversation(12L);
        conversation.setConnection(connection);
        Cliente customer = new Cliente();
        customer.setIdCliente(20);
        customer.setNombres("Ana Perez");
        customer.setTelefono("999111222");
        MetodoPagoConfig method = new MetodoPagoConfig();
        method.setIdMetodoPago(5);
        method.setNombre("YAPE");

        session = new CrmWhatsappAgentSession();
        session.setIdAgentSession(41L);
        session.setActiveSlot(true);
        session.setState(CrmWhatsappAgentState.LISTO_PARA_VENTA);
        session.setConnection(connection);
        session.setConversation(conversation);
        session.setCliente(customer);

        payment = new CrmWhatsappPaymentRequest();
        payment.setIdPaymentRequest(77L);
        payment.setAgentSession(session);
        payment.setConversation(conversation);
        payment.setConnection(connection);
        payment.setSucursal(branch);
        payment.setCliente(customer);
        payment.setMetodoPago(method);
        payment.setStatus(CrmWhatsappPaymentRequestStatus.UNDER_REVIEW);
        payment.setReservationStatus(CrmWhatsappPaymentReservationStatus.ACTIVE);
        payment.setExpectedAmount(new BigDecimal("140.00"));
        payment.setCurrency("PEN");
        payment.setExpiresAt(LocalDateTime.of(2026, 10, 9, 15, 0));
        payment.setEvidenceAcceptedAt(LocalDateTime.of(2026, 10, 9, 13, 59));
        payment.setReviewExpiresAt(LocalDateTime.of(2026, 10, 10, 13, 59));
        payment.setSaleRequestJson(objectMapper.writeValueAsString(new CrmSaleCreateRequest(
                1, null, "PEN", "CONTADO", 0D, 10D, "MONTO",
                 List.of(new VentaDetalleCreateItem(101, "EMMA L talla M", 2, "NIU", "10", 75D, 0D)),
                 List.of(new VentaPagoCreateItem(5, 140D, null, LocalDateTime.now(CLOCK))),
                 "999111222", null)));

        evidence = new CrmWhatsappPaymentEvidence();
        evidence.setIdPaymentEvidence(88L);
        evidence.setAgentSession(session);
        evidence.setPaymentRequest(payment);
        evidence.setValidationStatus(CrmWhatsappPaymentEvidenceStatus.ACEPTABLE);
        evidence.setProcessingStatus(CrmWhatsappPaymentProcessingStatus.COMPLETED);
        evidence.setOperationCode("OP-123");
        evidence.setOperationAt(LocalDateTime.of(2026, 10, 8, 15, 0));

        fulfillment = new CrmWhatsappFulfillment();
        fulfillment.setIdFulfillment(90L);
        fulfillment.setAgentSession(session);
        fulfillment.setConversation(conversation);
        fulfillment.setPaymentRequest(payment);
        fulfillment.setStatus(CrmWhatsappFulfillmentStatus.READY);

        ComprobanteConfig receipt = new ComprobanteConfig();
        receipt.setIdComprobante(4);
        receipt.setTipoComprobante("NOTA DE VENTA");
        receipt.setSerie("NV101");
        receipt.setActivo("ACTIVO");
        receipt.setHabilitadoVenta(true);

        Usuario actor = new Usuario();
        actor.setIdUsuario(9);
        actor.setCorreo("ia-kiments@system.local");
        actor.setEstado("ACTIVO");
        actor.setRol(Rol.SISTEMA);

        persistedSale = new Venta();
        persistedSale.setIdVenta(501);
        persistedSale.setTipoComprobante("NOTA DE VENTA");
        persistedSale.setSerie("NV101");
        persistedSale.setCorrelativo(9803);
        persistedSale.setTotal(new BigDecimal("140.00"));
        persistedSale.setOrigen("CRM_WHATSAPP");

        when(sessionRepository.findForUpdate(41L)).thenReturn(Optional.of(session));
        when(evidenceRepository
                .findFirstByAgentSession_IdAgentSessionAndValidationStatusOrderByCreatedAtDesc(
                        41L, CrmWhatsappPaymentEvidenceStatus.ACEPTABLE)).thenReturn(Optional.of(evidence));
        when(paymentRepository.findForUpdate(77L)).thenReturn(Optional.of(payment));
        when(fulfillmentRepository.findForUpdateBySessionId(41L)).thenReturn(Optional.of(fulfillment));
        when(receiptRepository.findTopByTipoComprobanteAndDeletedAtIsNullAndActivoOrderByIdComprobanteAsc(
                "NOTA DE VENTA", "ACTIVO")).thenReturn(Optional.of(receipt));
        when(userRepository.findByCorreoAndDeletedAtIsNull("ia-kiments@system.local"))
                .thenReturn(Optional.of(actor));
        when(operationStore.findOperation(anyString())).thenReturn(Optional.empty());
        when(operationStore.claimOperation(any(), anyString(), anyString(), anyString(), any())).thenReturn(true);
        when(saleRepository.findById(501)).thenReturn(Optional.of(persistedSale));
        VentaResponse created = mock(VentaResponse.class);
        when(created.idVenta()).thenReturn(501);
        when(created.tipoComprobante()).thenReturn("NOTA DE VENTA");
        when(created.serie()).thenReturn("NV101");
        when(created.correlativo()).thenReturn(9803);
        when(created.total()).thenReturn(new BigDecimal("140.00"));
        when(created.origen()).thenReturn("CRM_WHATSAPP");
        when(saleService.registrarVentaReservadaDesdeCrm(any(), any())).thenReturn(created);
    }

    @Test
    void createsOneCrmSalesNoteWithoutDiscountingReservedStockAgain() {
        SaleResult result = service.createSalesNote(41L, "sale-1");

        assertEquals(501, result.saleId());
        assertEquals("NOTA DE VENTA", result.receiptType());
        assertEquals("CRM_WHATSAPP", result.origin());
        assertEquals("CONSUMED", result.reservationStatus());
        assertEquals(CrmWhatsappAgentState.VENTA_PENDIENTE_REVISION, session.getState());
        assertEquals(CrmWhatsappAgentEvent.NOTA_VENTA_CREADA, session.getLastEvent());
        assertEquals(persistedSale, payment.getVenta());
        assertEquals(persistedSale, fulfillment.getVenta());
        assertFalse(result.alreadyCreated());

        ArgumentCaptor<VentaCreateRequest> request = ArgumentCaptor.forClass(VentaCreateRequest.class);
        verify(saleService).registrarVentaReservadaDesdeCrm(request.capture(), any());
        assertEquals("NOTA DE VENTA", request.getValue().tipoComprobante());
        assertEquals("NV101", request.getValue().serie());
        assertEquals(10D, request.getValue().descuentoTotal());
        assertEquals(1, request.getValue().pagos().size());
        assertEquals("OP-123", request.getValue().pagos().getFirst().codigoOperacion());
        assertEquals(140D, request.getValue().pagos().getFirst().monto());
        assertEquals(LocalDateTime.of(2026, 10, 8, 15, 0),
                request.getValue().pagos().getFirst().fecha());
        verify(reservationService).consume(payment);
        verify(reviewQueueService).enqueue(session.getConversation(), 501);
        verify(operationStore).completeOperation(anyString(), anyString(), any());
    }

    @Test
    void returnsExistingSaleAndNeverCreatesOrConsumesItTwice() {
        payment.setVenta(persistedSale);
        session.setState(CrmWhatsappAgentState.VENTA_PENDIENTE_REVISION);

        SaleResult result = service.createSalesNote(41L, "sale-replay-with-new-operation");

        assertEquals(501, result.saleId());
        assertEquals(true, result.alreadyCreated());
        assertEquals(persistedSale, fulfillment.getVenta());
        verify(saleService, never()).registrarVentaReservadaDesdeCrm(any(), any());
        verify(reservationService, never()).consume(any());
    }

    @Test
    void refusesToCreateSaleBeforeFulfillmentIsReady() {
        fulfillment.setStatus(CrmWhatsappFulfillmentStatus.AWAITING_AGENCY_CONFIRMATION);

        assertThrows(IllegalStateException.class, () -> service.createSalesNote(41L, "too-early"));

        verify(saleService, never()).registrarVentaReservadaDesdeCrm(any(), any());
        verify(reservationService, never()).consume(any());
    }
}
