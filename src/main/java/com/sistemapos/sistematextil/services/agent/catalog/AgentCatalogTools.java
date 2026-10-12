package com.sistemapos.sistematextil.services.agent.catalog;

import java.util.Map;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.sistemapos.sistematextil.services.agent.catalog.AgentCatalogQueryService.ProductDetail;
import com.sistemapos.sistematextil.services.agent.catalog.AgentCatalogQueryService.ProductImageResult;
import com.sistemapos.sistematextil.services.agent.catalog.AgentCatalogQueryService.ProductSearchResult;
import com.sistemapos.sistematextil.services.agent.catalog.AgentCatalogQueryService.StockResult;
import com.sistemapos.sistematextil.services.agent.catalog.AgentCatalogQueryService.VariantResult;
import com.sistemapos.sistematextil.services.agent.ai.AgentCatalogAvailability;

@Component
public class AgentCatalogTools {
    public static final String SESSION_ID_CONTEXT_KEY = "agentSessionId";

    private final AgentCatalogQueryService catalog;

    public AgentCatalogTools(AgentCatalogQueryService catalog) {
        this.catalog = catalog;
    }

    @Tool(name = "buscar_productos", description = "Busca productos reales con stock en la sucursal de la conversacion. Permite listar todos, solo preventa o solo productos que no son preventa. Devuelve precio, descripcion y tallas disponibles agrupadas por color. Usala para nombres aproximados, errores de escritura o consultas libres. No inventes identificadores.")
    public ProductSearchResult buscarProductos(
            @ToolParam(description = "Texto libre de la clienta, por ejemplo: quiero ver el Alice a rayas") String consulta,
            @ToolParam(description = "Cantidad maxima de resultados, entre 1 y 20", required = false) Integer limite,
            @ToolParam(description = "Filtro semantico: ALL, PREORDER_ONLY o READY_STOCK_ONLY", required = false) AgentCatalogAvailability disponibilidad,
            ToolContext context) {
        return catalog.searchProducts(sessionId(context), consulta, limite, disponibilidad);
    }

    @Tool(name = "obtener_producto", description = "Obtiene los datos verificados de un producto previamente encontrado: descripcion, preventa, guia, colores, tallas, precio y stock de la sucursal.")
    public ProductDetail obtenerProducto(
            @ToolParam(description = "Identificador exacto devuelto por buscar_productos") Integer productoId,
            ToolContext context) {
        return catalog.getProduct(sessionId(context), productoId);
    }

    @Tool(name = "consultar_variantes", description = "Lista variantes reales de un producto en la sucursal y permite filtrar por color y talla. Devuelve el id de variante necesario para consultar stock.")
    public VariantResult consultarVariantes(
            @ToolParam(description = "Identificador exacto del producto") Integer productoId,
            @ToolParam(description = "Color solicitado; omitir si aun no se conoce", required = false) String color,
            @ToolParam(description = "Talla solicitada; omitir si aun no se conoce", required = false) String talla,
            ToolContext context) {
        return catalog.getVariants(sessionId(context), productoId, color, talla);
    }

    @Tool(name = "consultar_stock", description = "Consulta el stock actual de una variante exclusivamente en la sucursal de la conexion WhatsApp y valida si alcanza para la cantidad solicitada.")
    public StockResult consultarStock(
            @ToolParam(description = "Identificador exacto devuelto por consultar_variantes") Integer varianteId,
            @ToolParam(description = "Cantidad que desea comprar la clienta", required = false) Integer cantidad,
            ToolContext context) {
        return catalog.getStock(sessionId(context), varianteId, cantidad);
    }

    @Tool(name = "obtener_imagen_producto", description = "Obtiene la imagen global y, cuando existen, las imagenes del color solicitado para un producto validado en la sucursal.")
    public ProductImageResult obtenerImagenProducto(
            @ToolParam(description = "Identificador exacto del producto") Integer productoId,
            @ToolParam(description = "Color cuya imagen solicito la clienta; omitir para imagen general", required = false) String color,
            ToolContext context) {
        return catalog.getProductImage(sessionId(context), productoId, color);
    }

    private Long sessionId(ToolContext context) {
        Map<String, Object> values = context == null ? Map.of() : context.getContext();
        Object raw = values.get(SESSION_ID_CONTEXT_KEY);
        if (raw instanceof Number number) return number.longValue();
        if (raw instanceof String text && !text.isBlank()) {
            try {
                return Long.valueOf(text.trim());
            } catch (NumberFormatException ignored) {
                // Report a stable error without exposing implementation details to the model.
            }
        }
        throw new IllegalStateException("Falta el contexto seguro de la sesion del agente");
    }
}
