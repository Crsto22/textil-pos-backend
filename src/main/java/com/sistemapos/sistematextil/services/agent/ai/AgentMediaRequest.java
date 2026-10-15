package com.sistemapos.sistematextil.services.agent.ai;

public record AgentMediaRequest(
        MediaType type,
        Integer productId,
        String color,
        boolean suppressIfRecentlySent,
        boolean catalogPreview) {

    public AgentMediaRequest(MediaType type, Integer productId, String color) {
        this(type, productId, color, false, false);
    }

    public AgentMediaRequest(MediaType type, Integer productId, String color, boolean suppressIfRecentlySent) {
        this(type, productId, color, suppressIfRecentlySent, false);
    }

    public enum MediaType { SIZE_GUIDE, PRODUCT_IMAGE }
}
