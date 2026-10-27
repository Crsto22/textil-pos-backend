package com.sistemapos.sistematextil.services.agent.ai;

public record AgentInterpretationResult(
        String provider,
        String model,
        String credentialSource,
        long latencyMs,
        int toolCalls,
        AgentConversationDecision decision,
        AiTokenUsage usage) {
    public AgentInterpretationResult(String provider, String model, String credentialSource, long latencyMs,
            int toolCalls, AgentConversationDecision decision) {
        this(provider, model, credentialSource, latencyMs, toolCalls, decision, AiTokenUsage.unknown());
    }
}
