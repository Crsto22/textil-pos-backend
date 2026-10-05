package com.sistemapos.sistematextil.services;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.http.HttpStatus;

import com.sistemapos.sistematextil.model.Cliente;
import com.sistemapos.sistematextil.model.ComprobanteConfig;
import com.sistemapos.sistematextil.model.CrmWhatsappConversation;
import com.sistemapos.sistematextil.model.CrmWhatsappAiAttentionMode;
import com.sistemapos.sistematextil.model.CrmWhatsappAttentionQueue;
import com.sistemapos.sistematextil.model.CrmWhatsappConversationTag;
import com.sistemapos.sistematextil.model.CrmWhatsappMessage;
import com.sistemapos.sistematextil.model.CrmWhatsappPaymentRequestStatus;
import com.sistemapos.sistematextil.model.CrmWhatsappWaitingReason;
import com.sistemapos.sistematextil.model.CrmWhatsappTag;
import com.sistemapos.sistematextil.model.Empresa;
import com.sistemapos.sistematextil.model.Pago;
import com.sistemapos.sistematextil.model.ProductoVariante;
import com.sistemapos.sistematextil.model.Sucursal;
import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.model.Venta;
import com.sistemapos.sistematextil.model.VentaDetalle;
import com.sistemapos.sistematextil.repositories.ClienteRepository;
import com.sistemapos.sistematextil.repositories.ComprobanteConfigRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappConversationRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappConversationTagRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappMessageRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappPaymentRequestRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappTagRepository;
import com.sistemapos.sistematextil.repositories.EmpresaRepository;
import com.sistemapos.sistematextil.repositories.PagoRepository;
import com.sistemapos.sistematextil.repositories.SucursalMetodoPagoConfigRepository;
import com.sistemapos.sistematextil.repositories.SucursalRepository;
import com.sistemapos.sistematextil.repositories.UsuarioRepository;
import com.sistemapos.sistematextil.repositories.VentaDetalleRepository;
import com.sistemapos.sistematextil.repositories.VentaRepository;
import com.sistemapos.sistematextil.util.cliente.ClienteCreateRequest;
import com.sistemapos.sistematextil.util.cliente.ClienteUpdateRequest;
import com.sistemapos.sistematextil.util.cliente.TipoDocumento;
import com.sistemapos.sistematextil.util.crm.CrmWhatsappPhoneUtils;
import com.sistemapos.sistematextil.util.paginacion.PagedResponse;
import com.sistemapos.sistematextil.util.producto.ProductoDetalleResponse;
import com.sistemapos.sistematextil.util.producto.ProductoVarianteDetalleResponse;
import com.sistemapos.sistematextil.util.producto.ProductoVarianteListadoResumenPageResponse;
import com.sistemapos.sistematextil.util.usuario.Rol;
import com.sistemapos.sistematextil.util.venta.VentaCreateRequest;
import com.sistemapos.sistematextil.util.venta.VentaDetalleCreateItem;
import com.sistemapos.sistematextil.util.venta.VentaPagoCreateItem;
import com.sistemapos.sistematextil.util.venta.VentaResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class CrmWhatsappChatService {

    private static final String ESPERA = "ESPERA";
    private static final String ATENDIDO = "ATENDIDO";
    private static final String RESUELTO = "RESUELTO";
    private static final DateTimeFormatter CRM_REPORT_HOUR_FORMAT = DateTimeFormatter.ofPattern("HH:00");

    private final CrmWhatsappConversationRepository conversationRepository;
    private final CrmWhatsappConversationTagRepository conversationTagRepository;
    private final CrmWhatsappMessageRepository messageRepository;
    private final CrmWhatsappPaymentRequestRepository paymentRequestRepository;
    private final CrmWhatsappTagRepository tagRepository;
    private final UsuarioRepository usuarioRepository;
    private final EmpresaRepository empresaRepository;
    private final ClienteRepository clienteRepository;
    private final SucursalRepository sucursalRepository;
    private final ComprobanteConfigRepository comprobanteConfigRepository;
    private final VentaRepository ventaRepository;
    private final VentaDetalleRepository ventaDetalleRepository;
    private final PagoRepository pagoRepository;
    private final SucursalMetodoPagoConfigRepository sucursalMetodoPagoConfigRepository;
    private final UsuarioSucursalAccessService usuarioSucursalAccessService;
    private final ProductoService productoService;
    private final ProductoVarianteService productoVarianteService;
    private final VentaService ventaService;
    private final CrmWhatsappBridgeService bridgeService;
    private final CrmWhatsappEventService eventService;
    private final CrmWhatsappConnectionService connectionService;
    private final CrmWhatsappAiJobService aiJobService;
    private final CrmWhatsappAiMemoryService aiMemoryService;
    private final CrmWhatsappAiSaleDraftService aiSaleDraftService;
    private final CrmWhatsappAiClientWriter aiClientWriter;
    private final CrmWhatsappPaymentReservationService paymentReservationService;
    private final S3StorageService storageService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CrmWhatsappChatService(
            CrmWhatsappConversationRepository conversationRepository,
            CrmWhatsappConversationTagRepository conversationTagRepository,
            CrmWhatsappMessageRepository messageRepository,
            CrmWhatsappPaymentRequestRepository paymentRequestRepository,
            CrmWhatsappTagRepository tagRepository,
            UsuarioRepository usuarioRepository,
            EmpresaRepository empresaRepository,
            ClienteRepository clienteRepository,
            SucursalRepository sucursalRepository,
            ComprobanteConfigRepository comprobanteConfigRepository,
            VentaRepository ventaRepository,
            VentaDetalleRepository ventaDetalleRepository,
            PagoRepository pagoRepository,
            SucursalMetodoPagoConfigRepository sucursalMetodoPagoConfigRepository,
            UsuarioSucursalAccessService usuarioSucursalAccessService,
            ProductoService productoService,
            ProductoVarianteService productoVarianteService,
            VentaService ventaService,
            CrmWhatsappBridgeService bridgeService,
            CrmWhatsappEventService eventService,
            CrmWhatsappConnectionService connectionService,
            CrmWhatsappAiJobService aiJobService,
            CrmWhatsappAiMemoryService aiMemoryService,
            CrmWhatsappAiSaleDraftService aiSaleDraftService,
            CrmWhatsappAiClientWriter aiClientWriter,
            CrmWhatsappPaymentReservationService paymentReservationService,
            S3StorageService storageService,
            ApplicationEventPublisher applicationEventPublisher) {
        this.conversationRepository = conversationRepository;
        this.conversationTagRepository = conversationTagRepository;
        this.messageRepository = messageRepository;
        this.paymentRequestRepository = paymentRequestRepository;
        this.tagRepository = tagRepository;
        this.usuarioRepository = usuarioRepository;
        this.empresaRepository = empresaRepository;
        this.clienteRepository = clienteRepository;
        this.sucursalRepository = sucursalRepository;
        this.comprobanteConfigRepository = comprobanteConfigRepository;
        this.ventaRepository = ventaRepository;
        this.ventaDetalleRepository = ventaDetalleRepository;
        this.pagoRepository = pagoRepository;
        this.sucursalMetodoPagoConfigRepository = sucursalMetodoPagoConfigRepository;
        this.usuarioSucursalAccessService = usuarioSucursalAccessService;
        this.productoService = productoService;
        this.productoVarianteService = productoVarianteService;
        this.ventaService = ventaService;
        this.bridgeService = bridgeService;
        this.eventService = eventService;
        this.connectionService = connectionService;
        this.aiJobService = aiJobService;
        this.aiMemoryService = aiMemoryService;
        this.aiSaleDraftService = aiSaleDraftService;
        this.aiClientWriter = aiClientWriter;
        this.paymentReservationService = paymentReservationService;
        this.storageService = storageService;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    public List<ConversationResponse> listarConversaciones(String status, Usuario usuarioSesion) {
        Usuario actor = requireCrmUser(usuarioSesion);
        boolean admin = isAdmin(actor);
        String normalized = normalizeConversationStatus(status);
        List<CrmWhatsappConversation> conversations = switch (normalized) {
            case ESPERA, ATENDIDO, RESUELTO -> conversationRepository.findByStatusOrderByLastMessageAtDesc(normalized);
            default -> conversationRepository.findAllByOrderByLastMessageAtDesc();
        };
        return conversations.stream()
                .filter(conversation -> canListConversation(conversation, actor, admin, normalized))
                .map(conversation -> toConversationResponse(conversation, shouldHideWaitingPreview(conversation, admin)))
                .toList();
    }

    public SseEmitter suscribirEventos(Usuario usuarioSesion) {
        Usuario actor = requireCrmUser(usuarioSesion);
        return eventService.subscribe(actor.getIdUsuario(), isAdmin(actor));
    }

    @Transactional(readOnly = true)
    public ConversationPageResponse listarConversacionesPaginadas(
            String status,
            String view,
            String q,
            Long tagId,
            int page,
            int size,
            Usuario usuarioSesion) {
        Usuario actor = requireCrmUser(usuarioSesion);
        boolean admin = isAdmin(actor);
        String normalizedView = clean(view).toUpperCase(Locale.ROOT);
        CrmWhatsappAttentionQueue requestedQueue = normalizeAttentionQueue(view);
        boolean waitingOnly = "WAITING".equals(normalizedView);
        boolean hasView = waitingOnly || requestedQueue != null;
        // WAITING is expressed by waitingOnly. Passing WAITING to the primary
        // view predicate excludes every row before that filter is evaluated.
        String effectiveView = waitingOnly ? "ALL" : hasView ? normalizedView : "ALL";
        String normalizedStatus = hasView ? "ALL" : normalizeConversationStatus(status);
        String term = normalizeConversationSearchTerm(q);
        int pageNumber = Math.max(0, page);
        int pageSize = Math.max(1, Math.min(size <= 0 ? 10 : size, 50));

        if (tagId != null) {
            requireTagEmpresa(tagId, resolverIdEmpresa(actor));
        }

        Page<CrmWhatsappConversation> conversationPage = conversationRepository.findAccessibleConversations(
                actor.getIdUsuario(),
                admin,
                normalizedStatus,
                effectiveView,
                waitingOnly,
                term.isBlank() ? null : term,
                tagId,
                PageRequest.of(pageNumber, pageSize));

        List<Long> conversationIds = conversationPage.getContent().stream()
                .map(CrmWhatsappConversation::getIdConversation)
                .toList();
        Map<Long, List<CrmConversationTagResponse>> tagsByConversation = conversationIds.isEmpty()
                ? Map.of()
                : conversationTagRepository.findActiveByConversationIds(conversationIds)
                        .stream()
                        .collect(java.util.stream.Collectors.groupingBy(
                                relation -> relation.getConversation().getIdConversation(),
                                LinkedHashMap::new,
                                java.util.stream.Collectors.mapping(
                                        this::toConversationTagResponse,
                                        java.util.stream.Collectors.toList())));

        List<ConversationResponse> content = conversationPage.getContent().stream()
                .map(conversation -> toConversationResponse(
                        conversation,
                        shouldHideWaitingPreview(conversation, admin),
                        tagsByConversation.getOrDefault(conversation.getIdConversation(), List.of())))
                .toList();

        List<Object[]> countRows = conversationRepository.countAccessibleConversationsByQueue(
                actor.getIdUsuario(),
                admin,
                term.isBlank() ? null : term,
                tagId);
        Object[] queueCounts = countRows.isEmpty() ? new Object[0] : countRows.getFirst();
        long attended = numericCount(queueCounts, 0);
        long aiAttending = numericCount(queueCounts, 1);
        long waiting = numericCount(queueCounts, 2);
        long resolved = numericCount(queueCounts, 3);

        ConversationStatusCounts counts = new ConversationStatusCounts(
                attended + aiAttending + waiting + resolved,
                attended,
                aiAttending,
                waiting,
                resolved);
        return new ConversationPageResponse(
                content,
                conversationPage.getNumber(),
                conversationPage.getSize(),
                conversationPage.getTotalPages(),
                conversationPage.getTotalElements(),
                conversationPage.getNumberOfElements(),
                conversationPage.isFirst(),
                conversationPage.isLast(),
                conversationPage.isEmpty(),
                counts);
    }

    @Transactional(readOnly = true)
    public MessagePageResponse listarMensajes(
            Long conversationId,
            Long beforeId,
            Long afterId,
            int limit,
            Usuario usuarioSesion) {
        Usuario actor = requireCrmUser(usuarioSesion);
        CrmWhatsappConversation conversation = requireConversation(conversationId);
        requireCanReadMessages(conversation, actor);
        if (beforeId != null && afterId != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "beforeId y afterId no pueden combinarse");
        }

        int pageSize = Math.max(1, Math.min(limit <= 0 ? 15 : limit, 50));
        List<CrmWhatsappMessage> selected;
        boolean hasMoreBefore;
        if (afterId != null) {
            selected = messageRepository.findMessagesAfter(
                    conversationId,
                    afterId,
                    PageRequest.of(0, pageSize));
            hasMoreBefore = false;
        } else {
            List<CrmWhatsappMessage> newestFirst = messageRepository.findMessageHistory(
                    conversationId,
                    beforeId,
                    PageRequest.of(0, pageSize + 1));
            hasMoreBefore = newestFirst.size() > pageSize;
            if (hasMoreBefore) {
                newestFirst = new ArrayList<>(newestFirst.subList(0, pageSize));
            } else {
                newestFirst = new ArrayList<>(newestFirst);
            }
            java.util.Collections.reverse(newestFirst);
            selected = newestFirst;
        }

        List<MessageResponse> content = toMessageResponses(selected);
        Long oldestId = content.isEmpty() ? null : content.getFirst().id();
        Long newestId = content.isEmpty() ? null : content.getLast().id();
        return new MessagePageResponse(content, oldestId, newestId, hasMoreBefore);
    }

    @Transactional(readOnly = true)
    public ConversationResponse obtenerConversacion(Long conversationId, Usuario usuarioSesion) {
        Usuario actor = requireCrmUser(usuarioSesion);
        CrmWhatsappConversation conversation = requireConversation(conversationId);
        boolean admin = isAdmin(actor);
        if (!canListConversation(conversation, actor, admin, "ALL")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puedes ver esta conversacion");
        }
        return toConversationResponse(conversation, shouldHideWaitingPreview(conversation, admin));
    }

    public List<TransferUserResponse> listarUsuariosTransferencia(Usuario usuarioSesion) {
        requireCrmUser(usuarioSesion);
        return usuarioRepository.findUsuariosConAccesoCrm().stream()
                .map(usuario -> new TransferUserResponse(
                        usuario.getIdUsuario(),
                        fullName(usuario),
                        usuario.getRol().name(),
                        usuario.getCorreo()))
                .toList();
    }

    @Transactional(readOnly = true)
    public AttentionModeResponse obtenerAtencion(Long conversationId, Usuario usuarioSesion) {
        Usuario actor = requireCrmUser(usuarioSesion);
        CrmWhatsappAiMemoryService.MemoryResponse memory = aiMemoryService.get(conversationId, actor);
        CrmWhatsappConversation conversation = requireConversation(conversationId);
        CrmWhatsappAiJobService.AutomaticAvailability availability = aiJobService.automaticAvailability(conversation);
        return new AttentionModeResponse(
                toConversationResponse(conversation, false),
                memory,
                availability.available(),
                availability.reason());
    }

    @Transactional
    public ConversationResponse aceptar(Long conversationId, Usuario usuarioSesion) {
        return cambiarAtencion(conversationId, new AttentionModeRequest("HUMANA"), usuarioSesion).conversation();
    }

    @Transactional
    public CrmWhatsappConversation asignarParaOperacion(Long conversationId, Usuario usuarioSesion) {
        Usuario actor = requireCrmUser(usuarioSesion);
        CrmWhatsappConversation conversation = conversationRepository.findForUpdateById(conversationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversacion no encontrada"));
        if (RESUELTO.equals(conversation.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Reabre la conversacion antes de operar");
        }
        Usuario assigned = conversation.getAssignedUser();
        if (assigned != null && !assigned.getIdUsuario().equals(actor.getIdUsuario())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Este chat ya fue aceptado por " + fullName(assigned));
        }
        if (assigned == null) {
            conversation.setAssignedUser(actor);
            conversation.setAssignedAt(LocalDateTime.now());
            conversation.setStatus(ATENDIDO);
            conversation.setUnreadCount(0);
            conversation.setAiAttentionMode(CrmWhatsappAiAttentionMode.HUMANA);
            conversation.setAiAttentionModeExplicit(true);
            conversation = conversationRepository.save(conversation);
            aiMemoryService.pauseForHuman(conversation, "Operacion CRM asumida por un asesor");
            registrarMensajeSistema(conversation, fullName(actor) + " acepto el chat.");
        }
        return conversation;
    }

    public ConversationResponse conversationResponse(CrmWhatsappConversation conversation) {
        return toConversationResponse(conversation, false);
    }

    @Transactional
    public AttentionModeResponse cambiarAtencion(
            Long conversationId,
            AttentionModeRequest request,
            Usuario usuarioSesion) {
        Usuario actor = requireCrmUser(usuarioSesion);
        CrmWhatsappAiAttentionMode requestedMode;
        try {
            requestedMode = CrmWhatsappAiAttentionMode.valueOf(clean(request == null ? null : request.mode()).toUpperCase());
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "mode debe ser AUTOMATICA o HUMANA");
        }
        CrmWhatsappConversation conversation = conversationRepository.findForUpdateById(conversationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversacion no encontrada"));

        if (RESUELTO.equals(conversation.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reabre la conversacion antes de cambiar la atencion");
        }

        boolean newlyAssigned = false;
        CrmWhatsappAiMemoryService.MemoryResponse memory;
        if (requestedMode == CrmWhatsappAiAttentionMode.HUMANA) {
            Usuario assignedUser = conversation.getAssignedUser();
            if (assignedUser != null && !assignedUser.getIdUsuario().equals(actor.getIdUsuario())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Este chat ya fue aceptado por otro usuario");
            }
            newlyAssigned = assignedUser == null;
            conversation.setAiAttentionMode(CrmWhatsappAiAttentionMode.HUMANA);
            conversation.setAiAttentionModeExplicit(true);
            conversation.setAssignedUser(actor);
            conversation.setAssignedAt(newlyAssigned ? LocalDateTime.now() : conversation.getAssignedAt());
            conversation.setStatus(ATENDIDO);
            conversation.setUnreadCount(0);
            conversation = conversationRepository.save(conversation);
            memory = aiMemoryService.pauseForHuman(conversation, "Atencion humana activada");
            if (newlyAssigned) registrarMensajeSistema(conversation, fullName(actor) + " acepto el chat.");
            else publishRealtimeEvent("conversation.updated", conversation, null);
        } else {
            if (conversation.getAssignedUser() != null
                    && !isAdmin(actor)
                    && !isAssignedTo(conversation, actor)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo el asesor asignado puede liberar este chat");
            }
            if (conversation.getAssignedUser() == null && !isAdmin(actor)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acepta el chat antes de activar la IA");
            }
            conversation.setAiAttentionMode(CrmWhatsappAiAttentionMode.AUTOMATICA);
            conversation.setAiAttentionModeExplicit(true);
            conversation.setAssignedUser(null);
            conversation.setAssignedAt(null);
            conversation.setStatus(ESPERA);
            conversation = conversationRepository.save(conversation);
            memory = aiMemoryService.resumeAutomatic(conversation);
            publishRealtimeEvent("conversation.updated", conversation, null);
        }

        CrmWhatsappAiJobService.AutomaticAvailability availability = aiJobService.automaticAvailability(conversation);
        return new AttentionModeResponse(
                toConversationResponse(conversation, false),
                memory,
                availability.available(),
                availability.reason());
    }

    @Transactional
    public ConversationResponse transferir(Long conversationId, TransferRequest request, Usuario usuarioSesion) {
        Usuario actor = requireCrmUser(usuarioSesion);
        if (request == null || request.assignedUserId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "assignedUserId es requerido");
        }
        CrmWhatsappConversation conversation = conversationRepository.findForUpdateById(conversationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversacion no encontrada"));
        if (conversation.getAssignedUser() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Acepta el chat antes de transferir");
        }
        if (!isAdmin(actor) && !isAssignedTo(conversation, actor)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puedes transferir esta conversacion");
        }

        Usuario target = usuarioRepository.findByIdUsuarioAndDeletedAtIsNull(request.assignedUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Usuario destino no valido"));
        if (!canReceiveCrmTransfer(target)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Usuario destino no tiene acceso al CRM");
        }
        if (isAssignedTo(conversation, target)) {
            return toConversationResponse(conversation);
        }

        Integer previousAssignedUserId = conversation.getAssignedUser().getIdUsuario();
        conversation.setAssignedUser(target);
        conversation.setAssignedAt(LocalDateTime.now());
        conversation.setAiAttentionMode(CrmWhatsappAiAttentionMode.HUMANA);
        conversation.setAiAttentionModeExplicit(true);
        conversation = conversationRepository.save(conversation);
        ConversationResponse updated = toConversationResponse(conversation);
        eventService.publishToUserAfterCommit(
                new CrmRealtimeEvent("conversation.removed", conversation.getIdConversation(), updated, null),
                previousAssignedUserId);
        publishRealtimeEvent("conversation.updated", conversation, null);
        return updated;
    }

    @Transactional
    public QuickSaleContextResponse obtenerVentaRapidaContexto(Long conversationId, Usuario usuarioSesion) {
        Usuario actor = requireCrmUser(usuarioSesion);
        CrmWhatsappConversation conversation = requireConversation(conversationId);
        requireCanReadMessages(conversation, actor);

        if (conversation.getCliente() == null) {
            List<Cliente> coincidencias = buscarClientesPorTelefonoExacto(conversation, actor);
            if (coincidencias.size() == 1) {
                sincronizarClienteEnConversacion(conversation, coincidencias.get(0));
                conversation = conversationRepository.save(conversation);
            }
        }

        Cliente cliente = conversation.getCliente();
        return new QuickSaleContextResponse(
                conversation.getIdConversation(),
                clean(conversation.getContactName()).isBlank() ? null : conversation.getContactName(),
                nullIfBlank(resolveConversationCrmPhone(conversation)),
                toClienteResumen(cliente),
                listarSucursalesVentaRapida(conversation, actor),
                listarComprobantesVentaRapida());
    }

    public List<ClienteResumenResponse> buscarClientes(String q, Usuario usuarioSesion) {
        Usuario actor = requireCrmUser(usuarioSesion);
        String term = clean(q);
        if (term.length() < 2) {
            return List.of();
        }
        Integer idEmpresa = resolverIdEmpresa(actor);
        String phone = normalizeCrmPhoneNumber(term);
        if (!phone.isBlank()) {
            Map<Integer, Cliente> matches = new LinkedHashMap<>();
            for (String variant : phoneVariants(phone)) {
                clienteRepository.buscarConFiltros(variant, idEmpresa, null, PageRequest.of(0, 10))
                        .getContent()
                        .forEach(cliente -> matches.putIfAbsent(cliente.getIdCliente(), cliente));
            }
            return matches.values().stream().limit(10).map(this::toClienteResumen).toList();
        }
        return clienteRepository.buscarConFiltros(term, idEmpresa, null, PageRequest.of(0, 10))
                .getContent()
                .stream()
                .map(this::toClienteResumen)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CrmTagResponse> listarEtiquetas(Usuario usuarioSesion) {
        Usuario actor = requireCrmUser(usuarioSesion);
        Integer idEmpresa = resolverIdEmpresa(actor);
        List<CrmWhatsappTag> tags = tagRepository.findByEmpresa_IdEmpresaAndDeletedAtIsNullOrderByNombreAsc(idEmpresa);
        Map<Long, Long> usageByTag = new LinkedHashMap<>();
        if (!tags.isEmpty()) {
            List<Long> tagIds = tags.stream().map(CrmWhatsappTag::getIdTag).toList();
            for (Object[] row : conversationTagRepository.countActiveByTagIds(tagIds)) {
                usageByTag.put((Long) row[0], (Long) row[1]);
            }
        }
        return tags.stream()
                .map(tag -> new CrmTagResponse(
                        tag.getIdTag(),
                        tag.getNombre(),
                        tag.getColor(),
                        usageByTag.getOrDefault(tag.getIdTag(), 0L)))
                .toList();
    }

    @Transactional
    public CrmTagResponse crearEtiqueta(CrmTagRequest request, Usuario usuarioSesion) {
        Usuario actor = requireCrmUser(usuarioSesion);
        Integer idEmpresa = resolverIdEmpresa(actor);
        String nombre = validarNombreEtiqueta(request == null ? null : request.nombre());
        String color = validarColorEtiqueta(request == null ? null : request.color());
        tagRepository.findFirstByEmpresa_IdEmpresaAndNombreIgnoreCaseAndDeletedAtIsNull(idEmpresa, nombre)
                .ifPresent(tag -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe una etiqueta con ese nombre");
                });
        Empresa empresa = empresaRepository.findById(idEmpresa)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Empresa no encontrada"));
        CrmWhatsappTag tag = new CrmWhatsappTag();
        tag.setEmpresa(empresa);
        tag.setNombre(nombre);
        tag.setColor(color);
        CrmWhatsappTag saved = tagRepository.save(tag);
        return new CrmTagResponse(saved.getIdTag(), saved.getNombre(), saved.getColor(), 0);
    }

    @Transactional
    public CrmTagResponse actualizarEtiqueta(Long tagId, CrmTagRequest request, Usuario usuarioSesion) {
        Usuario actor = requireCrmUser(usuarioSesion);
        Integer idEmpresa = resolverIdEmpresa(actor);
        CrmWhatsappTag tag = requireTagEmpresa(tagId, idEmpresa);
        String nombre = validarNombreEtiqueta(request == null ? null : request.nombre());
        String color = validarColorEtiqueta(request == null ? null : request.color());
        tagRepository.findFirstByEmpresa_IdEmpresaAndNombreIgnoreCaseAndDeletedAtIsNull(idEmpresa, nombre)
                .filter(existing -> !existing.getIdTag().equals(tag.getIdTag()))
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe una etiqueta con ese nombre");
                });
        tag.setNombre(nombre);
        tag.setColor(color);
        CrmWhatsappTag saved = tagRepository.save(tag);
        long usage = conversationTagRepository.countActiveByTagIds(List.of(saved.getIdTag()))
                .stream()
                .findFirst()
                .map(row -> (Long) row[1])
                .orElse(0L);
        return new CrmTagResponse(saved.getIdTag(), saved.getNombre(), saved.getColor(), usage);
    }

    @Transactional
    public void eliminarEtiqueta(Long tagId, Usuario usuarioSesion) {
        Usuario actor = requireCrmUser(usuarioSesion);
        CrmWhatsappTag tag = requireTagEmpresa(tagId, resolverIdEmpresa(actor));
        LocalDateTime now = LocalDateTime.now();
        tag.setDeletedAt(now);
        for (CrmWhatsappConversationTag relation : conversationTagRepository.findByTag_IdTagAndDeletedAtIsNull(tagId)) {
            relation.setDeletedAt(now);
        }
        tagRepository.save(tag);
    }

    @Transactional
    public DeleteWhatsappMessagesResponse eliminarTodosLosMensajes(Usuario usuarioSesion) {
        Usuario actor = requireCrmUser(usuarioSesion);
        if (!isAdmin(actor)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo un administrador puede eliminar el historial del CRM");
        }
        int deletedMediaFiles = 0;
        for (String storagePath : messageRepository.findAllMediaStoragePaths()) {
            try {
                storageService.deleteByKey(storagePath);
                deletedMediaFiles++;
            } catch (RuntimeException ignored) {
                // El mensaje se purga aunque un adjunto antiguo ya no exista o no sea accesible.
            }
        }
        paymentRequestRepository.findByStatusIn(List.of(
                CrmWhatsappPaymentRequestStatus.PENDING_EVIDENCE,
                CrmWhatsappPaymentRequestStatus.UNDER_REVIEW,
                CrmWhatsappPaymentRequestStatus.READY_FOR_SALE)).forEach(request -> {
                    paymentReservationService.release(request, actor, "Historial de WhatsApp eliminado");
                    request.setStatus(CrmWhatsappPaymentRequestStatus.CANCELLED);
                    paymentRequestRepository.save(request);
                });
        paymentRequestRepository.detachAllAiSaleDrafts();
        int deletedMessages = messageRepository.deleteAllWhatsappMessages();
        int deletedTagAssignments = conversationTagRepository.deleteAllConversationTags();
        int deletedConversations = conversationRepository.deleteAllWhatsappConversations();
        return new DeleteWhatsappMessagesResponse(
                deletedMessages,
                deletedConversations,
                deletedTagAssignments,
                deletedMediaFiles);
    }

    @Transactional(readOnly = true)
    public CrmWhatsappReportResponse obtenerReporteCrm(
            String filtro,
            LocalDate desdeRequest,
            LocalDate hastaRequest,
            Usuario usuarioSesion) {
        Usuario actor = requireCrmUser(usuarioSesion);
        boolean admin = isAdmin(actor);
        Integer idUsuario = actor.getIdUsuario();
        CrmReportRange range = resolveCrmReportRange(filtro, desdeRequest, hastaRequest);
        LocalDateTime desde = range.desde().atStartOfDay();
        LocalDateTime hasta = range.hastaExclusive();

        long chatsTotales = conversationRepository.countAccessibleCreatedInRange(idUsuario, admin, desde, hasta);
        long chatsResueltos = conversationRepository.countAccessibleResolvedInRange(idUsuario, admin, desde, hasta);
        long chatsEspera = conversationRepository.countAccessibleByStatusCreatedInRange(idUsuario, admin, ESPERA, desde, hasta);
        long chatsAtendidos = conversationRepository.countAccessibleByStatusCreatedInRange(idUsuario, admin, ATENDIDO, desde, hasta);
        long mensajesRecibidos = messageRepository.countAccessibleByDirectionInRange(idUsuario, admin, "INCOMING", desde, hasta);
        long mensajesEnviados = messageRepository.countAccessibleByDirectionInRange(idUsuario, admin, "OUTGOING", desde, hasta);
        long clientesVinculados = conversationRepository.countAccessibleLinkedClientsInRange(idUsuario, admin, desde, hasta);
        long clientesNuevos = conversationRepository.countAccessibleNewLinkedClientsInRange(idUsuario, admin, desde, hasta);

        List<Object[]> mensajesRows = range.hourly()
                ? messageRepository.countAccessibleMessagesByHour(idUsuario, admin, desde, hasta)
                : messageRepository.countAccessibleMessagesByDate(idUsuario, admin, desde, hasta);
        List<Object[]> resueltosRows = range.hourly()
                ? conversationRepository.countAccessibleResolvedByHour(idUsuario, admin, desde, hasta)
                : conversationRepository.countAccessibleResolvedByDate(idUsuario, admin, desde, hasta);
        List<Object[]> clientesRows = range.hourly()
                ? messageRepository.countAccessibleNewLinkedClientsByHour(idUsuario, admin, desde, hasta)
                : messageRepository.countAccessibleNewLinkedClientsByDate(idUsuario, admin, desde, hasta);

        return new CrmWhatsappReportResponse(
                range.filtroAplicado(),
                range.desde(),
                range.hasta(),
                new CrmReportKpis(
                        chatsTotales,
                        chatsResueltos,
                        chatsEspera,
                        chatsAtendidos,
                        mensajesRecibidos,
                        mensajesEnviados,
                        clientesVinculados,
                        clientesNuevos),
                buildCrmReportSeries(range, mensajesRows),
                buildCrmReportSeries(range, resueltosRows),
                buildCrmReportSeries(range, clientesRows),
                mapCrmReportCategoryRows(conversationRepository.countAccessibleByStatusDistribution(idUsuario, admin, desde, hasta)),
                mapCrmReportCategoryRows(messageRepository.countAccessibleMessagesByType(idUsuario, admin, desde, hasta)),
                mapCrmReportCategoryRows(messageRepository.countAccessibleMessagesByDirection(idUsuario, admin, desde, hasta)),
                mergeCrmUserRanking(
                        conversationRepository.countAccessibleAssignedByUser(idUsuario, admin, desde, hasta),
                        conversationRepository.countAccessibleResolvedByUser(idUsuario, admin, desde, hasta)));
    }

    @Transactional(readOnly = true)
    public PagedResponse<CrmWhatsappContactResponse> listarContactosCrm(
            String q,
            String filter,
            int page,
            int size,
            Usuario usuarioSesion) {
        Usuario actor = requireCrmUser(usuarioSesion);
        int pageNumber = Math.max(0, page);
        int pageSize = Math.max(1, Math.min(size <= 0 ? 12 : size, 50));
        String term = clean(q);
        String normalizedFilter = normalizeContactFilter(filter);
        Page<CrmWhatsappConversation> contactsPage = conversationRepository.findAccessibleContacts(
                resolverIdEmpresa(actor),
                actor.getIdUsuario(),
                isAdmin(actor),
                term.isBlank() ? null : term,
                normalizedFilter,
                PageRequest.of(pageNumber, pageSize));

        List<Long> conversationIds = contactsPage.getContent().stream()
                .map(CrmWhatsappConversation::getIdConversation)
                .toList();
        Map<Long, List<CrmConversationTagResponse>> tagsByConversation = conversationIds.isEmpty()
                ? Map.of()
                : conversationTagRepository.findActiveByConversationIds(conversationIds)
                        .stream()
                        .collect(java.util.stream.Collectors.groupingBy(
                                relation -> relation.getConversation().getIdConversation(),
                                LinkedHashMap::new,
                                java.util.stream.Collectors.mapping(this::toConversationTagResponse, java.util.stream.Collectors.toList())));

        return PagedResponse.fromPage(contactsPage.map(conversation -> {
            Cliente cliente = conversation.getCliente();
            List<CrmConversationTagResponse> tags = tagsByConversation.getOrDefault(
                    conversation.getIdConversation(),
                    List.of());
            long comprasEmitidas = cliente == null
                    ? 0
                    : ventaRepository.countByClienteIdClienteAndDeletedAtIsNullAndEstado(cliente.getIdCliente(), "EMITIDA");
            BigDecimal montoTotal = cliente == null
                    ? BigDecimal.ZERO
                    : ventaRepository.sumarTotalPorClienteYEstado(cliente.getIdCliente(), "EMITIDA");
            LocalDateTime ultimaCompra = cliente == null
                    ? null
                    : ventaRepository
                            .findTop3ByClienteIdClienteAndDeletedAtIsNullAndEstadoOrderByFechaDesc(cliente.getIdCliente(), "EMITIDA")
                            .stream()
                            .findFirst()
                            .map(Venta::getFecha)
                            .orElse(null);
            return new CrmWhatsappContactResponse(
                    conversation.getIdConversation(),
                    conversation.getStatus(),
                    conversation.getLastMessage(),
                    conversation.getLastMessageType(),
                    conversation.getLastMessageAt(),
                    toClienteResumen(cliente),
                    tags,
                    comprasEmitidas,
                    montoTotal == null ? BigDecimal.ZERO : montoTotal,
                    ultimaCompra,
                    tieneDatosIncompletos(cliente),
                    comprasEmitidas > 0,
                    !tags.isEmpty());
        }));
    }

    @Transactional(readOnly = true)
    public List<CrmConversationTagResponse> listarEtiquetasConversacion(Long conversationId, Usuario usuarioSesion) {
        Usuario actor = requireCrmUser(usuarioSesion);
        CrmWhatsappConversation conversation = requireConversation(conversationId);
        requireCanReadMessages(conversation, actor);
        return listarEtiquetasConversacionInterno(conversationId);
    }

    @Transactional
    public List<CrmConversationTagResponse> asignarEtiquetaConversacion(Long conversationId, Long tagId, Usuario usuarioSesion) {
        Usuario actor = requireCrmUser(usuarioSesion);
        CrmWhatsappConversation conversation = requireConversation(conversationId);
        requireCanOperateAssigned(conversation, actor);
        CrmWhatsappTag tag = requireTagEmpresa(tagId, resolverIdEmpresa(actor));
        CrmWhatsappConversationTag relation = conversationTagRepository
                .findFirstByConversation_IdConversationAndTag_IdTagOrderByIdConversationTagAsc(conversationId, tagId)
                .orElseGet(CrmWhatsappConversationTag::new);
        relation.setConversation(conversation);
        relation.setTag(tag);
        relation.setDeletedAt(null);
        conversationTagRepository.save(relation);
        return listarEtiquetasConversacionInterno(conversationId);
    }

    @Transactional
    public List<CrmConversationTagResponse> quitarEtiquetaConversacion(Long conversationId, Long tagId, Usuario usuarioSesion) {
        Usuario actor = requireCrmUser(usuarioSesion);
        CrmWhatsappConversation conversation = requireConversation(conversationId);
        requireCanOperateAssigned(conversation, actor);
        requireTagEmpresa(tagId, resolverIdEmpresa(actor));
        conversationTagRepository
                .findFirstByConversation_IdConversationAndTag_IdTagAndDeletedAtIsNull(conversationId, tagId)
                .ifPresent(relation -> {
                    relation.setDeletedAt(LocalDateTime.now());
                    conversationTagRepository.save(relation);
                });
        return listarEtiquetasConversacionInterno(conversationId);
    }

    @Transactional
    public QuickSaleContextResponse vincularCliente(Long conversationId, LinkClienteRequest request, Usuario usuarioSesion) {
        Usuario actor = requireCrmUser(usuarioSesion);
        if (request == null || request.idCliente() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idCliente es requerido");
        }
        CrmWhatsappConversation conversation = asignarParaOperacion(conversationId, actor);
        requireCanOperateAssigned(conversation, actor);
        Cliente cliente = requireClienteEmpresa(request.idCliente(), actor);
        sincronizarClienteEnConversacion(conversation, cliente);
        conversationRepository.save(conversation);
        return obtenerVentaRapidaContexto(conversationId, actor);
    }

    @Transactional
    public QuickSaleContextResponse registrarYVincularCliente(Long conversationId, ClienteCreateRequest request, Usuario usuarioSesion) {
        Usuario actor = requireCrmUser(usuarioSesion);
        CrmWhatsappConversation conversation = asignarParaOperacion(conversationId, actor);
        requireCanOperateAssigned(conversation, actor);
        Cliente cliente = crearClienteCrm(request, actor);
        sincronizarClienteEnConversacion(conversation, cliente);
        conversationRepository.save(conversation);
        return obtenerVentaRapidaContexto(conversationId, actor);
    }

    @Transactional
    public QuickSaleContextResponse actualizarClienteConversacion(Long conversationId, ClienteUpdateRequest request, Usuario usuarioSesion) {
        Usuario actor = requireCrmUser(usuarioSesion);
        CrmWhatsappConversation conversation = requireConversation(conversationId);
        requireCanOperateAssigned(conversation, actor);
        Cliente cliente = conversation.getCliente();
        if (cliente == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Primero registra un cliente");
        }
        cliente = requireClienteEmpresa(cliente.getIdCliente(), actor);
        Integer idClienteActual = cliente.getIdCliente();
        TipoDocumento tipoDocumento = request == null || request.tipoDocumento() == null
                ? TipoDocumento.SIN_DOC
                : request.tipoDocumento();
        String telefono = normalizeCrmPhoneNumber(request == null ? null : request.telefono());
        if (telefono.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Registra un celular peruano valido de 9 digitos");
        }
        String nombres = clean(request == null ? null : request.nombres());
        if (nombres.length() < 2) {
            nombres = "CLIENTE " + telefono;
        }
        String estado = clean(request == null ? null : request.estado()).isBlank() ? "ACTIVO" : clean(request.estado()).toUpperCase();
        if (!Set.of("ACTIVO", "INACTIVO").contains(estado)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Estado permitido: ACTIVO o INACTIVO");
        }
        Integer idEmpresa = resolverIdEmpresa(actor);
        Cliente duplicadoTelefono = clienteRepository
                .findByEmpresa_IdEmpresaAndTelefonoInAndDeletedAtIsNullOrderByIdClienteAsc(idEmpresa, phoneVariants(telefono))
                .stream()
                .filter(existing -> !existing.getIdCliente().equals(idClienteActual))
                .findFirst()
                .orElse(null);
        if (duplicadoTelefono != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El telefono ya esta registrado en otro cliente");
        }
        cliente.setTipoDocumento(tipoDocumento);
        cliente.setNroDocumento(normalizarDocumento(tipoDocumento, request == null ? null : request.nroDocumento()));
        cliente.setNombres(nombres);
        cliente.setTelefono(telefono);
        cliente.setCorreo(clean(request == null ? null : request.correo()).isBlank() ? null : clean(request.correo()));
        cliente.setDireccion(clean(request == null ? null : request.direccion()).isBlank() ? null : clean(request.direccion()));
        cliente.setEstado(estado);
        Cliente actualizado = clienteRepository.saveAndFlush(cliente);
        sincronizarClienteEnConversacion(conversation, actualizado);
        conversationRepository.save(conversation);
        return obtenerVentaRapidaContexto(conversationId, actor);
    }

    public VentasClienteCrmResponse listarVentasCliente(Long conversationId, Integer idSucursal, int page, Usuario usuarioSesion) {
        Usuario actor = requireCrmUser(usuarioSesion);
        CrmWhatsappConversation conversation = requireConversation(conversationId);
        requireCanReadMessages(conversation, actor);
        if (conversation.getCliente() == null) {
            return new VentasClienteCrmResponse(
                    new PagedResponse<>(List.of(), Math.max(0, page), 10, 0, 0, 0, true, true, true),
                    List.of(),
                    List.of());
        }
        if (page < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page debe ser mayor o igual a 0");
        }
        Integer idSucursalFiltro = null;
        Page<Venta> ventas = ventaRepository.buscarConFiltros(
                null,
                idSucursalFiltro,
                null,
                conversation.getCliente().getIdCliente(),
                null,
                null,
                null,
                PageRequest.of(page, 10, Sort.by("fecha").descending()));
        List<Integer> ventaIds = ventas.getContent().stream()
                .map(Venta::getIdVenta)
                .toList();
        Map<Integer, List<VentaDetalle>> detallesPorVenta = ventaIds.isEmpty()
                ? Map.of()
                : ventaDetalleRepository.findActivosByVentaIds(ventaIds).stream()
                        .collect(java.util.stream.Collectors.groupingBy(
                                detalle -> detalle.getVenta().getIdVenta(),
                                LinkedHashMap::new,
                                java.util.stream.Collectors.toList()));
        Map<Integer, List<Pago>> pagosPorVenta = ventaIds.isEmpty()
                ? Map.of()
                : pagoRepository.findActivosByVentaIds(ventaIds).stream()
                        .collect(java.util.stream.Collectors.groupingBy(
                                pago -> pago.getVenta().getIdVenta(),
                                LinkedHashMap::new,
                                java.util.stream.Collectors.toList()));

        PagedResponse<VentaRapidaHistorialResponse> historial = PagedResponse.fromPage(ventas.map(
                venta -> toVentaRapidaHistorial(
                        venta,
                        detallesPorVenta.getOrDefault(venta.getIdVenta(), List.of()),
                        pagosPorVenta.getOrDefault(venta.getIdVenta(), List.of()))));

        Page<Venta> ventasAnalitica = ventaRepository.buscarConFiltros(
                null,
                idSucursalFiltro,
                null,
                conversation.getCliente().getIdCliente(),
                null,
                null,
                null,
                PageRequest.of(0, 1000, Sort.by("fecha").descending()));
        List<Integer> ventaIdsEmitidas = ventasAnalitica.getContent().stream()
                .filter(venta -> "EMITIDA".equalsIgnoreCase(clean(venta.getEstado())))
                .map(Venta::getIdVenta)
                .toList();
        List<VentaDetalle> detallesAnalitica = ventaIdsEmitidas.isEmpty()
                ? List.of()
                : ventaDetalleRepository.findActivosByVentaIds(ventaIdsEmitidas);
        List<Pago> pagosAnalitica = ventaIdsEmitidas.isEmpty()
                ? List.of()
                : pagoRepository.findActivosByVentaIds(ventaIdsEmitidas);

        return new VentasClienteCrmResponse(
                historial,
                topVariantesCompradas(detallesAnalitica),
                metodosPagoUsados(pagosAnalitica));
    }

    public ProductoVarianteListadoResumenPageResponse listarCatalogoVenta(
            Long conversationId,
            Integer idSucursal,
            String q,
            Integer idCategoria,
            Integer idColor,
            Boolean conOferta,
            Boolean soloDisponibles,
            int page,
            Usuario usuarioSesion) {
        Usuario actor = requireCrmUser(usuarioSesion);
        CrmWhatsappConversation conversation = requireConversation(conversationId);
        requireCanReadMessages(conversation, actor);
        Integer idSucursalVenta = resolverIdSucursalFiltroCrm(actor, conversation, idSucursal);
        return productoVarianteService.listarResumenPaginadoCrm(
                q,
                page,
                idCategoria,
                idColor,
                conOferta,
                soloDisponibles == null ? Boolean.TRUE : soloDisponibles,
                idSucursalVenta,
                actor);
    }

    public Object listarCatalogoVenta(
            Long conversationId,
            Integer idSucursal,
            String q,
            Integer idCategoria,
            Integer idColor,
            Boolean conOferta,
            Boolean soloDisponibles,
            String view,
            int page,
            Usuario usuarioSesion) {
        if (!"productos".equalsIgnoreCase(clean(view))) {
            return listarCatalogoVenta(conversationId, idSucursal, q, idCategoria, idColor, conOferta, soloDisponibles, page, usuarioSesion);
        }

        Usuario actor = requireCrmUser(usuarioSesion);
        CrmWhatsappConversation conversation = requireConversation(conversationId);
        requireCanReadMessages(conversation, actor);
        Integer idSucursalVenta = resolverIdSucursalFiltroCrm(actor, conversation, idSucursal);
        if (clean(q).isBlank()) {
            return productoService.listarResumenPaginado(
                    page,
                    idCategoria,
                    idColor,
                    conOferta,
                    soloDisponibles == null ? Boolean.TRUE : soloDisponibles,
                    null,
                    idSucursalVenta,
                    actor.getCorreo());
        }
        return productoService.buscarPaginado(
                q,
                page,
                idCategoria,
                idColor,
                conOferta,
                soloDisponibles == null ? Boolean.TRUE : soloDisponibles,
                null,
                idSucursalVenta,
                actor.getCorreo());
    }

    public ProductoDetalleResponse obtenerProductoVentaDetalle(Long conversationId, Integer idProducto, Usuario usuarioSesion) {
        Usuario actor = requireCrmUser(usuarioSesion);
        CrmWhatsappConversation conversation = requireConversation(conversationId);
        requireCanReadMessages(conversation, actor);
        Integer idSucursalVenta = resolverIdSucursalFiltroCrm(actor, conversation, null);
        ProductoDetalleResponse detalle = productoService.obtenerDetalle(idProducto, actor.getCorreo());
        List<ProductoVarianteDetalleResponse> variantes = detalle.variantes().stream()
                .map(variante -> {
                    var stocks = (variante.stocksSucursales() == null ? List.<com.sistemapos.sistematextil.util.producto.ProductoVarianteStockDetalleResponse>of() : variante.stocksSucursales()).stream()
                            .filter(stock -> idSucursalVenta.equals(stock.idSucursal()))
                            .toList();
                    int stock = stocks.stream().mapToInt(item -> item.cantidad() == null ? 0 : item.cantidad()).sum();
                    return new ProductoVarianteDetalleResponse(
                            variante.idProductoVariante(), variante.sku(), variante.codigoBarras(),
                            variante.colorId(), variante.colorNombre(), variante.colorHex(),
                            variante.tallaId(), variante.tallaNombre(), variante.precio(), variante.precioMayor(),
                            variante.precioOferta(), variante.ofertaInicio(), variante.ofertaFin(), stock,
                            stocks, variante.estado());
                })
                .toList();
        return new ProductoDetalleResponse(detalle.producto(), variantes, detalle.imagenes());
    }

    @Transactional(readOnly = true)
    public SaleOptionsResponse obtenerOpcionesVenta(Long conversationId, Integer idSucursal, Usuario usuarioSesion) {
        Usuario actor = requireCrmUser(usuarioSesion);
        CrmWhatsappConversation conversation = requireConversation(conversationId);
        requireCanReadMessages(conversation, actor);
        Integer idSucursalVenta = resolverIdSucursalFiltroCrm(actor, conversation, idSucursal);
        List<MetodoPagoVentaRapidaResponse> metodos = sucursalMetodoPagoConfigRepository
                .findActivosBySucursal(idSucursalVenta)
                .stream()
                .filter(config -> "ACTIVO".equalsIgnoreCase(clean(config.getEstado())))
                .filter(config -> config.getMetodoPago() != null)
                .filter(config -> "ACTIVO".equalsIgnoreCase(clean(config.getMetodoPago().getEstado())))
                .map(config -> new MetodoPagoVentaRapidaResponse(
                        config.getMetodoPago().getIdMetodoPago(),
                        config.getMetodoPago().getNombre(),
                        Boolean.TRUE.equals(config.getRequiereCodigoOperacion()),
                        Boolean.TRUE.equals(config.getRequiereFechaPago()),
                        Boolean.TRUE.equals(config.getRequiereHoraPago()),
                        config.getMetodoPago().getCuentas() == null
                                ? List.of()
                                : config.getMetodoPago().getCuentas().stream()
                                        .filter(cuenta -> !Boolean.FALSE.equals(cuenta.getActivo()))
                                        .map(cuenta -> new MetodoPagoCuentaVentaRapidaResponse(
                                                cuenta.getIdMetodoPagoCuenta(),
                                                cuenta.getNumeroCuenta(),
                                                cuenta.getTitular()))
                                        .toList()))
                .toList();
        return new SaleOptionsResponse(idSucursalVenta, metodos);
    }

    @Transactional
    public VentaCrmResponse registrarVentaDesdeCrm(Long conversationId, CrmSaleCreateRequest request, Usuario usuarioSesion) {
        return registrarVentaDesdeCrm(conversationId, request, usuarioSesion, true);
    }

    @Transactional
    public VentaCrmResponse registrarVentaReservadaDesdeCrm(
            Long conversationId, CrmSaleCreateRequest request, Usuario usuarioSesion) {
        return registrarVentaDesdeCrm(conversationId, request, usuarioSesion, false);
    }

    private VentaCrmResponse registrarVentaDesdeCrm(
            Long conversationId, CrmSaleCreateRequest request, Usuario usuarioSesion, boolean moverStock) {
        Usuario actor = requireCrmUser(usuarioSesion);
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Datos de venta requeridos");
        }
        CrmWhatsappConversation conversation = conversationRepository.findForUpdateById(conversationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversacion no encontrada"));
        requireCanOperateAssigned(conversation, actor);

        Integer idSucursalVenta = resolverIdSucursalFiltroCrm(actor, conversation, request.idSucursal());
        Integer paymentMethodId = request.pagos() == null || request.pagos().isEmpty()
                ? null : request.pagos().get(0).idMetodoPago();
        if (moverStock) {
            aiSaleDraftService.validateSale(conversationId, request.aiSaleDraftId(), request.aiSaleDraftVersion(),
                    request.detalles(), paymentMethodId, request.descuentoTotal(), request.tipoDescuento());
        } else {
            aiSaleDraftService.validateReservedSale(conversationId, request.aiSaleDraftId(), request.aiSaleDraftVersion(),
                    request.detalles(), paymentMethodId, request.descuentoTotal(), request.tipoDescuento());
        }
        ComprobanteConfig comprobante = requireComprobanteVenta(request.idComprobante());
        Cliente cliente = resolverClienteParaVentaCrm(conversation, request.telefonoRespaldo(), comprobante, actor);
        sincronizarClienteEnConversacion(conversation, cliente);
        conversationRepository.save(conversation);

        VentaCreateRequest ventaRequest = new VentaCreateRequest(
                idSucursalVenta,
                cliente.getIdCliente(),
                comprobante.getTipoComprobante(),
                comprobante.getSerie(),
                null,
                clean(request.moneda()).isBlank() ? "PEN" : clean(request.moneda()).toUpperCase(),
                clean(request.formaPago()).isBlank() ? "CONTADO" : clean(request.formaPago()).toUpperCase(),
                request.igvPorcentaje() == null ? 18.0 : request.igvPorcentaje(),
                request.descuentoTotal() == null ? 0.0 : request.descuentoTotal(),
                clean(request.tipoDescuento()).isBlank() ? null : clean(request.tipoDescuento()).toUpperCase(),
                request.detalles(),
                request.pagos());
        try {
            VentaResponse venta = moverStock
                    ? ventaService.registrarVentaDesdeCrm(ventaRequest, actor)
                    : ventaService.registrarVentaReservadaDesdeCrm(ventaRequest, actor);
            aiSaleDraftService.markCompleted(request.aiSaleDraftId(), venta.idVenta());
            if (request.aiSaleDraftId() != null) aiMemoryService.clearAfterSale(conversationId);
            registrarMensajeSistema(
                    conversation,
                    "El cliente realizo una compra por " + money(venta.total()) + ".",
                    venta.idVenta());
            return new VentaCrmResponse(venta, obtenerVentaRapidaContexto(conversationId, actor));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "No se pudo registrar la venta" : e.getMessage();
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
    }

    @Transactional
    public MessageResponse enviarMensaje(Long conversationId, SendMessageRequest request, Usuario usuarioSesion) {
        Usuario actor = requireCrmUser(usuarioSesion);
        CrmWhatsappConversation conversation = requireConversation(conversationId);
        requireCanOperateAssigned(conversation, actor);
        String body = request == null ? "" : clean(request.message());
        if (body.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message es requerido");
        }
        if (!isValidChatId(conversation.getPhone())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La conversacion no tiene un chat valido");
        }

        CrmWhatsappMessage replyTo = resolveReplyMessage(conversation, request == null ? null : request.replyToMessageId());
        Object quotedMessage = replyTo == null ? null : quotedMessageFor(replyTo);

        ResponseEntity<String> response;
        try {
            response = bridgeService.enviarTexto(conversation.getPhone(), body, quotedMessage);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No se pudo enviar el mensaje por WhatsApp");
            }
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No se pudo enviar el mensaje por WhatsApp");
        }

        CrmWhatsappMessage message = new CrmWhatsappMessage();
        message.setConversation(conversation);
        message.setDirection("OUTGOING");
        message.setOrigin("HUMAN");
        message.setMessageType("TEXT");
        message.setBody(body);
        message.setMessageStatus("sent");
        message.setWhatsappMessageId(readMessageId(response.getBody()));
        message.setMessageKeyJson(readJsonString(response.getBody(), "messageKey"));
        message.setBaileysMessageJson(readJsonString(response.getBody(), "baileysMessage"));
        message.setReplyToMessageId(replyTo == null ? null : replyTo.getIdMessage());
        message = messageRepository.save(message);

        if (!RESUELTO.equals(conversation.getStatus())) {
            conversation.setStatus(ATENDIDO);
        }
        conversation.setUnreadCount(0);
        conversation.setLastMessage(body);
        conversation.setLastMessageType("TEXT");
        conversation.setLastMessageAt(message.getCreatedAt());
        conversation = conversationRepository.save(conversation);
        MessageResponse messageResponse = toMessageResponse(messageRepository.save(message));
        publishRealtimeEvent("message.created", conversation, messageResponse);
        return messageResponse;
    }

    @Transactional
    public MessageResponse enviarMensajeAutomatico(Long conversationId, String body) {
        CrmWhatsappConversation conversation = conversationRepository.findForUpdateById(conversationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversacion no encontrada"));
        String text = clean(body);
        if (text.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mensaje automatico vacio");
        if (conversation.getAssignedUser() != null || !ESPERA.equals(conversation.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La conversacion ya no admite respuesta automatica");
        }
        ResponseEntity<String> response = bridgeService.enviarTexto(conversation.getPhone(), text, null);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No se pudo enviar la respuesta automatica");
        }
        CrmWhatsappMessage message = new CrmWhatsappMessage();
        message.setConversation(conversation);
        message.setDirection("OUTGOING");
        message.setOrigin("AI_AUTOMATIC");
        message.setMessageType("TEXT");
        message.setBody(text);
        message.setMessageStatus("sent");
        message.setWhatsappMessageId(readMessageId(response.getBody()));
        message.setMessageKeyJson(readJsonString(response.getBody(), "messageKey"));
        message.setBaileysMessageJson(readJsonString(response.getBody(), "baileysMessage"));
        message = messageRepository.save(message);
        conversation.setUnreadCount(0);
        conversation.setLastMessage(text);
        conversation.setLastMessageType("TEXT");
        conversation.setLastMessageAt(message.getCreatedAt());
        conversationRepository.save(conversation);
        MessageResponse result = toMessageResponse(message);
        publishRealtimeEvent("message.created", conversation, result);
        return result;
    }

    @Transactional
    public MessageResponse enviarMediaAutomatico(Long conversationId, byte[] bytes, String fileName,
            String mimeType, String caption) {
        CrmWhatsappConversation conversation = conversationRepository.findForUpdateById(conversationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversacion no encontrada"));
        if (conversation.getAssignedUser() != null || !ESPERA.equals(conversation.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La conversacion ya no admite respuesta automatica");
        }
        return enviarMediaBytes(conversation, bytes, fileName, mimeType, caption, "AI_AUTOMATIC", false);
    }

    @Transactional
    public MessageResponse enviarMediaDesdeStorage(Long conversationId, String sourceReference, String fileName,
            String mimeType, String caption, Long replyToMessageId, Usuario usuarioSesion) {
        Usuario actor = requireCrmUser(usuarioSesion);
        CrmWhatsappConversation conversation = requireConversation(conversationId);
        requireCanOperateAssigned(conversation, actor);
        if (replyToMessageId != null) resolveReplyMessage(conversation, replyToMessageId);
        return enviarMediaBytes(conversation, storageService.readBytes(sourceReference), fileName,
                mimeType, caption, "HUMAN", true);
    }

    private MessageResponse enviarMediaBytes(CrmWhatsappConversation conversation, byte[] bytes, String fileName,
            String mimeType, String caption, String origin, boolean markAttended) {
        if (bytes == null || bytes.length == 0 || bytes.length > 10 * 1024 * 1024) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La guia de tallas debe pesar hasta 10 MB");
        }
        String safeName = safeFileName(fileName);
        String safeMime = detectImageMime(bytes);
        if (safeMime.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El archivo de guia de tallas no es una imagen valida");
        }
        YearMonth month = YearMonth.now();
        String storageKey = "crm/whatsapp/outgoing/%04d/%02d/%s-%s".formatted(
                month.getYear(), month.getMonthValue(), UUID.randomUUID(), safeName);
        String storagePath = storageService.upload(bytes, storageKey, safeMime);
        try {
            ResponseEntity<String> response = bridgeService.enviarMedia(
                    conversation.getPhone(), bytes, safeName, safeMime, clean(caption), null);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No se pudo enviar la guia de tallas");
            }
            CrmWhatsappMessage message = new CrmWhatsappMessage();
            message.setConversation(conversation);
            message.setDirection("OUTGOING");
            message.setOrigin(origin);
            message.setMessageType("IMAGE");
            message.setBody(clean(caption).isBlank() ? safeName : clean(caption));
            message.setMessageStatus("sent");
            message.setWhatsappMessageId(readMessageId(response.getBody()));
            message.setMessageKeyJson(readJsonString(response.getBody(), "messageKey"));
            message.setBaileysMessageJson(readJsonString(response.getBody(), "baileysMessage"));
            message.setMediaMimeType(safeMime);
            message.setMediaFileName(safeName);
            message.setMediaStoragePath(storagePath);
            message = messageRepository.save(message);
            if (markAttended && !RESUELTO.equals(conversation.getStatus())) conversation.setStatus(ATENDIDO);
            conversation.setUnreadCount(0);
            conversation.setLastMessage(mediaPreviewText(message.getBody(), safeName));
            conversation.setLastMessageType("IMAGE");
            conversation.setLastMessageAt(message.getCreatedAt());
            conversationRepository.save(conversation);
            MessageResponse result = toMessageResponse(message);
            publishRealtimeEvent("message.created", conversation, result);
            return result;
        } catch (RuntimeException error) {
            storageService.deleteByKey(storagePath);
            throw error;
        }
    }

    @Transactional
    public MessageResponse enviarComprobanteVenta(Long conversationId, Integer saleId,
            ReceiptRequest request, Usuario usuarioSesion) {
        Usuario actor = requireCrmUser(usuarioSesion);
        CrmWhatsappConversation conversation = conversationRepository.findForUpdateById(conversationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversacion no encontrada"));
        requireCanOperateAssigned(conversation, actor);
        String format = clean(request == null ? null : request.format()).toUpperCase();
        if (!Set.of("PDF", "TICKET").contains(format)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Formato de comprobante invalido");
        }
        CrmWhatsappMessage existing = messageRepository
                .findFirstByConversation_IdConversationAndRelatedSaleIdAndReceiptFormatAndDeletedAtIsNull(
                        conversationId, saleId, format)
                .orElse(null);
        if (existing != null) return toMessageResponse(existing);
        Venta venta = ventaRepository.findByIdVentaAndDeletedAtIsNull(saleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Venta no encontrada"));
        if (conversation.getCliente() == null || venta.getCliente() == null
                || !conversation.getCliente().getIdCliente().equals(venta.getCliente().getIdCliente())
                || venta.getSucursal() == null || venta.getSucursal().getEmpresa() == null
                || !venta.getSucursal().getEmpresa().getIdEmpresa().equals(resolverIdEmpresa(actor))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "La venta no pertenece a esta conversacion");
        }
        byte[] bytes;
        String fileName;
        if ("TICKET".equals(format)) {
            bytes = ventaService.generarTicket80mm(saleId, actor.getCorreo());
            fileName = "ticket-venta-" + saleId + ".pdf";
        } else {
            VentaService.ArchivoDescargable file = ventaService.descargarComprobantePdfPublico(saleId);
            bytes = file.bytes();
            fileName = file.nombreArchivo();
        }
        return enviarDocumentoVenta(conversation, bytes, fileName, saleId, format);
    }

    private MessageResponse enviarDocumentoVenta(CrmWhatsappConversation conversation, byte[] bytes,
            String fileName, Integer saleId, String format) {
        if (bytes == null || bytes.length == 0 || bytes.length > 10 * 1024 * 1024) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El comprobante debe pesar hasta 10 MB");
        }
        String safeName = safeFileName(fileName);
        String mimeType = "application/pdf";
        YearMonth month = YearMonth.now();
        String storageKey = "crm/whatsapp/outgoing/%04d/%02d/%s-%s".formatted(
                month.getYear(), month.getMonthValue(), UUID.randomUUID(), safeName);
        String storagePath = storageService.upload(bytes, storageKey, mimeType);
        try {
            ResponseEntity<String> response = bridgeService.enviarMedia(
                    conversation.getPhone(), bytes, safeName, mimeType, "", null);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No se pudo enviar el comprobante");
            }
            CrmWhatsappMessage message = new CrmWhatsappMessage();
            message.setConversation(conversation);
            message.setDirection("OUTGOING");
            message.setOrigin("HUMAN");
            message.setMessageType("DOCUMENT");
            message.setBody(safeName);
            message.setMessageStatus("sent");
            message.setWhatsappMessageId(readMessageId(response.getBody()));
            message.setMessageKeyJson(readJsonString(response.getBody(), "messageKey"));
            message.setBaileysMessageJson(readJsonString(response.getBody(), "baileysMessage"));
            message.setMediaMimeType(mimeType);
            message.setMediaFileName(safeName);
            message.setMediaStoragePath(storagePath);
            message.setRelatedSaleId(saleId);
            message.setReceiptFormat(format);
            message = messageRepository.save(message);
            conversation.setUnreadCount(0);
            conversation.setLastMessage(mediaPreviewText(safeName, safeName));
            conversation.setLastMessageType("DOCUMENT");
            conversation.setLastMessageAt(message.getCreatedAt());
            conversationRepository.save(conversation);
            MessageResponse result = toMessageResponse(message);
            publishRealtimeEvent("message.created", conversation, result);
            return result;
        } catch (RuntimeException error) {
            storageService.deleteByKey(storagePath);
            throw error;
        }
    }

    @Transactional
    public MessageResponse enviarMedia(Long conversationId, MultipartFile file, String caption, Long replyToMessageId, Usuario usuarioSesion) {
        Usuario actor = requireCrmUser(usuarioSesion);
        CrmWhatsappConversation conversation = requireConversation(conversationId);
        requireCanOperateAssigned(conversation, actor);
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file es requerido");
        }
        if (!isValidChatId(conversation.getPhone())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La conversacion no tiene un chat valido");
        }

        String fileName = safeFileName(file.getOriginalFilename());
        String mimeType = clean(file.getContentType());
        if (mimeType.isBlank()) {
            mimeType = "application/octet-stream";
        }
        CrmWhatsappMessage replyTo = resolveReplyMessage(conversation, replyToMessageId);
        String quotedMessageJson = replyTo == null ? null : quotedMessageJsonFor(replyTo);

        YearMonth month = YearMonth.now();
        String storageKey = "crm/whatsapp/outgoing/%04d/%02d/%s-%s".formatted(
                month.getYear(),
                month.getMonthValue(),
                UUID.randomUUID(),
                fileName);

        String storagePath = null;
        try {
            storagePath = storageService.upload(file.getBytes(), storageKey, mimeType);
            ResponseEntity<String> response = bridgeService.enviarMedia(conversation.getPhone(), file, clean(caption), quotedMessageJson);
            if (!response.getStatusCode().is2xxSuccessful()) {
                storageService.deleteByKey(storagePath);
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No se pudo enviar el archivo por WhatsApp");
            }

            CrmWhatsappMessage message = new CrmWhatsappMessage();
            message.setConversation(conversation);
            message.setDirection("OUTGOING");
            message.setOrigin("HUMAN");
            message.setMessageType(messageTypeFor(mimeType, fileName));
            message.setBody(clean(caption).isBlank() ? fileName : clean(caption));
            message.setMessageStatus("sent");
            message.setWhatsappMessageId(readMessageId(response.getBody()));
            message.setMessageKeyJson(readJsonString(response.getBody(), "messageKey"));
            message.setBaileysMessageJson(readJsonString(response.getBody(), "baileysMessage"));
            message.setReplyToMessageId(replyTo == null ? null : replyTo.getIdMessage());
            message.setMediaMimeType(mimeType);
            message.setMediaFileName(fileName);
            message.setMediaStoragePath(storagePath);
            message = messageRepository.save(message);

            if (!RESUELTO.equals(conversation.getStatus())) {
                conversation.setStatus(ATENDIDO);
            }
            conversation.setUnreadCount(0);
            conversation.setLastMessage(mediaPreviewText(message.getBody(), fileName));
            conversation.setLastMessageType(message.getMessageType());
            conversation.setLastMessageAt(message.getCreatedAt());
            conversation = conversationRepository.save(conversation);
            MessageResponse messageResponse = toMessageResponse(message);
            publishRealtimeEvent("message.created", conversation, messageResponse);
            return messageResponse;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            if (storagePath != null) {
                storageService.deleteByKey(storagePath);
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No se pudo enviar el archivo por WhatsApp");
        }
    }

    private String detectImageMime(byte[] bytes) {
        if (bytes == null) return "";
        if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8) {
            return "image/jpeg";
        }
        if (bytes.length >= 8 && (bytes[0] & 0xff) == 0x89 && bytes[1] == 0x50
                && bytes[2] == 0x4e && bytes[3] == 0x47) {
            return "image/png";
        }
        if (bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F'
                && bytes[3] == 'F' && bytes[8] == 'W' && bytes[9] == 'E'
                && bytes[10] == 'B' && bytes[11] == 'P') {
            return "image/webp";
        }
        return "";
    }

    @Transactional
    public MessageResponse eliminarMensaje(Long conversationId, Long messageId, Usuario usuarioSesion) {
        Usuario actor = requireCrmUser(usuarioSesion);
        CrmWhatsappConversation conversation = requireConversation(conversationId);
        requireCanOperateAssigned(conversation, actor);
        CrmWhatsappMessage message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mensaje no encontrado"));
        if (!message.getConversation().getIdConversation().equals(conversation.getIdConversation())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Mensaje no encontrado");
        }
        if (!"OUTGOING".equals(message.getDirection())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solo puedes eliminar mensajes enviados");
        }
        if (message.getDeletedAt() != null) {
            return toMessageResponse(message);
        }
        String messageKeyJson = messageKeyJsonFor(message);
        if (messageKeyJson == null || messageKeyJson.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se puede eliminar este mensaje");
        }
        ResponseEntity<String> response = bridgeService.eliminarMensaje(messageKeyJson);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "WhatsApp no permitio eliminar el mensaje");
        }
        markDeleted(message);
        MessageResponse messageResponse = toMessageResponse(messageRepository.save(message));
        publishRealtimeEvent("message.deleted", conversation, messageResponse);
        return messageResponse;
    }

    @Transactional
    public ConversationResponse resolver(Long conversationId, Usuario usuarioSesion) {
        Usuario actor = requireCrmUser(usuarioSesion);
        CrmWhatsappConversation conversation = requireConversation(conversationId);
        requireCanOperateAssigned(conversation, actor);
        conversation.setStatus(RESUELTO);
        conversation.setUnreadCount(0);
        conversation = conversationRepository.save(conversation);
        aiMemoryService.pauseResolved(conversation);
        publishRealtimeEvent("conversation.updated", conversation, null);
        return toConversationResponse(conversation);
    }

    @Transactional
    public ConversationResponse reabrir(Long conversationId, Usuario usuarioSesion) {
        Usuario actor = requireCrmUser(usuarioSesion);
        CrmWhatsappConversation conversation = requireConversation(conversationId);
        requireCanReopen(conversation, actor);
        conversation.setStatus(conversation.getAssignedUser() == null ? ESPERA : ATENDIDO);
        conversation = conversationRepository.save(conversation);
        publishRealtimeEvent("conversation.updated", conversation, null);
        return toConversationResponse(conversation);
    }

    @Transactional
    public void recibirWebhook(WebhookRequest request) {
        if (request == null) {
            return;
        }
        if ("message.status".equals(request.event())) {
            actualizarEstadoMensaje(request.messageId(), request.status());
            return;
        }
        if ("message.deleted".equals(request.event())) {
            marcarMensajeEliminado(request.messageId());
            return;
        }
        boolean outgoing = "message.sent".equals(request.event());
        if (!outgoing && !"message.received".equals(request.event())) {
            return;
        }

        String from = normalizeChatId(request.from());
        if (!isValidChatId(from)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from debe ser un chat valido");
        }

        if (request.messageId() != null
                && messageRepository.findFirstByWhatsappMessageId(request.messageId()).isPresent()) {
            return;
        }

        // The bridge can deliver consecutive events for a new chat on different
        // request threads. Create the row atomically, then serialize updates for
        // this phone so both the unread counter and message id remain idempotent.
        conversationRepository.insertIfAbsent(from);
        CrmWhatsappConversation conversation = conversationRepository.findForUpdateByPhone(from)
                .orElseThrow(() -> new IllegalStateException("No se pudo crear la conversacion de WhatsApp"));

        if (request.messageId() != null
                && messageRepository.findFirstByWhatsappMessageId(request.messageId()).isPresent()) {
            return;
        }

        var whatsappConnection = connectionService.buscarPorClientId(request.clientId());
        if (whatsappConnection != null) {
            conversation.setConnection(whatsappConnection);
        }

        String fromName = clean(request.fromName());
        if (conversation.getCliente() != null) {
            conversation.setContactName(conversation.getCliente().getNombres());
            String clientPhone = normalizeCrmPhoneNumber(conversation.getCliente().getTelefono());
            if (!clientPhone.isBlank()) conversation.setPhoneNumber(clientPhone);
        } else if (!outgoing && !fromName.isBlank() && !fromName.equals(from)) {
            conversation.setContactName(fromName);
        }
        String phoneNumber = normalizeCrmPhoneNumber(request.phoneNumber());
        if (conversation.getCliente() == null && !phoneNumber.isBlank()) {
            conversation.setPhoneNumber(phoneNumber);
        }
        String username = clean(request.username());
        if (!username.isBlank()) {
            conversation.setWhatsappUsername(username);
        }
        boolean reopenedForAi = !outgoing && RESUELTO.equals(conversation.getStatus());
        boolean hasCommercialBlock = reopenedForAi
                && (conversation.getWaitingReason() == CrmWhatsappWaitingReason.PAYMENT_VERIFICATION
                    || aiSaleDraftService.blocksAutomation(conversation.getIdConversation()));
        if (reopenedForAi) {
            conversation.setAssignedUser(null);
            conversation.setAssignedAt(null);
            conversation.setStatus(ESPERA);
            conversation.setAiAttentionMode(CrmWhatsappAiAttentionMode.AUTOMATICA);
            conversation.setAiAttentionModeExplicit(true);
            if (!hasCommercialBlock) {
                conversation.setWaitingReason(aiJobService.isAutomaticModeDisabled(conversation)
                        ? CrmWhatsappWaitingReason.AI_DISABLED
                        : null);
            }
        }
        if (outgoing) {
            conversation.setUnreadCount(0);
        } else {
            if (conversation.getStatus() == null || conversation.getStatus().isBlank() || ESPERA.equals(conversation.getStatus())) {
                conversation.setStatus(ESPERA);
            }
            conversation.setUnreadCount((conversation.getUnreadCount() == null ? 0 : conversation.getUnreadCount()) + 1);
            if (conversation.getAssignedUser() == null
                    && conversation.getAiAttentionMode() == CrmWhatsappAiAttentionMode.AUTOMATICA) {
                if (aiJobService.isAutomaticModeDisabled(conversation)) {
                    conversation.setWaitingReason(CrmWhatsappWaitingReason.AI_DISABLED);
                } else if (conversation.getWaitingReason() == CrmWhatsappWaitingReason.AI_DISABLED) {
                    conversation.setWaitingReason(null);
                }
            }
        }
        String mediaFileName = request.media() == null ? "" : clean(readString(request.media(), "fileName"));
        String mediaMimeType = request.media() == null ? "" : clean(readString(request.media(), "mimeType"));
        String messageType = request.hasMedia() ? messageTypeFor(mediaMimeType, mediaFileName) : "TEXT";
        conversation.setLastMessage(mediaPreviewText(clean(request.body()), mediaFileName));
        conversation.setLastMessageType(messageType);
        conversation.setLastMessageAt(parseDate(request.timestamp()));
        conversation = conversationRepository.save(conversation);

        CrmWhatsappMessage message = new CrmWhatsappMessage();
        message.setConversation(conversation);
        message.setDirection(outgoing ? "OUTGOING" : "INCOMING");
        message.setOrigin("EXTERNAL");
        message.setMessageType(messageType);
        message.setBody(clean(request.body()));
        message.setWhatsappMessageId(request.messageId());
        message.setMessageStatus(outgoing ? "sent" : "received");
        message.setCreatedAt(conversation.getLastMessageAt());
        message.setMessageKeyJson(writeJson(request.messageKey()));
        message.setBaileysMessageJson(writeJson(request.baileysMessage()));
        message.setReplyToMessageId(findReplyToMessageId(request.quotedMessageId(), conversation.getIdConversation()));

        if (request.media() != null) {
            message.setMediaMimeType(mediaMimeType.isBlank() ? null : mediaMimeType);
            message.setMediaFileName(mediaFileName.isBlank() ? null : mediaFileName);
            message.setMediaStoragePath(readString(request.media(), "storagePath"));
            message.setMediaDurationSeconds(readPositiveInteger(request.media(), "durationSeconds"));
        }

        message = messageRepository.save(message);
        if (!outgoing) {
            if (reopenedForAi && !hasCommercialBlock
                    && !aiJobService.isAutomaticModeDisabled(conversation)) {
                aiMemoryService.resumeAutomatic(conversation);
            }
            aiMemoryService.registerIncoming(conversation, message);
            aiJobService.enqueueAutomatic(message);
            if (message.getMediaStoragePath() != null && !message.getMediaStoragePath().isBlank()) {
                applicationEventPublisher.publishEvent(new CrmWhatsappIncomingMediaEvent(message.getIdMessage()));
            }
        }
        publishRealtimeEvent("message.created", conversation, toMessageResponse(message));
        if (reopenedForAi) publishRealtimeEvent("conversation.updated", conversation, null);
    }

    public MediaDownload descargarMedia(Long messageId, Usuario usuarioSesion) {
        Usuario actor = requireCrmUser(usuarioSesion);
        CrmWhatsappMessage message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mensaje no encontrado"));
        requireCanReadMessages(message.getConversation(), actor);
        if (message.getDeletedAt() != null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "El mensaje fue eliminado");
        }
        if (message.getMediaStoragePath() == null || message.getMediaStoragePath().isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "El mensaje no tiene archivo");
        }

        try {
            return new MediaDownload(
                    storageService.readBytes(message.getMediaStoragePath()),
                    clean(message.getMediaMimeType()).isBlank() ? "application/octet-stream" : message.getMediaMimeType(),
                    safeFileName(message.getMediaFileName()));
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No se pudo leer el archivo");
        }
    }

    private void actualizarEstadoMensaje(String messageId, String status) {
        String normalizedStatus = clean(status);
        if (messageId == null || messageId.isBlank() || normalizedStatus.isBlank()) {
            return;
        }
        messageRepository.findFirstByWhatsappMessageId(messageId)
                .filter(message -> "OUTGOING".equals(message.getDirection()))
                .filter(message -> message.getDeletedAt() == null)
                .ifPresent(message -> {
                    if (statusRank(normalizedStatus) >= statusRank(message.getMessageStatus())) {
                        message.setMessageStatus(normalizedStatus);
                        messageRepository.save(message);
                        publishRealtimeEvent("message.updated", message.getConversation(), toMessageResponse(message));
                    }
                });
    }

    private void marcarMensajeEliminado(String messageId) {
        if (messageId == null || messageId.isBlank()) {
            return;
        }
        messageRepository.findFirstByWhatsappMessageId(messageId)
                .ifPresent(message -> {
                    markDeleted(message);
                    messageRepository.save(message);
                    publishRealtimeEvent("message.deleted", message.getConversation(), toMessageResponse(message));
                });
    }

    private List<Cliente> buscarClientesPorTelefonoExacto(CrmWhatsappConversation conversation, Usuario actor) {
        String phone = resolveConversationCrmPhone(conversation);
        if (phone.isBlank()) {
            return List.of();
        }
        List<String> variants = phoneVariants(phone);
        if (variants.isEmpty()) {
            return List.of();
        }
        return clienteRepository.findByEmpresa_IdEmpresaAndTelefonoInAndDeletedAtIsNullOrderByIdClienteAsc(
                resolverIdEmpresa(actor),
                variants);
    }

    private ComprobanteConfig requireComprobanteVenta(Integer idComprobante) {
        if (idComprobante == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Seleccione comprobante");
        }
        ComprobanteConfig comprobante = comprobanteConfigRepository.findById(idComprobante)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comprobante no encontrado"));
        if (!Boolean.TRUE.equals(comprobante.getHabilitadoVenta())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Comprobante no habilitado para venta");
        }
        if (clean(comprobante.getTipoComprobante()).isBlank() || clean(comprobante.getSerie()).isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Comprobante incompleto");
        }
        return comprobante;
    }

    private Cliente resolverClienteParaVentaCrm(
            CrmWhatsappConversation conversation,
            String telefonoRespaldo,
            ComprobanteConfig comprobante,
            Usuario actor) {
        Cliente cliente = conversation.getCliente();
        if (cliente != null) {
            validarClienteFacturaCrm(cliente, comprobante);
            return cliente;
        }

        List<Cliente> coincidencias = buscarClientesPorTelefonoExacto(conversation, actor);
        if (coincidencias.size() == 1) {
            validarClienteFacturaCrm(coincidencias.get(0), comprobante);
            return coincidencias.get(0);
        }

        String telefono = normalizeCrmPhoneNumber(telefonoRespaldo);
        if (telefono.isBlank()) {
            telefono = resolveConversationCrmPhone(conversation);
        }
        if (telefono.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Registra un celular peruano valido de 9 digitos");
        }

        Integer idEmpresa = resolverIdEmpresa(actor);
        Cliente existente = clienteRepository
                .findByEmpresa_IdEmpresaAndTelefonoInAndDeletedAtIsNullOrderByIdClienteAsc(idEmpresa, phoneVariants(telefono))
                .stream()
                .findFirst()
                .orElse(null);
        if (existente != null) {
            validarClienteFacturaCrm(existente, comprobante);
            return existente;
        }

        if ("FACTURA".equalsIgnoreCase(clean(comprobante.getTipoComprobante()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La factura requiere un cliente con RUC valido");
        }

        Cliente nuevo = new Cliente();
        nuevo.setEmpresa(sucursalRepository.findByDeletedAtIsNullAndEstadoOrderByIdSucursalAsc("ACTIVO")
                .stream()
                .filter(sucursal -> sucursal.getEmpresa() != null && sucursal.getEmpresa().getIdEmpresa().equals(idEmpresa))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "No hay empresa registrada"))
                .getEmpresa());
        nuevo.setUsuarioCreacion(actor);
        nuevo.setTipoDocumento(TipoDocumento.SIN_DOC);
        nuevo.setNroDocumento(null);
        nuevo.setNombres("CLIENTE " + telefono);
        nuevo.setTelefono(telefono);
        nuevo.setCorreo(null);
        nuevo.setDireccion(null);
        nuevo.setEstado("ACTIVO");
        nuevo.setDeletedAt(null);

        try {
            return clienteRepository.saveAndFlush(nuevo);
        } catch (DataIntegrityViolationException e) {
            return clienteRepository
                    .findByEmpresa_IdEmpresaAndTelefonoInAndDeletedAtIsNullOrderByIdClienteAsc(idEmpresa, phoneVariants(telefono))
                    .stream()
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "El telefono ya fue registrado"));
        }
    }

    private void validarClienteFacturaCrm(Cliente cliente, ComprobanteConfig comprobante) {
        if (!"FACTURA".equalsIgnoreCase(clean(comprobante.getTipoComprobante()))) {
            return;
        }
        String documento = clean(cliente.getNroDocumento());
        if (cliente.getTipoDocumento() != TipoDocumento.RUC || !documento.matches("\\d{11}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La factura requiere un cliente con RUC valido");
        }
    }

    private List<String> phoneVariants(String phone) {
        return CrmWhatsappPhoneUtils.variants(phone);
    }

    private Integer resolverIdEmpresa(Usuario actor) {
        if (actor.getSucursal() != null && actor.getSucursal().getEmpresa() != null) {
            return actor.getSucursal().getEmpresa().getIdEmpresa();
        }
        return sucursalRepository.findByDeletedAtIsNullAndEstadoOrderByIdSucursalAsc("ACTIVO")
                .stream()
                .findFirst()
                .filter(sucursal -> sucursal.getEmpresa() != null)
                .map(sucursal -> sucursal.getEmpresa().getIdEmpresa())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "No hay empresa registrada"));
    }

    private Cliente requireClienteEmpresa(Integer idCliente, Usuario actor) {
        return clienteRepository.findByIdClienteAndDeletedAtIsNullAndEmpresa_IdEmpresa(idCliente, resolverIdEmpresa(actor))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cliente no encontrado"));
    }

    private Cliente crearClienteCrm(ClienteCreateRequest request, Usuario actor) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Datos de cliente requeridos");
        }
        TipoDocumento tipoDocumento = request.tipoDocumento() == null ? TipoDocumento.SIN_DOC : request.tipoDocumento();
        String nroDocumento = normalizarDocumento(tipoDocumento, request.nroDocumento());
        String nombres = clean(request.nombres());
        String telefono = normalizeCrmPhoneNumber(request.telefono());
        if (telefono.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El telefono debe ser un celular peruano valido de 9 digitos");
        }
        if (nombres.length() < 2) {
            nombres = "CLIENTE " + telefono;
        }
        Integer idEmpresa = resolverIdEmpresa(actor);
        Cliente duplicadoTelefono = clienteRepository
                .findByEmpresa_IdEmpresaAndTelefonoInAndDeletedAtIsNullOrderByIdClienteAsc(idEmpresa, phoneVariants(telefono))
                .stream()
                .findFirst()
                .orElse(null);
        if (duplicadoTelefono != null) {
            return duplicadoTelefono;
        }

        Empresa empresa = sucursalRepository.findByDeletedAtIsNullAndEstadoOrderByIdSucursalAsc("ACTIVO")
                .stream()
                .filter(sucursal -> sucursal.getEmpresa() != null && sucursal.getEmpresa().getIdEmpresa().equals(idEmpresa))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "No hay empresa registrada"))
                .getEmpresa();
        try {
            return aiClientWriter.create(
                    empresa,
                    actor,
                    tipoDocumento,
                    nroDocumento,
                    nombres,
                    telefono,
                    clean(request.correo()).isBlank() ? null : clean(request.correo()),
                    clean(request.direccion()).isBlank() ? null : clean(request.direccion()));
        } catch (DataIntegrityViolationException duplicate) {
            return aiClientWriter.findMatches(idEmpresa, phoneVariants(telefono)).stream()
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "No se pudo resolver el cliente registrado con ese celular"));
        }
    }

    private String normalizarDocumento(TipoDocumento tipoDocumento, String value) {
        if (tipoDocumento == TipoDocumento.SIN_DOC) {
            return null;
        }
        String document = clean(value).toUpperCase();
        if (document.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ingrese numero de documento");
        }
        boolean valid = switch (tipoDocumento) {
            case DNI -> document.matches("\\d{8}");
            case RUC -> document.matches("\\d{11}");
            case CE -> document.matches("[A-Z0-9]{6,20}");
            case SIN_DOC -> true;
        };
        if (!valid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Documento invalido");
        }
        return document;
    }

    private List<SucursalVentaRapidaResponse> listarSucursalesVentaRapida(
            CrmWhatsappConversation conversation,
            Usuario actor) {
        Sucursal sucursal = connectionService.requireSucursal(conversation, resolverIdEmpresa(actor));
        resolverIdSucursalPermitida(actor, sucursal.getIdSucursal());
        return List.of(new SucursalVentaRapidaResponse(
                sucursal.getIdSucursal(),
                sucursal.getNombre(),
                sucursal.getTipo() != null ? sucursal.getTipo().name() : null));
    }

    private Integer resolverIdSucursalFiltroCrm(
            Usuario actor,
            CrmWhatsappConversation conversation,
            Integer idSucursalSolicitada) {
        Sucursal sucursal = connectionService.requireSucursal(conversation, resolverIdEmpresa(actor));
        Integer idSucursalConfigurada = sucursal.getIdSucursal();
        if (idSucursalSolicitada != null && !idSucursalConfigurada.equals(idSucursalSolicitada)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La venta de WhatsApp solo puede usar la sucursal " + sucursal.getNombre());
        }
        return resolverIdSucursalPermitida(actor, idSucursalConfigurada);
    }

    private Integer resolverIdSucursalPermitida(Usuario actor, Integer idSucursal) {
        try {
            return usuarioSucursalAccessService.resolverIdSucursalFiltro(actor, idSucursal, "No tiene permisos para esta sucursal");
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        }
    }

    private List<ComprobanteVentaRapidaResponse> listarComprobantesVentaRapida() {
        return comprobanteConfigRepository.buscar("ACTIVO", true).stream()
                .map(this::toComprobanteVentaRapida)
                .toList();
    }

    private ClienteResumenResponse toClienteResumen(Cliente cliente) {
        if (cliente == null) {
            return null;
        }
        return new ClienteResumenResponse(
                cliente.getIdCliente(),
                cliente.getTipoDocumento() != null ? cliente.getTipoDocumento().name() : null,
                cliente.getNroDocumento(),
                cliente.getNombres(),
                nullIfBlank(normalizeCrmPhoneNumber(cliente.getTelefono())),
                cliente.getCorreo(),
                cliente.getDireccion());
    }

    private void sincronizarClienteEnConversacion(CrmWhatsappConversation conversation, Cliente cliente) {
        if (conversation == null || cliente == null) {
            return;
        }
        conversation.setCliente(cliente);
        String nombre = clean(cliente.getNombres());
        if (!nombre.isBlank()) {
            conversation.setContactName(nombre);
        }
        String telefono = normalizeCrmPhoneNumber(cliente.getTelefono());
        if (!telefono.isBlank()) {
            conversation.setPhoneNumber(telefono);
        }
    }

    private String resolveConversationCrmPhone(CrmWhatsappConversation conversation) {
        if (conversation == null) {
            return null;
        }
        String phone = normalizeCrmPhoneNumber(conversation.getPhoneNumber());
        if (!phone.isBlank()) {
            return phone;
        }
        return normalizeCrmPhoneNumber(conversation.getPhone());
    }

    private ComprobanteVentaRapidaResponse toComprobanteVentaRapida(ComprobanteConfig comprobante) {
        return new ComprobanteVentaRapidaResponse(
                comprobante.getIdComprobante(),
                comprobante.getTipoComprobante(),
                comprobante.getSerie(),
                (comprobante.getUltimoCorrelativo() == null ? 0 : comprobante.getUltimoCorrelativo()) + 1,
                comprobante.getHabilitadoVenta());
    }

    private VentaRapidaHistorialResponse toVentaRapidaHistorial(Venta venta, List<VentaDetalle> detalles, List<Pago> pagos) {
        return new VentaRapidaHistorialResponse(
                venta.getIdVenta(),
                venta.getFecha(),
                venta.getTipoComprobante(),
                venta.getSerie(),
                venta.getCorrelativo(),
                venta.getMoneda(),
                venta.getTotal() == null ? BigDecimal.ZERO : venta.getTotal(),
                venta.getEstado(),
                venta.getSucursal() == null ? null : venta.getSucursal().getIdSucursal(),
                venta.getSucursal() == null ? null : venta.getSucursal().getNombre(),
                detalles.stream().map(this::toVentaRapidaDetalle).toList(),
                pagos.stream().map(this::toVentaRapidaPago).toList());
    }

    private VentaRapidaDetalleResponse toVentaRapidaDetalle(VentaDetalle detalle) {
        ProductoVariante variante = detalle.getProductoVariante();
        return new VentaRapidaDetalleResponse(
                variante == null ? null : variante.getIdProductoVariante(),
                variante == null || variante.getProducto() == null ? detalle.getDescripcion() : variante.getProducto().getNombre(),
                variante == null ? null : variante.getSku(),
                variante == null || variante.getColor() == null ? null : variante.getColor().getNombre(),
                variante == null || variante.getTalla() == null ? null : variante.getTalla().getNombre(),
                detalle.getCantidad() == null ? 0 : detalle.getCantidad(),
                detalle.getTotalDetalle() == null ? BigDecimal.ZERO : detalle.getTotalDetalle());
    }

    private VentaRapidaPagoResponse toVentaRapidaPago(Pago pago) {
        return new VentaRapidaPagoResponse(
                pago.getMetodoPago() == null ? null : pago.getMetodoPago().getIdMetodoPago(),
                pago.getMetodoPago() == null ? "Metodo de pago" : pago.getMetodoPago().getNombre(),
                pago.getMonto() == null ? BigDecimal.ZERO : pago.getMonto());
    }

    private void registrarMensajeSistema(CrmWhatsappConversation conversation, String body) {
        registrarMensajeSistema(conversation, body, null);
    }

    private void registrarMensajeSistema(CrmWhatsappConversation conversation, String body, Integer relatedSaleId) {
        String text = clean(body);
        if (conversation == null || text.isBlank()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        CrmWhatsappMessage message = new CrmWhatsappMessage();
        message.setConversation(conversation);
        message.setDirection("SYSTEM");
        message.setOrigin("CRM_SYSTEM");
        message.setMessageType("TEXT");
        message.setBody(text);
        message.setRelatedSaleId(relatedSaleId);
        message.setMessageStatus("sent");
        message = messageRepository.save(message);

        conversation.setLastMessage(text);
        conversation.setLastMessageType("TEXT");
        conversation.setLastMessageAt(now);
        conversation = conversationRepository.save(conversation);
        publishRealtimeEvent("message.created", conversation, toMessageResponse(message));
    }

    private String money(BigDecimal value) {
        BigDecimal safe = value == null ? BigDecimal.ZERO : value;
        return "S/" + safe.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private List<VarianteCompradaChartResponse> topVariantesCompradas(List<VentaDetalle> detalles) {
        Map<Integer, VarianteCompradaMutable> acumulado = new LinkedHashMap<>();
        for (VentaDetalle detalle : detalles) {
            ProductoVariante variante = detalle.getProductoVariante();
            Integer idVariante = variante == null ? null : variante.getIdProductoVariante();
            if (idVariante == null) {
                continue;
            }
            VarianteCompradaMutable item = acumulado.computeIfAbsent(idVariante, key -> new VarianteCompradaMutable(
                    key,
                    variante.getProducto() == null ? clean(detalle.getDescripcion()) : clean(variante.getProducto().getNombre()),
                    clean(variante.getSku()),
                    variante.getColor() == null ? null : clean(variante.getColor().getNombre()),
                    variante.getTalla() == null ? null : clean(variante.getTalla().getNombre())));
            item.cantidad += detalle.getCantidad() == null ? 0 : detalle.getCantidad();
            item.monto = item.monto.add(detalle.getTotalDetalle() == null ? BigDecimal.ZERO : detalle.getTotalDetalle());
        }
        return acumulado.values().stream()
                .sorted((left, right) -> {
                    int byQuantity = Integer.compare(right.cantidad, left.cantidad);
                    return byQuantity != 0 ? byQuantity : right.monto.compareTo(left.monto);
                })
                .limit(5)
                .map(item -> new VarianteCompradaChartResponse(
                        item.idProductoVariante,
                        item.nombre,
                        item.sku,
                        item.color,
                        item.talla,
                        item.cantidad,
                        item.monto))
                .toList();
    }

    private List<MetodoPagoChartResponse> metodosPagoUsados(List<Pago> pagos) {
        Map<String, MetodoPagoMutable> acumulado = new LinkedHashMap<>();
        for (Pago pago : pagos) {
            Integer idMetodoPago = pago.getMetodoPago() == null ? null : pago.getMetodoPago().getIdMetodoPago();
            String key = idMetodoPago == null ? clean(pago.getMetodoPago() == null ? null : pago.getMetodoPago().getNombre()) : String.valueOf(idMetodoPago);
            if (key == null || key.isBlank()) {
                key = "SIN_METODO";
            }
            MetodoPagoMutable item = acumulado.computeIfAbsent(key, ignored -> new MetodoPagoMutable(
                    idMetodoPago,
                    pago.getMetodoPago() == null ? "Metodo de pago" : clean(pago.getMetodoPago().getNombre())));
            item.cantidad += 1;
            item.monto = item.monto.add(pago.getMonto() == null ? BigDecimal.ZERO : pago.getMonto());
        }
        return acumulado.values().stream()
                .sorted((left, right) -> {
                    int byAmount = right.monto.compareTo(left.monto);
                    return byAmount != 0 ? byAmount : Integer.compare(right.cantidad, left.cantidad);
                })
                .limit(5)
                .map(item -> new MetodoPagoChartResponse(item.idMetodoPago, item.nombre, item.cantidad, item.monto))
                .toList();
    }

    private static class VarianteCompradaMutable {
        private final Integer idProductoVariante;
        private final String nombre;
        private final String sku;
        private final String color;
        private final String talla;
        private int cantidad;
        private BigDecimal monto = BigDecimal.ZERO;

        private VarianteCompradaMutable(Integer idProductoVariante, String nombre, String sku, String color, String talla) {
            this.idProductoVariante = idProductoVariante;
            this.nombre = nombre;
            this.sku = sku;
            this.color = color;
            this.talla = talla;
        }
    }

    private static class MetodoPagoMutable {
        private final Integer idMetodoPago;
        private final String nombre;
        private int cantidad;
        private BigDecimal monto = BigDecimal.ZERO;

        private MetodoPagoMutable(Integer idMetodoPago, String nombre) {
            this.idMetodoPago = idMetodoPago;
            this.nombre = nombre;
        }
    }

    private static class CrmReportUserRankingMutable {
        private final Integer idUsuario;
        private final String nombre;
        private long atendidos;
        private long resueltos;

        private CrmReportUserRankingMutable(Integer idUsuario, String nombre) {
            this.idUsuario = idUsuario;
            this.nombre = nombre;
        }
    }

    private record CrmReportRange(String filtroAplicado, LocalDate desde, LocalDate hasta) {
        private LocalDateTime hastaExclusive() {
            return hasta.plusDays(1).atStartOfDay();
        }

        private boolean hourly() {
            return desde.equals(hasta);
        }
    }

    private CrmReportRange resolveCrmReportRange(String filtro, LocalDate desdeRequest, LocalDate hastaRequest) {
        if (desdeRequest != null || hastaRequest != null) {
            if (desdeRequest == null || hastaRequest == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debe enviar fecha desde y hasta");
            }
            if (desdeRequest.isAfter(hastaRequest)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La fecha desde no puede ser mayor a la fecha hasta");
            }
            return new CrmReportRange("RANGO_FECHAS", desdeRequest, hastaRequest);
        }

        String normalized = clean(filtro).isBlank() ? "HOY" : clean(filtro).toUpperCase().replace(" ", "_");
        LocalDate today = LocalDate.now();
        return switch (normalized) {
            case "HOY" -> new CrmReportRange("HOY", today, today);
            case "ULT_7_DIAS", "ULT7DIAS", "7_DIAS" -> new CrmReportRange("ULT_7_DIAS", today.minusDays(6), today);
            case "ULT_14_DIAS", "ULT14DIAS", "14_DIAS" -> new CrmReportRange("ULT_14_DIAS", today.minusDays(13), today);
            case "ULT_30_DIAS", "ULT30DIAS", "30_DIAS" -> new CrmReportRange("ULT_30_DIAS", today.minusDays(29), today);
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Filtro invalido");
        };
    }

    private List<CrmReportSeriesPoint> buildCrmReportSeries(CrmReportRange range, List<Object[]> rows) {
        Map<String, Long> values = new LinkedHashMap<>();
        if (range.hourly()) {
            for (int hour = 0; hour < 24; hour++) {
                LocalDateTime dateTime = range.desde().atTime(hour, 0);
                values.put(dateTime.format(CRM_REPORT_HOUR_FORMAT), 0L);
            }
            for (Object[] row : rows) {
                Integer hour = crmToInteger(row[0]);
                if (hour != null && hour >= 0 && hour <= 23) {
                    values.put(String.format("%02d:00", hour), crmToLong(row[1]));
                }
            }
            return values.entrySet().stream()
                    .map(entry -> new CrmReportSeriesPoint(range.desde().toString(), entry.getKey(), "HORA", entry.getValue()))
                    .toList();
        }

        LocalDate cursor = range.desde();
        while (!cursor.isAfter(range.hasta())) {
            values.put(cursor.toString(), 0L);
            cursor = cursor.plusDays(1);
        }
        for (Object[] row : rows) {
            LocalDate date = crmToLocalDate(row[0]);
            if (date != null && values.containsKey(date.toString())) {
                values.put(date.toString(), crmToLong(row[1]));
            }
        }
        return values.entrySet().stream()
                .map(entry -> new CrmReportSeriesPoint(entry.getKey(), entry.getKey(), "DIA", entry.getValue()))
                .toList();
    }

    private List<CrmReportCategoryItem> mapCrmReportCategoryRows(List<Object[]> rows) {
        return rows.stream()
                .map(row -> new CrmReportCategoryItem(clean(row[0] == null ? null : row[0].toString()), crmToLong(row[1])))
                .toList();
    }

    private List<CrmReportUserRankingItem> mergeCrmUserRanking(List<Object[]> assignedRows, List<Object[]> resolvedRows) {
        Map<Integer, CrmReportUserRankingMutable> items = new LinkedHashMap<>();
        for (Object[] row : assignedRows) {
            Integer parsedIdUsuario = crmToInteger(row[0]);
            if (parsedIdUsuario == null) {
                parsedIdUsuario = 0;
            }
            Integer idUsuario = parsedIdUsuario;
            CrmReportUserRankingMutable item = items.computeIfAbsent(
                    idUsuario,
                    key -> new CrmReportUserRankingMutable(idUsuario, clean(row[1] == null ? "Sin asignar" : row[1].toString())));
            item.atendidos = crmToLong(row[2]);
        }
        for (Object[] row : resolvedRows) {
            Integer parsedIdUsuario = crmToInteger(row[0]);
            if (parsedIdUsuario == null) {
                parsedIdUsuario = 0;
            }
            Integer idUsuario = parsedIdUsuario;
            CrmReportUserRankingMutable item = items.computeIfAbsent(
                    idUsuario,
                    key -> new CrmReportUserRankingMutable(idUsuario, clean(row[1] == null ? "Sin asignar" : row[1].toString())));
            item.resueltos = crmToLong(row[2]);
        }
        return items.values().stream()
                .sorted((left, right) -> {
                    int byResolved = Long.compare(right.resueltos, left.resueltos);
                    return byResolved != 0 ? byResolved : Long.compare(right.atendidos, left.atendidos);
                })
                .map(item -> new CrmReportUserRankingItem(item.idUsuario, item.nombre, item.atendidos, item.resueltos))
                .toList();
    }

    private Integer crmToInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.valueOf(value.toString());
    }

    private long crmToLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }

    private LocalDate crmToLocalDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        return LocalDate.parse(value.toString());
    }

    private CrmWhatsappConversation requireConversation(Long conversationId) {
        return conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversacion no encontrada"));
    }

    private ConversationResponse toConversationResponse(CrmWhatsappConversation conversation) {
        return toConversationResponse(conversation, false);
    }

    public void publicarConversacionActualizada(CrmWhatsappConversation conversation) {
        if (conversation != null) {
            publishRealtimeEvent("conversation.updated", conversation, null);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publicarConversacionActualizada(CrmWhatsappConversationUpdatedEvent event) {
        if (event == null || event.conversationId() == null) return;
        conversationRepository.findById(event.conversationId())
                .ifPresent(this::publicarConversacionActualizada);
    }

    private void publishRealtimeEvent(
            String type,
            CrmWhatsappConversation conversation,
            MessageResponse message) {
        ConversationResponse fullConversation = toConversationResponse(conversation, false);
        ConversationResponse waitingConversation = toConversationResponse(conversation, true);
        CrmRealtimeEvent fullEvent = new CrmRealtimeEvent(
                type,
                conversation.getIdConversation(),
                fullConversation,
                message);
        CrmRealtimeEvent waitingEvent = new CrmRealtimeEvent(
                "conversation.updated",
                conversation.getIdConversation(),
                waitingConversation,
                null);
        Usuario assignedUser = conversation.getAssignedUser();
        eventService.publishAfterCommit(
                fullEvent,
                waitingEvent,
                assignedUser == null ? null : assignedUser.getIdUsuario(),
                ESPERA.equals(conversation.getStatus()) && assignedUser == null);
        if (assignedUser != null) {
            CrmRealtimeEvent revokedEvent = new CrmRealtimeEvent(
                    "conversation.removed",
                    conversation.getIdConversation(),
                    fullConversation,
                    null);
            eventService.publishToUnassignedUsersAfterCommit(
                    revokedEvent,
                    assignedUser.getIdUsuario());
        }
    }

    private ConversationResponse toConversationResponse(CrmWhatsappConversation conversation, boolean hidePreview) {
        return toConversationResponse(
                conversation,
                hidePreview,
                listarEtiquetasConversacionInterno(conversation.getIdConversation()));
    }

    private ConversationResponse toConversationResponse(
            CrmWhatsappConversation conversation,
            boolean hidePreview,
            List<CrmConversationTagResponse> tags) {
        Usuario assignedUser = conversation.getAssignedUser();
        Cliente cliente = conversation.getCliente();
        String clientPhone = cliente == null ? "" : normalizeCrmPhoneNumber(cliente.getTelefono());
        String displayPhone = nullIfBlank(clientPhone.isBlank() ? resolveConversationCrmPhone(conversation) : clientPhone);
        String displayName = cliente != null && !clean(cliente.getNombres()).isBlank()
                ? cliente.getNombres()
                : conversation.getContactName();
        String lastMessageType = clean(conversation.getLastMessageType()).isBlank()
                ? messageRepository.findLatestByConversationId(conversation.getIdConversation())
                        .map(CrmWhatsappMessage::getMessageType)
                        .orElse(null)
                : conversation.getLastMessageType();
        return new ConversationResponse(
                conversation.getIdConversation(),
                conversation.getPhone(),
                displayPhone,
                conversation.getWhatsappUsername(),
                displayName,
                conversation.getStatus(),
                hidePreview ? null : conversation.getLastMessage(),
                lastMessageType,
                conversation.getLastMessageAt(),
                conversation.getUnreadCount() == null ? 0 : conversation.getUnreadCount(),
                assignedUser == null ? null : assignedUser.getIdUsuario(),
                assignedUser == null ? null : fullName(assignedUser),
                conversation.getAssignedAt(),
                conversation.getAiAttentionMode() == null
                        ? CrmWhatsappAiAttentionMode.AUTOMATICA.name()
                        : conversation.getAiAttentionMode().name(),
                effectiveAttentionQueue(conversation).name(),
                conversation.getWaitingReason() == null ? null : conversation.getWaitingReason().name(),
                tags);
    }

    private CrmWhatsappAttentionQueue effectiveAttentionQueue(CrmWhatsappConversation conversation) {
        if (RESUELTO.equals(conversation.getStatus())) return CrmWhatsappAttentionQueue.RESOLVED;
        if (conversation.getAssignedUser() != null || ATENDIDO.equals(conversation.getStatus())) {
            return CrmWhatsappAttentionQueue.HUMAN_ACTIVE;
        }
        if (conversation.getWaitingReason() == CrmWhatsappWaitingReason.PAYMENT_VERIFICATION) {
            return CrmWhatsappAttentionQueue.PAYMENT_VERIFICATION;
        }
        if (conversation.getWaitingReason() == CrmWhatsappWaitingReason.ADVISOR_REQUIRED
                || conversation.getWaitingReason() == CrmWhatsappWaitingReason.AI_DISABLED
                || conversation.getAiAttentionMode() == CrmWhatsappAiAttentionMode.HUMANA) {
            return CrmWhatsappAttentionQueue.ADVISOR_REQUIRED;
        }
        return CrmWhatsappAttentionQueue.AI_ACTIVE;
    }

    private long numericCount(Object[] counts, int index) {
        if (counts == null || index < 0 || index >= counts.length) return 0;
        return counts[index] instanceof Number number ? number.longValue() : 0;
    }

    private CrmWhatsappAttentionQueue normalizeAttentionQueue(String value) {
        String normalized = clean(value).toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "ATTENDED", "HUMAN_ACTIVE" -> CrmWhatsappAttentionQueue.HUMAN_ACTIVE;
            case "AI_ACTIVE" -> CrmWhatsappAttentionQueue.AI_ACTIVE;
            case "RESOLVED" -> CrmWhatsappAttentionQueue.RESOLVED;
            case "WAITING" -> null;
            default -> null;
        };
    }

    private List<CrmConversationTagResponse> listarEtiquetasConversacionInterno(Long conversationId) {
        return conversationTagRepository.findActiveByConversationId(conversationId)
                .stream()
                .map(this::toConversationTagResponse)
                .toList();
    }

    private CrmConversationTagResponse toConversationTagResponse(CrmWhatsappConversationTag relation) {
        CrmWhatsappTag tag = relation.getTag();
        return new CrmConversationTagResponse(tag.getIdTag(), tag.getNombre(), tag.getColor());
    }

    private CrmWhatsappTag requireTagEmpresa(Long tagId, Integer idEmpresa) {
        if (tagId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Etiqueta requerida");
        }
        return tagRepository.findByIdTagAndEmpresa_IdEmpresaAndDeletedAtIsNull(tagId, idEmpresa)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Etiqueta no encontrada"));
    }

    private String validarNombreEtiqueta(String value) {
        String nombre = clean(value);
        if (nombre.length() < 2 || nombre.length() > 80) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El nombre debe tener entre 2 y 80 caracteres");
        }
        return nombre;
    }

    private String validarColorEtiqueta(String value) {
        String color = clean(value);
        if (color.isBlank()) {
            return "#3b82f6";
        }
        if (!color.matches("^#[0-9a-fA-F]{6}$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Color invalido");
        }
        return color.toLowerCase();
    }

    private MessageResponse toMessageResponse(CrmWhatsappMessage message) {
        boolean deleted = message.getDeletedAt() != null;
        QuoteResponse replyTo = message.getReplyToMessageId() == null
                ? null
                : messageRepository.findById(message.getReplyToMessageId())
                        .map(this::toQuoteResponse)
                        .orElse(null);
        return new MessageResponse(
                message.getIdMessage(),
                message.getDirection(),
                message.getOrigin(),
                message.getMessageType(),
                deleted ? "Mensaje eliminado" : message.getBody(),
                message.getMessageStatus(),
                message.getCreatedAt(),
                deleted ? null : message.getMediaMimeType(),
                deleted ? null : message.getMediaFileName(),
                deleted ? null : message.getMediaStoragePath(),
                deleted || message.getMediaStoragePath() == null || message.getMediaStoragePath().isBlank()
                        ? null
                        : "/api/crm/whatsapp/messages/" + message.getIdMessage() + "/media",
                replyTo,
                message.getRelatedSaleId(),
                deleted);
    }

    private List<MessageResponse> toMessageResponses(List<CrmWhatsappMessage> messages) {
        Set<Long> replyIds = messages.stream()
                .map(CrmWhatsappMessage::getReplyToMessageId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        Map<Long, CrmWhatsappMessage> replies = replyIds.isEmpty()
                ? Map.of()
                : messageRepository.findAllById(replyIds).stream()
                        .collect(java.util.stream.Collectors.toMap(
                                CrmWhatsappMessage::getIdMessage,
                                message -> message));
        return messages.stream()
                .map(message -> toMessageResponse(message, replies))
                .toList();
    }

    private MessageResponse toMessageResponse(
            CrmWhatsappMessage message,
            Map<Long, CrmWhatsappMessage> replies) {
        boolean deleted = message.getDeletedAt() != null;
        CrmWhatsappMessage quoted = message.getReplyToMessageId() == null
                ? null
                : replies.get(message.getReplyToMessageId());
        return new MessageResponse(
                message.getIdMessage(),
                message.getDirection(),
                message.getOrigin(),
                message.getMessageType(),
                deleted ? "Mensaje eliminado" : message.getBody(),
                message.getMessageStatus(),
                message.getCreatedAt(),
                deleted ? null : message.getMediaMimeType(),
                deleted ? null : message.getMediaFileName(),
                deleted ? null : message.getMediaStoragePath(),
                deleted || message.getMediaStoragePath() == null || message.getMediaStoragePath().isBlank()
                        ? null
                        : "/api/crm/whatsapp/messages/" + message.getIdMessage() + "/media",
                quoted == null ? null : toQuoteResponse(quoted),
                message.getRelatedSaleId(),
                deleted);
    }

    private QuoteResponse toQuoteResponse(CrmWhatsappMessage message) {
        boolean deleted = message.getDeletedAt() != null;
        return new QuoteResponse(
                message.getIdMessage(),
                message.getDirection(),
                deleted ? "Mensaje eliminado" : quoteText(message),
                deleted ? "TEXT" : message.getMessageType(),
                deleted);
    }

    private LocalDateTime parseDate(String value) {
        if (value == null || value.isBlank()) {
            return LocalDateTime.now();
        }
        try {
            return Instant.parse(value).atZone(ZoneId.systemDefault()).toLocalDateTime();
        } catch (DateTimeParseException e) {
            return LocalDateTime.now();
        }
    }

    private Usuario requireCrmUser(Usuario usuarioSesion) {
        if (usuarioSesion == null || usuarioSesion.getIdUsuario() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autorizado");
        }
        Usuario usuario = usuarioRepository.findByIdUsuarioAndDeletedAtIsNull(usuarioSesion.getIdUsuario())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autorizado"));
        if (!"ACTIVO".equalsIgnoreCase(clean(usuario.getEstado()))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario inactivo");
        }
        if (!isAdmin(usuario) && !Boolean.TRUE.equals(usuario.getAccesoCrm())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes acceso al CRM");
        }
        return usuario;
    }

    private boolean isAdmin(Usuario usuario) {
        return usuario != null && usuario.getRol() == Rol.ADMINISTRADOR;
    }

    private boolean canReceiveCrmTransfer(Usuario usuario) {
        return usuario != null
                && "ACTIVO".equalsIgnoreCase(clean(usuario.getEstado()))
                && (isAdmin(usuario) || Boolean.TRUE.equals(usuario.getAccesoCrm()));
    }

    private boolean canListConversation(
            CrmWhatsappConversation conversation,
            Usuario actor,
            boolean admin,
            String requestedStatus) {
        if (admin) {
            return true;
        }
        boolean unassignedWaiting = ESPERA.equals(conversation.getStatus()) && conversation.getAssignedUser() == null;
        if (ESPERA.equals(requestedStatus)) {
            return unassignedWaiting;
        }
        if ("ALL".equals(requestedStatus) && unassignedWaiting) {
            return true;
        }
        return isAssignedTo(conversation, actor);
    }

    private boolean shouldHideWaitingPreview(CrmWhatsappConversation conversation, boolean admin) {
        return false;
    }

    private void requireCanReadMessages(CrmWhatsappConversation conversation, Usuario actor) {
        CrmWhatsappAttentionQueue queue = effectiveAttentionQueue(conversation);
        boolean sharedUnassignedQueue = conversation.getAssignedUser() == null
                && (queue == CrmWhatsappAttentionQueue.AI_ACTIVE
                    || queue == CrmWhatsappAttentionQueue.ADVISOR_REQUIRED
                    || queue == CrmWhatsappAttentionQueue.PAYMENT_VERIFICATION);
        if (isAdmin(actor)
                || isAssignedTo(conversation, actor)
                || sharedUnassignedQueue) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puedes ver esta conversacion");
    }

    private void requireCanOperateAssigned(CrmWhatsappConversation conversation, Usuario actor) {
        if (conversation.getAssignedUser() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acepta el chat antes de responder");
        }
        if (isAdmin(actor) || isAssignedTo(conversation, actor)) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puedes operar esta conversacion");
    }

    private void requireCanReopen(CrmWhatsappConversation conversation, Usuario actor) {
        if (isAdmin(actor) || isAssignedTo(conversation, actor)) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puedes reabrir esta conversacion");
    }

    private boolean isAssignedTo(CrmWhatsappConversation conversation, Usuario actor) {
        return conversation.getAssignedUser() != null
                && actor != null
                && conversation.getAssignedUser().getIdUsuario().equals(actor.getIdUsuario());
    }

    private String fullName(Usuario usuario) {
        String name = (clean(usuario.getNombre()) + " " + clean(usuario.getApellido())).trim();
        return name.isBlank() ? usuario.getCorreo() : name;
    }

    private String normalizeChatId(String value) {
        String cleaned = clean(value);
        if (cleaned.endsWith("@s.whatsapp.net") || cleaned.endsWith("@lid")) {
            return cleaned;
        }
        if (cleaned.endsWith("@c.us")) {
            return cleaned.replace("@c.us", "@s.whatsapp.net");
        }

        String digits = cleaned.replaceAll("\\D", "");
        return digits.isBlank() ? "" : digits + "@s.whatsapp.net";
    }

    private String normalizePhoneNumber(String value) {
        return clean(value).replaceAll("\\D", "");
    }

    private String normalizeCrmPhoneNumber(String value) {
        return CrmWhatsappPhoneUtils.normalizePeruvianMobile(value);
    }

    private String nullIfBlank(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String readMessageId(String body) {
        return readJsonText(body, "messageId");
    }

    private String readJsonString(String body, String field) {
        try {
            JsonNode node = objectMapper.readTree(body).path(field);
            return node.isMissingNode() || node.isNull() ? null : objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            return null;
        }
    }

    private String readJsonText(String body, String field) {
        try {
            return objectMapper.readTree(body).path(field).asText(null);
        } catch (Exception e) {
            return null;
        }
    }

    private int statusRank(String status) {
        return switch (clean(status)) {
            case "accepted" -> 0;
            case "failed" -> 0;
            case "sent" -> 1;
            case "delivered" -> 2;
            case "read" -> 3;
            default -> -1;
        };
    }

    private boolean isValidChatId(String value) {
        String chatId = normalizeChatId(value);
        return chatId.endsWith("@s.whatsapp.net") || chatId.endsWith("@lid");
    }

    private String messageTypeFor(String mimeType, String fileName) {
        String mime = clean(mimeType).toLowerCase();
        String name = clean(fileName).toLowerCase();
        if (mime.startsWith("image/") || name.matches(".*\\.(avif|bmp|gif|jpe?g|png|svg|webp)$")) {
            return "IMAGE";
        }
        if (mime.startsWith("video/") || name.matches(".*\\.(3gp|avi|m4v|mkv|mov|mp4|mpeg|mpg|ogg|ogv|webm)$")) {
            return "VIDEO";
        }
        if (mime.startsWith("audio/") || name.matches(".*\\.(aac|amr|m4a|mp3|ogg|opus|wav)$")) {
            return "AUDIO";
        }
        return "DOCUMENT";
    }

    private String mediaPreviewText(String body, String fileName) {
        String cleanBody = clean(body);
        if (!cleanBody.isBlank()) {
            return cleanBody;
        }
        String cleanName = clean(fileName);
        return cleanName.isBlank() ? "[Archivo]" : cleanName;
    }

    private CrmWhatsappMessage resolveReplyMessage(CrmWhatsappConversation conversation, Long replyToMessageId) {
        if (replyToMessageId == null) {
            return null;
        }
        CrmWhatsappMessage replyTo = messageRepository.findById(replyToMessageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mensaje citado no encontrado"));
        if (!replyTo.getConversation().getIdConversation().equals(conversation.getIdConversation())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El mensaje citado pertenece a otra conversacion");
        }
        if (replyTo.getDeletedAt() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se puede responder a un mensaje eliminado");
        }
        if (quotedMessageJsonFor(replyTo) == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se puede citar este mensaje");
        }
        return replyTo;
    }

    private Object quotedMessageFor(CrmWhatsappMessage message) {
        try {
            return objectMapper.readValue(quotedMessageJsonFor(message), Object.class);
        } catch (Exception e) {
            return null;
        }
    }

    private String quotedMessageJsonFor(CrmWhatsappMessage message) {
        String stored = clean(message.getBaileysMessageJson());
        if (!stored.isBlank()) {
            return stored;
        }
        String key = messageKeyJsonFor(message);
        if (key == null || key.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> quoted = new LinkedHashMap<>();
            quoted.put("key", objectMapper.readValue(key, Object.class));
            quoted.put("message", legacyBaileysContent(message));
            return objectMapper.writeValueAsString(quoted);
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> legacyBaileysContent(CrmWhatsappMessage message) {
        String text = quoteText(message);
        if ("TEXT".equals(message.getMessageType())) {
            return Map.of("conversation", text);
        }
        Map<String, Object> media = new LinkedHashMap<>();
        media.put("caption", clean(message.getBody()));
        media.put("mimetype", clean(message.getMediaMimeType()).isBlank() ? "application/octet-stream" : message.getMediaMimeType());
        return switch (clean(message.getMessageType())) {
            case "IMAGE" -> Map.of("imageMessage", media);
            case "VIDEO" -> Map.of("videoMessage", media);
            case "AUDIO" -> Map.of("audioMessage", media);
            default -> Map.of("documentMessage", media);
        };
    }

    private String messageKeyJsonFor(CrmWhatsappMessage message) {
        String stored = clean(message.getMessageKeyJson());
        if (!stored.isBlank()) {
            return stored;
        }
        if (clean(message.getWhatsappMessageId()).isBlank()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "remoteJid", message.getConversation().getPhone(),
                    "fromMe", "OUTGOING".equals(message.getDirection()),
                    "id", message.getWhatsappMessageId()));
        } catch (Exception e) {
            return null;
        }
    }

    private Long findReplyToMessageId(String quotedMessageId, Long conversationId) {
        String id = clean(quotedMessageId);
        if (id.isBlank()) {
            return null;
        }
        return messageRepository.findFirstByWhatsappMessageId(id)
                .filter(message -> message.getConversation().getIdConversation().equals(conversationId))
                .map(CrmWhatsappMessage::getIdMessage)
                .orElse(null);
    }

    private String quoteText(CrmWhatsappMessage message) {
        String body = clean(message.getBody());
        if (!body.isBlank()) {
            return body;
        }
        String name = clean(message.getMediaFileName());
        return name.isBlank() ? "[Archivo]" : name;
    }

    private void markDeleted(CrmWhatsappMessage message) {
        String storagePath = clean(message.getMediaStoragePath());
        if (!storagePath.isBlank()) {
            try {
                storageService.deleteByKey(storagePath);
            } catch (RuntimeException ignored) {
                // El mensaje debe quedar eliminado aunque el adjunto ya no exista.
            }
        }
        message.setDeletedAt(LocalDateTime.now());
        message.setBody("Mensaje eliminado");
        message.setMediaMimeType(null);
        message.setMediaFileName(null);
        message.setMediaStoragePath(null);
        message.setMessageType("TEXT");
        message.setMessageStatus("deleted");
    }

    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return null;
        }
    }

    private String safeFileName(String fileName) {
        String cleaned = clean(fileName).replace("\\", "/");
        int slash = cleaned.lastIndexOf('/');
        if (slash >= 0) {
            cleaned = cleaned.substring(slash + 1);
        }
        cleaned = cleaned.replaceAll("[\\p{Cntrl}<>:\"|?*]+", "_").trim();
        return cleaned.isBlank() ? "archivo" : cleaned;
    }

    private String normalizeContactFilter(String filter) {
        String normalized = clean(filter).isBlank() ? "TODOS" : clean(filter).toUpperCase();
        return switch (normalized) {
            case "CON_COMPRAS", "SIN_COMPRAS", "DATOS_INCOMPLETOS", "CON_ETIQUETAS" -> normalized;
            default -> "TODOS";
        };
    }

    private boolean tieneDatosIncompletos(Cliente cliente) {
        if (cliente == null) {
            return true;
        }
        return clean(cliente.getTelefono()).isBlank()
                || clean(cliente.getNroDocumento()).isBlank()
                || clean(cliente.getCorreo()).isBlank()
                || clean(cliente.getDireccion()).isBlank();
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeConversationStatus(String status) {
        String normalized = clean(status).toUpperCase();
        return switch (normalized) {
            case ESPERA, ATENDIDO, RESUELTO -> normalized;
            default -> "ALL";
        };
    }

    private String normalizeConversationSearchTerm(String value) {
        String term = clean(value);
        if (term.isBlank() || !term.matches("[+()\\-\\s\\d]+")) {
            return term;
        }
        String digits = normalizePhoneNumber(term);
        if (digits.length() == 11 && digits.startsWith("51")) {
            return digits.substring(2);
        }
        return digits;
    }

    private String readString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof String text ? text : null;
    }

    private Integer readPositiveInteger(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number number) {
            int parsed = number.intValue();
            return parsed > 0 ? parsed : null;
        }
        if (value instanceof String text) {
            try {
                int parsed = Integer.parseInt(text.trim());
                return parsed > 0 ? parsed : null;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    public record ConversationResponse(
            Long id,
            String phone,
            String phoneNumber,
            String whatsappUsername,
            String contactName,
            String status,
            String lastMessage,
            String lastMessageType,
            LocalDateTime lastMessageAt,
            int unreadCount,
            Integer assignedUserId,
            String assignedUserName,
            LocalDateTime assignedAt,
            String aiAttentionMode,
            String attentionQueue,
            String waitingReason,
            List<CrmConversationTagResponse> tags) {
    }

    public record AttentionModeRequest(String mode) {}

    public record AttentionModeResponse(
            ConversationResponse conversation,
            CrmWhatsappAiMemoryService.MemoryResponse memory,
            boolean automaticAvailable,
            String blockedReason) {}

    public record ConversationPageResponse(
            List<ConversationResponse> content,
            int page,
            int size,
            int totalPages,
            long totalElements,
            int numberOfElements,
            boolean first,
            boolean last,
            boolean empty,
            ConversationStatusCounts counts) {
    }

    public record ConversationStatusCounts(long all, long attended, long aiAttending, long waiting, long resolved) {
    }

    public record CrmRealtimeEvent(
            String type,
            Long conversationId,
            ConversationResponse conversation,
            MessageResponse message) {
    }

    public record CrmTagRequest(String nombre, String color) {
    }

    public record CrmTagResponse(Long id, String nombre, String color, long uso) {
    }

    public record CrmConversationTagResponse(Long id, String label, String color) {
    }

    public record DeleteWhatsappMessagesResponse(
            int deletedMessages,
            int deletedConversations,
            int deletedTagAssignments,
            int deletedMediaFiles) {
    }

    public record CrmWhatsappReportResponse(
            String filtro,
            LocalDate desde,
            LocalDate hasta,
            CrmReportKpis kpis,
            List<CrmReportSeriesPoint> mensajesPorFecha,
            List<CrmReportSeriesPoint> chatsResueltosPorFecha,
            List<CrmReportSeriesPoint> clientesNuevosPorFecha,
            List<CrmReportCategoryItem> conversacionesPorEstado,
            List<CrmReportCategoryItem> mensajesPorTipo,
            List<CrmReportCategoryItem> mensajesPorDireccion,
            List<CrmReportUserRankingItem> usuariosRanking) {
    }

    public record CrmReportKpis(
            long chatsTotales,
            long chatsResueltos,
            long chatsEspera,
            long chatsAtendidos,
            long mensajesRecibidos,
            long mensajesEnviados,
            long clientesVinculados,
            long clientesNuevos) {
    }

    public record CrmReportSeriesPoint(String fecha, String etiqueta, String granularidad, long valor) {
    }

    public record CrmReportCategoryItem(String label, long value) {
    }

    public record CrmReportUserRankingItem(Integer idUsuario, String usuario, long chatsAtendidos, long chatsResueltos) {
    }

    public record CrmWhatsappContactResponse(
            Long idConversation,
            String conversationStatus,
            String lastMessage,
            String lastMessageType,
            LocalDateTime lastMessageAt,
            ClienteResumenResponse cliente,
            List<CrmConversationTagResponse> tags,
            long comprasEmitidas,
            BigDecimal montoTotalComprado,
            LocalDateTime ultimaCompra,
            boolean datosIncompletos,
            boolean tieneCompras,
            boolean tieneEtiquetas) {
    }

    public record MessageResponse(
            Long id,
            String direction,
            String origin,
            String messageType,
            String body,
            String status,
            LocalDateTime createdAt,
            String mediaMimeType,
            String mediaFileName,
            String mediaStoragePath,
            String mediaUrl,
            QuoteResponse replyTo,
            Integer relatedSaleId,
            boolean deleted) {
        public MessageResponse(
                Long id, String direction, String origin, String messageType, String body, String status,
                LocalDateTime createdAt, String mediaMimeType, String mediaFileName, String mediaStoragePath,
                String mediaUrl, QuoteResponse replyTo, boolean deleted) {
            this(id, direction, origin, messageType, body, status, createdAt, mediaMimeType, mediaFileName,
                    mediaStoragePath, mediaUrl, replyTo, null, deleted);
        }
    }

    public record MessagePageResponse(
            List<MessageResponse> content,
            Long oldestId,
            Long newestId,
            boolean hasMoreBefore) {
    }

    public record QuoteResponse(
            Long id,
            String direction,
            String body,
            String messageType,
            boolean deleted) {
    }

    public record MediaDownload(byte[] bytes, String contentType, String fileName) {
    }

    public record SendMessageRequest(String message, Long replyToMessageId, Long pendingDeliveryId) {
        public SendMessageRequest(String message, Long replyToMessageId) {
            this(message, replyToMessageId, null);
        }
    }

    public record ReceiptRequest(String format) {
    }

    public record TransferRequest(Integer assignedUserId) {
    }

    public record TransferUserResponse(
            Integer id,
            String name,
            String role,
            String email) {
    }

    public record LinkClienteRequest(Integer idCliente) {
    }

    public record QuickSaleContextResponse(
            Long conversationId,
            String contactName,
            String contactPhone,
            ClienteResumenResponse cliente,
            List<SucursalVentaRapidaResponse> sucursales,
            List<ComprobanteVentaRapidaResponse> comprobantes) {
    }

    public record ClienteResumenResponse(
            Integer idCliente,
            String tipoDocumento,
            String nroDocumento,
            String nombres,
            String telefono,
            String correo,
            String direccion) {
    }

    public record SucursalVentaRapidaResponse(
            Integer idSucursal,
            String nombreSucursal,
            String tipoSucursal) {
    }

    public record ComprobanteVentaRapidaResponse(
            Integer idComprobante,
            String tipoComprobante,
            String serie,
            Integer siguienteCorrelativo,
            Boolean habilitadoVenta) {
    }

    public record VentaRapidaHistorialResponse(
            Integer idVenta,
            LocalDateTime fecha,
            String tipoComprobante,
            String serie,
            Integer correlativo,
            String moneda,
            BigDecimal total,
            String estado,
            Integer idSucursal,
            String nombreSucursal,
            List<VentaRapidaDetalleResponse> detalles,
            List<VentaRapidaPagoResponse> pagos) {
    }

    public record VentaRapidaDetalleResponse(
            Integer idProductoVariante,
            String nombre,
            String sku,
            String color,
            String talla,
            Integer cantidad,
            BigDecimal total) {
    }

    public record VentaRapidaPagoResponse(
            Integer idMetodoPago,
            String metodoPago,
            BigDecimal monto) {
    }

    public record VarianteCompradaChartResponse(
            Integer idProductoVariante,
            String nombre,
            String sku,
            String color,
            String talla,
            Integer cantidad,
            BigDecimal monto) {
    }

    public record MetodoPagoChartResponse(
            Integer idMetodoPago,
            String metodoPago,
            Integer cantidad,
            BigDecimal monto) {
    }

    public record VentasClienteCrmResponse(
            PagedResponse<VentaRapidaHistorialResponse> historial,
            List<VarianteCompradaChartResponse> productosTop,
            List<MetodoPagoChartResponse> metodosPago) {
    }

    public record MetodoPagoCuentaVentaRapidaResponse(
            Integer idMetodoPagoCuenta,
            String numeroCuenta,
            String titular) {
    }

    public record MetodoPagoVentaRapidaResponse(
            Integer idMetodoPago,
            String nombre,
            Boolean requiereCodigoOperacion,
            Boolean requiereFechaPago,
            Boolean requiereHoraPago,
            List<MetodoPagoCuentaVentaRapidaResponse> cuentas) {
    }

    public record SaleOptionsResponse(
            Integer idSucursal,
            List<MetodoPagoVentaRapidaResponse> metodosPago) {
    }

    public record CrmSaleCreateRequest(
            Integer idSucursal,
            Integer idComprobante,
            String moneda,
            String formaPago,
            Double igvPorcentaje,
            Double descuentoTotal,
            String tipoDescuento,
            List<VentaDetalleCreateItem> detalles,
            List<VentaPagoCreateItem> pagos,
            String telefonoRespaldo,
            Long aiSaleDraftId,
            Integer aiSaleDraftVersion) {
    }

    public record VentaCrmResponse(
            VentaResponse venta,
            QuickSaleContextResponse context) {
    }

    public record WebhookRequest(
            String clientId,
            String event,
            String messageId,
            String from,
            String phoneNumber,
            String username,
            String fromName,
            String body,
            String timestamp,
            String status,
            String quotedMessageId,
            boolean hasMedia,
            Map<String, Object> media,
            Map<String, Object> messageKey,
            Map<String, Object> baileysMessage) {
    }
}
