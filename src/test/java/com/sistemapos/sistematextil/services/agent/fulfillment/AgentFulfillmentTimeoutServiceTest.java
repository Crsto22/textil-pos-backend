package com.sistemapos.sistematextil.services.agent.fulfillment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.sistemapos.sistematextil.model.Cliente;
import com.sistemapos.sistematextil.model.CrmWhatsappAgentEvent;
import com.sistemapos.sistematextil.model.CrmWhatsappAgentSession;
import com.sistemapos.sistematextil.model.CrmWhatsappAgentState;
import com.sistemapos.sistematextil.model.CrmWhatsappFulfillment;
import com.sistemapos.sistematextil.model.CrmWhatsappFulfillmentMethod;
import com.sistemapos.sistematextil.model.CrmWhatsappFulfillmentStatus;
import com.sistemapos.sistematextil.model.CrmWhatsappPaymentRequest;
import com.sistemapos.sistematextil.model.Venta;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAgentSessionRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappFulfillmentRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappPaymentRequestRepository;
import com.sistemapos.sistematextil.services.agent.CrmWhatsappAgentStateMachine;
import com.sistemapos.sistematextil.services.agent.outbox.AgentOutboxService;
import com.sistemapos.sistematextil.services.agent.sale.AgentSaleService;
import com.sistemapos.sistematextil.services.agent.sale.AgentSaleService.SaleResult;

class AgentFulfillmentTimeoutServiceTest {
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-10-25T18:00:00Z"), ZoneOffset.UTC);

    @Test
    void createsOneSaleWithPendingDeliveryWhenTheCustomerNeverChoosesAMethod() {
        CrmWhatsappAgentSessionRepository sessions = mock(CrmWhatsappAgentSessionRepository.class);
        CrmWhatsappFulfillmentRepository fulfillments = mock(CrmWhatsappFulfillmentRepository.class);
        CrmWhatsappPaymentRequestRepository payments = mock(CrmWhatsappPaymentRequestRepository.class);
        CrmWhatsappAgentStateMachine stateMachine = mock(CrmWhatsappAgentStateMachine.class);
        AgentSaleService sales = mock(AgentSaleService.class);
        AgentOutboxService outbox = mock(AgentOutboxService.class);
        CrmWhatsappAgentSession session = session(9L, CrmWhatsappAgentState.ESPERANDO_METODO_ENTREGA, 31);
        CrmWhatsappPaymentRequest payment = new CrmWhatsappPaymentRequest();
        payment.setIdPaymentRequest(50L);
        Cliente customer = new Cliente();
        customer.setIdCliente(10);
        payment.setCliente(customer);

        when(sessions.findForUpdate(9L)).thenReturn(Optional.of(session));
        when(fulfillments.findForUpdateBySessionId(9L)).thenReturn(Optional.empty());
        when(payments.findFirstByAgentSession_IdAgentSessionAndStatusInOrderByCreatedAtDesc(eq(9L), any()))
                .thenReturn(Optional.of(payment));
        when(payments.findForUpdate(50L)).thenReturn(Optional.of(payment));
        when(stateMachine.transition(CrmWhatsappAgentState.ESPERANDO_METODO_ENTREGA,
                CrmWhatsappAgentEvent.ENTREGA_DERIVADA_A_ASESORA))
                .thenReturn(CrmWhatsappAgentState.LISTO_PARA_VENTA);
        when(fulfillments.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(sales.createSalesNote(9L, "automatic-delivery-timeout:9")).thenReturn(
                new SaleResult(12520, "NOTA DE VENTA", "NV101", 10649, BigDecimal.valueOf(146),
                        "CRM_WHATSAPP", 50L, 70L, 80L, "CONSUMED",
                        CrmWhatsappAgentState.VENTA_PENDIENTE_REVISION.name(), false));

        AgentFulfillmentTimeoutService service = new AgentFulfillmentTimeoutService(
                sessions, fulfillments, payments, stateMachine, sales, outbox, CLOCK, 30);

        assertTrue(service.completeTimedOutDelivery(9L));
        ArgumentCaptor<CrmWhatsappFulfillment> fulfillment = ArgumentCaptor.forClass(CrmWhatsappFulfillment.class);
        verify(fulfillments).save(fulfillment.capture());
        assertEquals(CrmWhatsappFulfillmentMethod.PENDING, fulfillment.getValue().getMethod());
        assertEquals(CrmWhatsappFulfillmentStatus.PENDING_ADVISOR, fulfillment.getValue().getStatus());
        verify(outbox).enqueueText(eq(9L), eq(null), eq(null), anyInt(),
                org.mockito.ArgumentMatchers.contains("Datos de entrega: No mencionados"));
    }

    @Test
    void handsAnExistingShalomSaleToTheAdvisorAfterThePreviousOrderQuestionTimesOut() {
        CrmWhatsappAgentSessionRepository sessions = mock(CrmWhatsappAgentSessionRepository.class);
        CrmWhatsappFulfillmentRepository fulfillments = mock(CrmWhatsappFulfillmentRepository.class);
        CrmWhatsappAgentStateMachine stateMachine = mock(CrmWhatsappAgentStateMachine.class);
        AgentSaleService sales = mock(AgentSaleService.class);
        AgentOutboxService outbox = mock(AgentOutboxService.class);
        CrmWhatsappAgentSession session = session(11L, CrmWhatsappAgentState.ESPERANDO_PEDIDO_ANTERIOR, 31);
        CrmWhatsappFulfillment fulfillment = new CrmWhatsappFulfillment();
        fulfillment.setMethod(CrmWhatsappFulfillmentMethod.SHALOM);
        fulfillment.setStatus(CrmWhatsappFulfillmentStatus.READY);
        Venta sale = new Venta();
        sale.setIdVenta(12521);
        fulfillment.setVenta(sale);

        when(sessions.findForUpdate(11L)).thenReturn(Optional.of(session));
        when(fulfillments.findForUpdateBySessionId(11L)).thenReturn(Optional.of(fulfillment));
        when(stateMachine.transition(CrmWhatsappAgentState.ESPERANDO_PEDIDO_ANTERIOR,
                CrmWhatsappAgentEvent.ENTREGA_DERIVADA_A_ASESORA))
                .thenReturn(CrmWhatsappAgentState.VENTA_PENDIENTE_REVISION);

        AgentFulfillmentTimeoutService service = new AgentFulfillmentTimeoutService(
                sessions, fulfillments, mock(CrmWhatsappPaymentRequestRepository.class), stateMachine,
                sales, outbox, CLOCK, 30);

        assertTrue(service.completeTimedOutDelivery(11L));
        assertEquals(CrmWhatsappFulfillmentStatus.PENDING_ADVISOR, fulfillment.getStatus());
        verify(sales, never()).createSalesNote(anyLong(), anyString());
        verify(outbox).enqueueText(eq(11L), eq(null), eq(null), anyInt(),
                org.mockito.ArgumentMatchers.contains("Envío por Shalom"));
    }

    @Test
    void doesNothingBeforeTheThirtyMinuteDeadline() {
        CrmWhatsappAgentSessionRepository sessions = mock(CrmWhatsappAgentSessionRepository.class);
        CrmWhatsappAgentSession session = session(12L, CrmWhatsappAgentState.ESPERANDO_DATOS_SHALOM, 29);
        when(sessions.findForUpdate(12L)).thenReturn(Optional.of(session));
        AgentSaleService sales = mock(AgentSaleService.class);
        AgentOutboxService outbox = mock(AgentOutboxService.class);
        AgentFulfillmentTimeoutService service = new AgentFulfillmentTimeoutService(
                sessions, mock(CrmWhatsappFulfillmentRepository.class),
                mock(CrmWhatsappPaymentRequestRepository.class), mock(CrmWhatsappAgentStateMachine.class),
                sales, outbox, CLOCK, 30);

        assertFalse(service.completeTimedOutDelivery(12L));
        verify(sales, never()).createSalesNote(anyLong(), anyString());
        verify(outbox, never()).enqueueText(anyLong(), any(), any(), anyInt(), anyString());
    }

    private CrmWhatsappAgentSession session(Long id, CrmWhatsappAgentState state, long inactiveMinutes) {
        CrmWhatsappAgentSession session = new CrmWhatsappAgentSession();
        session.setIdAgentSession(id);
        session.setState(state);
        session.setActiveSlot(true);
        session.setLastActivityAt(LocalDateTime.now(CLOCK).minusMinutes(inactiveMinutes));
        return session;
    }
}
