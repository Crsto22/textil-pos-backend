package com.sistemapos.sistematextil.services.agent.ai;

import com.sistemapos.sistematextil.services.agent.prompt.AgentMainPrompt;

public interface AgentResponseGateway {
    AgentResponseResult respond(Long connectionId, AgentMainPrompt prompt, int maxToolCalls);

    record AgentResponseResult(String provider, String model, long latencyMs, int toolCalls,
            AgentTurnResponse response) {
    }
}
