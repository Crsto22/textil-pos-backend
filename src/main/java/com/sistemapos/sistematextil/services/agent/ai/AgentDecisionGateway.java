package com.sistemapos.sistematextil.services.agent.ai;

public interface AgentDecisionGateway {
    AgentInterpretationResult interpret(Long connectionId, String conversation);
}
