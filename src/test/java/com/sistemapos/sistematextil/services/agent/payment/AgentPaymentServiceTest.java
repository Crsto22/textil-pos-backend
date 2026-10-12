package com.sistemapos.sistematextil.services.agent.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
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
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sistemapos.sistematextil.model.Cliente;
import com.sistemapos.sistematextil.model.CrmWhatsappAgentEvent;
import com.sistemapos.sistematextil.model.CrmWhatsappAgentSession;
import com.sistemapos.sistematextil.model.CrmWhatsappAgentState;
import com.sistemapos.sistematextil.model.CrmWhatsappConnection;
import com.sistemapos.sistematextil.model.CrmWhatsappConversation;
import com.sistemapos.sistematextil.model.CrmWhatsappPaymentRequest;
import com.sistemapos.sistematextil.model.CrmWhatsappPaymentRequestStatus;
import com.sistemapos.sistematextil.model.CrmWhatsappPaymentReservationStatus;
import com.sistemapos.sistematextil.model.Empresa;
import com.sistemapos.sistematextil.model.MetodoPagoConfig;
import com.sistemapos.sistematextil.model.MetodoPagoCuenta;
import com.sistemapos.sistematextil.model.Sucursal;
import com.sistemapos.sistematextil.model.SucursalMetodoPagoConfig;
import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAgentSessionRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappPaymentRequestRepository;
import com.sistemapos.sistematextil.repositories.MetodoPagoCuentaRepository;
import com.sistemapos.sistematextil.repositories.SucursalMetodoPagoConfigRepository;
import com.sistemapos.sistematextil.repositories.UsuarioRepository;
import com.sistemapos.sistematextil.services.CrmWhatsappPaymentReservationService;
import com.sistemapos.sistematextil.services.agent.CrmWhatsappAgentStateMachine;
import com.sistemapos.sistematextil.services.agent.cart.AgentCartStore;
import com.sistemapos.sistematextil.services.agent.cart.AgentCartStore.CartItemRow;
import com.sistemapos.sistematextil.services.agent.cart.AgentCartStore.CartRow;
import com.sistemapos.sistematextil.services.agent.payment.AgentPaymentService.PaymentMethodSelection;
import com.sistemapos.sistematextil.services.agent.payment.AgentPaymentService.PaymentRequestResult;

class AgentPaymentServiceTest {
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-10-08T19:00:00Z"), ZoneId.of("America/Lima"));

    private CrmWhatsappAgentSessionRepository sessionRepository;
    private SucursalMetodoPagoConfigRepository methodRepository;
    private MetodoPagoCuentaRepository accountRepository;
    private CrmWhatsappPaymentRequestRepository requestRepository;
    private UsuarioRepository userRepository;
    private AgentCartStore cartStore;
    private CrmWhatsappPaymentReservationService reservationService;
    private AgentPaymentService service;
    private CrmWhatsappAgentSession session;
    private SucursalMetodoPagoConfig methodConfig;
    private MetodoPagoCuenta account;

    @BeforeEach
    void setUp() {
        sessionRepository = mock(CrmWhatsappAgentSessionRepository.class);
        methodRepository = mock(SucursalMetodoPagoConfigRepository.class);
        accountRepository = mock(MetodoPagoCuentaRepository.class);
        requestRepository = mock(CrmWhatsappPaymentRequestRepository.class);
        userRepository = mock(UsuarioRepository.class);
        cartStore = mock(AgentCartStore.class);
        reservationService = mock(CrmWhatsappPaymentReservationService.class);
        service = new AgentPaymentService(sessionRepository, methodRepository, accountRepository,
                requestRepository, userRepository, cartStore, reservationService,
                new CrmWhatsappAgentStateMachine(), new ObjectMapper().findAndRegisterModules(), CLOCK);

        Sucursal branch = new Sucursal();
        branch.setIdSucursal(8);
        branch.setNombre("Gamarra");
        Empresa company = new Empresa();
        company.setIdEmpresa(3);
        branch.setEmpresa(company);
        CrmWhatsappConnection connection = new CrmWhatsappConnection();
        connection.setIdConnection(2L);
        connection.setSucursal(branch);
        connection.setEmpresa(company);
        CrmWhatsappConversation conversation = new CrmWhatsappConversation();
        conversation.setIdConversation(12L);
        conversation.setConnection(connection);
        Cliente customer = new Cliente();
        customer.setIdCliente(20);
        customer.setNombres("Ana Perez");
        customer.setTelefono("999111222");
        customer.setEstado("ACTIVO");
        session = new CrmWhatsappAgentSession();
        session.setIdAgentSession(41L);
        session.setActiveSlot(true);
        session.setState(CrmWhatsappAgentState.ESPERANDO_METODO_PAGO);
        session.setConnection(connection);
        session.setConversation(conversation);
        session.setCliente(customer);

        MetodoPagoConfig method = new MetodoPagoConfig();
        method.setIdMetodoPago(2);
        method.setNombre("YAPE");
        method.setDescripcion("Pago por Yape");
        method.setEstado("ACTIVO");
        account = new MetodoPagoCuenta();
        account.setIdMetodoPagoCuenta(9);
        account.setMetodoPago(method);
        account.setNumeroCuenta("999000111");
        account.setTitular("Kiments SAC");
        account.setActivo(true);
        methodConfig = new SucursalMetodoPagoConfig();
        methodConfig.setSucursal(branch);
        methodConfig.setMetodoPago(method);
        methodConfig.setEstado("ACTIVO");

        when(sessionRepository.findById(41L)).thenReturn(Optional.of(session));
        when(sessionRepository.findForUpdate(41L)).thenReturn(Optional.of(session));
        when(methodRepository.findActivosBySucursal(8)).thenReturn(List.of(methodConfig));
        when(accountRepository.findByMetodoPago_IdMetodoPagoAndActivoTrueOrderByIdMetodoPagoCuentaAsc(2))
                .thenReturn(List.of(account));
        when(cartStore.findOperation(anyString())).thenReturn(Optional.empty());
        when(cartStore.claimOperation(any(), anyString(), anyString(), anyString(), any())).thenReturn(true);
    }

    @Test
    void selectingSingleAccountPersistsSelectionAndValidatedEvent() {
        PaymentMethodSelection result = service.selectMethod(41L, 2, null, "select-yape");

        assertEquals("SELECTED", result.status());
        assertEquals(9, result.accountId());
        assertEquals(methodConfig.getMetodoPago(), session.getSelectedPaymentMethod());
        assertEquals(account, session.getSelectedPaymentAccount());
        assertEquals(CrmWhatsappAgentEvent.METODO_PAGO_SELECCIONADO, session.getLastEvent());
        assertEquals(CrmWhatsappAgentState.ESPERANDO_METODO_PAGO, session.getState());
    }

    @Test
    void multipleAccountsRequireAnExplicitSelection() {
        MetodoPagoCuenta second = new MetodoPagoCuenta();
        second.setIdMetodoPagoCuenta(10);
        second.setMetodoPago(methodConfig.getMetodoPago());
        second.setNumeroCuenta("999000222");
        second.setActivo(true);
        when(accountRepository.findByMetodoPago_IdMetodoPagoAndActivoTrueOrderByIdMetodoPagoCuentaAsc(2))
                .thenReturn(List.of(account, second));

        PaymentMethodSelection result = service.selectMethod(41L, 2, null, "choose-account");

        assertEquals("ACCOUNT_REQUIRED", result.status());
        assertEquals(2, result.availableAccounts().size());
        assertNull(session.getSelectedPaymentMethod());
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void createsAndReservesRequestForExactlyFourMinutesUsingBackendCartTotal() throws Exception {
        session.setCliente(null);
        session.setSelectedPaymentMethod(methodConfig.getMetodoPago());
        session.setSelectedPaymentAccount(account);
        Usuario agent = new Usuario();
        agent.setIdUsuario(99);
        agent.setEstado("ACTIVO");
        when(userRepository.findByCorreoAndDeletedAtIsNull("ia-kiments@system.local"))
                .thenReturn(Optional.of(agent));
        CartRow cart = new CartRow(5L, 41L, "CONFIRMED", "PEN",
                new BigDecimal("150.00"), new BigDecimal("10.00"), new BigDecimal("140.00"), 1L);
        CartItemRow item = new CartItemRow(7L, 101, 2, "EMMA", "NEGRO", "M",
                new BigDecimal("75.00"), BigDecimal.ZERO, new BigDecimal("150.00"));
        when(cartStore.lockActiveCart(41L)).thenReturn(Optional.of(cart));
        when(cartStore.findItems(5L)).thenReturn(List.of(item));
        when(requestRepository
                .findFirstByAgentSession_IdAgentSessionAndStatusInOrderByCreatedAtDesc(any(), any()))
                .thenReturn(Optional.empty());
        AtomicReference<CrmWhatsappPaymentRequest> saved = new AtomicReference<>();
        when(requestRepository.save(any(CrmWhatsappPaymentRequest.class))).thenAnswer(invocation -> {
            CrmWhatsappPaymentRequest request = invocation.getArgument(0);
            saved.set(request);
            request.setIdPaymentRequest(77L);
            return request;
        });
        doAnswer(invocation -> {
            CrmWhatsappPaymentRequest request = invocation.getArgument(0);
            request.setReservationStatus(CrmWhatsappPaymentReservationStatus.ACTIVE);
            return null;
        }).when(reservationService).reserve(any(), any(), any());

        PaymentRequestResult result = service.createRequest(41L, "create-payment");

        assertEquals(77L, result.paymentRequestId());
        assertEquals(new BigDecimal("140.00"), result.expectedAmount());
        assertEquals(LocalDateTime.of(2026, 10, 8, 14, 4), result.expiresAt());
        assertEquals(240L, result.expiresInSeconds());
        assertEquals("ACTIVE", result.reservationStatus());
        assertEquals(CrmWhatsappAgentState.ESPERANDO_COMPROBANTE, session.getState());
        assertEquals(CrmWhatsappAgentEvent.SOLICITUD_PAGO_CREADA, session.getLastEvent());
        assertNull(saved.get().getCliente());
        verify(reservationService).reserve(any(), any(), any());
        verify(cartStore).completeOperation(anyString(), anyString(), any());
    }

    @Test
    void listsOnlyConfiguredBranchMethods() {
        var result = service.listMethods(41L);
        assertEquals(8, result.branchId());
        assertEquals("YAPE", result.methods().getFirst().name());
        assertEquals("999000111", result.methods().getFirst().accounts().getFirst().accountNumber());
    }

    @Test
    void reopensPaymentStageAfterTheFourMinuteReservationExpired() {
        session.setState(CrmWhatsappAgentState.CANCELADO);
        session.setLastEvent(CrmWhatsappAgentEvent.SOLICITUD_PAGO_EXPIRADA);
        CrmWhatsappPaymentRequest expired = new CrmWhatsappPaymentRequest();
        expired.setIdPaymentRequest(88L);
        expired.setStatus(CrmWhatsappPaymentRequestStatus.EXPIRED);
        when(requestRepository.findFirstByAgentSession_IdAgentSessionAndStatusInOrderByCreatedAtDesc(
                41L, List.of(CrmWhatsappPaymentRequestStatus.EXPIRED))).thenReturn(Optional.of(expired));
        CartRow cart = new CartRow(5L, 41L, "CONFIRMED", "PEN",
                new BigDecimal("75.00"), BigDecimal.ZERO, new BigDecimal("75.00"), 1L);
        when(cartStore.lockActiveCart(41L)).thenReturn(Optional.of(cart));
        when(cartStore.findItems(5L)).thenReturn(List.of(new CartItemRow(7L, 101, 1,
                "EMMA", "NEGRO", "M", new BigDecimal("75.00"), BigDecimal.ZERO,
                new BigDecimal("75.00"))));

        var result = service.retryExpiredPayment(41L, "retry-expired");

        assertEquals(88L, result.expiredRequestId());
        assertEquals(CrmWhatsappAgentState.ESPERANDO_METODO_PAGO, session.getState());
        assertEquals(CrmWhatsappAgentEvent.SOLICITUD_PAGO_REINTENTADA, session.getLastEvent());
    }
}
