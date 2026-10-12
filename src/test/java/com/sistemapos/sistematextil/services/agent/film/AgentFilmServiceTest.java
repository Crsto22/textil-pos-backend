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
    private final AgentFilmService service=new AgentFilmService(addons,sessions,fulfillments,branchMethods,accounts,new CrmWhatsappAgentStateMachine());
    private CrmWhatsappAgentSession session;
    private CrmWhatsappFilmAddon addon;

    @BeforeEach void setup(){Sucursal branch=new Sucursal();branch.setIdSucursal(1);CrmWhatsappConnection connection=new CrmWhatsappConnection();connection.setSucursal(branch);
        session=new CrmWhatsappAgentSession();session.setIdAgentSession(9L);session.setState(CrmWhatsappAgentState.ESPERANDO_DECISION_FILM);session.setConnection(connection);session.setConversation(new CrmWhatsappConversation());
        CrmWhatsappFulfillment f=new CrmWhatsappFulfillment();f.setIdFulfillment(4L);f.setAgentSession(session);f.setMethod(CrmWhatsappFulfillmentMethod.SHALOM);f.setStatus(CrmWhatsappFulfillmentStatus.READY);
        addon=new CrmWhatsappFilmAddon();addon.setIdFilmAddon(3L);addon.setAgentSession(session);addon.setFulfillment(f);addon.setConversation(session.getConversation());addon.setStatus(CrmWhatsappFilmStatus.OFFERED);addon.setAmount(AgentFilmService.PRICE);
        when(sessions.findForUpdate(9L)).thenReturn(Optional.of(session));when(fulfillments.findForUpdateBySessionId(9L)).thenReturn(Optional.of(f));when(addons.findByAgentSession_IdAgentSession(9L)).thenReturn(Optional.of(addon));when(addons.save(any())).thenAnswer(i->i.getArgument(0));when(sessions.save(any())).thenAnswer(i->i.getArgument(0));}

    @Test void startsExactlyOneHourFilmPaymentWithoutChangingMainPayment(){MetodoPagoConfig method=new MetodoPagoConfig();method.setIdMetodoPago(7);method.setNombre("YAPE");method.setEstado("ACTIVO");SucursalMetodoPagoConfig cfg=new SucursalMetodoPagoConfig();cfg.setMetodoPago(method);cfg.setEstado("ACTIVO");MetodoPagoCuenta account=new MetodoPagoCuenta();account.setIdMetodoPagoCuenta(8);account.setNumeroCuenta("999111222");account.setTitular("Kiments");
        when(branchMethods.findActivosBySucursal(1)).thenReturn(List.of(cfg));when(accounts.findByMetodoPago_IdMetodoPagoAndActivoTrueOrderByIdMetodoPagoCuentaAsc(7)).thenReturn(List.of(account));
        var result=service.accept(9L);assertEquals("AWAITING_PAYMENT",result.status());assertEquals(CrmWhatsappAgentState.ESPERANDO_PAGO_FILM,session.getState());assertEquals(3600,java.time.Duration.between(addon.getPaymentStartedAt(),addon.getExpiresAt()).toSeconds());assertEquals("999111222",result.accountNumber());}

    @Test void continuesWithoutFilmWhenYapeIsUnavailable(){when(branchMethods.findActivosBySucursal(1)).thenReturn(List.of());var result=service.accept(9L);assertTrue(result.continueWithoutFilm());assertEquals(CrmWhatsappFilmStatus.UNAVAILABLE_WITHOUT_FILM,addon.getStatus());assertEquals(CrmWhatsappAgentState.LISTO_PARA_VENTA,session.getState());}

    @Test void decliningFilmLeavesTheSaleReady(){var result=service.decline(9L);assertTrue(result.continueWithoutFilm());assertEquals(CrmWhatsappFilmStatus.DECLINED,addon.getStatus());assertEquals(CrmWhatsappAgentState.LISTO_PARA_VENTA,session.getState());}
}
