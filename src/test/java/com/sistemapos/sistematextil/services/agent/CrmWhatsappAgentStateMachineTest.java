package com.sistemapos.sistematextil.services.agent;

import static com.sistemapos.sistematextil.model.CrmWhatsappAgentEvent.AYUDA_REQUERIDA;
import static com.sistemapos.sistematextil.model.CrmWhatsappAgentEvent.CANCELACION_SOLICITADA;
import static com.sistemapos.sistematextil.model.CrmWhatsappAgentEvent.CARRITO_CONFIRMADO;
import static com.sistemapos.sistematextil.model.CrmWhatsappAgentEvent.COMPROBANTE_APROBADO_INICIALMENTE;
import static com.sistemapos.sistematextil.model.CrmWhatsappAgentEvent.COMPROBANTE_RECIBIDO;
import static com.sistemapos.sistematextil.model.CrmWhatsappAgentEvent.CONVERSACION_INICIADA;
import static com.sistemapos.sistematextil.model.CrmWhatsappAgentEvent.DATOS_CLIENTE_COMPLETADOS;
import static com.sistemapos.sistematextil.model.CrmWhatsappAgentEvent.DATOS_SHALOM_COMPLETADOS;
import static com.sistemapos.sistematextil.model.CrmWhatsappAgentEvent.ENVIO_SHALOM_SELECCIONADO;
import static com.sistemapos.sistematextil.model.CrmWhatsappAgentEvent.NOTA_VENTA_CREADA;
import static com.sistemapos.sistematextil.model.CrmWhatsappAgentEvent.NUEVA_COMPRA_INICIADA;
import static com.sistemapos.sistematextil.model.CrmWhatsappAgentEvent.PAGO_APROBADO_FINALMENTE;
import static com.sistemapos.sistematextil.model.CrmWhatsappAgentEvent.FILM_ACEPTADO;
import static com.sistemapos.sistematextil.model.CrmWhatsappAgentEvent.PAGO_FILM_APROBADO;
import static com.sistemapos.sistematextil.model.CrmWhatsappAgentEvent.PAGO_RECHAZADO_Y_VENTA_ANULADA;
import static com.sistemapos.sistematextil.model.CrmWhatsappAgentEvent.PRODUCTO_SELECCIONADO;
import static com.sistemapos.sistematextil.model.CrmWhatsappAgentEvent.RECOJO_TIENDA_SELECCIONADO;
import static com.sistemapos.sistematextil.model.CrmWhatsappAgentEvent.SOLICITUD_PAGO_CREADA;
import static com.sistemapos.sistematextil.model.CrmWhatsappAgentEvent.SOLICITUD_PAGO_CANCELADA;
import static com.sistemapos.sistematextil.model.CrmWhatsappAgentEvent.SOLICITUD_PAGO_EXPIRADA;
import static com.sistemapos.sistematextil.model.CrmWhatsappAgentEvent.SOLICITUD_PAGO_REINTENTADA;
import static com.sistemapos.sistematextil.model.CrmWhatsappAgentEvent.AGENCIA_SHALOM_CONFIRMADA;
import static com.sistemapos.sistematextil.model.CrmWhatsappAgentState.ARMANDO_CARRITO;
import static com.sistemapos.sistematextil.model.CrmWhatsappAgentState.CANCELADO;
import static com.sistemapos.sistematextil.model.CrmWhatsappAgentState.COMPLETADO;
import static com.sistemapos.sistematextil.model.CrmWhatsappAgentState.ESPERANDO_CONFIRMACION_AGENCIA_SHALOM;
import static com.sistemapos.sistematextil.model.CrmWhatsappAgentState.ESPERANDO_DECISION_FILM;
import static com.sistemapos.sistematextil.model.CrmWhatsappAgentState.ESPERANDO_PAGO_FILM;
import static com.sistemapos.sistematextil.model.CrmWhatsappAgentState.ESPERANDO_COMPROBANTE;
import static com.sistemapos.sistematextil.model.CrmWhatsappAgentState.ESPERANDO_DATOS_CLIENTE;
import static com.sistemapos.sistematextil.model.CrmWhatsappAgentState.ESPERANDO_DATOS_SHALOM;
import static com.sistemapos.sistematextil.model.CrmWhatsappAgentState.ESPERANDO_METODO_ENTREGA;
import static com.sistemapos.sistematextil.model.CrmWhatsappAgentState.ESPERANDO_METODO_PAGO;
import static com.sistemapos.sistematextil.model.CrmWhatsappAgentState.EXPLORANDO_PRODUCTOS;
import static com.sistemapos.sistematextil.model.CrmWhatsappAgentState.IDLE;
import static com.sistemapos.sistematextil.model.CrmWhatsappAgentState.LISTO_PARA_VENTA;
import static com.sistemapos.sistematextil.model.CrmWhatsappAgentState.REQUIERE_AYUDA;
import static com.sistemapos.sistematextil.model.CrmWhatsappAgentState.VALIDANDO_COMPROBANTE;
import static com.sistemapos.sistematextil.model.CrmWhatsappAgentState.VENTA_PENDIENTE_REVISION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.sistemapos.sistematextil.model.CrmWhatsappAgentEvent;
import com.sistemapos.sistematextil.model.CrmWhatsappAgentState;

class CrmWhatsappAgentStateMachineTest {

    private final CrmWhatsappAgentStateMachine machine = new CrmWhatsappAgentStateMachine();

    @Test
    void completesPickupPurchaseFlow() {
        CrmWhatsappAgentState state = IDLE;
        state = machine.transition(state, CONVERSACION_INICIADA);
        assertEquals(EXPLORANDO_PRODUCTOS, state);
        state = machine.transition(state, PRODUCTO_SELECCIONADO);
        assertEquals(ARMANDO_CARRITO, state);
        state = machine.transition(state, CARRITO_CONFIRMADO);
        assertEquals(ESPERANDO_METODO_PAGO, state);
        state = machine.transition(state, SOLICITUD_PAGO_CREADA);
        assertEquals(ESPERANDO_COMPROBANTE, state);
        state = machine.transition(state, COMPROBANTE_RECIBIDO);
        assertEquals(VALIDANDO_COMPROBANTE, state);
        state = machine.transition(state, COMPROBANTE_APROBADO_INICIALMENTE);
        assertEquals(ESPERANDO_DATOS_CLIENTE, state);
        state = machine.transition(state, DATOS_CLIENTE_COMPLETADOS);
        assertEquals(ESPERANDO_METODO_ENTREGA, state);
        state = machine.transition(state, RECOJO_TIENDA_SELECCIONADO);
        assertEquals(LISTO_PARA_VENTA, state);
        state = machine.transition(state, NOTA_VENTA_CREADA);
        assertEquals(VENTA_PENDIENTE_REVISION, state);
        state = machine.transition(state, PAGO_APROBADO_FINALMENTE);
        assertEquals(COMPLETADO, state);
        assertTrue(state.isTerminal());
    }

    @Test
    void completesShalomPurchaseFlow() {
        CrmWhatsappAgentState state = ESPERANDO_METODO_ENTREGA;
        state = machine.transition(state, ENVIO_SHALOM_SELECCIONADO);
        assertEquals(ESPERANDO_DATOS_SHALOM, state);
        state = machine.transition(state, DATOS_SHALOM_COMPLETADOS);
        assertEquals(ESPERANDO_CONFIRMACION_AGENCIA_SHALOM, state);
        state = machine.transition(state, AGENCIA_SHALOM_CONFIRMADA);
        assertEquals(ESPERANDO_DECISION_FILM, state);
        state = machine.transition(state, FILM_ACEPTADO);
        assertEquals(ESPERANDO_PAGO_FILM, state);
        state = machine.transition(state, PAGO_FILM_APROBADO);
        assertEquals(LISTO_PARA_VENTA, state);
    }

    @Test
    void continuesShalomSaleWhenFilmIsDeclinedOrExpires() {
        assertEquals(LISTO_PARA_VENTA, machine.transition(ESPERANDO_DECISION_FILM,
                CrmWhatsappAgentEvent.FILM_RECHAZADO));
        assertEquals(LISTO_PARA_VENTA, machine.transition(ESPERANDO_PAGO_FILM,
                CrmWhatsappAgentEvent.PAGO_FILM_EXPIRADO));
    }

    @Test
    void rejectsUnsafeStateSkip() {
        assertFalse(machine.canTransition(ESPERANDO_COMPROBANTE, NOTA_VENTA_CREADA));
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> machine.transition(ESPERANDO_COMPROBANTE, NOTA_VENTA_CREADA));
        assertTrue(error.getMessage().contains("Transicion no permitida"));
    }

    @Test
    void expiresPaymentAndAllowsFreshPurchase() {
        assertEquals(CANCELADO, machine.transition(ESPERANDO_COMPROBANTE, SOLICITUD_PAGO_EXPIRADA));
        assertEquals(ESPERANDO_METODO_PAGO, machine.transition(CANCELADO, SOLICITUD_PAGO_REINTENTADA));
        assertEquals(IDLE, machine.transition(CANCELADO, NUEVA_COMPRA_INICIADA));
    }

    @Test
    void cannotCancelActiveReservationBeforeBackendReleasesIt() {
        assertFalse(machine.canTransition(ESPERANDO_COMPROBANTE, CANCELACION_SOLICITADA));
        assertEquals(CANCELADO, machine.transition(ESPERANDO_COMPROBANTE, SOLICITUD_PAGO_CANCELADA));
    }

    @Test
    void finalRejectionRequiresVoidedSaleEvent() {
        assertEquals(
                CANCELADO,
                machine.transition(VENTA_PENDIENTE_REVISION, PAGO_RECHAZADO_Y_VENTA_ANULADA));
        assertFalse(machine.canTransition(VENTA_PENDIENTE_REVISION, CANCELACION_SOLICITADA));
    }

    @Test
    void cancellationAfterInitialPaymentValidationRequiresHelp() {
        assertEquals(
                REQUIERE_AYUDA,
                machine.transition(ESPERANDO_METODO_ENTREGA, CANCELACION_SOLICITADA));
        assertEquals(REQUIERE_AYUDA, machine.transition(LISTO_PARA_VENTA, AYUDA_REQUERIDA));
    }

    @Test
    void everyDeclaredStateHasAtLeastOneAllowedEvent() {
        for (CrmWhatsappAgentState state : CrmWhatsappAgentState.values()) {
            assertFalse(machine.allowedEvents(state).isEmpty(), () -> "Sin eventos permitidos: " + state);
            assertFalse(machine.allowedTargets(state).isEmpty(), () -> "Sin destinos permitidos: " + state);
        }
    }

    @Test
    void nullsAreNeverValidTransitions() {
        assertFalse(machine.canTransition(null, CONVERSACION_INICIADA));
        assertFalse(machine.canTransition(IDLE, null));
        assertThrows(NullPointerException.class, () -> machine.transition(null, CONVERSACION_INICIADA));
        assertThrows(NullPointerException.class, () -> machine.transition(IDLE, null));
    }

    @Test
    void terminalFlagDescribesCurrentPurchaseNotSessionReuse() {
        assertTrue(COMPLETADO.isTerminal());
        assertTrue(CANCELADO.isTerminal());
        assertFalse(VENTA_PENDIENTE_REVISION.isTerminal());
        assertEquals(IDLE, machine.transition(COMPLETADO, NUEVA_COMPRA_INICIADA));
    }

    @Test
    void exposesEventsAsImmutableSet() {
        var events = machine.allowedEvents(IDLE);
        assertTrue(events.contains(CONVERSACION_INICIADA));
        assertThrows(UnsupportedOperationException.class, () -> events.add(CrmWhatsappAgentEvent.CARRITO_CONFIRMADO));
    }
}
