package com.sistemapos.sistematextil.services.agent.ai;

import org.springframework.ai.chat.metadata.Usage;

public record AiTokenUsage(Integer inputTokens, Integer outputTokens, Long cachedTokens, Integer totalTokens) {
    public static AiTokenUsage unknown() {
        return new AiTokenUsage(null, null, null, null);
    }

    public static AiTokenUsage from(Usage usage) {
        if (usage == null) return unknown();
        return new AiTokenUsage(usage.getPromptTokens(), usage.getCompletionTokens(),
                usage.getCacheReadInputTokens(), usage.getTotalTokens());
    }
}
