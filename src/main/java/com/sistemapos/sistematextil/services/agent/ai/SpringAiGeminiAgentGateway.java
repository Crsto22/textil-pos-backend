package com.sistemapos.sistematextil.services.agent.ai;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.google.genai.Client;
import com.google.genai.errors.ApiException;
import com.google.genai.errors.GenAiIOException;
import com.google.genai.types.HttpOptions;
import com.sistemapos.sistematextil.services.CrmWhatsappAiCredentialService;
import com.sistemapos.sistematextil.services.CrmWhatsappAiCredentialService.CredentialMaterial;
import com.sistemapos.sistematextil.services.agent.cart.AgentCartTools;
import com.sistemapos.sistematextil.services.agent.catalog.AgentCatalogTools;
import com.sistemapos.sistematextil.services.agent.config.AgentRuntimeConfigService;
import com.sistemapos.sistematextil.services.agent.customer.AgentCustomerTools;
import com.sistemapos.sistematextil.services.agent.fulfillment.AgentFulfillmentTools;
import com.sistemapos.sistematextil.services.agent.knowledge.AgentKnowledgeTools;
import com.sistemapos.sistematextil.services.agent.payment.AgentPaymentTools;
import com.sistemapos.sistematextil.services.agent.promotion.AgentPromotionTools;
import com.sistemapos.sistematextil.services.agent.prompt.AgentMainPrompt;
import com.sistemapos.sistematextil.services.agent.sale.AgentSaleTools;
import com.sistemapos.sistematextil.services.agent.sizeguide.ProductSizeGuideTools;
import com.sistemapos.sistematextil.services.agent.runtime.AgentExecutionContext;
import com.sistemapos.sistematextil.services.agent.runtime.AgentToolCallAuditService;
import com.sistemapos.sistematextil.services.ai.AiProviderException;

@Service
public class SpringAiGeminiAgentGateway implements AgentResponseGateway {
    public static final String TOOLS_ENABLED_CONTEXT_KEY = "agentToolsEnabled";
    private final CrmWhatsappAiCredentialService credentialService;
    private final AgentRuntimeConfigService runtimeConfig;
    private final List<Object> safeTools;
    private final AgentSaleTools saleTools;
    private final AgentToolCallAuditService toolAudit;
    private final AiInvocationAuditService invocationAudit;
    private final int timeoutMs;
    private final int maxOutputTokens;

    public SpringAiGeminiAgentGateway(
            CrmWhatsappAiCredentialService credentialService,
            AgentRuntimeConfigService runtimeConfig,
            AgentCatalogTools catalogTools,
            AgentPromotionTools promotionTools,
            AgentCartTools cartTools,
            AgentCustomerTools customerTools,
            AgentPaymentTools paymentTools,
            AgentFulfillmentTools fulfillmentTools,
            AgentKnowledgeTools knowledgeTools,
            ProductSizeGuideTools sizeGuideTools,
            AgentSaleTools saleTools,
            AgentToolCallAuditService toolAudit,
            AiInvocationAuditService invocationAudit,
            @Value("${crm.whatsapp.ai.gemini.timeout-ms:60000}") int timeoutMs,
            @Value("${crm.whatsapp.agent.response.max-output-tokens:1400}") int maxOutputTokens) {
        this.credentialService = credentialService;
        this.runtimeConfig = runtimeConfig;
        this.safeTools = List.of(catalogTools, promotionTools, cartTools, customerTools, paymentTools,
                fulfillmentTools, knowledgeTools, sizeGuideTools);
        this.saleTools = saleTools;
        this.toolAudit = toolAudit;
        this.invocationAudit = invocationAudit;
        this.timeoutMs = Math.max(10000, timeoutMs);
        this.maxOutputTokens = Math.max(512, maxOutputTokens);
    }

    @Override
    public AgentResponseResult respond(Long connectionId, AgentMainPrompt prompt, int requestedMaxToolCalls) {
        if (connectionId == null || prompt == null) throw new IllegalArgumentException("El prompt del agente es obligatorio");
        CredentialMaterial credentials = resolveCredentials(connectionId);
        int maxToolCalls = Math.max(1, Math.min(requestedMaxToolCalls, 30));
        AtomicInteger toolCalls = new AtomicInteger();
        boolean toolsEnabled = !Boolean.FALSE.equals(prompt.toolContext().get(TOOLS_ENABLED_CONTEXT_KEY));
        ToolCallback[] callbacks = toolsEnabled ? callbacks(connectionId, maxToolCalls, toolCalls) : new ToolCallback[0];
        long started = System.nanoTime();

        try (Client client = Client.builder().apiKey(credentials.apiKey())
                .httpOptions(HttpOptions.builder().timeout(timeoutMs).build()).build()) {
            GoogleGenAiChatOptions options = GoogleGenAiChatOptions.builder()
                    .model(credentials.model()).temperature(0.15).maxOutputTokens(maxOutputTokens).build();
            ChatClient chat = ChatClient.create(GoogleGenAiChatModel.builder()
                    .genAiClient(client).options(options).build());
            var request = chat.prompt()
                    .system(prompt.systemInstruction())
                    .user(prompt.userMessage());
            if (toolsEnabled) request = request.tools((Object[]) callbacks).toolContext(prompt.toolContext());
            var entity = request.call().responseEntity(AgentTurnResponse.class);
            AgentTurnResponse response = entity.entity();
            validate(response);
            long latency = Duration.ofNanos(System.nanoTime() - started).toMillis();
            AiTokenUsage usage = AiTokenUsage.from(entity.response().getMetadata().getUsage());
            audit(prompt, connectionId, credentials, latency, toolCalls.get(), usage, null);
            return new AgentResponseResult("GEMINI", credentials.model(), latency, toolCalls.get(), response, usage);
        } catch (AiProviderException error) {
            audit(prompt, connectionId, credentials,
                    Duration.ofNanos(System.nanoTime() - started).toMillis(), toolCalls.get(), null,
                    error.getClass().getSimpleName());
            throw error;
        } catch (RuntimeException error) {
            audit(prompt, connectionId, credentials,
                    Duration.ofNanos(System.nanoTime() - started).toMillis(), toolCalls.get(), null,
                    error.getClass().getSimpleName());
            throw mapProviderError(error);
        }
    }

    private void audit(AgentMainPrompt prompt, Long connectionId, CredentialMaterial credentials, long latency,
            int toolCalls, AiTokenUsage usage, String errorCode) {
        Long sessionId = number(prompt.toolContext().get(AgentCatalogTools.SESSION_ID_CONTEXT_KEY));
        Long jobId = number(prompt.toolContext().get(AgentExecutionContext.JOB_ID));
        Long conversationId = number(prompt.toolContext().get(AgentExecutionContext.CONVERSATION_ID));
        Object requestedPhase = prompt.toolContext().get(AgentExecutionContext.AI_PHASE);
        AiInvocationPhase phase = "CORRECTION".equals(String.valueOf(requestedPhase))
                ? AiInvocationPhase.CORRECTION : AiInvocationPhase.RESPONSE;
        var invocation = new AiInvocationAuditService.Invocation(connectionId, sessionId, jobId, conversationId,
                phase, "GEMINI", credentials.model(), credentials.apiKey(), latency, toolCalls);
        if (errorCode == null) invocationAudit.success(invocation, usage);
        else invocationAudit.failure(invocation, errorCode);
    }

    private ToolCallback[] callbacks(Long connectionId, int maxToolCalls, AtomicInteger counter) {
        List<Object> tools = new ArrayList<>(safeTools);
        if (runtimeConfig.forConnection(connectionId).automaticSalesEnabled()) tools.add(saleTools);
        ToolCallback[] delegates = MethodToolCallbackProvider.builder().toolObjects(tools.toArray()).build()
                .getToolCallbacks();
        ToolCallback[] guarded = new ToolCallback[delegates.length];
        for (int index = 0; index < delegates.length; index++) {
            ToolCallback delegate = delegates[index];
            guarded[index] = new ToolCallback() {
                @Override public org.springframework.ai.tool.definition.ToolDefinition getToolDefinition() {
                    return delegate.getToolDefinition();
                }
                @Override public org.springframework.ai.tool.metadata.ToolMetadata getToolMetadata() {
                    return delegate.getToolMetadata();
                }
                @Override public String call(String input) { return call(input, null); }
                @Override public String call(String input, ToolContext context) {
                    int ordinal = counter.incrementAndGet();
                    if (ordinal > maxToolCalls) {
                        throw new IllegalStateException("La ejecucion alcanzo el limite seguro de herramientas");
                    }
                    Map<String, Object> values = new LinkedHashMap<>();
                    if (context != null && context.getContext() != null) values.putAll(context.getContext());
                    Object prefix = values.get(AgentCartTools.OPERATION_ID_CONTEXT_KEY);
                    if (prefix != null && !prefix.toString().isBlank()) {
                        values.put(AgentCartTools.OPERATION_ID_CONTEXT_KEY,
                                prefix + ":" + delegate.getToolDefinition().name() + ":" + ordinal);
                    }
                    Long sessionId = number(values.get(AgentCatalogTools.SESSION_ID_CONTEXT_KEY));
                    Long jobId = number(values.get(AgentExecutionContext.JOB_ID));
                    ToolContext secured = new ToolContext(Map.copyOf(values));
                    return toolAudit.execute(sessionId, jobId, ordinal,
                            delegate.getToolDefinition().name(), input, () -> delegate.call(input, secured));
                }
            };
        }
        return guarded;
    }

    private Long number(Object value) {
        if (value instanceof Number number) return number.longValue();
        if (value == null) return null;
        try { return Long.valueOf(value.toString()); }
        catch (NumberFormatException ignored) { return null; }
    }

    private void validate(AgentTurnResponse response) {
        if (response == null || response.customerMessage() == null || response.customerMessage().isBlank()) {
            throw new AiProviderException("Gemini no devolvio un mensaje valido para la clienta", true);
        }
        if (response.customerMessage().length() > 6000) {
            throw new AiProviderException("Gemini devolvio una respuesta demasiado extensa", true);
        }
        if (response.conversationSummary() != null && response.conversationSummary().length() > 4000) {
            throw new AiProviderException("Gemini devolvio un resumen demasiado extenso", true);
        }
        // Los archivos sugeridos por Gemini no se envian directamente. El
        // orquestador usa exclusivamente los medios validados por Java y limita
        // la salida final, por lo que una sugerencia excesiva o incompleta del
        // modelo no debe derribar el job completo.
    }

    private CredentialMaterial resolveCredentials(Long connectionId) {
        try { return credentialService.resolve(connectionId); }
        catch (IllegalStateException error) { throw new AiProviderException(error.getMessage(), false, error); }
    }

    private AiProviderException mapProviderError(RuntimeException error) {
        ApiException api = findCause(error, ApiException.class);
        if (api != null) {
            int code = api.code();
            String message = code == 401 || code == 403 ? "Gemini rechazo la API key o el acceso al modelo"
                    : code == 404 ? "El modelo de Gemini configurado no esta disponible"
                    : "Gemini respondio HTTP " + code;
            return new AiProviderException(message, code == 408 || code == 429 || code >= 500, error);
        }
        if (findCause(error, GenAiIOException.class) != null) {
            return new AiProviderException("No se pudo conectar con Gemini", true, error);
        }
        return new AiProviderException("No se pudo generar la respuesta del agente: " + safeMessage(error), true, error);
    }

    private <T extends Throwable> T findCause(Throwable error, Class<T> type) {
        Throwable current = error;
        while (current != null) {
            if (type.isInstance(current)) return type.cast(current);
            current = current.getCause();
        }
        return null;
    }

    private String safeMessage(Throwable error) {
        return error.getMessage() == null || error.getMessage().isBlank()
                ? error.getClass().getSimpleName() : error.getMessage();
    }
}
