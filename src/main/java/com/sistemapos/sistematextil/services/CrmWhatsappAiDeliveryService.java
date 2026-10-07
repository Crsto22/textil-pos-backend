package com.sistemapos.sistematextil.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.data.domain.PageRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger log = LoggerFactory.getLogger(CrmWhatsappAiDeliveryService.class);
    private static final String HANDOFF_MESSAGE = "Claro, bella. En un momentito una asesora continuará contigo 💛";
    private static final DateTimeFormatter CUSTOMER_DATE_FORMAT = DateTimeFormatter
            .ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es-PE"));
    private static final List<String> NEW_CHAT_NOTICES = List.of(
            """
            💕 Bienvenida a Kiments

            Antes de realizar tu compra, ten en cuenta lo siguiente:

            📵 Desactiva los mensajes temporales para que podamos atenderte correctamente.
            🚚 Realizamos envíos a provincia mediante la agencia Shalom.
            🏬 También puedes recoger tu pedido en nuestro almacén de Gamarra, La Victoria.

            🔄 No realizamos cambios de talla, modelo o color.
            💳 No realizamos devoluciones de dinero, excepto cuando el inconveniente sea responsabilidad nuestra.
            ⚠️ Las prendas de liquidación no pueden combinarse con productos del catálogo regular.

            Gracias por comprender 💛
            """,
            """
            💕 Antes de comprar en Kiments

            Queremos compartirte algunas indicaciones importantes:

            📵 Recuerda desactivar los mensajes temporales para que podamos seguir correctamente tu atención.
            🚚 Enviamos pedidos a provincia por la agencia Shalom.
            🏬 Si prefieres, puedes recogerlos en nuestro almacén de Gamarra, La Victoria.

            🔄 No aceptamos cambios de talla, modelo ni color.
            💳 Las devoluciones de dinero aplican únicamente cuando el error sea responsabilidad nuestra.
            ⚠️ Los artículos de liquidación se procesan por separado del catálogo regular.

            Gracias por tenerlo en cuenta 💛
            """,
            """
            💕 Información importante para tu compra

            Para brindarte una mejor atención en Kiments:

            📵 Mantén desactivados los mensajes temporales durante la conversación.
            🚚 Los envíos a provincia se realizan mediante Shalom.
            🏬 El recojo también está disponible en nuestro almacén de Gamarra, La Victoria.

            🔄 No se realizan cambios de talla, modelo o color.
            💳 No efectuamos devoluciones de dinero, salvo que exista una equivocación de nuestra parte.
            ⚠️ Las prendas de liquidación no se juntan con productos del catálogo regular.

            Agradecemos tu comprensión 💛
            """);
    private final CrmWhatsappAiDeliveryRepository deliveryRepository;
    private final CrmWhatsappAiConfigRepository configRepository;
    private final CrmWhatsappAiMemoryRepository memoryRepository;
    private final CrmWhatsappMessageRepository messageRepository;
    private final CrmWhatsappChatService chatService;
    private final CrmWhatsappAiMemoryService memoryService;
    private final CrmWhatsappAiSaleDraftService saleDraftService;
    private final CrmWhatsappAiCommercialQueryService commercialQueryService;
    private final CrmWhatsappEventService eventService;
    private final CrmWhatsappAiOperationsService operationsService;
    private final S3StorageService storageService;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Transactional(readOnly = true)
    public Set<Long> messageIdsExcludedFromAiContext(Long conversationId) {
        if (conversationId == null) return Set.of();
        return new HashSet<>(deliveryRepository.findMessageIdsExcludedFromAiContext(conversationId));
    }

    @Transactional
    public void enqueue(CrmWhatsappAiRun run) {
        if (run == null || run.getJob() == null || !"AUTOMATIC".equals(run.getJob().getTriggerType())) return;
        if (run.getOutcome() != CrmWhatsappAiRunOutcome.DRAFT_READY || run.getRequiresHuman()) return;
        String idempotencyKey = "AI_RESPONSE:" + run.getMessage().getIdMessage();
        if (deliveryRepository.findByRun_IdAiRun(run.getIdAiRun()).isPresent()
                || deliveryRepository.findByIdempotencyKey(idempotencyKey).isPresent()) return;
        CrmWhatsappAiDelivery delivery = new CrmWhatsappAiDelivery();
        delivery.setRun(run);
        delivery.setConversation(run.getConversation());
        delivery.setSourceMessageId(run.getMessage().getIdMessage());
        delivery.setIdempotencyKey(idempotencyKey);
        boolean firstConversationResponse = !messageRepository
                .existsByConversation_IdConversationAndDirection(
                        run.getConversation().getIdConversation(), "OUTGOING");
        delivery.setInitialConversationResponse(firstConversationResponse);
        String preorderNotice = preorderNotice(run);
        if (!preorderNotice.isBlank()) {
            delivery.setTextBody(preorderNotice);
        } else if (firstConversationResponse) {
            delivery.setTextBody(newChatNotice(run.getConversation().getIdConversation()));
        }
        DeliveryMedia media = deliveryMedia(run);
        delivery.setDeliveryType(media == null
                ? CrmWhatsappAiDeliveryType.AUTOMATIC_RESPONSE
                : media.secondary() == null ? media.primary().deliveryType()
                        : CrmWhatsappAiDeliveryType.PRODUCT_IMAGE);
        if (media != null) {
            delivery.setMediaReference(media.primary().reference());
            delivery.setMediaMimeType(media.primary().mimeType());
            delivery.setMediaFileName(media.primary().fileName());
            delivery.setMediaCaption("PRODUCTOS".equals(clean(run.getIntent()))
                    && "SIZE_GUIDE".equals(media.primary().sourceType())
                    ? "Guía de tallas de " + media.primary().productName()
                    : clean(run.getDraftResponse()));
            if (media.secondary() != null) {
                delivery.setSecondaryMediaReference(media.secondary().reference());
                delivery.setSecondaryMediaMimeType(media.secondary().mimeType());
                delivery.setSecondaryMediaFileName(media.secondary().fileName());
            }
        }
        delivery.setStatus(CrmWhatsappAiDeliveryStatus.PENDING);
        int catalogCards = enqueueCatalogProductCards(run);
        delivery.setAvailableAt(LocalDateTime.now().plusSeconds(catalogCards > 0 ? catalogCards + 1L : 0L));
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

    @Transactional
    public void enqueueMediaNotice(CrmWhatsappConversation conversation, CrmWhatsappAiDeliveryType type,
            String idempotencyKey, String text, String mediaReference) {
        if (conversation == null || (type != CrmWhatsappAiDeliveryType.NEW_PRODUCT_ANNOUNCEMENT
                && type != CrmWhatsappAiDeliveryType.CATALOG_PRODUCT_CARD)
                || clean(idempotencyKey).isBlank() || clean(text).isBlank()
                || clean(mediaReference).isBlank()) return;
        if (deliveryRepository.findByIdempotencyKey(clean(idempotencyKey)).isPresent()) return;
        CrmWhatsappAiDelivery delivery = new CrmWhatsappAiDelivery();
        delivery.setConversation(conversation);
        delivery.setDeliveryType(type);
        delivery.setTextBody(clean(text));
        delivery.setIdempotencyKey(clean(idempotencyKey));
        delivery.setMediaReference(clean(mediaReference));
        delivery.setMediaMimeType(mimeType(mediaReference));
        delivery.setMediaFileName(fileName(mediaReference));
        delivery.setStatus(CrmWhatsappAiDeliveryStatus.PENDING);
        delivery.setAvailableAt(LocalDateTime.now());
        deliveryRepository.save(delivery);
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
        boolean marketingNotice = isMarketingNotice(delivery.getDeliveryType());
        boolean humanDraft = requiresHumanSend(delivery.getDeliveryType())
                || (businessNotice && (conversation.getAssignedUser() != null
                        || !"ESPERA".equals(conversation.getStatus())) && !marketingNotice);
        CrmWhatsappAiConfig config = businessNotice || conversation.getConnection() == null ? null
                : configRepository.findByConnection_IdConnection(conversation.getConnection().getIdConnection()).orElse(null);
        boolean handoff = delivery.getDeliveryType() == CrmWhatsappAiDeliveryType.HANDOFF_NOTICE;
        boolean media = isMediaDelivery(delivery.getDeliveryType());
        String invalid = marketingNotice && (conversation.getAssignedUser() != null
                || !"ESPERA".equals(conversation.getStatus()))
                ? "La conversacion ya no admite sugerencias automaticas"
                : businessNotice
                ? (clean(delivery.getTextBody()).isBlank() ? "Aviso comercial vacio"
                        : media && clean(delivery.getMediaReference()).isBlank() ? "Imagen sin archivo" : "")
                : handoff ? validateHandoff(delivery, conversation) : validate(delivery, conversation, run, config);
        return new PreparedDelivery(delivery.getIdAiDelivery(), conversation.getIdConversation(),
                businessNotice ? clean(delivery.getTextBody())
                        : handoff ? handoffMessage(run) : clean(run.getDraftResponse()),
                invalid, handoff, media, businessNotice, humanDraft,
                clean(delivery.getMediaReference()), clean(delivery.getMediaMimeType()),
                clean(delivery.getMediaFileName()),
                clean(delivery.getSecondaryMediaReference()), clean(delivery.getSecondaryMediaMimeType()),
                clean(delivery.getSecondaryMediaFileName()), delivery.getGuideOutgoingMessageId(),
                delivery.getPreludeOutgoingMessageId(), businessNotice ? "" : clean(delivery.getTextBody()),
                clean(delivery.getMediaCaption()));
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
        MessageResponse sent;
        if (!prepared.welcomeNotice().isBlank() && prepared.preludeOutgoingMessageId() == null) {
            MessageResponse notice = chatService.enviarMensajeAutomatico(
                    prepared.conversationId(), prepared.welcomeNotice());
            markPreludeSent(prepared.deliveryId(), notice.id());
        }
        if (prepared.dualProductMedia()) {
            if (prepared.guideOutgoingMessageId() == null) {
                MessageResponse guide = chatService.enviarMediaAutomatico(prepared.conversationId(),
                        readMedia(prepared.mediaReference()), prepared.mediaFileName(),
                        prepared.mediaMimeType(), prepared.guideCaption());
                markGuideSent(prepared.deliveryId(), guide.id());
            }
            sent = chatService.enviarMediaAutomatico(prepared.conversationId(),
                    readMedia(prepared.secondaryMediaReference()), prepared.secondaryMediaFileName(),
                    prepared.secondaryMediaMimeType(), prepared.text());
        } else if (prepared.productGuideWithSeparateDetails()) {
            if (prepared.guideOutgoingMessageId() == null) {
                MessageResponse guide = chatService.enviarMediaAutomatico(prepared.conversationId(),
                        readMedia(prepared.mediaReference()), prepared.mediaFileName(),
                        prepared.mediaMimeType(), prepared.guideCaption());
                markGuideSent(prepared.deliveryId(), guide.id());
            }
            sent = chatService.enviarMensajeAutomatico(prepared.conversationId(), prepared.text());
        } else {
            sent = prepared.media()
                    ? chatService.enviarMediaAutomatico(prepared.conversationId(),
                            readMedia(prepared.mediaReference()), prepared.mediaFileName(),
                            prepared.mediaMimeType(), prepared.text())
                    : chatService.enviarMensajeAutomatico(prepared.conversationId(), prepared.text());
        }
        complete(prepared.deliveryId(), sent.id());
    }

    private void markGuideSent(Long deliveryId, Long messageId) {
        CrmWhatsappAiDelivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new IllegalStateException("Entrega IA no encontrada"));
        delivery.setGuideOutgoingMessageId(messageId);
        deliveryRepository.save(delivery);
    }

    private void markPreludeSent(Long deliveryId, Long messageId) {
        CrmWhatsappAiDelivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new IllegalStateException("Entrega IA no encontrada"));
        delivery.setPreludeOutgoingMessageId(messageId);
        deliveryRepository.save(delivery);
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
        if (messageId != null && deliveryRepository.existsByOutgoingMessage_IdMessage(messageId)) {
            delivery.setStatus(CrmWhatsappAiDeliveryStatus.CANCELLED);
            delivery.setFailureReason("Respuesta automatica identica ya enviada recientemente");
            delivery.setLockedAt(null);
            deliveryRepository.save(delivery);
            return;
        }
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
        if (!handoff && !businessNotice && Boolean.TRUE.equals(delivery.getInitialConversationResponse())) {
            runOptionalFollowUp(delivery, "productos nuevos",
                    () -> enqueueNewProducts(delivery.getConversation()));
        }
        if (!handoff && !businessNotice) {
            runOptionalFollowUp(delivery, "promocion del producto",
                    () -> enqueueSameProductPromotion(delivery));
        }
        if (!handoff && !businessNotice && delivery.getRun() != null
                && Set.of("INTENCION_COMPRA", "MODIFICAR_CARRITO")
                        .contains(clean(delivery.getRun().getIntent()))) {
            runOptionalFollowUp(delivery, "promocion del carrito",
                    () -> enqueueCartPromotionSuggestion(delivery.getConversation()));
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

    private void runOptionalFollowUp(CrmWhatsappAiDelivery delivery, String label, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException error) {
            log.warn("La entrega IA {} ya fue enviada; se omitio {} para evitar reintentos: {}",
                    delivery == null ? null : delivery.getIdAiDelivery(), label, error.getMessage());
        }
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
            if (isMarketingNotice(delivery.getDeliveryType())) {
                delivery.setStatus(CrmWhatsappAiDeliveryStatus.FAILED);
            } else if (isMediaDelivery(delivery.getDeliveryType())) {
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
        if (!clean(delivery.getSecondaryMediaReference()).isBlank()
                && (clean(delivery.getSecondaryMediaMimeType()).isBlank()
                        || clean(delivery.getSecondaryMediaFileName()).isBlank())) {
            return "Imagen global del producto incompleta";
        }
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

    private String newChatNotice(Long conversationId) {
        int index = Math.floorMod(conversationId == null ? 0 : conversationId.hashCode(), NEW_CHAT_NOTICES.size());
        return NEW_CHAT_NOTICES.get(index).strip();
    }

    private String preorderNotice(CrmWhatsappAiRun run) {
        if (run == null || !"PRODUCTOS".equals(clean(run.getIntent()))
                || clean(run.getEvidenceJson()).isBlank()) return "";
        try {
            List<Map<String, Object>> evidence = objectMapper.readValue(
                    run.getEvidenceJson(), new TypeReference<>() {});
            var products = evidence.stream()
                    .filter(item -> "buscar_productos".equals(clean(String.valueOf(item.get("tool")))))
                    .filter(item -> item.get("products") instanceof List<?>)
                    .flatMap(item -> ((List<?>) item.get("products")).stream())
                    .filter(Map.class::isInstance)
                    .map(value -> (Map<?, ?>) value)
                    .filter(product -> Boolean.TRUE.equals(product.get("preventa")))
                    .toList();
            if (products.size() != 1) return "";
            Map<?, ?> product = products.getFirst();
            String name = clean(String.valueOf(product.get("name")));
            String rawDate = product.get("fechaEnvioPreventa") == null
                    ? "" : clean(String.valueOf(product.get("fechaEnvioPreventa")));
            if (name.isBlank() || rawDate.isBlank()) return "";
            LocalDate shippingDate = LocalDate.parse(rawDate);
            return preorderNoticeVariant(run.getConversation().getIdConversation(), name,
                    CUSTOMER_DATE_FORMAT.format(shippingDate),
                    CUSTOMER_DATE_FORMAT.format(shippingDate.plusDays(1)));
        } catch (RuntimeException ignored) {
            return "";
        } catch (Exception ignored) {
            return "";
        }
    }

    private String preorderNoticeVariant(Long conversationId, String product, String shippingDate, String pickupDate) {
        int index = Math.floorMod(conversationId == null ? 0 : conversationId.hashCode(), 3);
        return switch (index) {
            case 0 -> "Bonita 💝🌸, te contamos que el conjunto *" + product
                    + "* está en lanzamiento de *PREVENTA*.\n\n"
                    + "📦 Los envíos se realizarán a partir del " + shippingDate
                    + ", respetando el orden de compra.\n"
                    + "🏬 El recojo en almacén o envío por Shalom estará disponible desde el "
                    + pickupDate + ", previa coordinación.\n\n"
                    + "Gracias por tu atención y comprensión 🥰";
            case 1 -> "Bella 💕, el conjunto *" + product
                    + "* forma parte de nuestro lanzamiento en *PREVENTA*.\n\n"
                    + "📦 Empezaremos los envíos desde el " + shippingDate
                    + " según el orden en que se registren las compras.\n"
                    + "🏬 Para recojo en almacén o despacho por Shalom, podrá coordinarse desde el "
                    + pickupDate + ".\n\n"
                    + "Muchas gracias por esperar este modelito con nosotras 🌸🥰";
            default -> "Hermosa 🌸, queremos avisarte que *" + product
                    + "* se encuentra actualmente en *PREVENTA*.\n\n"
                    + "📦 La fecha de inicio de envíos es el " + shippingDate
                    + " y se atenderá de acuerdo con el orden de compra.\n"
                    + "🏬 Los recojos en almacén y envíos por Shalom podrán coordinarse a partir del "
                    + pickupDate + ".\n\n"
                    + "Agradecemos mucho tu paciencia y comprensión 💝";
        };
    }

    private boolean isBusinessNotice(CrmWhatsappAiDeliveryType type) {
        return type == CrmWhatsappAiDeliveryType.PAYMENT_REGISTERED
                || type == CrmWhatsappAiDeliveryType.PAYMENT_RETRY
                || type == CrmWhatsappAiDeliveryType.PAYMENT_REJECTED
                || type == CrmWhatsappAiDeliveryType.SALE_COMPLETED
                || isMarketingNotice(type);
    }

    private boolean isMarketingNotice(CrmWhatsappAiDeliveryType type) {
        return type == CrmWhatsappAiDeliveryType.NEW_PRODUCT_ANNOUNCEMENT
                || type == CrmWhatsappAiDeliveryType.CATALOG_PRODUCT_CARD
                || type == CrmWhatsappAiDeliveryType.PRODUCT_PROMOTION_SUGGESTION
                || type == CrmWhatsappAiDeliveryType.CART_PROMOTION_SUGGESTION;
    }

    private int enqueueCatalogProductCards(CrmWhatsappAiRun run) {
        if (run == null || run.getConversation() == null || run.getConversation().getConnection() == null
                || run.getMessage() == null || !isGenericCatalogRequest(run.getMessage().getBody())
                || !Set.of("PRODUCTOS", "ENLACE_ECOMMERCE").contains(clean(run.getIntent()))) return 0;
        CrmWhatsappAiConfig config = configRepository
                .findByConnection_IdConnection(run.getConversation().getConnection().getIdConnection()).orElse(null);
        if (config == null || !Boolean.TRUE.equals(config.getMandarCatalogoImagenes())) return 0;
        List<CrmWhatsappAiCommercialQueryService.ProductResult> products = commercialQueryService
                .latestCatalogProducts(run.getConversation(), 3);
        int queued = 0;
        LocalDateTime baseTime = LocalDateTime.now();
        for (var product : products) {
            if (clean(product.globalImageUrl()).isBlank()) continue;
            String idempotencyKey = "CATALOG_PRODUCT:" + run.getMessage().getIdMessage()
                    + ":" + product.productId();
            if (deliveryRepository.findByIdempotencyKey(idempotencyKey).isPresent()) continue;
            CrmWhatsappAiDelivery card = new CrmWhatsappAiDelivery();
            card.setConversation(run.getConversation());
            card.setSourceMessageId(run.getMessage().getIdMessage());
            card.setDeliveryType(CrmWhatsappAiDeliveryType.CATALOG_PRODUCT_CARD);
            card.setTextBody(catalogProductCaption(product));
            card.setIdempotencyKey(idempotencyKey);
            card.setMediaReference(product.globalImageUrl());
            card.setMediaMimeType(mimeType(product.globalImageUrl()));
            card.setMediaFileName(fileName(product.globalImageUrl()));
            card.setStatus(CrmWhatsappAiDeliveryStatus.PENDING);
            card.setAvailableAt(baseTime.plusNanos(queued * 200_000_000L));
            deliveryRepository.save(card);
            queued++;
        }
        return queued;
    }

    private String catalogProductCaption(CrmWhatsappAiCommercialQueryService.ProductResult product) {
        StringBuilder caption = new StringBuilder("👗 *").append(product.name()).append("*");
        if (!clean(product.description()).isBlank()) {
            caption.append("\n📝 ").append(clean(product.description()));
        }
        BigDecimal price = product.variants().stream()
                .map(CrmWhatsappAiCommercialQueryService.VariantResult::currentPrice)
                .filter(value -> value != null && value.signum() > 0)
                .min(BigDecimal::compareTo).orElse(null);
        if (price != null) {
            caption.append("\n💰 *Precio desde:* S/")
                    .append(price.setScale(2, RoundingMode.HALF_UP).toPlainString());
        }
        if (product.preventa()) {
            caption.append("\n📦 *Preventa*");
            if (product.fechaEnvioPreventa() != null) {
                caption.append("\n📅 *Envíos desde:* ")
                        .append(CUSTOMER_DATE_FORMAT.format(product.fechaEnvioPreventa()));
            }
        }
        caption.append("\n\nSi quieres conocer sus colores, tallas y más detalles, escribe *")
                .append(product.name()).append("*.");
        return caption.toString();
    }

    private boolean isGenericCatalogRequest(String message) {
        String value = Normalizer.normalize(clean(message), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", " ").replaceAll("\\s+", " ").trim();
        return value.contains("catalogo")
                || value.matches(".*\\b(que|cuales|dime que) (productos|modelos|prendas|ropa) "
                        + "(tienes|tienen|vendes|venden|ofreces|ofrecen|hay)\\b.*")
                || value.matches(".*\\b(muestra|muestrame|ensena|mandame|enviame|ver) "
                        + "(tus |los |el )?(productos|modelos|prendas|catalogo)\\b.*")
                || value.matches(".*\\bque vendes\\b.*");
    }

    private void enqueueNewProducts(CrmWhatsappConversation conversation) {
        if (conversation == null || conversation.getConnection() == null) return;
        CrmWhatsappAiConfig config = configRepository
                .findByConnection_IdConnection(conversation.getConnection().getIdConnection()).orElse(null);
        if (config == null || !Boolean.TRUE.equals(config.getMostrarProductosNuevos())) return;
        for (var product : commercialQueryService.newProducts(conversation, LocalDateTime.now().minusDays(3))) {
            StringBuilder caption = new StringBuilder("✨ *Nuevo modelo: ")
                    .append(product.name()).append("*\n\n")
                    .append("Aprovecha nuestro nuevo conjunto.");
            if (product.preorder()) {
                caption.append("\n\n📦 *Preventa*");
                if (product.preorderShippingDate() != null) {
                    caption.append("\n📅 Envíos estimados desde el ")
                            .append(CUSTOMER_DATE_FORMAT.format(product.preorderShippingDate())).append('.');
                }
            }
            caption.append("\n\nSi deseas conocer precio, colores, tallas y guía de medidas, escribe *")
                    .append(product.name()).append("*.");
            enqueueMediaNotice(conversation, CrmWhatsappAiDeliveryType.NEW_PRODUCT_ANNOUNCEMENT,
                    "NEW_PRODUCT:" + conversation.getIdConversation() + ":" + product.productId(),
                    caption.toString(), product.globalImageUrl());
        }
    }

    private void enqueueCartPromotionSuggestion(CrmWhatsappConversation conversation) {
        if (conversation == null || conversation.getConnection() == null) return;
        CrmWhatsappAiConfig config = configRepository
                .findByConnection_IdConnection(conversation.getConnection().getIdConnection()).orElse(null);
        if (config == null || !Boolean.TRUE.equals(config.getSugerirPromocionesCarrito())) return;
        var suggestion = saleDraftService.promotionSuggestion(conversation.getIdConversation());
        if (suggestion == null) return;
        enqueueNotice(conversation, CrmWhatsappAiDeliveryType.CART_PROMOTION_SUGGESTION,
                suggestion.idempotencyKey(), suggestion.text());
    }

    private void enqueueSameProductPromotion(CrmWhatsappAiDelivery delivery) {
        if (delivery == null || delivery.getRun() == null
                || delivery.getDeliveryType() != CrmWhatsappAiDeliveryType.PRODUCT_IMAGE
                || !"PRODUCTOS".equals(clean(delivery.getRun().getIntent()))) return;
        DisplayedProduct product = displayedProduct(delivery.getRun());
        if (product == null || product.productId() == null || product.name().isBlank()) return;
        var promotion = commercialQueryService.sameProductPromotion(
                delivery.getConversation(), product.productId(), 2);
        if (promotion == null || promotion.comboPrice() == null
                || promotion.comboPrice().signum() <= 0) return;
        StringBuilder message = new StringBuilder("🎁 *Promoción especial*\n\n")
                .append("Lleva 2 de *").append(product.name()).append("* por tan solo *S/")
                .append(promotion.comboPrice().setScale(2, RoundingMode.HALF_UP).toPlainString())
                .append("*.");
        if (promotion.savings() != null && promotion.savings().signum() > 0) {
            message.append("\n💰 Ahorras S/")
                    .append(promotion.savings().setScale(2, RoundingMode.HALF_UP).toPlainString()).append('.');
        }
        message.append("\n\n🛍️ Lleva 2 ahora y aprovecha este precio especial.");
        enqueueNotice(delivery.getConversation(),
                CrmWhatsappAiDeliveryType.PRODUCT_PROMOTION_SUGGESTION,
                "PRODUCT_PROMOTION:" + delivery.getSourceMessageId() + ":" + promotion.promotionId(),
                message.toString());
    }

    private DisplayedProduct displayedProduct(CrmWhatsappAiRun run) {
        if (run == null || clean(run.getSuggestedMediaJson()).isBlank()) return null;
        try {
            List<Map<String, Object>> values = objectMapper.readValue(
                    run.getSuggestedMediaJson(), new TypeReference<>() {});
            for (String preferredType : List.of("PRODUCT_GLOBAL_IMAGE", "SIZE_GUIDE")) {
                for (Map<String, Object> value : values) {
                    if (!preferredType.equals(clean(String.valueOf(value.get("type"))))) continue;
                    Integer productId = integer(value.get("productId"));
                    String name = value.get("product") == null
                            ? "" : clean(String.valueOf(value.get("product")));
                    if (productId != null && !name.isBlank()) return new DisplayedProduct(productId, name);
                }
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
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

    private DeliveryMedia deliveryMedia(CrmWhatsappAiRun run) {
        if (clean(run.getSuggestedMediaJson()).isBlank()) return null;
        try {
            List<Map<String, Object>> values = objectMapper.readValue(run.getSuggestedMediaJson(), new TypeReference<>() {});
            List<MediaPayload> payloads = values.stream()
                    .map(item -> mediaPayload(run, item))
                    .filter(java.util.Objects::nonNull)
                    .toList();
            if (payloads.isEmpty()) return null;
            MediaPayload global = payloads.stream()
                    .filter(payload -> "PRODUCT_GLOBAL_IMAGE".equals(payload.sourceType()))
                    .findFirst().orElse(null);
            if (global != null) {
                MediaPayload guide = payloads.stream()
                        .filter(payload -> "SIZE_GUIDE".equals(payload.sourceType()))
                        .filter(payload -> global.productId() != null
                                && global.productId().equals(payload.productId()))
                        .findFirst().orElse(null);
                if (guide != null) return new DeliveryMedia(guide, global);
                return new DeliveryMedia(global, null);
            }
            return new DeliveryMedia(payloads.getFirst(), null);
        } catch (Exception ignored) {
            return null;
        }
    }

    private MediaPayload mediaPayload(CrmWhatsappAiRun run, Map<String, Object> item) {
        String type = clean(String.valueOf(item.get("type")));
        String reference = item.get("url") == null ? "" : clean(String.valueOf(item.get("url")));
        String product = item.get("product") == null ? "" : clean(String.valueOf(item.get("product")));
        if (reference.isBlank()) return null;
        Integer productId = integer(item.get("productId"));
        if ("SIZE_GUIDE".equals(type)
                && ("GUIA_TALLAS".equals(clean(run.getIntent())) || isProductIntent(run.getIntent()))) {
            return new MediaPayload(CrmWhatsappAiDeliveryType.SIZE_GUIDE_IMAGE,
                    type, productId, product, reference, mimeType(reference), fileName(reference));
        }
        if (Set.of("PRODUCT_GLOBAL_IMAGE", "PRODUCT_COLOR_IMAGE").contains(type)
                && isProductIntent(run.getIntent())) {
            return new MediaPayload(CrmWhatsappAiDeliveryType.PRODUCT_IMAGE,
                    type, productId, product, reference, mimeType(reference), fileName(reference));
        }
        return null;
    }

    private boolean isProductIntent(String intent) {
        return Set.of("PRODUCTOS", "PRECIO", "STOCK", "COLORES_TALLAS").contains(clean(intent));
    }

    private Integer integer(Object value) {
        if (value instanceof Number number) return number.intValue();
        try { return Integer.valueOf(clean(String.valueOf(value))); }
        catch (NumberFormatException ignored) { return null; }
    }

    private boolean isMediaDelivery(CrmWhatsappAiDeliveryType type) {
        return type == CrmWhatsappAiDeliveryType.SIZE_GUIDE_IMAGE
                || type == CrmWhatsappAiDeliveryType.PRODUCT_IMAGE
                || type == CrmWhatsappAiDeliveryType.NEW_PRODUCT_ANNOUNCEMENT
                || type == CrmWhatsappAiDeliveryType.CATALOG_PRODUCT_CARD;
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
            String mediaReference, String mediaMimeType, String mediaFileName,
            String secondaryMediaReference, String secondaryMediaMimeType, String secondaryMediaFileName,
            Long guideOutgoingMessageId, Long preludeOutgoingMessageId, String welcomeNotice,
            String guideCaption) {
        boolean dualProductMedia() { return !secondaryMediaReference.isBlank(); }
        boolean productGuideWithSeparateDetails() {
            return media && secondaryMediaReference.isBlank() && !guideCaption.isBlank()
                    && !text.trim().equalsIgnoreCase(guideCaption.trim());
        }
    }
    public record HumanDraftResponse(Long id, String text, String type, LocalDateTime createdAt) {}
    private record MediaPayload(
            CrmWhatsappAiDeliveryType deliveryType, String sourceType, Integer productId, String productName,
            String reference, String mimeType, String fileName) {}
    private record DeliveryMedia(MediaPayload primary, MediaPayload secondary) {}
    private record DisplayedProduct(Integer productId, String name) {}
}
