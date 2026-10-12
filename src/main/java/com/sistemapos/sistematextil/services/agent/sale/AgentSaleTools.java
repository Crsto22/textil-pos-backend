package com.sistemapos.sistematextil.services.agent.sale;

import java.util.Map;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import com.sistemapos.sistematextil.services.agent.cart.AgentCartTools;
import com.sistemapos.sistematextil.services.agent.catalog.AgentCatalogTools;
import com.sistemapos.sistematextil.services.agent.sale.AgentSaleService.SaleResult;

@Component
public class AgentSaleTools {
    private final AgentSaleService sales;

    public AgentSaleTools(AgentSaleService sales) {
        this.sales = sales;
    }

    @Tool(name = "generar_nota_venta", description = "Genera una unica NOTA DE VENTA cuando el comprobante fue aprobado inicialmente y la entrega esta completa. Usa exclusivamente el pedido, pago, reserva y entrega validados por el backend; no acepta precios ni productos del modelo.")
    public SaleResult generarNotaVenta(ToolContext context) {
        return sales.createSalesNote(sessionId(context), operationId(context));
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
