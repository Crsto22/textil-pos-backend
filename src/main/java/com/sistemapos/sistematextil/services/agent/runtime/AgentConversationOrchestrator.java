package com.sistemapos.sistematextil.services.agent.runtime;

import java.time.LocalDateTime;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.sistemapos.sistematextil.services.agent.ai.AgentResponseGateway;
import com.sistemapos.sistematextil.services.agent.ai.AgentCommercialAction;
import com.sistemapos.sistematextil.services.agent.ai.AgentDecisionIntent;
import com.sistemapos.sistematextil.services.agent.ai.AgentMediaRequest;
import com.sistemapos.sistematextil.services.agent.batch.AgentJobCoordinator;
import com.sistemapos.sistematextil.services.agent.batch.AgentJobStore;
import com.sistemapos.sistematextil.services.agent.batch.AgentJobStore.GeneratedTurn;
import com.sistemapos.sistematextil.services.agent.batch.AgentJobStore.JobLease;
import com.sistemapos.sistematextil.services.agent.batch.AgentMessageBatch;
import com.sistemapos.sistematextil.services.agent.batch.AgentMessageBatchFactory;
import com.sistemapos.sistematextil.services.agent.cart.AgentCartTools;
import com.sistemapos.sistematextil.services.agent.config.AgentRuntimeConfigService;
import com.sistemapos.sistematextil.services.agent.context.AgentContextFactory;
import com.sistemapos.sistematextil.services.agent.prompt.AgentMainPrompt;
import com.sistemapos.sistematextil.services.agent.prompt.AgentMainPromptFactory;
import com.sistemapos.sistematextil.services.agent.runtime.AgentTurnFinalizer.StaleAgentTurnException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AgentConversationOrchestrator {
    static final String POST_SALE_CHANGE_MESSAGE = """
            Entiendo. Como tu pedido ya fue confirmado, no puedo realizar cambios de color, talla, modelo, cantidad, pago o entrega desde el chat automatico.

            Una asesora revisara tu solicitud y te indicara si el cambio es posible. Ten en cuenta que puede aplicarse una penalidad de S/10 por prenda.

            Para recibir ayuda con tu caso, comunícate con nuestra asesora al 933918047.""";
    static final String GENERAL_IMAGE_MESSAGE = """
            Recibí tu imagen, bella 💛
            Para confirmar correctamente el producto, indícame el nombre del modelo que aparece en la foto.""";
    static final String GENERAL_DOCUMENT_MESSAGE = """
            Recibí tu archivo, bella 💛
            Por el momento no puedo revisar su contenido directamente. Cuéntame brevemente qué contiene o qué deseas consultar.""";

    private final AgentProductPriceResponseValidator priceValidator = new AgentProductPriceResponseValidator();
    private final AgentPromotionResponseValidator promotionValidator = new AgentPromotionResponseValidator();
    private final AgentJobCoordinator coordinator;
    private final AgentJobStore jobStore;
    private final AgentMessageBatchFactory batchFactory;
    private final AgentContextFactory contextFactory;
    private final AgentMainPromptFactory promptFactory;
    private final AgentVerifiedGroundingService groundingService;
    private final AgentResponseGateway gateway;
    private final AgentRuntimeConfigService runtimeConfig;
    private final AgentTurnFinalizer finalizer;

    public Outcome process(JobLease lease) {
        if (lease == null || !coordinator.isCurrent(lease)) return Outcome.STALE;
        try {
            GeneratedTurn turn = jobStore.findGeneratedTurn(lease.jobId(), lease.workerId())
                    .orElseGet(() -> generate(lease));
            if (!coordinator.isCurrent(lease)) return Outcome.STALE;
            finalizer.finalizeTurn(lease, turn);
            return coordinator.complete(lease) ? Outcome.COMPLETED : Outcome.STALE;
        } catch (StaleAgentTurnException stale) {
            return Outcome.STALE;
        } catch (RuntimeException error) {
            AgentJobStore.FailureResult result = coordinator.fail(lease, error);
            String failure = safeMessage(error);
            if (result == AgentJobStore.FailureResult.FAILED_PERMANENTLY) {
                log.error("Job del agente {} fallo tras {} intentos: {}",
                        lease.jobId(), lease.attempt(), failure, error);
                try {
                    finalizer.failPermanently(lease, failure);
                } catch (RuntimeException recoveryError) {
                    // El fallo del aviso de recuperacion nunca debe interrumpir el
                    // scheduler ni ocultar el error original del job.
                    log.error("No se pudo finalizar de forma segura el job {}: {}",
                            lease.jobId(), safeMessage(recoveryError), recoveryError);
                }
            } else if (result == AgentJobStore.FailureResult.RETRY_SCHEDULED) {
                log.warn("Job del agente {} fallo en el intento {} y se reintentara: {}",
                        lease.jobId(), lease.attempt(), failure);
            }
            return result == AgentJobStore.FailureResult.RETRY_SCHEDULED ? Outcome.RETRY
                    : result == AgentJobStore.FailureResult.FAILED_PERMANENTLY ? Outcome.FAILED : Outcome.STALE;
        }
    }

    private GeneratedTurn generate(JobLease lease) {
        var context = contextFactory.build(lease.sessionId(), lease.anchorMessageId());
        var batch = batchFactory.build(lease.sessionId(), context.conversationId(), lease.anchorMessageId());
        if (isPostSaleChangeRequest(context, batch)) {
            GeneratedTurn deterministic = new GeneratedTurn(
                    POST_SALE_CHANGE_MESSAGE, context.previousSummary(), true,
                    "JAVA_DETERMINISTIC", "post-sale-change-guard", 0L, 0,
                    context.fingerprint(), List.of());
            return persistGeneratedTurn(lease, deterministic);
        }
        String mediaMessage = generalMediaMessage(batch, context.state());
        if (mediaMessage != null) {
            GeneratedTurn deterministic = new GeneratedTurn(
                    mediaMessage, context.previousSummary(), false,
                    "JAVA_DETERMINISTIC", "general-media-routing", 0L, 0,
                    context.fingerprint(), List.of());
            return persistGeneratedTurn(lease, deterministic);
        }
        AgentMainPrompt base = promptFactory.create(context);
        String conversation = base.userMessage() + "\n\nLOTE_ACTUAL_AGRUPADO:\n" + batch.combinedInput();
        var grounding = groundingService.ground(context.connectionId(), lease.sessionId(), lease.jobId(), conversation);
        if (grounding.customerMessage() != null && !grounding.customerMessage().isBlank()) {
            GeneratedTurn deterministic = new GeneratedTurn(
                    grounding.customerMessage(), context.previousSummary(),
                    grounding.decision().requiresHumanHelp(), "JAVA_DETERMINISTIC", "state-machine", 0L,
                    grounding.verifiedToolCalls(), context.fingerprint(), grounding.media());
            return persistGeneratedTurn(lease, deterministic);
        }
        Map<String, Object> toolContext = new LinkedHashMap<>(base.toolContext());
        toolContext.put(AgentExecutionContext.JOB_ID, lease.jobId());
        toolContext.put(AgentCartTools.OPERATION_ID_CONTEXT_KEY, "agent-job-" + lease.jobId());
        AgentMainPrompt prompt = new AgentMainPrompt(base.version(), base.systemInstruction(),
                conversation + """

                        EVIDENCIA_VERIFICADA_DEL_BACKEND:
                        %s

                        REGLA DE SALIDA:
                        - Usa esta evidencia como unica fuente para hechos del negocio.
                        - No digas que estas buscando, revisando o verificando: la consulta ya fue ejecutada.
                        - No escribas marcadores como [lista], datos pendientes ni productos no presentes en la evidencia.
                        - Solo afirma que adjuntas, compartes, envias o muestras fotos cuando verifiedMediaCount es
                          mayor que cero. Si vale cero, no prometas ni anuncies imagenes que no seran enviadas.
                        - Cuando exista selectedProduct, responde ahora con sus datos reales.
                        - Todo producto que menciones o muestres debe incluir su precio real de la evidencia.
                          No omitas precios, no los agrupes si tienen importes distintos y no inventes montos.
                        - Cuando exista availabilityByColor, presenta cada color con sus tallas disponibles reales.
                        - Respeta el filtro availability y no mezcles preventas con productos listos.
                        - El campo preorder de la evidencia es la unica autoridad para afirmar que un producto esta
                          en preventa. Si preorder=false, no lo llames preventa ni comuniques una antigua fecha de
                          despacho aunque la descripcion comercial conserve texto historico.
                        - Para preventas, di "despachos a partir de [fecha]". La fecha es referencial y el despacho
                          depende del volumen de pedidos. Nunca digas "llega el [fecha]" ni garantices que saldra
                          exactamente ese dia.
                        - Si la clienta solicita todos los productos de un grupo, incluye todos los productos presentes
                          en la evidencia; no agregues modelos, tipos de prenda ni variantes que no aparezcan alli.
                        - Cuando exista promotions, responde la consulta usando esa lista real. Solo puedes afirmar que
                          no hay promociones cuando promotions este presente y su lista sea vacia.
                        - Si existe sizeRecommendationBusinessRule, el conjunto se vende en una sola talla: comunica
                          una unica talla para todas sus piezas y nunca propongas mezclar tallas.
                        - Si cartOperation.executed es true, confirma el carrito real devuelto por el backend.
                        - Si cartOperation.executed es false y no ejecutaste una herramienta, no afirmes que agregaste,
                          cambiaste o confirmaste nada; solicita unicamente el dato que falta.
                        """.formatted(grounding.evidenceJson()), Map.copyOf(toolContext));
        int maxTools = runtimeConfig.forSession(lease.sessionId()).maxToolCallsPerRun();
        var generated = gateway.respond(context.connectionId(), prompt, maxTools);
        var response = generated.response();
        validateTurn(grounding, response.customerMessage());
        var priceValidation = priceValidator.validate(grounding.evidenceJson(), response.customerMessage());
        var promotionValidation = promotionValidator.validate(
                grounding.evidenceJson(), response.customerMessage());
        String mediaCorrection = mediaCorrectionInstruction(grounding, response.customerMessage());
        if (!priceValidation.valid() || !promotionValidation.valid() || !mediaCorrection.isBlank()) {
            String correctionInstruction = String.join("\n",
                    priceValidation.valid() ? "" : priceValidation.correctionInstruction(),
                    promotionValidation.valid() ? "" : promotionValidation.correctionInstruction(),
                    mediaCorrection).trim();
            AgentMainPrompt correctionPrompt = groundingCorrectionPrompt(
                    prompt, response.customerMessage(), correctionInstruction);
            var corrected = gateway.respond(context.connectionId(), correctionPrompt, maxTools);
            var correctedResponse = corrected.response();
            validateTurn(grounding, correctedResponse.customerMessage());
            var correctedValidation = priceValidator.validate(
                    grounding.evidenceJson(), correctedResponse.customerMessage());
            var correctedPromotionValidation = promotionValidator.validate(
                    grounding.evidenceJson(), correctedResponse.customerMessage());
            String correctedMediaValidation = mediaCorrectionInstruction(
                    grounding, correctedResponse.customerMessage());
            if (!correctedValidation.valid() || !correctedPromotionValidation.valid()
                    || !correctedMediaValidation.isBlank()) {
                throw new IllegalStateException("Gemini contradijo la evidencia comercial despues de corregir la redaccion: "
                        + String.join(" ", correctedValidation.correctionInstruction(),
                                correctedPromotionValidation.correctionInstruction(),
                                correctedMediaValidation).trim());
            }
            generated = new AgentResponseGateway.AgentResponseResult(
                    corrected.provider(), corrected.model(), generated.latencyMs() + corrected.latencyMs(),
                    generated.toolCalls() + corrected.toolCalls(), correctedResponse);
            response = correctedResponse;
        }
        // Gemini redacta el texto, pero solo Java puede autorizar archivos. Esto evita
        // fotos repetidas o no solicitadas durante recomendaciones y reintentos.
        List<AgentMediaRequest> media = verifiedMedia(grounding.media());
        GeneratedTurn turn = new GeneratedTurn(response.customerMessage(), response.conversationSummary(),
                response.requiresHumanHelp(), generated.provider(), generated.model(), generated.latencyMs(),
                generated.toolCalls() + grounding.verifiedToolCalls(), context.fingerprint(), media);
        return persistGeneratedTurn(lease, turn);
    }

    static String generalMediaMessage(AgentMessageBatch batch,
            com.sistemapos.sistematextil.model.CrmWhatsappAgentState state) {
        if (batch == null || isReceiptWorkflow(state)) return null;
        boolean image = batch.messages().stream()
                .anyMatch(item -> "IMAGE".equalsIgnoreCase(item.messageType()));
        if (image) return GENERAL_IMAGE_MESSAGE;
        boolean document = batch.messages().stream()
                .anyMatch(item -> "DOCUMENT".equalsIgnoreCase(item.messageType()));
        return document ? GENERAL_DOCUMENT_MESSAGE : null;
    }

    static boolean isPostSaleChangeRequest(
            com.sistemapos.sistematextil.services.agent.context.AgentContext context,
            AgentMessageBatch batch) {
        if (context == null || context.fulfillment() == null || context.fulfillment().saleId() == null
                || batch == null) return false;
        String message = normalizeText(batch.combinedInput());
        boolean asksForChange = message.matches(
                ".*\\b(cambi(?:ar|arme|o|ame|es|e|arlo|arla)|modific(?:ar|acion)|reemplaz(?:ar|o)|correg(?:ir|irlo|irla))\\b.*");
        if (!asksForChange) return false;
        return message.matches(
                ".*\\b(color|talla|modelo|producto|prenda|cantidad|pedido|pago|direccion|agencia|envio|entrega)\\b.*")
                || message.contains("quiero cambiar") || message.contains("deseo cambiar");
    }

    private static String normalizeText(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private static boolean isReceiptWorkflow(
            com.sistemapos.sistematextil.model.CrmWhatsappAgentState state) {
        return state == com.sistemapos.sistematextil.model.CrmWhatsappAgentState.ESPERANDO_COMPROBANTE
                || state == com.sistemapos.sistematextil.model.CrmWhatsappAgentState.VALIDANDO_COMPROBANTE
                || state == com.sistemapos.sistematextil.model.CrmWhatsappAgentState.ESPERANDO_PAGO_FILM;
    }

    private AgentMainPrompt groundingCorrectionPrompt(AgentMainPrompt original, String rejectedResponse,
            String correctionInstruction) {
        return new AgentMainPrompt(original.version(), original.systemInstruction(),
                original.userMessage() + """

                        CORRECCION_OBLIGATORIA_ANTES_DE_ENVIAR:
                        La redaccion anterior no se enviara porque omitio o contradijo datos comerciales verificados.

                        RESPUESTA_ANTERIOR:
                        %s

                        ERRORES_A_CORREGIR:
                        %s

                        Redacta nuevamente una respuesta natural y breve. Conserva exclusivamente los datos de
                        EVIDENCIA_VERIFICADA_DEL_BACKEND. Si hay promociones, presenta las promociones reales con
                        su nombre, productos incluidos y precio. Incluye junto a cada producto mostrado su precio
                        real y no menciones esta revision a la clienta. No uses una plantilla fija.
                        """.formatted(rejectedResponse, correctionInstruction), original.toolContext());
    }

    private GeneratedTurn persistGeneratedTurn(JobLease lease, GeneratedTurn turn) {
        if (!coordinator.isCurrent(lease)) throw new StaleAgentTurnException();
        if (!jobStore.saveGeneratedTurn(lease.jobId(), lease.workerId(), turn, LocalDateTime.now())) {
            return jobStore.findGeneratedTurn(lease.jobId(), lease.workerId())
                    .orElseThrow(StaleAgentTurnException::new);
        }
        return turn;
    }

    private void validateTurn(AgentVerifiedGroundingService.Grounding grounding, String customerMessage) {
        AgentDecisionIntent intent = grounding.decision().intent();
        boolean requiresVerifiedRead = requiresVerifiedRead(intent)
                || grounding.decision().commercialAction() == AgentCommercialAction.SHOW_PROMOTIONS
                || grounding.decision().commercialAction() == AgentCommercialAction.SHOW_PRODUCT_IMAGES;
        if (requiresVerifiedRead && grounding.verifiedToolCalls() < 1) {
            throw new IllegalStateException("La respuesta comercial no tiene evidencia verificada del backend");
        }
        if (requiresVerifiedRead && isDeferredOrPlaceholder(customerMessage)) {
            throw new IllegalStateException("Gemini devolvio una respuesta comercial pendiente o con marcadores vacios");
        }
    }

    private boolean requiresVerifiedRead(AgentDecisionIntent intent) {
        return intent == AgentDecisionIntent.PRODUCT_SEARCH
                || intent == AgentDecisionIntent.PRODUCT_DETAILS
                || intent == AgentDecisionIntent.PROMOTION_SEARCH
                || intent == AgentDecisionIntent.BUSINESS_INFORMATION;
    }

    private boolean isDeferredOrPlaceholder(String value) {
        String normalized = normalize(value);
        if (normalized.contains("[lista") || normalized.contains("[dato") || normalized.contains("[pendiente")) {
            return true;
        }
        return normalized.contains("dame un momento")
                || normalized.contains("dame un segundo")
                || normalized.contains("estoy revisando")
                || normalized.contains("estoy verificando")
                || normalized.contains("estoy buscando")
                || normalized.contains("permitame verificar")
                || normalized.contains("permiteme verificar")
                || normalized.contains("voy a revisar");
    }

    private String mediaCorrectionInstruction(AgentVerifiedGroundingService.Grounding grounding,
            String customerMessage) {
        if (grounding.media() != null && !grounding.media().isEmpty()) return "";
        String normalized = normalize(customerMessage);
        boolean mentionsMedia = normalized.contains("foto") || normalized.contains("imagen");
        boolean claimsDelivery = normalized.contains("aqui tienes")
                || normalized.contains("te comparto")
                || normalized.contains("te envio")
                || normalized.contains("te muestro")
                || normalized.contains("te paso")
                || normalized.contains("adjunto");
        if (!mentionsMedia || !claimsDelivery) return "";
        return "No hay archivos verificados para adjuntar. No afirmes que envias, compartes o muestras fotos; "
                + "explica brevemente la opcion disponible o solicita precisar el modelo.";
    }

    private List<AgentMediaRequest> verifiedMedia(List<AgentMediaRequest> verified) {
        List<AgentMediaRequest> result = new ArrayList<>();
        if (verified != null) for (AgentMediaRequest item : verified) addMedia(result, item);
        return List.copyOf(result.stream().limit(3).toList());
    }

    private void addMedia(List<AgentMediaRequest> target, AgentMediaRequest item) {
        if (item != null && !target.contains(item)) target.add(item);
    }

    private String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private String safeMessage(Throwable error) {
        return error.getMessage() == null || error.getMessage().isBlank()
                ? error.getClass().getSimpleName() : error.getMessage();
    }

    public enum Outcome { COMPLETED, RETRY, FAILED, STALE }
}
