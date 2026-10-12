package com.sistemapos.sistematextil.services.agent.sizeguide;

import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.sistemapos.sistematextil.services.agent.catalog.AgentCatalogTools;
import com.sistemapos.sistematextil.services.agent.sizeguide.ProductSizeGuideQueryService.BodyMeasurement;
import com.sistemapos.sistematextil.services.agent.sizeguide.ProductSizeGuideQueryService.SizeGuideResult;
import com.sistemapos.sistematextil.services.agent.sizeguide.ProductSizeGuideQueryService.SizeRecommendation;

@Component
public class ProductSizeGuideTools {
    private final ProductSizeGuideQueryService guides;

    public ProductSizeGuideTools(ProductSizeGuideQueryService guides) { this.guides = guides; }

    @Tool(name = "obtener_guia_tallas", description = "Obtiene las medidas estructuradas y verificadas de la guia de un producto. No estima ni inventa medidas.")
    public SizeGuideResult obtenerGuiaTallas(
            @ToolParam(description = "Identificador exacto del producto") Integer productoId,
            ToolContext context) {
        return guides.getGuide(sessionId(context), productoId);
    }

    @Tool(name = "recomendar_talla", description = "Compara medidas corporales de la clienta con la guia estructurada de un producto y valida la disponibilidad en la sucursal. La recomendacion es orientativa.")
    public SizeRecommendation recomendarTalla(
            @ToolParam(description = "Identificador exacto del producto") Integer productoId,
            @ToolParam(description = "Componente solicitado, por ejemplo pantalon o blazer; omitir para todo el conjunto", required = false) String componente,
            @ToolParam(description = "Medidas de la clienta con nombre, valor y unidad CM o IN") List<BodyMeasurement> medidasCorporales,
            @ToolParam(description = "Preferencia opcional: AJUSTADO, NORMAL u HOLGADO", required = false) String preferenciaAjuste,
            @ToolParam(description = "Color elegido para validar la disponibilidad real de la talla", required = false) String color,
            ToolContext context) {
        return guides.recommend(sessionId(context), productoId, componente, medidasCorporales, preferenciaAjuste, color);
    }

    private Long sessionId(ToolContext context) {
        Map<String, Object> values = context == null ? Map.of() : context.getContext();
        Object raw = values.get(AgentCatalogTools.SESSION_ID_CONTEXT_KEY);
        if (raw instanceof Number number) return number.longValue();
        if (raw != null) try { return Long.valueOf(raw.toString()); } catch (NumberFormatException ignored) {}
        throw new IllegalStateException("Falta el contexto seguro de la sesion del agente");
    }
}
