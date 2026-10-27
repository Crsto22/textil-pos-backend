package com.sistemapos.sistematextil.controllers;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.sistemapos.sistematextil.services.CrmWhatsappChatService;
import com.sistemapos.sistematextil.services.CrmWhatsappBridgeService;
import com.sistemapos.sistematextil.services.CrmWhatsappConnectionService;
import com.sistemapos.sistematextil.services.CrmWhatsappConnectionService.BranchRequest;
import com.sistemapos.sistematextil.services.CrmWhatsappConnectionStateService.BridgeConnectionState;
import com.sistemapos.sistematextil.services.CrmWhatsappAiCredentialService;
import com.sistemapos.sistematextil.services.CrmWhatsappPaymentEvidenceService;
import com.sistemapos.sistematextil.services.CrmWhatsappSalesReviewService;
import com.sistemapos.sistematextil.services.CrmWhatsappFilmReviewService;
import com.sistemapos.sistematextil.services.CrmQuickMessageService;
import com.sistemapos.sistematextil.services.CrmQuickMessageService.QuickMessageMedia;
import com.sistemapos.sistematextil.services.CrmQuickMessageService.QuickMessageRequest;
import com.sistemapos.sistematextil.services.CrmQuickMessageService.SendQuickMessageRequest;
import com.sistemapos.sistematextil.services.CrmWhatsappPaymentEvidenceService.PaymentRequestCreateRequest;
import com.sistemapos.sistematextil.services.CrmWhatsappPaymentEvidenceService.PaymentEvidenceDownload;
import com.sistemapos.sistematextil.services.CrmWhatsappAiCredentialService.CredentialRequest;
import com.sistemapos.sistematextil.services.CrmWhatsappAgentConfigService;
import com.sistemapos.sistematextil.services.CrmWhatsappAgentConfigService.AgentConfigRequest;
import com.sistemapos.sistematextil.services.ai.GeminiConnectionService;
import com.sistemapos.sistematextil.services.ai.AiProviderException;
import com.sistemapos.sistematextil.services.agent.visibility.AgentConversationVisibilityService;
import com.sistemapos.sistematextil.services.agent.knowledge.AgentKnowledgeAdminService;
import com.sistemapos.sistematextil.services.agent.knowledge.AgentKnowledgeAdminService.ArticleRequest;
import com.sistemapos.sistematextil.services.agent.knowledge.AgentKnowledgeAdminService.KnowledgeTestRequest;
import com.sistemapos.sistematextil.services.agent.fulfillment.ShalomAgencySyncService;
import com.sistemapos.sistematextil.model.CustomUser;
import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.model.CrmWhatsappArea;
import com.sistemapos.sistematextil.util.usuario.Rol;
import com.sistemapos.sistematextil.services.CrmWhatsappChatService.MediaDownload;
import com.sistemapos.sistematextil.services.CrmWhatsappChatService.CrmSaleCreateRequest;
import com.sistemapos.sistematextil.services.CrmWhatsappChatService.CrmTagRequest;
import com.sistemapos.sistematextil.services.CrmWhatsappChatService.LinkClienteRequest;
import com.sistemapos.sistematextil.services.CrmWhatsappChatService.SendMessageRequest;
import com.sistemapos.sistematextil.services.CrmWhatsappChatService.ReceiptRequest;
import com.sistemapos.sistematextil.services.CrmWhatsappChatService.ReadStateRequest;
import com.sistemapos.sistematextil.services.CrmWhatsappChatService.TransferRequest;
import com.sistemapos.sistematextil.services.CrmWhatsappChatService.VisitRequest;
import com.sistemapos.sistematextil.services.CrmWhatsappChatService.WebhookRequest;
import com.sistemapos.sistematextil.util.cliente.ClienteCreateRequest;
import com.sistemapos.sistematextil.util.cliente.ClienteUpdateRequest;

import lombok.AllArgsConstructor;

@RestController
@RequestMapping(value = "api/crm/whatsapp", produces = MediaType.APPLICATION_JSON_VALUE)
@AllArgsConstructor
public class CrmWhatsappController {

    private final CrmWhatsappBridgeService crmWhatsappBridgeService;
    private final CrmWhatsappChatService crmWhatsappChatService;
    private final CrmWhatsappConnectionService crmWhatsappConnectionService;
    private final CrmWhatsappAiCredentialService crmWhatsappAiCredentialService;
    private final CrmWhatsappAgentConfigService crmWhatsappAgentConfigService;
    private final CrmWhatsappPaymentEvidenceService crmWhatsappPaymentEvidenceService;
    private final GeminiConnectionService geminiConnectionService;
    private final AgentConversationVisibilityService agentConversationVisibilityService;
    private final AgentKnowledgeAdminService agentKnowledgeAdminService;
    private final CrmWhatsappSalesReviewService crmWhatsappSalesReviewService;
    private final ShalomAgencySyncService shalomAgencySyncService;
    private final CrmWhatsappFilmReviewService crmWhatsappFilmReviewService;
    private final CrmQuickMessageService crmQuickMessageService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @GetMapping("/quick-messages")
    public ResponseEntity<?> listarMensajesRapidos(
            @RequestParam(name = "q", defaultValue = "") String query,
            Authentication authentication) {
        return ResponseEntity.ok(crmQuickMessageService.list(query, currentUser(authentication)));
    }

    @GetMapping("/quick-messages/{commandId}")
    public ResponseEntity<?> obtenerMensajeRapido(@PathVariable Long commandId, Authentication authentication) {
        return ResponseEntity.ok(crmQuickMessageService.get(commandId, currentUser(authentication)));
    }

    @PostMapping(value = "/quick-messages", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> crearMensajeRapido(
            @RequestParam("payload") String payload,
            @RequestParam(name = "files", required = false) List<MultipartFile> files,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(crmQuickMessageService.create(
                readQuickMessageRequest(payload), files, currentUser(authentication)));
    }

    @PutMapping(value = "/quick-messages/{commandId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> actualizarMensajeRapido(
            @PathVariable Long commandId,
            @RequestParam("payload") String payload,
            @RequestParam(name = "files", required = false) List<MultipartFile> files,
            Authentication authentication) {
        return ResponseEntity.ok(crmQuickMessageService.update(commandId,
                readQuickMessageRequest(payload), files, currentUser(authentication)));
    }

    @DeleteMapping("/quick-messages/{commandId}")
    public ResponseEntity<Void> eliminarMensajeRapido(@PathVariable Long commandId, Authentication authentication) {
        crmQuickMessageService.delete(commandId, currentUser(authentication));
        return ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/quick-messages/{commandId}/blocks/{blockId}/media", produces = MediaType.ALL_VALUE)
    public ResponseEntity<byte[]> descargarImagenMensajeRapido(
            @PathVariable Long commandId,
            @PathVariable Long blockId,
            Authentication authentication) {
        QuickMessageMedia media = crmQuickMessageService.download(commandId, blockId, currentUser(authentication));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(media.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + media.fileName().replace("\"", "") + "\"")
                .body(media.bytes());
    }

    @PostMapping("/quick-messages/{commandId}/send")
    public ResponseEntity<?> enviarMensajeRapido(
            @PathVariable Long commandId,
            @RequestBody SendQuickMessageRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(crmQuickMessageService.send(commandId, request, currentUser(authentication)));
    }

    @PostMapping("/quick-messages/{commandId}/blocks/{blockId}/send")
    public ResponseEntity<?> enviarBloqueMensajeRapido(
            @PathVariable Long commandId,
            @PathVariable Long blockId,
            @RequestBody SendQuickMessageRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(crmQuickMessageService.sendBlock(
                commandId, blockId, request, currentUser(authentication)));
    }

    @GetMapping("/sales")
    public ResponseEntity<?> listarVentasCrm(
            @RequestParam(name = "q", defaultValue = "") String query,
            @RequestParam(name = "reviewStatus", defaultValue = "ALL") String reviewStatus,
            @RequestParam(name = "receiptType", defaultValue = "ALL") String receiptType,
            @RequestParam(name = "saleStatus", defaultValue = "ALL") String saleStatus,
            @RequestParam(name = "period", defaultValue = "ALL") String period,
            @RequestParam(name = "from", required = false) LocalDate from,
            @RequestParam(name = "to", required = false) LocalDate to,
            @RequestParam(name = "source", defaultValue = "ALL") String source,
            @RequestParam(name = "order", defaultValue = "DESC") String order,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            Authentication authentication) {
        return ResponseEntity.ok(crmWhatsappSalesReviewService.list(
                currentUser(authentication), query, reviewStatus, receiptType, saleStatus,
                period, from, to, source, order, page, size));
    }

    @GetMapping("/status")
    public ResponseEntity<?> obtenerEstado(Authentication authentication) {
        return crmWhatsappConnectionService.obtenerEstado(currentUser(authentication));
    }

    @GetMapping("/connections")
    public ResponseEntity<?> listarConexiones(Authentication authentication) {
        return ResponseEntity.ok(crmWhatsappConnectionService.listarConexiones(currentUser(authentication)));
    }

    @GetMapping("/connections/{area}/status")
    public ResponseEntity<?> obtenerEstadoArea(@PathVariable String area, Authentication authentication) {
        return crmWhatsappConnectionService.obtenerEstado(parseArea(area), currentUser(authentication));
    }

    @GetMapping("/connections/{area}/qr")
    public ResponseEntity<String> obtenerQrArea(@PathVariable String area, Authentication authentication) {
        requireAdmin(authentication);
        return crmWhatsappBridgeService.obtenerQr(parseArea(area));
    }

    @PostMapping("/connections/{area}/logout")
    public ResponseEntity<String> cerrarSesionArea(@PathVariable String area, Authentication authentication) {
        requireAdmin(authentication);
        return crmWhatsappBridgeService.cerrarSesion(parseArea(area));
    }

    @PostMapping("/connections/{area}/acknowledge-phone-change")
    public ResponseEntity<?> confirmarCambioNumeroArea(@PathVariable String area, Authentication authentication) {
        requireAdmin(authentication);
        return ResponseEntity.ok(crmWhatsappConnectionService.confirmarCambioNumero(
                parseArea(area), currentUser(authentication)));
    }

    @PostMapping("/connection-event")
    public ResponseEntity<?> actualizarEstadoConexion(
            @RequestHeader(name = "X-Bridge-Token", required = false) String bridgeToken,
            @RequestBody BridgeConnectionState request) {
        if (!crmWhatsappBridgeService.isBridgeToken(bridgeToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autorizado");
        }
        return ResponseEntity.ok(crmWhatsappConnectionService.actualizarEstadoBridge(request));
    }

    @PostMapping("/connection/acknowledge-phone-change")
    public ResponseEntity<?> confirmarCambioNumero(Authentication authentication) {
        return ResponseEntity.ok(crmWhatsappConnectionService.confirmarCambioNumero(currentUser(authentication)));
    }

    @GetMapping("/connection/branches")
    public ResponseEntity<?> listarSucursalesConexion(Authentication authentication) {
        return ResponseEntity.ok(crmWhatsappConnectionService.listarSucursales(currentUser(authentication)));
    }

    @PutMapping("/connection/branch")
    public ResponseEntity<?> asignarSucursalConexion(
            @RequestBody BranchRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(crmWhatsappConnectionService.asignarSucursal(
                request == null ? null : request.idSucursal(),
                currentUser(authentication)));
    }

    @GetMapping("/connection/ai-credentials")
    public ResponseEntity<?> obtenerCredencialesIa(Authentication authentication) {
        return ResponseEntity.ok(crmWhatsappAiCredentialService.getStatus(currentUser(authentication)));
    }

    @PutMapping("/connection/ai-credentials")
    public ResponseEntity<?> guardarCredencialesIa(
            @RequestBody CredentialRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(crmWhatsappAiCredentialService.save(request, currentUser(authentication)));
    }

    @DeleteMapping("/connection/ai-credentials")
    public ResponseEntity<?> eliminarCredencialesIa(Authentication authentication) {
        return ResponseEntity.ok(crmWhatsappAiCredentialService.delete(currentUser(authentication)));
    }

    @PostMapping("/connection/ai-credentials/test")
    public ResponseEntity<?> probarCredencialesIa(Authentication authentication) {
        Long connectionId = crmWhatsappAiCredentialService.requireAdminConnectionId(currentUser(authentication));
        try {
            return ResponseEntity.ok(geminiConnectionService.testConnection(connectionId));
        } catch (AiProviderException error) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, error.getMessage());
        }
    }

    @GetMapping("/connection/agent-config")
    public ResponseEntity<?> obtenerConfiguracionAgente(Authentication authentication) {
        return ResponseEntity.ok(crmWhatsappAgentConfigService.get(currentUser(authentication)));
    }

    @PutMapping("/connection/agent-config")
    public ResponseEntity<?> guardarConfiguracionAgente(
            @RequestBody AgentConfigRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(crmWhatsappAgentConfigService.save(request, currentUser(authentication)));
    }

    @GetMapping("/connection/shalom-agencies/status")
    public ResponseEntity<?> obtenerEstadoAgenciasShalom(Authentication authentication) {
        crmWhatsappAiCredentialService.requireAdminConnectionId(currentUser(authentication));
        return ResponseEntity.ok(shalomAgencySyncService.status());
    }

    @PostMapping("/connection/shalom-agencies/sync")
    public ResponseEntity<?> sincronizarAgenciasShalom(Authentication authentication) {
        crmWhatsappAiCredentialService.requireAdminConnectionId(currentUser(authentication));
        return ResponseEntity.ok(shalomAgencySyncService.synchronize());
    }

    @GetMapping("/connection/knowledge")
    public ResponseEntity<?> listarConocimientos(Authentication authentication) {
        return ResponseEntity.ok(agentKnowledgeAdminService.list(currentUser(authentication)));
    }

    @PostMapping("/connection/knowledge")
    public ResponseEntity<?> guardarConocimiento(
            @RequestBody ArticleRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(agentKnowledgeAdminService.save(request, currentUser(authentication)));
    }

    @DeleteMapping("/connection/knowledge/{articleId}")
    public ResponseEntity<?> eliminarConocimiento(
            @PathVariable Long articleId,
            Authentication authentication) {
        agentKnowledgeAdminService.delete(articleId, currentUser(authentication));
        return ResponseEntity.ok(java.util.Map.of("deleted", true));
    }

    @PostMapping("/connection/knowledge/test")
    public ResponseEntity<?> probarConocimiento(
            @RequestBody KnowledgeTestRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(agentKnowledgeAdminService.test(request, currentUser(authentication)));
    }


    @GetMapping("/qr")
    public ResponseEntity<String> obtenerQr(Authentication authentication) {
        requireAdmin(authentication);
        return crmWhatsappBridgeService.obtenerQr();
    }

    @PostMapping("/logout")
    public ResponseEntity<String> cerrarSesion(Authentication authentication) {
        requireAdmin(authentication);
        return crmWhatsappBridgeService.cerrarSesion();
    }

    @PostMapping("/webhook")
    public ResponseEntity<?> recibirWebhook(
            @RequestHeader(name = "X-Bridge-Token", required = false) String bridgeToken,
            @RequestBody WebhookRequest request) {
        if (!crmWhatsappBridgeService.isBridgeToken(bridgeToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autorizado");
        }
        crmWhatsappChatService.recibirWebhook(request);
        return ResponseEntity.ok().body(java.util.Map.of("ok", true));
    }

    @GetMapping("/conversations")
    public ResponseEntity<?> listarConversaciones(
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "view", required = false) String view,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "tagId", required = false) Long tagId,
            @RequestParam(name = "unreadOnly", defaultValue = "false") boolean unreadOnly,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size,
            Authentication authentication) {
        if (q != null || tagId != null || unreadOnly || page != null || size != null || view != null) {
            return ResponseEntity.ok(crmWhatsappChatService.listarConversacionesPaginadas(
                    status,
                    view,
                    q,
                    tagId,
                    unreadOnly,
                    page == null ? 0 : page,
                    size == null ? 10 : size,
                    currentUser(authentication)));
        }
        return ResponseEntity.ok(crmWhatsappChatService.listarConversaciones(status, currentUser(authentication)));
    }

    @GetMapping("/conversations/{id}/messages")
    public ResponseEntity<?> listarMensajes(
            @PathVariable("id") Long id,
            @RequestParam(name = "beforeId", required = false) Long beforeId,
            @RequestParam(name = "afterId", required = false) Long afterId,
            @RequestParam(name = "limit", defaultValue = "15") int limit,
            Authentication authentication) {
        return ResponseEntity.ok(crmWhatsappChatService.listarMensajes(
                id,
                beforeId,
                afterId,
                limit,
                currentUser(authentication)));
    }

    @PostMapping("/conversations/{id}/read-state")
    public ResponseEntity<?> actualizarEstadoLectura(
            @PathVariable("id") Long id,
            @RequestBody ReadStateRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(crmWhatsappChatService.actualizarEstadoLectura(
                id,
                request,
                currentUser(authentication)));
    }

    @PostMapping("/conversations/{id}/visits")
    public ResponseEntity<?> registrarIngreso(
            @PathVariable("id") Long id,
            @RequestBody VisitRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(crmWhatsappChatService.registrarIngreso(id, request, currentUser(authentication)));
    }

    @GetMapping("/conversations/{id}")
    public ResponseEntity<?> obtenerConversacion(
            @PathVariable("id") Long id,
            Authentication authentication) {
        return ResponseEntity.ok(crmWhatsappChatService.obtenerConversacion(id, currentUser(authentication)));
    }

    @GetMapping("/conversations/{id}/agent-summary")
    public ResponseEntity<?> obtenerResumenAgente(
            @PathVariable("id") Long id,
            Authentication authentication) {
        return ResponseEntity.ok(agentConversationVisibilityService.getSummary(id, currentUser(authentication)));
    }

    @GetMapping("/transfer-users")
    public ResponseEntity<?> listarUsuariosTransferencia(Authentication authentication) {
        return ResponseEntity.ok(crmWhatsappChatService.listarUsuariosTransferencia(currentUser(authentication)));
    }

    @GetMapping("/clients/search")
    public ResponseEntity<?> buscarClientes(
            @RequestParam(name = "q", required = false) String q,
            Authentication authentication) {
        return ResponseEntity.ok(crmWhatsappChatService.buscarClientes(q, currentUser(authentication)));
    }

    @GetMapping("/tags")
    public ResponseEntity<?> listarEtiquetas(Authentication authentication) {
        return ResponseEntity.ok(crmWhatsappChatService.listarEtiquetas(currentUser(authentication)));
    }

    @GetMapping("/reports")
    public ResponseEntity<?> obtenerReportes(
            @RequestParam(name = "filtro", required = false) String filtro,
            @RequestParam(name = "desde", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(name = "hasta", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            Authentication authentication) {
        return ResponseEntity.ok(crmWhatsappChatService.obtenerReporteCrm(filtro, desde, hasta, currentUser(authentication)));
    }

    @GetMapping("/contacts")
    public ResponseEntity<?> listarContactos(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "filter", required = false) String filter,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "12") int size,
            Authentication authentication) {
        return ResponseEntity.ok(crmWhatsappChatService.listarContactosCrm(q, filter, page, size, currentUser(authentication)));
    }

    @PostMapping("/tags")
    public ResponseEntity<?> crearEtiqueta(
            @RequestBody CrmTagRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(crmWhatsappChatService.crearEtiqueta(request, currentUser(authentication)));
    }

    @PutMapping("/tags/{tagId}")
    public ResponseEntity<?> actualizarEtiqueta(
            @PathVariable("tagId") Long tagId,
            @RequestBody CrmTagRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(crmWhatsappChatService.actualizarEtiqueta(tagId, request, currentUser(authentication)));
    }

    @DeleteMapping("/tags/{tagId}")
    public ResponseEntity<?> eliminarEtiqueta(
            @PathVariable("tagId") Long tagId,
            Authentication authentication) {
        crmWhatsappChatService.eliminarEtiqueta(tagId, currentUser(authentication));
        return ResponseEntity.ok(java.util.Map.of("ok", true));
    }

    @DeleteMapping("/messages")
    public ResponseEntity<?> eliminarTodosLosMensajes(Authentication authentication) {
        return ResponseEntity.ok(crmWhatsappChatService.eliminarTodosLosMensajes(currentUser(authentication)));
    }

    @DeleteMapping("/conversations")
    public ResponseEntity<?> eliminarTodoElHistorial(
            @RequestParam(value = "area", required = false) String area,
            Authentication authentication) {
        return ResponseEntity.ok(crmWhatsappChatService.eliminarTodosLosMensajes(currentUser(authentication), area));
    }

    @GetMapping("/conversations/{id}/tags")
    public ResponseEntity<?> listarEtiquetasConversacion(
            @PathVariable("id") Long id,
            Authentication authentication) {
        return ResponseEntity.ok(crmWhatsappChatService.listarEtiquetasConversacion(id, currentUser(authentication)));
    }

    @PostMapping("/conversations/{id}/tags/{tagId}")
    public ResponseEntity<?> asignarEtiquetaConversacion(
            @PathVariable("id") Long id,
            @PathVariable("tagId") Long tagId,
            Authentication authentication) {
        return ResponseEntity.ok(crmWhatsappChatService.asignarEtiquetaConversacion(id, tagId, currentUser(authentication)));
    }

    @DeleteMapping("/conversations/{id}/tags/{tagId}")
    public ResponseEntity<?> quitarEtiquetaConversacion(
            @PathVariable("id") Long id,
            @PathVariable("tagId") Long tagId,
            Authentication authentication) {
        return ResponseEntity.ok(crmWhatsappChatService.quitarEtiquetaConversacion(id, tagId, currentUser(authentication)));
    }

    @GetMapping("/conversations/{id}/quick-sale")
    public ResponseEntity<?> obtenerVentaRapida(@PathVariable("id") Long id, Authentication authentication) {
        return ResponseEntity.ok(crmWhatsappChatService.obtenerVentaRapidaContexto(id, currentUser(authentication)));
    }

    @PostMapping("/conversations/{id}/client-link")
    public ResponseEntity<?> vincularCliente(
            @PathVariable("id") Long id,
            @RequestBody LinkClienteRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(crmWhatsappChatService.vincularCliente(id, request, currentUser(authentication)));
    }

    @PostMapping("/conversations/{id}/client-register")
    public ResponseEntity<?> registrarCliente(
            @PathVariable("id") Long id,
            @RequestBody ClienteCreateRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(crmWhatsappChatService.registrarYVincularCliente(id, request, currentUser(authentication)));
    }

    @PutMapping("/conversations/{id}/client")
    public ResponseEntity<?> actualizarClienteConversacion(
            @PathVariable("id") Long id,
            @RequestBody ClienteUpdateRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(crmWhatsappChatService.actualizarClienteConversacion(id, request, currentUser(authentication)));
    }

    @GetMapping("/conversations/{id}/sales")
    public ResponseEntity<?> listarVentasCliente(
            @PathVariable("id") Long id,
            @RequestParam(name = "idSucursal", required = false) Integer idSucursal,
            @RequestParam(name = "estado", required = false) String estado,
            @RequestParam(name = "page", defaultValue = "0") int page,
            Authentication authentication) {
        return ResponseEntity.ok(crmWhatsappChatService.listarVentasCliente(
                id, idSucursal, estado, page, currentUser(authentication)));
    }

    @GetMapping("/conversations/{id}/sale-catalog")
    public ResponseEntity<?> listarCatalogoVenta(
            @PathVariable("id") Long id,
            @RequestParam(name = "idSucursal", required = false) Integer idSucursal,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "idCategoria", required = false) Integer idCategoria,
            @RequestParam(name = "idColor", required = false) Integer idColor,
            @RequestParam(name = "conOferta", required = false) Boolean conOferta,
            @RequestParam(name = "soloDisponibles", required = false) Boolean soloDisponibles,
            @RequestParam(name = "view", required = false) String view,
            @RequestParam(name = "page", defaultValue = "0") int page,
            Authentication authentication) {
        return ResponseEntity.ok(crmWhatsappChatService.listarCatalogoVenta(
                id,
                idSucursal,
                q,
                idCategoria,
                idColor,
                conOferta,
                soloDisponibles,
                view,
                page,
                currentUser(authentication)));
    }

    @GetMapping("/conversations/{id}/sale-products/{idProducto}")
    public ResponseEntity<?> obtenerProductoVentaDetalle(
            @PathVariable("id") Long id,
            @PathVariable("idProducto") Integer idProducto,
            Authentication authentication) {
        return ResponseEntity.ok(crmWhatsappChatService.obtenerProductoVentaDetalle(id, idProducto, currentUser(authentication)));
    }

    @GetMapping("/conversations/{id}/sale-options")
    public ResponseEntity<?> obtenerOpcionesVenta(
            @PathVariable("id") Long id,
            @RequestParam(name = "idSucursal", required = false) Integer idSucursal,
            Authentication authentication) {
        return ResponseEntity.ok(crmWhatsappChatService.obtenerOpcionesVenta(id, idSucursal, currentUser(authentication)));
    }

    @PostMapping("/conversations/{id}/sales")
    public ResponseEntity<?> registrarVentaDesdeCrm(
            @PathVariable("id") Long id,
            @RequestBody CrmSaleCreateRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(crmWhatsappChatService.registrarVentaDesdeCrm(id, request, currentUser(authentication)));
    }

    @PostMapping("/conversations/{id}/payment-requests")
    public ResponseEntity<?> crearSolicitudPago(
            @PathVariable("id") Long id,
            @RequestBody PaymentRequestCreateRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(crmWhatsappPaymentEvidenceService.createRequest(id, request, currentUser(authentication)));
    }

    @GetMapping("/conversations/{id}/payment-requests/active")
    public ResponseEntity<?> obtenerSolicitudPagoActiva(
            @PathVariable("id") Long id,
            Authentication authentication) {
        return ResponseEntity.ok(crmWhatsappPaymentEvidenceService.getActiveRequest(id, currentUser(authentication)));
    }

    @PostMapping("/payment-requests/{requestId}/sales")
    public ResponseEntity<?> completarVentaReservada(
            @PathVariable Long requestId,
            @RequestBody CrmSaleCreateRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(crmWhatsappPaymentEvidenceService.completeReservedSale(
                requestId, request, currentUser(authentication)));
    }

    @GetMapping("/conversations/{id}/payment-evidences")
    public ResponseEntity<?> listarEvidenciasPago(
            @PathVariable("id") Long id,
            Authentication authentication) {
        return ResponseEntity.ok(crmWhatsappPaymentEvidenceService.list(id, currentUser(authentication)));
    }

    @GetMapping("/payment-evidences/{evidenceId}")
    public ResponseEntity<?> obtenerEvidenciaPago(
            @PathVariable Long evidenceId,
            Authentication authentication) {
        return ResponseEntity.ok(crmWhatsappPaymentEvidenceService.get(evidenceId, currentUser(authentication)));
    }

    @PostMapping("/payment-evidences/{evidenceId}/decision")
    public ResponseEntity<?> decidirEvidenciaPago(
            @PathVariable Long evidenceId,
            @RequestBody CrmWhatsappPaymentEvidenceService.PaymentDecisionRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(crmWhatsappPaymentEvidenceService.decide(
                evidenceId, request, currentUser(authentication)));
    }

    @GetMapping(value = "/payment-evidences/{evidenceId}/media", produces = MediaType.ALL_VALUE)
    public ResponseEntity<byte[]> descargarEvidenciaPago(
            @PathVariable Long evidenceId,
            Authentication authentication) {
        PaymentEvidenceDownload media = crmWhatsappPaymentEvidenceService.download(evidenceId, currentUser(authentication));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(media.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + media.fileName().replace("\"", "") + "\"")
                .body(media.bytes());
    }

    @GetMapping("/film-evidences/{evidenceId}")
    public ResponseEntity<?> obtenerEvidenciaFilm(@PathVariable Long evidenceId,Authentication authentication){return ResponseEntity.ok(crmWhatsappFilmReviewService.get(evidenceId,currentUser(authentication)));}

    @PostMapping("/film-evidences/{evidenceId}/decision")
    public ResponseEntity<?> decidirEvidenciaFilm(@PathVariable Long evidenceId,@RequestBody CrmWhatsappFilmReviewService.DecisionRequest request,Authentication authentication){return ResponseEntity.ok(crmWhatsappFilmReviewService.decide(evidenceId,request,currentUser(authentication)));}

    @PostMapping("/film-addons/{addonId}/reactivate")
    public ResponseEntity<?> reactivarCobroFilm(@PathVariable Long addonId,Authentication authentication){return ResponseEntity.ok(crmWhatsappFilmReviewService.reactivate(addonId,currentUser(authentication)));}

    @PostMapping("/film-addons/{addonId}/manual-confirm")
    public ResponseEntity<?> confirmarFilmManualmente(@PathVariable Long addonId,
            @RequestBody(required=false) CrmWhatsappFilmReviewService.ManualConfirmationRequest request,
            Authentication authentication){return ResponseEntity.ok(crmWhatsappFilmReviewService.confirmManually(addonId,request,currentUser(authentication)));}

    @GetMapping(value="/film-evidences/{evidenceId}/media",produces=MediaType.ALL_VALUE)
    public ResponseEntity<byte[]> descargarEvidenciaFilm(@PathVariable Long evidenceId,Authentication authentication){var media=crmWhatsappFilmReviewService.download(evidenceId,currentUser(authentication));return ResponseEntity.ok().contentType(MediaType.parseMediaType(media.contentType())).header(HttpHeaders.CONTENT_DISPOSITION,"inline; filename=\""+media.fileName().replace("\"","")+"\"").body(media.bytes());}

    @GetMapping(value = "/payment-evidences/{evidenceId}/sales-note", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> descargarNotaVentaDeEvidencia(
            @PathVariable Long evidenceId,
            Authentication authentication) {
        PaymentEvidenceDownload file = crmWhatsappPaymentEvidenceService
                .downloadSalesNote(evidenceId, currentUser(authentication));
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + file.fileName().replace("\"", "") + "\"")
                .body(file.bytes());
    }

    @PostMapping("/messages/{messageId}/payment-evidence/analyze")
    public ResponseEntity<?> analizarEvidenciaPago(
            @PathVariable Long messageId,
            Authentication authentication) {
        return ResponseEntity.accepted().body(
                crmWhatsappPaymentEvidenceService.analyzeMessage(messageId, currentUser(authentication)));
    }

    @PostMapping("/conversations/{id}/accept")
    public ResponseEntity<?> aceptar(@PathVariable("id") Long id, Authentication authentication) {
        return ResponseEntity.ok(crmWhatsappChatService.aceptar(id, currentUser(authentication)));
    }

    @PostMapping("/conversations/{id}/enable-agent")
    public ResponseEntity<?> habilitarAgente(@PathVariable("id") Long id, Authentication authentication) {
        return ResponseEntity.ok(crmWhatsappChatService.habilitarAgente(id, currentUser(authentication)));
    }

    @PostMapping("/conversations/{id}/transfer")
    public ResponseEntity<?> transferir(
            @PathVariable("id") Long id,
            @RequestBody TransferRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(crmWhatsappChatService.transferir(id, request, currentUser(authentication)));
    }

    @PostMapping("/conversations/{id}/messages")
    public ResponseEntity<?> enviarMensaje(
            @PathVariable("id") Long id,
            @RequestBody SendMessageRequest request,
            Authentication authentication) {
        Usuario actor = currentUser(authentication);
        var message = crmWhatsappChatService.enviarMensaje(id, request, actor);
        return ResponseEntity.ok(message);
    }

    @PostMapping(value = "/conversations/{id}/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> enviarMedia(
            @PathVariable("id") Long id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "caption", required = false) String caption,
            @RequestParam(name = "replyToMessageId", required = false) Long replyToMessageId,
            Authentication authentication) {
        return ResponseEntity.ok(crmWhatsappChatService.enviarMedia(id, file, caption, replyToMessageId, currentUser(authentication)));
    }

    @PostMapping("/conversations/{id}/sales/{saleId}/receipt")
    public ResponseEntity<?> enviarComprobanteVenta(
            @PathVariable("id") Long id,
            @PathVariable Integer saleId,
            @RequestBody ReceiptRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(crmWhatsappChatService.enviarComprobanteVenta(
                id, saleId, request, currentUser(authentication)));
    }

    @DeleteMapping("/conversations/{conversationId}/messages/{messageId}")
    public ResponseEntity<?> eliminarMensaje(
            @PathVariable("conversationId") Long conversationId,
            @PathVariable("messageId") Long messageId,
            Authentication authentication) {
        return ResponseEntity.ok(crmWhatsappChatService.eliminarMensaje(conversationId, messageId, currentUser(authentication)));
    }

    @DeleteMapping("/conversations/{conversationId}")
    public ResponseEntity<Void> eliminarConversacion(
            @PathVariable("conversationId") Long conversationId,
            Authentication authentication) {
        crmWhatsappChatService.eliminarConversacion(conversationId, currentUser(authentication));
        return ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/messages/{id}/media", produces = MediaType.ALL_VALUE)
    public ResponseEntity<byte[]> descargarMedia(@PathVariable("id") Long id, Authentication authentication) {
        MediaDownload media = crmWhatsappChatService.descargarMedia(id, currentUser(authentication));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(media.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + media.fileName().replace("\"", "") + "\"")
                .body(media.bytes());
    }

    @PostMapping("/conversations/{id}/resolve")
    public ResponseEntity<?> resolver(@PathVariable("id") Long id, Authentication authentication) {
        return ResponseEntity.ok(crmWhatsappChatService.resolver(id, currentUser(authentication)));
    }

    @PostMapping("/conversations/{id}/reopen")
    public ResponseEntity<?> reabrir(@PathVariable("id") Long id, Authentication authentication) {
        return ResponseEntity.ok(crmWhatsappChatService.reabrir(id, currentUser(authentication)));
    }

    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> eventos(Authentication authentication) {
        SseEmitter emitter = crmWhatsappChatService.suscribirEventos(currentUser(authentication));
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-transform")
                .header("X-Accel-Buffering", "no")
                .body(emitter);
    }

    private QuickMessageRequest readQuickMessageRequest(String payload) {
        try {
            return objectMapper.readValue(payload, QuickMessageRequest.class);
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El contenido del comando no es valido");
        }
    }

    private Usuario currentUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUser customUser)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autorizado");
        }
        return customUser.getUsuario();
    }

    private CrmWhatsappArea parseArea(String value) {
        try {
            return CrmWhatsappArea.from(value);
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Area de WhatsApp invalida");
        }
    }

    private Usuario requireAdmin(Authentication authentication) {
        Usuario actor = currentUser(authentication);
        if (actor.getRol() != Rol.ADMINISTRADOR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo un administrador puede administrar WhatsApp");
        }
        return actor;
    }
}
