package com.sistemapos.sistematextil.services.ai;

import org.springframework.stereotype.Service;

import com.sistemapos.sistematextil.services.agent.ai.AgentConversationDecision;
import com.sistemapos.sistematextil.services.agent.ai.AgentDecisionGateway;
import com.sistemapos.sistematextil.services.agent.ai.AgentInterpretationResult;

@Service
public class GeminiConnectionService {
    private static final String ISOLATED_CONVERSATION = """
            Cliente: Buenas tardes, quiero ver el modelo Emma color camel en talla L.
            Cliente: Si hay stock deseo llevar dos.
            """;

    private final AgentDecisionGateway decisionGateway;

    public GeminiConnectionService(AgentDecisionGateway decisionGateway) {
        this.decisionGateway = decisionGateway;
    }

    public ConnectionTest testConnection(Long connectionId) {
        AgentInterpretationResult result = decisionGateway.interpret(connectionId, ISOLATED_CONVERSATION);
        return new ConnectionTest(
                true,
                result.provider(),
                result.model(),
                result.latencyMs(),
                result.toolCalls(),
                result.decision());
    }

    public record ConnectionTest(
            boolean ok,
            String provider,
            String model,
            long latencyMs,
            int toolCalls,
            AgentConversationDecision decision) {
    }
}
