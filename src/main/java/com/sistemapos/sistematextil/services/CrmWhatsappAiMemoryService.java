package com.sistemapos.sistematextil.services;

import java.time.LocalDateTime;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sistemapos.sistematextil.model.*;
import com.sistemapos.sistematextil.repositories.*;
import com.sistemapos.sistematextil.services.CrmWhatsappAiEngineService.ProcessingResult;
import com.sistemapos.sistematextil.services.ai.AiModelProvider.SaleActionResult;
import com.sistemapos.sistematextil.services.ai.AiModelProvider.Usage;
import com.sistemapos.sistematextil.util.usuario.Rol;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CrmWhatsappAiMemoryService {
    private static final int TTL_HOURS = 24;
    private static final Pattern QUANTITY = Pattern.compile("(?<!\\d)(\\d{1,2})(?!\\d)");
    private static final Pattern EXPLICIT_COLOR = Pattern.compile(
            "\\bcolor\\s+(.+?)(?=\\s+\\b(?:talla|cantidad|unidades?)\\b|$)");
    private static final Pattern EXPLICIT_SIZE = Pattern.compile(
            "\\btalla\\s+(xxl|xl|xs|s|m|l)\\b");
    private static final Pattern EXPLICIT_QUANTITY = Pattern.compile(
            "\\b(?:cantidad|unidades?)\\s+(\\d{1,2})\\b");

    private final CrmWhatsappAiMemoryRepository memoryRepository;
    private final CrmWhatsappConversationRepository conversationRepository;
    private final CrmWhatsappAiJobRepository jobRepository;
    private final CrmWhatsappAiDeliveryRepository deliveryRepository;
    private final CrmWhatsappEventService eventService;
    private final CrmWhatsappAiSaleDraftService saleDraftService;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Transactional
    public void registerIncoming(CrmWhatsappConversation conversation, CrmWhatsappMessage message) {
        CrmWhatsappAiMemory memory = getOrCreate(conversation);
        expireIfNeeded(memory);
        if (saleDraftService.blocksAutomation(conversation.getIdConversation())) {
            memory.setAttentionState(CrmWhatsappAiAttentionState.HUMANA);
            memory.setLastIncomingMessageId(message.getIdMessage());
            memory.setExpiresAt(LocalDateTime.now().plusHours(TTL_HOURS));
            memoryRepository.save(memory);
            publish(memory);
            return;
        }
        CrmWhatsappAiAttentionState effectiveState = effectiveState(conversation);
        memory.setAttentionState(effectiveState);
        memory.setLastIncomingMessageId(message.getIdMessage());
        memory.setExpiresAt(LocalDateTime.now().plusHours(TTL_HOURS));
        memoryRepository.save(memory);
        publish(memory);
    }

    @Transactional
    public void updateFromRun(CrmWhatsappAiRun run, ProcessingResult result) {
        updateFromRun(run, result, null);
    }

    @Transactional
    public void updateFromRun(CrmWhatsappAiRun run, ProcessingResult result, String customerInput) {
        if (result == null || result.outcome() == CrmWhatsappAiRunOutcome.SKIPPED || result.superseded()) return;
        CrmWhatsappAiMemory memory = getOrCreate(run.getConversation());
        expireIfNeeded(memory);
        Long messageId = run.getMessage().getIdMessage();
        if (memory.getLastIncomingMessageId() != null && messageId != null
                && messageId < memory.getLastIncomingMessageId()) {
            return;
        }
        memory.setCurrentIntent(clean(result.intent()));
        if (result.outcome() == CrmWhatsappAiRunOutcome.DRAFT_READY
                && "SALUDO".equalsIgnoreCase(clean(result.intent()))
                && !clean(result.draft()).isBlank()) {
            memory.setGreetingSentAt(LocalDateTime.now());
        }
        memory.setLastIncomingMessageId(messageId);
        memory.setExpiresAt(LocalDateTime.now().plusHours(TTL_HOURS));
        String effectiveInput = clean(customerInput).isBlank() ? run.getMessage().getBody() : customerInput;
        extractCommercialContext(memory, effectiveInput, result.evidence());
        CrmWhatsappAiPendingQuestion previousPending = pendingQuestion(memory);
        CrmWhatsappAiPendingQuestion inferredPending = inferPendingQuestion(result.draft(), result.intent());
        memory.setPendingQuestion(shouldPreservePending(previousPending, result.intent())
                ? previousPending : inferredPending);
        memoryRepository.save(memory);
        publish(memory);
    }

    @Transactional
    public void markAutomaticSent(Long conversationId, Long outgoingMessageId) {
        memoryRepository.findForUpdate(conversationId).ifPresent(memory -> {
            memory.setLastAiMessageId(outgoingMessageId);
            memory.setConsecutiveAutoResponses(memory.getConsecutiveAutoResponses() + 1);
            memory.setExpiresAt(LocalDateTime.now().plusHours(TTL_HOURS));
            memoryRepository.save(memory);
            publish(memory);
        });
    }

    @Transactional
    public MemoryResponse pauseForHuman(CrmWhatsappConversation conversation, String reason) {
        CrmWhatsappAiMemory memory = getOrCreate(conversation);
        memory.setAttentionState(CrmWhatsappAiAttentionState.HUMANA);
        clearPending(memory);
        memoryRepository.save(memory);
        jobRepository.supersedePending(conversation.getIdConversation(), CrmWhatsappAiJobStatus.PENDING,
                CrmWhatsappAiJobStatus.SUPERSEDED, LocalDateTime.now());
        deliveryRepository.cancelForConversation(conversation.getIdConversation(),
                List.of(CrmWhatsappAiDeliveryStatus.PENDING, CrmWhatsappAiDeliveryStatus.SENDING),
                List.of(CrmWhatsappAiDeliveryType.AUTOMATIC_RESPONSE,
                        CrmWhatsappAiDeliveryType.PRODUCT_IMAGE,
                        CrmWhatsappAiDeliveryType.SIZE_GUIDE_IMAGE,
                        CrmWhatsappAiDeliveryType.NEW_PRODUCT_ANNOUNCEMENT,
                        CrmWhatsappAiDeliveryType.CATALOG_PRODUCT_CARD,
                        CrmWhatsappAiDeliveryType.PRODUCT_PROMOTION_SUGGESTION,
                        CrmWhatsappAiDeliveryType.CART_PROMOTION_SUGGESTION,
                        CrmWhatsappAiDeliveryType.HANDOFF_NOTICE),
                CrmWhatsappAiDeliveryStatus.CANCELLED, clean(reason));
        publish(memory);
        return response(memory);
    }

    @Transactional
    public MemoryResponse resumeAutomatic(CrmWhatsappConversation conversation) {
        CrmWhatsappAiMemory memory = getOrCreate(conversation);
        memory.setAttentionState(CrmWhatsappAiAttentionState.AUTOMATICA);
        memory.setConsecutiveAutoResponses(0);
        memory.setExpiresAt(LocalDateTime.now().plusHours(TTL_HOURS));
        memoryRepository.save(memory);
        publish(memory);
        return response(memory);
    }

    @Transactional
    public void pauseResolved(CrmWhatsappConversation conversation) {
        CrmWhatsappAiMemory memory = getOrCreate(conversation);
        memory.setAttentionState(CrmWhatsappAiAttentionState.PAUSADA);
        clearPending(memory);
        memoryRepository.save(memory);
        deliveryRepository.cancelForConversation(conversation.getIdConversation(),
                List.of(CrmWhatsappAiDeliveryStatus.PENDING, CrmWhatsappAiDeliveryStatus.SENDING),
                List.of(CrmWhatsappAiDeliveryType.AUTOMATIC_RESPONSE,
                        CrmWhatsappAiDeliveryType.PRODUCT_IMAGE,
                        CrmWhatsappAiDeliveryType.SIZE_GUIDE_IMAGE,
                        CrmWhatsappAiDeliveryType.NEW_PRODUCT_ANNOUNCEMENT,
                        CrmWhatsappAiDeliveryType.CATALOG_PRODUCT_CARD,
                        CrmWhatsappAiDeliveryType.PRODUCT_PROMOTION_SUGGESTION,
                        CrmWhatsappAiDeliveryType.CART_PROMOTION_SUGGESTION,
                        CrmWhatsappAiDeliveryType.HANDOFF_NOTICE),
                CrmWhatsappAiDeliveryStatus.CANCELLED, "Conversacion resuelta");
        publish(memory);
    }

    @Transactional(readOnly = true)
    public String contextFor(Long conversationId) {
        return memoryRepository.findByConversation_IdConversation(conversationId)
                .filter(memory -> memory.getExpiresAt().isAfter(LocalDateTime.now()))
                .map(memory -> "Contexto recordado: intencion=" + clean(memory.getCurrentIntent())
                        + ", pregunta_pendiente=" + pendingQuestion(memory).name()
                        + ", producto=" + clean(memory.getProductName())
                        + ", color=" + clean(memory.getColor())
                        + ", talla=" + clean(memory.getSize())
                        + ", cantidad=" + (memory.getQuantity() == null ? "" : memory.getQuantity())
                        + ", carrito=" + memory.getItems().stream().map(item -> clean(item.getProductName())
                                + " " + clean(item.getColor()) + " " + clean(item.getSize()) + " x" + item.getQuantity()).toList())
                .orElse("");
    }

    @Transactional(readOnly = true)
    public String rememberedProductName(Long conversationId) {
        return memoryRepository.findByConversation_IdConversation(conversationId)
                .filter(memory -> memory.getExpiresAt() != null
                        && memory.getExpiresAt().isAfter(LocalDateTime.now()))
                .map(CrmWhatsappAiMemory::getProductName)
                .map(this::clean)
                .orElse("");
    }

    @Transactional(readOnly = true)
    public MemorySelection selectionFor(Long conversationId) {
        return memoryRepository.findByConversation_IdConversation(conversationId)
                .filter(memory -> memory.getExpiresAt() != null
                        && memory.getExpiresAt().isAfter(LocalDateTime.now()))
                .map(memory -> new MemorySelection(memory.getProductId(), clean(memory.getProductName()),
                        clean(memory.getColor()), clean(memory.getSize()), memory.getQuantity()))
                .orElse(MemorySelection.empty());
    }

    @Transactional(readOnly = true)
    public CrmWhatsappAiPendingQuestion pendingQuestionFor(Long conversationId) {
        return memoryRepository.findByConversation_IdConversation(conversationId)
                .filter(memory -> memory.getExpiresAt() != null
                        && memory.getExpiresAt().isAfter(LocalDateTime.now()))
                .map(this::pendingQuestion)
                .orElse(CrmWhatsappAiPendingQuestion.NONE);
    }

    @Transactional(readOnly = true)
    public boolean greetingSentInCurrentSession(Long conversationId) {
        return memoryRepository.findByConversation_IdConversation(conversationId)
                .filter(memory -> memory.getGreetingSentAt() != null)
                .filter(memory -> memory.getExpiresAt() == null
                        || memory.getExpiresAt().isAfter(LocalDateTime.now()))
                .isPresent();
    }

    @Transactional
    public PendingReplyResolution resolvePendingReply(
            CrmWhatsappConversation conversation, Long messageId, String customerMessage) {
        if (conversation == null || messageId == null || clean(customerMessage).isBlank()) return null;
        CrmWhatsappAiMemory memory = memoryRepository.findForUpdate(conversation.getIdConversation()).orElse(null);
        if (memory == null || memory.getExpiresAt() == null || memory.getExpiresAt().isBefore(LocalDateTime.now())) {
            return null;
        }
        if (messageId.equals(memory.getLastPendingReplyMessageId())
                && !clean(memory.getLastPendingReplyResponse()).isBlank()) {
            return new PendingReplyResolution(
                    clean(memory.getLastPendingReplyIntent()), memory.getLastPendingReplyResponse(), false);
        }

        CrmWhatsappAiPendingQuestion pending = pendingQuestion(memory);
        String normalized = normalize(customerMessage);
        CrmWhatsappAiSaleDraftService.ActionOutcome outcome = null;
        String intent = "MODIFICAR_CARRITO";

        if (pending == CrmWhatsappAiPendingQuestion.QUANTITY && isQuantityOnlyReply(normalized)) {
            Integer quantity = parseQuantity(normalized);
            if (quantity != null && !clean(memory.getProductName()).isBlank()
                    && !clean(memory.getColor()).isBlank() && !clean(memory.getSize()).isBlank()) {
                outcome = saleDraftService.applyAiAction(conversation, saleAction(
                        "ADD", memory.getProductName(), memory.getColor(), memory.getSize(), quantity));
            }
        } else if (pending == CrmWhatsappAiPendingQuestion.ORDER_CONFIRMATION) {
            if (isAffirmative(normalized)) {
                intent = "CONFIRMAR_PEDIDO";
                outcome = saleDraftService.applyAiAction(conversation, saleAction(
                        "CONFIRM", "", "", "", null));
            } else if (isNegative(normalized)) {
                intent = "CANCELAR_PEDIDO";
                outcome = saleDraftService.applyAiAction(conversation, saleAction(
                        "CANCEL", "", "", "", null));
            }
        } else if (pending == CrmWhatsappAiPendingQuestion.COLOR
                && isShortReply(normalized) && !clean(memory.getProductName()).isBlank()
                && saleDraftService.matchesPendingAttribute(conversation, memory.getProductName(), customerMessage,
                        CrmWhatsappAiPendingQuestion.COLOR)) {
            intent = "INTENCION_COMPRA";
            outcome = saleDraftService.applyAiAction(conversation, saleAction(
                    "ADD", memory.getProductName(), customerMessage, memory.getSize(), memory.getQuantity()));
        } else if (pending == CrmWhatsappAiPendingQuestion.SIZE
                && isShortReply(normalized) && !clean(memory.getProductName()).isBlank()
                && saleDraftService.matchesPendingAttribute(conversation, memory.getProductName(), customerMessage,
                        CrmWhatsappAiPendingQuestion.SIZE)) {
            intent = "INTENCION_COMPRA";
            outcome = saleDraftService.applyAiAction(conversation, saleAction(
                    "ADD", memory.getProductName(), memory.getColor(), customerMessage, memory.getQuantity()));
        } else if (pending == CrmWhatsappAiPendingQuestion.PRODUCT && isAffirmative(normalized)) {
            return cachePendingReply(memory, messageId, "PRODUCTOS",
                    "¿Qué información deseas consultar: colores, tallas o precio?");
        } else if (pending == CrmWhatsappAiPendingQuestion.CATALOG_PRODUCT && !normalized.isBlank()
                && saleDraftService.matchesCatalogProduct(conversation, customerMessage)) {
            if ("INTENCION_COMPRA".equalsIgnoreCase(clean(memory.getCurrentIntent()))) {
                intent = "INTENCION_COMPRA";
                memory.setProductName(clean(customerMessage));
                outcome = saleDraftService.applyAiAction(conversation, saleAction(
                        "ADD", customerMessage, memory.getColor(), memory.getSize(), memory.getQuantity()));
            } else {
            return new PendingReplyResolution("PRODUCTOS", "", false, customerMessage);
            }
        } else if (pending == CrmWhatsappAiPendingQuestion.CATALOG_CONFIRMATION
                && isAffirmative(normalized)) {
            return new PendingReplyResolution("PRODUCTOS", "", true, "");
        } else if (pending == CrmWhatsappAiPendingQuestion.COMBO_ITEM && isAffirmative(normalized)) {
            return cachePendingReply(memory, messageId, "PROMOCIONES",
                    "Indícame el producto, color y talla que deseas para completar la promoción.");
        }

        if (outcome == null || clean(outcome.response()).isBlank()) return null;
        return cachePendingReply(memory, messageId, intent, outcome.response());
    }

    private PendingReplyResolution cachePendingReply(
            CrmWhatsappAiMemory memory, Long messageId, String intent, String response) {
        memory.setCurrentIntent(intent);
        memory.setLastPendingReplyMessageId(messageId);
        memory.setLastPendingReplyIntent(intent);
        memory.setLastPendingReplyResponse(response);
        memory.setPendingQuestion(inferPendingQuestion(response, intent));
        memory.setExpiresAt(LocalDateTime.now().plusHours(TTL_HOURS));
        memoryRepository.save(memory);
        publish(memory);
        return new PendingReplyResolution(intent, response, false);
    }

    private SaleActionResult saleAction(
            String action, String product, String color, String size, Integer quantity) {
        return new SaleActionResult(action, product, null, clean(color), clean(size), quantity,
                "", 100, "Respuesta resuelta desde el contexto pendiente", Usage.empty());
    }

    @Transactional(readOnly = true)
    public MemoryResponse get(Long conversationId, Usuario actor) {
        CrmWhatsappConversation conversation = requireConversation(conversationId, actor);
        return memoryRepository.findByConversation_IdConversation(conversationId)
                .map(this::response)
                .orElseGet(() -> emptyResponse(conversation));
    }

    @Transactional
    public MemoryResponse clear(Long conversationId, Usuario actor) {
        CrmWhatsappConversation conversation = requireConversation(conversationId, actor);
        CrmWhatsappAiMemory memory = getOrCreate(conversation);
        clearContext(memory);
        memory.setAttentionState(effectiveState(conversation));
        memoryRepository.save(memory);
        publish(memory);
        return response(memory);
    }

    @Transactional
    public int deleteAllActiveMemories() {
        return memoryRepository.deleteAllAiMemories();
    }

    @Transactional
    public void clearAfterSale(Long conversationId) {
        memoryRepository.findForUpdate(conversationId).ifPresent(memory -> {
            clearContext(memory);
            memory.setAttentionState(effectiveState(memory.getConversation()));
            memoryRepository.save(memory);
            publish(memory);
        });
    }

    private void extractCommercialContext(CrmWhatsappAiMemory memory, String body, List<Map<String, Object>> evidence) {
        if (evidence == null) return;
        JsonNode productEvidence = evidence.stream().<JsonNode>map(value -> objectMapper.valueToTree(value))
                .filter(node -> "buscar_productos".equals(node.path("tool").asText()))
                .filter(node -> node.path("products").isArray() && node.path("products").size() == 1)
                .findFirst().orElse(null);
        if (productEvidence == null) {
            rememberAmbiguousPurchaseAttributes(memory, body, evidence);
            return;
        }
        JsonNode product = productEvidence.path("products").get(0);
        Integer productId = nullableInt(product, "productId");
        String productName = product.path("name").asText("");
        boolean productChanged = memory.getProductId() != null && productId != null
                ? !memory.getProductId().equals(productId)
                : !clean(memory.getProductName()).isBlank()
                        && !normalize(memory.getProductName()).equals(normalize(productName));
        if (productChanged) {
            memory.setColor(null);
            memory.setSize(null);
            memory.setVariantId(null);
            memory.setQuantity(null);
        }
        memory.setProductId(productId);
        memory.setProductName(productName);

        List<JsonNode> variants = new ArrayList<>();
        Map<String, String> colors = new LinkedHashMap<>();
        Map<String, String> sizes = new LinkedHashMap<>();
        for (JsonNode variant : product.path("variants")) {
            variants.add(variant);
            String color = clean(variant.path("color").asText(""));
            String size = clean(variant.path("size").asText(""));
            if (!color.isBlank()) colors.putIfAbsent(normalize(color), color);
            if (!size.isBlank()) sizes.putIfAbsent(normalize(size), size);
        }

        String mentionedColor = matchingCatalogValue(body, colors);
        String mentionedSize = matchingCatalogValue(body, sizes);
        if (!mentionedColor.isBlank()) {
            memory.setColor(mentionedColor);
            memory.setVariantId(null);
        }
        if (!mentionedSize.isBlank()) {
            memory.setSize(mentionedSize);
            memory.setVariantId(null);
        }
        if (!clean(memory.getColor()).isBlank() && !colors.containsKey(normalize(memory.getColor()))) {
            memory.setColor(null);
            memory.setVariantId(null);
        }
        if (!clean(memory.getSize()).isBlank() && !sizes.containsKey(normalize(memory.getSize()))) {
            memory.setSize(null);
            memory.setVariantId(null);
        }

        if (!clean(memory.getColor()).isBlank() && !clean(memory.getSize()).isBlank()) {
            List<JsonNode> matches = variants.stream()
                    .filter(variant -> normalize(variant.path("color").asText(""))
                            .equals(normalize(memory.getColor())))
                    .filter(variant -> normalize(variant.path("size").asText(""))
                            .equals(normalize(memory.getSize())))
                    .toList();
            if (matches.size() == 1) {
                JsonNode selected = matches.getFirst();
                memory.setVariantId(nullableInt(selected, "variantId"));
                memory.setColor(selected.path("color").asText(memory.getColor()));
                memory.setSize(selected.path("size").asText(memory.getSize()));
            } else {
                memory.setVariantId(null);
            }
        } else {
            memory.setVariantId(null);
        }
        Matcher quantity = QUANTITY.matcher(normalize(body));
        if (quantity.find()) memory.setQuantity(Math.max(1, Math.min(99, Integer.parseInt(quantity.group(1)))));
        if (containsCartIntent(normalize(body).toUpperCase(Locale.ROOT)) && memory.getVariantId() != null) upsertItem(memory);
    }

    private void rememberAmbiguousPurchaseAttributes(
            CrmWhatsappAiMemory memory,
            String body,
            List<Map<String, Object>> evidence) {
        if (!"INTENCION_COMPRA".equalsIgnoreCase(clean(memory.getCurrentIntent()))) return;
        boolean ambiguous = evidence.stream()
                .filter(item -> "buscar_productos".equals(clean(String.valueOf(item.get("tool")))))
                .anyMatch(item -> "AMBIGUOUS".equalsIgnoreCase(clean(String.valueOf(item.get("resolution")))));
        if (!ambiguous) return;
        String normalized = normalize(body).replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ").trim();
        Matcher color = EXPLICIT_COLOR.matcher(normalized);
        Matcher size = EXPLICIT_SIZE.matcher(normalized);
        Matcher quantity = EXPLICIT_QUANTITY.matcher(normalized);
        memory.setProductId(null);
        memory.setVariantId(null);
        memory.setProductName(null);
        memory.setColor(color.find() ? clean(color.group(1)) : null);
        memory.setSize(size.find() ? clean(size.group(1)).toUpperCase(Locale.ROOT) : null);
        memory.setQuantity(quantity.find()
                ? Math.max(1, Math.min(99, Integer.parseInt(quantity.group(1))))
                : null);
    }

    private String matchingCatalogValue(String body, Map<String, String> values) {
        String normalizedBody = " " + normalize(body).replaceAll("[^a-z0-9]+", " ").trim() + " ";
        String exact = values.entrySet().stream()
                .filter(entry -> normalizedBody.contains(" " + entry.getKey() + " "))
                .sorted(Comparator.comparingInt((Map.Entry<String, String> entry) -> entry.getKey().length()).reversed())
                .map(Map.Entry::getValue)
                .findFirst().orElse("");
        if (!exact.isBlank()) return exact;
        List<String> bodyTokens = List.of(normalizedBody.trim().split("\\s+"));
        List<String> matches = values.entrySet().stream()
                .filter(entry -> List.of(entry.getKey().split("\\s+")).stream()
                        .filter(token -> token.length() >= 3)
                        .anyMatch(bodyTokens::contains))
                .map(Map.Entry::getValue)
                .distinct()
                .toList();
        return matches.size() == 1 ? matches.getFirst() : "";
    }

    private String normalize(String value) {
        return Normalizer.normalize(clean(value).toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private void upsertItem(CrmWhatsappAiMemory memory) {
        CrmWhatsappAiMemoryItem item = memory.getItems().stream()
                .filter(current -> java.util.Objects.equals(current.getVariantId(), memory.getVariantId())
                        && java.util.Objects.equals(clean(current.getProductName()), clean(memory.getProductName())))
                .findFirst().orElseGet(() -> { var created = new CrmWhatsappAiMemoryItem(); created.setMemory(memory); memory.getItems().add(created); return created; });
        item.setProductId(memory.getProductId());
        item.setVariantId(memory.getVariantId());
        item.setProductName(memory.getProductName());
        item.setColor(memory.getColor());
        item.setSize(memory.getSize());
        item.setQuantity(memory.getQuantity() == null ? 1 : memory.getQuantity());
    }

    private boolean containsCartIntent(String value) {
        return value.contains("QUIERO") || value.contains("AGREGA") || value.contains("LLEVO")
                || value.contains("COMPRAR") || value.contains("PEDIDO");
    }

    private Integer nullableInt(JsonNode node, String field) { return node.hasNonNull(field) ? node.path(field).asInt() : null; }

    private CrmWhatsappAiMemory getOrCreate(CrmWhatsappConversation conversation) {
        Long conversationId = conversation.getIdConversation();
        CrmWhatsappConversation lockedConversation = conversationRepository.findForUpdateById(conversationId)
                .orElseThrow(() -> new IllegalStateException(
                        "No se encontro la conversacion al actualizar su memoria IA: " + conversationId));
        return memoryRepository.findForUpdate(conversationId).orElseGet(() -> {
            CrmWhatsappAiMemory memory = new CrmWhatsappAiMemory();
            memory.setConversation(lockedConversation);
            memory.setAttentionState(effectiveState(lockedConversation));
            memory.setExpiresAt(LocalDateTime.now().plusHours(TTL_HOURS));
            return memory;
        });
    }

    private void expireIfNeeded(CrmWhatsappAiMemory memory) {
        if (memory.getExpiresAt() != null && memory.getExpiresAt().isBefore(LocalDateTime.now())) clearContext(memory);
    }

    private void clearContext(CrmWhatsappAiMemory memory) {
        memory.setCurrentIntent(null); memory.setProductId(null); memory.setVariantId(null);
        memory.setProductName(null); memory.setColor(null); memory.setSize(null); memory.setQuantity(null);
        memory.setPendingQuestion(CrmWhatsappAiPendingQuestion.NONE);
        memory.setLastPendingReplyMessageId(null); memory.setLastPendingReplyIntent(null);
        memory.setLastPendingReplyResponse(null);
        memory.setLastIncomingMessageId(null); memory.setLastAiMessageId(null);
        memory.setGreetingSentAt(null);
        memory.setConsecutiveAutoResponses(0); memory.getItems().clear();
        memory.setExpiresAt(LocalDateTime.now().plusHours(TTL_HOURS));
    }

    private void clearPending(CrmWhatsappAiMemory memory) {
        memory.setPendingQuestion(CrmWhatsappAiPendingQuestion.NONE);
        memory.setLastPendingReplyMessageId(null);
        memory.setLastPendingReplyIntent(null);
        memory.setLastPendingReplyResponse(null);
    }

    private CrmWhatsappConversation requireConversation(Long id, Usuario actor) {
        CrmWhatsappConversation conversation = conversationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversacion no encontrada"));
        boolean admin = actor != null && actor.getRol() == Rol.ADMINISTRADOR;
        boolean assigned = actor != null && conversation.getAssignedUser() != null
                && actor.getIdUsuario().equals(conversation.getAssignedUser().getIdUsuario());
        boolean waiting = "ESPERA".equals(conversation.getStatus()) && conversation.getAssignedUser() == null;
        if (!admin && !assigned && !waiting) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puedes ver esta memoria");
        }
        return conversation;
    }

    private MemoryResponse response(CrmWhatsappAiMemory memory) {
        return new MemoryResponse(attentionMode(memory.getConversation()).name(), memory.getAttentionState().name(), clean(memory.getCurrentIntent()),
                pendingQuestion(memory).name(),
                memory.getProductId(), memory.getVariantId(), clean(memory.getProductName()), clean(memory.getColor()),
                clean(memory.getSize()), memory.getQuantity(), memory.getConsecutiveAutoResponses(), memory.getExpiresAt(),
                memory.getItems().stream().map(item -> new MemoryItemResponse(item.getProductId(), item.getVariantId(),
                        item.getProductName(), clean(item.getColor()), clean(item.getSize()), item.getQuantity())).toList());
    }

    private MemoryResponse emptyResponse(CrmWhatsappConversation conversation) {
        return new MemoryResponse(attentionMode(conversation).name(), effectiveState(conversation).name(), "", CrmWhatsappAiPendingQuestion.NONE.name(), null, null,
                "", "", "", null, 0, null, List.of());
    }

    private CrmWhatsappAiAttentionMode attentionMode(CrmWhatsappConversation conversation) {
        return conversation.getAiAttentionMode() == null
                ? CrmWhatsappAiAttentionMode.AUTOMATICA
                : conversation.getAiAttentionMode();
    }

    private CrmWhatsappAiAttentionState effectiveState(CrmWhatsappConversation conversation) {
        if ("RESUELTO".equals(conversation.getStatus())) return CrmWhatsappAiAttentionState.PAUSADA;
        if (attentionMode(conversation) == CrmWhatsappAiAttentionMode.HUMANA
                || conversation.getAssignedUser() != null) return CrmWhatsappAiAttentionState.HUMANA;
        return CrmWhatsappAiAttentionState.AUTOMATICA;
    }

    private void publish(CrmWhatsappAiMemory memory) {
        Map<String, Object> event = Map.of("type", "ai.memory.updated", "conversationId",
                memory.getConversation().getIdConversation(), "memory", response(memory));
        Integer assigned = memory.getConversation().getAssignedUser() == null ? null
                : memory.getConversation().getAssignedUser().getIdUsuario();
        eventService.publishAfterCommit(event, event, assigned, assigned == null);
    }

    private String clean(String value) { return value == null ? "" : value.trim(); }

    private CrmWhatsappAiPendingQuestion pendingQuestion(CrmWhatsappAiMemory memory) {
        return memory.getPendingQuestion() == null ? CrmWhatsappAiPendingQuestion.NONE : memory.getPendingQuestion();
    }

    private CrmWhatsappAiPendingQuestion inferPendingQuestion(String response, String intent) {
        String value = normalize(response);
        if (value.isBlank()) return CrmWhatsappAiPendingQuestion.NONE;
        if (value.contains("de que producto")
                && (value.contains("guia de tallas") || value.contains("tabla de medidas"))) {
            return CrmWhatsappAiPendingQuestion.SIZE_GUIDE_PRODUCT;
        }
        boolean asksWhichModel = value.contains("cual deseas")
                || value.matches(".*\\bcual\\b.*\\bmodelos?\\b.*\\bdeseas\\b.*");
        if ("GUIA_TALLAS".equals(clean(intent).toUpperCase(Locale.ROOT))
                && value.contains("varios modelos") && asksWhichModel) {
            return CrmWhatsappAiPendingQuestion.SIZE_GUIDE_PRODUCT;
        }
        if (value.contains("varios modelos") && asksWhichModel) {
            return CrmWhatsappAiPendingQuestion.CATALOG_PRODUCT;
        }
        if (value.contains("deseas que te muestre el catalogo")) {
            return CrmWhatsappAiPendingQuestion.CATALOG_CONFIRMATION;
        }
        if (value.contains("nombre completo") && value.contains("celular")) return CrmWhatsappAiPendingQuestion.CUSTOMER_NAME;
        if (value.contains("nombre completo")) return CrmWhatsappAiPendingQuestion.CUSTOMER_NAME;
        if (value.contains("celular peruano")) return CrmWhatsappAiPendingQuestion.CUSTOMER_PHONE;
        if (value.contains("varias cuentas") || value.contains("que cuenta")) return CrmWhatsappAiPendingQuestion.PAYMENT_ACCOUNT;
        if (value.contains("como deseas pagar") || value.contains("elige un metodo")) return CrmWhatsappAiPendingQuestion.PAYMENT_METHOD;
        if (value.contains("confirmas este pedido")
                || value.contains("cuando desees continuar puedes confirmarlo")) {
            return CrmWhatsappAiPendingQuestion.ORDER_CONFIRMATION;
        }
        if (value.contains("para completar el combo") || value.contains("completar la promocion")) return CrmWhatsappAiPendingQuestion.COMBO_ITEM;
        if (value.contains("cuantas unidades") || value.contains("cantidad deseas")) return CrmWhatsappAiPendingQuestion.QUANTITY;
        if (value.contains("que color") || value.contains("cual color")) return CrmWhatsappAiPendingQuestion.COLOR;
        if (value.contains("que talla") || value.contains("cual talla")
                || value.contains("deseas llevarlo en talla")) {
            return CrmWhatsappAiPendingQuestion.SIZE;
        }
        if (value.contains("que modelo deseas")) return CrmWhatsappAiPendingQuestion.CATALOG_PRODUCT;
        if (value.contains("colores tallas o precio")
                || value.contains("deseas conocer sus colores")) return CrmWhatsappAiPendingQuestion.PRODUCT;
        return CrmWhatsappAiPendingQuestion.NONE;
    }

    private Integer parseQuantity(String value) {
        Matcher digits = QUANTITY.matcher(value);
        if (digits.find()) return Math.max(1, Math.min(99, Integer.parseInt(digits.group(1))));
        Map<String, Integer> words = Map.ofEntries(
                Map.entry("un", 1), Map.entry("uno", 1), Map.entry("una", 1),
                Map.entry("dos", 2), Map.entry("tres", 3), Map.entry("cuatro", 4),
                Map.entry("cinco", 5), Map.entry("seis", 6), Map.entry("siete", 7),
                Map.entry("ocho", 8), Map.entry("nueve", 9), Map.entry("diez", 10));
        for (Map.Entry<String, Integer> entry : words.entrySet()) {
            if ((" " + value + " ").matches(".*\\b" + entry.getKey() + "\\b.*")) return entry.getValue();
        }
        return null;
    }

    private boolean isAffirmative(String value) {
        return value.matches("^(si|sí|ok|okay|confirmo|confirmado|perfecto|correcto|de acuerdo|esta bien|listo)( por favor)?[.!]?$" );
    }

    private boolean isQuantityOnlyReply(String value) {
        if (value.isBlank() || parseQuantity(value) == null) return false;
        String remainder = value
                .replaceAll("\\b(solo|solamente|quiero|deseo|dame|agrega|anade|llevo|necesito|un|uno|una|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez|unidad|unidades|mas|por|favor)\\b", " ")
                .replaceAll("\\d{1,2}", " ")
                .replaceAll("[.!]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return remainder.isBlank();
    }

    private boolean shouldPreservePending(CrmWhatsappAiPendingQuestion pending, String intent) {
        if (pending == CrmWhatsappAiPendingQuestion.NONE) return false;
        String currentIntent = clean(intent).toUpperCase(Locale.ROOT);
        return switch (pending) {
            case ORDER_CONFIRMATION -> !Set.of("CONFIRMAR_PEDIDO", "CANCELAR_PEDIDO", "INTENCION_COMPRA",
                    "MODIFICAR_CARRITO").contains(currentIntent);
            case CUSTOMER_NAME, CUSTOMER_PHONE -> !"DATOS_CLIENTE".equals(currentIntent);
            case PAYMENT_METHOD, PAYMENT_ACCOUNT -> !Set.of("METODOS_PAGO", "PAGO_PENDIENTE").contains(currentIntent);
            case QUANTITY, COLOR, SIZE, COMBO_ITEM -> !Set.of("INTENCION_COMPRA", "MODIFICAR_CARRITO",
                    "CONFIRMAR_PEDIDO", "CANCELAR_PEDIDO", "PROMOCIONES").contains(currentIntent);
            case PRODUCT, CATALOG_PRODUCT, CATALOG_CONFIRMATION, SIZE_GUIDE_PRODUCT ->
                    !Set.of("PRODUCTOS", "PRECIO", "STOCK", "COLORES_TALLAS", "GUIA_TALLAS",
                            "ENLACE_ECOMMERCE", "INTENCION_COMPRA").contains(currentIntent);
            case NONE -> false;
        };
    }

    private boolean isNegative(String value) {
        return value.matches("^(no|cancelar|cancela|ya no|no gracias)[.!]?$" );
    }

    private boolean isShortReply(String value) {
        return !value.isBlank() && value.split("\\s+").length <= 4 && !isAffirmative(value) && !isNegative(value);
    }

    public record MemoryResponse(String attentionMode, String attentionState, String currentIntent, String pendingQuestion, Integer productId, Integer variantId,
            String productName, String color, String size, Integer quantity, Integer consecutiveAutoResponses,
            LocalDateTime expiresAt, List<MemoryItemResponse> cart) {}
    public record MemoryItemResponse(Integer productId, Integer variantId, String productName, String color,
            String size, Integer quantity) {}
    public record MemorySelection(Integer productId, String productName, String color, String size, Integer quantity) {
        static MemorySelection empty() { return new MemorySelection(null, "", "", "", null); }
    }
    public record PendingReplyResolution(String intent, String response, boolean showCatalog, String catalogQuery) {
        public PendingReplyResolution(String intent, String response, boolean showCatalog) {
            this(intent, response, showCatalog, showCatalog ? "" : null);
        }

        public boolean requestsCatalog() {
            return catalogQuery != null;
        }
    }
}
