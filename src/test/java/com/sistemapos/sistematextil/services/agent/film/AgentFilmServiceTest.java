package com.sistemapos.sistematextil.services.agent.film;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import java.sql.Timestamp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import com.sistemapos.sistematextil.model.*;
import com.sistemapos.sistematextil.repositories.*;
import com.sistemapos.sistematextil.services.agent.CrmWhatsappAgentStateMachine;

class AgentFilmServiceTest {
    private final CrmWhatsappFilmAddonRepository addons=mock(CrmWhatsappFilmAddonRepository.class);
    private final CrmWhatsappAgentSessionRepository sessions=mock(CrmWhatsappAgentSessionRepository.class);
    private final CrmWhatsappFulfillmentRepository fulfillments=mock(CrmWhatsappFulfillmentRepository.class);
    private final SucursalMetodoPagoConfigRepository branchMethods=mock(SucursalMetodoPagoConfigRepository.class);
    private final MetodoPagoCuentaRepository accounts=mock(MetodoPagoCuentaRepository.class);
    private final VentaRepository sales=mock(VentaRepository.class);
    private final AgentFilmEscalationService escalation=mock(AgentFilmEscalationService.class);
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    private final AgentFilmService service=new AgentFilmService(addons,sessions,fulfillments,branchMethods,accounts,sales,new CrmWhatsappAgentStateMachine(),escalation,jdbc);
    private CrmWhatsappAgentSession session;
    private CrmWhatsappFilmAddon addon;

    @BeforeEach void setup(){Sucursal branch=new Sucursal();branch.setIdSucursal(1);CrmWhatsappConnection connection=new CrmWhatsappConnection();connection.setSucursal(branch);
        session=new CrmWhatsappAgentSession();session.setIdAgentSession(9L);session.setState(CrmWhatsappAgentState.VENTA_PENDIENTE_REVISION);session.setConnection(connection);session.setConversation(new CrmWhatsappConversation());
        CrmWhatsappFulfillment f=new CrmWhatsappFulfillment();f.setIdFulfillment(4L);f.setAgentSession(session);f.setMethod(CrmWhatsappFulfillmentMethod.SHALOM);f.setStatus(CrmWhatsappFulfillmentStatus.READY);
        addon=new CrmWhatsappFilmAddon();addon.setIdFilmAddon(3L);addon.setAgentSession(session);addon.setFulfillment(f);addon.setConversation(session.getConversation());addon.setStatus(CrmWhatsappFilmStatus.OFFERED);addon.setAmount(AgentFilmService.PRICE);
        Venta sale=new Venta();sale.setIdVenta(12);when(sales.findById(12)).thenReturn(Optional.of(sale));
        when(sessions.findForUpdate(9L)).thenReturn(Optional.of(session));when(fulfillments.findForUpdateBySessionId(9L)).thenReturn(Optional.of(f));when(addons.findByAgentSession_IdAgentSession(9L)).thenReturn(Optional.of(addon));when(addons.findForUpdate(3L)).thenReturn(Optional.of(addon));when(addons.save(any())).thenAnswer(i->i.getArgument(0));when(sessions.save(any())).thenAnswer(i->i.getArgument(0));when(fulfillments.save(any())).thenAnswer(i->i.getArgument(0));}

    @Test void startsExactlyOneHourAfterSaleWithoutChangingMainPayment(){MetodoPagoConfig method=new MetodoPagoConfig();method.setIdMetodoPago(7);method.setNombre("YAPE");method.setEstado("ACTIVO");SucursalMetodoPagoConfig cfg=new SucursalMetodoPagoConfig();cfg.setMetodoPago(method);cfg.setEstado("ACTIVO");MetodoPagoCuenta account=new MetodoPagoCuenta();account.setIdMetodoPagoCuenta(8);account.setNumeroCuenta("999111222");account.setTitular("Kiments");
        when(branchMethods.findActivosBySucursal(1)).thenReturn(List.of(cfg));when(accounts.findByMetodoPago_IdMetodoPagoAndActivoTrueOrderByIdMetodoPagoCuentaAsc(7)).thenReturn(List.of(account));
        var result=service.startMandatoryPayment(9L,12);assertEquals("AWAITING_PAYMENT",result.status());assertEquals(3600,java.time.Duration.between(addon.getPaymentStartedAt(),addon.getExpiresAt()).toSeconds());assertEquals("999111222",result.accountNumber());assertNotNull(addon.getVenta());assertEquals(CrmWhatsappAgentState.ESPERANDO_PAGO_FILM,session.getState());}

    @Test void waitsForPreviousOrderAnswerBeforeStartingPayment(){
        var result=service.awaitPreviousOrderDecision(9L,12);
        assertEquals("AWAITING_PREVIOUS_ORDER_DECISION",result.status());
        assertEquals(CrmWhatsappAgentState.ESPERANDO_PEDIDO_ANTERIOR,session.getState());
        assertNull(addon.getPaymentStartedAt());
    }

    @Test void previousOrderRequiresAdvisorReviewWithoutStartingYape(){
        session.setState(CrmWhatsappAgentState.ESPERANDO_PEDIDO_ANTERIOR);
        addon.setStatus(CrmWhatsappFilmStatus.AWAITING_PREVIOUS_ORDER_DECISION);
        var result=service.confirmPreviousOrder(9L);
        assertEquals("ADVISOR_REVIEW_REQUIRED",result.status());
        assertEquals(CrmWhatsappAgentState.VENTA_PENDIENTE_REVISION,session.getState());
        assertNull(addon.getPaymentStartedAt());
    }

    @Test void noPreviousOrderStartsOneHourPayment(){MetodoPagoConfig method=new MetodoPagoConfig();method.setIdMetodoPago(7);method.setNombre("YAPE");method.setEstado("ACTIVO");SucursalMetodoPagoConfig cfg=new SucursalMetodoPagoConfig();cfg.setMetodoPago(method);cfg.setEstado("ACTIVO");MetodoPagoCuenta account=new MetodoPagoCuenta();account.setNumeroCuenta("999111222");
        session.setState(CrmWhatsappAgentState.ESPERANDO_PEDIDO_ANTERIOR);addon.setStatus(CrmWhatsappFilmStatus.AWAITING_PREVIOUS_ORDER_DECISION);
        when(branchMethods.findActivosBySucursal(1)).thenReturn(List.of(cfg));when(accounts.findByMetodoPago_IdMetodoPagoAndActivoTrueOrderByIdMetodoPagoCuentaAsc(7)).thenReturn(List.of(account));
        var result=service.confirmNoPreviousOrder(9L);
        assertEquals("AWAITING_PAYMENT",result.status());assertEquals(3600,java.time.Duration.between(addon.getPaymentStartedAt(),addon.getExpiresAt()).toSeconds());assertEquals(CrmWhatsappAgentState.ESPERANDO_PAGO_FILM,session.getState());}

    @Test void manualConfirmationIsAuditedAndMakesShipmentReady(){Venta sale=new Venta();sale.setIdVenta(12);addon.setVenta(sale);addon.setStatus(CrmWhatsappFilmStatus.PAYMENT_OVERDUE);session.setState(CrmWhatsappAgentState.REQUIERE_AYUDA);Usuario actor=new Usuario();actor.setIdUsuario(5);
        var result=service.confirmManually(3L,actor,null);
        assertEquals("CONFIRMED",result.status());assertEquals("MANUAL_CRM",addon.getResolutionSource());assertEquals(actor,addon.getResolvedBy());assertEquals(CrmWhatsappFulfillmentStatus.READY,addon.getFulfillment().getStatus());assertEquals(CrmWhatsappAgentState.VENTA_PENDIENTE_REVISION,session.getState());verify(jdbc).update(any(String.class),any(),any(),any(),any(),any());}

    @Test void previousPaidFilmCoversCurrentOrderWithoutNewMovement(){Cliente customer=new Cliente();customer.setIdCliente(7);Venta current=new Venta();current.setIdVenta(12);current.setCliente(customer);addon.setVenta(current);addon.setStatus(CrmWhatsappFilmStatus.ADVISOR_REVIEW_REQUIRED);session.setState(CrmWhatsappAgentState.VENTA_PENDIENTE_REVISION);Venta previousSale=new Venta();previousSale.setIdVenta(10);previousSale.setCliente(customer);CrmWhatsappFilmAddon previous=new CrmWhatsappFilmAddon();previous.setVenta(previousSale);previous.setStatus(CrmWhatsappFilmStatus.CONFIRMED);when(addons.findByVenta_IdVenta(10)).thenReturn(Optional.of(previous));
        var result=service.confirmManually(3L,new Usuario(),10);
        assertEquals("COVERED_BY_PREVIOUS_ORDER",result.status());assertEquals(previousSale,addon.getCoveredBySale());assertEquals("PREVIOUS_ORDER",addon.getResolutionSource());verifyNoInteractions(jdbc);}

    @Test void repairsWaitingStateOnIdempotentFilmPaymentRetry(){addon.setStatus(CrmWhatsappFilmStatus.AWAITING_PAYMENT);
        var result=service.startMandatoryPayment(9L,12);
        assertEquals("AWAITING_PAYMENT",result.status());assertEquals(CrmWhatsappAgentState.ESPERANDO_PAGO_FILM,session.getState());}

    @Test void preservesSaleAndBlocksShipmentWhenYapeIsUnavailable(){when(branchMethods.findActivosBySucursal(1)).thenReturn(List.of());var result=service.startMandatoryPayment(9L,12);assertFalse(result.continueWithoutFilm());assertEquals(CrmWhatsappFilmStatus.PAYMENT_CONFIGURATION_REQUIRED,addon.getStatus());assertNotNull(addon.getVenta());}

    @Test void decliningMandatoryFilmIsNotAllowed(){assertThrows(IllegalStateException.class,()->service.decline(9L));}

    @SuppressWarnings("unchecked")
    @Test void combinedMainReceiptCreatesOneAuditedFilmMovement(){
        MetodoPagoConfig method=new MetodoPagoConfig();method.setIdMetodoPago(7);method.setNombre("PLIN");
        MetodoPagoCuenta account=new MetodoPagoCuenta();account.setIdMetodoPagoCuenta(8);account.setMetodoPago(method);
        session.setSelectedPaymentMethod(method);session.setSelectedPaymentAccount(account);
        when(jdbc.queryForObject(anyString(),any(RowMapper.class),eq(9L))).thenAnswer(invocation->{
            RowMapper<Object> mapper=invocation.getArgument(1);java.sql.ResultSet rs=mock(java.sql.ResultSet.class);
            when(rs.getLong("id_payment_evidence")).thenReturn(91L);when(rs.getInt("id_metodo_pago")).thenReturn(7);
            when(rs.getObject("id_metodo_pago_cuenta")).thenReturn(8);when(rs.getString("nombre")).thenReturn("PLIN");
            when(rs.getString("currency")).thenReturn("PEN");when(rs.getString("operation_code")).thenReturn("OP-81");
            when(rs.getTimestamp("operation_at")).thenReturn(Timestamp.valueOf("2026-10-18 09:00:00"));
            return mapper.mapRow(rs,0);
        });

        var result=service.confirmCombinedPayment(9L,12);

        assertEquals("CONFIRMED",result.status());assertEquals("COMBINED_MAIN_RECEIPT",addon.getResolutionSource());
        assertEquals(account,addon.getPaymentAccount());assertEquals(CrmWhatsappFulfillmentStatus.READY,addon.getFulfillment().getStatus());
        verify(jdbc).update(contains("id_payment_evidence"),eq(3L),eq(12),eq(91L),eq(7),eq(8),
                eq(AgentFilmService.PRICE),eq("PEN"),eq("PLIN"),eq("OP-81"),any());
    }
}
