package com.sistemapos.sistematextil.services;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sistemapos.sistematextil.model.CrmWhatsappArea;
import com.sistemapos.sistematextil.model.CrmWhatsappConversation;
import com.sistemapos.sistematextil.model.CrmWhatsappMessage;
import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.repositories.CrmWhatsappConversationRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappMessageRepository;
import com.sistemapos.sistematextil.repositories.CrmWarehouseOrderRepository;
import com.sistemapos.sistematextil.repositories.SucursalRepository;
import com.sistemapos.sistematextil.util.usuario.Rol;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CrmShippingChatService {
    private final CrmWhatsappConversationRepository conversationRepository;
    private final CrmWhatsappMessageRepository messageRepository;
    private final CrmWarehouseOrderRepository warehouseOrderRepository;
    private final SucursalRepository sucursalRepository;
    private final CrmWhatsappBridgeService bridgeService;
    private final CrmWhatsappNumberChangeNoticeService numberChangeNoticeService;
    private final CrmWarehouseEventService warehouseEventService;
    private final S3StorageService storageService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional(readOnly = true)
    public ConversationPage conversations(Usuario actor, String query, int page, int size) {
        requireShippingAccess(actor);
        String term = clean(query);
        var result = conversationRepository.findShippingChatConversations(
                companyId(actor), branchId(actor), term.isBlank() ? null : term,
                PageRequest.of(Math.max(0, page), Math.min(10, Math.max(1, size))));
        return new ConversationPage(result.getContent().stream().map(this::conversation).toList(),
                result.getNumber(), result.getSize(), result.getTotalPages(), result.getTotalElements(),
                result.isLast());
    }

    @Transactional(readOnly = true)
    public ConversationDto conversation(Long conversationId, Usuario actor) {
        return conversation(requireConversation(conversationId, actor));
    }

    @Transactional
    public MessagePage messages(Long conversationId, Long beforeId, Long afterId, int requestedLimit, Usuario actor) {
        CrmWhatsappConversation conversation = requireConversation(conversationId, actor);
        MessagePage page = messagePage(conversation, beforeId, afterId, requestedLimit,
                "/api/crm/whatsapp/shipping/messages/");
        if (conversation.getUnreadCount() != null && conversation.getUnreadCount() > 0) {
            conversation.setUnreadCount(0);
            conversationRepository.save(conversation);
        }
        return page;
    }

    @Transactional(readOnly = true)
    public SalesMessagePage salesMessages(Long conversationId, Long beforeId, Long afterId,
            int requestedLimit, Usuario actor) {
        CrmWhatsappConversation shippingConversation = requireConversation(conversationId, actor);
        String phone = clean(shippingConversation.getPhone());
        String phoneNumber = clean(shippingConversation.getPhoneNumber());
        if (phoneNumber.isBlank()) phoneNumber = clean(shippingConversation.getWhatsappUsername());
        if (phoneNumber.isBlank()) phoneNumber = phone.replaceFirst("@.+$", "");
        Integer clientId = shippingConversation.getCliente() == null
                ? null : shippingConversation.getCliente().getIdCliente();
        CrmWhatsappConversation salesConversation = conversationRepository.findSalesConversationForShippingContact(
                companyId(actor), clientId, phone, phoneNumber, PageRequest.of(0, 1)).stream()
                .findFirst().orElse(null);
        if (salesConversation == null) {
            return new SalesMessagePage(List.of(), null, null, false, false);
        }
        MessagePage page = messagePage(salesConversation, beforeId, afterId, requestedLimit,
                "/api/crm/whatsapp/messages/");
        return new SalesMessagePage(page.content(), page.oldestId(), page.newestId(), page.hasMoreBefore(), true);
    }

    private MessagePage messagePage(CrmWhatsappConversation conversation, Long beforeId, Long afterId,
            int requestedLimit, String mediaBasePath) {
        if (beforeId != null && afterId != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "beforeId y afterId no pueden combinarse");
        }
        int limit = Math.min(10, Math.max(1, requestedLimit));
        List<CrmWhatsappMessage> selected;
        boolean hasMoreBefore;
        if (afterId != null) {
            selected = messageRepository.findMessagesAfter(
                    conversation.getIdConversation(), afterId, PageRequest.of(0, limit));
            hasMoreBefore = false;
        } else {
            selected = messageRepository.findMessageHistory(
                    conversation.getIdConversation(), beforeId, PageRequest.of(0, limit + 1));
            hasMoreBefore = selected.size() > limit;
            if (hasMoreBefore) selected = selected.subList(0, limit);
            selected = new ArrayList<>(selected);
            Collections.reverse(selected);
        }
        List<MessageDto> content = selected.stream().map(value -> message(value, mediaBasePath)).toList();
        return new MessagePage(content,
                content.isEmpty() ? null : content.getFirst().id(),
                content.isEmpty() ? null : content.getLast().id(), hasMoreBefore);
    }

    @Transactional
    public MessageDto sendText(Long conversationId, MessageRequest request, Usuario actor) {
        CrmWhatsappConversation conversation = requireConversation(conversationId, actor);
        String body = clean(request == null ? null : request.message());
        if (body.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message es requerido");
        numberChangeNoticeService.registerIfNeeded(conversationId, null);
        ResponseEntity<String> response = bridgeService.enviarTexto(CrmWhatsappArea.SHIPPING,
                conversation.getPhone(), body, null, UUID.randomUUID().toString());
        requireBridgeSuccess(response);
        CrmWhatsappMessage message = outgoing(conversation, body, "TEXT", response.getBody());
        message = messageRepository.save(message);
        touch(conversation, body, "TEXT", message.getCreatedAt());
        publish(conversationId);
        return message(message);
    }

    @Transactional
    public MessageDto sendMedia(Long conversationId, MultipartFile file, String caption, Usuario actor) {
        CrmWhatsappConversation conversation = requireConversation(conversationId, actor);
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file es requerido");
        }
        numberChangeNoticeService.registerIfNeeded(conversationId, null);
        String fileName = safeFileName(file.getOriginalFilename());
        String mime = clean(file.getContentType());
        if (mime.isBlank()) mime = "application/octet-stream";
        YearMonth month = YearMonth.now();
        String storageKey = "crm/whatsapp/shipping/%04d/%02d/%s-%s".formatted(
                month.getYear(), month.getMonthValue(), UUID.randomUUID(), fileName);
        String storagePath = null;
        try {
            byte[] bytes = file.getBytes();
            storagePath = storageService.upload(bytes, storageKey, mime);
            ResponseEntity<String> response = bridgeService.enviarMedia(CrmWhatsappArea.SHIPPING,
                    conversation.getPhone(), bytes, fileName, mime, clean(caption), null,
                    UUID.randomUUID().toString());
            requireBridgeSuccess(response);
            String type = mediaType(mime);
            String body = clean(caption).isBlank() ? fileName : clean(caption);
            CrmWhatsappMessage message = outgoing(conversation, body, type, response.getBody());
            message.setMediaFileName(fileName);
            message.setMediaMimeType(mime);
            message.setMediaStoragePath(storagePath);
            message = messageRepository.save(message);
            touch(conversation, body, type, message.getCreatedAt());
            publish(conversationId);
            return message(message);
        } catch (ResponseStatusException exception) {
            if (storagePath != null) storageService.deleteByKey(storagePath);
            throw exception;
        } catch (Exception exception) {
            if (storagePath != null) storageService.deleteByKey(storagePath);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No se pudo enviar el archivo por WhatsApp");
        }
    }

    @Transactional(readOnly = true)
    public MediaDownload media(Long messageId, Usuario actor) {
        requireShippingAccess(actor);
        CrmWhatsappMessage message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mensaje no encontrado"));
        requireShippingCompany(message.getConversation(), actor);
        requireShippingBranch(message.getConversation(), actor);
        if (message.getDeletedAt() != null || clean(message.getMediaStoragePath()).isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "El mensaje no tiene archivo disponible");
        }
        return new MediaDownload(storageService.readBytes(message.getMediaStoragePath()),
                clean(message.getMediaMimeType()).isBlank() ? "application/octet-stream" : message.getMediaMimeType(),
                safeFileName(message.getMediaFileName()));
    }

    private CrmWhatsappConversation requireConversation(Long id, Usuario actor) {
        requireShippingAccess(actor);
        CrmWhatsappConversation conversation = conversationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversacion no encontrada"));
        requireShippingCompany(conversation, actor);
        requireShippingBranch(conversation, actor);
        return conversation;
    }

    private void requireShippingCompany(CrmWhatsappConversation conversation, Usuario actor) {
        if (conversation.getConnection() == null
                || conversation.getConnection().getArea() != CrmWhatsappArea.SHIPPING
                || conversation.getConnection().getEmpresa() == null
                || !companyId(actor).equals(conversation.getConnection().getEmpresa().getIdEmpresa())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversacion no encontrada");
        }
    }

    private void requireShippingAccess(Usuario actor) {
        if (actor == null || actor.getIdUsuario() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autorizado");
        }
        if (actor.getRol() != Rol.ADMINISTRADOR
                && actor.getRol() != Rol.ALMACEN
                && actor.getRol() != Rol.VENTAS_ALMACEN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso exclusivo para administradores y almacen");
        }
    }

    private void requireShippingBranch(CrmWhatsappConversation conversation, Usuario actor) {
        Integer branchId = branchId(actor);
        if (branchId != null && !warehouseOrderRepository.canAccessConversation(
                companyId(actor), branchId, conversation.getIdConversation())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversacion no encontrada");
        }
    }

    private Integer branchId(Usuario actor) {
        if (actor.getRol() == Rol.ADMINISTRADOR) return null;
        if (actor.getSucursal() == null || actor.getSucursal().getIdSucursal() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "El usuario no tiene una sucursal asignada");
        }
        return actor.getSucursal().getIdSucursal();
    }

    private Integer companyId(Usuario actor) {
        if (actor.getSucursal() != null && actor.getSucursal().getEmpresa() != null) {
            return actor.getSucursal().getEmpresa().getIdEmpresa();
        }
        return sucursalRepository.findByDeletedAtIsNullAndEstadoOrderByIdSucursalAsc("ACTIVO").stream()
                .filter(branch -> branch.getEmpresa() != null).findFirst()
                .map(branch -> branch.getEmpresa().getIdEmpresa())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "No hay empresa activa asignada"));
    }

    private ConversationDto conversation(CrmWhatsappConversation value) {
        String name = clean(value.getContactName());
        if (name.isBlank() && value.getCliente() != null) name = clean(value.getCliente().getNombres());
        return new ConversationDto(value.getIdConversation(), value.getPhone(), value.getPhoneNumber(),
                value.getWhatsappUsername(), name.isBlank() ? null : name, value.getLastMessage(),
                value.getLastMessageType(), value.getLastMessageAt(), value.getUnreadCount() == null ? 0 : value.getUnreadCount());
    }

    private MessageDto message(CrmWhatsappMessage value) {
        return message(value, "/api/crm/whatsapp/shipping/messages/");
    }

    private MessageDto message(CrmWhatsappMessage value, String mediaBasePath) {
        boolean deleted = value.getDeletedAt() != null;
        return new MessageDto(value.getIdMessage(), value.getDirection(), value.getMessageType(),
                deleted ? "Mensaje eliminado" : value.getBody(), value.getMessageStatus(), value.getCreatedAt(),
                deleted ? null : value.getMediaMimeType(), deleted ? null : value.getMediaFileName(),
                deleted || clean(value.getMediaStoragePath()).isBlank() ? null
                        : mediaBasePath + value.getIdMessage() + "/media",
                deleted ? null : value.getMediaDurationSeconds(), value.getOrigin(), value.getRelatedSaleId(),
                value.getReceiptFormat(), deleted);
    }

    private CrmWhatsappMessage outgoing(CrmWhatsappConversation conversation, String body, String type, String json) {
        CrmWhatsappMessage message = new CrmWhatsappMessage();
        message.setConversation(conversation);
        message.setDirection("OUTGOING");
        message.setOrigin("WAREHOUSE");
        message.setMessageType(type);
        message.setBody(body);
        message.setMessageStatus("sent");
        try {
            JsonNode root = objectMapper.readTree(json == null ? "{}" : json);
            if (root.path("messageId").isTextual()) message.setWhatsappMessageId(root.path("messageId").asText());
            if (!root.path("messageKey").isMissingNode()) message.setMessageKeyJson(root.path("messageKey").toString());
            if (!root.path("baileysMessage").isMissingNode()) message.setBaileysMessageJson(root.path("baileysMessage").toString());
        } catch (Exception ignored) {
        }
        return message;
    }

    private void touch(CrmWhatsappConversation conversation, String body, String type, LocalDateTime at) {
        conversation.setLastMessage(body);
        conversation.setLastMessageType(type);
        conversation.setLastMessageAt(at);
        conversation.setUnreadCount(0);
        conversationRepository.save(conversation);
    }

    private void publish(Long conversationId) {
        warehouseEventService.publishAfterCommit(java.util.Map.of(
                "type", "warehouse.message.updated", "conversationId", conversationId));
    }

    private void requireBridgeSuccess(ResponseEntity<String> response) {
        if (response == null || !response.getStatusCode().is2xxSuccessful()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No se pudo enviar el mensaje por WhatsApp");
        }
    }

    private String mediaType(String mime) {
        if (mime.startsWith("image/")) return "IMAGE";
        if (mime.startsWith("video/")) return "VIDEO";
        if (mime.startsWith("audio/")) return "AUDIO";
        return "DOCUMENT";
    }

    private String safeFileName(String value) {
        String name = clean(value).replaceAll("[\\\\/:*?\"<>|]", "_");
        return name.isBlank() ? "archivo" : name;
    }

    private String clean(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    public record ConversationPage(List<ConversationDto> content, int page, int size, int totalPages,
            long totalElements, boolean last) {}
    public record ConversationDto(Long id, String phone, String phoneNumber, String whatsappUsername,
            String contactName, String lastMessage, String lastMessageType, LocalDateTime lastMessageAt,
            int unreadCount) {}
    public record MessagePage(List<MessageDto> content, Long oldestId, Long newestId, boolean hasMoreBefore) {}
    public record SalesMessagePage(List<MessageDto> content, Long oldestId, Long newestId,
            boolean hasMoreBefore, boolean available) {}
    public record MessageDto(Long id, String direction, String messageType, String body, String status,
            LocalDateTime createdAt, String mediaMimeType, String mediaFileName, String mediaUrl,
            Integer mediaDurationSeconds, String origin, Integer relatedSaleId, String receiptFormat,
            boolean deleted) {}
    public record MessageRequest(String message) {}
    public record MediaDownload(byte[] bytes, String contentType, String fileName) {}
}
