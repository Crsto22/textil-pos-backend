package com.sistemapos.sistematextil.controllers;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import com.sistemapos.sistematextil.model.CustomUser;
import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.services.CrmShippingChatService;
import com.sistemapos.sistematextil.services.CrmShippingChatService.MessageRequest;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping(value = "api/crm/whatsapp/shipping", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class CrmShippingChatController {
    private final CrmShippingChatService service;

    @GetMapping("/conversations")
    public ResponseEntity<?> conversations(@RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication) {
        return ResponseEntity.ok(service.conversations(currentUser(authentication), q, page, size));
    }

    @GetMapping("/conversations/{id}")
    public ResponseEntity<?> conversation(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(service.conversation(id, currentUser(authentication)));
    }

    @GetMapping("/conversations/{id}/messages")
    public ResponseEntity<?> messages(@PathVariable Long id,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) Long afterId,
            @RequestParam(defaultValue = "10") int limit,
            Authentication authentication) {
        return ResponseEntity.ok(service.messages(id, beforeId, afterId, limit, currentUser(authentication)));
    }

    @GetMapping("/conversations/{id}/sales-messages")
    public ResponseEntity<?> salesMessages(@PathVariable Long id,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) Long afterId,
            @RequestParam(defaultValue = "10") int limit,
            Authentication authentication) {
        return ResponseEntity.ok(service.salesMessages(id, beforeId, afterId, limit, currentUser(authentication)));
    }

    @PostMapping("/conversations/{id}/messages")
    public ResponseEntity<?> sendText(@PathVariable Long id, @RequestBody MessageRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(service.sendText(id, request, currentUser(authentication)));
    }

    @PostMapping(value = "/conversations/{id}/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> sendMedia(@PathVariable Long id, @RequestParam MultipartFile file,
            @RequestParam(defaultValue = "") String caption, Authentication authentication) {
        return ResponseEntity.ok(service.sendMedia(id, file, caption, currentUser(authentication)));
    }

    @GetMapping(value = "/messages/{id}/media", produces = MediaType.ALL_VALUE)
    public ResponseEntity<byte[]> media(@PathVariable Long id, Authentication authentication) {
        var media = service.media(id, currentUser(authentication));
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(media.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + media.fileName().replace("\"", "") + "\"")
                .body(media.bytes());
    }

    private Usuario currentUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUser customUser)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autorizado");
        }
        return customUser.getUsuario();
    }
}
