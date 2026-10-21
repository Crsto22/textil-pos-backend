package com.sistemapos.sistematextil.controllers;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.http.HttpStatus;

import com.sistemapos.sistematextil.model.CustomUser;
import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.services.CrmWarehouseService;
import com.sistemapos.sistematextil.services.CrmWarehouseEventService;
import com.sistemapos.sistematextil.services.CrmWarehouseService.MessageRequest;
import com.sistemapos.sistematextil.services.CrmWarehouseService.StatusRequest;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping(value = "api/crm/warehouse", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class CrmWarehouseController {
    private final CrmWarehouseService service;
    private final CrmWarehouseEventService eventService;

    @GetMapping("/orders")
    public ResponseEntity<?> list(@RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(defaultValue = "LATEST") String order,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        return ResponseEntity.ok(service.list(currentUser(authentication), q, status, order, page, size));
    }

    @GetMapping("/orders/{saleId}")
    public ResponseEntity<?> get(@PathVariable Integer saleId, Authentication authentication) {
        return ResponseEntity.ok(service.get(saleId, currentUser(authentication)));
    }

    @PostMapping("/orders/{saleId}/shipping-conversation")
    public ResponseEntity<?> openShippingConversation(@PathVariable Integer saleId,
            Authentication authentication) {
        return ResponseEntity.ok(service.openShippingConversation(saleId, currentUser(authentication)));
    }

    @PatchMapping("/orders/{saleId}/status")
    public ResponseEntity<?> status(@PathVariable Integer saleId, @RequestBody StatusRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(service.updateStatus(saleId, request, currentUser(authentication)));
    }

    @GetMapping("/orders/{saleId}/sales-messages")
    public ResponseEntity<?> salesMessages(@PathVariable Integer saleId,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) Long afterId,
            @RequestParam(defaultValue = "10") int limit,
            Authentication authentication) {
        return ResponseEntity.ok(service.salesMessages(saleId, beforeId, afterId, limit,
                currentUser(authentication)));
    }

    @GetMapping("/orders/{saleId}/shipping-messages")
    public ResponseEntity<?> shippingMessages(@PathVariable Integer saleId,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) Long afterId,
            @RequestParam(defaultValue = "10") int limit,
            Authentication authentication) {
        return ResponseEntity.ok(service.shippingMessages(saleId, beforeId, afterId, limit,
                currentUser(authentication)));
    }

    @PostMapping("/orders/{saleId}/shipping-messages")
    public ResponseEntity<?> sendMessage(@PathVariable Integer saleId, @RequestBody MessageRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(service.sendMessage(saleId, request, currentUser(authentication)));
    }

    @PostMapping(value = "/orders/{saleId}/shipping-media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> sendMedia(@PathVariable Integer saleId, @RequestParam MultipartFile file,
            @RequestParam(defaultValue = "") String caption, Authentication authentication) {
        return ResponseEntity.ok(service.sendMedia(saleId, file, caption, currentUser(authentication)));
    }

    @GetMapping(value = "/messages/{messageId}/media", produces = MediaType.ALL_VALUE)
    public ResponseEntity<byte[]> media(@PathVariable Long messageId, Authentication authentication) {
        var media = service.downloadMedia(messageId, currentUser(authentication));
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(media.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + media.fileName().replace("\"", "") + "\"")
                .body(media.bytes());
    }

    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> events(Authentication authentication) {
        Usuario actor = currentUser(authentication);
        if (actor.getRol() == null || !actor.getRol().permiteAlmacen()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso exclusivo para Envios");
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-transform")
                .header("X-Accel-Buffering", "no")
                .body(eventService.subscribe());
    }

    private Usuario currentUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUser customUser)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autorizado");
        }
        return customUser.getUsuario();
    }
}
