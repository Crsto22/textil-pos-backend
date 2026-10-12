package com.sistemapos.sistematextil.services.agent.prompt;

import java.util.Map;

public record AgentMainPrompt(
        String version,
        String systemInstruction,
        String userMessage,
        Map<String, Object> toolContext) {
}
