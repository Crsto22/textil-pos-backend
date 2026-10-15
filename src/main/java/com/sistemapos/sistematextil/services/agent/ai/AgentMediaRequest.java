package com.sistemapos.sistematextil.services.agent.ai;

public record AgentMediaRequest(
        MediaType type,
        Integer productId,
        String color,
        boolean suppressIfRecentlySent) {

    public AgentMediaRequest(MediaType type, Integer productId, String color) {
        this(type, productId, color, false);
    }

    public enum MediaType { SIZE_GUIDE, PRODUCT_IMAGE }
}
