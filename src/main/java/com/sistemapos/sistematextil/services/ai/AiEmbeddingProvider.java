package com.sistemapos.sistematextil.services.ai;

import java.util.List;

public interface AiEmbeddingProvider {
    List<Float> embedDocument(Long connectionId, String title, String content);
    List<Float> embedQuery(Long connectionId, String query);
    String embeddingModel();
}
