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

import com.sistemapos.sistematextil.services.CrmWhatsappChatService;
import com.sistemapos.sistematextil.services.CrmWhatsappBridgeService;
import com.sistemapos.sistematextil.services.CrmWhatsappConnectionService;
import com.sistemapos.sistematextil.services.CrmWhatsappConnectionService.BranchRequest;
import com.sistemapos.sistematextil.services.CrmWhatsappConnectionStateService.BridgeConnectionState;
import com.sistemapos.sistematextil.services.CrmWhatsappAiConfigService;
import com.sistemapos.sistematextil.services.CrmWhatsappAiJobService;
import com.sistemapos.sistematextil.services.CrmWhatsappAiJobService.DraftRequest;
import com.sistemapos.sistematextil.services.CrmWhatsappAiFeedbackService;
import com.sistemapos.sistematextil.services.CrmWhatsappAiFeedbackService.DecisionRequest;
import com.sistemapos.sistematextil.services.CrmWhatsappAiCredentialService;
import com.sistemapos.sistematextil.services.CrmWhatsappAiMemoryService;
import com.sistemapos.sistematextil.services.CrmWhatsappAiSaleDraftService;
import com.sistemapos.sistematextil.services.CrmWhatsappAiReportService;
import com.sistemapos.sistematextil.services.CrmWhatsappAiOperationsService;
import com.sistemapos.sistematextil.services.CrmWhatsappAiKnowledgeService;
import com.sistemapos.sistematextil.services.CrmWhatsappAiDeliveryService;
import com.sistemapos.sistematextil.services.CrmWhatsappAiKnowledgeService.ArticleRequest;
import com.sistemapos.sistematextil.services.CrmWhatsappAiKnowledgeService.TestRequest;
import com.sistemapos.sistematextil.services.CrmWhatsappAiSaleDraftService.RevalidateRequest;
import com.sistemapos.sistematextil.services.CrmWhatsappAiSaleDraftService.SaleDraftUpdateRequest;
import com.sistemapos.sistematextil.services.CrmWhatsappAiSaleDraftService.CustomerNameSuggestionRequest;
import com.sistemapos.sistematextil.services.CrmWhatsappPaymentEvidenceService;
import com.sistemapos.sistematextil.services.CrmWhatsappPaymentEvidenceService.PaymentRequestCreateRequest;
import com.sistemapos.sistematextil.services.CrmWhatsappPaymentEvidenceService.PaymentEvidenceDownload;
import com.sistemapos.sistematextil.services.CrmWhatsappAiCredentialService.CredentialRequest;
import com.sistemapos.sistematextil.services.ai.AiModelProvider;
import com.sistemapos.sistematextil.services.ai.AiProviderException;
import com.sistemapos.sistematextil.services.CrmWhatsappAiConfigService.AiConfigRequest;
import com.sistemapos.sistematextil.model.CustomUser;
import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.services.CrmWhatsappChatService.MediaDownload;
import com.sistemapos.sistematextil.services.CrmWhatsappChatService.CrmSaleCreateRequest;
import com.sistemapos.sistematextil.services.CrmWhatsappChatService.CrmTagRequest;
import com.sistemapos.sistematextil.services.CrmWhatsappChatService.LinkClienteRequest;
import com.sistemapos.sistematextil.services.CrmWhatsappChatService.SendMessageRequest;
import com.sistemapos.sistematextil.services.CrmWhatsappChatService.ReceiptRequest;
import com.sistemapos.sistematextil.services.CrmWhatsappChatService.TransferRequest;
import com.sistemapos.sistematextil.services.CrmWhatsappChatService.AttentionModeRequest;
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
    private final CrmWhatsappAiConfigService crmWhatsappAiConfigService;
    private final CrmWhatsappAiJobService crmWhatsappAiJobService;
    private final CrmWhatsappAiFeedbackService crmWhatsappAiFeedbackService;
    private final CrmWhatsappAiCredentialService crmWhatsappAiCredentialService;
    private final CrmWhatsappAiMemoryService crmWhatsappAiMemoryService;
    private final CrmWhatsappPaymentEvidenceService crmWhatsappPaymentEvidenceService;
    private final CrmWhatsappAiSaleDraftService crmWhatsappAiSaleDraftService;
    private final AiModelProvider aiModelProvider;
    private final CrmWhatsappAiReportService crmWhatsappAiReportService;
    private final CrmWhatsappAiOperationsService crmWhatsappAiOperationsService;
    private final CrmWhatsappAiKnowledgeService crmWhatsappAiKnowledgeService;
    private final CrmWhatsappAiDeliveryService crmWhatsappAiDeliveryService;

    @GetMapping("/status")
    public ResponseEntity<?> obtenerEstado(Authentication authentication) {
        return crmWhatsappConnectionService.obtenerEstado(currentUser(authentication));
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

    @GetMapping("/connection/ai-config")
    public ResponseEntity<?> obtenerConfiguracionIa(Authentication authentication) {
        return ResponseEntity.ok(crmWhatsappAiConfigService.obtener(currentUser(authentication)));
    }

    @PutMapping("/connection/ai-config")
    public ResponseEntity<?> guardarConfiguracionIa(
            @RequestBody AiConfigRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(crmWhatsappAiConfigService.guardar(request, currentUser(authentication)));
    }

    @PostMapping("/connection/ai-control")
    public ResponseEntity<?> controlarIa(
            @RequestBody CrmWhatsappAiOperationsService.ControlRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(crmWhatsappAiOperationsService.control(request, currentUser(authentication)));
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
            return ResponseEntity.ok(aiModelProvider.testConnection(connectionId));
        } catch (AiProviderException error) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, error.getMessage());
        }
    }

    @GetMapping("/connection/ai-knowledge")
    public ResponseEntity<?> listarConocimientoIa(
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "category", required = false) String category,
            Authentication authentication) {
        return ResponseEntity.ok(crmWhatsappAiKnowledgeService.list(query, category, currentUser(authentication)));
    }

    @PostMapping("/connection/ai-knowledge")
    public ResponseEntity<?> crearConocimientoIa(
            @RequestBody ArticleRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(crmWhatsappAiKnowledgeService.create(request, currentUser(authentication)));
    }

    @PutMapping("/connection/ai-knowledge/{articleId}")
    public ResponseEntity<?> actualizarConocimientoIa(
            @PathVariable Long articleId,
            @RequestBody ArticleRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(crmWhatsappAiKnowledgeService.update(
                articleId, request, currentUser(authentication)));
    }

    @DeleteMapping("/connection/ai-knowledge/{articleId}")
    public ResponseEntity<?> eliminarConocimientoIa(
            @PathVariable Long articleId,
            Authentication authentication) {
        crmWhatsappAiKnowledgeService.delete(articleId, currentUser(authentication));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/connection/ai-knowledge/{articleId}/reindex")
    public ResponseEntity<?> reindexarConocimientoIa(
            @PathVariable Long articleId,
            Authentication authentication) {
        return ResponseEntity.accepted().body(crmWhatsappAiKnowledgeService.reindex(
                articleId, currentUser(authentication)));
    }

    @PostMapping("/connection/ai-knowledge/test")
    public ResponseEntity<?> probarConocimientoIa(
            @RequestBody TestRequest request,
            Authentication authentication) {
        try {
            return ResponseEntity.ok(crmWhatsappAiKnowledgeService.test(request, currentUser(authentication)));
        } catch (AiProviderException error) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, error.getMessage());
        }
    }

    @PostMapping("/conversations/{id}/ai/drafts")
    public ResponseEntity<?> solicitarBorradorIa(
            @PathVariable("id") Long id,
            @RequestBody(required = false) DraftRequest request,
            Authentication authentication) {
        return ResponseEntity.accepted().body(
                crmWhatsappAiJobService.enqueueManual(id, request, currentUser(authentication)));
    }

    @GetMapping("/conversations/{id}/ai/runs")
    public ResponseEntity<?> listarEjecucionesIa(
            @PathVariable("id") Long id,
            Authentication authentication) {
        return ResponseEntity.ok(crmWhatsappAiJobService.listRuns(id, currentUser(authentication)));
    }

    @GetMapping("/conversations/{id}/ai/memory")
    public ResponseEntity<?> obtenerMemoriaIa(
            @PathVariable("id") Long id,
            Authentication authentication) {
        return ResponseEntity.ok(crmWhatsappAiMemoryService.get(id, currentUser(authentication)));
    }

    @GetMapping("/conversations/{id}/ai/attention")
    public ResponseEntity<?> obtenerAtencionIa(
            @PathVariable("id") Long id,
            Authentication authentication) {
        return ResponseEntity.ok(crmWhatsappChatService.obtenerAtencion(id, currentUser(authentication)));
    }

    @PutMapping("/conversations/{id}/ai/attention")
    public ResponseEntity<?> cambiarAtencionIa(
            @PathVariable("id") Long id,
            @RequestBody AttentionModeRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(crmWhatsappChatService.cambiarAtencion(id, request, currentUser(authentication)));
    }

    @DeleteMapping("/conversations/{id}/ai/memory")
    public ResponseEntity<?> limpiarMemoriaIa(
            @PathVariable("id") Long id,
            Authentication authentication) {
        return ResponseEntity.ok(crmWhatsappAiMemoryService.clear(id, currentUser(authentication)));
    }

    @GetMapping("/conversations/{id}/ai/sale-draft")
    public ResponseEntity<?> obtenerPedidoIa(@PathVariable("id") Long id, Authentication authentication) {
        var draft = crmWhatsappAiSaleDraftService.get(id, currentUser(authentication));
        return draft == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(draft);
    }

    @PostMapping("/conversations/{id}/ai/sale-draft/revalidate")
    public ResponseEntity<?> revalidarPedidoIa(
            @PathVariable("id") Long id,
            @RequestBody(required = false) RevalidateRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(crmWhatsappAiSaleDraftService.revalidate(id, request, currentUser(authentication)));
    }

    @PostMapping("/conversations/{id}/ai/sale-draft/request-confirmation")
    public ResponseEntity<?> solicitarConfirmacionPedidoIa(
            @PathVariable("id") Long id,
            @RequestBody(required = false) SaleDraftUpdateRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(crmWhatsappAiSaleDraftService.requestConfirmation(id, request, currentUser(authentication)));
    }

    @DeleteMapping("/conversations/{id}/ai/sale-draft")
    public ResponseEntity<?> cancelarPedidoIa(@PathVariable("id") Long id, Authentication authentication) {
        return ResponseEntity.ok(crmWhatsappAiSaleDraftService.cancel(id, currentUser(authentication)));
    }

    @PostMapping("/conversations/{id}/ai/sale-draft/customer-name-suggestion")
    public ResponseEntity<?> decidirSugerenciaNombreCliente(
            @PathVariable("id") Long id,
            @RequestBody CustomerNameSuggestionRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(crmWhatsappAiSaleDraftService.decideCustomerNameSuggestion(
                id, request, currentUser(authentication)));
    }

    @PostMapping("/conversations/{id}/ai/runs/{runId}/decision")
    public ResponseEntity<?> decidirBorradorIa(
            @PathVariable("id") Long id,
            @PathVariable("runId") Long runId,
            @RequestBody DecisionRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(crmWhatsappAiFeedbackService.decide(
                id, runId, request, currentUser(authentication)));
    }

    @GetMapping("/qr")
    public ResponseEntity<String> obtenerQr() {
        return crmWhatsappBridgeService.obtenerQr();
    }

    @PostMapping("/logout")
    public ResponseEntity<String> cerrarSesion() {
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
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size,
            Authentication authentication) {
        if (q != null || tagId != null || page != null || size != null || view != null) {
            return ResponseEntity.ok(crmWhatsappChatService.listarConversacionesPaginadas(
                    status,
                    view,
                    q,
                    tagId,
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

    @GetMapping("/conversations/{id}")
    public ResponseEntity<?> obtenerConversacion(
            @PathVariable("id") Long id,
            Authentication authentication) {
        return ResponseEntity.ok(crmWhatsappChatService.obtenerConversacion(id, currentUser(authentication)));
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

    @GetMapping("/reports/ai")
    public ResponseEntity<?> obtenerReportesIa(
            @RequestParam(name = "filtro", required = false) String filtro,
            @RequestParam(name = "desde", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(name = "hasta", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            Authentication authentication) {
        return ResponseEntity.ok(crmWhatsappAiReportService.report(filtro, desde, hasta, currentUser(authentication)));
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
    public ResponseEntity<?> eliminarTodoElHistorial(Authentication authentication) {
        return ResponseEntity.ok(crmWhatsappChatService.eliminarTodosLosMensajes(currentUser(authentication)));
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
            @RequestParam(name = "page", defaultValue = "0") int page,
            Authentication authentication) {
        return ResponseEntity.ok(crmWhatsappChatService.listarVentasCliente(id, idSucursal, page, currentUser(authentication)));
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
        crmWhatsappAiDeliveryService.completeHumanDraft(
                id, request == null ? null : request.pendingDeliveryId(), message.id(), actor);
        return ResponseEntity.ok(message);
    }

    @GetMapping("/conversations/{id}/pending-message")
    public ResponseEntity<?> obtenerBorradorPendiente(
            @PathVariable("id") Long id,
            Authentication authentication) {
        var draft = crmWhatsappAiDeliveryService.pendingHumanDraft(id, currentUser(authentication));
        return draft == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(draft);
    }

    @DeleteMapping("/conversations/{id}/pending-message/{deliveryId}")
    public ResponseEntity<Void> descartarBorradorPendiente(
            @PathVariable("id") Long id,
            @PathVariable Long deliveryId,
            Authentication authentication) {
        crmWhatsappAiDeliveryService.discardHumanDraft(id, deliveryId, currentUser(authentication));
        return ResponseEntity.noContent().build();
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
    public SseEmitter eventos(Authentication authentication) {
        return crmWhatsappChatService.suscribirEventos(currentUser(authentication));
    }

    private Usuario currentUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUser customUser)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autorizado");
        }
        return customUser.getUsuario();
    }
}
