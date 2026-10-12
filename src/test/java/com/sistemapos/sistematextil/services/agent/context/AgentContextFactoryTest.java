package com.sistemapos.sistematextil.services.agent.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sistemapos.sistematextil.model.CrmWhatsappAgentEvent;
import com.sistemapos.sistematextil.model.CrmWhatsappAgentState;
import com.sistemapos.sistematextil.services.agent.context.AgentContext.Cart;
import com.sistemapos.sistematextil.services.agent.context.AgentContext.CartItem;
import com.sistemapos.sistematextil.services.agent.context.AgentContext.CartPromotion;
import com.sistemapos.sistematextil.services.agent.context.AgentContext.Fulfillment;
import com.sistemapos.sistematextil.services.agent.context.AgentContext.Message;
import com.sistemapos.sistematextil.services.agent.context.AgentContext.Payment;
import com.sistemapos.sistematextil.services.agent.context.AgentContext.PaymentEvidence;
import com.sistemapos.sistematextil.services.agent.context.AgentContext.Role;
import com.sistemapos.sistematextil.services.agent.context.AgentContext.ToolResult;
import com.sistemapos.sistematextil.services.agent.context.AgentContextDataSource.MessageSource;
import com.sistemapos.sistematextil.services.agent.context.AgentContextDataSource.SessionSnapshot;

class AgentContextFactoryTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 8, 12, 0);

    @Test
    void buildsACompleteReproducibleContextAndExcludesOperationalMessages() {
        AgentContextDataSource source = mock(AgentContextDataSource.class);
        AgentContextFactory factory = new AgentContextFactory(
                source, new ObjectMapper().findAndRegisterModules(), 30, 12);
        SessionSnapshot session = session(CrmWhatsappAgentState.ESPERANDO_DATOS_SHALOM);
        when(source.requireSession(10L)).thenReturn(session);
        when(source.findRecentMessages(20L, 105L, 30)).thenReturn(List.of(
                message(101L, "INCOMING", "EXTERNAL", "Quiero el combo 45"),
                message(102L, "OUTGOING", "CRM_SYSTEM", "Aviso operativo"),
                message(103L, "OUTGOING", "HUMAN", "Mensaje de una asesora"),
                message(104L, "OUTGOING", "AGENT", "¿Qué talla deseas?"),
                message(105L, "INCOMING", "EXTERNAL", "Plata y L")));

        Cart cart = new Cart(30L, "DRAFT", "PEN", money("170"), money("15"), money("155"),
                List.of(new CartItem(1L, 50, "ALICE RAYAS", "PLATA", "L", 1, money("95"), money("95"))),
                List.of(new CartPromotion(2L, 45, "combo 45", 1, money("155"), money("15"))));
        Payment payment = new Payment(40L, "PENDING_EVIDENCE", "ACTIVE", "YAPE", money("155"), "PEN",
                NOW.plusMinutes(4), new PaymentEvidence(41L, "ACEPTABLE", "COMPLETED", "YAPE", money("155"),
                        "PEN", 98, NOW));
        Fulfillment fulfillment = new Fulfillment(50L, null, "SHALOM", "DRAFT", "Ana Perez", "DNI", null,
                "999111222", null, null, null, "AREQUIPA", null, null);
        ToolResult tool = new ToolResult(60L, "buscar_producto", "COMPLETED", "{\"product\":\"ALICE RAYAS\"}",
                null, null, NOW);
        when(source.findActiveCart(10L)).thenReturn(Optional.of(cart));
        when(source.findLatestPayment(10L)).thenReturn(Optional.of(payment));
        when(source.findFulfillment(10L)).thenReturn(Optional.of(fulfillment));
        when(source.findRecentToolResults(10L, 12)).thenReturn(List.of(tool));

        AgentContext first = factory.build(10L, 105L);
        AgentContext second = factory.build(10L, 105L);

        assertEquals(first, second);
        assertEquals(3, first.recentMessages().size());
        assertEquals(List.of(Role.CUSTOMER, Role.ASSISTANT, Role.CUSTOMER),
                first.recentMessages().stream().map(Message::role).toList());
        assertFalse(first.recentMessages().stream().anyMatch(message -> message.content().contains("operativo")));
        assertFalse(first.recentMessages().stream().anyMatch(message -> message.content().contains("asesora")));
        assertEquals("Resumen anterior", first.previousSummary());
        assertEquals(cart, first.cart());
        assertEquals(payment, first.payment());
        assertEquals(fulfillment, first.fulfillment());
        assertEquals(List.of(tool), first.toolResults());
        assertEquals(List.of(AgentMissingData.SHALOM_DOCUMENT_NUMBER, AgentMissingData.SHALOM_AGENCY),
                first.missingData());
        assertNotNull(first.fingerprint());
        assertEquals(64, first.fingerprint().length());
        verify(source, org.mockito.Mockito.times(2)).findRecentMessages(20L, 105L, 30);
    }

    @Test
    void derivesOnlyTheMissingDataExpectedByTheCurrentState() {
        AgentContextDataSource source = mock(AgentContextDataSource.class);
        AgentContextFactory factory = new AgentContextFactory(
                source, new ObjectMapper().findAndRegisterModules(), 30, 12);
        SessionSnapshot session = new SessionSnapshot(
                10L, "session-10", 20L, 2L, 1, 105L,
                CrmWhatsappAgentState.ESPERANDO_DATOS_CLIENTE, null,
                CrmWhatsappAgentEvent.CARRITO_CONFIRMADO, "", null, "", "");
        when(source.requireSession(10L)).thenReturn(session);
        when(source.findRecentMessages(20L, 105L, 30)).thenReturn(List.of());
        when(source.findActiveCart(10L)).thenReturn(Optional.empty());
        when(source.findLatestPayment(10L)).thenReturn(Optional.empty());
        when(source.findFulfillment(10L)).thenReturn(Optional.empty());
        when(source.findRecentToolResults(10L, 12)).thenReturn(List.of());

        AgentContext context = factory.build(10L);

        assertEquals(List.of(AgentMissingData.CUSTOMER_NAME, AgentMissingData.CUSTOMER_PHONE),
                context.missingData());
        assertTrue(context.cart().items().isEmpty());
        assertEquals(105L, context.anchorMessageId());
    }

    @Test
    void treatsTheWhatsappConversationPhoneAsKnownCustomerData() {
        AgentContextDataSource source = mock(AgentContextDataSource.class);
        AgentContextFactory factory = new AgentContextFactory(
                source, new ObjectMapper().findAndRegisterModules(), 30, 12);
        SessionSnapshot session = new SessionSnapshot(
                10L, "session-10", 20L, 2L, 1, 105L,
                CrmWhatsappAgentState.ESPERANDO_DATOS_CLIENTE, null,
                CrmWhatsappAgentEvent.COMPROBANTE_APROBADO_INICIALMENTE, "", null, "",
                "51932889985@s.whatsapp.net");
        when(source.requireSession(10L)).thenReturn(session);
        when(source.findRecentMessages(20L, 105L, 30)).thenReturn(List.of());
        when(source.findActiveCart(10L)).thenReturn(Optional.empty());
        when(source.findLatestPayment(10L)).thenReturn(Optional.empty());
        when(source.findFulfillment(10L)).thenReturn(Optional.empty());
        when(source.findRecentToolResults(10L, 12)).thenReturn(List.of());

        AgentContext context = factory.build(10L);

        assertEquals("932889985", context.customer().phone());
        assertEquals(List.of(AgentMissingData.CUSTOMER_NAME), context.missingData());
    }

    private SessionSnapshot session(CrmWhatsappAgentState state) {
        return new SessionSnapshot(
                10L, "session-10", 20L, 2L, 1, 105L, state,
                null, CrmWhatsappAgentEvent.ENVIO_SHALOM_SELECCIONADO,
                " Resumen anterior ", 9, "Ana Perez", "999111222");
    }

    private MessageSource message(Long id, String direction, String origin, String content) {
        return new MessageSource(new Message(id, Role.CUSTOMER, "TEXT", content, NOW.plusSeconds(id)),
                direction, origin);
    }

    private BigDecimal money(String value) {
        return new BigDecimal(value).setScale(2);
    }
}
