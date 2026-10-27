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
import com.sistemapos.sistematextil.services.agent.ai.AiInvocationAuditService;
import com.sistemapos.sistematextil.services.agent.ai.AiInvocationPhase;
import com.sistemapos.sistematextil.services.agent.ai.AiTokenUsage;

@Service
public class GeminiAgentKnowledgeEmbeddingGateway implements AgentKnowledgeEmbeddingGateway {
    private final CrmWhatsappAiCredentialService credentialService;
    private final String embeddingModel;
    private final int timeoutMs;
    private final AiInvocationAuditService invocationAudit;

    public GeminiAgentKnowledgeEmbeddingGateway(
            CrmWhatsappAiCredentialService credentialService,
            @Value("${crm.whatsapp.ai.gemini.embedding-model:gemini-embedding-001}") String embeddingModel,
            @Value("${crm.whatsapp.ai.gemini.timeout-ms:60000}") int timeoutMs,
            AiInvocationAuditService invocationAudit) {
        this.credentialService = credentialService;
        this.embeddingModel = clean(embeddingModel).isBlank() ? "gemini-embedding-001" : clean(embeddingModel);
        this.timeoutMs = Math.max(10000, timeoutMs);
        this.invocationAudit = invocationAudit;
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
        long started = System.nanoTime();
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
            List<Float> embedding = response.embeddings()
                    .flatMap(items -> items.stream().findFirst())
                    .flatMap(item -> item.values())
                    .filter(values -> !values.isEmpty())
                    .orElseThrow(() -> new AiProviderException("Gemini no devolvio el embedding RAG", true));
            audit(connectionId, credentials, started, null);
            return embedding;
        } catch (AiProviderException error) {
            audit(connectionId, credentials, started, error.getClass().getSimpleName());
            throw error;
        } catch (ApiException error) {
            audit(connectionId, credentials, started, "HTTP_" + error.code());
            int code = error.code();
            throw new AiProviderException("Gemini respondio HTTP " + code + " al consultar el RAG",
                    code == 408 || code == 429 || code >= 500, error);
        } catch (GenAiIOException error) {
            audit(connectionId, credentials, started, error.getClass().getSimpleName());
            throw new AiProviderException("No se pudo conectar con Gemini para consultar el RAG", true, error);
        } catch (RuntimeException error) {
            audit(connectionId, credentials, started, error.getClass().getSimpleName());
            throw new AiProviderException("No se pudo generar el embedding de la consulta RAG", true, error);
        }
    }

    private void audit(Long connectionId, CredentialMaterial credential, long started, String errorCode) {
        long latency = java.time.Duration.ofNanos(System.nanoTime() - started).toMillis();
        var invocation = new AiInvocationAuditService.Invocation(connectionId, null, null, null,
                AiInvocationPhase.EMBEDDING, "GEMINI", embeddingModel, credential.apiKey(), latency, 0);
        if (errorCode == null) invocationAudit.success(invocation, AiTokenUsage.unknown());
        else invocationAudit.failure(invocation, errorCode);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
