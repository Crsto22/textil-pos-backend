package com.sistemapos.sistematextil.services.agent.runtime;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.sistemapos.sistematextil.model.CrmWhatsappAgentState;
import com.sistemapos.sistematextil.model.CrmWhatsappAgentSession;
import com.sistemapos.sistematextil.model.CrmWhatsappConversation;
import com.sistemapos.sistematextil.model.CrmWhatsappFulfillmentMethod;
import com.sistemapos.sistematextil.model.CrmWhatsappPaymentRequestStatus;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAgentSessionRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappPaymentRequestRepository;
import com.sistemapos.sistematextil.services.agent.ai.AgentCommercialAction;
import com.sistemapos.sistematextil.services.agent.ai.AgentCartAction;
import com.sistemapos.sistematextil.services.agent.ai.AgentConversationDecision;
import com.sistemapos.sistematextil.services.agent.ai.AgentShalomDataField;
import com.sistemapos.sistematextil.services.agent.cart.AgentCartService;
import com.sistemapos.sistematextil.services.agent.cart.AgentCartService.CartItemView;
import com.sistemapos.sistematextil.services.agent.cart.AgentCartService.CartPromotionView;
import com.sistemapos.sistematextil.services.agent.cart.AgentCartService.CartResult;
import com.sistemapos.sistematextil.services.agent.catalog.AgentCatalogQueryService;
import com.sistemapos.sistematextil.services.agent.customer.AgentCustomerService;
import com.sistemapos.sistematextil.services.agent.fulfillment.AgentFulfillmentService;
import com.sistemapos.sistematextil.services.agent.film.AgentFilmFinalizationService;
import com.sistemapos.sistematextil.services.agent.film.AgentFilmService;
import com.sistemapos.sistematextil.services.agent.payment.AgentPaymentService;
import com.sistemapos.sistematextil.services.agent.payment.AgentPaymentService.PaymentMethodList;
import com.sistemapos.sistematextil.services.agent.payment.AgentPaymentService.PaymentMethodView;
import com.sistemapos.sistematextil.services.agent.payment.AgentPaymentService.PaymentRequestResult;
import com.sistemapos.sistematextil.services.agent.sale.AgentSaleService;
import com.sistemapos.sistematextil.util.cliente.TipoDocumento;
import com.sistemapos.sistematextil.util.crm.CrmWhatsappPhoneUtils;

import lombok.RequiredArgsConstructor;

/** Executes state-changing sales steps and renders their customer-facing template. */
@Service
@RequiredArgsConstructor
public class AgentDeterministicCommerceService {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' uuuu",
            Locale.forLanguageTag("es-PE"));
    private static final List<CrmWhatsappPaymentRequestStatus> ACTIVE_PAYMENT = List.of(
            CrmWhatsappPaymentRequestStatus.PENDING_EVIDENCE,
            CrmWhatsappPaymentRequestStatus.UNDER_REVIEW,
            CrmWhatsappPaymentRequestStatus.READY_FOR_SALE);

    private final CrmWhatsappAgentSessionRepository sessions;
    private final CrmWhatsappPaymentRequestRepository paymentRequests;
    private final AgentCartService carts;
    private final AgentCatalogQueryService catalog;
    private final AgentPaymentService payments;
    private final AgentCustomerService customers;
    private final AgentFulfillmentService fulfillment;
    private final AgentSaleService sales;
    private final AgentFilmService films;
    private final AgentFilmFinalizationService filmFinalizer;
    private final AgentToolCallAuditService audit;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public Outcome execute(Long sessionId, Long jobId, AgentConversationDecision decision) {
        AgentConversationDecision effectiveDecision = normalizeShalomAgencyRefinement(sessionId, decision);
        AgentCommercialAction action = effectiveCommercialAction(effectiveDecision);
        if ((action == AgentCommercialAction.HAS_PREVIOUS_PENDING_ORDER
                || action == AgentCommercialAction.NO_PREVIOUS_PENDING_ORDER)
                && requireSession(sessionId).getState() != CrmWhatsappAgentState.ESPERANDO_PEDIDO_ANTERIOR) {
            return Outcome.notHandled();
        }
        boolean earlyDeliveryPreference = rememberEarlyDeliveryPreference(sessionId, effectiveDecision);
        if (action == AgentCommercialAction.SELECT_PICKUP
                && requireSession(sessionId).getState() != CrmWhatsappAgentState.ESPERANDO_METODO_ENTREGA) {
            return Outcome.message("Contamos con recojo en tienda sin costo adicional y envío por Shalom con "
                    + "film protector obligatorio de S/2. Primero terminaremos de confirmar tu pedido y el pago; "
                    + "después podrás elegir una modalidad de entrega.");
        }
        if (earlyDeliveryPreference && action == AgentCommercialAction.SELECT_SHALOM) {
            return Outcome.message(earlyDeliveryPreferenceMessage());
        }
        if (earlyDeliveryPreference && action == AgentCommercialAction.NONE) {
            return Outcome.message(earlyDeliveryPreferenceMessage());
        }
        if (action == AgentCommercialAction.CHANGE_CART
                && effectiveDecision.cartAction() != null && effectiveDecision.cartAction() != AgentCartAction.NONE) {
            return Outcome.notHandled();
        }
        if (!handledByJava(action)) return Outcome.notHandled();
        String response = audit.execute(sessionId, jobId, 20_001, "flujo_" + action.name().toLowerCase(Locale.ROOT),
                write(auditRequest(action, effectiveDecision)),
                () -> write(executeInternal(sessionId, jobId, effectiveDecision, action)));
        try {
            return objectMapper.readValue(response, Outcome.class);
        } catch (Exception error) {
            throw new IllegalStateException("No se pudo recuperar el resultado determinístico", error);
        }
    }

    /** Persists semantic delivery intent without advancing the commercial state. */
    public boolean rememberEarlyDeliveryPreference(Long sessionId, AgentConversationDecision decision) {
        if (decision == null) return false;
        CrmWhatsappAgentSession session = requireSession(sessionId);
        if (!canRememberEarlyDeliveryPreference(session.getState())) return false;
        String method = normalize(decision.deliveryMethod());
        boolean shalom = decision.commercialAction() == AgentCommercialAction.SELECT_SHALOM
                || method.equals("shalom") || method.equals("envio shalom") || method.equals("shipping");
        if (!shalom) return false;
        if (session.getPreferredFulfillmentMethod() != CrmWhatsappFulfillmentMethod.SHALOM) {
            session.setPreferredFulfillmentMethod(CrmWhatsappFulfillmentMethod.SHALOM);
            sessions.save(session);
        }
        return true;
    }

    private boolean canRememberEarlyDeliveryPreference(CrmWhatsappAgentState state) {
        return state == CrmWhatsappAgentState.IDLE
                || state == CrmWhatsappAgentState.EXPLORANDO_PRODUCTOS
                || state == CrmWhatsappAgentState.ARMANDO_CARRITO
                || state == CrmWhatsappAgentState.ESPERANDO_METODO_PAGO
                || state == CrmWhatsappAgentState.ESPERANDO_COMPROBANTE
                || state == CrmWhatsappAgentState.VALIDANDO_COMPROBANTE
                || state == CrmWhatsappAgentState.ESPERANDO_DATOS_CLIENTE;
    }

    public String earlyDeliveryPreferenceMessage() {
        return "Sí realizamos envíos 💛\n"
                + "🏬 Recojo en tienda: sin costo adicional.\n"
                + "🚌 Envío por Shalom: requiere film protector obligatorio de S/2.\n"
                + "He guardado tu preferencia por envío. Primero terminaremos de confirmar tu pedido y el pago; "
                + "después te solicitaré los datos de Shalom.";
    }

    public String appendEarlyDeliveryPreference(String message) {
        if (blank(message)) return earlyDeliveryPreferenceMessage();
        return message.trim() + "\n\nTambién registré que prefieres envío. Trabajamos con Shalom y el envío "
                + "requiere film protector obligatorio de S/2. Primero confirma que el pedido está correcto "
                + "para continuar con el pago.";
    }

    AgentCommercialAction effectiveCommercialAction(AgentConversationDecision decision) {
        AgentCommercialAction action = decision.commercialAction() == null
                ? AgentCommercialAction.NONE : decision.commercialAction();
        // Defensa ante una decision parcialmente correcta: si Gemini entendio el metodo
        // pero clasifico el mensaje compuesto solo como confirmacion, ejecutamos ambos
        // pasos atomicos mediante SELECT_PAYMENT_METHOD.
        if (action == AgentCommercialAction.CONFIRM_CART && !blank(decision.paymentMethodReference())) {
            return AgentCommercialAction.SELECT_PAYMENT_METHOD;
        }
        return action;
    }

    /** A different Shalom location is another search, never a support handoff. */
    AgentConversationDecision normalizeShalomAgencyRefinement(Long sessionId,
            AgentConversationDecision decision) {
        if (decision == null) throw new IllegalArgumentException("La decision del agente es obligatoria");
        AgentCommercialAction action = decision.commercialAction() == null
                ? AgentCommercialAction.NONE : decision.commercialAction();
        String explicitAgency = !blank(decision.shalomAgency()) ? decision.shalomAgency()
                : !blank(decision.shalomDestination()) ? decision.shalomDestination() : "";
        String reference = !blank(explicitAgency) ? explicitAgency
                : decision.intent() == com.sistemapos.sistematextil.services.agent.ai.AgentDecisionIntent.SHALOM_DATA
                        ? decision.customerGoal() : "";
        if ((action != AgentCommercialAction.NONE && action != AgentCommercialAction.REQUEST_SUPPORT)
                ) return decision;
        var state = requireSession(sessionId).getState();
        boolean waitingSavedData = state == CrmWhatsappAgentState.ESPERANDO_DATOS_SHALOM;
        if(waitingSavedData&&(decision.intent()==com.sistemapos.sistematextil.services.agent.ai.AgentDecisionIntent.SHALOM_DATA
                ||decision.shalomFieldsToChange()!=null&&!decision.shalomFieldsToChange().isEmpty()
                ||!blank(explicitAgency))) {
            boolean explicitlyChangesAgency=!blank(explicitAgency)
                    && normalize(decision.customerGoal()).matches(".*\\b(cambiar|cambio|modificar|corregir|reemplazar)\\b.*");
            if(explicitlyChangesAgency)
                return decision.withShalomDataEdit(List.of(AgentShalomDataField.AGENCY));
            if(decision.intent()==com.sistemapos.sistematextil.services.agent.ai.AgentDecisionIntent.SHALOM_DATA
                    &&(decision.shalomFieldsToChange()==null||decision.shalomFieldsToChange().isEmpty())) {
                AgentConversationDecision partial=decision.withCommercialAction(AgentCommercialAction.PROVIDE_SHALOM_DATA)
                        .withRequiresHumanHelp(false);
                return blank(explicitAgency)?partial:partial.withShalomAgencySearch(explicitAgency);
            }
            List<AgentShalomDataField> fields=decision.shalomFieldsToChange();
            if((fields==null||fields.isEmpty())&&!blank(explicitAgency))fields=List.of(AgentShalomDataField.AGENCY);
            return decision.withShalomDataEdit(fields);
        }
        if(blank(reference))return decision;
        boolean mayBeRefinement = state == com.sistemapos.sistematextil.model.CrmWhatsappAgentState.ESPERANDO_CONFIRMACION_AGENCIA_SHALOM;
        return mayBeRefinement && !blank(reference)
                ? decision.withShalomAgencySearch(reference)
                : decision;
    }

    private Outcome executeInternal(Long sessionId, Long jobId, AgentConversationDecision decision,
            AgentCommercialAction action) {
        String operation = "agent-job-" + jobId + ":" + action.name().toLowerCase(Locale.ROOT);
        return switch (action) {
            case CONFIRM_CART -> confirmCart(sessionId, operation);
            case SHOW_PAYMENT_METHODS -> showPaymentMethods(sessionId, operation);
            case SELECT_PAYMENT_METHOD -> selectPaymentMethod(sessionId, decision, operation);
            case PAYMENT_TIME_REMAINING -> paymentTimeRemaining(sessionId);
            case RETRY_PAYMENT -> retryPayment(sessionId, operation);
            case EXPLAIN_IMMEDIATE_PAYMENT_POLICY -> immediatePaymentPolicy(sessionId);
            case PROVIDE_CUSTOMER_DATA -> provideCustomer(sessionId, decision, operation);
            case CONFIRM_EXISTING_CUSTOMER -> confirmExistingCustomer(sessionId, operation);
            case SELECT_PICKUP -> selectPickup(sessionId, operation);
            case SELECT_SHALOM -> selectShalom(sessionId, operation);
            case PROVIDE_SHALOM_DATA -> saveShalomData(sessionId, decision, operation);
            case EDIT_SHALOM_DATA -> editShalomData(sessionId, decision, operation);
            case CONFIRM_SHALOM_AGENCY -> confirmShalomAgency(sessionId, decision, operation);
            case REUSE_SHALOM_PROFILE -> reuseShalomProfile(sessionId, operation);
            case HAS_PREVIOUS_PENDING_ORDER -> confirmPreviousOrder(sessionId);
            case NO_PREVIOUS_PENDING_ORDER -> confirmNoPreviousOrder(sessionId);
            case ACCEPT_FILM -> acceptFilm(sessionId);
            case DECLINE_FILM -> declineFilm(sessionId);
            case SHARE_ADVISOR_CONTACT -> advisorContact();
            case SHARE_INVOICE_CONTACT -> invoiceContact();
            case SHARE_MOTORIZED_CONTACT -> motorizedContact();
            case SHARE_DELIVERY_TIMING_CONTACT -> deliveryTimingContact();
            case REFER_EXISTING_ORDER_DELIVERY -> Outcome.notHandled();
            case REQUEST_WHOLESALE_SUPPORT -> wholesaleContact();
            case CHANGE_CART -> beginCartEdit(sessionId);
            default -> Outcome.notHandled();
        };
    }

    private boolean handledByJava(AgentCommercialAction action) {
        return switch (action) {
            case CHANGE_CART, CONFIRM_CART, SHOW_PAYMENT_METHODS, SELECT_PAYMENT_METHOD, PAYMENT_TIME_REMAINING,
                    RETRY_PAYMENT, EXPLAIN_IMMEDIATE_PAYMENT_POLICY,
                    PROVIDE_CUSTOMER_DATA, CONFIRM_EXISTING_CUSTOMER, SELECT_PICKUP, SELECT_SHALOM,
                    PROVIDE_SHALOM_DATA, EDIT_SHALOM_DATA, CONFIRM_SHALOM_AGENCY, REUSE_SHALOM_PROFILE,
                    HAS_PREVIOUS_PENDING_ORDER, NO_PREVIOUS_PENDING_ORDER,
                    ACCEPT_FILM, DECLINE_FILM, SHARE_ADVISOR_CONTACT, SHARE_INVOICE_CONTACT,
                    SHARE_MOTORIZED_CONTACT,
                    SHARE_DELIVERY_TIMING_CONTACT,
                    REQUEST_WHOLESALE_SUPPORT -> true;
            default -> false;
        };
    }

    private Outcome beginCartEdit(Long sessionId) {
        payments.beginCartEdit(sessionId);
        return Outcome.executed("Tu reserva anterior fue liberada y el carrito volvió a edición. "
                + "Indícame qué producto, talla, color o cantidad deseas cambiar.");
    }

    /** Reabre el carrito confirmado antes de ejecutar una mutación concreta. */
    public void prepareCartMutation(Long sessionId) {
        CrmWhatsappAgentState state = requireSession(sessionId).getState();
        if (state == CrmWhatsappAgentState.CANCELADO
                || state == CrmWhatsappAgentState.COMPLETADO) {
            payments.beginNewPurchase(sessionId);
        } else if (state == CrmWhatsappAgentState.ESPERANDO_METODO_PAGO
                || state == CrmWhatsappAgentState.ESPERANDO_COMPROBANTE) {
            payments.beginCartEdit(sessionId);
        }
    }

    public String cartTemplate(Long sessionId, CartResult cart, boolean requestConfirmation) {
        StringBuilder text = new StringBuilder("*Resumen de tu pedido*\n\n");
        boolean preorder = false;
        boolean regular = false;
        boolean hasPromotions = cart.promotions() != null && !cart.promotions().isEmpty();
        for (CartItemView item : cart.items()) {
            text.append("• ").append(item.quantity()).append(" ").append(item.product())
                    .append(" — ").append(item.color()).append(", ").append(item.size());
            if (!hasPromotions) text.append(" — ").append(money(item.lineTotal()));
            text.append("\n");
            var stock = catalog.getStock(sessionId, item.variantId(), item.quantity());
            var product = catalog.getProduct(sessionId, stock.productId());
            if (product.preorder()) {
                preorder = true;
                text.append("  Preventa: despachos a partir de");
                if (product.preorderShippingDate() != null) {
                    text.append("l ").append(product.preorderShippingDate().format(DATE));
                } else {
                    text.append(" la fecha indicada");
                }
                text.append("\n");
            } else {
                regular = true;
            }
        }
        Map<PromotionGroupKey, PromotionGroup> groupedPromotions = groupPromotions(cart.promotions());
        if (!groupedPromotions.isEmpty()) {
            if (groupedPromotions.size() == 1) {
                PromotionGroup promotion = groupedPromotions.values().iterator().next();
                text.append("\n🎁 *");
                if (promotion.applications() > 1) text.append(promotion.applications()).append(" x ");
                text.append(promotion.name()).append("*\n");
                if (promotion.applications() > 1) {
                    text.append("Precio por combo: ").append(money(promotion.comboPrice())).append("\n");
                }
            } else {
                text.append("\n🎁 *Promociones aplicadas*\n");
                for (PromotionGroup promotion : groupedPromotions.values()) {
                    text.append("• ").append(promotion.applications()).append(" x ")
                            .append(promotion.name()).append(" — ")
                            .append(money(promotion.comboPrice())).append(" cada combo\n");
                }
            }
            text.append("Precio normal: ").append(money(cart.subtotal())).append("\n");
        }
        if (cart.discountTotal() != null && cart.discountTotal().signum() > 0) {
            text.append("Descuento: ").append(money(cart.discountTotal())).append("\n");
        }
        text.append("*Total: ").append(money(cart.total())).append("*\n\n");
        if (preorder && regular) {
            text.append("📅 Los despachos del pedido comienzan cuando los productos en preventa estén disponibles, ")
                    .append("a partir de la fecha indicada y según el volumen de pedidos.\n\n");
        } else if (preorder) {
            text.append("📅 Los despachos comienzan a partir de la fecha indicada y dependen del volumen de pedidos.\n\n");
        }
        if (requestConfirmation) {
            text.append("⚠️ Revisa bien el modelo, color y talla. Los cambios tienen una penalidad de S/10 y pueden demorar.\n\n")
                    .append("Si todo está correcto, confirma tu pedido para continuar con el pago.");
        }
        return text.toString().trim();
    }

    private Map<PromotionGroupKey, PromotionGroup> groupPromotions(List<CartPromotionView> promotions) {
        Map<PromotionGroupKey, PromotionGroup> grouped = new LinkedHashMap<>();
        if (promotions == null) return grouped;
        for (CartPromotionView promotion : promotions) {
            if (promotion == null) continue;
            PromotionGroupKey key = new PromotionGroupKey(
                    promotion.promotionId(), promotion.name(), moneyValue(promotion.comboPrice()));
            grouped.compute(key, (ignored, current) -> current == null
                    ? new PromotionGroup(promotion.name(), promotion.comboPrice(), 1)
                    : new PromotionGroup(current.name(), current.comboPrice(), current.applications() + 1));
        }
        return grouped;
    }

    private BigDecimal moneyValue(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private Outcome confirmCart(Long sessionId, String operation) {
        var session = requireSession(sessionId);
        CartResult cart;
        if (session.getState() == CrmWhatsappAgentState.ARMANDO_CARRITO) {
            cart = carts.confirm(sessionId, operation);
        } else if (session.getState() == CrmWhatsappAgentState.ESPERANDO_METODO_PAGO) {
            return Outcome.message(paymentMethodsTemplate(payments.listMethods(sessionId)));
        } else {
            return Outcome.message("Tu pedido no se puede confirmar en esta etapa.");
        }
        return Outcome.executed(cartTemplate(sessionId, cart, false) + "\n\n" + paymentMethodsTemplate(payments.listMethods(sessionId)));
    }

    private Outcome showPaymentMethods(Long sessionId, String operation) {
        var session = requireSession(sessionId);
        if (session.getState() == CrmWhatsappAgentState.ARMANDO_CARRITO) {
            CartResult cart = carts.confirm(sessionId, operation + ":confirm-cart");
            return Outcome.executed(cartTemplate(sessionId, cart, false) + "\n\n"
                    + paymentMethodsTemplate(payments.listMethods(sessionId)));
        }
        if (session.getState() == CrmWhatsappAgentState.ESPERANDO_METODO_PAGO) {
            return Outcome.message(paymentMethodsTemplate(payments.listMethods(sessionId)));
        }
        return Outcome.message("Aún no hay un pedido confirmado para pagar. Primero elige los productos, colores y tallas.");
    }

    private Map<String, Object> auditRequest(AgentCommercialAction action, AgentConversationDecision decision) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("action", action.name());
        switch (action) {
            case SELECT_PAYMENT_METHOD -> {
                request.put("paymentMethodReference", safe(decision.paymentMethodReference()));
                request.put("paymentAccountReference", safe(decision.paymentAccountReference()));
            }
            case PROVIDE_CUSTOMER_DATA -> {
                request.put("customerName", safe(decision.customerName()));
                request.put("customerPhone", safe(decision.customerPhone()));
            }
            case PROVIDE_SHALOM_DATA, EDIT_SHALOM_DATA -> {
                request.put("customerName", safe(decision.customerName()));
                request.put("customerPhone", safe(decision.customerPhone()));
                request.put("documentNumber", safe(decision.documentNumber()));
                request.put("shalomAgency", safe(decision.shalomAgency()));
                request.put("shalomDestination", safe(decision.shalomDestination()));
                request.put("shalomFieldsToChange", decision.shalomFieldsToChange());
            }
            case CONFIRM_SHALOM_AGENCY -> request.put("shalomAgency", safe(decision.shalomAgency()));
            default -> {
                // El job y la acción bastan; no incluimos texto libre que Gemini puede reformular.
            }
        }
        return request;
    }

    private Outcome selectPaymentMethod(Long sessionId, AgentConversationDecision decision, String operation) {
        var session = requireSession(sessionId);
        if (session.getState() == CrmWhatsappAgentState.ARMANDO_CARRITO) {
            carts.confirm(sessionId, operation + ":confirm-cart");
        } else if (session.getState() != CrmWhatsappAgentState.ESPERANDO_METODO_PAGO) {
            return Outcome.message(
                    "Aún no hay un pedido confirmado para pagar. Primero elige los productos, colores y tallas.");
        }
        PaymentMethodList list = payments.listMethods(sessionId);
        PaymentMethodResolution resolution = resolvePaymentMethod(list, decision.paymentMethodReference());
        if (resolution.matches().size() > 1) {
            return Outcome.message(ambiguousPaymentMethodsTemplate(resolution.matches()));
        }
        if (resolution.matches().isEmpty()) {
            return Outcome.message(paymentMethodsTemplate(list));
        }
        PaymentMethodView selected = resolution.matches().getFirst();
        String accountReference = normalize(decision.paymentAccountReference());
        var accountMatches = selected.accounts().stream()
                .filter(account -> matches(normalize(account.accountNumber()), accountReference)
                        || matches(normalize(account.accountHolder()), accountReference))
                .toList();
        Integer accountId = selected.accounts().size() == 1
                ? selected.accounts().getFirst().accountId()
                : accountMatches.size() == 1 ? accountMatches.getFirst().accountId() : null;
        var selection = payments.selectMethod(sessionId, selected.paymentMethodId(), accountId,
                operation + ":select");
        if (!"SELECTED".equals(selection.status())) {
            StringBuilder text = new StringBuilder("Elige la cuenta para pagar por *")
                    .append(selected.name()).append("*:\n");
            selection.availableAccounts().forEach(account -> text.append("• ")
                    .append(account.accountNumber()).append(" · ").append(account.accountHolder()).append("\n"));
            return Outcome.message(text.toString().trim());
        }
        PaymentRequestResult request = payments.createRequest(sessionId, operation + ":reserve");
        return Outcome.executed(paymentRequestTemplate(request));
    }

    PaymentMethodResolution resolvePaymentMethod(PaymentMethodList list, String paymentMethodReference) {
        String reference = normalize(paymentMethodReference);
        if (list == null || list.methods() == null || reference.isBlank()) {
            return new PaymentMethodResolution(List.of());
        }
        return new PaymentMethodResolution(list.methods().stream()
                .filter(item -> matches(normalize(item.name()), reference))
                .toList());
    }

    private Outcome paymentTimeRemaining(Long sessionId) {
        var request = paymentRequests
                .findFirstByAgentSession_IdAgentSessionAndStatusInOrderByCreatedAtDesc(sessionId, ACTIVE_PAYMENT)
                .orElse(null);
        if (request == null || request.getStatus() != CrmWhatsappPaymentRequestStatus.PENDING_EVIDENCE) {
            return Outcome.message("No tienes una reserva de pago pendiente en este momento.");
        }
        long seconds = Math.max(0, Duration.between(LocalDateTime.now(), request.getExpiresAt()).toSeconds());
        if (seconds == 0) return Outcome.message("⏱️ Tu reserva de pago ya venció.");
        return Outcome.message("⏱️ Te quedan aproximadamente *" + ((seconds + 59) / 60)
                + " minutos* para enviar el comprobante.");
    }

    private Outcome retryPayment(Long sessionId, String operation) {
        payments.retryExpiredPayment(sessionId, operation + ":reopen");
        PaymentRequestResult request = payments.createRequest(sessionId, operation + ":reserve");
        return Outcome.executed(paymentRequestTemplate(request));
    }

    Outcome provideCustomer(Long sessionId, AgentConversationDecision decision, String operation) {
        String phone = CrmWhatsappPhoneUtils.normalizePeruvianMobile(decision.customerPhone());
        if (phone.isBlank()) phone = conversationPhone(requireSession(sessionId));
        if (blank(decision.customerName())) {
            return phone.isBlank()
                    ? Outcome.message("Para registrar tu compra, envíame en un solo mensaje:\n☑️ Nombre completo\n☑️ Número de celular")
                    : Outcome.message("Para registrar tu compra, envíame tu nombre completo.");
        }
        if (phone.isBlank()) {
            return Outcome.message("No pude identificar un celular válido en la conversación. Envíame tu número de celular de 9 dígitos.");
        }
        customers.register(sessionId, decision.customerName(), phone,
                TipoDocumento.SIN_DOC, null, operation);
        payments.bindApprovedCustomer(sessionId);
        return Outcome.executed(deliveryQuestion(sessionId));
    }

    private String conversationPhone(CrmWhatsappAgentSession session) {
        CrmWhatsappConversation conversation = session == null ? null : session.getConversation();
        if (conversation == null) return "";
        String phone = CrmWhatsappPhoneUtils.normalizePeruvianMobile(conversation.getPhoneNumber());
        return phone.isBlank() ? CrmWhatsappPhoneUtils.normalizePeruvianMobile(conversation.getPhone()) : phone;
    }

    private Outcome confirmExistingCustomer(Long sessionId, String operation) {
        var session = requireSession(sessionId);
        if (session.getCliente() == null || session.getCliente().getIdCliente() == null) {
            return Outcome.message("Para registrar tu compra, envíame tu nombre completo y número de celular.");
        }
        customers.link(sessionId, session.getCliente().getIdCliente(), operation);
        payments.bindApprovedCustomer(sessionId);
        return Outcome.executed(deliveryQuestion(sessionId));
    }

    private Outcome selectPickup(Long sessionId, String operation) {
        var session = requireSession(sessionId);
        var branches = fulfillment.listPickupBranches(sessionId);
        Integer connectedBranch = session.getConnection() == null || session.getConnection().getSucursal() == null
                ? null : session.getConnection().getSucursal().getIdSucursal();
        var branch = branches.branches().stream().filter(item -> Objects.equals(item.branchId(), connectedBranch))
                .findFirst().orElseGet(() -> branches.branches().size() == 1 ? branches.branches().getFirst() : null);
        if (branch == null) {
            StringBuilder text = new StringBuilder("¿En qué tienda deseas recoger?\n");
            branches.branches().forEach(item -> text.append("• ").append(item.name()).append("\n"));
            return Outcome.message(text.toString().trim());
        }
        fulfillment.selectPickup(sessionId, branch.branchId(), null, null, operation);
        var sale = sales.createSalesNote(sessionId, operation + ":sale");
        return Outcome.executed(pickupConfirmation(sale.saleId()));
    }

    private String pickupConfirmation(Integer saleId) {
        return "Entendido 💛\n\n"
                + "✅ Tu pedido fue registrado correctamente.\n"
                + "🧾 Número de pedido: " + saleId + "\n"
                + "🏬 Tipo de entrega: Recojo en tienda\n"
                + "📍 Dirección: Jr. Huánuco 1705-1707, piso 7, interior 701, Gamarra, La Victoria, Lima, Perú.\n\n"
                + "Una asesora del área de envíos se comunicará contigo para corroborar tus datos.\n\n"
                + "¡Gracias por confiar en Kiments! 💛";
    }

    private Outcome selectShalom(Long sessionId, String operation) {
        var result = fulfillment.selectShalom(sessionId, operation);
        if (result.savedProfile() != null && result.savedProfile().agencyAvailable()) {
            var profile = result.savedProfile();
            return Outcome.executed("Tengo estos datos de Shalom guardados:\n"
                    + "☑️ " + profile.recipientName() + "\n☑️ DNI " + profile.documentNumber()
                    + "\n☑️ " + profile.phone() + "\n📍 " + profile.agencyName()
                    + (profile.agencyAddress() == null ? "" : "\n" + profile.agencyAddress())
                    + "\n\n¿Deseas usar estos mismos datos para este pedido?");
        }
        return Outcome.executed(shalomDataRequest());
    }

    private Outcome saveShalomData(Long sessionId, AgentConversationDecision decision, String operation) {
        String agency=firstPresent(decision.shalomAgency(),decision.shalomDestination());
        AgentFulfillmentService.ShalomDataProgress progress=fulfillment.savePartialShalomData(sessionId,
                decision.customerName(),decision.documentNumber(),decision.customerPhone(),agency);
        AgentFulfillmentService.FulfillmentResult result=progress.fulfillment();
        if(progress.missingFields().isEmpty()&&!result.agencyCandidates().isEmpty())
            return Outcome.executed(agencyCandidatesMessage(result));
        StringBuilder message=new StringBuilder();
        if(!progress.validationErrors().isEmpty())
            message.append(String.join(" ",progress.validationErrors().stream()
                    .map(value->value.endsWith(".")?value:value+".").toList())).append(" ");
        if(progress.agencyNotFound())
            message.append("No encontré una agencia Shalom segura con esa referencia. ");
        if(!progress.missingFields().isEmpty())
            message.append("Envíame únicamente: *")
                    .append(String.join(", ",progress.missingFields())).append("*.");
        return Outcome.executed(message.toString().trim());
    }

    private Outcome editShalomData(Long sessionId, AgentConversationDecision decision, String operation) {
        List<AgentShalomDataField> fields=decision.shalomFieldsToChange();
        if(fields==null||fields.isEmpty())
            return Outcome.message("Indícame si deseas cambiar el nombre, DNI, celular, agencia o todos los datos.");
        String agency=firstPresent(decision.shalomAgency(),decision.shalomDestination());
        AgentFulfillmentService.FulfillmentResult result;
        try {
            result=fulfillment.editShalomData(sessionId,fields,decision.customerName(),decision.documentNumber(),
                    decision.customerPhone(),agency,operation);
        } catch (IllegalArgumentException invalidData) {
            return Outcome.message(shalomValidationMessage(invalidData));
        }
        if(!result.agencyCandidates().isEmpty()&&!blank(result.recipientName())
                &&!blank(result.documentNumber())&&!blank(result.phone()))
            return Outcome.executed(editedShalomDataMessage(result));
        List<String> missing=new java.util.ArrayList<>();
        if(blank(result.recipientName()))missing.add("nombres y apellidos");
        if(blank(result.documentNumber()))missing.add("DNI");
        if(blank(result.phone()))missing.add("celular");
        if(result.shalomAgencyId()==null&&result.agencyCandidates().isEmpty())missing.add("agencia Shalom");
        return Outcome.executed("Conservaré los demás datos. Envíame únicamente: *"
                +String.join(", ",missing)+"*.");
    }

    private String editedShalomDataMessage(AgentFulfillmentService.FulfillmentResult result) {
        return "Así quedan tus datos para Shalom:\n"
                + "☑️ " + result.recipientName() + "\n"
                + "☑️ DNI " + result.documentNumber() + "\n"
                + "☑️ " + result.phone() + "\n\n"
                + agencyCandidatesMessage(result);
    }

    private String agencyCandidatesMessage(AgentFulfillmentService.FulfillmentResult result) {
        StringBuilder text = new StringBuilder(result.agencyCandidates().size() == 1
                ? "Encontré esta agencia de Shalom:\n\n" : "Encontré estas agencias de Shalom:\n\n");
        int index = 1;
        for (var candidate : result.agencyCandidates()) {
            if (result.agencyCandidates().size() > 1) text.append(index++).append(". ");
            text.append("*").append(candidate.name()).append("*\n");
            if (candidate.address() != null) text.append("📍 ").append(candidate.address()).append("\n");
            text.append("\n");
        }
        return text.append(result.agencyCandidates().size() == 1
                ? "¿Confirmas que esta es la agencia donde recogerás tu pedido?"
                : "¿Cuál de estas agencias deseas usar?").toString().trim();
    }

    private Outcome confirmShalomAgency(Long sessionId, AgentConversationDecision decision, String operation) {
        var confirmed = fulfillment.confirmShalomAgency(sessionId, decision.shalomAgency(), operation);
        var sale = sales.createSalesNote(sessionId, operation + ":sale");
        films.awaitPreviousOrderDecision(sessionId, sale.saleId());
        return Outcome.executed(previousOrderQuestion(confirmed, sale.saleId()));
    }

    private Outcome reuseShalomProfile(Long sessionId, String operation) {
        var confirmed = fulfillment.reuseSavedProfile(sessionId, operation);
        var sale = sales.createSalesNote(sessionId, operation + ":sale");
        films.awaitPreviousOrderDecision(sessionId, sale.saleId());
        return Outcome.executed(previousOrderQuestion(confirmed, sale.saleId()));
    }

    private Outcome confirmPreviousOrder(Long sessionId) {
        var film = films.confirmPreviousOrder(sessionId);
        return Outcome.executed(finalShalomMessage(film, true));
    }

    private Outcome confirmNoPreviousOrder(Long sessionId) {
        var film = films.confirmNoPreviousOrder(sessionId);
        if (film.accountNumber() == null) return Outcome.executed(finalShalomMessage(film, true));
        String holder = blank(film.accountHolder()) ? "" : " — " + film.accountHolder();
        return Outcome.executed("Entendido 💛\n\n📦 Corresponde el pago de S/2.00 por el film protector.\n"
                + "💳 Yape: " + film.accountNumber() + holder
                + "\nTienes 1 hora para enviar el comprobante por este chat.");
    }

    private String previousOrderQuestion(AgentFulfillmentService.FulfillmentResult result, Integer saleId) {
        return "✅ Tu pedido fue registrado correctamente.\n"
                + "🧾 Número de pedido: " + saleId + "\n"
                + "📍 Agencia: " + result.shalomAgency() + "\n\n"
                + "¿Tienes algún pedido anterior pendiente de envío para juntarlo con esta compra?";
    }

    private String finalShalomMessage(AgentFilmService.FilmResult film, boolean reviewPreviousOrders) {
        String review = reviewPreviousOrders
                ? "Una asesora del área de envíos se comunicará contigo para corroborar tus datos, revisar tus pedidos anteriores y verificar el film protector pendiente."
                : "Una asesora del área de envíos se comunicará contigo para corroborar tus datos.";
        return "Entendido 💛\n\n"
                + "🧾 Número de pedido: " + film.saleId() + "\n"
                + "🚌 Tipo de entrega: Envío por Shalom\n"
                + "📍 Agencia: " + film.shalomAgency() + "\n\n"
                + review + "\n\n¡Gracias por confiar en Kiments! 💛";
    }

    private Outcome acceptFilm(Long sessionId) {
        var result = films.accept(sessionId);
        if (result.continueWithoutFilm()) {
            var addon = result.addonId();
            return Outcome.executed(filmFinalizer.finalizeSale(addon, false,
                    "No contamos con una cuenta Yape activa para el film, por lo que continuamos tu pedido sin este adicional.").message());
        }
        String holder = blank(result.accountHolder()) ? "" : "\n👤 Titular: " + result.accountHolder();
        return Outcome.executed("📦 *Film protector para tu envío por Shalom*\n\n"
                + "💰 Monto exacto: *S/2.00*\n📱 Yape: *" + result.accountNumber() + "*" + holder
                + "\n\n⏱️ Tienes *1 hora* para enviar el comprobante por este chat. "
                + "Este pago no modifica el total de tu Nota de Venta.");
    }

    private Outcome declineFilm(Long sessionId) {
        return Outcome.executed("El film protector de S/2 es obligatorio para los envíos por Shalom. "
                + "Si necesitas ayuda con el pago, comunícate con nuestra asesora al 933918047.");
    }

    private Outcome advisorContact() {
        return Outcome.message("Claro que si, bella. Puedes comunicarte con nuestra asesora al 933918047. "
                + "Yo tambien puedo seguir ayudandote por aqui con productos, colores, tallas, stock y tu compra.");
    }

    private Outcome invoiceContact() {
        return Outcome.message("Para solicitar una factura, comunicate con nuestra asesora al 933918047. "
                + "Ella te ayudara a coordinar los datos de facturacion. "
                + "Yo tambien puedo seguir ayudandote por aqui con tus productos y tu compra.");
    }

    private Outcome motorizedContact() {
        return Outcome.message("Para coordinar el envio por motorizado, comunicate con nuestra asesora al "
                + "933918047. Ella te ayudara a revisar la disponibilidad y el costo del servicio. "
                + "Yo tambien puedo seguir ayudandote por aqui con tus productos y tu compra.");
    }

    private Outcome deliveryTimingContact() {
        return Outcome.message("Sí realizamos envíos por Shalom, hermosa 💛\n"
                + "La fecha de despacho depende del producto:\n"
                + "• Disponibilidad inmediata: despachamos aproximadamente en 1 a 4 días, según el volumen de pedidos.\n"
                + "• Preventa: el despacho comienza desde la fecha indicada para el producto.\n\n"
                + "Después del despacho, el tiempo de llegada depende de Shalom, por eso no podemos confirmar un día "
                + "exacto de entrega.");
    }

    private Outcome wholesaleContact() {
        return Outcome.message("Hermosa, soy la asistente virtual de Kiments y atiendo compras rápidas al por menor. "
                + "Para solicitar el catálogo mayorista, precios por mayor o realizar una compra para negocio, "
                + "comunícate con nuestra asesora al 933918047. 💛");
    }

    private Outcome immediatePaymentPolicy(Long sessionId) {
        CrmWhatsappAgentState state = requireSession(sessionId).getState();
        if (!allowsImmediatePaymentPolicy(state)) return Outcome.notHandled();
        boolean releasedReservation = state == CrmWhatsappAgentState.ESPERANDO_METODO_PAGO
                || state == CrmWhatsappAgentState.ESPERANDO_COMPROBANTE;
        if (releasedReservation) payments.beginCartEdit(sessionId);
        String message = "Entiendo, bella 💛 Para registrar tu pedido y separar las prendas, el pago debe realizarse "
                + "ahora, ya que no reservamos productos para otro día y el stock cambia diariamente. Si deseas pagar "
                + "más adelante, deberás realizar nuevamente tu pedido para verificar la disponibilidad.\n\n"
                + "Soy la asistente virtual de Kiments y puedo ayudarte a realizar tu compra de forma rápida y segura. "
                + "Si necesitas una atención más personalizada, puedes comunicarte con nuestra asesora al 933918047. 💛";
        return releasedReservation ? Outcome.executed(message) : Outcome.message(message);
    }

    private boolean allowsImmediatePaymentPolicy(CrmWhatsappAgentState state) {
        return state == CrmWhatsappAgentState.IDLE
                || state == CrmWhatsappAgentState.EXPLORANDO_PRODUCTOS
                || state == CrmWhatsappAgentState.ARMANDO_CARRITO
                || state == CrmWhatsappAgentState.ESPERANDO_METODO_PAGO
                || state == CrmWhatsappAgentState.ESPERANDO_COMPROBANTE;
    }

    private String mandatoryFilmMessage(AgentFulfillmentService.FulfillmentResult result, Integer saleId,
            AgentFilmService.FilmResult film) {
        String registered = "✅ Tu pedido fue registrado correctamente.\n"
                + "🧾 Número de pedido: " + saleId + "\n"
                + "📍 Agencia: " + result.shalomAgency() + "\n"
                + "Para completar la preparación del envío, queda pendiente el pago obligatorio de S/2 por el film protector.";
        if (film.accountNumber() == null) {
            return registered + "\n\nNo hay una cuenta Yape activa. Tu venta se conserva. Para recibir ayuda, "
                    + "comunícate con nuestra asesora al 933918047.";
        }
        String holder = blank(film.accountHolder()) ? "" : "\nTitular: " + film.accountHolder();
        return registered + "\n\n📦 Pago obligatorio del film protector\nMonto exacto: S/2.00\nYape: "
                + film.accountNumber() + holder + "\nTienes hasta 1 hora para enviar el comprobante por este chat.";
    }

    private String filmOffer() {
        return "📦 ¿Deseas agregar *film protector* a tu paquete de Shalom por *S/2.00*?\n\n"
                + "Este adicional protege mejor el paquete durante el traslado. Puedes aceptarlo o continuar sin film.";
    }

    private String shalomConfirmation(AgentFulfillmentService.FulfillmentResult result, Integer saleId) {
        return "✅ *Tu pedido fue registrado para envío por Shalom*\n\n📍 *Agencia:* "
                + result.shalomAgency() + (result.shalomAgencyAddress() == null ? "" : "\n" + result.shalomAgencyAddress())
                + "\n\n🧾 *Tu número de pedido es " + saleId + ".*\n\n"
                + "Si necesitas ayuda, comunícate con nuestra asesora al 933918047.\n\n"
                + "Gracias por confiar en Kiments 💛";
    }

    private String paymentMethodsTemplate(PaymentMethodList list) {
        StringBuilder text = new StringBuilder("💳 *Métodos de pago disponibles*\n");
        list.methods().forEach(method -> text.append("• ").append(method.name()).append("\n"));
        return text.append("\n¿Cuál deseas utilizar?").toString();
    }

    private String ambiguousPaymentMethodsTemplate(List<PaymentMethodView> matches) {
        StringBuilder text = new StringBuilder("Perfecto, tu pedido está confirmado 💛\n\n")
                .append("Para reservarlo, elige un solo método de pago:\n");
        matches.forEach(method -> text.append("• *").append(method.name()).append("*\n"));
        return text.append("\n¿Cuál deseas utilizar?").toString();
    }

    private String paymentRequestTemplate(PaymentRequestResult request) {
        StringBuilder text = new StringBuilder("💳 *Pago por ").append(request.paymentMethod()).append("*\n\n")
                .append("💰 Monto exacto: *").append(money(request.expectedAmount())).append("*\n");
        if (!blank(request.accountNumber())) text.append("📱 Cuenta: *").append(request.accountNumber()).append("*\n");
        if (!blank(request.accountHolder())) text.append("👤 Titular: ").append(request.accountHolder()).append("\n");
        long minutes = Math.max(1, (request.expiresInSeconds() + 59) / 60);
        text.append("\n⏱️ Tienes *").append(minutes)
                .append(minutes == 1 ? " minuto" : " minutos")
                .append("* para enviar el comprobante por este chat.");
        return text.toString();
    }

    private String deliveryQuestion(Long sessionId) {
        if (requireSession(sessionId).getPreferredFulfillmentMethod() == CrmWhatsappFulfillmentMethod.SHALOM) {
            return "Me indicaste que prefieres envío por Shalom. Este método requiere film protector obligatorio "
                    + "de S/2. ¿Confirmas que deseas recibirlo por Shalom?";
        }
        return "¿Cómo deseas recibir tu pedido?\n🏬 Recojo en tienda: sin costo adicional.\n"
                + "🚌 Envío por Shalom: requiere film protector obligatorio de S/2.\n¿Cuál opción deseas?";
    }

    private String shalomDataRequest() {
        return "Saludos 🤝🚌\nPara el envío por *SHALOM*, envíame:\n\n"
                + "☑️ Nombres y apellidos\n☑️ DNI\n☑️ Celular\n☑️ Agencia de Shalom\n\n"
                + "Puedes enviarlos juntos o uno por uno; guardaré cada dato válido y te indicaré únicamente lo que falte.";
    }

    private String shalomValidationMessage(IllegalArgumentException error) {
        String detail = error == null || blank(error.getMessage())
                ? "Revisa los datos ingresados para el envio por Shalom."
                : error.getMessage().trim() + ".";
        return "No pude continuar porque uno de los datos de envio es incorrecto. " + detail
                + " Corrige ese dato y vuelve a enviarme los datos de Shalom para continuar.";
    }

    private com.sistemapos.sistematextil.model.CrmWhatsappAgentSession requireSession(Long sessionId) {
        return sessions.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("La sesión del agente no existe"));
    }

    private boolean matches(String candidate, String reference) {
        return !reference.isBlank() && (candidate.equals(reference) || candidate.contains(reference)
                || reference.contains(candidate));
    }

    private String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }
    private String firstPresent(String... values) {
        for(String value:values)if(!blank(value))return value.trim();
        return "";
    }
    private String safe(String value) { return value == null ? "" : value.trim(); }
    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception error) {
            throw new IllegalStateException("No se pudo serializar la operación determinística", error);
        }
    }
    private String money(BigDecimal value) {
        return "S/" + (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    record PaymentMethodResolution(List<PaymentMethodView> matches) {
        PaymentMethodResolution {
            matches = matches == null ? List.of() : List.copyOf(matches);
        }
    }

    private record PromotionGroupKey(Integer promotionId, String name, BigDecimal comboPrice) {
    }

    private record PromotionGroup(String name, BigDecimal comboPrice, int applications) {
    }

    public record Outcome(boolean handled, boolean mutationExecuted, String customerMessage) {
        static Outcome notHandled() { return new Outcome(false, false, null); }
        static Outcome message(String message) { return new Outcome(true, false, message); }
        static Outcome executed(String message) { return new Outcome(true, true, message); }
    }
}
