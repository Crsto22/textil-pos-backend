package com.sistemapos.sistematextil.services.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.sistemapos.sistematextil.services.agent.ai.AgentConversationDecision;
import com.sistemapos.sistematextil.services.agent.ai.AgentDecisionGateway;
import com.sistemapos.sistematextil.services.agent.ai.AgentDecisionIntent;
import com.sistemapos.sistematextil.services.agent.ai.AgentInterpretationResult;

class GeminiConnectionServiceTest {

    @Test
    void currentConnectionTestReturnsAnIsolatedStructuredDecision() {
        AgentDecisionGateway gateway = mock(AgentDecisionGateway.class);
        AgentConversationDecision decision = new AgentConversationDecision(
                AgentDecisionIntent.CART_CHANGE,
                "Comprar dos unidades de Emma",
                "Emma",
                "camel",
                "L",
                2,
                null,
                com.sistemapos.sistematextil.services.agent.ai.AgentCartAction.ADD_PRODUCT,
                "Consultar catalogo y stock",
                98,
                false);
        when(gateway.interpret(org.mockito.ArgumentMatchers.eq(7L), contains("Emma")))
                .thenReturn(new AgentInterpretationResult("GEMINI", "gemini-3.5-flash-lite", "DATABASE", 125, 1,
                        decision));

        GeminiConnectionService service = new GeminiConnectionService(gateway);
        GeminiConnectionService.ConnectionTest result = service.testConnection(7L);

        assertTrue(result.ok());
        assertEquals("GEMINI", result.provider());
        assertEquals("gemini-3.5-flash-lite", result.model());
        assertEquals(1, result.toolCalls());
        assertEquals(decision, result.decision());
        verify(gateway).interpret(org.mockito.ArgumentMatchers.eq(7L), contains("Emma"));
    }
}
