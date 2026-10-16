package com.sistemapos.sistematextil.services.agent.payment;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
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
import com.sistemapos.sistematextil.model.Cliente;
import com.sistemapos.sistematextil.model.CrmWhatsappAgentEvent;
import com.sistemapos.sistematextil.model.CrmWhatsappAgentSession;
import com.sistemapos.sistematextil.model.CrmWhatsappAgentState;
import com.sistemapos.sistematextil.model.CrmWhatsappPaymentRequest;
import com.sistemapos.sistematextil.model.CrmWhatsappPaymentRequestStatus;
import com.sistemapos.sistematextil.model.CrmWhatsappPaymentReservationStatus;
import com.sistemapos.sistematextil.model.MetodoPagoConfig;
import com.sistemapos.sistematextil.model.MetodoPagoCuenta;
import com.sistemapos.sistematextil.model.SucursalMetodoPagoConfig;
import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAgentSessionRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappPaymentRequestRepository;
import com.sistemapos.sistematextil.repositories.MetodoPagoCuentaRepository;
import com.sistemapos.sistematextil.repositories.SucursalMetodoPagoConfigRepository;
import com.sistemapos.sistematextil.repositories.UsuarioRepository;
import com.sistemapos.sistematextil.services.CrmWhatsappChatService.CrmSaleCreateRequest;
import com.sistemapos.sistematextil.services.CrmWhatsappPaymentReservationService;
import com.sistemapos.sistematextil.services.agent.CrmWhatsappAgentStateMachine;
import com.sistemapos.sistematextil.services.agent.cart.AgentCartStore;
import com.sistemapos.sistematextil.services.agent.cart.AgentCartStore.CartItemRow;
import com.sistemapos.sistematextil.services.agent.cart.AgentCartStore.CartRow;
import com.sistemapos.sistematextil.services.agent.cart.AgentCartStore.OperationRow;
import com.sistemapos.sistematextil.services.agent.config.AgentRuntimeConfigService;
import com.sistemapos.sistematextil.util.crm.CrmWhatsappPhoneUtils;
import com.sistemapos.sistematextil.util.venta.VentaDetalleCreateItem;
import com.sistemapos.sistematextil.util.venta.VentaPagoCreateItem;

@Service
public class AgentPaymentService {
    static final Duration PAYMENT_WINDOW = Duration.ofMinutes(6);
    private static final String AGENT_USER_EMAIL = "ia-kiments@system.local";
    private static final List<CrmWhatsappPaymentRequestStatus> ACTIVE_REQUESTS = List.of(
            CrmWhatsappPaymentRequestStatus.PENDING_EVIDENCE,
            CrmWhatsappPaymentRequestStatus.UNDER_REVIEW,
            CrmWhatsappPaymentRequestStatus.READY_FOR_SALE);

    private final CrmWhatsappAgentSessionRepository sessionRepository;
    private final SucursalMetodoPagoConfigRepository branchMethodRepository;
    private final MetodoPagoCuentaRepository accountRepository;
    private final CrmWhatsappPaymentRequestRepository requestRepository;
    private final UsuarioRepository userRepository;
    private final AgentCartStore cartStore;
    private final CrmWhatsappPaymentReservationService reservationService;
    private final CrmWhatsappAgentStateMachine stateMachine;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final AgentRuntimeConfigService runtimeConfig;

    @Autowired
    public AgentPaymentService(
            CrmWhatsappAgentSessionRepository sessionRepository,
            SucursalMetodoPagoConfigRepository branchMethodRepository,
            MetodoPagoCuentaRepository accountRepository,
            CrmWhatsappPaymentRequestRepository requestRepository,
            UsuarioRepository userRepository,
            AgentCartStore cartStore,
            CrmWhatsappPaymentReservationService reservationService,
            CrmWhatsappAgentStateMachine stateMachine,
            AgentRuntimeConfigService runtimeConfig) {
        this(sessionRepository, branchMethodRepository, accountRepository, requestRepository, userRepository,
                cartStore, reservationService, stateMachine,
                new ObjectMapper().findAndRegisterModules(), Clock.systemDefaultZone(), runtimeConfig);
    }

    AgentPaymentService(
            CrmWhatsappAgentSessionRepository sessionRepository,
            SucursalMetodoPagoConfigRepository branchMethodRepository,
            MetodoPagoCuentaRepository accountRepository,
            CrmWhatsappPaymentRequestRepository requestRepository,
            UsuarioRepository userRepository,
            AgentCartStore cartStore,
            CrmWhatsappPaymentReservationService reservationService,
            CrmWhatsappAgentStateMachine stateMachine,
            ObjectMapper objectMapper,
            Clock clock) {
        this(sessionRepository, branchMethodRepository, accountRepository, requestRepository, userRepository,
                cartStore, reservationService, stateMachine, objectMapper, clock, null);
    }

    AgentPaymentService(CrmWhatsappAgentSessionRepository sessionRepository,
            SucursalMetodoPagoConfigRepository branchMethodRepository, MetodoPagoCuentaRepository accountRepository,
            CrmWhatsappPaymentRequestRepository requestRepository, UsuarioRepository userRepository,
            AgentCartStore cartStore, CrmWhatsappPaymentReservationService reservationService,
            CrmWhatsappAgentStateMachine stateMachine, ObjectMapper objectMapper, Clock clock,
            AgentRuntimeConfigService runtimeConfig) {
        this.sessionRepository = sessionRepository;
        this.branchMethodRepository = branchMethodRepository;
        this.accountRepository = accountRepository;
        this.requestRepository = requestRepository;
        this.userRepository = userRepository;
        this.cartStore = cartStore;
        this.reservationService = reservationService;
        this.stateMachine = stateMachine;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.runtimeConfig = runtimeConfig;
    }

    @Transactional(readOnly = true)
    public PaymentMethodList listMethods(Long sessionId) {
        CrmWhatsappAgentSession session = requireSession(sessionId, false);
        Integer branchId = branchId(session);
        List<PaymentMethodView> methods = activeMethods(branchId).stream().map(this::view).toList();
        return new PaymentMethodList(branchId, methods, methods.isEmpty()
                ? "La sucursal no tiene metodos de pago habilitados"
                : "Metodos de pago disponibles en la sucursal");
    }

    @Transactional
    public PaymentMethodSelection selectMethod(Long sessionId, Integer paymentMethodId, Integer accountId,
            String operationId) {
        if (paymentMethodId == null) throw new IllegalArgumentException("El metodo de pago es obligatorio");
        return execute(sessionId, operationId, "seleccionar_metodo_pago",
                paymentMethodId + ":" + Objects.toString(accountId, ""), PaymentMethodSelection.class, () -> {
                    CrmWhatsappAgentSession session = requirePaymentStage(sessionId);
                    SucursalMetodoPagoConfig config = requireActiveMethod(branchId(session), paymentMethodId);
                    List<MetodoPagoCuenta> accounts = activeAccounts(paymentMethodId);
                    MetodoPagoCuenta selectedAccount = null;
                    if (accountId != null) {
                        selectedAccount = accounts.stream()
                                .filter(account -> Objects.equals(account.getIdMetodoPagoCuenta(), accountId))
                                .findFirst().orElseThrow(() -> new IllegalArgumentException(
                                        "La cuenta no esta activa o no pertenece al metodo seleccionado"));
                    } else if (accounts.size() == 1) {
                        selectedAccount = accounts.getFirst();
                    } else if (accounts.size() > 1) {
                        return new PaymentMethodSelection("ACCOUNT_REQUIRED", paymentMethodId,
                                config.getMetodoPago().getNombre(), null, null, null,
                                accounts.stream().map(this::accountView).toList(), session.getState().name());
                    }

                    session.setSelectedPaymentMethod(config.getMetodoPago());
                    session.setSelectedPaymentAccount(selectedAccount);
                    applyEvent(session, CrmWhatsappAgentEvent.METODO_PAGO_SELECCIONADO, now());
                    return new PaymentMethodSelection("SELECTED", paymentMethodId,
                            config.getMetodoPago().getNombre(),
                            selectedAccount == null ? null : selectedAccount.getIdMetodoPagoCuenta(),
                            selectedAccount == null ? null : selectedAccount.getNumeroCuenta(),
                            selectedAccount == null ? null : selectedAccount.getTitular(),
                            accounts.stream().map(this::accountView).toList(), session.getState().name());
                });
    }

    @Transactional
    public PaymentRequestResult createRequest(Long sessionId, String operationId) {
        String operationKey = operationKey(sessionId, operationId);
        return execute(sessionId, operationId, "crear_solicitud_pago", "confirmed-cart",
                PaymentRequestResult.class, () -> {
                    CrmWhatsappAgentSession session = requirePaymentStage(sessionId);
                    MetodoPagoConfig selectedMethod = session.getSelectedPaymentMethod();
                    if (selectedMethod == null || selectedMethod.getIdMetodoPago() == null) {
                        throw new IllegalStateException("Primero selecciona un metodo de pago");
                    }
                    SucursalMetodoPagoConfig config = requireActiveMethod(
                            branchId(session), selectedMethod.getIdMetodoPago());
                    MetodoPagoCuenta account = validateSelectedAccount(session, selectedMethod.getIdMetodoPago());
                    requestRepository.findFirstByAgentSession_IdAgentSessionAndStatusInOrderByCreatedAtDesc(
                            sessionId, ACTIVE_REQUESTS).ifPresent(existing -> {
                                throw new IllegalStateException("La sesion ya tiene una solicitud de pago activa");
                            });

                    CartRow cart = cartStore.lockActiveCart(sessionId)
                            .orElseThrow(() -> new IllegalStateException("La sesion no tiene un carrito confirmado"));
                    if (!"CONFIRMED".equals(cart.status())) {
                        throw new IllegalStateException("Confirma el carrito antes de solicitar el pago");
                    }
                    List<CartItemRow> items = cartStore.findItems(cart.id());
                    if (items.isEmpty() || cart.total() == null || cart.total().signum() <= 0) {
                        throw new IllegalStateException("El carrito confirmado no contiene productos validos");
                    }

                    Usuario actor = requireAgentUser();
                    LocalDateTime createdAt = now();
                    Duration paymentWindow = paymentWindow(session);
                    LocalDateTime expiresAt = createdAt.plus(paymentWindow);
                    List<VentaDetalleCreateItem> details = saleDetails(items);
                    CrmSaleCreateRequest saleSnapshot = saleSnapshot(session, cart, details,
                            config.getMetodoPago().getIdMetodoPago(), createdAt);

                    CrmWhatsappPaymentRequest request = new CrmWhatsappPaymentRequest();
                    request.setConversation(session.getConversation());
                    request.setConnection(session.getConnection());
                    request.setSucursal(session.getConnection().getSucursal());
                    request.setCliente(session.getCliente());
                    request.setMetodoPago(config.getMetodoPago());
                    request.setCuenta(account);
                    request.setAgentSession(session);
                    request.setAgentIdempotencyKey(operationKey);
                    request.setCreatedBy(actor);
                    request.setExpectedAmount(money(cart.total()));
                    request.setCurrency(currency(cart.currency()));
                    request.setSaleRequestJson(write(saleSnapshot));
                    request.setReservationStatus(CrmWhatsappPaymentReservationStatus.LEGACY_NONE);
                    request.setStatus(CrmWhatsappPaymentRequestStatus.PENDING_EVIDENCE);
                    request.setExpiresAt(expiresAt);
                    request = requestRepository.save(request);

                    reservationService.reserve(request, details, actor);
                    applyEvent(session, CrmWhatsappAgentEvent.SOLICITUD_PAGO_CREADA, createdAt);
                    return paymentResult(request, items, session, paymentWindow);
                });
    }

    /**
     * Relaciona el cliente recopilado despues de aprobar el comprobante. La
     * solicitud se crea sin cliente para que los seis minutos empiecen apenas
     * se elige el metodo de pago, no mientras la clienta completa sus datos.
     */
    @Transactional
    public PaymentCustomerBinding bindApprovedCustomer(Long sessionId) {
        CrmWhatsappAgentSession session = requireSession(sessionId, true);
        if (session.getCliente() == null || session.getCliente().getIdCliente() == null) {
            throw new IllegalStateException("Primero registra los datos del cliente");
        }
        CrmWhatsappPaymentRequest request = requestRepository
                .findFirstByAgentSession_IdAgentSessionAndStatusInOrderByCreatedAtDesc(
                        sessionId, List.of(CrmWhatsappPaymentRequestStatus.UNDER_REVIEW,
                                CrmWhatsappPaymentRequestStatus.READY_FOR_SALE))
                .flatMap(found -> requestRepository.findForUpdate(found.getIdPaymentRequest()))
                .orElseThrow(() -> new IllegalStateException("No existe un comprobante aprobado para esta sesion"));
        request.setCliente(session.getCliente());
        requestRepository.save(request);
        return new PaymentCustomerBinding(request.getIdPaymentRequest(), session.getCliente().getIdCliente(),
                session.getCliente().getNombres(), session.getCliente().getTelefono());
    }

    @Transactional
    public void beginCartEdit(Long sessionId) {
        CrmWhatsappAgentSession session = requireSession(sessionId, true);
        LocalDateTime now = now();
        if (session.getState() == CrmWhatsappAgentState.ESPERANDO_COMPROBANTE) {
            CrmWhatsappPaymentRequest request = requestRepository
                    .findFirstByAgentSession_IdAgentSessionAndStatusInOrderByCreatedAtDesc(
                            sessionId, List.of(CrmWhatsappPaymentRequestStatus.PENDING_EVIDENCE))
                    .flatMap(found -> requestRepository.findForUpdate(found.getIdPaymentRequest()))
                    .orElseThrow(() -> new IllegalStateException("No existe una reserva de pago activa"));
            reservationService.release(request, requireAgentUser(), "Edición del carrito solicitada por la clienta");
            request.setStatus(CrmWhatsappPaymentRequestStatus.CANCELLED);
            requestRepository.save(request);
            applyEvent(session, CrmWhatsappAgentEvent.RESERVA_LIBERADA_POR_EDICION, now);
        } else if (session.getState() == CrmWhatsappAgentState.ESPERANDO_METODO_PAGO) {
            applyEvent(session, CrmWhatsappAgentEvent.EDICION_CARRITO_SOLICITADA, now);
        } else {
            throw new IllegalStateException("El carrito no se puede editar en esta etapa");
        }
        CartRow confirmed = cartStore.lockActiveCart(sessionId)
                .orElseThrow(() -> new IllegalStateException("No existe un carrito activo"));
        cartStore.reopen(confirmed.id(), now);
        session.setSelectedPaymentMethod(null);
        session.setSelectedPaymentAccount(null);
        sessionRepository.save(session);
    }

    /** Reabre el paso de pago conservando el carrito confirmado tras vencer la reserva. */
    @Transactional
    public RetryPaymentResult retryExpiredPayment(Long sessionId, String operationId) {
        return execute(sessionId, operationId, "reintentar_solicitud_pago", "expired-request",
                RetryPaymentResult.class, () -> {
                    CrmWhatsappAgentSession session = requireSession(sessionId, true);
                    if (session.getState() != CrmWhatsappAgentState.CANCELADO
                            || session.getLastEvent() != CrmWhatsappAgentEvent.SOLICITUD_PAGO_EXPIRADA) {
                        throw new IllegalStateException("No existe una reserva vencida que pueda renovarse");
                    }
                    CrmWhatsappPaymentRequest expired = requestRepository
                            .findFirstByAgentSession_IdAgentSessionAndStatusInOrderByCreatedAtDesc(
                                    sessionId, List.of(CrmWhatsappPaymentRequestStatus.EXPIRED))
                            .orElseThrow(() -> new IllegalStateException("No existe una solicitud de pago vencida"));
                    CartRow cart = cartStore.lockActiveCart(sessionId)
                            .orElseThrow(() -> new IllegalStateException("No existe un carrito confirmado"));
                    if (!"CONFIRMED".equals(cart.status()) || cartStore.findItems(cart.id()).isEmpty()) {
                        throw new IllegalStateException("El carrito ya no puede reservarse nuevamente");
                    }
                    applyEvent(session, CrmWhatsappAgentEvent.SOLICITUD_PAGO_REINTENTADA, now());
                    return new RetryPaymentResult(expired.getIdPaymentRequest(), session.getState().name());
                });
    }

    private CrmWhatsappAgentSession requirePaymentStage(Long sessionId) {
        CrmWhatsappAgentSession session = requireSession(sessionId, true);
        if (session.getState() != CrmWhatsappAgentState.ESPERANDO_METODO_PAGO) {
            throw new IllegalStateException("La sesion no esta esperando el metodo de pago");
        }
        return session;
    }

    private CrmWhatsappAgentSession requireSession(Long sessionId, boolean lock) {
        if (sessionId == null) throw new IllegalArgumentException("La sesion es obligatoria");
        CrmWhatsappAgentSession session = (lock
                ? sessionRepository.findForUpdate(sessionId)
                : sessionRepository.findById(sessionId))
                .orElseThrow(() -> new IllegalArgumentException("La sesion del agente no existe"));
        if (!Boolean.TRUE.equals(session.getActiveSlot())) {
            throw new IllegalStateException("La sesion del agente ya no esta activa");
        }
        branchId(session);
        return session;
    }

    private Integer branchId(CrmWhatsappAgentSession session) {
        if (session.getConnection() == null || session.getConnection().getSucursal() == null
                || session.getConnection().getSucursal().getIdSucursal() == null) {
            throw new IllegalStateException("La conexion WhatsApp no tiene sucursal configurada");
        }
        return session.getConnection().getSucursal().getIdSucursal();
    }

    private List<SucursalMetodoPagoConfig> activeMethods(Integer branchId) {
        return branchMethodRepository.findActivosBySucursal(branchId).stream()
                .filter(config -> config.getMetodoPago() != null
                        && config.getDeletedAt() == null
                        && "ACTIVO".equalsIgnoreCase(config.getEstado())
                        && config.getMetodoPago().getDeletedAt() == null
                        && "ACTIVO".equalsIgnoreCase(config.getMetodoPago().getEstado()))
                .toList();
    }

    private SucursalMetodoPagoConfig requireActiveMethod(Integer branchId, Integer paymentMethodId) {
        return activeMethods(branchId).stream()
                .filter(config -> Objects.equals(config.getMetodoPago().getIdMetodoPago(), paymentMethodId))
                .findFirst().orElseThrow(() -> new IllegalArgumentException(
                        "El metodo de pago no esta habilitado en la sucursal de esta conversacion"));
    }

    private List<MetodoPagoCuenta> activeAccounts(Integer paymentMethodId) {
        return accountRepository
                .findByMetodoPago_IdMetodoPagoAndActivoTrueOrderByIdMetodoPagoCuentaAsc(paymentMethodId);
    }

    private MetodoPagoCuenta validateSelectedAccount(CrmWhatsappAgentSession session, Integer paymentMethodId) {
        List<MetodoPagoCuenta> accounts = activeAccounts(paymentMethodId);
        MetodoPagoCuenta selected = session.getSelectedPaymentAccount();
        if (selected == null) {
            if (accounts.size() == 1) return accounts.getFirst();
            if (accounts.size() > 1) throw new IllegalStateException("Selecciona una cuenta para realizar el pago");
            return null;
        }
        return accounts.stream()
                .filter(account -> Objects.equals(account.getIdMetodoPagoCuenta(), selected.getIdMetodoPagoCuenta()))
                .findFirst().orElseThrow(() -> new IllegalStateException("La cuenta seleccionada ya no esta activa"));
    }

    private Usuario requireAgentUser() {
        return userRepository.findByCorreoAndDeletedAtIsNull(AGENT_USER_EMAIL)
                .filter(user -> "ACTIVO".equalsIgnoreCase(user.getEstado()))
                .orElseThrow(() -> new IllegalStateException("El usuario tecnico de IA no esta disponible"));
    }

    private List<VentaDetalleCreateItem> saleDetails(List<CartItemRow> items) {
        return items.stream().map(item -> new VentaDetalleCreateItem(
                item.variantId(), description(item), item.quantity(), "NIU", "10",
                money(item.unitPrice()).doubleValue(), 0D)).toList();
    }

    private CrmSaleCreateRequest saleSnapshot(CrmWhatsappAgentSession session, CartRow cart,
            List<VentaDetalleCreateItem> details, Integer paymentMethodId, LocalDateTime now) {
        BigDecimal discount = money(cart.discountTotal());
        return new CrmSaleCreateRequest(branchId(session), null, currency(cart.currency()), "CONTADO", 0D,
                discount.doubleValue(), discount.signum() > 0 ? "MONTO" : null, details,
                List.of(new VentaPagoCreateItem(paymentMethodId, money(cart.total()).doubleValue(), null, now)),
                session.getConversation() == null ? null : session.getConversation().getPhone());
    }

    private String description(CartItemRow item) {
        return (item.productName() + " " + item.color() + " talla " + item.size()).trim();
    }

    private PaymentMethodView view(SucursalMetodoPagoConfig config) {
        MetodoPagoConfig method = config.getMetodoPago();
        return new PaymentMethodView(method.getIdMetodoPago(), method.getNombre(), method.getDescripcion(),
                Boolean.TRUE.equals(config.getRequiereCodigoOperacion()),
                Boolean.TRUE.equals(config.getRequiereFechaPago()),
                Boolean.TRUE.equals(config.getRequiereHoraPago()),
                activeAccounts(method.getIdMetodoPago()).stream().map(this::accountView).toList());
    }

    private PaymentAccountView accountView(MetodoPagoCuenta account) {
        return new PaymentAccountView(account.getIdMetodoPagoCuenta(), account.getNumeroCuenta(),
                account.getTitular());
    }

    private PaymentRequestResult paymentResult(CrmWhatsappPaymentRequest request, List<CartItemRow> items,
            CrmWhatsappAgentSession session, Duration paymentWindow) {
        MetodoPagoCuenta account = request.getCuenta();
        return new PaymentRequestResult(request.getIdPaymentRequest(), request.getStatus().name(),
                request.getExpectedAmount(), request.getCurrency(), request.getExpiresAt(),
                paymentWindow.toSeconds(), request.getReservationStatus().name(),
                request.getMetodoPago().getIdMetodoPago(), request.getMetodoPago().getNombre(),
                account == null ? null : account.getIdMetodoPagoCuenta(),
                account == null ? null : account.getNumeroCuenta(),
                account == null ? null : account.getTitular(),
                items.stream().map(item -> new ReservedItem(item.variantId(), item.productName(), item.color(),
                        item.size(), item.quantity())).toList(), session.getState().name());
    }

    private Duration paymentWindow(CrmWhatsappAgentSession session) {
        if (runtimeConfig == null) return PAYMENT_WINDOW;
        return Duration.ofSeconds(runtimeConfig.forSession(session.getIdAgentSession()).paymentReservationSeconds());
    }

    public record RetryPaymentResult(Long expiredRequestId, String sessionState) {}

    private void applyEvent(CrmWhatsappAgentSession session, CrmWhatsappAgentEvent event, LocalDateTime time) {
        session.setState(stateMachine.transition(session.getState(), event));
        session.setLastEvent(event);
        session.setLastActivityAt(time);
        session.setStateChangedAt(time);
        sessionRepository.save(session);
    }

    private <T> T execute(Long sessionId, String operationId, String toolName, String request,
            Class<T> resultType, Supplier<T> action) {
        String key = operationKey(sessionId, operationId);
        String fingerprint = sha256(toolName + ":" + request);
        Optional<OperationRow> existing = cartStore.findOperation(key);
        if (existing.isPresent()) return replay(existing.get(), toolName, fingerprint, resultType);
        LocalDateTime startedAt = now();
        if (!cartStore.claimOperation(sessionId, key, toolName, fingerprint, startedAt)) {
            OperationRow concurrent = cartStore.findOperation(key)
                    .orElseThrow(() -> new IllegalStateException("La operacion de pago esta en curso"));
            return replay(concurrent, toolName, fingerprint, resultType);
        }
        T result = action.get();
        cartStore.completeOperation(key, write(result), now());
        return result;
    }

    private <T> T replay(OperationRow operation, String toolName, String fingerprint, Class<T> resultType) {
        if (!toolName.equals(operation.toolName()) || !fingerprint.equals(operation.requestFingerprint())) {
            throw new IllegalStateException("El identificador de operacion ya fue usado con otra solicitud");
        }
        if (!"COMPLETED".equals(operation.status()) || operation.responseJson() == null) {
            throw new IllegalStateException("La operacion de pago aun esta en curso");
        }
        try {
            return objectMapper.readValue(operation.responseJson(), resultType);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("No se pudo recuperar el resultado idempotente del pago", error);
        }
    }

    private String operationKey(Long sessionId, String operationId) {
        if (sessionId == null) throw new IllegalArgumentException("La sesion es obligatoria");
        if (operationId == null || operationId.isBlank()) {
            throw new IllegalStateException("Falta el identificador seguro de la operacion del agente");
        }
        return sha256("payment:" + sessionId + ":" + operationId.trim());
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("No se pudo serializar la operacion de pago", error);
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

    private BigDecimal money(BigDecimal value) {
        if (value == null) return BigDecimal.ZERO.setScale(2);
        return value.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private String currency(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        return normalized.matches("[A-Z]{3}") ? normalized : "PEN";
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    public record PaymentMethodList(Integer branchId, List<PaymentMethodView> methods, String message) {
    }

    public record PaymentMethodView(Integer paymentMethodId, String name, String description,
            boolean requiresOperationCode, boolean requiresPaymentDate, boolean requiresPaymentTime,
            List<PaymentAccountView> accounts) {
    }

    public record PaymentAccountView(Integer accountId, String accountNumber, String accountHolder) {
    }

    public record PaymentMethodSelection(String status, Integer paymentMethodId, String paymentMethod,
            Integer accountId, String accountNumber, String accountHolder,
            List<PaymentAccountView> availableAccounts, String agentState) {
    }

    public record ReservedItem(Integer variantId, String product, String color, String size, int quantity) {
    }

    public record PaymentRequestResult(Long paymentRequestId, String status, BigDecimal expectedAmount,
            String currency, LocalDateTime expiresAt, long expiresInSeconds, String reservationStatus,
            Integer paymentMethodId, String paymentMethod, Integer accountId, String accountNumber,
            String accountHolder, List<ReservedItem> reservedItems, String agentState) {
    }

    public record PaymentCustomerBinding(Long paymentRequestId, Integer customerId, String names, String phone) {
    }
}
