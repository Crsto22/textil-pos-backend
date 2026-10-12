package com.sistemapos.sistematextil.services.agent.ai;

/** Semantic catalog filter selected by Gemini and enforced by Java/MySQL. */
public enum AgentCatalogAvailability {
    ALL,
    PREORDER_ONLY,
    READY_STOCK_ONLY
}
