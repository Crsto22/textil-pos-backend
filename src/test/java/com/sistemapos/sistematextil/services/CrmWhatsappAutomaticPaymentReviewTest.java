package com.sistemapos.sistematextil.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import com.sistemapos.sistematextil.model.CrmWhatsappAgentSession;
import com.sistemapos.sistematextil.model.CrmWhatsappAgentState;
import com.sistemapos.sistematextil.model.CrmWhatsappConnection;
import com.sistemapos.sistematextil.model.CrmWhatsappConversation;
import com.sistemapos.sistematextil.model.CrmWhatsappFulfillment;
import com.sistemapos.sistematextil.model.CrmWhatsappPaymentEvidence;
import com.sistemapos.sistematextil.model.CrmWhatsappPaymentEvidenceStatus;
import com.sistemapos.sistematextil.model.CrmWhatsappPaymentProcessingStatus;
import com.sistemapos.sistematextil.model.CrmWhatsappPaymentRequest;
import com.sistemapos.sistematextil.model.CrmWhatsappPaymentRequestStatus;
import com.sistemapos.sistematextil.model.CrmWhatsappPaymentReservationStatus;
import com.sistemapos.sistematextil.model.CrmWhatsappPaymentReviewAction;
import com.sistemapos.sistematextil.model.CrmWhatsappPaymentReviewAudit;
import com.sistemapos.sistematextil.model.MetodoPagoConfig;
import com.sistemapos.sistematextil.model.Sucursal;
import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.model.Venta;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAgentSessionRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappConversationRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappFulfillmentRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappMessageRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappPaymentEvidenceRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappPaymentRequestRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappPaymentReviewAuditRepository;
import com.sistemapos.sistematextil.repositories.MetodoPagoCuentaRepository;
import com.sistemapos.sistematextil.repositories.PagoRepository;
import com.sistemapos.sistematextil.repositories.SucursalMetodoPagoConfigRepository;
import com.sistemapos.sistematextil.repositories.UsuarioRepository;
import com.sistemapos.sistematextil.repositories.VentaDetalleRepository;
import com.sistemapos.sistematextil.repositories.VentaRepository;
import com.sistemapos.sistematextil.services.agent.CrmWhatsappAgentStateMachine;
import com.sistemapos.sistematextil.util.usuario.Rol;
import com.sistemapos.sistematextil.util.venta.VentaAnulacionResponse;

@ExtendWith(MockitoExtension.class)
class CrmWhatsappAutomaticPaymentReviewTest {
    @Mock CrmWhatsappPaymentRequestRepository requestRepository;
    @Mock CrmWhatsappPaymentEvidenceRepository evidenceRepository;
    @Mock CrmWhatsappPaymentReviewAuditRepository reviewAuditRepository;
    @Mock CrmWhatsappAgentSessionRepository agentSessionRepository;
    @Mock CrmWhatsappFulfillmentRepository fulfillmentRepository;
    @Mock CrmWhatsappConversationRepository conversationRepository;
    @Mock CrmWhatsappMessageRepository messageRepository;
    @Mock SucursalMetodoPagoConfigRepository branchPaymentRepository;
    @Mock MetodoPagoCuentaRepository accountRepository;
    @Mock UsuarioRepository usuarioRepository;
    @Mock VentaRepository ventaRepository;
    @Mock VentaDetalleRepository ventaDetalleRepository;
    @Mock PagoRepository pagoRepository;
    @Mock S3StorageService storageService;
    @Mock CrmWhatsappEventService eventService;
    @Mock ObjectProvider<CrmWhatsappChatService> chatServiceProvider;
    @Mock CrmWhatsappPaymentReservationService reservationService;
    @Mock VentaAnulacionService ventaAnulacionService;
    @Mock VentaService ventaService;
    @Spy CrmWhatsappAgentStateMachine agentStateMachine = new CrmWhatsappAgentStateMachine();
    @InjectMocks CrmWhatsappPaymentEvidenceService service;

    private CrmWhatsappChatService chatService;
    private Usuario reviewer;
    private CrmWhatsappConversation conversation;
    private CrmWhatsappAgentSession session;
    private CrmWhatsappPaymentRequest request;
    private CrmWhatsappPaymentEvidence evidence;
    private CrmWhatsappFulfillment fulfillment;
    private Venta sale;

    @BeforeEach
    void setUp() {
        chatService = mock(CrmWhatsappChatService.class);
        reviewer = new Usuario();
        reviewer.setIdUsuario(9);
        reviewer.setRol(Rol.VENTAS);
        reviewer.setAccesoCrm(true);
        reviewer.setEstado("ACTIVO");
        reviewer.setCorreo("asesora@kiments.pe");

        Sucursal branch = new Sucursal();
        branch.setIdSucursal(1);
        branch.setNombre("Gamarra");
        CrmWhatsappConnection connection = new CrmWhatsappConnection();
        connection.setIdConnection(7L);
        connection.setSucursal(branch);
        conversation = new CrmWhatsappConversation();
        conversation.setIdConversation(12L);
        conversation.setConnection(connection);
        conversation.setStatus("ESPERA");

        session = new CrmWhatsappAgentSession();
        session.setIdAgentSession(41L);
        session.setActiveSlot(true);
        session.setState(CrmWhatsappAgentState.VENTA_PENDIENTE_REVISION);
        session.setConversation(conversation);
        session.setConnection(connection);

        MetodoPagoConfig method = new MetodoPagoConfig();
        method.setIdMetodoPago(5);
        method.setNombre("YAPE");
        sale = new Venta();
        sale.setIdVenta(501);
        sale.setTipoComprobante("NOTA DE VENTA");
        sale.setSerie("NV101");
        sale.setCorrelativo(9803);
        sale.setEstado("EMITIDA");
        sale.setOrigen("CRM_WHATSAPP");
        sale.setTotal(new BigDecimal("140.00"));
        sale.setSucursal(branch);

        request = new CrmWhatsappPaymentRequest();
        request.setIdPaymentRequest(77L);
        request.setAgentSession(session);
        request.setConversation(conversation);
        request.setSucursal(branch);
        request.setMetodoPago(method);
        request.setVenta(sale);
        request.setExpectedAmount(new BigDecimal("140.00"));
        request.setCurrency("PEN");
        request.setStatus(CrmWhatsappPaymentRequestStatus.UNDER_REVIEW);
        request.setReservationStatus(CrmWhatsappPaymentReservationStatus.CONSUMED);

        evidence = new CrmWhatsappPaymentEvidence();
        evidence.setIdPaymentEvidence(88L);
        evidence.setConversation(conversation);
        evidence.setAgentSession(session);
        evidence.setPaymentRequest(request);
        evidence.setValidationStatus(CrmWhatsappPaymentEvidenceStatus.ACEPTABLE);
        evidence.setProcessingStatus(CrmWhatsappPaymentProcessingStatus.COMPLETED);
        evidence.setDetectedAmount(new BigDecimal("140.00"));
        evidence.setOperationCode("OP-123");

        fulfillment = new CrmWhatsappFulfillment();
        fulfillment.setIdFulfillment(90L);
        fulfillment.setAgentSession(session);
        fulfillment.setConversation(conversation);
        fulfillment.setPaymentRequest(request);
        fulfillment.setVenta(sale);

        when(usuarioRepository.findByIdUsuarioAndDeletedAtIsNull(9)).thenReturn(Optional.of(reviewer));
        when(evidenceRepository.findForUpdate(88L)).thenReturn(Optional.of(evidence));
        when(chatServiceProvider.getObject()).thenReturn(chatService);
        when(requestRepository.findForUpdate(77L)).thenReturn(Optional.of(request));
        when(agentSessionRepository.findForUpdate(41L)).thenReturn(Optional.of(session));
        when(fulfillmentRepository.findForUpdateBySessionId(41L)).thenReturn(Optional.of(fulfillment));
        when(fulfillmentRepository.findByAgentSession_IdAgentSession(41L)).thenReturn(Optional.of(fulfillment));
        when(reviewAuditRepository.findByPaymentEvidence_IdPaymentEvidence(88L)).thenReturn(Optional.empty());
        when(evidenceRepository.findFirstByPaymentRequest_IdPaymentRequestAndValidationStatusOrderByCreatedAtDesc(
                77L, CrmWhatsappPaymentEvidenceStatus.ACEPTABLE)).thenReturn(Optional.empty());
        when(ventaDetalleRepository.findByVenta_IdVentaAndDeletedAtIsNullOrderByIdVentaDetalleAsc(501))
                .thenReturn(List.of());
        when(pagoRepository.findByVenta_IdVentaAndDeletedAtIsNullOrderByIdPagoAsc(501)).thenReturn(List.of());
    }

    @Test
    void acceptsAutomaticSaleAndClosesAgentSession() {
        service.decide(88L,
                new CrmWhatsappPaymentEvidenceService.PaymentDecisionRequest("ACCEPT", true, "Abono verificado"),
                reviewer);

        assertEquals(CrmWhatsappPaymentEvidenceStatus.VALIDADO, evidence.getValidationStatus());
        assertEquals(CrmWhatsappPaymentRequestStatus.COMPLETED, request.getStatus());
        assertEquals(CrmWhatsappAgentState.COMPLETADO, session.getState());
        assertNull(session.getActiveSlot());
        ArgumentCaptor<CrmWhatsappPaymentReviewAudit> audit =
                ArgumentCaptor.forClass(CrmWhatsappPaymentReviewAudit.class);
        verify(reviewAuditRepository).save(audit.capture());
        assertEquals(CrmWhatsappPaymentReviewAction.ACCEPTED, audit.getValue().getAction());
        assertEquals("EMITIDA", audit.getValue().getSaleStatusAfter());
        verify(chatService, never()).asignarParaOperacion(12L, reviewer);
    }

    @Test
    void rejectsAutomaticSaleAnnullingItAndReturningStock() {
        VentaAnulacionResponse cancellation = mock(VentaAnulacionResponse.class);
        when(cancellation.estadoVenta()).thenReturn("ANULADA");
        when(ventaAnulacionService.anularNotaVentaAutomaticaDesdeCrm(
                501, "Operacion inexistente", reviewer)).thenReturn(cancellation);

        service.decide(88L,
                new CrmWhatsappPaymentEvidenceService.PaymentDecisionRequest(
                        "REJECT", false, "Operacion inexistente"),
                reviewer);

        verify(ventaAnulacionService).anularNotaVentaAutomaticaDesdeCrm(
                501, "Operacion inexistente", reviewer);
        verify(reservationService).markReversedAfterSaleCancellation(request,
                "Pago rechazado por revision humana: Operacion inexistente");
        assertEquals(CrmWhatsappPaymentEvidenceStatus.RECHAZADO, evidence.getValidationStatus());
        assertEquals(CrmWhatsappPaymentRequestStatus.CANCELLED, request.getStatus());
        assertEquals(CrmWhatsappAgentState.CANCELADO, session.getState());
        assertNull(session.getActiveSlot());
        ArgumentCaptor<CrmWhatsappPaymentReviewAudit> audit =
                ArgumentCaptor.forClass(CrmWhatsappPaymentReviewAudit.class);
        verify(reviewAuditRepository).save(audit.capture());
        assertEquals(CrmWhatsappPaymentReviewAction.REJECTED, audit.getValue().getAction());
        assertEquals("ANULADA", audit.getValue().getSaleStatusAfter());
        assertEquals("Operacion inexistente", audit.getValue().getReason());
        verify(chatService, never()).asignarParaOperacion(12L, reviewer);
    }
}
