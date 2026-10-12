package com.sistemapos.sistematextil.services.agent.film;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
    private final AgentFilmService service=new AgentFilmService(addons,sessions,fulfillments,branchMethods,accounts,sales,new CrmWhatsappAgentStateMachine(),escalation);
    private CrmWhatsappAgentSession session;
    private CrmWhatsappFilmAddon addon;

    @BeforeEach void setup(){Sucursal branch=new Sucursal();branch.setIdSucursal(1);CrmWhatsappConnection connection=new CrmWhatsappConnection();connection.setSucursal(branch);
        session=new CrmWhatsappAgentSession();session.setIdAgentSession(9L);session.setState(CrmWhatsappAgentState.VENTA_PENDIENTE_REVISION);session.setConnection(connection);session.setConversation(new CrmWhatsappConversation());
        CrmWhatsappFulfillment f=new CrmWhatsappFulfillment();f.setIdFulfillment(4L);f.setAgentSession(session);f.setMethod(CrmWhatsappFulfillmentMethod.SHALOM);f.setStatus(CrmWhatsappFulfillmentStatus.READY);
        addon=new CrmWhatsappFilmAddon();addon.setIdFilmAddon(3L);addon.setAgentSession(session);addon.setFulfillment(f);addon.setConversation(session.getConversation());addon.setStatus(CrmWhatsappFilmStatus.OFFERED);addon.setAmount(AgentFilmService.PRICE);
        Venta sale=new Venta();sale.setIdVenta(12);when(sales.findById(12)).thenReturn(Optional.of(sale));
        when(sessions.findForUpdate(9L)).thenReturn(Optional.of(session));when(fulfillments.findForUpdateBySessionId(9L)).thenReturn(Optional.of(f));when(addons.findByAgentSession_IdAgentSession(9L)).thenReturn(Optional.of(addon));when(addons.save(any())).thenAnswer(i->i.getArgument(0));when(sessions.save(any())).thenAnswer(i->i.getArgument(0));when(fulfillments.save(any())).thenAnswer(i->i.getArgument(0));}

    @Test void startsExactlyTwelveHoursAfterSaleWithoutChangingMainPayment(){MetodoPagoConfig method=new MetodoPagoConfig();method.setIdMetodoPago(7);method.setNombre("YAPE");method.setEstado("ACTIVO");SucursalMetodoPagoConfig cfg=new SucursalMetodoPagoConfig();cfg.setMetodoPago(method);cfg.setEstado("ACTIVO");MetodoPagoCuenta account=new MetodoPagoCuenta();account.setIdMetodoPagoCuenta(8);account.setNumeroCuenta("999111222");account.setTitular("Kiments");
        when(branchMethods.findActivosBySucursal(1)).thenReturn(List.of(cfg));when(accounts.findByMetodoPago_IdMetodoPagoAndActivoTrueOrderByIdMetodoPagoCuentaAsc(7)).thenReturn(List.of(account));
        var result=service.startMandatoryPayment(9L,12);assertEquals("AWAITING_PAYMENT",result.status());assertEquals(43200,java.time.Duration.between(addon.getPaymentStartedAt(),addon.getExpiresAt()).toSeconds());assertEquals("999111222",result.accountNumber());assertNotNull(addon.getVenta());assertEquals(CrmWhatsappAgentState.ESPERANDO_PAGO_FILM,session.getState());}

    @Test void repairsWaitingStateOnIdempotentFilmPaymentRetry(){addon.setStatus(CrmWhatsappFilmStatus.AWAITING_PAYMENT);
        var result=service.startMandatoryPayment(9L,12);
        assertEquals("AWAITING_PAYMENT",result.status());assertEquals(CrmWhatsappAgentState.ESPERANDO_PAGO_FILM,session.getState());}

    @Test void preservesSaleAndBlocksShipmentWhenYapeIsUnavailable(){when(branchMethods.findActivosBySucursal(1)).thenReturn(List.of());var result=service.startMandatoryPayment(9L,12);assertFalse(result.continueWithoutFilm());assertEquals(CrmWhatsappFilmStatus.PAYMENT_CONFIGURATION_REQUIRED,addon.getStatus());assertNotNull(addon.getVenta());}

    @Test void decliningMandatoryFilmIsNotAllowed(){assertThrows(IllegalStateException.class,()->service.decline(9L));}
}
