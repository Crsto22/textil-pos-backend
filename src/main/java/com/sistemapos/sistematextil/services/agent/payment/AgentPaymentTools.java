package com.sistemapos.sistematextil.services.agent.payment;

import java.util.Map;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.sistemapos.sistematextil.services.agent.cart.AgentCartTools;
import com.sistemapos.sistematextil.services.agent.catalog.AgentCatalogTools;
import com.sistemapos.sistematextil.services.agent.payment.AgentPaymentService.PaymentMethodList;
import com.sistemapos.sistematextil.services.agent.payment.AgentPaymentService.PaymentMethodSelection;
import com.sistemapos.sistematextil.services.agent.payment.AgentPaymentService.PaymentRequestResult;

@Component
public class AgentPaymentTools {
    private final AgentPaymentService payments;

    public AgentPaymentTools(AgentPaymentService payments) {
        this.payments = payments;
    }

    @Tool(name = "listar_metodos_pago", description = "Lista exclusivamente los metodos de pago activos de la sucursal conectada, con sus cuentas e instrucciones verificadas.")
    public PaymentMethodList listarMetodosPago(ToolContext context) {
        return payments.listMethods(sessionId(context));
    }

    @Tool(name = "seleccionar_metodo_pago", description = "Valida y guarda el metodo de pago elegido para la sesion. Si el metodo tiene varias cuentas, usa el identificador exacto elegido por la clienta.")
    public PaymentMethodSelection seleccionarMetodoPago(
            @ToolParam(description = "Identificador exacto devuelto por listar_metodos_pago") Integer metodoPagoId,
            @ToolParam(description = "Identificador de cuenta; omitir si el metodo no tiene cuenta o solo tiene una", required = false) Integer cuentaId,
            ToolContext context) {
        return payments.selectMethod(sessionId(context), metodoPagoId, cuentaId, operationId(context));
    }

    @Tool(name = "crear_solicitud_pago", description = "Crea la solicitud desde el carrito confirmado, reserva stock real y fija un vencimiento exacto de cuatro minutos. No acepta montos, precios ni productos del modelo.")
    public PaymentRequestResult crearSolicitudPago(ToolContext context) {
        return payments.createRequest(sessionId(context), operationId(context));
    }

    private Long sessionId(ToolContext context) {
        Object raw = values(context).get(AgentCatalogTools.SESSION_ID_CONTEXT_KEY);
        if (raw instanceof Number number) return number.longValue();
        if (raw instanceof String text && !text.isBlank()) {
            try {
                return Long.valueOf(text.trim());
            } catch (NumberFormatException ignored) {
                // Return the stable context error below.
            }
        }
        throw new IllegalStateException("Falta el contexto seguro de la sesion del agente");
    }

    private String operationId(ToolContext context) {
        Object raw = values(context).get(AgentCartTools.OPERATION_ID_CONTEXT_KEY);
        if (raw == null || raw.toString().isBlank()) {
            throw new IllegalStateException("Falta el identificador seguro de la operacion del agente");
        }
        return raw.toString().trim();
    }

    private Map<String, Object> values(ToolContext context) {
        return context == null ? Map.of() : context.getContext();
    }
}
