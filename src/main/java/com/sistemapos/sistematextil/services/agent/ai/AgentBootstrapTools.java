package com.sistemapos.sistematextil.services.agent.ai;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.ai.tool.annotation.Tool;

public final class AgentBootstrapTools {
    public static final String TOOL_NAME = "consultar_capacidades_agente";

    private final AtomicInteger calls = new AtomicInteger();

    @Tool(name = TOOL_NAME, description = "Devuelve las capacidades habilitadas para esta prueba aislada del agente Kiments.")
    public AgentCapabilities consultarCapacidadesAgente() {
        calls.incrementAndGet();
        return new AgentCapabilities(
                "INTERPRETACION_AISLADA",
                List.of("INTERPRETAR_CONVERSACION", "DEVOLVER_DECISION_ESTRUCTURADA"),
                false,
                false);
    }

    public int callCount() {
        return calls.get();
    }

    public record AgentCapabilities(
            String phase,
            List<String> capabilities,
            boolean mutationsAllowed,
            boolean realMessagesAllowed) {
    }
}
