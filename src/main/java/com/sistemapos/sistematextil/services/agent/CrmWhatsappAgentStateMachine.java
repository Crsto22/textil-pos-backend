package com.sistemapos.sistematextil.services.agent;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.sistemapos.sistematextil.model.CrmWhatsappAgentEvent;
import com.sistemapos.sistematextil.model.CrmWhatsappAgentState;

/**
 * Politica unica de transiciones del flujo de venta del agente Kiments.
 *
 * <p>Una transicion solo debe aplicarse despues de que el servicio responsable
 * haya validado y ejecutado su efecto (reserva, pago, venta o anulacion).</p>
 */
@Component
public class CrmWhatsappAgentStateMachine {

    private static final Map<CrmWhatsappAgentState, Map<CrmWhatsappAgentEvent, CrmWhatsappAgentState>> TRANSITIONS
            = buildTransitions();

    public CrmWhatsappAgentState transition(
            CrmWhatsappAgentState current,
            CrmWhatsappAgentEvent event) {
        Objects.requireNonNull(current, "El estado actual es obligatorio");
        Objects.requireNonNull(event, "El evento es obligatorio");
        CrmWhatsappAgentState next = TRANSITIONS.getOrDefault(current, Map.of()).get(event);
        if (next == null) {
            throw new IllegalStateException(
                    "Transicion no permitida: " + current + " + " + event);
        }
        return next;
    }

    public boolean canTransition(
            CrmWhatsappAgentState current,
            CrmWhatsappAgentEvent event) {
        if (current == null || event == null) return false;
        return TRANSITIONS.getOrDefault(current, Map.of()).containsKey(event);
    }

    public Set<CrmWhatsappAgentEvent> allowedEvents(CrmWhatsappAgentState current) {
        if (current == null) return Set.of();
        Map<CrmWhatsappAgentEvent, CrmWhatsappAgentState> transitions =
                TRANSITIONS.getOrDefault(current, Map.of());
        return transitions.isEmpty()
                ? Set.of()
                : Collections.unmodifiableSet(EnumSet.copyOf(transitions.keySet()));
    }

    public Set<CrmWhatsappAgentState> allowedTargets(CrmWhatsappAgentState current) {
        if (current == null) return Set.of();
        Map<CrmWhatsappAgentEvent, CrmWhatsappAgentState> transitions =
                TRANSITIONS.getOrDefault(current, Map.of());
        if (transitions.isEmpty()) return Set.of();
        EnumSet<CrmWhatsappAgentState> targets = EnumSet.noneOf(CrmWhatsappAgentState.class);
        targets.addAll(transitions.values());
        return Collections.unmodifiableSet(targets);
    }

    private static Map<CrmWhatsappAgentState, Map<CrmWhatsappAgentEvent, CrmWhatsappAgentState>> buildTransitions() {
        EnumMap<CrmWhatsappAgentState, Map<CrmWhatsappAgentEvent, CrmWhatsappAgentState>> result =
                new EnumMap<>(CrmWhatsappAgentState.class);

        register(result, CrmWhatsappAgentState.IDLE,
                entry(CrmWhatsappAgentEvent.CONVERSACION_INICIADA, CrmWhatsappAgentState.EXPLORANDO_PRODUCTOS),
                entry(CrmWhatsappAgentEvent.PRODUCTOS_SOLICITADOS, CrmWhatsappAgentState.EXPLORANDO_PRODUCTOS),
                entry(CrmWhatsappAgentEvent.AYUDA_REQUERIDA, CrmWhatsappAgentState.REQUIERE_AYUDA));

        register(result, CrmWhatsappAgentState.EXPLORANDO_PRODUCTOS,
                entry(CrmWhatsappAgentEvent.PRODUCTOS_SOLICITADOS, CrmWhatsappAgentState.EXPLORANDO_PRODUCTOS),
                entry(CrmWhatsappAgentEvent.PRODUCTO_SELECCIONADO, CrmWhatsappAgentState.ARMANDO_CARRITO),
                entry(CrmWhatsappAgentEvent.CARRITO_ACTUALIZADO, CrmWhatsappAgentState.ARMANDO_CARRITO),
                entry(CrmWhatsappAgentEvent.CANCELACION_SOLICITADA, CrmWhatsappAgentState.CANCELADO),
                entry(CrmWhatsappAgentEvent.AYUDA_REQUERIDA, CrmWhatsappAgentState.REQUIERE_AYUDA));

        register(result, CrmWhatsappAgentState.ARMANDO_CARRITO,
                entry(CrmWhatsappAgentEvent.PRODUCTOS_SOLICITADOS, CrmWhatsappAgentState.EXPLORANDO_PRODUCTOS),
                entry(CrmWhatsappAgentEvent.PRODUCTO_SELECCIONADO, CrmWhatsappAgentState.ARMANDO_CARRITO),
                entry(CrmWhatsappAgentEvent.CARRITO_ACTUALIZADO, CrmWhatsappAgentState.ARMANDO_CARRITO),
                entry(CrmWhatsappAgentEvent.CARRITO_VACIADO, CrmWhatsappAgentState.EXPLORANDO_PRODUCTOS),
                entry(CrmWhatsappAgentEvent.CARRITO_CONFIRMADO, CrmWhatsappAgentState.ESPERANDO_METODO_PAGO),
                entry(CrmWhatsappAgentEvent.CANCELACION_SOLICITADA, CrmWhatsappAgentState.CANCELADO),
                entry(CrmWhatsappAgentEvent.AYUDA_REQUERIDA, CrmWhatsappAgentState.REQUIERE_AYUDA));

        register(result, CrmWhatsappAgentState.ESPERANDO_DATOS_CLIENTE,
                entry(CrmWhatsappAgentEvent.DATOS_CLIENTE_COMPLETADOS, CrmWhatsappAgentState.ESPERANDO_METODO_ENTREGA),
                entry(CrmWhatsappAgentEvent.EDICION_CARRITO_SOLICITADA, CrmWhatsappAgentState.ARMANDO_CARRITO),
                entry(CrmWhatsappAgentEvent.CANCELACION_SOLICITADA, CrmWhatsappAgentState.CANCELADO),
                entry(CrmWhatsappAgentEvent.AYUDA_REQUERIDA, CrmWhatsappAgentState.REQUIERE_AYUDA));

        register(result, CrmWhatsappAgentState.ESPERANDO_METODO_PAGO,
                entry(CrmWhatsappAgentEvent.METODO_PAGO_SELECCIONADO, CrmWhatsappAgentState.ESPERANDO_METODO_PAGO),
                entry(CrmWhatsappAgentEvent.SOLICITUD_PAGO_CREADA, CrmWhatsappAgentState.ESPERANDO_COMPROBANTE),
                entry(CrmWhatsappAgentEvent.EDICION_CARRITO_SOLICITADA, CrmWhatsappAgentState.ARMANDO_CARRITO),
                entry(CrmWhatsappAgentEvent.CANCELACION_SOLICITADA, CrmWhatsappAgentState.CANCELADO),
                entry(CrmWhatsappAgentEvent.AYUDA_REQUERIDA, CrmWhatsappAgentState.REQUIERE_AYUDA));

        register(result, CrmWhatsappAgentState.ESPERANDO_COMPROBANTE,
                entry(CrmWhatsappAgentEvent.COMPROBANTE_RECIBIDO, CrmWhatsappAgentState.VALIDANDO_COMPROBANTE),
                entry(CrmWhatsappAgentEvent.RESERVA_LIBERADA_POR_EDICION, CrmWhatsappAgentState.ARMANDO_CARRITO),
                entry(CrmWhatsappAgentEvent.SOLICITUD_PAGO_EXPIRADA, CrmWhatsappAgentState.CANCELADO),
                entry(CrmWhatsappAgentEvent.SOLICITUD_PAGO_CANCELADA, CrmWhatsappAgentState.CANCELADO),
                entry(CrmWhatsappAgentEvent.AYUDA_REQUERIDA, CrmWhatsappAgentState.REQUIERE_AYUDA));

        register(result, CrmWhatsappAgentState.VALIDANDO_COMPROBANTE,
                entry(CrmWhatsappAgentEvent.COMPROBANTE_APROBADO_INICIALMENTE,
                        CrmWhatsappAgentState.ESPERANDO_DATOS_CLIENTE),
                entry(CrmWhatsappAgentEvent.COMPROBANTE_OBSERVADO,
                        CrmWhatsappAgentState.ESPERANDO_COMPROBANTE),
                entry(CrmWhatsappAgentEvent.COMPROBANTE_RECHAZADO_INICIALMENTE,
                        CrmWhatsappAgentState.ESPERANDO_COMPROBANTE),
                entry(CrmWhatsappAgentEvent.SOLICITUD_PAGO_EXPIRADA, CrmWhatsappAgentState.CANCELADO),
                entry(CrmWhatsappAgentEvent.AYUDA_REQUERIDA, CrmWhatsappAgentState.REQUIERE_AYUDA));

        register(result, CrmWhatsappAgentState.ESPERANDO_METODO_ENTREGA,
                entry(CrmWhatsappAgentEvent.RECOJO_TIENDA_SELECCIONADO, CrmWhatsappAgentState.LISTO_PARA_VENTA),
                entry(CrmWhatsappAgentEvent.ENVIO_SHALOM_SELECCIONADO, CrmWhatsappAgentState.ESPERANDO_DATOS_SHALOM),
                entry(CrmWhatsappAgentEvent.CANCELACION_SOLICITADA, CrmWhatsappAgentState.REQUIERE_AYUDA),
                entry(CrmWhatsappAgentEvent.AYUDA_REQUERIDA, CrmWhatsappAgentState.REQUIERE_AYUDA));

        register(result, CrmWhatsappAgentState.ESPERANDO_DATOS_SHALOM,
                entry(CrmWhatsappAgentEvent.DATOS_SHALOM_COMPLETADOS,
                        CrmWhatsappAgentState.ESPERANDO_CONFIRMACION_AGENCIA_SHALOM),
                entry(CrmWhatsappAgentEvent.PERFIL_SHALOM_REUTILIZADO,
                        CrmWhatsappAgentState.ESPERANDO_DECISION_FILM),
                entry(CrmWhatsappAgentEvent.RECOJO_TIENDA_SELECCIONADO, CrmWhatsappAgentState.LISTO_PARA_VENTA),
                entry(CrmWhatsappAgentEvent.CAMBIO_METODO_ENTREGA_SOLICITADO,
                        CrmWhatsappAgentState.ESPERANDO_METODO_ENTREGA),
                entry(CrmWhatsappAgentEvent.CANCELACION_SOLICITADA, CrmWhatsappAgentState.REQUIERE_AYUDA),
                entry(CrmWhatsappAgentEvent.AYUDA_REQUERIDA, CrmWhatsappAgentState.REQUIERE_AYUDA));

        register(result, CrmWhatsappAgentState.ESPERANDO_CONFIRMACION_AGENCIA_SHALOM,
                entry(CrmWhatsappAgentEvent.AGENCIA_SHALOM_PROPUESTA,
                        CrmWhatsappAgentState.ESPERANDO_CONFIRMACION_AGENCIA_SHALOM),
                entry(CrmWhatsappAgentEvent.AGENCIA_SHALOM_CONFIRMADA,
                        CrmWhatsappAgentState.ESPERANDO_DECISION_FILM),
                entry(CrmWhatsappAgentEvent.CORRECCION_DATOS_SHALOM_SOLICITADA,
                        CrmWhatsappAgentState.ESPERANDO_DATOS_SHALOM),
                entry(CrmWhatsappAgentEvent.RECOJO_TIENDA_SELECCIONADO, CrmWhatsappAgentState.LISTO_PARA_VENTA),
                entry(CrmWhatsappAgentEvent.CAMBIO_METODO_ENTREGA_SOLICITADO,
                        CrmWhatsappAgentState.ESPERANDO_METODO_ENTREGA),
                entry(CrmWhatsappAgentEvent.CANCELACION_SOLICITADA, CrmWhatsappAgentState.REQUIERE_AYUDA),
                entry(CrmWhatsappAgentEvent.AYUDA_REQUERIDA, CrmWhatsappAgentState.REQUIERE_AYUDA));

        register(result, CrmWhatsappAgentState.ESPERANDO_DECISION_FILM,
                entry(CrmWhatsappAgentEvent.FILM_ACEPTADO, CrmWhatsappAgentState.ESPERANDO_PAGO_FILM),
                entry(CrmWhatsappAgentEvent.FILM_RECHAZADO, CrmWhatsappAgentState.LISTO_PARA_VENTA),
                entry(CrmWhatsappAgentEvent.PAGO_FILM_NO_DISPONIBLE, CrmWhatsappAgentState.LISTO_PARA_VENTA),
                entry(CrmWhatsappAgentEvent.CANCELACION_SOLICITADA, CrmWhatsappAgentState.REQUIERE_AYUDA),
                entry(CrmWhatsappAgentEvent.AYUDA_REQUERIDA, CrmWhatsappAgentState.REQUIERE_AYUDA));

        register(result, CrmWhatsappAgentState.ESPERANDO_PAGO_FILM,
                entry(CrmWhatsappAgentEvent.PAGO_FILM_APROBADO, CrmWhatsappAgentState.LISTO_PARA_VENTA),
                entry(CrmWhatsappAgentEvent.PAGO_FILM_EXPIRADO, CrmWhatsappAgentState.LISTO_PARA_VENTA),
                entry(CrmWhatsappAgentEvent.FILM_RECHAZADO, CrmWhatsappAgentState.LISTO_PARA_VENTA),
                entry(CrmWhatsappAgentEvent.CANCELACION_SOLICITADA, CrmWhatsappAgentState.REQUIERE_AYUDA),
                entry(CrmWhatsappAgentEvent.AYUDA_REQUERIDA, CrmWhatsappAgentState.REQUIERE_AYUDA));

        register(result, CrmWhatsappAgentState.LISTO_PARA_VENTA,
                entry(CrmWhatsappAgentEvent.NOTA_VENTA_CREADA,
                        CrmWhatsappAgentState.VENTA_PENDIENTE_REVISION),
                entry(CrmWhatsappAgentEvent.CAMBIO_METODO_ENTREGA_SOLICITADO,
                        CrmWhatsappAgentState.ESPERANDO_METODO_ENTREGA),
                entry(CrmWhatsappAgentEvent.CANCELACION_SOLICITADA, CrmWhatsappAgentState.REQUIERE_AYUDA),
                entry(CrmWhatsappAgentEvent.AYUDA_REQUERIDA, CrmWhatsappAgentState.REQUIERE_AYUDA));

        register(result, CrmWhatsappAgentState.VENTA_PENDIENTE_REVISION,
                entry(CrmWhatsappAgentEvent.PAGO_APROBADO_FINALMENTE, CrmWhatsappAgentState.COMPLETADO),
                entry(CrmWhatsappAgentEvent.PAGO_RECHAZADO_Y_VENTA_ANULADA, CrmWhatsappAgentState.CANCELADO));

        register(result, CrmWhatsappAgentState.COMPLETADO,
                entry(CrmWhatsappAgentEvent.NUEVA_COMPRA_INICIADA, CrmWhatsappAgentState.IDLE));

        register(result, CrmWhatsappAgentState.CANCELADO,
                entry(CrmWhatsappAgentEvent.SOLICITUD_PAGO_REINTENTADA,
                        CrmWhatsappAgentState.ESPERANDO_METODO_PAGO),
                entry(CrmWhatsappAgentEvent.NUEVA_COMPRA_INICIADA, CrmWhatsappAgentState.IDLE));

        register(result, CrmWhatsappAgentState.REQUIERE_AYUDA,
                entry(CrmWhatsappAgentEvent.AYUDA_RESUELTA, CrmWhatsappAgentState.IDLE),
                entry(CrmWhatsappAgentEvent.NUEVA_COMPRA_INICIADA, CrmWhatsappAgentState.IDLE));

        return Collections.unmodifiableMap(result);
    }

    @SafeVarargs
    private static void register(
            EnumMap<CrmWhatsappAgentState, Map<CrmWhatsappAgentEvent, CrmWhatsappAgentState>> result,
            CrmWhatsappAgentState state,
            Map.Entry<CrmWhatsappAgentEvent, CrmWhatsappAgentState>... entries) {
        EnumMap<CrmWhatsappAgentEvent, CrmWhatsappAgentState> transitions =
                new EnumMap<>(CrmWhatsappAgentEvent.class);
        for (Map.Entry<CrmWhatsappAgentEvent, CrmWhatsappAgentState> entry : entries) {
            if (transitions.put(entry.getKey(), entry.getValue()) != null) {
                throw new IllegalStateException("Evento duplicado para " + state + ": " + entry.getKey());
            }
        }
        result.put(state, Collections.unmodifiableMap(transitions));
    }

    private static Map.Entry<CrmWhatsappAgentEvent, CrmWhatsappAgentState> entry(
            CrmWhatsappAgentEvent event,
            CrmWhatsappAgentState target) {
        return Map.entry(event, target);
    }
}
