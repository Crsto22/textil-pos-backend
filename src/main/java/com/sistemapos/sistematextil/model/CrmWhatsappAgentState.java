package com.sistemapos.sistematextil.model;

/**
 * Estado comercial persistible de una sesion del agente de WhatsApp.
 *
 * <p>El modelo de lenguaje no modifica este valor directamente. El backend
 * aplica un {@link CrmWhatsappAgentEvent} mediante la maquina de estados.</p>
 */
public enum CrmWhatsappAgentState {
    IDLE(false),
    EXPLORANDO_PRODUCTOS(false),
    ARMANDO_CARRITO(false),
    ESPERANDO_DATOS_CLIENTE(false),
    ESPERANDO_METODO_PAGO(false),
    ESPERANDO_COMPROBANTE(false),
    VALIDANDO_COMPROBANTE(false),
    ESPERANDO_METODO_ENTREGA(false),
    ESPERANDO_DATOS_SHALOM(false),
    ESPERANDO_CONFIRMACION_AGENCIA_SHALOM(false),
    ESPERANDO_PEDIDO_ANTERIOR(false),
    ESPERANDO_DECISION_FILM(false),
    ESPERANDO_PAGO_FILM(false),
    LISTO_PARA_VENTA(false),
    VENTA_PENDIENTE_REVISION(false),
    COMPLETADO(true),
    CANCELADO(true),
    REQUIERE_AYUDA(false);

    private final boolean terminal;

    CrmWhatsappAgentState(boolean terminal) {
        this.terminal = terminal;
    }

    public boolean isTerminal() {
        return terminal;
    }
}
