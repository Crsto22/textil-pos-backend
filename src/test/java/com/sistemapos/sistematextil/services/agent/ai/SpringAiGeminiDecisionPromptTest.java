package com.sistemapos.sistematextil.services.agent.ai;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SpringAiGeminiDecisionPromptTest {
    @Test
    void compactPromptPreservesEveryCommercialAndCartAction() {
        String prompt = SpringAiGeminiDecisionGateway.compactSystemPrompt();
        for (AgentCommercialAction action : AgentCommercialAction.values()) {
            assertTrue(prompt.contains(action.name()), action.name());
        }
        for (AgentCartAction action : AgentCartAction.values()) {
            assertTrue(prompt.contains(action.name()), action.name());
        }
    }
}
