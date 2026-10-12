package com.sistemapos.sistematextil.services.agent.knowledge;

import java.util.List;

public interface AgentKnowledgeEmbeddingGateway {
    List<Float> embedQuery(Long connectionId, String query);

    default List<Float> embedDocument(Long connectionId, String document) {
        return embedQuery(connectionId, document);
    }
}
