package com.sistemapos.sistematextil.services.agent.runtime;

import java.time.LocalDateTime;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.sistemapos.sistematextil.services.agent.ai.AgentResponseGateway;
import com.sistemapos.sistematextil.services.agent.ai.AgentCommercialAction;
import com.sistemapos.sistematextil.services.agent.ai.AgentDecisionIntent;
import com.sistemapos.sistematextil.services.agent.ai.AgentMediaRequest;
import com.sistemapos.sistematextil.services.agent.ai.AgentTurnResponse;
import com.sistemapos.sistematextil.services.agent.batch.AgentJobCoordinator;
import com.sistemapos.sistematextil.services.agent.batch.AgentJobStore;
import com.sistemapos.sistematextil.services.agent.batch.AgentJobStore.GeneratedTurn;
import com.sistemapos.sistematextil.services.agent.batch.AgentJobStore.JobLease;
import com.sistemapos.sistematextil.services.agent.batch.AgentMessageBatch;
import com.sistemapos.sistematextil.services.agent.batch.AgentMessageBatchFactory;
import com.sistemapos.sistematextil.services.agent.cart.AgentCartTools;
import com.sistemapos.sistematextil.services.agent.config.AgentRuntimeConfigService;
import com.sistemapos.sistematextil.services.agent.context.AgentContext;
import com.sistemapos.sistematextil.services.agent.context.AgentContextFactory;
import com.sistemapos.sistematextil.services.agent.context.AgentMissingData;
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

            Para recibir ayuda con tu caso, comunícate con nuestra asesora al 936310946.""";
    static final String POST_SALE_CHANGE_CONTACT_ONLY_MESSAGE = """
            Entiendo. Como tu pedido ya fue confirmado, no puedo realizar cambios de color, talla, modelo, cantidad, pago o entrega desde este chat.

            Para consultar si el cambio es posible, comunícate con nuestra asesora al 936310946. Puedes seguir escribiéndome si deseas información o realizar una compra nueva.""";
    static final String GENERAL_IMAGE_MESSAGE = """
            Recibí tu imagen, bella 💛
            Para ayudarte correctamente, indícame el nombre del modelo, el color y la talla que deseas.""";
    static final String GENERAL_DOCUMENT_MESSAGE = """
            Recibí tu archivo, bella 💛
            Por el momento no puedo revisar su contenido directamente. Cuéntame brevemente qué contiene o qué deseas consultar.""";

    private final AgentProductPriceResponseValidator priceValidator = new AgentProductPriceResponseValidator();
    private final AgentPromotionResponseValidator promotionValidator = new AgentPromotionResponseValidator();
    private final ObjectMapper evidenceMapper = new ObjectMapper();
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
        var runtime = runtimeConfig.forSession(lease.sessionId());
        if (runtime == null) runtime = AgentRuntimeConfigService.RuntimeConfig.defaults();
        boolean advisorEnabled = runtime != null && runtime.advisorAttentionEnabled();
        if (isPostSaleChangeRequest(context, batch)) {
            GeneratedTurn deterministic = new GeneratedTurn(
                    advisorEnabled ? POST_SALE_CHANGE_MESSAGE : POST_SALE_CHANGE_CONTACT_ONLY_MESSAGE,
                    context.previousSummary(), advisorEnabled,
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
        boolean pendingReminderRequired = pendingReminderRequired(context, grounding);
        if (grounding.customerMessage() != null && !grounding.customerMessage().isBlank()
                && !pendingReminderRequired) {
            GeneratedTurn deterministic = new GeneratedTurn(
                    grounding.customerMessage(), context.previousSummary(),
                    advisorEnabled && grounding.decision().requiresHumanHelp(),
                    "JAVA_DETERMINISTIC", "state-machine", 0L,
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
                        - El inventario exacto es informacion interna. Nunca menciones cantidades de stock,
                          existencias ni unidades restantes. Comunica solamente si cada color o talla esta
                          disponible o no disponible, aunque el historial contenga una cantidad.
                        - Si existe unresolvedImageProductReference, no uses el producto supuesto por la decision ni
                          por el historial. Indica brevemente que no puedes identificar el modelo desde la imagen,
                          responde solo la parte textual verificable y pide el nombre del modelo cuando sea necesario.
                        - No digas que estas buscando, revisando o verificando: la consulta ya fue ejecutada.
                        - No escribas marcadores como [lista], datos pendientes ni productos no presentes en la evidencia.
                        - Solo afirma que adjuntas, compartes, envias o muestras fotos cuando verifiedMediaCount es
                          mayor que cero. Si vale cero, no prometas ni anuncies imagenes que no seran enviadas.
                        - Cuando exista selectedProduct, responde ahora con sus datos reales.
                        - Todo producto que menciones o muestres debe incluir su precio real de la evidencia.
                          No omitas precios, no los agrupes si tienen importes distintos y no inventes montos.
                        - Cuando exista availabilityByColor, presenta cada color con sus tallas disponibles reales.
                        - Cuando exista catalogAttributeAvailability, la clienta consulto el catalogo completo por
                          color, talla o ambos sin nombrar un modelo. Menciona todos los productos verificados en
                          products, con su precio, y separa naturalmente entrega inmediata de preventa usando el
                          campo preorder. Si products esta vacio, informa que no existe esa combinacion. No conviertas
                          la regla en una plantilla ni reveles cantidades internas.
                        - Cuando exista requestedVariantAvailability, redacta la respuesta de forma natural usando
                          exclusivamente sus productos, precios y variantes verificadas. No uses una plantilla fija.
                          Si variants esta vacio, informa que la combinacion solicitada no tiene stock y NUNCA
                          preguntes si desea agregarla, comprarla o separarla. Invita de forma natural a explorar
                          otros productos en https://kiments.com.pe/productos, sin repetir una frase fija. Si la
                          clienta pidio algo parecido,
                          ofrece solo alternativas que aparezcan verificadas con stock; si la evidencia no contiene
                          alternativas, pregunta si desea que busques otros modelos con ese color y talla.
                          Si variants contiene opciones, invita a elegir o agregar solo opciones disponibles.
                        - Usa la web como galeria visual solo en consultas generales. Si presentas de forma general
                          exactamente un producto y su productPageUrl no esta vacio, incluye ese enlace individual;
                          si presentas de forma general dos o mas productos, incluye una sola vez
                          https://kiments.com.pe/productos. Si la clienta consulta una seleccion concreta de color,
                          talla o ambos, responde directamente disponibilidad y precio sin incluir ninguna URL.
                          No inventes slugs ni construyas enlaces a partir del nombre; usa solamente URLs presentes
                          en la evidencia. Redacta naturalmente y no conviertas esta regla en una plantilla fija.
                        - Cuando exista sizeGuideDelivery, redacta libremente una respuesta breve: menciona solo las
                          guias realmente incluidas en sent y explica cuales faltan usando missing. No uses una
                          plantilla fija ni afirmes que enviaste una guia que no aparece en sent.
                        - Cuando exista productColorGallery, menciona por texto TODOS los availableColors, aunque solo
                          se adjunten hasta imageLimit imagenes. Indica naturalmente cuales se mostraron y, si
                          hasMoreColors=true, invita a la clienta a pedir cualquier otro color para ver su foto.
                          Si colorsWithoutImage contiene valores, no afirmes que adjuntaste esos colores; explica
                          brevemente que estan disponibles pero no tienen una foto configurada.
                        - Cuando requestedColorAvailability.available sea false, no afirmes que mostraste ni que esta
                          disponible el color solicitado. Ofrece unicamente los availableColors verificados.
                        - Respeta el filtro availability y no mezcles preventas con productos listos.
                        - El campo preorder de la evidencia es la unica autoridad para afirmar que un producto esta
                          en preventa. Si preorder=false, no lo llames preventa ni comuniques una antigua fecha de
                          despacho aunque la descripcion comercial conserve texto historico.
                        - Para preventas, di "despachos a partir de [fecha]". La fecha es referencial y el despacho
                          depende del volumen de pedidos. Nunca digas "llega el [fecha]" ni garantices que saldra
                          exactamente ese dia.
                        - Cuando NO existe orderDeliveryGuidance, se trata de una consulta previa a la compra: para
                          disponibilidad inmediata, el plazo permitido es el despacho aproximado desde Kiments en
                          1 a 4 días según el volumen de pedidos; para preventa, usa el inicio referencial verificado.
                          Nunca presentes esos plazos como tiempo de traslado o llegada: después del despacho, la
                          llegada depende de Shalom y no tiene un día exacto garantizado.
                        - Si existe orderDeliveryGuidance, redacta una respuesta breve y natural siguiendo solo su
                          policy. READY_STOCK comunica despacho aproximado de 1 a 3 días según volumen y orden de
                          atención. PREORDER dice solamente que el envío se realizará según la fecha indicada en la
                          preventa; no escribas una fecha exacta. En ambos casos indica que una asesora del área de
                          envíos se comunicará para coordinar. NOT_LINKED_TO_AGENT no informa plazos ni promete
                          contacto: explica que no encontraste una venta registrada por este chat y comparte 936310946.
                        - Si la clienta solicita todos los productos de un grupo, incluye todos los productos presentes
                          en la evidencia; no agregues modelos, tipos de prenda ni variantes que no aparezcan alli.
                        - Cuando commercialAction sea SHOW_PRODUCTS, incluye siempre https://kiments.com.pe/productos
                          como galeria completa, ademas de responder con los productos reales de la evidencia. Redacta
                          naturalmente y continua ayudando a elegir o comprar; no conviertas esta regla en una plantilla.
                        - Cuando exista promotions, responde la consulta usando esa lista real. Solo puedes afirmar que
                          no hay promociones cuando promotions este presente y su lista sea vacia.
                        - Si existe sizeRecommendationBusinessRule, el conjunto se vende en una sola talla: comunica
                          una unica talla para todas sus piezas y nunca propongas mezclar tallas. Si
                          sizeRecommendation.betweenSizes es true, explica el equilibrio entre las candidatas usando
                          su evidencia completa y recomienda la mayor como opcion segura; no respondas basandote solo
                          en la ultima medida mencionada ni garantices como quedara la prenda.
                        - Si cartOperation.executed es true, confirma el carrito real devuelto por el backend.
                        - Si cartOperation.executed es false y no ejecutaste una herramienta, no afirmes que agregaste,
                          cambiaste o confirmaste nada; solicita unicamente el dato que falta.
                        %s
                        """.formatted(grounding.evidenceJson(), pendingReminderRule(context, pendingReminderRequired)),
                Map.copyOf(toolContext));
        int maxTools = runtime.maxToolCallsPerRun();
        var generated = gateway.respond(context.connectionId(), prompt, maxTools);
        var response = generated.response();
        validateTurn(grounding, response.customerMessage());
        var priceValidation = priceValidator.validate(grounding.evidenceJson(), response.customerMessage());
        var promotionValidation = promotionValidator.validate(
                grounding.evidenceJson(), response.customerMessage());
        String mediaCorrection = mediaCorrectionInstruction(grounding, response.customerMessage());
        String catalogUrlCorrection = catalogUrlCorrectionInstruction(grounding, response.customerMessage());
        String deliveryCorrection = deliveryTimingCorrectionInstruction(grounding, response.customerMessage());
        String availabilityCorrection = availabilityCorrectionInstruction(grounding, response.customerMessage());
        String inventoryDisclosureCorrection = inventoryDisclosureCorrectionInstruction(response.customerMessage());
        String advisorCorrection = advisorCorrectionInstruction(advisorEnabled,
                grounding.decision().requiresHumanHelp(), response);
        String pendingReminderCorrection = pendingReminderCorrectionInstruction(
                context, pendingReminderRequired, response);
        if (!priceValidation.valid() || !promotionValidation.valid() || !mediaCorrection.isBlank()
                || !catalogUrlCorrection.isBlank() || !deliveryCorrection.isBlank()
                || !availabilityCorrection.isBlank() || !inventoryDisclosureCorrection.isBlank()
                || !advisorCorrection.isBlank()
                || !pendingReminderCorrection.isBlank()) {
            String correctionInstruction = String.join("\n",
                    priceValidation.valid() ? "" : priceValidation.correctionInstruction(),
                    promotionValidation.valid() ? "" : promotionValidation.correctionInstruction(),
                    mediaCorrection,
                    catalogUrlCorrection,
                    deliveryCorrection,
                    availabilityCorrection,
                    inventoryDisclosureCorrection,
                    advisorCorrection,
                    pendingReminderCorrection).trim();
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
            String correctedCatalogUrlValidation = catalogUrlCorrectionInstruction(
                    grounding, correctedResponse.customerMessage());
            String correctedDeliveryValidation = deliveryTimingCorrectionInstruction(
                    grounding, correctedResponse.customerMessage());
            String correctedAvailabilityValidation = availabilityCorrectionInstruction(
                    grounding, correctedResponse.customerMessage());
            String correctedInventoryDisclosureValidation = inventoryDisclosureCorrectionInstruction(
                    correctedResponse.customerMessage());
            String correctedAdvisorValidation = advisorCorrectionInstruction(
                    advisorEnabled, false, correctedResponse);
            String correctedPendingReminderValidation = pendingReminderCorrectionInstruction(
                    context, pendingReminderRequired, correctedResponse);
            if (!correctedValidation.valid() || !correctedPromotionValidation.valid()
                    || !correctedMediaValidation.isBlank() || !correctedCatalogUrlValidation.isBlank()
                    || !correctedDeliveryValidation.isBlank()
                    || !correctedAvailabilityValidation.isBlank()
                    || !correctedInventoryDisclosureValidation.isBlank()
                    || !correctedAdvisorValidation.isBlank()
                    || !correctedPendingReminderValidation.isBlank()) {
                throw new IllegalStateException("Gemini contradijo la evidencia comercial despues de corregir la redaccion: "
                        + String.join(" ", correctedValidation.correctionInstruction(),
                                correctedPromotionValidation.correctionInstruction(),
                                correctedMediaValidation,
                                correctedCatalogUrlValidation,
                                correctedDeliveryValidation,
                                correctedAvailabilityValidation,
                                correctedInventoryDisclosureValidation,
                                correctedAdvisorValidation,
                                correctedPendingReminderValidation).trim());
            }
            generated = new AgentResponseGateway.AgentResponseResult(
                    corrected.provider(), corrected.model(), generated.latencyMs() + corrected.latencyMs(),
                    generated.toolCalls() + corrected.toolCalls(), correctedResponse);
            response = correctedResponse;
        }
        // Gemini redacta el texto, pero solo Java puede autorizar archivos. Esto evita
        // fotos repetidas o no solicitadas durante recomendaciones y reintentos.
        List<AgentMediaRequest> media = verifiedMedia(grounding.media());
        String customerMessage = composeCustomerMessage(response, pendingReminderRequired);
        GeneratedTurn turn = new GeneratedTurn(customerMessage, response.conversationSummary(),
                advisorEnabled && grounding.decision().requiresHumanHelp(),
                generated.provider(), generated.model(), generated.latencyMs(),
                generated.toolCalls() + grounding.verifiedToolCalls(), context.fingerprint(), media);
        return persistGeneratedTurn(lease, turn);
    }

    private boolean pendingReminderRequired(AgentContext context,
            AgentVerifiedGroundingService.Grounding grounding) {
        return context != null
                && context.missingData() != null
                && !context.missingData().isEmpty()
                && grounding != null
                && !grounding.mutationExecuted();
    }

    private String pendingReminderRule(AgentContext context, boolean required) {
        if (!required) {
            return "- No existe un recordatorio pendiente para esta respuesta: devuelve pendingStepReminder vacio "
                    + "y remindedPendingData como lista vacia.";
        }
        String paymentOptions = context.state() == com.sistemapos.sistematextil.model.CrmWhatsappAgentState.ESPERANDO_METODO_PAGO
                ? " Si espera el metodo de pago, menciona naturalmente que puede continuar eligiendo como pagar, modificar el pedido o cancelarlo."
                : "";
        return "- Esta es una consulta intermedia y el estado comercial no avanzo. Responde primero la consulta "
                + "usando la evidencia verificada. Escribe aparte en pendingStepReminder un recordatorio breve, "
                + "natural y sin repetir el formulario completo. Solicita solamente: "
                + context.missingData() + ". Devuelve exactamente esos valores en remindedPendingData. No vuelvas "
                + "a pedir datos que ya estan validados ni incluyas el recordatorio dentro de customerMessage."
                + paymentOptions;
    }

    private String pendingReminderCorrectionInstruction(AgentContext context, boolean required,
            AgentTurnResponse response) {
        String reminder = response == null ? "" : response.pendingStepReminder();
        List<AgentMissingData> actual = response == null || response.remindedPendingData() == null
                ? List.of() : response.remindedPendingData();
        if (!required) {
            if (reminder.isBlank() && actual.isEmpty()) return "";
            return "No existe un paso pendiente que deba recordarse en esta respuesta. Deja pendingStepReminder "
                    + "vacio y remindedPendingData como lista vacia.";
        }
        List<AgentMissingData> expected = context.missingData().stream().distinct().toList();
        boolean sameValues = actual.size() == expected.size()
                && actual.containsAll(expected) && expected.containsAll(actual);
        if (!reminder.isBlank() && sameValues) return "";
        String paymentOptions = context.state() == com.sistemapos.sistematextil.model.CrmWhatsappAgentState.ESPERANDO_METODO_PAGO
                ? " Para el metodo de pago, permite continuar, modificar el pedido o cancelarlo."
                : "";
        return "El estado comercial sigue esperando informacion. Responde la consulta y completa "
                + "pendingStepReminder con una sola frase natural que recuerde unicamente " + expected
                + "; devuelve exactamente esos mismos valores en remindedPendingData. No repitas el formulario "
                + "completo ni solicites datos ya guardados." + paymentOptions;
    }

    private String composeCustomerMessage(AgentTurnResponse response, boolean reminderRequired) {
        String message = response.customerMessage().trim();
        if (!reminderRequired || response.pendingStepReminder().isBlank()) return message;
        String reminder = response.pendingStepReminder().trim();
        return message.endsWith(reminder) ? message : message + "\n\n" + reminder;
    }

    private String advisorCorrectionInstruction(boolean advisorEnabled, boolean decisionRequiresHumanHelp,
            AgentTurnResponse response) {
        boolean handoffAllowed = advisorEnabled && decisionRequiresHumanHelp;
        if (handoffAllowed) return "";
        String message = normalizeText(response == null ? null : response.customerMessage());
        boolean promisesAutomaticHandoff = message.contains("te derivare")
                || message.contains("te transferire")
                || message.contains("dejare el chat en espera")
                || message.contains("una asesora revisara tu caso")
                || message.contains("una asesora se comunicara contigo para ayudarte");
        boolean modelRequestsHandoff = response != null && response.requiresHumanHelp();
        boolean disabledDecisionRequestsHandoff = !advisorEnabled && decisionRequiresHumanHelp;
        if (!modelRequestsHandoff && !disabledDecisionRequestsHandoff && !promisesAutomaticHandoff) return "";
        return "No esta autorizada una derivacion humana para esta respuesta. Redacta nuevamente con "
                + "requiresHumanHelp=false, sin prometer transferencia, espera, revision ni contacto automatico. "
                + "Responde la consulta normalmente; si esta fuera de alcance, comparte 936310946 solo como "
                + "contacto informativo y mantente disponible para seguir informando o vendiendo.";
    }

    static String generalMediaMessage(AgentMessageBatch batch,
            com.sistemapos.sistematextil.model.CrmWhatsappAgentState state) {
        if (batch == null || isReceiptWorkflow(state)) return null;
        boolean image = batch.messages().stream()
                .anyMatch(item -> "IMAGE".equalsIgnoreCase(item.messageType()));
        if (image && !hasMeaningfulCustomerText(batch)) return GENERAL_IMAGE_MESSAGE;
        boolean document = batch.messages().stream()
                .anyMatch(item -> "DOCUMENT".equalsIgnoreCase(item.messageType()));
        return document ? GENERAL_DOCUMENT_MESSAGE : null;
    }

    private static boolean hasMeaningfulCustomerText(AgentMessageBatch batch) {
        return batch.messages().stream()
                .map(AgentMessageBatch.Item::content)
                .filter(content -> content != null && !content.isBlank())
                .flatMap(content -> content.lines())
                .map(String::trim)
                .anyMatch(line -> !line.isBlank() && !isMediaMarker(line));
    }

    private static boolean isMediaMarker(String line) {
        return line.startsWith("[") && line.endsWith("]")
                && (line.contains("ADJUNT") || line.contains("PENDIENTE DE TRANSCRIPCION"));
    }

    static boolean isPostSaleChangeRequest(
            com.sistemapos.sistematextil.services.agent.context.AgentContext context,
            AgentMessageBatch batch) {
        if (context == null || context.fulfillment() == null || context.fulfillment().saleId() == null
                || batch == null) return false;
        if (isPreSaleWorkflow(context.state())) return false;
        String message = normalizeText(batch.combinedInput());
        boolean asksForChange = message.matches(
                ".*\\b(cambi(?:ar|arme|o|ame|es|e|arlo|arla)|modific(?:ar|acion)|reemplaz(?:ar|o)|correg(?:ir|irlo|irla))\\b.*");
        if (!asksForChange) return false;
        return message.matches(
                ".*\\b(color|talla|modelo|producto|prenda|cantidad|pedido|pago|direccion|agencia|envio|entrega)\\b.*")
                || message.contains("quiero cambiar") || message.contains("deseo cambiar");
    }

    private static boolean isPreSaleWorkflow(
            com.sistemapos.sistematextil.model.CrmWhatsappAgentState state) {
        if (state == null) return false;
        return switch (state) {
            case IDLE, EXPLORANDO_PRODUCTOS, ARMANDO_CARRITO, ESPERANDO_METODO_PAGO,
                    ESPERANDO_COMPROBANTE, VALIDANDO_COMPROBANTE, ESPERANDO_DATOS_CLIENTE,
                    ESPERANDO_METODO_ENTREGA, ESPERANDO_DATOS_SHALOM,
                    ESPERANDO_CONFIRMACION_AGENCIA_SHALOM, LISTO_PARA_VENTA -> true;
            default -> false;
        };
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

    static String catalogUrlCorrectionInstruction(AgentVerifiedGroundingService.Grounding grounding,
            String customerMessage) {
        if (grounding == null || grounding.decision() == null
                || grounding.decision().commercialAction() != AgentCommercialAction.SHOW_PRODUCTS) return "";
        if (customerMessage != null && customerMessage.contains("https://kiments.com.pe/productos")) return "";
        return "La clienta solicito el catalogo o la lista general de productos. Conserva la lista real y agrega "
                + "https://kiments.com.pe/productos como galeria completa. Redacta naturalmente y sigue ayudando "
                + "con la compra; no uses una plantilla fija.";
    }

    static String deliveryTimingCorrectionInstruction(AgentVerifiedGroundingService.Grounding grounding,
            String customerMessage) {
        if (grounding == null || grounding.decision() == null
                || grounding.decision().commercialAction()
                        != AgentCommercialAction.REFER_EXISTING_ORDER_DELIVERY) return "";
        String evidence = grounding.evidenceJson() == null ? "" : grounding.evidenceJson();
        String response = normalizeText(customerMessage);
        if (evidence.contains("NOT_LINKED_TO_AGENT")) {
            if (response.contains("936310946") && !response.contains("1 a 3")
                    && !containsShippingContact(response)) return "";
            return "No existe una venta vinculada a este chat. No informes plazos ni prometas contacto del area "
                    + "de envios; comparte el 936310946 para que una asesora revise el pedido.";
        }
        if (evidence.contains("READY_STOCK")) {
            if (response.contains("1 a 3") && containsShippingContact(response)) return "";
            return "La venta fue registrada por la asistente y es de disponibilidad inmediata. Comunica despacho "
                    + "aproximado de 1 a 3 dias segun volumen y orden, e indica que una asesora del area de envios "
                    + "contactara a la clienta para coordinar. Redacta naturalmente, sin copiar una plantilla fija.";
        }
        if (evidence.contains("PREORDER")) {
            boolean exactDate = response.matches(".*\\b(enero|febrero|marzo|abril|mayo|junio|julio|agosto|"
                    + "septiembre|octubre|noviembre|diciembre)\\b.*")
                    || response.matches(".*\\b\\d{1,2}[/\\-]\\d{1,2}([/\\-]\\d{2,4})?\\b.*");
            if (response.contains("fecha indicada") && containsShippingContact(response)
                    && !response.contains("1 a 3") && !response.contains("1 a 4") && !exactDate) return "";
            return "La venta registrada incluye preventa. Di solamente que el envio se realizara segun la fecha "
                    + "indicada en la preventa y que una asesora del area de envios contactara a la clienta para "
                    + "coordinar. No escribas una fecha exacta ni un plazo de dias. Redacta naturalmente.";
        }
        return "";
    }

    private String availabilityCorrectionInstruction(AgentVerifiedGroundingService.Grounding grounding,
            String customerMessage) {
        if (grounding == null || grounding.evidenceJson() == null) return "";
        try {
            JsonNode root = evidenceMapper.readTree(grounding.evidenceJson());
            JsonNode catalogAttributes = root.path("catalogAttributeAvailability");
            if (!catalogAttributes.isMissingNode()) {
                return catalogAttributeCorrectionInstruction(catalogAttributes, customerMessage);
            }
            JsonNode availability = root.path("requestedVariantAvailability");
            if (availability.isMissingNode()) return "";
            boolean specificSelection = !availability.path("requestedColor").asText("").isBlank()
                    || !availability.path("requestedSize").asText("").isBlank();
            JsonNode products = availability.path("products");
            boolean anyAvailable = false;
            int productCount = 0;
            String singleProductUrl = "";
            if (products.isArray()) {
                for (JsonNode product : products) {
                    productCount++;
                    if (productCount == 1) singleProductUrl = product.path("productPageUrl").asText("");
                    if (product.path("variants").isArray() && !product.path("variants").isEmpty()) {
                        anyAvailable = true;
                    }
                }
            }
            String response = normalizeText(customerMessage);
            boolean offersUnavailableItem = response.contains("deseas agregar")
                    || response.contains("quieres agregar")
                    || response.contains("deseas comprar")
                    || response.contains("quieres comprar")
                    || response.contains("deseas separar")
                    || response.contains("quieres separar");
            List<String> corrections = new ArrayList<>();
            if (!anyAvailable && offersUnavailableItem) {
                corrections.add("Ninguna variante solicitada tiene stock. No invites a agregar, comprar ni "
                        + "separar esa combinacion; informa la falta de stock.");
            }
            String catalogUrl = "https://kiments.com.pe/productos";
            boolean containsWebsiteUrl = customerMessage != null && customerMessage.contains(catalogUrl);
            if (specificSelection && containsWebsiteUrl) {
                corrections.add("La clienta consulto una seleccion concreta de color o talla. Retira el enlace web "
                        + "y responde directamente con disponibilidad, precio y el siguiente paso de compra.");
            } else if (!specificSelection && !anyAvailable
                    && (customerMessage == null || !customerMessage.contains(catalogUrl))) {
                corrections.add("Como no hay stock, invita naturalmente a explorar otros productos e incluye "
                        + catalogUrl + ".");
            } else if (!specificSelection && anyAvailable && productCount == 1 && !singleProductUrl.isBlank()
                    && (customerMessage == null || !customerMessage.contains(singleProductUrl))) {
                corrections.add("Presentaste un solo producto: incluye su pagina verificada " + singleProductUrl
                        + " como galeria visual.");
            } else if (!specificSelection && anyAvailable && productCount > 1
                    && (customerMessage == null || !customerMessage.contains(catalogUrl))) {
                corrections.add("Presentaste varios productos: incluye una sola vez " + catalogUrl
                        + " como galeria completa.");
            }
            return String.join(" ", corrections);
        } catch (Exception ignored) {
            return "";
        }
    }

    private String catalogAttributeCorrectionInstruction(JsonNode availability, String customerMessage) {
        JsonNode products = availability.path("products");
        if (!products.isArray()) return "";
        List<String> requiredProducts = new ArrayList<>();
        products.forEach(product -> {
            String name = product.path("name").asText("").trim();
            if (!name.isBlank()) requiredProducts.add(name);
        });
        String response = normalizeText(customerMessage);
        if (requiredProducts.isEmpty()) {
            return "";
        }
        List<String> omitted = requiredProducts.stream()
                .filter(name -> !response.contains(normalizeText(name)))
                .toList();
        boolean falselyUnavailable = response.contains("no hay stock")
                || response.contains("no tenemos stock")
                || response.contains("no esta disponible")
                || response.contains("no esta disponibles")
                || response.contains("no contamos con stock");
        List<String> corrections = new ArrayList<>();
        if (!omitted.isEmpty()) {
            corrections.add("La consulta abarca todo el catalogo por color y talla. Menciona todos los productos "
                    + "verificados, sin omitir ninguno: " + String.join(", ", requiredProducts) + ".");
        }
        if (falselyUnavailable) {
            corrections.add("La evidencia contiene productos disponibles para la combinacion solicitada. No afirmes "
                    + "que no hay stock; distingue los productos de entrega inmediata y los de preventa.");
        }
        return String.join(" ", corrections);
    }

    private String inventoryDisclosureCorrectionInstruction(String customerMessage) {
        String response = normalizeText(customerMessage);
        if (response.isBlank()) return "";
        boolean exposesInventory = response.matches("(?s).*\\b\\d+\\s+(?:unidades?\\s+)?"
                + "(?:disponibles?|restantes?|en\\s+stock)\\b.*")
                || response.matches("(?s).*\\b(?:quedan?|tenemos|hay)\\s+\\d+\\s+"
                + "(?:unidades?|prendas?|piezas?)\\b.*")
                || response.matches("(?s).*\\b(?:stock|existencias?)\\s*(?:disponible)?\\s*[:=-]?\\s*\\d+\\b.*");
        if (!exposesInventory) return "";
        return "El inventario exacto es interno. Elimina toda cantidad de stock, unidades disponibles o "
                + "existencias restantes. Conserva solamente los colores y tallas disponibles, el precio y el "
                + "siguiente paso natural de compra.";
    }

    private static boolean containsShippingContact(String response) {
        if (response == null || !response.contains("envios")) return false;
        return response.contains("comunicara") || response.contains("contactara")
                || response.contains("escribira") || response.contains("llamara")
                || response.contains("pondra en contacto") || response.contains("coordinara contigo");
    }

    private List<AgentMediaRequest> verifiedMedia(List<AgentMediaRequest> verified) {
        List<AgentMediaRequest> result = new ArrayList<>();
        if (verified != null) for (AgentMediaRequest item : verified) addMedia(result, item);
        return List.copyOf(result.stream().limit(5).toList());
    }

    private void addMedia(List<AgentMediaRequest> target, AgentMediaRequest item) {
        if (item == null) return;
        boolean duplicate = target.stream().anyMatch(existing -> existing.type() == item.type()
                && java.util.Objects.equals(existing.productId(), item.productId())
                && normalize(existing.color()).equals(normalize(item.color())));
        if (!duplicate) target.add(item);
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
