package com.sistemapos.sistematextil.services.agent.knowledge;

import java.util.Map;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.sistemapos.sistematextil.services.agent.catalog.AgentCatalogTools;
import com.sistemapos.sistematextil.services.agent.knowledge.AgentKnowledgeRetriever.KnowledgeSearchResult;

@Component
public class AgentKnowledgeTools {
    private final AgentKnowledgeRetriever knowledge;

    public AgentKnowledgeTools(AgentKnowledgeRetriever knowledge) {
        this.knowledge = knowledge;
    }

    @Tool(name = "consultar_informacion_negocio", description = "Busca exclusivamente politicas, horarios, ubicacion, envios, cuidados y preguntas frecuentes en los articulos verificados de Kiments. Redacta despues una respuesta breve y natural usando solo las fuentes devueltas; si no hay fuentes, indica que no tienes informacion suficiente. Nunca uses esta herramienta para precios, stock, productos, promociones, carrito, pagos o ventas.")
    public KnowledgeSearchResult consultarInformacionNegocio(
            @ToolParam(description = "Pregunta informativa completa de la clienta") String pregunta,
            ToolContext context) {
        return knowledge.searchForSession(sessionId(context), pregunta);
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
