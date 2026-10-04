package com.sistemapos.sistematextil.services;

import java.time.*;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import com.sistemapos.sistematextil.model.*;
import com.sistemapos.sistematextil.repositories.*;
import com.sistemapos.sistematextil.services.CrmWhatsappChatService.MessageResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CrmWhatsappAiDeliveryService {
    private static final String HANDOFF_MESSAGE = "Claro, bella. En un momentito una asesora continuará contigo 💛";
    private final CrmWhatsappAiDeliveryRepository deliveryRepository;
    private final CrmWhatsappAiConfigRepository configRepository;
    private final CrmWhatsappAiMemoryRepository memoryRepository;
    private final CrmWhatsappMessageRepository messageRepository;
    private final CrmWhatsappChatService chatService;
    private final CrmWhatsappAiMemoryService memoryService;
    private final CrmWhatsappAiSaleDraftService saleDraftService;
    private final CrmWhatsappEventService eventService;
    private final CrmWhatsappAiOperationsService operationsService;
    private final S3StorageService storageService;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Transactional
    public void enqueue(CrmWhatsappAiRun run) {
        if (run == null || run.getJob() == null || !"AUTOMATIC".equals(run.getJob().getTriggerType())) return;
        if (run.getOutcome() != CrmWhatsappAiRunOutcome.DRAFT_READY || run.getRequiresHuman()) return;
        if (deliveryRepository.findByRun_IdAiRun(run.getIdAiRun()).isPresent()) return;
        CrmWhatsappAiDelivery delivery = new CrmWhatsappAiDelivery();
        delivery.setRun(run);
        delivery.setConversation(run.getConversation());
        delivery.setSourceMessageId(run.getMessage().getIdMessage());
        MediaPayload media = deliveryMedia(run);
        delivery.setDeliveryType(media == null
                ? CrmWhatsappAiDeliveryType.AUTOMATIC_RESPONSE
                : media.deliveryType());
        if (media != null) {
            delivery.setMediaReference(media.reference());
            delivery.setMediaMimeType(media.mimeType());
            delivery.setMediaFileName(media.fileName());
            delivery.setMediaCaption(clean(run.getDraftResponse()));
        }
        delivery.setStatus(CrmWhatsappAiDeliveryStatus.PENDING);
        delivery.setAvailableAt(LocalDateTime.now());
        deliveryRepository.save(delivery);
    }

    @Transactional
    public void enqueueHandoff(CrmWhatsappAiRun run) {
        if (run == null || run.getJob() == null || !"AUTOMATIC".equals(run.getJob().getTriggerType())) return;
        if (run.getOutcome() != CrmWhatsappAiRunOutcome.HUMAN_REQUIRED) return;
        if (deliveryRepository.findByRun_IdAiRun(run.getIdAiRun()).isPresent()) return;
        CrmWhatsappAiDelivery delivery = new CrmWhatsappAiDelivery();
        delivery.setRun(run);
        delivery.setConversation(run.getConversation());
        delivery.setSourceMessageId(run.getMessage().getIdMessage());
        delivery.setDeliveryType(CrmWhatsappAiDeliveryType.HANDOFF_NOTICE);
        delivery.setStatus(CrmWhatsappAiDeliveryStatus.PENDING);
        delivery.setAvailableAt(LocalDateTime.now());
        deliveryRepository.save(delivery);
    }

    @Transactional
    public void enqueueNotice(CrmWhatsappConversation conversation, CrmWhatsappAiDeliveryType type,
            String idempotencyKey, String text) {
        if (conversation == null || !isBusinessNotice(type) || clean(idempotencyKey).isBlank()
                || clean(text).isBlank()) return;
        if (deliveryRepository.findByIdempotencyKey(clean(idempotencyKey)).isPresent()) return;
        CrmWhatsappAiDelivery delivery = new CrmWhatsappAiDelivery();
        delivery.setConversation(conversation);
        delivery.setDeliveryType(type);
        delivery.setTextBody(clean(text));
        delivery.setIdempotencyKey(clean(idempotencyKey));
        delivery.setStatus(requiresHumanSend(type)
                ? CrmWhatsappAiDeliveryStatus.AWAITING_HUMAN
                : CrmWhatsappAiDeliveryStatus.PENDING);
        delivery.setAvailableAt(LocalDateTime.now());
        deliveryRepository.save(delivery);
        if (delivery.getStatus() == CrmWhatsappAiDeliveryStatus.AWAITING_HUMAN) {
            publishHumanDraft(delivery);
        }
    }

    @Transactional(readOnly = true)
    public HumanDraftResponse pendingHumanDraft(Long conversationId, Usuario actor) {
        requireHumanAccess(conversationId, actor);
        return deliveryRepository
                .findFirstByConversation_IdConversationAndStatusOrderByCreatedAtAsc(
                        conversationId, CrmWhatsappAiDeliveryStatus.AWAITING_HUMAN)
                .map(this::toHumanDraftResponse)
                .orElse(null);
    }

    @Transactional
    public void discardHumanDraft(Long conversationId, Long deliveryId, Usuario actor) {
        requireHumanAccess(conversationId, actor);
        CrmWhatsappAiDelivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Borrador no encontrado"));
        if (!delivery.getConversation().getIdConversation().equals(conversationId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Borrador no encontrado");
        }
        if (delivery.getStatus() == CrmWhatsappAiDeliveryStatus.AWAITING_HUMAN) {
            delivery.setStatus(CrmWhatsappAiDeliveryStatus.CANCELLED);
            delivery.setFailureReason("Descartado por el asesor");
            deliveryRepository.save(delivery);
        }
    }

    @Transactional
    public void completeHumanDraft(Long conversationId, Long deliveryId, Long messageId, Usuario actor) {
        if (deliveryId == null) return;
        requireHumanAccess(conversationId, actor);
        CrmWhatsappAiDelivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Borrador no encontrado"));
        if (!delivery.getConversation().getIdConversation().equals(conversationId)
                || delivery.getStatus() != CrmWhatsappAiDeliveryStatus.AWAITING_HUMAN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El borrador ya no esta disponible");
        }
        delivery.setOutgoingMessage(messageRepository.findById(messageId).orElse(null));
        delivery.setStatus(CrmWhatsappAiDeliveryStatus.SENT);
        delivery.setSentAt(LocalDateTime.now());
        deliveryRepository.save(delivery);
    }

    @Transactional(readOnly = true)
    public List<Long> readyIds(int size) {
        return deliveryRepository.findReadyIds(LocalDateTime.now(), PageRequest.of(0, Math.max(1, Math.min(size, 20))));
    }

    @Transactional
    public boolean claim(Long id) {
        return deliveryRepository.claim(id, CrmWhatsappAiDeliveryStatus.PENDING,
                CrmWhatsappAiDeliveryStatus.SENDING, LocalDateTime.now()) == 1;
    }

    @Transactional(readOnly = true)
    public PreparedDelivery prepare(Long id) {
        CrmWhatsappAiDelivery delivery = deliveryRepository.findDetailedById(id)
                .orElseThrow(() -> new IllegalStateException("Entrega IA no encontrada"));
        CrmWhatsappConversation conversation = delivery.getConversation();
        CrmWhatsappAiRun run = delivery.getRun();
        boolean businessNotice = isBusinessNotice(delivery.getDeliveryType());
        boolean humanDraft = requiresHumanSend(delivery.getDeliveryType())
                || (businessNotice && (conversation.getAssignedUser() != null
                        || !"ESPERA".equals(conversation.getStatus())));
        CrmWhatsappAiConfig config = businessNotice || conversation.getConnection() == null ? null
                : configRepository.findByConnection_IdConnection(conversation.getConnection().getIdConnection()).orElse(null);
        boolean handoff = delivery.getDeliveryType() == CrmWhatsappAiDeliveryType.HANDOFF_NOTICE;
        boolean media = isMediaDelivery(delivery.getDeliveryType());
        String invalid = businessNotice
                ? (clean(delivery.getTextBody()).isBlank() ? "Aviso comercial vacio" : "")
                : handoff ? validateHandoff(delivery, conversation) : validate(delivery, conversation, run, config);
        return new PreparedDelivery(delivery.getIdAiDelivery(), conversation.getIdConversation(),
                businessNotice ? clean(delivery.getTextBody())
                        : handoff ? handoffMessage(run) : clean(run.getDraftResponse()),
                invalid, handoff, media, businessNotice, humanDraft,
                clean(delivery.getMediaReference()), clean(delivery.getMediaMimeType()),
                clean(delivery.getMediaFileName()));
    }

    public void send(PreparedDelivery prepared) {
        if (prepared.humanDraft()) {
            deferToHuman(prepared.deliveryId());
            return;
        }
        if (!prepared.invalidReason().isBlank()) {
            cancel(prepared.deliveryId(), prepared.invalidReason(), !prepared.handoff());
            return;
        }
        MessageResponse sent = prepared.media()
                ? chatService.enviarMediaAutomatico(prepared.conversationId(),
                        readMedia(prepared.mediaReference()), prepared.mediaFileName(),
                        prepared.mediaMimeType(), prepared.text())
                : chatService.enviarMensajeAutomatico(prepared.conversationId(), prepared.text());
        complete(prepared.deliveryId(), sent.id());
    }

    @Transactional
    public void deferToHuman(Long deliveryId) {
        CrmWhatsappAiDelivery delivery = deliveryRepository.findDetailedById(deliveryId).orElse(null);
        if (delivery == null || delivery.getStatus() == CrmWhatsappAiDeliveryStatus.SENT
                || delivery.getStatus() == CrmWhatsappAiDeliveryStatus.CANCELLED) return;
        delivery.setStatus(CrmWhatsappAiDeliveryStatus.AWAITING_HUMAN);
        delivery.setLockedAt(null);
        delivery.setFailureReason(null);
        delivery = deliveryRepository.save(delivery);
        publishHumanDraft(delivery);
    }

    @Transactional
    public void complete(Long id, Long messageId) {
        CrmWhatsappAiDelivery delivery = deliveryRepository.findDetailedById(id)
                .orElseThrow(() -> new IllegalStateException("Entrega IA no encontrada"));
        if (delivery.getStatus() != CrmWhatsappAiDeliveryStatus.SENDING) return;
        delivery.setOutgoingMessage(messageRepository.findById(messageId).orElse(null));
        delivery.setStatus(CrmWhatsappAiDeliveryStatus.SENT);
        delivery.setSentAt(LocalDateTime.now());
        delivery.setLockedAt(null);
        deliveryRepository.save(delivery);
        boolean handoff = delivery.getDeliveryType() == CrmWhatsappAiDeliveryType.HANDOFF_NOTICE;
        boolean businessNotice = isBusinessNotice(delivery.getDeliveryType());
        if (!handoff && !businessNotice) {
            memoryService.markAutomaticSent(delivery.getConversation().getIdConversation(), messageId);
            saleDraftService.markConfirmationSent(delivery.getConversation().getIdConversation(), messageId);
            if (saleDraftService.pauseIfReady(delivery.getConversation().getIdConversation())) {
                memoryService.pauseForHuman(delivery.getConversation(), "Pedido confirmado, requiere revision del asesor");
            }
        }
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", businessNotice ? "crm.notice.sent"
                : handoff ? "ai.handoff.notice.sent" : "ai.auto_reply.sent");
        event.put("conversationId", delivery.getConversation().getIdConversation());
        event.put("messageId", messageId);
        if (delivery.getRun() != null) event.put("runId", delivery.getRun().getIdAiRun());
        event.put("deliveryType", delivery.getDeliveryType().name());
        eventService.publishAfterCommit(event, event, null, true);
    }

    @Transactional
    public void fail(Long id, Throwable error) {
        CrmWhatsappAiDelivery delivery = deliveryRepository.findById(id).orElse(null);
        if (delivery == null || delivery.getStatus() == CrmWhatsappAiDeliveryStatus.CANCELLED) return;
        String errorMessage = error == null ? "" : clean(error.getMessage());
        if (isBusinessNotice(delivery.getDeliveryType())
                && (errorMessage.contains("409") || errorMessage.contains("ya no admite respuesta automatica")
                        || errorMessage.contains("asesor ya atiende"))) {
            delivery.setStatus(CrmWhatsappAiDeliveryStatus.AWAITING_HUMAN);
            delivery.setLockedAt(null);
            delivery.setFailureReason(null);
            deliveryRepository.save(delivery);
            publishHumanDraft(delivery);
            return;
        }
        delivery.setLockedAt(null);
        delivery.setFailureReason(limit(error == null ? "Error enviando respuesta automatica" : error.getMessage(), 1000));
        if (delivery.getAttempts() < 3) {
            delivery.setStatus(CrmWhatsappAiDeliveryStatus.PENDING);
            delivery.setAvailableAt(LocalDateTime.now().plusSeconds((long) delivery.getAttempts() * 10));
        } else {
            if (isMediaDelivery(delivery.getDeliveryType())) {
                try {
                    MessageResponse fallback = chatService.enviarMensajeAutomatico(
                            delivery.getConversation().getIdConversation(),
                            delivery.getDeliveryType() == CrmWhatsappAiDeliveryType.SIZE_GUIDE_IMAGE
                                    ? "No pudimos adjuntar la guia de tallas en este momento. ¿Deseas realizar otra consulta?"
                                    : clean(delivery.getRun() == null ? null : delivery.getRun().getDraftResponse()));
                    delivery.setOutgoingMessage(messageRepository.findById(fallback.id()).orElse(null));
                    delivery.setStatus(CrmWhatsappAiDeliveryStatus.SENT);
                    delivery.setSentAt(LocalDateTime.now());
                    memoryService.markAutomaticSent(delivery.getConversation().getIdConversation(), fallback.id());
                } catch (RuntimeException fallbackError) {
                    delivery.setStatus(CrmWhatsappAiDeliveryStatus.FAILED);
                }
            } else if (isBusinessNotice(delivery.getDeliveryType())) {
                delivery.setStatus(CrmWhatsappAiDeliveryStatus.FAILED);
            } else {
                delivery.setStatus(CrmWhatsappAiDeliveryStatus.FAILED);
                publishHandoff(delivery.getRun(), "No se pudo enviar la respuesta automatica");
            }
        }
        deliveryRepository.save(delivery);
    }

    @Transactional
    public void cancel(Long id, String reason, boolean handoff) {
        CrmWhatsappAiDelivery delivery = deliveryRepository.findDetailedById(id).orElse(null);
        if (delivery == null || delivery.getStatus() == CrmWhatsappAiDeliveryStatus.SENT) return;
        delivery.setStatus(CrmWhatsappAiDeliveryStatus.CANCELLED);
        delivery.setFailureReason(limit(reason, 1000));
        delivery.setLockedAt(null);
        deliveryRepository.save(delivery);
        if (handoff && delivery.getRun() != null) publishHandoff(delivery.getRun(), reason);
    }

    private String validate(CrmWhatsappAiDelivery delivery, CrmWhatsappConversation conversation,
            CrmWhatsappAiRun run, CrmWhatsappAiConfig config) {
        if (config == null || config.getModo() != CrmWhatsappAiMode.AUTOMATICA) return "La IA automatica ya no esta activa";
        if (!operationsService.automaticAllowedForConversation(config, conversation)) return "Automatizacion pausada por control operativo";
        if (conversation.getAssignedUser() != null || !"ESPERA".equals(conversation.getStatus())) return "Un asesor ya atiende el chat";
        if (!withinSchedule(config)) return "Fuera del horario operativo de IA";
        if (run.getConfidence() == null || run.getConfidence() < config.getConfianzaMinima()) return "Confianza insuficiente";
        if (clean(run.getDraftResponse()).isBlank()) return "Respuesta automatica vacia";
        if (isMediaDelivery(delivery.getDeliveryType())
                && clean(delivery.getMediaReference()).isBlank()) return "Imagen sin archivo";
        var latest = messageRepository.findRecentIncomingMessages(conversation.getIdConversation(), PageRequest.of(0, 1));
        if (latest.isEmpty() || !latest.get(0).getIdMessage().equals(delivery.getSourceMessageId())) return "Existe un mensaje mas reciente";
        var memory = memoryRepository.findByConversation_IdConversation(conversation.getIdConversation()).orElse(null);
        if (memory != null && memory.getAttentionState() != CrmWhatsappAiAttentionState.AUTOMATICA) return "Atencion automatica pausada";
        if (memory != null && memory.getConsecutiveAutoResponses() >= config.getMaxRespuestasAutomaticas()) return "Limite de respuestas automaticas alcanzado";
        return "";
    }

    private String validateHandoff(CrmWhatsappAiDelivery delivery, CrmWhatsappConversation conversation) {
        if (conversation.getAssignedUser() != null || !"ESPERA".equals(conversation.getStatus())) {
            return "Un asesor ya atiende el chat";
        }
        var latest = messageRepository.findRecentIncomingMessages(conversation.getIdConversation(), PageRequest.of(0, 1));
        if (latest.isEmpty() || !latest.get(0).getIdMessage().equals(delivery.getSourceMessageId())) {
            return "Existe un mensaje mas reciente";
        }
        return "";
    }

    private String handoffMessage(CrmWhatsappAiRun run) {
        String reason = clean(run == null ? null : run.getReason()).toLowerCase(Locale.ROOT);
        return HANDOFF_MESSAGE;
    }

    private boolean isBusinessNotice(CrmWhatsappAiDeliveryType type) {
        return type == CrmWhatsappAiDeliveryType.PAYMENT_REGISTERED
                || type == CrmWhatsappAiDeliveryType.PAYMENT_RETRY
                || type == CrmWhatsappAiDeliveryType.PAYMENT_REJECTED
                || type == CrmWhatsappAiDeliveryType.SALE_COMPLETED;
    }

    private boolean requiresHumanSend(CrmWhatsappAiDeliveryType type) {
        return type == CrmWhatsappAiDeliveryType.PAYMENT_REJECTED
                || type == CrmWhatsappAiDeliveryType.SALE_COMPLETED;
    }

    private void publishHumanDraft(CrmWhatsappAiDelivery delivery) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "crm.composer.suggested");
        event.put("conversationId", delivery.getConversation().getIdConversation());
        event.put("deliveryId", delivery.getIdAiDelivery());
        event.put("text", clean(delivery.getTextBody()));
        event.put("deliveryType", delivery.getDeliveryType().name());
        eventService.publishAfterCommit(event, event, null, true);
    }

    private void requireHumanAccess(Long conversationId, Usuario actor) {
        if (actor == null || actor.getSucursal() == null || actor.getSucursal().getEmpresa() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autorizado");
        }
        CrmWhatsappAiDelivery delivery = deliveryRepository
                .findFirstByConversation_IdConversationAndStatusOrderByCreatedAtAsc(
                        conversationId, CrmWhatsappAiDeliveryStatus.AWAITING_HUMAN).orElse(null);
        CrmWhatsappConversation conversation = delivery == null ? null : delivery.getConversation();
        if (conversation != null && conversation.getConnection() != null
                && conversation.getConnection().getEmpresa() != null
                && !conversation.getConnection().getEmpresa().getIdEmpresa()
                        .equals(actor.getSucursal().getEmpresa().getIdEmpresa())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No autorizado");
        }
        if (conversation != null && conversation.getAssignedUser() != null
                && !conversation.getAssignedUser().getIdUsuario().equals(actor.getIdUsuario())
                && !"ADMINISTRADOR".equals(String.valueOf(actor.getRol()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La conversacion esta asignada a otro asesor");
        }
    }

    private HumanDraftResponse toHumanDraftResponse(CrmWhatsappAiDelivery delivery) {
        return new HumanDraftResponse(delivery.getIdAiDelivery(), clean(delivery.getTextBody()),
                delivery.getDeliveryType().name(), delivery.getCreatedAt());
    }

    private boolean withinSchedule(CrmWhatsappAiConfig config) {
        ZoneId zone;
        try { zone = ZoneId.of(config.getZonaHoraria()); } catch (DateTimeException ex) { return false; }
        ZonedDateTime now = ZonedDateTime.now(zone);
        String day = switch (now.getDayOfWeek()) {
            case MONDAY -> "LUNES"; case TUESDAY -> "MARTES"; case WEDNESDAY -> "MIERCOLES";
            case THURSDAY -> "JUEVES"; case FRIDAY -> "VIERNES"; case SATURDAY -> "SABADO"; case SUNDAY -> "DOMINGO";
        };
        if (!List.of(config.getDiasAtencion().split(",")).contains(day)) return false;
        LocalTime time = now.toLocalTime();
        return !time.isBefore(config.getHoraInicio()) && !time.isAfter(config.getHoraFin());
    }

    private void publishHandoff(CrmWhatsappAiRun run, String reason) {
        Map<String, Object> event = Map.of("type", "ai.handoff.required", "conversationId",
                run.getConversation().getIdConversation(), "runId", run.getIdAiRun(), "reason", clean(reason));
        eventService.publishAfterCommit(event, event, null, true);
    }

    private MediaPayload deliveryMedia(CrmWhatsappAiRun run) {
        if (clean(run.getSuggestedMediaJson()).isBlank()) return null;
        try {
            List<Map<String, Object>> values = objectMapper.readValue(run.getSuggestedMediaJson(), new TypeReference<>() {});
            return values.stream()
                    .map(item -> mediaPayload(run, item))
                    .filter(java.util.Objects::nonNull)
                    .findFirst()
                    .orElse(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    private MediaPayload mediaPayload(CrmWhatsappAiRun run, Map<String, Object> item) {
        String type = clean(String.valueOf(item.get("type")));
        String reference = item.get("url") == null ? "" : clean(String.valueOf(item.get("url")));
        if (reference.isBlank()) return null;
        if ("SIZE_GUIDE".equals(type) && "GUIA_TALLAS".equals(clean(run.getIntent()))) {
            return new MediaPayload(CrmWhatsappAiDeliveryType.SIZE_GUIDE_IMAGE,
                    reference, mimeType(reference), fileName(reference));
        }
        if (Set.of("PRODUCT_GLOBAL_IMAGE", "PRODUCT_COLOR_IMAGE").contains(type)
                && Set.of("PRODUCTOS", "PRECIO", "STOCK", "COLORES_TALLAS")
                        .contains(clean(run.getIntent()))) {
            return new MediaPayload(CrmWhatsappAiDeliveryType.PRODUCT_IMAGE,
                    reference, mimeType(reference), fileName(reference));
        }
        return null;
    }

    private boolean isMediaDelivery(CrmWhatsappAiDeliveryType type) {
        return type == CrmWhatsappAiDeliveryType.SIZE_GUIDE_IMAGE
                || type == CrmWhatsappAiDeliveryType.PRODUCT_IMAGE;
    }

    private byte[] readMedia(String reference) {
        byte[] bytes = storageService.readBytes(reference);
        if (bytes.length == 0 || bytes.length > 10 * 1024 * 1024) {
            throw new IllegalStateException("La imagen debe pesar hasta 10 MB");
        }
        return bytes;
    }

    private String mimeType(String reference) {
        String value = clean(reference).toLowerCase(Locale.ROOT).replaceAll("[?#].*$", "");
        if (value.endsWith(".png")) return "image/png";
        if (value.endsWith(".jpg") || value.endsWith(".jpeg")) return "image/jpeg";
        return "image/webp";
    }

    private String fileName(String reference) {
        String value = clean(reference).replaceAll("[?#].*$", "");
        int slash = Math.max(value.lastIndexOf('/'), value.lastIndexOf('\\'));
        String name = slash >= 0 ? value.substring(slash + 1) : value;
        return name.isBlank() ? "imagen-producto.webp" : name;
    }

    private String clean(String value) { return value == null ? "" : value.trim(); }
    private String limit(String value, int max) { String text = clean(value); return text.length() <= max ? text : text.substring(0, max); }
    public record PreparedDelivery(Long deliveryId, Long conversationId, String text, String invalidReason,
            boolean handoff, boolean media, boolean businessNotice, boolean humanDraft,
            String mediaReference, String mediaMimeType, String mediaFileName) {}
    public record HumanDraftResponse(Long id, String text, String type, LocalDateTime createdAt) {}
    private record MediaPayload(
            CrmWhatsappAiDeliveryType deliveryType, String reference, String mimeType, String fileName) {}
}
