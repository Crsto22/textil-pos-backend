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

import com.sistemapos.sistematextil.model.CrmWhatsappAgentEvent;
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
import com.sistemapos.sistematextil.services.agent.cart.AgentCartService.CartTotals;
import com.sistemapos.sistematextil.services.agent.cart.AgentCartService.ExpiredCartRefresh;
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
        if (fulfillment != null && fulfillment.isPendingAdvisorDelivery(sessionId)
                && isPendingDeliveryFollowup(decision)) {
            return Outcome.message("Gracias por la información 💛 Tu pedido ya fue registrado correctamente. "
                    + "Una asesora del área de envíos se comunicará contigo para completar y confirmar "
                    + "todos los datos de entrega.");
        }
        AgentConversationDecision effectiveDecision = normalizeShalomAgencyRefinement(sessionId, decision);
        AgentCommercialAction action = effectiveCommercialAction(effectiveDecision);
        resetExpiredPaymentForFreshShopping(sessionId, action);
        if (carts != null && action != AgentCommercialAction.CANCEL_PENDING_CART) {
            ExpiredCartRefresh expired = carts.refreshExpiredConfirmedCart(sessionId);
            boolean concreteCartMutation = (action == AgentCommercialAction.CHANGE_CART
                    || action == AgentCommercialAction.ADD_PRODUCT)
                    && effectiveDecision.cartAction() != null
                    && effectiveDecision.cartAction() != AgentCartAction.NONE;
            if (expired != null && expired.refreshed() && !concreteCartMutation
                    && isPaymentContinuation(action)) {
                return Outcome.executed(expiredCartMessage(sessionId, expired));
            }
        }
        if ((action == AgentCommercialAction.HAS_PREVIOUS_PENDING_ORDER
                || action == AgentCommercialAction.NO_PREVIOUS_PENDING_ORDER)
                && requireSession(sessionId).getState() != CrmWhatsappAgentState.ESPERANDO_PEDIDO_ANTERIOR) {
            return Outcome.notHandled();
        }
        boolean earlyDeliveryPreference = rememberEarlyDeliveryPreference(sessionId, effectiveDecision);
        if (action == AgentCommercialAction.SELECT_PICKUP
                && requireSession(sessionId).getState() != CrmWhatsappAgentState.ESPERANDO_METODO_ENTREGA) {
            return Outcome.message("Contamos con recojo en tienda, disponible aproximadamente 2 días después de la compra; si cae domingo pasa al lunes, y sin costo adicional. Esa es la primera fecha disponible y puedes coordinar una posterior. También contamos con envío por Shalom con "
                    + "film protector para embalaje obligatorio de S/2. Primero terminaremos de confirmar tu pedido y el pago; "
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

    private boolean isPendingDeliveryFollowup(AgentConversationDecision decision) {
        AgentCommercialAction action = decision == null || decision.commercialAction() == null
                ? AgentCommercialAction.NONE : decision.commercialAction();
        return switch (action) {
            case NONE, SELECT_PICKUP, SELECT_SHALOM, PROVIDE_SHALOM_DATA, EDIT_SHALOM_DATA,
                    SHOW_PENDING_SHALOM_AGENCIES, CONFIRM_SHALOM_AGENCY, REUSE_SHALOM_PROFILE,
                    HAS_PREVIOUS_PENDING_ORDER, NO_PREVIOUS_PENDING_ORDER, ACCEPT_FILM, DECLINE_FILM -> true;
            default -> false;
        };
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
                + "🏬 Recojo en tienda: disponible aproximadamente 2 días después de la compra; si cae domingo, pasa al lunes porque no atendemos los domingos. Sin costo adicional.\n"
                + "🚌 Envío por Shalom: se entrega a Shalom al día siguiente de atención. No realizamos entregas a Shalom los domingos ni feriados; en esos casos pasa al siguiente día laborable. Esa es la primera fecha disponible y puedes coordinar una posterior. Requiere film protector para embalaje obligatorio de S/2.\n"
                + "He guardado tu preferencia por envío. Primero terminaremos de confirmar tu pedido y el pago; "
                + "después te solicitaré los datos de Shalom.";
    }

    public String appendEarlyDeliveryPreference(String message) {
        if (blank(message)) return earlyDeliveryPreferenceMessage();
        return message.trim() + "\n\nTambién registré que prefieres envío. Trabajamos con Shalom y el envío "
                + "requiere film protector para embalaje obligatorio de S/2. Primero confirma que el pedido está correcto "
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
        var state = requireSession(sessionId).getState();
        if(state==CrmWhatsappAgentState.ESPERANDO_CONFIRMACION_AGENCIA_SHALOM) {
            if(action==AgentCommercialAction.CONFIRM_SHALOM_AGENCY) {
                if(blank(explicitAgency))return decision.withRequiresHumanHelp(false);
                var pendingResolution=fulfillment.resolvePendingAgencyReference(sessionId,explicitAgency);
                if("MATCH".equals(pendingResolution.status()))return decision.withRequiresHumanHelp(false);
                if("AMBIGUOUS".equals(pendingResolution.status()))
                    return decision.withCommercialAction(AgentCommercialAction.SHOW_PENDING_SHALOM_AGENCIES)
                            .withRequiresHumanHelp(false);
                var newLocation=fulfillment.searchAgencies(explicitAgency);
                if(!newLocation.candidates().isEmpty())
                    return decision.withShalomDataEdit(List.of(AgentShalomDataField.AGENCY))
                            .withRequiresHumanHelp(false);
                return decision.withRequiresHumanHelp(false);
            }
            boolean explicitCorrection=decision.shalomFieldsToChange()!=null
                    &&!decision.shalomFieldsToChange().isEmpty();
            if(explicitCorrection&&action==AgentCommercialAction.EDIT_SHALOM_DATA)
                return decision.withRequiresHumanHelp(false);
            if(blank(explicitAgency)) {
                String possibleNewAgency=decision.intent()==com.sistemapos.sistematextil.services.agent.ai.AgentDecisionIntent.SHALOM_DATA
                        ? decision.customerGoal() : "";
                if(!blank(possibleNewAgency)&&fulfillment!=null
                        &&!fulfillment.searchAgencies(possibleNewAgency).candidates().isEmpty())
                    return decision.withShalomAgencySearch(possibleNewAgency)
                            .withShalomDataEdit(List.of(AgentShalomDataField.AGENCY))
                            .withRequiresHumanHelp(false);
                return decision.withCommercialAction(AgentCommercialAction.SHOW_PENDING_SHALOM_AGENCIES)
                        .withRequiresHumanHelp(false);
            }
            var resolution=fulfillment.resolvePendingAgencyReference(sessionId,explicitAgency);
            if("MATCH".equals(resolution.status()))
                return decision.withCommercialAction(AgentCommercialAction.CONFIRM_SHALOM_AGENCY)
                        .withRequiresHumanHelp(false);
            if("AMBIGUOUS".equals(resolution.status()))
                return decision.withCommercialAction(AgentCommercialAction.SHOW_PENDING_SHALOM_AGENCIES)
                        .withRequiresHumanHelp(false);
            return decision.withShalomDataEdit(List.of(AgentShalomDataField.AGENCY))
                    .withRequiresHumanHelp(false);
        }
        if ((action != AgentCommercialAction.NONE && action != AgentCommercialAction.REQUEST_SUPPORT)) return decision;
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
        return decision;
    }

    private Outcome executeInternal(Long sessionId, Long jobId, AgentConversationDecision decision,
            AgentCommercialAction action) {
        String operation = "agent-job-" + jobId + ":" + action.name().toLowerCase(Locale.ROOT);
        return switch (action) {
            case CONFIRM_CART -> confirmCart(sessionId, operation);
            case SHOW_CURRENT_TOTAL -> showCurrentTotal(sessionId);
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
            case SHOW_PENDING_SHALOM_AGENCIES -> showPendingShalomAgencies(sessionId);
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
            case CANCEL_PENDING_CART -> cancelPendingCart(sessionId);
            default -> Outcome.notHandled();
        };
    }

    private boolean handledByJava(AgentCommercialAction action) {
        return switch (action) {
            case CHANGE_CART, CANCEL_PENDING_CART, CONFIRM_CART, SHOW_CURRENT_TOTAL,
                    SHOW_PAYMENT_METHODS, SELECT_PAYMENT_METHOD, PAYMENT_TIME_REMAINING,
                    RETRY_PAYMENT, EXPLAIN_IMMEDIATE_PAYMENT_POLICY,
                    PROVIDE_CUSTOMER_DATA, CONFIRM_EXISTING_CUSTOMER, SELECT_PICKUP, SELECT_SHALOM,
                    PROVIDE_SHALOM_DATA, EDIT_SHALOM_DATA, SHOW_PENDING_SHALOM_AGENCIES,
                    CONFIRM_SHALOM_AGENCY, REUSE_SHALOM_PROFILE,
                    HAS_PREVIOUS_PENDING_ORDER, NO_PREVIOUS_PENDING_ORDER,
                    ACCEPT_FILM, DECLINE_FILM, SHARE_ADVISOR_CONTACT, SHARE_INVOICE_CONTACT,
                    SHARE_MOTORIZED_CONTACT,
                    SHARE_DELIVERY_TIMING_CONTACT,
                    REQUEST_WHOLESALE_SUPPORT -> true;
            default -> false;
        };
    }

    private Outcome showCurrentTotal(Long sessionId) {
        CartTotals cart = carts.currentTotals(sessionId);
        if (cart.empty()) {
            return Outcome.message("Todavía no hay productos en tu pedido. Indícame qué producto deseas para calcular el total.");
        }
        CrmWhatsappAgentSession session = requireSession(sessionId);
        BigDecimal total = cart.total();
        BigDecimal shalomTotal = total.add(AgentFilmService.PRICE);
        StringBuilder message = new StringBuilder("El total actual de tu pedido es *")
                .append(money(total)).append("*.");
        if (session.getPreferredFulfillmentMethod() == CrmWhatsappFulfillmentMethod.SHALOM) {
            message.append("\n\nPara envío por Shalom, puedes pagar *")
                    .append(money(shalomTotal))
                    .append("*: ").append(money(total)).append(" del pedido + S/2.00 del film protector.");
        } else {
            message.append("\n\nSi eliges envío por Shalom, serían *")
                    .append(money(shalomTotal))
                    .append("*, incluyendo S/2.00 del film protector.");
        }
        message.append(" El costo del traslado no está incluido porque lo determina Shalom.");
        return Outcome.message(message.toString());
    }

    private Outcome beginCartEdit(Long sessionId) {
        payments.beginCartEdit(sessionId);
        return Outcome.executed("Tu reserva anterior fue liberada y el carrito volvió a edición. "
                + "Indícame qué producto, talla, color o cantidad deseas cambiar.");
    }

    private Outcome cancelPendingCart(Long sessionId) {
        carts.cancelPendingCart(sessionId);
        return Outcome.executed("Entendido 💛 Cancelé el pedido que estaba pendiente de pago. "
                + "No se realizó ningún cobro ni se reservó stock. Puedes iniciar otra compra cuando desees.");
    }

    private String expiredCartMessage(Long sessionId, ExpiredCartRefresh refresh) {
        CartResult cart = refresh.cart();
        if (cart == null || cart.items() == null || cart.items().isEmpty()) {
            return "Pasó más de 1 hora desde que confirmaste el pedido sin completar el pago, por eso el "
                    + "carrito pendiente fue eliminado. Indícame qué producto deseas para ayudarte a preparar "
                    + "un pedido nuevo. 💛";
        }
        StringBuilder text = new StringBuilder("Pasó más de 1 hora desde que confirmaste el pedido, hermosa 💛 "
                + "Volví a verificar el stock, los precios y las promociones antes de permitir el pago.");
        if (refresh.removedItems() != null && !refresh.removedItems().isEmpty()) {
            text.append("\n\nEstas opciones ya no están disponibles y fueron retiradas del carrito:");
            refresh.removedItems().forEach(item -> text.append("\n• ").append(item.product())
                    .append(" — ").append(item.color()).append(", ").append(item.size()));
        }
        return text.append("\n\n").append(cartTemplate(sessionId, cart, true)).toString();
    }

    private void resetExpiredPaymentForFreshShopping(Long sessionId, AgentCommercialAction action) {
        if (payments == null || sessions == null || !isFreshShoppingAction(action)) return;
        CrmWhatsappAgentSession session = requireSession(sessionId);
        if (session.getState() == CrmWhatsappAgentState.CANCELADO
                && session.getLastEvent() == CrmWhatsappAgentEvent.SOLICITUD_PAGO_EXPIRADA) {
            payments.beginNewPurchase(sessionId);
        }
    }

    private boolean isFreshShoppingAction(AgentCommercialAction action) {
        return switch (action) {
            case SHOW_PRODUCTS, SHOW_PRODUCT, SHOW_PRODUCT_AVAILABILITY,
                    SHOW_PRODUCT_AVAILABILITY_FOR_DELIVERY, SHOW_PRODUCT_IMAGES,
                    SHOW_PRODUCT_COLOR_GALLERY, SHOW_SIZE_GUIDE, RECOMMEND_SIZE,
                    SHOW_PROMOTIONS, ADD_PRODUCT, CHANGE_CART -> true;
            default -> false;
        };
    }

    private boolean isPaymentContinuation(AgentCommercialAction action) {
        return switch (action) {
            case CONFIRM_CART, SHOW_PAYMENT_METHODS, SELECT_PAYMENT_METHOD,
                    PAYMENT_TIME_REMAINING, RETRY_PAYMENT,
                    EXPLAIN_IMMEDIATE_PAYMENT_POLICY -> true;
            default -> false;
        };
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
        StringBuilder text = new StringBuilder("🛍️ *Tu pedido*\n\n");
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
                text.append("  📅 Preventa");
                if (product.preorderShippingDate() != null) {
                    text.append(": ").append(product.preorderShippingDate().format(DATE));
                } else {
                    text.append(": fecha indicada en el catálogo");
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
            text.append("📦 *Entrega*\n")
                    .append("🏬 Recojo: lo disponible, aproximadamente 2 días después de la compra; la preventa, 2 días después de su fecha. Si cae domingo, pasa al lunes. Sin costo.\n")
                    .append("🚌 Shalom: entregamos lo disponible al siguiente día de atención y la preventa desde su fecha. Domingos y feriados pasan al siguiente día laborable. Film obligatorio: S/2.\n\n")
                    .append("Puedes recibir primero lo disponible o esperar la preventa y coordinar todo junto.\n\n");
        } else if (preorder) {
            text.append("📦 *Entrega desde la fecha de preventa*\n")
                    .append("🏬 Recojo: aproximadamente 2 días después. Si cae domingo, pasa al lunes. Sin costo.\n")
                    .append("🚌 Shalom: entregamos al siguiente día de atención. Domingos y feriados pasan al siguiente día laborable. Film obligatorio: S/2.\n\n");
        } else {
            text.append("📦 *Entrega*\n")
                    .append("🏬 Recojo: aproximadamente 2 días después de la compra. Si cae domingo, pasa al lunes. Sin costo.\n")
                    .append("🚌 Shalom: entregamos al siguiente día de atención. Domingos y feriados pasan al siguiente día laborable. Film obligatorio: S/2.\n\n");
        }
        text.append("Puedes coordinar una fecha posterior con el área de envíos.\n\n");
        if (requestConfirmation) {
            text.append("⚠️ Revisa modelo, color y talla. Los cambios tienen una penalidad de S/10 por prenda.\n\n")
                    .append("¿Confirmas tu pedido?");
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
        if (awaitingExpiredCartReconfirmation(session)) {
            return expiredCartReconfirmation(sessionId, operation);
        }
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
        if (awaitingExpiredCartReconfirmation(session)) {
            return expiredCartReconfirmation(sessionId, operation);
        }
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

    private boolean awaitingExpiredCartReconfirmation(CrmWhatsappAgentSession session) {
        return session != null
                && session.getState() == CrmWhatsappAgentState.ARMANDO_CARRITO
                && session.getLastEvent() == com.sistemapos.sistematextil.model.CrmWhatsappAgentEvent.CARRITO_REVALIDADO_POR_VENCIMIENTO;
    }

    private Outcome expiredCartReconfirmation(Long sessionId, String operation) {
        CartResult refreshed = carts.recalculate(sessionId, operation + ":expired-cart-review");
        if (refreshed.items().isEmpty()) {
            return Outcome.executed("El carrito que superó 1 hora ya no tiene productos disponibles. "
                    + "Indícame qué producto deseas para preparar un pedido nuevo. 💛");
        }
        return Outcome.executed("Tu carrito superó 1 hora y fue actualizado con la disponibilidad, los precios "
                + "y las promociones vigentes. Antes de elegir el método de pago, confirma nuevamente este resumen:\n\n"
                + cartTemplate(sessionId, refreshed, true));
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
                    ? Outcome.message("Para registrar tu compra, envíame en un solo mensaje:\n☑️ Tu nombre\n☑️ Tu número de celular")
                    : Outcome.message("Para registrar tu compra, envíame tu nombre, por favor.");
        }
        if (phone.isBlank()) {
            return Outcome.message("No pude identificar un celular válido en la conversación. Envíame tu número de celular de 9 dígitos.");
        }
        customers.register(sessionId, decision.customerName(), phone,
                TipoDocumento.SIN_DOC, null, operation);
        payments.bindApprovedCustomer(sessionId);
        if (payments.hasCombinedFilmPayment(sessionId)) {
            fulfillment.selectShalom(sessionId, operation + ":combined-shalom");
            return Outcome.executed(combinedFilmShalomDataRequest());
        }
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
            return Outcome.message("Para registrar tu compra, envíame tu nombre y número de celular.");
        }
        customers.link(sessionId, session.getCliente().getIdCliente(), operation);
        payments.bindApprovedCustomer(sessionId);
        if (payments.hasCombinedFilmPayment(sessionId)) {
            fulfillment.selectShalom(sessionId, operation + ":combined-shalom");
            return Outcome.executed(combinedFilmShalomDataRequest());
        }
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
                + "Tu pedido estará disponible para recojo aproximadamente 2 días después de la compra; si la fecha cae domingo, pasa al lunes porque no atendemos los domingos. Si incluye preventa, ese plazo se cuenta desde la fecha indicada para la prenda en preventa. Si también incluye productos disponibles, puedes coordinar recogerlos primero o esperar para recoger todo junto. La fecha informada es la primera disponible y puedes coordinar una posterior.\n"
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
        List<String> missing=new java.util.ArrayList<>();
        if(blank(result.recipientName()))missing.add("nombres y apellidos");
        if(blank(result.documentNumber()))missing.add("DNI");
        if(blank(result.phone()))missing.add("celular");
        if(result.shalomAgencyId()==null)missing.add("agencia de Shalom");
        return missing.size()==4?Outcome.executed(shalomDataRequest())
                :Outcome.executed("Para continuar con el envío por Shalom, envíame únicamente: *"
                        +String.join(", ",missing)+"*.");
    }

    private Outcome saveShalomData(Long sessionId, AgentConversationDecision decision, String operation) {
        String agency=firstPresent(decision.shalomAgency(),decision.shalomDestination());
        AgentFulfillmentService.ShalomDataProgress progress=fulfillment.savePartialShalomData(sessionId,
                decision.customerName(),decision.documentNumber(),decision.customerPhone(),agency);
        AgentFulfillmentService.FulfillmentResult result=progress.fulfillment();
        ensureCombinedCustomerLinked(sessionId,result,operation+":customer");
        if(progress.agencyNotFound()&&progress.validationErrors().isEmpty()
                &&progress.missingFields().equals(List.of("agencia de Shalom"))){
            fulfillment.handoffMissingShalomAgency(sessionId,agency);
            var sale=sales.createSalesNote(sessionId,operation+":sale:agency-not-found");
            return Outcome.executed(missingShalomAgencyConfirmation(sale.saleId()));
        }
        if(progress.missingFields().isEmpty()&&!result.agencyCandidates().isEmpty()
                &&CrmWhatsappAgentState.ESPERANDO_DATOS_SHALOM.name().equals(result.agentState())
                &&payments!=null&&payments.hasCombinedFilmPayment(sessionId))
            result=fulfillment.completeCombinedShalomData(sessionId);
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

    private String missingShalomAgencyConfirmation(Integer saleId) {
        return "Entendido 💛\n\n"
                + "✅ Tu pedido fue registrado correctamente.\n"
                + "🧾 Número de pedido: " + saleId + "\n"
                + "🚌 Tipo de entrega: Envío por Shalom\n"
                + "📍 Agencia: Pendiente de localizar y confirmar\n\n"
                + "No encontramos automáticamente una agencia de Shalom para la ubicación indicada. "
                + "Una asesora del área de envíos se comunicará contigo para ayudarte a encontrar la agencia "
                + "adecuada y completar la coordinación del envío.\n\n"
                + "¡Gracias por confiar en Kiments! 💛";
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

    private Outcome showPendingShalomAgencies(Long sessionId) {
        var pending=fulfillment.pendingAgencyConfirmation(sessionId);
        if(pending.agencyCandidates().isEmpty())
            return Outcome.message("La agencia anterior ya no está disponible. Envíame otra ciudad, dirección o "
                    + "referencia de Shalom; conservaré tu nombre, DNI y celular.");
        return Outcome.message(agencyCandidatesMessage(pending));
    }

    private Outcome confirmShalomAgency(Long sessionId, AgentConversationDecision decision, String operation) {
        ensureCombinedCustomerLinked(sessionId,fulfillment.pendingAgencyConfirmation(sessionId),
                operation+":customer");
        AgentFulfillmentService.FulfillmentResult confirmed;
        try {
            confirmed=fulfillment.confirmShalomAgency(sessionId,decision.shalomAgency(),operation);
        } catch(IllegalArgumentException ambiguousReference) {
            return showPendingShalomAgencies(sessionId);
        }
        var sale = sales.createSalesNote(sessionId, operation + ":sale");
        if (payments.hasCombinedFilmPayment(sessionId)) {
            var film = films.confirmCombinedPayment(sessionId, sale.saleId());
            return Outcome.executed(finalCombinedShalomMessage(film));
        }
        films.awaitPreviousOrderDecision(sessionId, sale.saleId());
        return Outcome.executed(previousOrderQuestion(confirmed, sale.saleId()));
    }

    private void ensureCombinedCustomerLinked(Long sessionId,
            AgentFulfillmentService.FulfillmentResult result,String operation) {
        if(payments==null||!payments.hasCombinedFilmPayment(sessionId))return;
        if(result==null||blank(result.recipientName())||blank(result.documentNumber())||blank(result.phone()))return;
        customers.registerFromShalom(sessionId,result.recipientName(),result.phone(),TipoDocumento.DNI,
                result.documentNumber(),operation);
        payments.bindApprovedCustomer(sessionId);
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
        return Outcome.executed("Entendido 💛\n\n📦 Corresponde el pago de S/2.00 por el film protector para embalaje.\n"
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
                ? "Una asesora del área de envíos se comunicará contigo para corroborar tus datos, revisar tus pedidos anteriores y verificar el film protector para embalaje pendiente."
                : "Una asesora del área de envíos se comunicará contigo para corroborar tus datos.";
        return "Entendido 💛\n\n"
                + "🧾 Número de pedido: " + film.saleId() + "\n"
                + "🚌 Tipo de entrega: Envío por Shalom\n"
                + "📍 Agencia: " + film.shalomAgency() + "\n\n"
                + review + "\n\n¡Gracias por confiar en Kiments! 💛";
    }

    private String finalCombinedShalomMessage(AgentFilmService.FilmResult film) {
        return "✅ Tu pedido fue registrado correctamente.\n"
                + "🧾 Número de pedido: " + film.saleId() + "\n"
                + "🚌 Tipo de entrega: Envío por Shalom\n"
                + "📍 Agencia: " + film.shalomAgency() + "\n"
                + "📦 Film protector para embalaje: Pagado\n\n"
                + "Una asesora del área de envíos se comunicará contigo para corroborar tus datos.\n\n"
                + "¡Gracias por confiar en Kiments! 💛";
    }

    private Outcome acceptFilm(Long sessionId) {
        var result = films.accept(sessionId);
        if (result.continueWithoutFilm()) {
            var addon = result.addonId();
            return Outcome.executed(filmFinalizer.finalizeSale(addon, false,
                    "No contamos con una cuenta Yape activa para el film para embalaje, por lo que continuamos tu pedido sin este adicional.").message());
        }
        String holder = blank(result.accountHolder()) ? "" : "\n👤 Titular: " + result.accountHolder();
        return Outcome.executed("📦 *Film protector para embalaje de tu envío por Shalom*\n\n"
                + "💰 Monto exacto: *S/2.00*\n📱 Yape: *" + result.accountNumber() + "*" + holder
                + "\n\n⏱️ Tienes *1 hora* para enviar el comprobante por este chat. "
                + "Este pago no modifica el total de tu Nota de Venta.");
    }

    private Outcome declineFilm(Long sessionId) {
        return Outcome.executed("El film protector para embalaje de S/2 es obligatorio para los envíos por Shalom. "
                + "Si necesitas ayuda con el pago, comunícate con nuestra asesora al 936310946.");
    }

    private Outcome advisorContact() {
        return Outcome.message("Claro que si, bella. Puedes comunicarte con nuestra asesora al 936310946. "
                + "Yo tambien puedo seguir ayudandote por aqui con productos, colores, tallas, stock y tu compra.");
    }

    private Outcome invoiceContact() {
        return Outcome.message("Para solicitar una factura, comunicate con nuestra asesora al 936310946. "
                + "Ella te ayudara a coordinar los datos de facturacion. "
                + "Yo tambien puedo seguir ayudandote por aqui con tus productos y tu compra.");
    }

    private Outcome motorizedContact() {
        return Outcome.message("Para coordinar el envio por motorizado, comunicate con nuestra asesora al "
                + "936310946. Ella te ayudara a revisar la disponibilidad y el costo del servicio. "
                + "Yo tambien puedo seguir ayudandote por aqui con tus productos y tu compra.");
    }

    private Outcome deliveryTimingContact() {
        return Outcome.message("Sí realizamos envíos por Shalom, hermosa 💛\n"
                + "La fecha de despacho depende del producto:\n"
                + "• Disponibilidad inmediata: Kiments entrega el pedido a Shalom al día siguiente de atención.\n"
                + "• Preventa: se espera la fecha indicada y desde ella se aplica la misma regla.\n\n"
                + "No realizamos despachos el mismo día. Para recojo en tienda, el pedido está listo aproximadamente "
                + "2 días después de la compra; si la fecha cae domingo, pasa al lunes porque no atendemos los domingos. "
                + "No realizamos entregas a Shalom los domingos ni feriados; en esos casos pasa al siguiente día laborable. "
                + "En preventa, este plazo se cuenta desde la fecha indicada. Si el pedido combina un producto disponible "
                + "y uno en preventa, tú eliges entre recibir o recoger primero el disponible, o esperar y coordinar todo "
                + "junto en un solo paquete.\n\n"
                + "Los plazos mencionados son la primera fecha disponible; si prefieres una fecha posterior, podrás "
                + "coordinarla con el área de envíos.\n\n"
                + "Después del despacho, el tiempo de llegada depende de Shalom, por eso no podemos confirmar un día "
                + "exacto de entrega.");
    }

    private Outcome wholesaleContact() {
        return Outcome.message("Hermosa, soy la asistente virtual de Kiments y atiendo compras rápidas al por menor. "
                + "Para solicitar el catálogo mayorista, precios por mayor o realizar una compra para negocio, "
                + "comunícate con nuestra asesora al 936310946. 💛");
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
                + "Si necesitas una atención más personalizada, puedes comunicarte con nuestra asesora al 936310946. 💛";
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
                + "Para completar la preparación del envío, queda pendiente el pago obligatorio de S/2 por el film protector para embalaje.";
        if (film.accountNumber() == null) {
            return registered + "\n\nNo hay una cuenta Yape activa. Tu venta se conserva. Para recibir ayuda, "
                    + "comunícate con nuestra asesora al 936310946.";
        }
        String holder = blank(film.accountHolder()) ? "" : "\nTitular: " + film.accountHolder();
        return registered + "\n\n📦 Pago obligatorio del film protector para embalaje\nMonto exacto: S/2.00\nYape: "
                + film.accountNumber() + holder + "\nTienes hasta 1 hora para enviar el comprobante por este chat.";
    }

    private String filmOffer() {
        return "📦 ¿Deseas agregar *film protector para embalaje* a tu paquete de Shalom por *S/2.00*?\n\n"
                + "Este adicional protege mejor el paquete durante el traslado. Puedes aceptarlo o continuar sin film para embalaje.";
    }

    private String shalomConfirmation(AgentFulfillmentService.FulfillmentResult result, Integer saleId) {
        return "✅ *Tu pedido fue registrado para envío por Shalom*\n\n📍 *Agencia:* "
                + result.shalomAgency() + (result.shalomAgencyAddress() == null ? "" : "\n" + result.shalomAgencyAddress())
                + "\n\n🧾 *Tu número de pedido es " + saleId + ".*\n\n"
                + "Si necesitas ayuda, comunícate con nuestra asesora al 936310946.\n\n"
                + "Gracias por confiar en Kiments 💛";
    }

    private String paymentMethodsTemplate(PaymentMethodList list) {
        StringBuilder text = new StringBuilder("✅ Pedido confirmado.\n\nElige tu método de pago:\n");
        list.methods().forEach(method -> text.append("• ").append(method.name()).append("\n"));
        return text.toString().trim();
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
        text.append("\nSi deseas envío por Shalom, puedes realizar un solo pago de *")
                .append(money(request.expectedAmount().add(AgentFilmService.PRICE)))
                .append("*, que incluye S/2 por el film protector para embalaje.\n");
        long minutes = Math.max(1, (request.expiresInSeconds() + 59) / 60);
        text.append("\n✅ Tus productos quedaron reservados durante *").append(minutes)
                .append(minutes == 1 ? " minuto" : " minutos")
                .append("*.\n\nEnvía el comprobante por este chat antes de que termine el plazo para continuar con tu pedido.");
        return text.toString();
    }

    private String deliveryQuestion(Long sessionId) {
        if (requireSession(sessionId).getPreferredFulfillmentMethod() == CrmWhatsappFulfillmentMethod.SHALOM) {
            return "Me indicaste que prefieres envío por Shalom. Este método requiere film protector para embalaje obligatorio "
                    + "de S/2. ¿Confirmas que deseas recibirlo por Shalom?";
        }
        return "¿Cómo deseas recibir tu pedido?\n🏬 Recojo en tienda: disponible aproximadamente 2 días después de la compra; si cae domingo, pasa al lunes. Sin costo adicional.\n"
                + "🚌 Envío por Shalom: se entrega a Shalom al día siguiente de atención. No realizamos entregas a Shalom los domingos ni feriados; en esos casos pasa al siguiente día laborable. Requiere film protector para embalaje obligatorio de S/2.\n¿Cuál opción deseas?";
    }

    private String shalomDataRequest() {
        return "Saludos 🤝🚌\nPara el envío por *SHALOM*, envíame:\n\n"
                + "☑️ Nombres y apellidos\n☑️ DNI\n☑️ Celular\n☑️ Agencia de Shalom\n\n"
                + "Puedes enviarlos juntos o uno por uno; guardaré cada dato válido y te indicaré únicamente lo que falte.";
    }

    private String combinedFilmShalomDataRequest() {
        return "✅ El pago de las prendas y el film protector para embalaje fue confirmado.\n"
                + "📦 Film protector para embalaje: S/2.00 — Pagado\n"
                + "🚌 Tipo de entrega: Envío por Shalom\n\n"
                + shalomDataRequest();
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
