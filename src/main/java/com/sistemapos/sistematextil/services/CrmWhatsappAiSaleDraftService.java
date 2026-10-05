package com.sistemapos.sistematextil.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.sistemapos.sistematextil.model.*;
import com.sistemapos.sistematextil.repositories.*;
import com.sistemapos.sistematextil.services.CrmWhatsappAiCommercialQueryService.CatalogResult;
import com.sistemapos.sistematextil.services.CrmWhatsappAiCommercialQueryService.PaymentMethodItem;
import com.sistemapos.sistematextil.services.CrmWhatsappAiCommercialQueryService.ProductResult;
import com.sistemapos.sistematextil.services.CrmWhatsappAiCommercialQueryService.PromotionProductResult;
import com.sistemapos.sistematextil.services.CrmWhatsappAiCommercialQueryService.PromotionResult;
import com.sistemapos.sistematextil.services.CrmWhatsappAiCommercialQueryService.VariantResult;
import com.sistemapos.sistematextil.services.CrmWhatsappEcommerceOrderParser.EcommerceWhatsappOrder;
import com.sistemapos.sistematextil.services.CrmWhatsappEcommerceOrderParser.EcommerceWhatsappOrderItem;
import com.sistemapos.sistematextil.services.ai.AiModelProvider.SaleActionResult;
import com.sistemapos.sistematextil.util.usuario.Rol;
import com.sistemapos.sistematextil.util.ecommerce.EcommerceCarritoResumenResponse;
import com.sistemapos.sistematextil.util.crm.CrmWhatsappPhoneUtils;
import com.sistemapos.sistematextil.util.venta.VentaDetalleCreateItem;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CrmWhatsappAiSaleDraftService {
    private static final DateTimeFormatter CUSTOMER_DATE_FORMAT = DateTimeFormatter
            .ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es-PE"));
    private static final Set<CrmWhatsappAiSaleDraftStatus> ACTIVE = Set.of(
            CrmWhatsappAiSaleDraftStatus.BUILDING,
            CrmWhatsappAiSaleDraftStatus.AWAITING_CUSTOMER,
            CrmWhatsappAiSaleDraftStatus.AWAITING_CUSTOMER_DATA,
            CrmWhatsappAiSaleDraftStatus.READY_FOR_REVIEW,
            CrmWhatsappAiSaleDraftStatus.IMPORTED,
            CrmWhatsappAiSaleDraftStatus.PAYMENT_PENDING);

    private final CrmWhatsappAiSaleDraftRepository draftRepository;
    private final CrmWhatsappConversationRepository conversationRepository;
    private final SucursalStockRepository stockRepository;
    private final SucursalMetodoPagoConfigRepository paymentRepository;
    private final UsuarioRepository usuarioRepository;
    private final ClienteRepository clienteRepository;
    private final VentaRepository ventaRepository;
    private final CrmWhatsappAiCommercialQueryService commercialQueryService;
    private final CrmWhatsappAiPaymentService aiPaymentService;
    private final PrecioOfertaService precioOfertaService;
    private final EcommercePromocionComboService promotionService;
    private final CrmWhatsappEventService eventService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final CrmWhatsappAiAuditService auditService;
    private final CrmWhatsappAiClientWriter clientWriter;

    @Transactional
    public ActionOutcome applyAiAction(CrmWhatsappConversation conversation, SaleActionResult action) {
        if (conversation == null || action == null || conversation.getConnection() == null
                || conversation.getConnection().getSucursal() == null) {
            return ActionOutcome.human("La conversacion no tiene una sucursal de venta configurada");
        }
        String command = clean(action.action()).toUpperCase(Locale.ROOT);
        CrmWhatsappAiSaleDraft draft = activeDraft(conversation.getIdConversation());
        if ("CANCEL".equals(command)) {
            if (draft == null) return new ActionOutcome("No tienes un pedido pendiente.", false, null);
            aiPaymentService.cancelForDraft(draft.getIdAiSaleDraft(), "Pedido cancelado por el cliente");
            draft.setStatus(CrmWhatsappAiSaleDraftStatus.CANCELLED);
            clearCustomerNameSuggestion(draft);
            saveAndPublish(draft, "ai.sale_draft.updated");
            return new ActionOutcome("Listo, cancelé el pedido pendiente.", false, response(draft));
        }
        if ("CONFIRM".equals(command)) return confirmCustomer(draft);
        if ("SET_PAYMENT".equals(command)) return setPayment(conversation, draft, action.paymentMethod());
        if ("ADD_COMBO".equals(command)) {
            if (draft == null) draft = createDraft(conversation);
            return selectCombo(conversation, draft, action);
        }
        if (!Set.of("ADD", "UPDATE", "REMOVE").contains(command)) {
            return ActionOutcome.human("No se identifico una accion de compra segura");
        }
        if (draft == null) draft = createDraft(conversation);
        return mutateItem(conversation, draft, action, command);
    }

    @Transactional
    public ActionOutcome applyEcommerceOrder(CrmWhatsappConversation conversation, Long sourceMessageId,
            EcommerceWhatsappOrder order) {
        if (conversation == null || order == null || !order.recognized()
                || conversation.getConnection() == null || conversation.getConnection().getSucursal() == null) {
            return ActionOutcome.human("La conversacion no tiene una sucursal de venta configurada");
        }
        CrmWhatsappAiSaleDraft draft = activeDraft(conversation.getIdConversation());
        if (draft == null) {
            draft = createDraft(conversation);
        } else {
            draft = draftRepository.findForUpdate(draft.getIdAiSaleDraft()).orElseThrow();
        }
        if (sourceMessageId != null && sourceMessageId.equals(draft.getLastEcommerceMessageId())) {
            return new ActionOutcome("Este pedido del ecommerce ya fue procesado.\n\n" + summary(draft, true),
                    false, response(draft));
        }

        List<BatchAcceptedLine> accepted = new ArrayList<>();
        List<String> rejected = new ArrayList<>(order.parsingErrors());
        Map<Integer, BatchResolvedLine> resolvedByVariant = new LinkedHashMap<>();
        for (EcommerceWhatsappOrderItem requested : order.items()) {
            resolveEcommerceLine(conversation, requested, resolvedByVariant, accepted, rejected, draft);
        }

        if (!resolvedByVariant.isEmpty()) {
            for (BatchResolvedLine resolved : resolvedByVariant.values()) {
                CrmWhatsappAiSaleDraftItem item = draft.getItems().stream()
                        .filter(current -> current.getVariantId().equals(resolved.variant().variantId()))
                        .findFirst().orElse(null);
                if (item == null) {
                    item = new CrmWhatsappAiSaleDraftItem();
                    item.setDraft(draft);
                    item.setProductId(resolved.product().productId());
                    item.setVariantId(resolved.variant().variantId());
                    draft.getItems().add(item);
                }
                item.setProductName(resolved.product().name());
                item.setSku(resolved.variant().sku());
                item.setColor(resolved.variant().color());
                item.setSize(resolved.variant().size());
                item.setQuantity(resolved.finalQuantity());
                item.setUnitPrice(resolved.variant().currentPrice());
                item.setRegularUnitPrice(resolved.variant().regularPrice());
                item.setStockSnapshot(resolved.variant().stock());
                item.setImageUrl(resolved.variant().imageUrl());
                item.setPreventa(resolved.product().preventa());
                item.setFechaEnvioPreventa(resolved.product().fechaEnvioPreventa());
            }
            draft.setVersion(draft.getVersion() + 1);
            invalidateConfirmation(draft);
            draft.setExpiresAt(LocalDateTime.now().plusHours(24));
            draft.setStatus(CrmWhatsappAiSaleDraftStatus.AWAITING_CUSTOMER);
        }
        draft.setLastEcommerceMessageId(sourceMessageId);
        saveAndPublish(draft, "ai.sale_draft.updated");
        return new ActionOutcome(ecommerceOrderResponse(draft, order, accepted, rejected), false, response(draft));
    }

    private void resolveEcommerceLine(CrmWhatsappConversation conversation, EcommerceWhatsappOrderItem requested,
            Map<Integer, BatchResolvedLine> resolvedByVariant, List<BatchAcceptedLine> accepted,
            List<String> rejected, CrmWhatsappAiSaleDraft draft) {
        String requestedName = clean(requested.productName());
        if (requestedName.isBlank()) {
            rejected.add("Hay un producto sin nombre.");
            return;
        }
        if (clean(requested.size()).isBlank()) {
            rejected.add("Falta la talla de " + requested.label() + ".");
            return;
        }
        int quantity = requested.quantity() == null ? 0 : requested.quantity();
        if (quantity < 1 || quantity > 99) {
            rejected.add("La cantidad de " + requested.label() + " no es valida.");
            return;
        }

        CatalogResult catalog = commercialQueryService.searchProducts(conversation, requestedName);
        List<ProductResult> exactProducts = catalog.products().stream()
                .filter(product -> normalize(product.name()).equals(normalize(requestedName)))
                .toList();
        if (exactProducts.isEmpty() && clean(requested.color()).isBlank()) {
            int longestName = catalog.products().stream()
                    .filter(product -> containsWholePhrase(normalize(requestedName), normalize(product.name())))
                    .mapToInt(product -> normalize(product.name()).length())
                    .max().orElse(0);
            exactProducts = catalog.products().stream()
                    .filter(product -> normalize(product.name()).length() == longestName)
                    .filter(product -> containsWholePhrase(normalize(requestedName), normalize(product.name())))
                    .toList();
        }
        if (exactProducts.size() != 1) {
            if (catalog.corrected() && !clean(catalog.interpretedProduct()).isBlank()) {
                rejected.add(requested.label() + " no esta disponible actualmente. ¿Quisiste decir "
                        + catalog.interpretedProduct() + "?");
            } else {
                rejected.add(requested.label() + " no esta disponible actualmente.");
            }
            return;
        }
        ProductResult product = exactProducts.getFirst();
        String requestedColor = clean(requested.color());
        if (requestedColor.isBlank()) requestedColor = compactColor(requestedName, product.name());
        if (requestedColor.isBlank()) {
            rejected.add("Falta el color de " + product.name() + ".");
            return;
        }
        String colorKey = normalize(requestedColor);
        String sizeKey = normalize(requested.size());
        List<VariantResult> colorVariants = product.variants().stream()
                .filter(variant -> normalize(variant.color()).equals(colorKey))
                .toList();
        if (colorVariants.isEmpty()) {
            rejected.add(product.name() + " no tiene el color " + requestedColor + ".");
            return;
        }
        List<VariantResult> matches = colorVariants.stream()
                .filter(variant -> normalize(variant.size()).equals(sizeKey))
                .filter(variant -> variant.available() && variant.stock() != null && variant.stock() > 0)
                .toList();
        if (matches.size() != 1) {
            rejected.add(product.name() + " no tiene stock en " + requestedColor + ", talla "
                    + requested.size() + ".");
            return;
        }
        VariantResult variant = matches.getFirst();
        CrmWhatsappAiSaleDraftItem existing = draft.getItems().stream()
                .filter(item -> item.getVariantId().equals(variant.variantId())).findFirst().orElse(null);
        int currentQuantity = existing == null ? 0 : existing.getQuantity();
        BatchResolvedLine alreadyResolved = resolvedByVariant.get(variant.variantId());
        int pendingQuantity = alreadyResolved == null ? 0 : alreadyResolved.addedQuantity();
        int finalQuantity = currentQuantity + pendingQuantity + quantity;
        if (finalQuantity > variant.stock()) {
            int availableToAdd = Math.max(0, variant.stock() - currentQuantity - pendingQuantity);
            rejected.add(availableToAdd == 0
                    ? "No hay mas unidades disponibles de " + product.name() + " en esa combinacion."
                    : "Solo hay " + availableToAdd + " unidad(es) disponibles de " + product.name()
                            + " en esa combinacion.");
            return;
        }
        resolvedByVariant.put(variant.variantId(), new BatchResolvedLine(product, variant,
                pendingQuantity + quantity, finalQuantity));
        accepted.add(new BatchAcceptedLine(product, variant, quantity));
    }

    private String compactColor(String requestedName, String productName) {
        String source = normalize(requestedName);
        String product = normalize(productName);
        int position = source.indexOf(product);
        if (position < 0) return "";
        return (source.substring(0, position) + " " + source.substring(position + product.length()))
                .replaceAll("\\s+", " ").trim();
    }

    private String ecommerceOrderResponse(CrmWhatsappAiSaleDraft draft, EcommerceWhatsappOrder order,
            List<BatchAcceptedLine> accepted, List<String> rejected) {
        StringBuilder text = new StringBuilder();
        if (!accepted.isEmpty()) {
            text.append(rejected.isEmpty() ? "✅ Preparé tu pedido:\n" : "✅ Agregué:\n");
            for (BatchAcceptedLine line : accepted) {
                text.append("• ").append(line.quantity()).append(" x ").append(line.product().name())
                        .append(" ").append(line.variant().color()).append(", talla ")
                        .append(line.variant().size()).append(" - S/")
                        .append(line.variant().currentPrice().multiply(BigDecimal.valueOf(line.quantity())).setScale(2))
                        .append("\n");
                if (line.variant().regularPrice() != null
                        && line.variant().currentPrice().compareTo(line.variant().regularPrice()) < 0) {
                    text.append("  Oferta aplicada: S/").append(line.variant().currentPrice().setScale(2))
                            .append(" c/u\n");
                }
            }
        }
        if (!rejected.isEmpty()) {
            if (!text.isEmpty()) text.append("\n");
            text.append("⚠️ No pude agregar:\n");
            rejected.stream().distinct().forEach(error -> text.append("• ").append(error).append("\n"));
        }
        if (accepted.isEmpty()) {
            text.append("\nCorrige esos datos y vuelve a intentarlo.");
            return text.toString().trim();
        }
        if (!draft.getPromotions().isEmpty()) {
            text.append("\n🎁 Promociones aplicadas:\n");
            for (CrmWhatsappAiSaleDraftPromotion promotion : draft.getPromotions()) {
                text.append("• ").append(promotion.getName()).append(": -S/")
                        .append(promotion.getDiscount().setScale(2)).append("\n");
            }
        }
        BigDecimal informedTotal = order.informedTotal();
        if (informedTotal != null && money(informedTotal).compareTo(money(draft.getTotal())) != 0) {
            text.append("\nEl total fue actualizado de S/").append(money(informedTotal))
                    .append(" a S/").append(money(draft.getTotal())).append(" según precios y promociones vigentes.\n");
        }
        text.append("\n💰 Total actualizado: S/").append(money(draft.getTotal()))
                .append("\n\nTu pedido queda guardado. Cuando desees continuar, puedes confirmarlo.")
                .append("\n\n¿En qué más puedo ayudarte?");
        return text.toString().trim();
    }

    @Transactional(readOnly = true)
    public String pendingPaymentEvidenceReminder(Long conversationId) {
        return aiPaymentService.pendingEvidenceReminder(conversationId);
    }

    @Transactional
    public ActionOutcome selectConfirmedPaymentMethod(CrmWhatsappConversation conversation, String customerMessage) {
        if (conversation == null || clean(customerMessage).isBlank()) return null;
        CrmWhatsappAiSaleDraft draft = activeDraft(conversation.getIdConversation());
        if (draft == null || !isCustomerConfirmed(draft)
                || draft.getStatus() == CrmWhatsappAiSaleDraftStatus.AWAITING_CUSTOMER_DATA
                || draft.getStatus() == CrmWhatsappAiSaleDraftStatus.PAYMENT_PENDING) return null;

        String message = normalize(customerMessage);
        List<PaymentMethodItem> matches = commercialQueryService.paymentMethods(conversation).methods().stream()
                .filter(method -> {
                    String methodName = normalize(method.name());
                    return !methodName.isBlank() && containsWholePhrase(message, methodName);
                })
                .toList();
        if (matches.size() != 1) return null;
        if (!isExplicitPaymentSelection(customerMessage, matches.getFirst().name())) return null;
        return setPayment(conversation, draft, matches.getFirst().name());
    }

    @Transactional
    public ActionOutcome captureConfirmedCustomerData(CrmWhatsappConversation conversation, String customerMessage) {
        if (conversation == null || clean(customerMessage).isBlank()) return null;
        CrmWhatsappAiSaleDraft draft = activeDraft(conversation.getIdConversation());
        if (draft == null || draft.getStatus() != CrmWhatsappAiSaleDraftStatus.AWAITING_CUSTOMER_DATA
                || !isCustomerConfirmed(draft)) return null;

        String phone = extractPhone(customerMessage);
        if (phone.isBlank() && matchesCatalogProduct(conversation, customerMessage)) return null;
        if (phone.isBlank() && (!validCustomerName(draft.getPendingCustomerName(), draft.getPendingCustomerPhone())
                ? !isLikelyCustomerNameReply(customerMessage)
                : true)) {
            return null;
        }
        if (!phone.isBlank()) draft.setPendingCustomerPhone(phone);
        String name = extractName(customerMessage, phone, draft.getPendingCustomerName() == null);
        if (!name.isBlank()) draft.setPendingCustomerName(name);

        boolean needsName = !validCustomerName(draft.getPendingCustomerName(), draft.getPendingCustomerPhone());
        boolean needsPhone = CrmWhatsappPhoneUtils.normalizePeruvianMobile(draft.getPendingCustomerPhone()).isBlank();
        if (needsName || needsPhone) {
            draftRepository.save(draft);
            publish(draft, "ai.sale_draft.updated");
            return new ActionOutcome(customerDataPrompt(needsName, needsPhone), false, response(draft));
        }

        CustomerResolution resolution = resolveCustomer(draft);
        if (resolution.requiresHuman()) {
            return new ActionOutcome(resolution.message(), true, response(draft));
        }
        draft.setStatus(CrmWhatsappAiSaleDraftStatus.AWAITING_CUSTOMER);
        draftRepository.save(draft);
        publish(draft, "ai.sale_draft.updated");
        return paymentPrompt(draft);
    }

    @Transactional
    public SaleDraftResponse get(Long conversationId, Usuario sessionUser) {
        Usuario actor = requireCrmUser(sessionUser);
        CrmWhatsappConversation conversation = requireConversation(conversationId);
        requireCanRead(conversation, actor);
        CrmWhatsappAiSaleDraft draft = activeDraft(conversationId);
        return draft == null ? null : response(draft);
    }

    @Transactional
    public SaleDraftResponse revalidate(Long conversationId, RevalidateRequest request, Usuario sessionUser) {
        Usuario actor = requireCrmUser(sessionUser);
        CrmWhatsappConversation conversation = requireConversation(conversationId);
        requireCanOperate(conversation, actor);
        CrmWhatsappAiSaleDraft draft = requireActiveDraft(conversationId);
        draft = draftRepository.findForUpdate(draft.getIdAiSaleDraft()).orElseThrow();
        List<String> warnings = revalidateItems(draft, true);
        if (Boolean.TRUE.equals(request == null ? null : request.markImported())) {
            if (!isCustomerConfirmed(draft)) throw conflict("El cliente debe confirmar esta version del pedido");
            if (!warnings.isEmpty()) throw conflict(String.join(". ", warnings));
            draft.setStatus(CrmWhatsappAiSaleDraftStatus.IMPORTED);
        }
        draft.setExpiresAt(LocalDateTime.now().plusHours(24));
        draftRepository.save(draft);
        publish(draft, "ai.sale_draft.updated");
        return response(draft, warnings);
    }

    @Transactional
    public SaleDraftResponse requestConfirmation(Long conversationId, SaleDraftUpdateRequest request, Usuario sessionUser) {
        Usuario actor = requireCrmUser(sessionUser);
        CrmWhatsappConversation conversation = requireConversation(conversationId);
        requireCanOperate(conversation, actor);
        CrmWhatsappAiSaleDraft draft = draftRepository.findForUpdate(
                requireActiveDraft(conversationId).getIdAiSaleDraft()).orElseThrow();
        if (request != null && request.items() != null) rebuildFromAdvisor(draft, request);
        List<String> warnings = revalidateItems(draft, true);
        if (!warnings.isEmpty()) throw conflict(String.join(". ", warnings));
        invalidateConfirmation(draft);
        draft.setStatus(CrmWhatsappAiSaleDraftStatus.AWAITING_CUSTOMER);
        draft.setExpiresAt(LocalDateTime.now().plusHours(24));
        draftRepository.save(draft);
        applicationEventPublisher.publishEvent(new CrmWhatsappAiSaleConfirmationRequested(
                conversationId, draft.getIdAiSaleDraft(), summary(draft, true), actor));
        publish(draft, "ai.sale_draft.updated");
        return response(draft);
    }

    @Transactional
    public SaleDraftResponse cancel(Long conversationId, Usuario sessionUser) {
        Usuario actor = requireCrmUser(sessionUser);
        CrmWhatsappConversation conversation = requireConversation(conversationId);
        requireCanOperate(conversation, actor);
        CrmWhatsappAiSaleDraft draft = requireActiveDraft(conversationId);
        draft = draftRepository.findForUpdate(draft.getIdAiSaleDraft()).orElseThrow();
        aiPaymentService.cancelForDraft(draft.getIdAiSaleDraft(), "Pedido descartado por el asesor");
        draft.setStatus(CrmWhatsappAiSaleDraftStatus.CANCELLED);
        clearCustomerNameSuggestion(draft);
        draftRepository.save(draft);
        publish(draft, "ai.sale_draft.updated");
        return response(draft);
    }

    @Transactional
    public SaleDraftResponse decideCustomerNameSuggestion(Long conversationId,
            CustomerNameSuggestionRequest request, Usuario sessionUser) {
        Usuario actor = requireCrmUser(sessionUser);
        CrmWhatsappConversation conversation = requireConversation(conversationId);
        requireCanRead(conversation, actor);
        CrmWhatsappAiSaleDraft draft = draftRepository.findForUpdate(
                requireActiveDraft(conversationId).getIdAiSaleDraft()).orElseThrow();
        if (draft.getCustomerNameSuggestionStatus() != CrmWhatsappAiCustomerNameSuggestionStatus.PENDING) {
            throw conflict("La sugerencia de nombre ya no esta pendiente");
        }
        String action = clean(request == null ? null : request.action()).toUpperCase(Locale.ROOT);
        if (!Set.of("APPLY", "DISMISS").contains(action)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Accion de sugerencia invalida");
        }
        Cliente client = conversation.getCliente();
        if (client == null) throw conflict("La conversacion ya no tiene un cliente asociado");

        if ("APPLY".equals(action)) {
            String suggestedName = clean(draft.getCustomerNameSuggested());
            if (suggestedName.length() < 2 || suggestedName.length() > 150) {
                throw conflict("El nombre sugerido ya no es valido");
            }
            client.setNombres(suggestedName);
            clienteRepository.save(client);
            draft.setCustomerNameCurrent(suggestedName);
            draft.setCustomerNameSuggestionStatus(CrmWhatsappAiCustomerNameSuggestionStatus.APPLIED);
            syncConversation(conversation, client);
            conversationRepository.save(conversation);
            auditCustomer(draft, client, "CLIENT_NAME_UPDATED");
        } else {
            draft.setCustomerNameSuggestionStatus(CrmWhatsappAiCustomerNameSuggestionStatus.DISMISSED);
        }
        draftRepository.save(draft);
        publish(draft, "ai.sale_draft.updated");
        publishConversationUpdated(conversation);
        return response(draft);
    }

    @Transactional
    public void validateSale(Long conversationId, Long draftId, Integer version,
            List<VentaDetalleCreateItem> details, Integer paymentMethodId,
            Double discountTotal, String discountType) {
        validateSale(conversationId, draftId, version, details, paymentMethodId,
                discountTotal, discountType, true, true);
    }

    @Transactional
    public void validateReservedSale(Long conversationId, Long draftId, Integer version,
            List<VentaDetalleCreateItem> details, Integer paymentMethodId,
            Double discountTotal, String discountType) {
        validateSale(conversationId, draftId, version, details, paymentMethodId,
                discountTotal, discountType, false, false);
    }

    private void validateSale(Long conversationId, Long draftId, Integer version,
            List<VentaDetalleCreateItem> details, Integer paymentMethodId,
            Double discountTotal, String discountType, boolean validateStock,
            boolean validatePaymentMethod) {
        if (draftId == null) return;
        CrmWhatsappAiSaleDraft draft = draftRepository.findForUpdate(draftId)
                .orElseThrow(() -> conflict("El pedido preparado por IA no existe"));
        if (!draft.getConversation().getIdConversation().equals(conversationId)) throw conflict("El pedido pertenece a otro chat");
        if (!ACTIVE.contains(draft.getStatus())) throw conflict("El pedido IA ya no esta activo");
        if (!draft.getVersion().equals(version) || !isCustomerConfirmed(draft)) {
            throw conflict("El cliente no confirmo esta version del pedido");
        }
        if (validateStock) {
            List<String> warnings = revalidateItems(draft, false);
            if (!warnings.isEmpty()) throw conflict(String.join(". ", warnings));
        } else {
            for (CrmWhatsappAiSaleDraftItem item : draft.getItems()) {
                SucursalStock stock = requireStock(draft, item.getVariantId());
                BigDecimal currentPrice = money(precioOfertaService.resolverPrecioVigente(
                        stock.getProductoVariante(), draft.getSucursal().getIdSucursal()));
                if (currentPrice.compareTo(item.getUnitPrice()) != 0) {
                    throw conflict("El precio de " + item.getProductName() + " cambió; requiere nueva confirmación");
                }
                if (preventaChanged(item, stock.getProductoVariante().getProducto())) {
                    throw conflict("La fecha de envío de preventa de " + item.getProductName()
                            + " cambió; requiere nueva confirmación");
                }
            }
        }
        Map<Integer, VentaDetalleCreateItem> sent = new LinkedHashMap<>();
        if (details != null) details.forEach(item -> sent.put(item.idProductoVariante(), item));
        if (sent.size() != draft.getItems().size()) throw conflict("El carrito fue modificado; solicita nueva confirmacion");
        for (CrmWhatsappAiSaleDraftItem item : draft.getItems()) {
            VentaDetalleCreateItem detail = sent.get(item.getVariantId());
            if (detail == null || !item.getQuantity().equals(detail.cantidad())
                    || money(detail.precioUnitario()).compareTo(item.getUnitPrice()) != 0) {
                throw conflict("El carrito fue modificado; solicita nueva confirmacion");
            }
        }
        BigDecimal expectedDiscount = money(draft.getPromotionDiscount());
        BigDecimal sentDiscount = money(discountTotal);
        if (expectedDiscount.compareTo(sentDiscount) != 0
                || (expectedDiscount.signum() > 0 && !"MONTO".equalsIgnoreCase(clean(discountType)))) {
            throw conflict("El descuento promocional no coincide con el pedido confirmado");
        }
        if (validatePaymentMethod && (draft.getMetodoPago() == null || paymentMethodId == null
                || !draft.getMetodoPago().getIdMetodoPago().equals(paymentMethodId))) {
            throw conflict("El metodo de pago no coincide con el confirmado por el cliente");
        }
    }

    @Transactional
    public void markPaymentPending(Long draftId) {
        if (draftId == null) return;
        draftRepository.findForUpdate(draftId).ifPresent(draft -> {
            draft.setStatus(CrmWhatsappAiSaleDraftStatus.PAYMENT_PENDING);
            draftRepository.save(draft);
            publish(draft, "ai.sale_draft.updated");
        });
    }

    @Transactional
    public void markCompleted(Long draftId, Integer saleId) {
        if (draftId == null) return;
        draftRepository.findForUpdate(draftId).ifPresent(draft -> {
            draft.setVenta(saleId == null ? null : ventaRepository.findById(saleId).orElse(null));
            draft.setStatus(CrmWhatsappAiSaleDraftStatus.COMPLETED);
            clearCustomerNameSuggestion(draft);
            draftRepository.save(draft);
            publish(draft, "ai.sale_draft.completed");
        });
    }

    @Transactional
    public void markPaymentExpired(Long draftId) {
        if (draftId == null) return;
        draftRepository.findForUpdate(draftId).ifPresent(draft -> {
            if (draft.getStatus() != CrmWhatsappAiSaleDraftStatus.PAYMENT_PENDING) return;
            draft.setStatus(CrmWhatsappAiSaleDraftStatus.EXPIRED);
            clearCustomerNameSuggestion(draft);
            draftRepository.save(draft);
            publish(draft, "ai.sale_draft.expired");
        });
    }

    @Transactional
    public void markPaymentRejected(Long draftId) {
        if (draftId == null) return;
        draftRepository.findForUpdate(draftId).ifPresent(draft -> {
            if (draft.getStatus() == CrmWhatsappAiSaleDraftStatus.COMPLETED
                    || draft.getStatus() == CrmWhatsappAiSaleDraftStatus.CANCELLED
                    || draft.getStatus() == CrmWhatsappAiSaleDraftStatus.EXPIRED) return;
            draft.setStatus(CrmWhatsappAiSaleDraftStatus.CANCELLED);
            clearCustomerNameSuggestion(draft);
            draftRepository.save(draft);
            publish(draft, "ai.sale_draft.updated");
        });
    }

    @Transactional
    public void extendPaymentReview(Long draftId, LocalDateTime expiresAt) {
        if (draftId == null || expiresAt == null) return;
        draftRepository.findForUpdate(draftId).ifPresent(draft -> {
            if (draft.getStatus() != CrmWhatsappAiSaleDraftStatus.PAYMENT_PENDING) return;
            if (draft.getExpiresAt() == null || draft.getExpiresAt().isBefore(expiresAt)) {
                draft.setExpiresAt(expiresAt);
                draftRepository.save(draft);
                publish(draft, "ai.sale_draft.updated");
            }
        });
    }

    @Transactional
    public void markConfirmationSent(Long conversationId, Long messageId) {
        CrmWhatsappAiSaleDraft draft = activeDraft(conversationId);
        if (draft == null || draft.getStatus() != CrmWhatsappAiSaleDraftStatus.AWAITING_CUSTOMER) return;
        draft.setConfirmationMessageId(messageId);
        draftRepository.save(draft);
    }

    @Transactional
    public boolean pauseIfReady(Long conversationId) {
        CrmWhatsappAiSaleDraft draft = activeDraft(conversationId);
        if (draft == null || draft.getStatus() != CrmWhatsappAiSaleDraftStatus.READY_FOR_REVIEW) return false;
        publish(draft, "ai.sale_draft.ready");
        return true;
    }

    @Transactional
    public boolean blocksAutomation(Long conversationId) {
        CrmWhatsappAiSaleDraft draft = activeDraft(conversationId);
        return draft != null && Set.of(
                CrmWhatsappAiSaleDraftStatus.READY_FOR_REVIEW,
                CrmWhatsappAiSaleDraftStatus.IMPORTED).contains(draft.getStatus());
    }

    @Transactional(readOnly = true)
    public boolean matchesPendingAttribute(CrmWhatsappConversation conversation, String productName,
            String candidate, CrmWhatsappAiPendingQuestion pendingQuestion) {
        if (conversation == null || clean(productName).isBlank() || clean(candidate).isBlank()) return false;
        if (pendingQuestion != CrmWhatsappAiPendingQuestion.COLOR
                && pendingQuestion != CrmWhatsappAiPendingQuestion.SIZE) return false;
        String expected = normalize(candidate);
        CatalogResult catalog = commercialQueryService.searchProducts(conversation, productName);
        return catalog.products().stream().anyMatch(product -> {
            List<String> values = pendingQuestion == CrmWhatsappAiPendingQuestion.COLOR
                    ? product.availableColors() : product.availableSizes();
            return values != null && values.stream().map(this::normalize).anyMatch(expected::equals);
        });
    }

    @Transactional(readOnly = true)
    public boolean matchesCatalogProduct(CrmWhatsappConversation conversation, String candidate) {
        if (conversation == null || clean(candidate).isBlank()) return false;
        CatalogResult catalog = commercialQueryService.searchProducts(conversation, candidate);
        return catalog.products() != null && !catalog.products().isEmpty()
                && !"AMBIGUOUS".equalsIgnoreCase(clean(catalog.resolution()));
    }

    @Transactional
    public void expireDrafts() {
        draftRepository.findByStatusInAndExpiresAtBefore(ACTIVE, LocalDateTime.now()).forEach(draft -> {
            LocalDateTime paymentExpiry = aiPaymentService.activeRequestExpiry(draft.getIdAiSaleDraft());
            if (paymentExpiry != null && paymentExpiry.isAfter(LocalDateTime.now())) {
                draft.setExpiresAt(paymentExpiry);
                draftRepository.save(draft);
                return;
            }
            aiPaymentService.cancelForDraft(draft.getIdAiSaleDraft(), "Pedido IA vencido");
            draft.setStatus(CrmWhatsappAiSaleDraftStatus.EXPIRED);
            clearCustomerNameSuggestion(draft);
            draftRepository.save(draft);
            publish(draft, "ai.sale_draft.expired");
        });
    }

    private ActionOutcome mutateItem(CrmWhatsappConversation conversation, CrmWhatsappAiSaleDraft draft,
            SaleActionResult action, String command) {
        if ("REMOVE".equals(command)) {
            CrmWhatsappAiSaleDraftItem item = findDraftItem(draft, action);
            if (item == null) return new ActionOutcome("No encontré ese producto en el pedido.", false, response(draft));
            draft.getItems().remove(item);
            changed(draft);
            if (draft.getItems().isEmpty()) draft.setStatus(CrmWhatsappAiSaleDraftStatus.BUILDING);
            else draft.setStatus(CrmWhatsappAiSaleDraftStatus.AWAITING_CUSTOMER);
            saveAndPublish(draft, "ai.sale_draft.updated");
            return new ActionOutcome(draft.getItems().isEmpty() ? "El carrito quedó vacío." : summary(draft, true), false, response(draft));
        }
        if ("UPDATE".equals(command)) {
            CrmWhatsappAiSaleDraftItem item = findDraftItem(draft, action);
            if (item == null) return new ActionOutcome("¿Qué producto del pedido deseas modificar?", false, response(draft));
            int quantity = action.quantity() == null ? item.getQuantity() : Math.max(1, Math.min(99, action.quantity()));
            SucursalStock stock = requireStock(draft, item.getVariantId());
            if (quantity > stock.getCantidad()) return new ActionOutcome("Solo hay " + stock.getCantidad() + " unidad(es) disponibles.", false, response(draft));
            item.setQuantity(quantity);
            refreshItem(item, stock);
            changed(draft);
            draft.setStatus(CrmWhatsappAiSaleDraftStatus.AWAITING_CUSTOMER);
            saveAndPublish(draft, "ai.sale_draft.updated");
            return new ActionOutcome(summary(draft, true), false, response(draft));
        }

        CatalogResult catalog = commercialQueryService.searchProducts(conversation, action.productQuery());
        List<VariantCandidate> candidates = candidates(catalog, action.color(), action.size());
        if (candidates.isEmpty()) return new ActionOutcome(
                "No hay stock disponible para ese producto, color y talla. Prueba con otra combinacion.",
                false, response(draft));
        List<String> colors = candidates.stream().map(item -> clean(item.variant().color())).filter(value -> !value.isBlank()).distinct().toList();
        List<String> sizes = candidates.stream().map(item -> clean(item.variant().size())).filter(value -> !value.isBlank()).distinct().toList();
        if (clean(action.color()).isBlank() && colors.size() > 1) {
            return new ActionOutcome("¿Qué color deseas? Disponibles: " + String.join(", ", colors) + ".", false, response(draft));
        }
        if (clean(action.size()).isBlank() && sizes.size() > 1) {
            return new ActionOutcome("¿Qué talla deseas? Disponibles: " + String.join(", ", sizes) + ".", false, response(draft));
        }
        if (candidates.size() != 1) return new ActionOutcome(
                "Indícame el color y la talla exactos para continuar, por favor.", false, response(draft));
        VariantCandidate selected = candidates.get(0);
        int quantity = action.quantity() == null || action.quantity() < 1 ? 1 : Math.min(99, action.quantity());
        if (quantity > selected.variant().stock()) {
            return new ActionOutcome("Solo hay " + selected.variant().stock() + " unidad(es) disponibles.", false, response(draft));
        }
        CrmWhatsappAiSaleDraftItem item = draft.getItems().stream()
                .filter(current -> current.getVariantId().equals(selected.variant().variantId())).findFirst().orElse(null);
        if (item == null) {
            item = new CrmWhatsappAiSaleDraftItem();
            item.setDraft(draft); item.setProductId(selected.product().productId());
            item.setVariantId(selected.variant().variantId()); draft.getItems().add(item);
        } else {
            quantity += item.getQuantity();
            if (quantity > selected.variant().stock()) return new ActionOutcome("No puedes superar el stock disponible de " + selected.variant().stock() + ".", false, response(draft));
        }
        item.setProductName(selected.product().name()); item.setSku(selected.variant().sku());
        item.setColor(selected.variant().color()); item.setSize(selected.variant().size());
        item.setQuantity(quantity); item.setUnitPrice(selected.variant().currentPrice());
        item.setRegularUnitPrice(selected.variant().regularPrice());
        item.setStockSnapshot(selected.variant().stock()); item.setImageUrl(selected.variant().imageUrl());
        item.setPreventa(selected.product().preventa());
        item.setFechaEnvioPreventa(selected.product().fechaEnvioPreventa());
        changed(draft);
        draft.setStatus(CrmWhatsappAiSaleDraftStatus.AWAITING_CUSTOMER);
        String comboPrompt = pendingComboPrompt(draft);
        saveAndPublish(draft, "ai.sale_draft.updated");
        return new ActionOutcome(comboPrompt == null ? summary(draft, true) : comboPrompt, false, response(draft));
    }

    private ActionOutcome selectCombo(CrmWhatsappConversation conversation, CrmWhatsappAiSaleDraft draft,
            SaleActionResult action) {
        List<PromotionResult> promotions = commercialQueryService.promotions(conversation, "", 0).promotions();
        PromotionResult selected = promotions.stream()
                .filter(item -> action.promotionId() != null && action.promotionId() > 0
                        && item.promotionId().equals(action.promotionId()))
                .findFirst().orElse(null);
        if (selected == null && !clean(action.productQuery()).isBlank()) {
            String query = normalize(action.productQuery());
            List<PromotionResult> matches = promotions.stream()
                    .filter(item -> normalize(item.name()).contains(query) || query.contains(normalize(item.name()))
                            || normalize(item.rule()).contains(query))
                    .toList();
            if (matches.size() == 1) selected = matches.getFirst();
        }
        if (selected == null) {
            return new ActionOutcome("No pude identificar un combo vigente. ¿Cuál promoción deseas agregar?",
                    false, response(draft));
        }
        draft.setPendingPromotionId(selected.promotionId());
        changed(draft);
        draft.setStatus(CrmWhatsappAiSaleDraftStatus.BUILDING);
        String prompt = pendingComboPrompt(draft);
        saveAndPublish(draft, "ai.sale_draft.updated");
        return new ActionOutcome(prompt == null ? summary(draft, true) : prompt, false, response(draft));
    }

    private String pendingComboPrompt(CrmWhatsappAiSaleDraft draft) {
        if (draft.getPendingPromotionId() == null) return null;
        PromotionResult promotion = commercialQueryService.promotions(draft.getConversation(), "", 0).promotions().stream()
                .filter(item -> item.promotionId().equals(draft.getPendingPromotionId()))
                .findFirst().orElse(null);
        if (promotion == null) {
            draft.setPendingPromotionId(null);
            return "La promoción seleccionada ya no está vigente. ¿Deseas consultar los combos disponibles?";
        }
        Map<Integer, Integer> quantities = new LinkedHashMap<>();
        draft.getItems().forEach(item -> quantities.merge(item.getProductId(), item.getQuantity(), Integer::sum));
        for (PromotionProductResult product : promotion.products()) {
            int missing = product.quantity() - quantities.getOrDefault(product.productId(), 0);
            if (missing > 0) {
                return "Para completar el combo " + promotion.name() + ", elige color y talla de "
                        + product.name() + (missing > 1 ? " (" + missing + " unidades)" : "") + ".";
            }
        }
        draft.setPendingPromotionId(null);
        calculate(draft);
        return null;
    }

    private ActionOutcome confirmCustomer(CrmWhatsappAiSaleDraft draft) {
        if (draft == null || draft.getItems().isEmpty()) return new ActionOutcome("Aún no hay productos para confirmar.", false, null);
        if (draft.getStatus() != CrmWhatsappAiSaleDraftStatus.AWAITING_CUSTOMER) {
            return new ActionOutcome("Primero debo mostrarte el resumen actualizado del pedido.", false, response(draft));
        }
        draft.setConfirmedVersion(draft.getVersion());
        draft.setCustomerConfirmedAt(LocalDateTime.now());
        CustomerReadiness readiness = prepareCustomerData(draft);
        if (readiness.requiresHuman()) {
            draftRepository.save(draft);
            publish(draft, "ai.sale_draft.updated");
            return new ActionOutcome(readiness.message(), true, response(draft));
        }
        if (readiness.needsName() || readiness.needsPhone()) {
            draft.setStatus(CrmWhatsappAiSaleDraftStatus.AWAITING_CUSTOMER_DATA);
            draftRepository.save(draft);
            publish(draft, "ai.sale_draft.updated");
            return new ActionOutcome(customerDataPrompt(readiness.needsName(), readiness.needsPhone()), false,
                    response(draft));
        }
        if (draft.getMetodoPago() == null) {
            return paymentPrompt(draft);
        }
        draft.setStatus(CrmWhatsappAiSaleDraftStatus.READY_FOR_REVIEW);
        saveAndPublish(draft, "ai.sale_draft.ready");
        return new ActionOutcome("Pedido confirmado. Un asesor revisará stock y precio antes de completar la venta.", true, response(draft));
    }

    private ActionOutcome paymentPrompt(CrmWhatsappAiSaleDraft draft) {
        draftRepository.save(draft);
        List<String> methods = commercialQueryService.paymentMethods(draft.getConversation()).methods().stream()
                .map(PaymentMethodItem::name).toList();
        publish(draft, "ai.sale_draft.updated");
        return new ActionOutcome("Perfecto 💛 Tus datos quedaron registrados. ¿Cómo deseas pagar? Opciones: "
                + String.join(", ", methods) + ".", false, response(draft));
    }

    private CustomerReadiness prepareCustomerData(CrmWhatsappAiSaleDraft draft) {
        CrmWhatsappConversation conversation = draft.getConversation();
        Cliente client = conversation.getCliente();
        if (client == null) {
            String conversationPhone = CrmWhatsappPhoneUtils.normalizePeruvianMobile(conversation.getPhoneNumber());
            if (!conversationPhone.isBlank()) {
                List<Cliente> matches = clienteRepository
                        .findByEmpresa_IdEmpresaAndTelefonoInAndDeletedAtIsNullOrderByIdClienteAsc(
                                draft.getConnection().getEmpresa().getIdEmpresa(),
                                CrmWhatsappPhoneUtils.variants(conversationPhone));
                if (matches.size() > 1) {
                    return new CustomerReadiness(false, false, true,
                            "Encontramos varios clientes con ese celular. Un asesor debe seleccionar el correcto.");
                }
                if (matches.size() == 1) {
                    client = matches.getFirst();
                    conversation.setCliente(client);
                    syncConversation(conversation, client);
                    conversationRepository.save(conversation);
                    auditCustomer(draft, client, "CLIENT_LINKED");
                    publishConversationUpdated(conversation);
                }
            }
        }
        String phone = client == null ? "" : CrmWhatsappPhoneUtils.normalizePeruvianMobile(client.getTelefono());
        String name = client == null ? "" : clean(client.getNombres());
        draft.setPendingCustomerPhone(phone.isBlank() ? null : phone);
        draft.setPendingCustomerName(validCustomerName(name, phone) ? name : null);
        return new CustomerReadiness(!validCustomerName(name, phone), phone.isBlank(), false, "");
    }

    private CustomerResolution resolveCustomer(CrmWhatsappAiSaleDraft draft) {
        CrmWhatsappConversation conversation = draft.getConversation();
        String phone = CrmWhatsappPhoneUtils.normalizePeruvianMobile(draft.getPendingCustomerPhone());
        String submittedName = clean(draft.getPendingCustomerName());
        Cliente linkedClient = conversation.getCliente();
        Integer companyId = draft.getConnection().getEmpresa().getIdEmpresa();
        List<Cliente> matches = clienteRepository
                .findByEmpresa_IdEmpresaAndTelefonoInAndDeletedAtIsNullOrderByIdClienteAsc(
                        companyId, CrmWhatsappPhoneUtils.variants(phone));

        if (matches.size() > 1) {
            return CustomerResolution.human("Encontramos varios clientes con ese celular. Un asesor debe seleccionar el correcto.");
        }

        if (matches.isEmpty()) {
            List<Cliente> archivedMatches = clientWriter.findMatchesIncludingDeleted(
                    companyId, CrmWhatsappPhoneUtils.variants(phone));
            if (archivedMatches.size() > 1) {
                return CustomerResolution.human(
                        "Encontramos varios clientes con ese celular. Un asesor debe seleccionar el correcto.");
            }
            if (archivedMatches.size() == 1) {
                matches = List.of(clientWriter.restore(archivedMatches.getFirst().getIdCliente()));
            }
        }

        Cliente client;
        if (matches.size() == 1) {
            client = matches.getFirst();
            if (!"ACTIVO".equalsIgnoreCase(clean(client.getEstado()))) {
                client = clientWriter.restore(client.getIdCliente());
            }
            boolean relationshipChanged = linkedClient == null
                    || !linkedClient.getIdCliente().equals(client.getIdCliente());
            completeOrSuggestName(draft, client, submittedName, phone);
            conversation.setCliente(client);
            if (relationshipChanged) auditCustomer(draft, client, "CLIENT_LINKED");
        } else if (linkedClient != null
                && (CrmWhatsappPhoneUtils.normalizePeruvianMobile(linkedClient.getTelefono()).isBlank()
                    || phone.equals(CrmWhatsappPhoneUtils.normalizePeruvianMobile(linkedClient.getTelefono())))) {
            client = linkedClient;
            boolean changed = false;
            if (CrmWhatsappPhoneUtils.normalizePeruvianMobile(client.getTelefono()).isBlank()) {
                client.setTelefono(phone);
                changed = true;
            }
            if (!validCustomerName(client.getNombres(), phone)) {
                client.setNombres(submittedName);
                changed = true;
            }
            if (changed) {
                client = clienteRepository.save(client);
                clearCustomerNameSuggestion(draft);
                auditCustomer(draft, client, "CLIENT_COMPLETED");
            } else {
                updateCustomerNameSuggestion(draft, client.getNombres(), submittedName, phone);
            }
        } else {
            Usuario aiActor = usuarioRepository.findByCorreoAndDeletedAtIsNull("ia-kiments@system.local")
                    .orElseThrow(() -> new IllegalStateException("Usuario IA Kiments no configurado"));
            try {
                client = clientWriter.create(draft.getConnection().getEmpresa(), aiActor, submittedName, phone);
                clearCustomerNameSuggestion(draft);
                auditCustomer(draft, client, "CLIENT_CREATED");
            } catch (DataIntegrityViolationException duplicate) {
                List<Cliente> concurrent = clientWriter.findMatchesAfterConflict(
                        companyId, CrmWhatsappPhoneUtils.variants(phone));
                if (concurrent.size() > 1) {
                    return CustomerResolution.human(
                            "Encontramos varios clientes con ese celular. Un asesor debe seleccionar el correcto.");
                }
                if (concurrent.isEmpty()) {
                    return CustomerResolution.human(
                            "No pudimos recuperar el cliente registrado. Un asesor debe revisar los datos.");
                }
                client = concurrent.getFirst().getDeletedAt() == null
                        && "ACTIVO".equalsIgnoreCase(clean(concurrent.getFirst().getEstado()))
                                ? concurrent.getFirst()
                                : clientWriter.restore(concurrent.getFirst().getIdCliente());
                completeOrSuggestName(draft, client, submittedName, phone);
                auditCustomer(draft, client, "CLIENT_LINKED");
            }
            conversation.setCliente(client);
        }

        draft.setPendingCustomerName(client.getNombres());
        draft.setPendingCustomerPhone(phone);
        syncConversation(conversation, client);
        conversationRepository.save(conversation);
        publishConversationUpdated(conversation);
        return CustomerResolution.ok();
    }

    private void completeOrSuggestName(CrmWhatsappAiSaleDraft draft, Cliente client,
            String submittedName, String phone) {
        if (!validCustomerName(client.getNombres(), phone)) {
            client.setNombres(submittedName);
            clienteRepository.save(client);
            clearCustomerNameSuggestion(draft);
            auditCustomer(draft, client, "CLIENT_COMPLETED");
            return;
        }
        updateCustomerNameSuggestion(draft, client.getNombres(), submittedName, phone);
    }

    private void updateCustomerNameSuggestion(CrmWhatsappAiSaleDraft draft, String currentName,
            String submittedName, String phone) {
        if (!validCustomerName(submittedName, phone)
                || normalize(currentName).equals(normalize(submittedName))) {
            clearCustomerNameSuggestion(draft);
            return;
        }
        draft.setCustomerNameCurrent(clean(currentName));
        draft.setCustomerNameSuggested(clean(submittedName));
        draft.setCustomerNameSuggestionStatus(CrmWhatsappAiCustomerNameSuggestionStatus.PENDING);
    }

    private void clearCustomerNameSuggestion(CrmWhatsappAiSaleDraft draft) {
        draft.setCustomerNameCurrent(null);
        draft.setCustomerNameSuggested(null);
        draft.setCustomerNameSuggestionStatus(null);
    }

    private void publishConversationUpdated(CrmWhatsappConversation conversation) {
        applicationEventPublisher.publishEvent(
                new CrmWhatsappConversationUpdatedEvent(conversation.getIdConversation()));
    }

    private void syncConversation(CrmWhatsappConversation conversation, Cliente client) {
        conversation.setContactName(client.getNombres());
        conversation.setPhoneNumber(CrmWhatsappPhoneUtils.normalizePeruvianMobile(client.getTelefono()));
    }

    private void auditCustomer(CrmWhatsappAiSaleDraft draft, Cliente client, String event) {
        Usuario actor = usuarioRepository.findByCorreoAndDeletedAtIsNull("ia-kiments@system.local").orElse(null);
        auditService.record(draft.getConnection(), draft.getConversation(), null, actor, event, "INFO",
                "Cliente gestionado por IA Kiments", Map.of("clientId", client.getIdCliente()));
    }

    private String customerDataPrompt(boolean needsName, boolean needsPhone) {
        if (needsName && needsPhone) {
            return "Para continuar con tu pedido, compárteme tu nombre completo y tu celular peruano de 9 dígitos, por favor.";
        }
        if (needsName) return "Para continuar con tu pedido, compárteme tu nombre completo, por favor.";
        return "Para continuar con tu pedido, compárteme tu celular peruano de 9 dígitos, por favor.";
    }

    private String extractPhone(String message) {
        java.util.regex.Matcher matcher = customerPhonePattern().matcher(clean(message));
        if (!matcher.find()) return "";
        return CrmWhatsappPhoneUtils.normalizePeruvianMobile(matcher.group().replaceAll("\\D", ""));
    }

    private String extractName(String message, String phone, boolean needed) {
        if (!needed) return "";
        String candidate = clean(message);
        if (!phone.isBlank()) {
            candidate = customerPhonePattern().matcher(candidate).replaceAll(" ");
        }
        candidate = candidate.replaceAll("(?i)\\b(nombre|telefono|teléfono|celular|soy|me llamo|mi nombre es)\\b", " ")
                .replaceAll("[:;,]", " ").replaceAll("\\s+", " ").trim();
        long letters = candidate.codePoints().filter(Character::isLetter).count();
        return letters >= 2 && candidate.length() <= 150 ? candidate : "";
    }

    private java.util.regex.Pattern customerPhonePattern() {
        return java.util.regex.Pattern.compile("(?<!\\d)(?:\\+?51[\\s()\\-]*)?9(?:[\\s()\\-]*\\d){8}(?!\\d)");
    }

    private boolean validCustomerName(String name, String phone) {
        String value = clean(name);
        if (value.length() < 2 || value.length() > 150) return false;
        String normalizedPhone = CrmWhatsappPhoneUtils.normalizePeruvianMobile(phone);
        return normalizedPhone.isBlank() || !normalize(value).equals(normalize("CLIENTE " + normalizedPhone));
    }

    private boolean isLikelyCustomerNameReply(String message) {
        String value = clean(message);
        if (value.isBlank() || value.contains("?") || value.contains("¿")) return false;
        String normalized = normalize(value);
        if (normalized.matches(".*\\b(hola|buenas|gracias|si|no|ok|quiero|deseo|busco|producto|modelo|color|talla|precio|stock|envio|tienda|horario|pago|promocion|oferta|pedido|cancelar|confirmar)\\b.*")) {
            return false;
        }
        long letters = value.codePoints().filter(Character::isLetter).count();
        int words = normalized.isBlank() ? 0 : normalized.split("\\s+").length;
        return letters >= 2 && words >= 1 && words <= 6 && value.length() <= 150;
    }

    private boolean isExplicitPaymentSelection(String message, String paymentMethod) {
        String value = normalize(message).replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ").trim();
        String method = normalize(paymentMethod).replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ").trim();
        if (method.isBlank()) return false;
        if (value.equals(method) || value.equals(method + " por favor")) return true;
        String quotedMethod = Pattern.quote(method);
        return value.matches("^(quiero|elijo|prefiero|usare|pagare|voy a pagar|pago|pagar|por) (con )?"
                + quotedMethod + "( por favor)?$")
                || value.matches("^" + quotedMethod + "( lo)? (voy a usar|voy a pagar|usare|pagare)( por favor)?$");
    }

    private ActionOutcome setPayment(CrmWhatsappConversation conversation, CrmWhatsappAiSaleDraft draft, String requested) {
        if (draft == null || draft.getItems().isEmpty()) return new ActionOutcome("Primero debemos preparar el pedido.", false, null);
        if (draft.getStatus() == CrmWhatsappAiSaleDraftStatus.AWAITING_CUSTOMER_DATA
                || conversation.getCliente() == null) {
            return new ActionOutcome("Primero necesito completar tus datos de cliente.", false, response(draft));
        }
        List<PaymentMethodItem> methods = commercialQueryService.paymentMethods(conversation).methods();
        String target = normalize(requested);
        List<PaymentMethodItem> matches = methods.stream().filter(item -> {
            String value = normalize(item.name()); return !target.isBlank() && (value.contains(target) || target.contains(value));
        }).toList();
        PaymentMethodItem selected = matches.size() == 1 ? matches.getFirst() : null;
        if (selected == null && draft.getMetodoPago() != null) {
            selected = methods.stream()
                    .filter(item -> item.paymentMethodId().equals(draft.getMetodoPago().getIdMetodoPago()))
                    .findFirst().orElse(null);
        }
        if (selected == null) return new ActionOutcome("Elige un método disponible: "
                + String.join(", ", methods.stream().map(PaymentMethodItem::name).toList()) + ".", false, response(draft));
        Integer id = selected.paymentMethodId();
        MetodoPagoConfig paymentMethod = paymentRepository
                .findBySucursal_IdSucursalAndMetodoPago_IdMetodoPagoAndDeletedAtIsNull(
                        draft.getSucursal().getIdSucursal(), id)
                .map(SucursalMetodoPagoConfig::getMetodoPago).orElseThrow();
        draft.setMetodoPago(paymentMethod);
        draft.setExpiresAt(LocalDateTime.now().plusHours(24));
        if (isCustomerConfirmed(draft)) {
            if (normalize(selected.name()).contains("EFECTIVO")) {
                draft.setStatus(CrmWhatsappAiSaleDraftStatus.READY_FOR_REVIEW);
                saveAndPublish(draft, "ai.sale_draft.ready");
                return new ActionOutcome("Método registrado: " + selected.name()
                        + ". Un asesor completará la venta contigo.", true, response(draft));
            }
            List<MetodoPagoCuenta> accounts = paymentMethod.getCuentas() == null ? List.of()
                    : paymentMethod.getCuentas().stream()
                            .filter(account -> !Boolean.FALSE.equals(account.getActivo()))
                            .filter(account -> account.getNumeroCuenta() != null && !account.getNumeroCuenta().isBlank())
                            .sorted(java.util.Comparator.comparing(MetodoPagoCuenta::getIdMetodoPagoCuenta))
                            .toList();
            if (accounts.isEmpty()) {
                return new ActionOutcome("El método " + selected.name()
                        + " no tiene una cuenta configurada. Elige otro método de pago.", false, response(draft));
            }
            MetodoPagoCuenta account = accounts.size() == 1 ? accounts.getFirst() : matchAccount(accounts, target);
            if (account == null) {
                String options = accounts.stream().map(this::accountOption)
                        .collect(java.util.stream.Collectors.joining("\n"));
                draftRepository.save(draft);
                publish(draft, "ai.sale_draft.updated");
                return new ActionOutcome("Tenemos varias cuentas para " + selected.name()
                        + ". Indica cuál deseas usar:\n" + options, false, response(draft));
            }
            List<String> warnings = revalidateItems(draft, true);
            if (!warnings.isEmpty()) {
                return new ActionOutcome(String.join(". ", warnings)
                        + ". El pedido debe confirmarse nuevamente.", false, response(draft));
            }
            var instructions = aiPaymentService.create(draft, account);
            draft.setStatus(CrmWhatsappAiSaleDraftStatus.PAYMENT_PENDING);
            draft.setExpiresAt(instructions.request().getExpiresAt());
            saveAndPublish(draft, "ai.sale_draft.updated");
            return new ActionOutcome(instructions.message(), false, response(draft));
        }
        draft.setStatus(CrmWhatsappAiSaleDraftStatus.AWAITING_CUSTOMER);
        saveAndPublish(draft, "ai.sale_draft.updated");
        return new ActionOutcome(summary(draft, true), false, response(draft));
    }

    private MetodoPagoCuenta matchAccount(List<MetodoPagoCuenta> accounts, String requested) {
        if (requested == null || requested.isBlank()) return null;
        List<MetodoPagoCuenta> matches = accounts.stream().filter(account -> {
            String number = normalize(account.getNumeroCuenta());
            String holder = normalize(account.getTitular());
            String numberDigits = clean(account.getNumeroCuenta()).replaceAll("\\D", "");
            String requestedDigits = requested.replaceAll("\\D", "");
            return (!number.isBlank() && requested.contains(number))
                    || (!holder.isBlank() && requested.contains(holder))
                    || (numberDigits.length() >= 4 && requestedDigits.contains(numberDigits));
        }).toList();
        return matches.size() == 1 ? matches.getFirst() : null;
    }

    private String accountOption(MetodoPagoCuenta account) {
        String holder = clean(account.getTitular());
        return "- " + (holder.isBlank() ? "Cuenta" : holder) + ": " + clean(account.getNumeroCuenta());
    }

    private CrmWhatsappAiSaleDraft createDraft(CrmWhatsappConversation conversation) {
        CrmWhatsappAiSaleDraft draft = new CrmWhatsappAiSaleDraft();
        draft.setConversation(conversation); draft.setConnection(conversation.getConnection());
        draft.setSucursal(conversation.getConnection().getSucursal());
        return draftRepository.save(draft);
    }

    private CrmWhatsappAiSaleDraft activeDraft(Long conversationId) {
        CrmWhatsappAiSaleDraft draft = draftRepository
                .findFirstByConversation_IdConversationAndStatusInOrderByCreatedAtDesc(conversationId, ACTIVE).orElse(null);
        if (draft != null && draft.getExpiresAt().isBefore(LocalDateTime.now())) {
            draft.setStatus(CrmWhatsappAiSaleDraftStatus.EXPIRED);
            clearCustomerNameSuggestion(draft);
            draftRepository.save(draft);
            publish(draft, "ai.sale_draft.expired"); return null;
        }
        return draft;
    }

    private CrmWhatsappAiSaleDraft requireActiveDraft(Long conversationId) {
        CrmWhatsappAiSaleDraft draft = activeDraft(conversationId);
        if (draft == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe un pedido IA activo");
        return draft;
    }

    private List<VariantCandidate> candidates(CatalogResult catalog, String color, String size) {
        String colorKey = normalize(color); String sizeKey = normalize(size);
        List<VariantCandidate> result = new ArrayList<>();
        for (ProductResult product : catalog.products()) for (VariantResult variant : product.variants()) {
            if (!variant.available() || variant.stock() == null || variant.stock() <= 0) continue;
            if (!colorKey.isBlank() && !normalize(variant.color()).equals(colorKey)) continue;
            if (!sizeKey.isBlank() && !normalize(variant.size()).equals(sizeKey)) continue;
            result.add(new VariantCandidate(product, variant));
        }
        return result;
    }

    private CrmWhatsappAiSaleDraftItem findDraftItem(CrmWhatsappAiSaleDraft draft, SaleActionResult action) {
        String query = normalize(action.productQuery()); String color = normalize(action.color()); String size = normalize(action.size());
        List<CrmWhatsappAiSaleDraftItem> matches = draft.getItems().stream()
                .filter(item -> query.isBlank() || normalize(item.getProductName()).contains(query) || normalize(item.getSku()).contains(query))
                .filter(item -> color.isBlank() || normalize(item.getColor()).equals(color))
                .filter(item -> size.isBlank() || normalize(item.getSize()).equals(size)).toList();
        return matches.size() == 1 ? matches.get(0) : null;
    }

    private List<String> revalidateItems(CrmWhatsappAiSaleDraft draft, boolean updateSnapshots) {
        List<String> warnings = new ArrayList<>();
        boolean priceChanged = false;
        BigDecimal previousPromotionDiscount = money(draft.getPromotionDiscount());
        for (CrmWhatsappAiSaleDraftItem item : draft.getItems()) {
            SucursalStock stock = stockRepository.findBySucursalIdSucursalAndProductoVarianteIdProductoVariante(
                    draft.getSucursal().getIdSucursal(), item.getVariantId()).orElse(null);
            if (stock == null || stock.getProductoVariante() == null || stock.getProductoVariante().getDeletedAt() != null) {
                warnings.add(item.getProductName() + " ya no está disponible"); continue;
            }
            BigDecimal currentPrice = money(precioOfertaService.resolverPrecioVigente(
                    stock.getProductoVariante(), draft.getSucursal().getIdSucursal()));
            if (stock.getCantidad() < item.getQuantity()) warnings.add("Stock insuficiente para " + item.getProductName());
            if (currentPrice.compareTo(item.getUnitPrice()) != 0) {
                warnings.add("El precio cambió para " + item.getProductName());
                priceChanged = true;
            }
            Producto product = stock.getProductoVariante().getProducto();
            if (preventaChanged(item, product)) {
                warnings.add("La fecha de envío de preventa cambió para " + item.getProductName());
                priceChanged = true;
            }
            if (updateSnapshots) {
                item.setStockSnapshot(stock.getCantidad());
                item.setUnitPrice(currentPrice);
                item.setRegularUnitPrice(money(stock.getProductoVariante().getPrecio()));
                applyPreventaSnapshot(item, product);
            }
        }
        calculate(draft);
        boolean promotionChanged = previousPromotionDiscount.compareTo(money(draft.getPromotionDiscount())) != 0;
        if (promotionChanged) warnings.add("La promocion del pedido cambio");
        if (updateSnapshots && (priceChanged || promotionChanged)) {
            draft.setVersion(draft.getVersion() + 1);
            invalidateConfirmation(draft);
            draft.setStatus(CrmWhatsappAiSaleDraftStatus.AWAITING_CUSTOMER);
            draft.setExpiresAt(LocalDateTime.now().plusHours(24));
        }
        return warnings;
    }

    private void rebuildFromAdvisor(CrmWhatsappAiSaleDraft draft, SaleDraftUpdateRequest request) {
        if (request.items().isEmpty()) throw conflict("El pedido no puede quedar vacio");
        Map<Integer, Integer> quantities = new LinkedHashMap<>();
        for (SaleDraftLineRequest line : request.items()) {
            if (line == null || line.variantId() == null || line.quantity() == null || line.quantity() < 1)
                throw conflict("El pedido contiene cantidades invalidas");
            quantities.merge(line.variantId(), line.quantity(), Integer::sum);
        }
        draft.getItems().clear();
        for (Map.Entry<Integer, Integer> entry : quantities.entrySet()) {
            SucursalStock stock = requireStock(draft, entry.getKey());
            if (stock.getProductoVariante() == null || stock.getProductoVariante().getDeletedAt() != null
                    || !"ACTIVO".equalsIgnoreCase(clean(stock.getProductoVariante().getEstado()))
                    || stock.getCantidad() < entry.getValue()) {
                throw conflict("Una variante ya no tiene stock suficiente");
            }
            ProductoVariante variant = stock.getProductoVariante();
            CrmWhatsappAiSaleDraftItem item = new CrmWhatsappAiSaleDraftItem();
            item.setDraft(draft); item.setProductId(variant.getProducto().getIdProducto());
            item.setVariantId(variant.getIdProductoVariante()); item.setProductName(variant.getProducto().getNombre());
            item.setSku(variant.getSku()); item.setColor(variant.getColor() == null ? "" : variant.getColor().getNombre());
            item.setSize(variant.getTalla() == null ? "" : variant.getTalla().getNombre());
            item.setQuantity(entry.getValue()); item.setStockSnapshot(stock.getCantidad());
            item.setUnitPrice(money(precioOfertaService.resolverPrecioVigente(variant, draft.getSucursal().getIdSucursal())));
            item.setRegularUnitPrice(money(variant.getPrecio()));
            applyPreventaSnapshot(item, variant.getProducto());
            draft.getItems().add(item);
        }
        if (request.paymentMethodId() != null) {
            draft.setMetodoPago(paymentRepository.findBySucursal_IdSucursalAndMetodoPago_IdMetodoPagoAndDeletedAtIsNull(
                    draft.getSucursal().getIdSucursal(), request.paymentMethodId())
                    .filter(config -> "ACTIVO".equalsIgnoreCase(clean(config.getEstado())))
                    .map(SucursalMetodoPagoConfig::getMetodoPago)
                    .orElseThrow(() -> conflict("El metodo de pago no esta habilitado")));
        }
        draft.setVersion(draft.getVersion() + 1);
        calculate(draft);
    }

    private SucursalStock requireStock(CrmWhatsappAiSaleDraft draft, Integer variantId) {
        return stockRepository.findBySucursalIdSucursalAndProductoVarianteIdProductoVariante(
                draft.getSucursal().getIdSucursal(), variantId)
                .orElseThrow(() -> conflict("La variante ya no está disponible"));
    }

    private void refreshItem(CrmWhatsappAiSaleDraftItem item, SucursalStock stock) {
        item.setStockSnapshot(stock.getCantidad());
        item.setUnitPrice(money(precioOfertaService.resolverPrecioVigente(stock.getProductoVariante(), stock.getSucursal().getIdSucursal())));
        item.setRegularUnitPrice(money(stock.getProductoVariante().getPrecio()));
        applyPreventaSnapshot(item, stock.getProductoVariante().getProducto());
    }

    private void applyPreventaSnapshot(CrmWhatsappAiSaleDraftItem item, Producto product) {
        boolean active = preventaActiva(product);
        item.setPreventa(active);
        item.setFechaEnvioPreventa(active ? product.getFechaEnvioPreventa() : null);
    }

    private boolean preventaChanged(CrmWhatsappAiSaleDraftItem item, Producto product) {
        boolean active = preventaActiva(product);
        LocalDate shippingDate = active ? product.getFechaEnvioPreventa() : null;
        return !Objects.equals(Boolean.TRUE.equals(item.getPreventa()), active)
                || !Objects.equals(item.getFechaEnvioPreventa(), shippingDate);
    }

    private boolean preventaActiva(Producto product) {
        return product != null
                && Boolean.TRUE.equals(product.getPreventa())
                && product.getFechaEnvioPreventa() != null
                && product.getFechaEnvioPreventa().isAfter(LocalDate.now());
    }

    private void changed(CrmWhatsappAiSaleDraft draft) {
        draft.setVersion(draft.getVersion() + 1); invalidateConfirmation(draft);
        draft.setExpiresAt(LocalDateTime.now().plusHours(24)); calculate(draft);
    }

    private void invalidateConfirmation(CrmWhatsappAiSaleDraft draft) {
        draft.setConfirmedVersion(null); draft.setCustomerConfirmedAt(null); draft.setConfirmationMessageId(null);
    }

    private boolean isCustomerConfirmed(CrmWhatsappAiSaleDraft draft) {
        return draft.getConfirmedVersion() != null && draft.getConfirmedVersion().equals(draft.getVersion())
                && draft.getCustomerConfirmedAt() != null;
    }

    private void calculate(CrmWhatsappAiSaleDraft draft) {
        List<ProductoVariante> variants = new ArrayList<>();
        Map<Integer, Integer> quantities = new LinkedHashMap<>();
        for (CrmWhatsappAiSaleDraftItem item : draft.getItems()) {
            SucursalStock stock = stockRepository
                    .findBySucursalIdSucursalAndProductoVarianteIdProductoVariante(
                            draft.getSucursal().getIdSucursal(), item.getVariantId())
                    .orElse(null);
            if (stock == null || stock.getProductoVariante() == null) continue;
            variants.add(stock.getProductoVariante());
            quantities.put(item.getVariantId(), item.getQuantity());
        }
        EcommerceCarritoResumenResponse totals = promotionService.calcular(
                promotionService.itemsDesdeVariantes(variants, quantities, draft.getSucursal().getIdSucursal()));
        draft.setSubtotal(totals.subtotal());
        draft.setPromotionDiscount(totals.descuentoPromocion());
        draft.setTotal(totals.total());
        draft.getPromotions().clear();
        for (EcommerceCarritoResumenResponse.ComboAplicado applied : totals.combosAplicados()) {
            CrmWhatsappAiSaleDraftPromotion promotion = new CrmWhatsappAiSaleDraftPromotion();
            promotion.setDraft(draft);
            promotion.setPromotionId(applied.idPromocionCombo());
            promotion.setName(applied.nombre());
            promotion.setRuleDescription(applied.regla());
            promotion.setRegularPrice(applied.precioNormal());
            promotion.setComboPrice(applied.precioCombo());
            promotion.setDiscount(applied.descuento());
            draft.getPromotions().add(promotion);
        }
    }

    private String summary(CrmWhatsappAiSaleDraft draft, boolean askConfirmation) {
        StringBuilder text = new StringBuilder("🛍️ Así quedaría tu pedido:\n");
        for (CrmWhatsappAiSaleDraftItem item : draft.getItems()) {
            text.append("- ").append(item.getQuantity()).append(" x ").append(item.getProductName());
            if (!clean(item.getColor()).isBlank()) text.append(" ").append(item.getColor());
            if (!clean(item.getSize()).isBlank()) text.append(" talla ").append(item.getSize());
            text.append(" - S/").append(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())).setScale(2)).append("\n");
            if (item.getRegularUnitPrice() != null && item.getUnitPrice().compareTo(item.getRegularUnitPrice()) < 0) {
                text.append("  Oferta aplicada: S/").append(item.getUnitPrice().setScale(2)).append(" c/u\n");
            }
            if (Boolean.TRUE.equals(item.getPreventa()) && item.getFechaEnvioPreventa() != null) {
                text.append("  📦 Preventa - envíos desde: ")
                        .append(CUSTOMER_DATE_FORMAT.format(item.getFechaEnvioPreventa()))
                        .append("\n");
            }
        }
        for (CrmWhatsappAiSaleDraftPromotion promotion : draft.getPromotions()) {
            text.append("- Combo ").append(promotion.getName()).append(": -S/")
                    .append(promotion.getDiscount().setScale(2)).append("\n");
        }
        text.append("Subtotal: S/").append(draft.getSubtotal().setScale(2)).append("\n");
        if (draft.getPromotionDiscount().signum() > 0) {
            text.append("Descuento promocional: -S/").append(draft.getPromotionDiscount().setScale(2)).append("\n");
        }
        text.append("Total estimado: S/").append(draft.getTotal().setScale(2));
        if (askConfirmation) {
            text.append("\n\nTu pedido queda guardado. Cuando desees continuar, puedes confirmarlo.")
                    .append("\n\n¿En qué más puedo ayudarte?");
        }
        return text.toString();
    }

    private void saveAndPublish(CrmWhatsappAiSaleDraft draft, String event) {
        calculate(draft); draftRepository.save(draft); publish(draft, event);
    }

    private void publish(CrmWhatsappAiSaleDraft draft, String type) {
        Map<String, Object> event = Map.of("type", type, "conversationId",
                draft.getConversation().getIdConversation(), "draft", response(draft));
        Integer assigned = draft.getConversation().getAssignedUser() == null ? null
                : draft.getConversation().getAssignedUser().getIdUsuario();
        eventService.publishAfterCommit(event, event, assigned,
                "ESPERA".equals(draft.getConversation().getStatus()) && assigned == null);
    }

    private SaleDraftResponse response(CrmWhatsappAiSaleDraft draft) { return response(draft, List.of()); }
    private SaleDraftResponse response(CrmWhatsappAiSaleDraft draft, List<String> warnings) {
        return new SaleDraftResponse(draft.getIdAiSaleDraft(), draft.getConversation().getIdConversation(),
                draft.getStatus().name(), draft.getVersion(), isCustomerConfirmed(draft), draft.getCustomerConfirmedAt(),
                draft.getMetodoPago() == null ? null : draft.getMetodoPago().getIdMetodoPago(),
                draft.getMetodoPago() == null ? null : draft.getMetodoPago().getNombre(),
                draft.getPendingCustomerName(), draft.getPendingCustomerPhone(),
                draft.getCustomerNameCurrent(), draft.getCustomerNameSuggested(),
                draft.getCustomerNameSuggestionStatus() == null ? null : draft.getCustomerNameSuggestionStatus().name(),
                draft.getPendingPromotionId(),
                draft.getSubtotal(), draft.getPromotionDiscount(), draft.getTotal(), draft.getExpiresAt(), warnings,
                draft.getPromotions().stream().map(item -> new SaleDraftPromotionResponse(item.getPromotionId(),
                        item.getName(), item.getRuleDescription(), item.getRegularPrice(), item.getComboPrice(),
                        item.getDiscount())).toList(),
                draft.getItems().stream().map(item -> new SaleDraftItemResponse(item.getProductId(), item.getVariantId(),
                        item.getProductName(), item.getSku(), item.getColor(), item.getSize(), item.getQuantity(),
                        item.getRegularUnitPrice(), item.getUnitPrice(), item.getStockSnapshot(), item.getImageUrl(),
                        Boolean.TRUE.equals(item.getPreventa()), item.getFechaEnvioPreventa())).toList());
    }

    private Usuario requireCrmUser(Usuario user) {
        if (user == null || user.getIdUsuario() == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autenticado");
        Usuario actor = usuarioRepository.findByIdUsuarioAndDeletedAtIsNull(user.getIdUsuario())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autenticado"));
        if (!(actor.getRol() == Rol.ADMINISTRADOR || actor.getRol() == Rol.SISTEMA || Boolean.TRUE.equals(actor.getAccesoCrm())))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sin acceso al CRM");
        return actor;
    }
    private CrmWhatsappConversation requireConversation(Long id) { return conversationRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversacion no encontrada")); }
    private void requireCanRead(CrmWhatsappConversation c, Usuario u) {
        if (u.getRol().esAdministrador()) return;
        if (c.getAssignedUser() != null && c.getAssignedUser().getIdUsuario().equals(u.getIdUsuario())) return;
        if (c.getAssignedUser() == null && "ESPERA".equals(c.getStatus())) return;
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puedes acceder a esta conversacion");
    }
    private void requireCanOperate(CrmWhatsappConversation c, Usuario u) {
        if (c.getAssignedUser() == null || !c.getAssignedUser().getIdUsuario().equals(u.getIdUsuario()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acepta el chat antes de preparar la venta");
    }
    private ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
    private BigDecimal money(Double value) { return BigDecimal.valueOf(value == null ? 0 : value).setScale(2, RoundingMode.HALF_UP); }
    private BigDecimal money(BigDecimal value) { return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP); }
    private String clean(String value) { return value == null ? "" : value.trim(); }
    private String normalize(String value) { return java.text.Normalizer.normalize(clean(value), java.text.Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "").toUpperCase(Locale.ROOT)
            .replaceAll("[^A-Z0-9]+", " ").trim(); }
    private boolean containsWholePhrase(String text, String phrase) {
        return (" " + text + " ").contains(" " + phrase + " ");
    }

    private record VariantCandidate(ProductResult product, VariantResult variant) {}
    private record BatchResolvedLine(ProductResult product, VariantResult variant,
            int addedQuantity, int finalQuantity) {}
    private record BatchAcceptedLine(ProductResult product, VariantResult variant, int quantity) {}
    private record CustomerReadiness(boolean needsName, boolean needsPhone, boolean requiresHuman, String message) {}
    private record CustomerResolution(boolean requiresHuman, String message) {
        static CustomerResolution ok() { return new CustomerResolution(false, ""); }
        static CustomerResolution human(String message) { return new CustomerResolution(true, message); }
    }
    public record RevalidateRequest(Boolean markImported) {}
    public record SaleDraftLineRequest(Integer variantId, Integer quantity) {}
    public record SaleDraftUpdateRequest(List<SaleDraftLineRequest> items, Integer paymentMethodId) {}
    public record SaleDraftItemResponse(Integer productId, Integer variantId, String productName, String sku,
            String color, String size, Integer quantity, BigDecimal regularUnitPrice, BigDecimal unitPrice,
            Integer stock, String imageUrl, boolean preventa, LocalDate fechaEnvioPreventa) {}
    public record SaleDraftPromotionResponse(Integer promotionId, String name, String rule,
            BigDecimal regularPrice, BigDecimal comboPrice, BigDecimal discount) {}
    public record SaleDraftResponse(Long id, Long conversationId, String status, Integer version,
            boolean customerConfirmed, LocalDateTime customerConfirmedAt, Integer paymentMethodId,
            String paymentMethod, String pendingCustomerName, String pendingCustomerPhone,
            String customerNameCurrent, String customerNameSuggested, String customerNameSuggestionStatus,
            Integer pendingPromotionId, BigDecimal subtotal, BigDecimal promotionDiscount, BigDecimal total,
            LocalDateTime expiresAt, List<String> warnings, List<SaleDraftPromotionResponse> promotions,
            List<SaleDraftItemResponse> items) {}
    public record CustomerNameSuggestionRequest(String action) {}
    public record ActionOutcome(String response, boolean readyForReview, SaleDraftResponse draft) {
        static ActionOutcome human(String message) { return new ActionOutcome(message, false, null); }
    }
    public record CrmWhatsappAiSaleConfirmationRequested(Long conversationId, Long draftId, String message, Usuario actor) {}
}
