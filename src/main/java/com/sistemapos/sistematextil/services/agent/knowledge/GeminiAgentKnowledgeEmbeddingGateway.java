package com.sistemapos.sistematextil.services.agent.knowledge;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.google.genai.Client;
import com.google.genai.errors.ApiException;
import com.google.genai.errors.GenAiIOException;
import com.google.genai.types.EmbedContentConfig;
import com.google.genai.types.EmbedContentResponse;
import com.google.genai.types.HttpOptions;
import com.sistemapos.sistematextil.services.CrmWhatsappAiCredentialService;
import com.sistemapos.sistematextil.services.CrmWhatsappAiCredentialService.CredentialMaterial;
import com.sistemapos.sistematextil.services.ai.AiProviderException;

@Service
public class GeminiAgentKnowledgeEmbeddingGateway implements AgentKnowledgeEmbeddingGateway {
    private final CrmWhatsappAiCredentialService credentialService;
    private final String embeddingModel;
    private final int timeoutMs;

    public GeminiAgentKnowledgeEmbeddingGateway(
            CrmWhatsappAiCredentialService credentialService,
            @Value("${crm.whatsapp.ai.gemini.embedding-model:gemini-embedding-001}") String embeddingModel,
            @Value("${crm.whatsapp.ai.gemini.timeout-ms:60000}") int timeoutMs) {
        this.credentialService = credentialService;
        this.embeddingModel = clean(embeddingModel).isBlank() ? "gemini-embedding-001" : clean(embeddingModel);
        this.timeoutMs = Math.max(10000, timeoutMs);
    }

    @Override
    public List<Float> embedQuery(Long connectionId, String query) {
        return embed(connectionId, query, "RETRIEVAL_QUERY");
    }

    @Override
    public List<Float> embedDocument(Long connectionId, String document) {
        return embed(connectionId, document, "RETRIEVAL_DOCUMENT");
    }

    private List<Float> embed(Long connectionId, String content, String taskType) {
        CredentialMaterial credentials;
        try {
            credentials = credentialService.resolve(connectionId);
        } catch (IllegalStateException error) {
            throw new AiProviderException(error.getMessage(), false, error);
        }
        try (Client client = Client.builder()
                .apiKey(credentials.apiKey())
                .httpOptions(HttpOptions.builder().timeout(timeoutMs).build())
                .build()) {
            EmbedContentResponse response = client.models.embedContent(
                    embeddingModel,
                    clean(content),
                    EmbedContentConfig.builder()
                            .taskType(taskType)
                            .outputDimensionality(256)
                            .build());
            return response.embeddings()
                    .flatMap(items -> items.stream().findFirst())
                    .flatMap(item -> item.values())
                    .filter(values -> !values.isEmpty())
                    .orElseThrow(() -> new AiProviderException("Gemini no devolvio el embedding RAG", true));
        } catch (AiProviderException error) {
            throw error;
        } catch (ApiException error) {
            int code = error.code();
            throw new AiProviderException("Gemini respondio HTTP " + code + " al consultar el RAG",
                    code == 408 || code == 429 || code >= 500, error);
        } catch (GenAiIOException error) {
            throw new AiProviderException("No se pudo conectar con Gemini para consultar el RAG", true, error);
        } catch (RuntimeException error) {
            throw new AiProviderException("No se pudo generar el embedding de la consulta RAG", true, error);
        }
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
