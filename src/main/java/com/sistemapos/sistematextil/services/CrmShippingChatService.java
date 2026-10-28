package com.sistemapos.sistematextil.services;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import com.sistemapos.sistematextil.model.CrmWhatsappConversationTag;
import com.sistemapos.sistematextil.model.CrmWhatsappMessage;
import com.sistemapos.sistematextil.model.CrmWhatsappTag;
import com.sistemapos.sistematextil.model.Empresa;
import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.repositories.CrmWhatsappConversationTagRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappConversationRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappMessageRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappTagRepository;
import com.sistemapos.sistematextil.repositories.EmpresaRepository;
import com.sistemapos.sistematextil.repositories.SucursalRepository;
import com.sistemapos.sistematextil.util.usuario.Rol;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CrmShippingChatService {
    private final CrmWhatsappConversationRepository conversationRepository;
    private final CrmWhatsappConversationTagRepository conversationTagRepository;
    private final CrmWhatsappMessageRepository messageRepository;
    private final CrmWhatsappTagRepository tagRepository;
    private final EmpresaRepository empresaRepository;
    private final SucursalRepository sucursalRepository;
    private final CrmWhatsappBridgeService bridgeService;
    private final CrmWhatsappNumberChangeNoticeService numberChangeNoticeService;
    private final CrmWarehouseEventService warehouseEventService;
    private final S3StorageService storageService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional(readOnly = true)
    public ConversationPage conversations(
            Usuario actor,
            String query,
            Long tagId,
            boolean unreadOnly,
            int page,
            int size) {
        requireShippingAccess(actor);
        Integer actorCompanyId = companyId(actor);
        if (tagId != null) requireTag(tagId, actorCompanyId);
        String term = clean(query);
        var result = conversationRepository.findShippingChatConversations(
                actorCompanyId, term.isBlank() ? null : term, tagId, unreadOnly,
                PageRequest.of(Math.max(0, page), Math.min(50, Math.max(1, size))));
        Map<Long, List<ConversationTagDto>> tagsByConversation = tagsByConversation(result.getContent());
        return new ConversationPage(result.getContent().stream()
                .map(value -> conversation(value, tagsByConversation.getOrDefault(value.getIdConversation(), List.of())))
                .toList(),
                result.getNumber(), result.getSize(), result.getTotalPages(), result.getTotalElements(),
                result.isLast());
    }

    @Transactional(readOnly = true)
    public ConversationDto conversation(Long conversationId, Usuario actor) {
        CrmWhatsappConversation conversation = requireConversation(conversationId, actor);
        return conversation(conversation, conversationTagsInternal(conversationId));
    }

    @Transactional
    public ConversationDto updateReadState(Long conversationId, ReadStateRequest request, Usuario actor) {
        requireShippingAccess(actor);
        if (request == null || request.unread() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unread es requerido");
        }
        CrmWhatsappConversation conversation = conversationRepository.findForUpdateById(conversationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversacion no encontrada"));
        requireShippingCompany(conversation, actor);
        int currentUnreadCount = conversation.getUnreadCount() == null
                ? 0
                : Math.max(0, conversation.getUnreadCount());
        int nextUnreadCount = request.unread() ? Math.max(1, currentUnreadCount) : 0;
        if (currentUnreadCount != nextUnreadCount) {
            conversation.setUnreadCount(nextUnreadCount);
            conversation = conversationRepository.save(conversation);
            publishReadState(conversationId);
        }
        return conversation(conversation, conversationTagsInternal(conversationId));
    }

    @Transactional(readOnly = true)
    public List<TagDto> tags(Usuario actor) {
        requireShippingAccess(actor);
        List<CrmWhatsappTag> tags = tagRepository
                .findByEmpresa_IdEmpresaAndDeletedAtIsNullOrderByNombreAsc(companyId(actor));
        Map<Long, Long> usageByTag = new LinkedHashMap<>();
        if (!tags.isEmpty()) {
            for (Object[] row : conversationTagRepository
                    .countActiveByTagIds(tags.stream().map(CrmWhatsappTag::getIdTag).toList())) {
                usageByTag.put((Long) row[0], (Long) row[1]);
            }
        }
        return tags.stream().map(tag -> new TagDto(tag.getIdTag(), tag.getNombre(), tag.getColor(),
                usageByTag.getOrDefault(tag.getIdTag(), 0L))).toList();
    }

    @Transactional
    public TagDto createTag(TagRequest request, Usuario actor) {
        requireShippingAccess(actor);
        Integer actorCompanyId = companyId(actor);
        String name = validateTagName(request == null ? null : request.nombre());
        String color = validateTagColor(request == null ? null : request.color());
        tagRepository.findFirstByEmpresa_IdEmpresaAndNombreIgnoreCaseAndDeletedAtIsNull(actorCompanyId, name)
                .ifPresent(tag -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe una etiqueta con ese nombre");
                });
        Empresa company = empresaRepository.findById(actorCompanyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Empresa no encontrada"));
        CrmWhatsappTag tag = new CrmWhatsappTag();
        tag.setEmpresa(company);
        tag.setNombre(name);
        tag.setColor(color);
        CrmWhatsappTag saved = tagRepository.save(tag);
        return new TagDto(saved.getIdTag(), saved.getNombre(), saved.getColor(), 0L);
    }

    @Transactional(readOnly = true)
    public List<ConversationTagDto> conversationTags(Long conversationId, Usuario actor) {
        requireConversation(conversationId, actor);
        return conversationTagsInternal(conversationId);
    }

    @Transactional
    public List<ConversationTagDto> assignTag(Long conversationId, Long tagId, Usuario actor) {
        CrmWhatsappConversation conversation = requireConversation(conversationId, actor);
        CrmWhatsappTag tag = requireTag(tagId, companyId(actor));
        CrmWhatsappConversationTag relation = conversationTagRepository
                .findFirstByConversation_IdConversationAndTag_IdTagOrderByIdConversationTagAsc(conversationId, tagId)
                .orElseGet(CrmWhatsappConversationTag::new);
        relation.setConversation(conversation);
        relation.setTag(tag);
        relation.setDeletedAt(null);
        conversationTagRepository.save(relation);
        publishTag(conversationId);
        return conversationTagsInternal(conversationId);
    }

    @Transactional
    public List<ConversationTagDto> removeTag(Long conversationId, Long tagId, Usuario actor) {
        requireConversation(conversationId, actor);
        requireTag(tagId, companyId(actor));
        conversationTagRepository
                .findFirstByConversation_IdConversationAndTag_IdTagAndDeletedAtIsNull(conversationId, tagId)
                .ifPresent(relation -> {
                    relation.setDeletedAt(LocalDateTime.now());
                    conversationTagRepository.save(relation);
                });
        publishTag(conversationId);
        return conversationTagsInternal(conversationId);
    }

    @Transactional(readOnly = true)
    public MessagePage messages(Long conversationId, Long beforeId, Long afterId, int requestedLimit, Usuario actor) {
        CrmWhatsappConversation conversation = requireConversation(conversationId, actor);
        return messagePage(conversation, beforeId, afterId, requestedLimit,
                "/api/crm/whatsapp/shipping/messages/");
    }

    @Transactional(readOnly = true)
    public SalesMessagePage salesMessages(Long conversationId, Long beforeId, Long afterId,
            int requestedLimit, Usuario actor) {
        CrmWhatsappConversation shippingConversation = requireConversation(conversationId, actor);
        CrmWhatsappConversation salesConversation = findSalesConversation(shippingConversation, actor);
        if (salesConversation == null) {
            return new SalesMessagePage(List.of(), null, null, false, false);
        }
        MessagePage page = messagePage(salesConversation, beforeId, afterId, requestedLimit,
                "/api/crm/whatsapp/shipping/conversations/" + conversationId + "/sales-messages/");
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
        numberChangeNoticeService.registerIfNeeded(conversation.getIdConversation(), null);
        ResponseEntity<String> response = bridgeService.enviarTexto(CrmWhatsappArea.SHIPPING,
                conversation.getPhone(), body, null, UUID.randomUUID().toString());
        requireBridgeSuccess(response);
        CrmWhatsappMessage message = outgoing(conversation, body, "TEXT", response.getBody(), actor);
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
        try {
            return sendMediaBytes(conversation, file.getBytes(), file.getOriginalFilename(),
                    file.getContentType(), caption, actor);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se pudo leer el archivo");
        }
    }

    @Transactional
    public MessageDto sendMediaFromStorage(Long conversationId, String sourceStoragePath, String fileName,
            String mimeType, String caption, Usuario actor) {
        CrmWhatsappConversation conversation = requireConversation(conversationId, actor);
        if (clean(sourceStoragePath).isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "La imagen del comando no esta disponible");
        }
        return sendMediaBytes(conversation, storageService.readBytes(sourceStoragePath), fileName,
                mimeType, caption, actor);
    }

    private MessageDto sendMediaBytes(CrmWhatsappConversation conversation, byte[] bytes, String originalFileName,
            String contentType, String caption, Usuario actor) {
        if (bytes == null || bytes.length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El archivo esta vacio");
        }
        numberChangeNoticeService.registerIfNeeded(conversation.getIdConversation(), null);
        String fileName = safeFileName(originalFileName);
        String mime = clean(contentType);
        if (mime.isBlank()) mime = "application/octet-stream";
        YearMonth month = YearMonth.now();
        String storageKey = "crm/whatsapp/shipping/%04d/%02d/%s-%s".formatted(
                month.getYear(), month.getMonthValue(), UUID.randomUUID(), fileName);
        String storagePath = null;
        try {
            storagePath = storageService.upload(bytes, storageKey, mime);
            ResponseEntity<String> response = bridgeService.enviarMedia(CrmWhatsappArea.SHIPPING,
                    conversation.getPhone(), bytes, fileName, mime, clean(caption), null,
                    UUID.randomUUID().toString());
            requireBridgeSuccess(response);
            String type = mediaType(mime);
            String body = clean(caption).isBlank() ? fileName : clean(caption);
            CrmWhatsappMessage message = outgoing(conversation, body, type, response.getBody(), actor);
            message.setMediaFileName(fileName);
            message.setMediaMimeType(mime);
            message.setMediaStoragePath(storagePath);
            message = messageRepository.save(message);
            touch(conversation, body, type, message.getCreatedAt());
            publish(conversation.getIdConversation());
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
        if (message.getDeletedAt() != null || clean(message.getMediaStoragePath()).isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "El mensaje no tiene archivo disponible");
        }
        return new MediaDownload(storageService.readBytes(message.getMediaStoragePath()),
                clean(message.getMediaMimeType()).isBlank() ? "application/octet-stream" : message.getMediaMimeType(),
                safeFileName(message.getMediaFileName()));
    }

    @Transactional(readOnly = true)
    public MediaDownload salesMedia(Long shippingConversationId, Long messageId, Usuario actor) {
        CrmWhatsappConversation shippingConversation = requireConversation(shippingConversationId, actor);
        CrmWhatsappConversation salesConversation = findSalesConversation(shippingConversation, actor);
        if (salesConversation == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversacion de ventas no encontrada");
        }
        CrmWhatsappMessage message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mensaje no encontrado"));
        if (message.getConversation() == null
                || !salesConversation.getIdConversation().equals(message.getConversation().getIdConversation())
                || message.getDeletedAt() != null
                || clean(message.getMediaStoragePath()).isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "El mensaje no tiene archivo disponible");
        }
        return new MediaDownload(storageService.readBytes(message.getMediaStoragePath()),
                clean(message.getMediaMimeType()).isBlank() ? "application/octet-stream" : message.getMediaMimeType(),
                safeFileName(message.getMediaFileName()));
    }

    private CrmWhatsappConversation findSalesConversation(CrmWhatsappConversation shippingConversation, Usuario actor) {
        String phone = clean(shippingConversation.getPhone());
        String phoneNumber = clean(shippingConversation.getPhoneNumber());
        if (phoneNumber.isBlank()) phoneNumber = clean(shippingConversation.getWhatsappUsername());
        if (phoneNumber.isBlank()) phoneNumber = phone.replaceFirst("@.+$", "");
        Integer clientId = shippingConversation.getCliente() == null
                ? null : shippingConversation.getCliente().getIdCliente();
        return conversationRepository.findSalesConversationForShippingContact(
                companyId(actor), clientId, phone, phoneNumber, PageRequest.of(0, 1)).stream()
                .findFirst().orElse(null);
    }

    private CrmWhatsappConversation requireConversation(Long id, Usuario actor) {
        requireShippingAccess(actor);
        CrmWhatsappConversation conversation = conversationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversacion no encontrada"));
        requireShippingCompany(conversation, actor);
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
        if (!"ACTIVO".equalsIgnoreCase(clean(actor.getEstado()))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario inactivo");
        }
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

    private ConversationDto conversation(CrmWhatsappConversation value, List<ConversationTagDto> tags) {
        String name = clean(value.getContactName());
        if (name.isBlank() && value.getCliente() != null) name = clean(value.getCliente().getNombres());
        return new ConversationDto(value.getIdConversation(), value.getPhone(), value.getPhoneNumber(),
                value.getWhatsappUsername(), name.isBlank() ? null : name, value.getLastMessage(),
                value.getLastMessageType(), value.getLastMessageAt(), value.getUnreadCount() == null ? 0 : value.getUnreadCount(),
                tags);
    }

    private Map<Long, List<ConversationTagDto>> tagsByConversation(List<CrmWhatsappConversation> conversations) {
        Map<Long, List<ConversationTagDto>> result = new LinkedHashMap<>();
        List<Long> ids = conversations.stream().map(CrmWhatsappConversation::getIdConversation).toList();
        if (ids.isEmpty()) return result;
        for (CrmWhatsappConversationTag relation : conversationTagRepository.findActiveByConversationIds(ids)) {
            result.computeIfAbsent(relation.getConversation().getIdConversation(), ignored -> new ArrayList<>())
                    .add(conversationTag(relation));
        }
        return result;
    }

    private List<ConversationTagDto> conversationTagsInternal(Long conversationId) {
        return conversationTagRepository.findActiveByConversationId(conversationId).stream()
                .map(this::conversationTag).toList();
    }

    private ConversationTagDto conversationTag(CrmWhatsappConversationTag relation) {
        CrmWhatsappTag tag = relation.getTag();
        return new ConversationTagDto(tag.getIdTag(), tag.getNombre(), tag.getColor());
    }

    private CrmWhatsappTag requireTag(Long tagId, Integer actorCompanyId) {
        if (tagId == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Etiqueta requerida");
        return tagRepository.findByIdTagAndEmpresa_IdEmpresaAndDeletedAtIsNull(tagId, actorCompanyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Etiqueta no encontrada"));
    }

    private String validateTagName(String value) {
        String name = clean(value);
        if (name.length() < 2 || name.length() > 80) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El nombre debe tener entre 2 y 80 caracteres");
        }
        return name;
    }

    private String validateTagColor(String value) {
        String color = clean(value);
        if (color.isBlank()) return "#3b82f6";
        if (!color.matches("^#[0-9a-fA-F]{6}$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Color invalido");
        }
        return color.toLowerCase();
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
                value.getReceiptFormat(), value.getActor() == null ? null : value.getActor().getIdUsuario(),
                value.getActorName(), deleted);
    }

    private CrmWhatsappMessage outgoing(CrmWhatsappConversation conversation, String body, String type, String json,
            Usuario actor) {
        CrmWhatsappMessage message = new CrmWhatsappMessage();
        message.setConversation(conversation);
        message.setDirection("OUTGOING");
        message.setOrigin("WAREHOUSE");
        message.setActor(actor);
        message.setActorName(fullName(actor));
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

    private void publishTag(Long conversationId) {
        warehouseEventService.publishAfterCommit(java.util.Map.of(
                "type", "warehouse.tag.updated", "conversationId", conversationId));
    }

    private void publishReadState(Long conversationId) {
        warehouseEventService.publishAfterCommit(java.util.Map.of(
                "type", "warehouse.conversation.updated", "conversationId", conversationId));
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

    private String fullName(Usuario actor) {
        if (actor == null) return "";
        String name = (clean(actor.getNombre()) + " " + clean(actor.getApellido())).trim();
        return name.isBlank() ? clean(actor.getCorreo()) : name;
    }

    public record ConversationPage(List<ConversationDto> content, int page, int size, int totalPages,
            long totalElements, boolean last) {}
    public record ConversationDto(Long id, String phone, String phoneNumber, String whatsappUsername,
            String contactName, String lastMessage, String lastMessageType, LocalDateTime lastMessageAt,
            int unreadCount, List<ConversationTagDto> tags) {}
    public record TagDto(Long id, String nombre, String color, long uso) {}
    public record ConversationTagDto(Long id, String label, String color) {}
    public record TagRequest(String nombre, String color) {}
    public record ReadStateRequest(Boolean unread) {}
    public record MessagePage(List<MessageDto> content, Long oldestId, Long newestId, boolean hasMoreBefore) {}
    public record SalesMessagePage(List<MessageDto> content, Long oldestId, Long newestId,
            boolean hasMoreBefore, boolean available) {}
    public record MessageDto(Long id, String direction, String messageType, String body, String status,
            LocalDateTime createdAt, String mediaMimeType, String mediaFileName, String mediaUrl,
            Integer mediaDurationSeconds, String origin, Integer relatedSaleId, String receiptFormat,
            Integer sentByUserId, String sentByName, boolean deleted) {}
    public record MessageRequest(String message) {}
    public record MediaDownload(byte[] bytes, String contentType, String fileName) {}
}
