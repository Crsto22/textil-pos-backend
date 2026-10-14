package com.sistemapos.sistematextil.services.agent.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.List;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.sistemapos.sistematextil.model.CrmWhatsappAgentSession;
import com.sistemapos.sistematextil.model.CrmWhatsappAgentState;
import com.sistemapos.sistematextil.model.CrmWhatsappConversation;
import com.sistemapos.sistematextil.model.CrmWhatsappFulfillmentMethod;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAgentSessionRepository;
import com.sistemapos.sistematextil.services.agent.ai.AgentCartAction;
import com.sistemapos.sistematextil.services.agent.ai.AgentCatalogAvailability;
import com.sistemapos.sistematextil.services.agent.ai.AgentCommercialAction;
import com.sistemapos.sistematextil.services.agent.ai.AgentConversationDecision;
import com.sistemapos.sistematextil.services.agent.ai.AgentDecisionIntent;
import com.sistemapos.sistematextil.services.agent.ai.AgentShalomDataField;
import com.sistemapos.sistematextil.services.agent.payment.AgentPaymentService.PaymentMethodList;
import com.sistemapos.sistematextil.services.agent.payment.AgentPaymentService.PaymentMethodView;
import com.sistemapos.sistematextil.services.agent.payment.AgentPaymentService;
import com.sistemapos.sistematextil.services.agent.customer.AgentCustomerService;
import com.sistemapos.sistematextil.services.agent.fulfillment.AgentFulfillmentService;
import com.sistemapos.sistematextil.services.agent.cart.AgentCartService.CartPromotionView;
import com.sistemapos.sistematextil.services.agent.cart.AgentCartService.CartResult;
import com.sistemapos.sistematextil.services.agent.cart.AgentCartService.CartItemView;
import com.sistemapos.sistematextil.services.agent.catalog.AgentCatalogQueryService;
import com.sistemapos.sistematextil.services.agent.catalog.AgentCatalogQueryService.ProductDetail;
import com.sistemapos.sistematextil.services.agent.catalog.AgentCatalogQueryService.StockResult;
import java.math.BigDecimal;
import com.sistemapos.sistematextil.util.cliente.TipoDocumento;

class AgentDeterministicCommerceServiceTest {

    @Test
    void remembersEarlyShippingPreferenceWithoutExecutingFulfillmentOrEscalating() {
        CrmWhatsappAgentSessionRepository sessions=mock(CrmWhatsappAgentSessionRepository.class);
        CrmWhatsappAgentSession session=new CrmWhatsappAgentSession();session.setState(CrmWhatsappAgentState.ARMANDO_CARRITO);
        when(sessions.findById(41L)).thenReturn(Optional.of(session));
        AgentDeterministicCommerceService service=new AgentDeterministicCommerceService(
                sessions,null,null,null,null,null,null,null,null,null,null);
        AgentConversationDecision decision=new AgentConversationDecision(
                AgentDecisionIntent.CART_CHANGE,"prefiero envio","BELEN","HUESO","XS",1,"",
                AgentCartAction.NONE,AgentCommercialAction.SELECT_SHALOM,"","","","",
                "SHALOM","","","",AgentCatalogAvailability.ALL,100,false);

        var result=service.execute(41L,181L,decision);

        assertTrue(result.handled());assertFalse(result.mutationExecuted());
        assertEquals(CrmWhatsappFulfillmentMethod.SHALOM,session.getPreferredFulfillmentMethod());
        assertTrue(result.customerMessage().contains("He guardado tu preferencia"));
        assertFalse(result.customerMessage().contains("asesora"));
        verify(sessions).save(session);
    }

    @Test
    void doesNotAppendEarlyShippingPreferenceAfterShalomWasAlreadySelected() {
        CrmWhatsappAgentSessionRepository sessions=mock(CrmWhatsappAgentSessionRepository.class);
        CrmWhatsappAgentSession session=new CrmWhatsappAgentSession();
        session.setState(CrmWhatsappAgentState.ESPERANDO_CONFIRMACION_AGENCIA_SHALOM);
        when(sessions.findById(41L)).thenReturn(Optional.of(session));
        AgentDeterministicCommerceService service=new AgentDeterministicCommerceService(
                sessions,null,null,null,null,null,null,null,null,null,null);
        AgentConversationDecision decision=new AgentConversationDecision(
                AgentDecisionIntent.SHALOM_DATA,"confirmar agencia","","","",0,"",
                AgentCartAction.NONE,AgentCommercialAction.CONFIRM_SHALOM_AGENCY,"","","","",
                "SHALOM","","Agencia Proceres","",AgentCatalogAvailability.ALL,100,false);

        assertFalse(service.rememberEarlyDeliveryPreference(41L,decision));
        assertEquals(null,session.getPreferredFulfillmentMethod());
        verify(sessions,never()).save(session);
    }

    @Test
    void groupsRepeatedPromotionsAndShowsTheTotalDiscountOnlyOnce() {
        AgentDeterministicCommerceService service = serviceWithNoDependencies();
        List<CartPromotionView> promotions = java.util.stream.IntStream.range(0, 10)
                .mapToObj(index -> new CartPromotionView(29, "combo 29", index + 1,
                        BigDecimal.valueOf(130), BigDecimal.valueOf(125), BigDecimal.valueOf(5)))
                .toList();
        CartResult cart = new CartResult(1L, "ACTIVE", "PEN", BigDecimal.valueOf(1300),
                BigDecimal.valueOf(50), BigDecimal.valueOf(1250), List.of(), promotions);

        String message = service.cartTemplate(41L, cart, true);

        assertEquals(1, occurrences(message, "combo 29"));
        assertTrue(message.contains("*10 x combo 29*"));
        assertTrue(message.contains("Precio normal: S/1300.00"));
        assertTrue(message.contains("Descuento: S/50.00"));
        assertTrue(message.contains("Despacho del pedido: aproximadamente en 1 a 3 días"));
        assertFalse(message.contains("tiempo de entrega"));
        assertFalse(message.contains("logística de Shalom"));
        assertTrue(message.contains("penalidad de S/10"));
        assertFalse(message.contains("🛍️"));
    }

    @Test
    void explainsMixedPreorderShippingWithoutPromisingAnExactDispatchDate() {
        AgentCatalogQueryService catalog = mock(AgentCatalogQueryService.class);
        AgentDeterministicCommerceService service = new AgentDeterministicCommerceService(
                null, null, null, catalog, null, null, null, null, null, null, null);
        LocalDate preorderDate = LocalDate.of(2026, 10, 16);
        when(catalog.getStock(41L, 101, 1)).thenReturn(
                new StockResult(101, 10, "LIA RAYAS", "Negro", "L", 7, 1, true, 1, true));
        when(catalog.getStock(41L, 201, 1)).thenReturn(
                new StockResult(201, 20, "MELINA", "TOPO", "L", 7, 1, true, 1, true));
        when(catalog.getProduct(41L, 10)).thenReturn(new ProductDetail(
                10, "LIA RAYAS", "", "CONJUNTOS", false, null, null, null,
                List.of("Negro"), List.of("L"), 1, 75.0, 1));
        when(catalog.getProduct(41L, 20)).thenReturn(new ProductDetail(
                20, "MELINA", "", "CONJUNTOS", true, preorderDate, null, null,
                List.of("TOPO"), List.of("L"), 1, 79.0, 1));
        CartResult cart = new CartResult(1L, "ACTIVE", "PEN", BigDecimal.valueOf(154),
                BigDecimal.ZERO, BigDecimal.valueOf(154), List.of(
                        new CartItemView(1L, 101, "LIA RAYAS", "Negro", "L", 1,
                                BigDecimal.valueOf(75), BigDecimal.ZERO, BigDecimal.valueOf(75)),
                        new CartItemView(2L, 201, "MELINA", "TOPO", "L", 1,
                                BigDecimal.valueOf(79), BigDecimal.ZERO, BigDecimal.valueOf(79))), List.of());

        String message = service.cartTemplate(41L, cart, true);

        assertTrue(message.contains("Preventa: despachos a partir del 16 de octubre de 2026"));
        assertTrue(message.contains("La fecha de preventa es referencial"));
        assertTrue(message.contains("depende del volumen de pedidos"));
        assertTrue(message.contains("coordinar el envío anticipado"));
        assertFalse(message.contains("serán despachados desde"));
    }

    @Test
    void treatsConfirmationWithOnePaymentMethodAsOneCommercialAction() {
        AgentDeterministicCommerceService service = serviceWithNoDependencies();
        AgentConversationDecision decision = decision(
                AgentCommercialAction.CONFIRM_CART, "Yape");

        assertSame(AgentCommercialAction.SELECT_PAYMENT_METHOD,
                service.effectiveCommercialAction(decision));
    }

    @Test
    void requiresExactlyOneActivePaymentMethodBeforeCreatingAReservation() {
        AgentDeterministicCommerceService service = serviceWithNoDependencies();
        PaymentMethodList methods = new PaymentMethodList(1, List.of(
                method(1, "YAPE"), method(2, "PLIN"), method(3, "TRANSFERENCIA")), "OK");

        assertEquals(List.of("YAPE"), service.resolvePaymentMethod(methods, "pago con yape").matches()
                .stream().map(PaymentMethodView::name).toList());
        assertEquals(List.of("YAPE", "PLIN"), service.resolvePaymentMethod(methods, "Yape o Plin").matches()
                .stream().map(PaymentMethodView::name).toList());
        assertEquals(List.of("TRANSFERENCIA"), service.resolvePaymentMethod(methods,
                "conforme, pagare mediante transferencia bancaria").matches()
                .stream().map(PaymentMethodView::name).toList());
    }

    @Test
    void convertsARejectedCandidateAndNewLocationIntoAnotherSearch() {
        CrmWhatsappAgentSessionRepository sessions = mock(CrmWhatsappAgentSessionRepository.class);
        CrmWhatsappAgentSession session = new CrmWhatsappAgentSession();
        session.setState(CrmWhatsappAgentState.ESPERANDO_CONFIRMACION_AGENCIA_SHALOM);
        when(sessions.findById(41L)).thenReturn(Optional.of(session));
        AgentDeterministicCommerceService service = new AgentDeterministicCommerceService(
                sessions, null, null, null, null, null, null, null, null, null, null);
        AgentConversationDecision mistaken = new AgentConversationDecision(
                AgentDecisionIntent.SHALOM_DATA, "San Juan de Lurigancho", "", "", "", 0, "",
                AgentCartAction.NONE, AgentCommercialAction.REQUEST_SUPPORT, "", "", "", "",
                "", "", "", "", AgentCatalogAvailability.ALL, 90, true);

        AgentConversationDecision normalized = service.normalizeShalomAgencyRefinement(41L, mistaken);

        assertEquals(AgentCommercialAction.PROVIDE_SHALOM_DATA, normalized.commercialAction());
        assertEquals("San Juan de Lurigancho", normalized.shalomAgency());
        assertFalse(normalized.requiresHumanHelp());
    }

    @Test
    void convertsAnAgencyChangeOnSavedDataIntoAnEditWithoutHumanHandoff() {
        CrmWhatsappAgentSessionRepository sessions = mock(CrmWhatsappAgentSessionRepository.class);
        CrmWhatsappAgentSession session = new CrmWhatsappAgentSession();
        session.setState(CrmWhatsappAgentState.ESPERANDO_DATOS_SHALOM);
        when(sessions.findById(41L)).thenReturn(Optional.of(session));
        AgentDeterministicCommerceService service = new AgentDeterministicCommerceService(
                sessions, null, null, null, null, null, null, null, null, null, null);
        AgentConversationDecision mistaken = new AgentConversationDecision(
                AgentDecisionIntent.SHALOM_DATA, "cambiar agencia", "", "", "", 0, "",
                AgentCartAction.NONE, AgentCommercialAction.REQUEST_SUPPORT, "", "", "", "",
                "", "", "San Juan de Lurigancho", "", AgentCatalogAvailability.ALL, 90, true);

        AgentConversationDecision normalized = service.normalizeShalomAgencyRefinement(41L, mistaken);

        assertEquals(AgentCommercialAction.EDIT_SHALOM_DATA, normalized.commercialAction());
        assertEquals(java.util.List.of(AgentShalomDataField.AGENCY), normalized.shalomFieldsToChange());
        assertFalse(normalized.requiresHumanHelp());
    }

    @Test
    void reportsInvalidShalomPhoneWithoutEscalatingOrRetryingTheFlow() {
        CrmWhatsappAgentSessionRepository sessions = mock(CrmWhatsappAgentSessionRepository.class);
        AgentFulfillmentService fulfillment = mock(AgentFulfillmentService.class);
        CrmWhatsappAgentSession session = new CrmWhatsappAgentSession();
        session.setState(CrmWhatsappAgentState.ESPERANDO_DATOS_SHALOM);
        when(sessions.findById(41L)).thenReturn(Optional.of(session));
        AgentFulfillmentService.FulfillmentResult partialResult = new AgentFulfillmentService.FulfillmentResult(
                41L, 51L, "SHALOM", "DRAFT", "Cristhofer Leonardo Quispe Mamani", "DNI", "75132958",
                null, null, null, null, null, null, List.of(), "ESPERANDO_DATOS_SHALOM", null);
        when(fulfillment.savePartialShalomData(41L, "Cristhofer Leonardo Quispe Mamani", "75132958",
                "9338889985", "Agencia Montenegro"))
                .thenReturn(new AgentFulfillmentService.ShalomDataProgress(partialResult, List.of("celular"),
                        List.of("El celular es incorrecto. Ingresa un numero celular peruano valido de exactamente 9 digitos"),
                        false));
        AgentDeterministicCommerceService service = new AgentDeterministicCommerceService(
                sessions, null, null, null, null, null, fulfillment, null, null, null,
                new AgentToolCallAuditService(null));
        AgentConversationDecision decision = new AgentConversationDecision(
                AgentDecisionIntent.SHALOM_DATA, "datos para envio", "", "", "", 0, "",
                AgentCartAction.NONE, AgentCommercialAction.PROVIDE_SHALOM_DATA, "", "",
                "Cristhofer Leonardo Quispe Mamani", "9338889985", "SHALOM", "75132958",
                "Agencia Montenegro", "", AgentCatalogAvailability.ALL, 100, false);

        var result = service.execute(41L, null, decision);

        assertTrue(result.handled());
        assertTrue(result.mutationExecuted());
        assertTrue(result.customerMessage().contains("exactamente 9 digitos"));
        assertFalse(result.customerMessage().contains("asesora"));
    }

    @Test
    void asksOnlyForTheRemainingShalomFieldsAfterAPartialMessage() {
        CrmWhatsappAgentSessionRepository sessions = mock(CrmWhatsappAgentSessionRepository.class);
        AgentFulfillmentService fulfillment = mock(AgentFulfillmentService.class);
        CrmWhatsappAgentSession session = new CrmWhatsappAgentSession();
        session.setState(CrmWhatsappAgentState.ESPERANDO_DATOS_SHALOM);
        when(sessions.findById(41L)).thenReturn(Optional.of(session));
        AgentFulfillmentService.FulfillmentResult partialResult = new AgentFulfillmentService.FulfillmentResult(
                41L, 51L, "SHALOM", "DRAFT", "Jennifer Roxana Tapia Jara", "DNI", null,
                null, null, null, null, null, null, List.of(), "ESPERANDO_DATOS_SHALOM", null);
        when(fulfillment.savePartialShalomData(41L, "Jennifer Roxana Tapia Jara", "", "", ""))
                .thenReturn(new AgentFulfillmentService.ShalomDataProgress(partialResult,
                        List.of("DNI", "celular", "agencia de Shalom"), List.of(), false));
        AgentDeterministicCommerceService service = new AgentDeterministicCommerceService(
                sessions, null, null, null, null, null, fulfillment, null, null, null,
                new AgentToolCallAuditService(null));
        AgentConversationDecision decision = new AgentConversationDecision(
                AgentDecisionIntent.SHALOM_DATA, "datos para envio", "", "", "", 0, "",
                AgentCartAction.NONE, AgentCommercialAction.PROVIDE_SHALOM_DATA, "", "",
                "Jennifer Roxana Tapia Jara", "", "SHALOM", "", "", "",
                AgentCatalogAvailability.ALL, 100, false);

        var result = service.execute(41L, null, decision);

        assertTrue(result.customerMessage().contains("DNI, celular, agencia de Shalom"));
        assertFalse(result.customerMessage().contains("nombres y apellidos"));
    }

    @Test
    void sharesAdvisorContactWithoutStoppingAutomaticSales() {
        CrmWhatsappAgentSessionRepository sessions = mock(CrmWhatsappAgentSessionRepository.class);
        CrmWhatsappAgentSession session = new CrmWhatsappAgentSession();
        session.setState(CrmWhatsappAgentState.EXPLORANDO_PRODUCTOS);
        when(sessions.findById(41L)).thenReturn(Optional.of(session));
        AgentDeterministicCommerceService service = new AgentDeterministicCommerceService(
                sessions, null, null, null, null, null, null, null, null, null,
                new AgentToolCallAuditService(null));
        AgentConversationDecision decision = decision(AgentCommercialAction.SHARE_ADVISOR_CONTACT, "");

        var result = service.execute(41L, null, decision);

        assertTrue(result.handled());
        assertFalse(result.mutationExecuted());
        assertTrue(result.customerMessage().contains("933918047"));
        assertTrue(result.customerMessage().contains("seguir ayudandote"));
        assertEquals(CrmWhatsappAgentState.EXPLORANDO_PRODUCTOS, session.getState());
    }

    @Test
    void redirectsMotorizedDeliveryWithoutChangingStateOrStoppingAutomaticSales() {
        CrmWhatsappAgentSessionRepository sessions = mock(CrmWhatsappAgentSessionRepository.class);
        CrmWhatsappAgentSession session = new CrmWhatsappAgentSession();
        session.setState(CrmWhatsappAgentState.ARMANDO_CARRITO);
        when(sessions.findById(41L)).thenReturn(Optional.of(session));
        AgentDeterministicCommerceService service = new AgentDeterministicCommerceService(
                sessions, null, null, null, null, null, null, null, null, null,
                new AgentToolCallAuditService(null));
        AgentConversationDecision decision = decision(AgentCommercialAction.SHARE_MOTORIZED_CONTACT, "");

        var result = service.execute(41L, null, decision);

        assertTrue(result.handled());
        assertFalse(result.mutationExecuted());
        assertTrue(result.customerMessage().contains("motorizado"));
        assertTrue(result.customerMessage().contains("933918047"));
        assertTrue(result.customerMessage().contains("seguir ayudandote"));
        assertEquals(CrmWhatsappAgentState.ARMANDO_CARRITO, session.getState());
    }

    @Test
    void reopensAConfirmedCartBeforeApplyingAConcreteChange() {
        CrmWhatsappAgentSessionRepository sessions = mock(CrmWhatsappAgentSessionRepository.class);
        AgentPaymentService payments = mock(AgentPaymentService.class);
        CrmWhatsappAgentSession session = new CrmWhatsappAgentSession();
        session.setState(CrmWhatsappAgentState.ESPERANDO_METODO_PAGO);
        when(sessions.findById(41L)).thenReturn(Optional.of(session));
        AgentDeterministicCommerceService service = new AgentDeterministicCommerceService(
                sessions, null, null, null, payments, null, null, null, null, null, null);

        service.prepareCartMutation(41L);

        verify(payments).beginCartEdit(41L);
    }

    @Test
    void leavesAnEditableCartUntouchedBeforeApplyingAConcreteChange() {
        CrmWhatsappAgentSessionRepository sessions = mock(CrmWhatsappAgentSessionRepository.class);
        AgentPaymentService payments = mock(AgentPaymentService.class);
        CrmWhatsappAgentSession session = new CrmWhatsappAgentSession();
        session.setState(CrmWhatsappAgentState.ARMANDO_CARRITO);
        when(sessions.findById(41L)).thenReturn(Optional.of(session));
        AgentDeterministicCommerceService service = new AgentDeterministicCommerceService(
                sessions, null, null, null, payments, null, null, null, null, null, null);

        service.prepareCartMutation(41L);

        verify(payments, never()).beginCartEdit(41L);
    }

    @Test
    void usesTheWhatsappConversationPhoneWhenTheCustomerOnlyProvidesAName() {
        CrmWhatsappAgentSessionRepository sessions = mock(CrmWhatsappAgentSessionRepository.class);
        AgentPaymentService payments = mock(AgentPaymentService.class);
        AgentCustomerService customers = mock(AgentCustomerService.class);
        CrmWhatsappConversation conversation = new CrmWhatsappConversation();
        conversation.setPhone("51932889985@s.whatsapp.net");
        CrmWhatsappAgentSession session = new CrmWhatsappAgentSession();
        session.setConversation(conversation);
        when(sessions.findById(41L)).thenReturn(Optional.of(session));
        AgentDeterministicCommerceService service = new AgentDeterministicCommerceService(
                sessions, null, null, null, payments, customers, null, null, null, null, null);
        AgentConversationDecision decision = new AgentConversationDecision(
                AgentDecisionIntent.CUSTOMER_DATA, "registrar mis datos", "", "", "", 0, "", List.of(),
                AgentCartAction.NONE, AgentCommercialAction.PROVIDE_CUSTOMER_DATA, "", "",
                "Juancito Perez", "", "", "", "", "", List.of(),
                AgentCatalogAvailability.ALL, "", List.of(), "", 100, false);

        AgentDeterministicCommerceService.Outcome result = service.provideCustomer(
                41L, decision, "agent-job-1:provide_customer_data");

        assertTrue(result.handled());
        verify(customers).register(41L, "Juancito Perez", "932889985",
                TipoDocumento.SIN_DOC, null, "agent-job-1:provide_customer_data");
        verify(payments).bindApprovedCustomer(41L);
    }

    private AgentDeterministicCommerceService serviceWithNoDependencies() {
        return new AgentDeterministicCommerceService(null, null, null, null, null, null, null, null, null, null, null);
    }

    private PaymentMethodView method(int id, String name) {
        return new PaymentMethodView(id, name, "", false, false, false, List.of());
    }

    private AgentConversationDecision decision(AgentCommercialAction action, String paymentMethod) {
        return new AgentConversationDecision(
                AgentDecisionIntent.PAYMENT_METHOD, "confirmar y pagar", "", "", "", 0, "",
                AgentCartAction.CONFIRM_CART, action, paymentMethod, "", "", "",
                "", "", "", "", AgentCatalogAvailability.ALL, 100, false);
    }

    private int occurrences(String value, String expected) {
        return value.split(java.util.regex.Pattern.quote(expected), -1).length - 1;
    }
}
