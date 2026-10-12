package com.sistemapos.sistematextil.services.agent.promotion;

import java.util.Map;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.sistemapos.sistematextil.services.agent.catalog.AgentCatalogTools;
import com.sistemapos.sistematextil.services.agent.promotion.AgentPromotionQueryService.CartPromotionValidation;
import com.sistemapos.sistematextil.services.agent.promotion.AgentPromotionQueryService.PromotionDetail;
import com.sistemapos.sistematextil.services.agent.promotion.AgentPromotionQueryService.PromotionSearchResult;

@Component
public class AgentPromotionTools {
    private final AgentPromotionQueryService promotions;

    public AgentPromotionTools(AgentPromotionQueryService promotions) {
        this.promotions = promotions;
    }

    @Tool(name = "buscar_promociones", description = "Busca promociones vigentes y con stock en la sucursal de la conversacion. Entiende nombres libres como 'si llevo dos Emma', productos participantes y referencias como 'combo 42'.")
    public PromotionSearchResult buscarPromociones(
            @ToolParam(description = "Consulta libre de la clienta sobre combos, descuentos o dos unidades iguales") String consulta,
            @ToolParam(description = "Cantidad maxima de resultados, entre 1 y 20", required = false) Integer limite,
            ToolContext context) {
        return promotions.searchPromotions(sessionId(context), consulta, limite);
    }

    @Tool(name = "obtener_promocion", description = "Obtiene una promocion vigente previamente encontrada, sus productos y cantidades, precio combo, ahorro y disponibilidad en la sucursal.")
    public PromotionDetail obtenerPromocion(
            @ToolParam(description = "Identificador exacto devuelto por buscar_promociones") Integer promocionId,
            ToolContext context) {
        return promotions.getPromotion(sessionId(context), promocionId);
    }

    @Tool(name = "validar_promocion_carrito", description = "Valida sin modificar el carrito activo. Comprueba productos, cantidades, stock y ahorro para una promocion concreta o para todas las promociones vigentes.")
    public CartPromotionValidation validarPromocionCarrito(
            @ToolParam(description = "Identificador de la promocion a validar; omitir para encontrar las aplicables al carrito", required = false) Integer promocionId,
            ToolContext context) {
        return promotions.validateCart(sessionId(context), promocionId);
    }

    private Long sessionId(ToolContext context) {
        Map<String, Object> values = context == null ? Map.of() : context.getContext();
        Object raw = values.get(AgentCatalogTools.SESSION_ID_CONTEXT_KEY);
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
}
