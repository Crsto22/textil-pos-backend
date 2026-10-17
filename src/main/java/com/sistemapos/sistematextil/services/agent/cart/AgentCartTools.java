package com.sistemapos.sistematextil.services.agent.cart;

import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.sistemapos.sistematextil.services.agent.cart.AgentCartService.CartResult;
import com.sistemapos.sistematextil.services.agent.cart.AgentCartService.ComboSelection;
import com.sistemapos.sistematextil.services.agent.catalog.AgentCatalogTools;

@Component
public class AgentCartTools {
    public static final String OPERATION_ID_CONTEXT_KEY = "agentOperationId";

    private final AgentCartService cart;

    public AgentCartTools(AgentCartService cart) {
        this.cart = cart;
    }

    @Tool(name = "agregar_producto_carrito", description = "Agrega una cantidad de una variante validada al carrito persistente. El backend obtiene precio y stock reales; nunca solicites ni envies un precio.")
    public CartResult agregarProducto(
            @ToolParam(description = "Identificador exacto devuelto por consultar_variantes") Integer varianteId,
            @ToolParam(description = "Cantidad positiva que desea la clienta") Integer cantidad,
            ToolContext context) {
        return cart.addProduct(sessionId(context), varianteId, cantidad, operationId(context));
    }

    @Tool(name = "agregar_combo_carrito", description = "Agrega un combo vigente después de conocer color y talla de sus dos unidades. Envia variantes y cantidades, nunca precios.")
    public CartResult agregarCombo(
            @ToolParam(description = "Identificador exacto devuelto por buscar_promociones u obtener_promocion") Integer promocionId,
            @ToolParam(description = "Variantes elegidas para cumplir exactamente los productos y cantidades del combo") List<ComboSelection> selecciones,
            ToolContext context) {
        return cart.addCombo(sessionId(context), promocionId, selecciones, operationId(context));
    }

    @Tool(name = "cambiar_cantidad_carrito", description = "Reemplaza la cantidad de una variante que ya existe en el carrito y recalcula precios, stock y promociones.")
    public CartResult cambiarCantidad(
            @ToolParam(description = "Identificador de la variante presente en el carrito") Integer varianteId,
            @ToolParam(description = "Nueva cantidad total positiva") Integer cantidad,
            ToolContext context) {
        return cart.changeQuantity(sessionId(context), varianteId, cantidad, operationId(context));
    }

    @Tool(name = "cambiar_variante_carrito", description = "Cambia de forma atomica la talla o el color de un producto ya agregado. La variante nueva debe pertenecer al mismo producto y tener stock.")
    public CartResult cambiarVariante(
            @ToolParam(description = "Identificador de la variante que actualmente esta en el carrito") Integer varianteActualId,
            @ToolParam(description = "Identificador exacto de la nueva variante devuelta por consultar_variantes") Integer varianteNuevaId,
            ToolContext context) {
        return cart.changeVariant(sessionId(context), varianteActualId, varianteNuevaId, operationId(context));
    }

    @Tool(name = "reemplazar_producto_carrito", description = "Reemplaza de forma atomica un producto del carrito por una variante validada de otro modelo, conservando la cantidad. Solo aplica antes de registrar la venta.")
    public CartResult reemplazarProducto(
            @ToolParam(description = "Identificador de la variante actualmente presente en el carrito") Integer varianteActualId,
            @ToolParam(description = "Identificador exacto de la variante del nuevo modelo devuelta por consultar_variantes") Integer varianteNuevaId,
            ToolContext context) {
        return cart.replaceProduct(sessionId(context), varianteActualId, varianteNuevaId, operationId(context));
    }

    @Tool(name = "eliminar_producto_carrito", description = "Elimina una variante del carrito y recalcula todas las promociones.")
    public CartResult eliminarProducto(
            @ToolParam(description = "Identificador de la variante presente en el carrito") Integer varianteId,
            ToolContext context) {
        return cart.removeProduct(sessionId(context), varianteId, operationId(context));
    }

    @Tool(name = "vaciar_carrito", description = "Elimina todos los productos y promociones del carrito activo.")
    public CartResult vaciarCarrito(ToolContext context) {
        return cart.emptyCart(sessionId(context), operationId(context));
    }

    @Tool(name = "recalcular_carrito", description = "Vuelve a leer precios, promociones y stock reales y devuelve el carrito actualizado sin confiar en datos calculados por el modelo.")
    public CartResult recalcularCarrito(ToolContext context) {
        return cart.recalculate(sessionId(context), operationId(context));
    }

    @Tool(name = "confirmar_carrito", description = "Valida nuevamente stock, precios y promociones, cierra el carrito y avanza el flujo para solicitar datos del cliente.")
    public CartResult confirmarCarrito(ToolContext context) {
        return cart.confirm(sessionId(context), operationId(context));
    }

    private Long sessionId(ToolContext context) {
        return longContext(context, AgentCatalogTools.SESSION_ID_CONTEXT_KEY,
                "Falta el contexto seguro de la sesion del agente");
    }

    private String operationId(ToolContext context) {
        Map<String, Object> values = context == null ? Map.of() : context.getContext();
        Object raw = values.get(OPERATION_ID_CONTEXT_KEY);
        if (raw == null || raw.toString().isBlank()) {
            throw new IllegalStateException("Falta el identificador seguro de la operacion del agente");
        }
        return raw.toString().trim();
    }

    private Long longContext(ToolContext context, String key, String errorMessage) {
        Map<String, Object> values = context == null ? Map.of() : context.getContext();
        Object raw = values.get(key);
        if (raw instanceof Number number) return number.longValue();
        if (raw instanceof String text && !text.isBlank()) {
            try {
                return Long.valueOf(text.trim());
            } catch (NumberFormatException ignored) {
                // Return the stable context error below.
            }
        }
        throw new IllegalStateException(errorMessage);
    }
}
