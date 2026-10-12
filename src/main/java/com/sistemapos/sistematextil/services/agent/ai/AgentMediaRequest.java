package com.sistemapos.sistematextil.services.agent.ai;

public record AgentMediaRequest(
        MediaType type,
        Integer productId,
        String color) {
    public enum MediaType { SIZE_GUIDE, PRODUCT_IMAGE }
}
