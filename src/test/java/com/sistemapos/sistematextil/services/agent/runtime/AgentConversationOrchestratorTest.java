package com.sistemapos.sistematextil.services.agent.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.sistemapos.sistematextil.services.agent.ai.AgentResponseGateway;
import com.sistemapos.sistematextil.services.agent.ai.AgentCommercialAction;
import com.sistemapos.sistematextil.services.agent.ai.AgentResponseGateway.AgentResponseResult;
import com.sistemapos.sistematextil.services.agent.ai.AgentConversationDecision;
import com.sistemapos.sistematextil.services.agent.ai.AgentDecisionIntent;
import com.sistemapos.sistematextil.services.agent.ai.AgentMediaRequest;
import com.sistemapos.sistematextil.services.agent.ai.AgentTurnResponse;
import com.sistemapos.sistematextil.services.agent.batch.AgentJobCoordinator;
import com.sistemapos.sistematextil.services.agent.batch.AgentJobStore;
import com.sistemapos.sistematextil.services.agent.batch.AgentJobStore.GeneratedTurn;
import com.sistemapos.sistematextil.services.agent.batch.AgentJobStore.JobLease;
import com.sistemapos.sistematextil.services.agent.batch.AgentMessageBatch;
import com.sistemapos.sistematextil.services.agent.batch.AgentMessageBatchFactory;
import com.sistemapos.sistematextil.services.agent.config.AgentRuntimeConfigService;
import com.sistemapos.sistematextil.services.agent.context.AgentContext;
import com.sistemapos.sistematextil.services.agent.context.AgentContextFactory;
import com.sistemapos.sistematextil.services.agent.prompt.AgentMainPrompt;
import com.sistemapos.sistematextil.services.agent.prompt.AgentMainPromptFactory;
import com.sistemapos.sistematextil.model.CrmWhatsappAgentState;

class AgentConversationOrchestratorTest {
    @Test
    void doesNotTreatACartReplacementAsAPostSaleChangeWithoutARegisteredSale() {
        AgentContext context = mock(AgentContext.class);
        AgentMessageBatch batch = new AgentMessageBatch(
                8L, 20L, 99L, List.of(), "Cámbialo por Anguie Liso gris oscuro talla M", false, false, false);

        assertFalse(AgentConversationOrchestrator.isPostSaleChangeRequest(context, batch));
    }

    @Test
    void doesNotTreatANewCartEditAsPostSaleWhenTheConversationHasAnOlderSale() {
        AgentContext context = mock(AgentContext.class);
        AgentContext.Fulfillment fulfillment = mock(AgentContext.Fulfillment.class);
        when(context.fulfillment()).thenReturn(fulfillment);
        when(fulfillment.saleId()).thenReturn(11305);
        when(context.state()).thenReturn(CrmWhatsappAgentState.ARMANDO_CARRITO);
        AgentMessageBatch batch = new AgentMessageBatch(
                8L, 20L, 99L, List.of(), "Cámbialo por Anguie Liso gris oscuro talla M", false, false, false);

        assertFalse(AgentConversationOrchestrator.isPostSaleChangeRequest(context, batch));
    }

    @Test
    void escalatesPostSaleChangesWithoutCallingGeminiOrCartTools() {
        Fixture fixture = new Fixture();
        AgentContext context = mock(AgentContext.class);
        AgentContext.Fulfillment fulfillment = mock(AgentContext.Fulfillment.class);
        when(fulfillment.saleId()).thenReturn(11305);
        when(context.fulfillment()).thenReturn(fulfillment);
        when(context.connectionId()).thenReturn(7L);
        when(context.conversationId()).thenReturn(20L);
        when(context.fingerprint()).thenReturn("post-sale-change");
        when(context.previousSummary()).thenReturn("Pedido 11305 emitido");
        when(fixture.contexts.build(8L, 99L)).thenReturn(context);
        when(fixture.batches.build(8L, 20L, 99L)).thenReturn(new AgentMessageBatch(
                8L, 20L, 99L, List.of(), "Quiero que me cambies de color", false, false, false));
        fixture.current(true, true, true);

        assertEquals(AgentConversationOrchestrator.Outcome.COMPLETED,
                fixture.orchestrator.process(fixture.lease));

        verify(fixture.grounding, never()).ground(any(), any(), any(), any());
        verify(fixture.gateway, never()).respond(any(), any(), any(Integer.class));
        ArgumentCaptor<GeneratedTurn> turn = ArgumentCaptor.forClass(GeneratedTurn.class);
        verify(fixture.jobs).saveGeneratedTurn(eq(41L), eq("worker:lease"), turn.capture(), any());
        assertTrue(turn.getValue().requiresHumanHelp());
        assertEquals("post-sale-change-guard", turn.getValue().model());
        assertEquals(AgentConversationOrchestrator.POST_SALE_CHANGE_MESSAGE,
                turn.getValue().customerMessage());
    }

    @Test
    void answersGeneralImagesWithoutCallingGemini() {
        Fixture fixture = new Fixture();
        fixture.current(true, true, true);
        fixture.batch("IMAGE", true);

        assertEquals(AgentConversationOrchestrator.Outcome.COMPLETED,
                fixture.orchestrator.process(fixture.lease));

        verify(fixture.gateway, never()).respond(any(), any(), any(Integer.class));
        ArgumentCaptor<GeneratedTurn> turn = ArgumentCaptor.forClass(GeneratedTurn.class);
        verify(fixture.jobs).saveGeneratedTurn(eq(41L), eq("worker:lease"), turn.capture(), any());
        assertEquals(AgentConversationOrchestrator.GENERAL_IMAGE_MESSAGE,
                turn.getValue().customerMessage());
    }

    @Test
    void answersGeneralDocumentsWithoutCallingGemini() {
        Fixture fixture = new Fixture();
        fixture.current(true, true, true);
        fixture.batch("DOCUMENT", false);

        assertEquals(AgentConversationOrchestrator.Outcome.COMPLETED,
                fixture.orchestrator.process(fixture.lease));

        verify(fixture.gateway, never()).respond(any(), any(), any(Integer.class));
        ArgumentCaptor<GeneratedTurn> turn = ArgumentCaptor.forClass(GeneratedTurn.class);
        verify(fixture.jobs).saveGeneratedTurn(eq(41L), eq("worker:lease"), turn.capture(), any());
        assertEquals(AgentConversationOrchestrator.GENERAL_DOCUMENT_MESSAGE,
                turn.getValue().customerMessage());
    }

    @Test
    void doesNotUseGeneralImageReplyForPaymentReceipts() {
        AgentMessageBatch batch = batch("IMAGE", true);

        assertEquals(null, AgentConversationOrchestrator.generalMediaMessage(
                batch, CrmWhatsappAgentState.ESPERANDO_COMPROBANTE));
        assertEquals(null, AgentConversationOrchestrator.generalMediaMessage(
                batch, CrmWhatsappAgentState.VALIDANDO_COMPROBANTE));
        assertEquals(null, AgentConversationOrchestrator.generalMediaMessage(
                batch, CrmWhatsappAgentState.ESPERANDO_PAGO_FILM));
    }

    @Test
    void letsTheAgentInterpretAnImageCaptionInsteadOfUsingTheGenericReply() {
        AgentMessageBatch batch = new AgentMessageBatch(8L, 20L, 99L,
                List.of(new AgentMessageBatch.Item(99L, "IMAGE",
                        "Quiero el conjunto Alice en guinda talla XS\n[IMAGEN ADJUNTA]",
                        true, false, false, LocalDateTime.now())),
                "Quiero el conjunto Alice en guinda talla XS\n[IMAGEN ADJUNTA]",
                true, false, false);

        assertEquals(null, AgentConversationOrchestrator.generalMediaMessage(
                batch, CrmWhatsappAgentState.EXPLORANDO_PRODUCTOS));
    }

    @Test
    void letsTheAgentInterpretTextSentNextToAnImage() {
        AgentMessageBatch batch = new AgentMessageBatch(8L, 20L, 100L,
                List.of(
                        new AgentMessageBatch.Item(99L, "IMAGE", "[IMAGEN ADJUNTA]",
                                true, false, false, LocalDateTime.now()),
                        new AgentMessageBatch.Item(100L, "TEXT", "¿A qué número se yapea?",
                                false, false, false, LocalDateTime.now())),
                "[IMAGEN ADJUNTA]\n¿A qué número se yapea?",
                true, false, false);

        assertEquals(null, AgentConversationOrchestrator.generalMediaMessage(
                batch, CrmWhatsappAgentState.EXPLORANDO_PRODUCTOS));
    }

    @Test
    void generatesPersistsFinalizesAndCompletesOneCurrentJob() {
        Fixture fixture = new Fixture();
        fixture.current(true, true, true);

        assertEquals(AgentConversationOrchestrator.Outcome.COMPLETED,
                fixture.orchestrator.process(fixture.lease));

        verify(fixture.gateway).respond(eq(7L), any(), eq(12));
        verify(fixture.jobs).saveGeneratedTurn(eq(41L), eq("worker:lease"), any(), any());
        verify(fixture.finalizer).finalizeTurn(eq(fixture.lease), any());
        verify(fixture.coordinator).complete(fixture.lease);
    }

    @Test
    void reusesPersistedGenerationAfterRestartWithoutCallingGeminiAgain() {
        Fixture fixture = new Fixture();
        GeneratedTurn stored = new GeneratedTurn("Respuesta guardada", "Resumen", false,
                "GEMINI", "gemini", 10, 2, "fingerprint", java.util.List.of());
        when(fixture.jobs.findGeneratedTurn(41L, "worker:lease")).thenReturn(Optional.of(stored));
        when(fixture.coordinator.isCurrent(fixture.lease)).thenReturn(true);
        when(fixture.coordinator.complete(fixture.lease)).thenReturn(true);

        assertEquals(AgentConversationOrchestrator.Outcome.COMPLETED,
                fixture.orchestrator.process(fixture.lease));

        verify(fixture.gateway, never()).respond(any(), any(), any(Integer.class));
        verify(fixture.finalizer).finalizeTurn(fixture.lease, stored);
    }

    @Test
    void discardsGeneratedReplyWhenANewerMessageMakesTheJobStale() {
        Fixture fixture = new Fixture();
        fixture.current(true, false);

        assertEquals(AgentConversationOrchestrator.Outcome.STALE,
                fixture.orchestrator.process(fixture.lease));

        verify(fixture.finalizer, never()).finalizeTurn(any(), any());
        verify(fixture.coordinator, never()).complete(any());
    }

    @Test
    void ignoresMediaRequestedByGeminiWhenBackendDidNotVerifyIt() {
        Fixture fixture = new Fixture();
        fixture.current(true, true, true);
        when(fixture.gateway.respond(eq(7L), any(), eq(12))).thenReturn(new AgentResponseResult(
                "GEMINI", "gemini", 100, 2, new AgentTurnResponse("Respuesta", "Resumen", false,
                        java.util.List.of(new AgentMediaRequest(
                                AgentMediaRequest.MediaType.PRODUCT_IMAGE, 45, "TOPO")))));

        assertEquals(AgentConversationOrchestrator.Outcome.COMPLETED,
                fixture.orchestrator.process(fixture.lease));

        ArgumentCaptor<GeneratedTurn> turn = ArgumentCaptor.forClass(GeneratedTurn.class);
        verify(fixture.jobs).saveGeneratedTurn(eq(41L), eq("worker:lease"), turn.capture(), any());
        assertTrue(turn.getValue().media().isEmpty());
    }

    @Test
    void sendsAtMostFiveBackendVerifiedProductImages() {
        Fixture fixture = new Fixture();
        fixture.current(true, true, true);
        AgentConversationDecision decision = new AgentConversationDecision(
                AgentDecisionIntent.CART_CHANGE, "agregar variantes", "MELINA", "", "", 0, "",
                java.util.List.of(),
                com.sistemapos.sistematextil.services.agent.ai.AgentCartAction.ADD_PRODUCT,
                AgentCommercialAction.ADD_PRODUCT,
                "", "", "", "", "", "", "", "",
                com.sistemapos.sistematextil.services.agent.ai.AgentCatalogAvailability.ALL, 99, false);
        java.util.List<AgentMediaRequest> verified = java.util.stream.IntStream.rangeClosed(1, 6)
                .mapToObj(index -> new AgentMediaRequest(
                        AgentMediaRequest.MediaType.PRODUCT_IMAGE, 78, "COLOR " + index))
                .toList();
        when(fixture.grounding.ground(eq(7L), eq(8L), eq(41L), any())).thenReturn(
                new AgentVerifiedGroundingService.Grounding(decision, "{}", verified, 6, true));

        assertEquals(AgentConversationOrchestrator.Outcome.COMPLETED,
                fixture.orchestrator.process(fixture.lease));

        ArgumentCaptor<GeneratedTurn> turn = ArgumentCaptor.forClass(GeneratedTurn.class);
        verify(fixture.jobs).saveGeneratedTurn(eq(41L), eq("worker:lease"), turn.capture(), any());
        assertEquals(5, turn.getValue().media().size());
        assertEquals("COLOR 5", turn.getValue().media().getLast().color());
    }

    @Test
    void wholesaleContactDoesNotPauseAutomaticRetailAttention() {
        Fixture fixture = new Fixture();
        fixture.current(true, true, true);
        AgentConversationDecision wholesale = new AgentConversationDecision(
                AgentDecisionIntent.SUPPORT_REQUEST, "venta mayorista", "", "", "", 0, "",
                java.util.List.of(),
                com.sistemapos.sistematextil.services.agent.ai.AgentCartAction.NONE,
                AgentCommercialAction.REQUEST_WHOLESALE_SUPPORT,
                "", "", "", "", "", "", "", "",
                com.sistemapos.sistematextil.services.agent.ai.AgentCatalogAvailability.ALL, 99, false);
        when(fixture.grounding.ground(eq(7L), eq(8L), eq(41L), any())).thenReturn(
                new AgentVerifiedGroundingService.Grounding(
                        wholesale, "{}", java.util.List.of(), 0, false));
        when(fixture.gateway.respond(eq(7L), any(), eq(12))).thenReturn(new AgentResponseResult(
                "GEMINI", "gemini", 100, 1,
                new AgentTurnResponse("Una asesora puede ayudarte con la venta por mayor.",
                        "Solicita venta mayorista", false, java.util.List.of())));

        assertEquals(AgentConversationOrchestrator.Outcome.COMPLETED,
                fixture.orchestrator.process(fixture.lease));

        ArgumentCaptor<GeneratedTurn> turn = ArgumentCaptor.forClass(GeneratedTurn.class);
        verify(fixture.jobs).saveGeneratedTurn(eq(41L), eq("worker:lease"), turn.capture(), any());
        assertFalse(turn.getValue().requiresHumanHelp());
    }

    @Test
    void asksGeminiToRewriteNaturallyWhenAProductPriceWasOmitted() {
        Fixture fixture = new Fixture();
        fixture.current(true, true, true);
        when(fixture.grounding.ground(eq(7L), eq(8L), eq(41L), any())).thenReturn(
                new AgentVerifiedGroundingService.Grounding(
                        new AgentConversationDecision(AgentDecisionIntent.PRODUCT_DETAILS,
                                "ver Emma", "Emma", null, null, null, null,
                                com.sistemapos.sistematextil.services.agent.ai.AgentCartAction.NONE,
                                "mostrar producto", 100, false),
                        "{\"selectedProduct\":{\"productId\":8,\"name\":\"EMMA\",\"priceFrom\":65.0}}",
                        java.util.List.of(), 2, false));
        when(fixture.gateway.respond(eq(7L), any(), eq(12)))
                .thenReturn(new AgentResponseResult("GEMINI", "gemini", 100, 2,
                        new AgentTurnResponse("Emma esta disponible.", "Busca Emma", false, java.util.List.of())))
                .thenReturn(new AgentResponseResult("GEMINI", "gemini", 90, 0,
                        new AgentTurnResponse("Emma esta disponible a S/65.00.", "Busca Emma", false,
                                java.util.List.of())));

        assertEquals(AgentConversationOrchestrator.Outcome.COMPLETED,
                fixture.orchestrator.process(fixture.lease));

        ArgumentCaptor<AgentMainPrompt> prompts = ArgumentCaptor.forClass(AgentMainPrompt.class);
        verify(fixture.gateway, times(2)).respond(eq(7L), prompts.capture(), eq(12));
        assertTrue(prompts.getAllValues().get(1).userMessage().contains("CORRECCION_OBLIGATORIA_ANTES_DE_ENVIAR"));
        ArgumentCaptor<GeneratedTurn> turn = ArgumentCaptor.forClass(GeneratedTurn.class);
        verify(fixture.jobs).saveGeneratedTurn(eq(41L), eq("worker:lease"), turn.capture(), any());
        assertEquals("Emma esta disponible a S/65.00.", turn.getValue().customerMessage());
    }

    @Test
    void rewritesAFalseImageDeliveryClaimWhenNoMediaWasVerified() {
        Fixture fixture = new Fixture();
        fixture.current(true, true, true);
        when(fixture.grounding.ground(eq(7L), eq(8L), eq(41L), any())).thenReturn(
                new AgentVerifiedGroundingService.Grounding(
                        new AgentConversationDecision(AgentDecisionIntent.PRODUCT_DETAILS,
                                "mostrar fotos", "Alessia", "", "", 0, "",
                                com.sistemapos.sistematextil.services.agent.ai.AgentCartAction.NONE,
                                AgentCommercialAction.SHOW_PRODUCT_IMAGES,
                                "", "", "", "", "", "", "", "",
                                com.sistemapos.sistematextil.services.agent.ai.AgentCatalogAvailability.ALL,
                                100, false),
                        "{\"productSelectionRequiredBeforeColorImage\":true,\"verifiedMediaCount\":0}",
                        java.util.List.of(), 1, false));
        when(fixture.gateway.respond(eq(7L), any(), eq(12)))
                .thenReturn(new AgentResponseResult("GEMINI", "gemini", 100, 2,
                        new AgentTurnResponse("Aqui tienes las imagenes de Alessia.", "Fotos", false,
                                java.util.List.of())))
                .thenReturn(new AgentResponseResult("GEMINI", "gemini", 90, 0,
                        new AgentTurnResponse(
                                "Tengo Alessia Entero y Alessia Rayas. ¿Cual modelo deseas ver?",
                                "Fotos", false, java.util.List.of())));

        assertEquals(AgentConversationOrchestrator.Outcome.COMPLETED,
                fixture.orchestrator.process(fixture.lease));

        verify(fixture.gateway, times(2)).respond(eq(7L), any(), eq(12));
        ArgumentCaptor<GeneratedTurn> turn = ArgumentCaptor.forClass(GeneratedTurn.class);
        verify(fixture.jobs).saveGeneratedTurn(eq(41L), eq("worker:lease"), turn.capture(), any());
        assertEquals("Tengo Alessia Entero y Alessia Rayas. ¿Cual modelo deseas ver?",
                turn.getValue().customerMessage());
    }

    @Test
    void rewritesCatalogReplyWhenTheWebGalleryWasOmitted() {
        Fixture fixture = new Fixture();
        fixture.current(true, true, true);
        AgentConversationDecision catalog = new AgentConversationDecision(
                AgentDecisionIntent.PRODUCT_SEARCH, "mostrar catalogo", "", "", "", 0, "",
                java.util.List.of(),
                com.sistemapos.sistematextil.services.agent.ai.AgentCartAction.NONE,
                AgentCommercialAction.SHOW_PRODUCTS,
                "", "", "", "", "", "", "", "",
                com.sistemapos.sistematextil.services.agent.ai.AgentCatalogAvailability.ALL, 99, false);
        when(fixture.grounding.ground(eq(7L), eq(8L), eq(41L), any())).thenReturn(
                new AgentVerifiedGroundingService.Grounding(
                        catalog,
                        "{\"products\":[{\"name\":\"EMMA\",\"priceFrom\":65.0}]}",
                        java.util.List.of(), 1, false));
        when(fixture.gateway.respond(eq(7L), any(), eq(12)))
                .thenReturn(new AgentResponseResult("GEMINI", "gemini", 100, 1,
                        new AgentTurnResponse("Emma esta disponible a S/65.00.", "Catalogo", false,
                                java.util.List.of())))
                .thenReturn(new AgentResponseResult("GEMINI", "gemini", 90, 0,
                        new AgentTurnResponse(
                                "Emma esta disponible a S/65.00. Mira la galeria completa en https://kiments.com.pe/productos",
                                "Catalogo", false, java.util.List.of())));

        assertEquals(AgentConversationOrchestrator.Outcome.COMPLETED,
                fixture.orchestrator.process(fixture.lease));

        verify(fixture.gateway, times(2)).respond(eq(7L), any(), eq(12));
        ArgumentCaptor<GeneratedTurn> turn = ArgumentCaptor.forClass(GeneratedTurn.class);
        verify(fixture.jobs).saveGeneratedTurn(eq(41L), eq("worker:lease"), turn.capture(), any());
        assertTrue(turn.getValue().customerMessage().contains("https://kiments.com.pe/productos"));
    }

    @Test
    void validatesReadyStockDeliveryGuidanceForAnAgentSale() {
        var grounding = deliveryGrounding("READY_STOCK");

        assertEquals("", AgentConversationOrchestrator.deliveryTimingCorrectionInstruction(
                grounding,
                "Tu pedido se despacha aproximadamente en 1 a 3 días. El área de envíos se comunicará contigo."));
        assertFalse(AgentConversationOrchestrator.deliveryTimingCorrectionInstruction(
                grounding, "Mañana saldrá tu pedido.").isBlank());
    }

    @Test
    void preventsAnExactDateForARegisteredPreorder() {
        var grounding = deliveryGrounding("PREORDER");

        assertEquals("", AgentConversationOrchestrator.deliveryTimingCorrectionInstruction(
                grounding,
                "Tu pedido se enviará según la fecha indicada en la preventa y el área de envíos se comunicará contigo."));
        assertFalse(AgentConversationOrchestrator.deliveryTimingCorrectionInstruction(
                grounding,
                "Tu pedido saldrá el 21 de octubre y el área de envíos se comunicará contigo.").isBlank());
    }

    @Test
    void refersAnOrderNotLinkedToTheAgentWithoutPromisingShippingContact() {
        var grounding = deliveryGrounding("NOT_LINKED_TO_AGENT");

        assertEquals("", AgentConversationOrchestrator.deliveryTimingCorrectionInstruction(
                grounding, "Para revisar ese pedido, comunícate con nuestra asesora al 933918047."));
        assertFalse(AgentConversationOrchestrator.deliveryTimingCorrectionInstruction(
                grounding, "El área de envíos se comunicará contigo en 1 a 3 días.").isBlank());
    }

    private AgentVerifiedGroundingService.Grounding deliveryGrounding(String policy) {
        AgentConversationDecision decision = new AgentConversationDecision(
                AgentDecisionIntent.DELIVERY_METHOD, "consultar pedido", "", "", "", 0, "",
                java.util.List.of(),
                com.sistemapos.sistematextil.services.agent.ai.AgentCartAction.NONE,
                AgentCommercialAction.REFER_EXISTING_ORDER_DELIVERY,
                "", "", "", "", "", "", "", "",
                com.sistemapos.sistematextil.services.agent.ai.AgentCatalogAvailability.ALL, 99, false);
        return new AgentVerifiedGroundingService.Grounding(
                decision, "{\"orderDeliveryGuidance\":{\"policy\":\"" + policy + "\"}}",
                java.util.List.of(), 0, false);
    }

    private static final class Fixture {
        final AgentJobCoordinator coordinator = mock(AgentJobCoordinator.class);
        final AgentJobStore jobs = mock(AgentJobStore.class);
        final AgentMessageBatchFactory batches = mock(AgentMessageBatchFactory.class);
        final AgentContextFactory contexts = mock(AgentContextFactory.class);
        final AgentMainPromptFactory prompts = mock(AgentMainPromptFactory.class);
        final AgentVerifiedGroundingService grounding = mock(AgentVerifiedGroundingService.class);
        final AgentResponseGateway gateway = mock(AgentResponseGateway.class);
        final AgentRuntimeConfigService runtime = mock(AgentRuntimeConfigService.class);
        final AgentTurnFinalizer finalizer = mock(AgentTurnFinalizer.class);
        final JobLease lease = new JobLease(41L, 8L, 99L, 1, "worker:lease",
                LocalDateTime.now().plusMinutes(1));
        final AgentConversationOrchestrator orchestrator = new AgentConversationOrchestrator(
                coordinator, jobs, batches, contexts, prompts, grounding, gateway, runtime, finalizer);

        Fixture() {
            AgentContext context = mock(AgentContext.class);
            when(context.connectionId()).thenReturn(7L);
            when(context.conversationId()).thenReturn(20L);
            when(context.fingerprint()).thenReturn("fingerprint");
            when(contexts.build(8L, 99L)).thenReturn(context);
            when(batches.build(8L, 20L, 99L)).thenReturn(new AgentMessageBatch(
                    8L, 20L, 99L, java.util.List.of(), "Hola quiero Emma", false, false, false));
            when(prompts.create(context)).thenReturn(new AgentMainPrompt("v1", "system", "user",
                    Map.of("agentSessionId", 8L)));
            when(grounding.ground(eq(7L), eq(8L), eq(41L), any())).thenReturn(
                    new AgentVerifiedGroundingService.Grounding(
                            new AgentConversationDecision(AgentDecisionIntent.PRODUCT_DETAILS,
                                    "ver Emma", "Emma", null, null, null, null,
                                    com.sistemapos.sistematextil.services.agent.ai.AgentCartAction.NONE,
                                    "mostrar producto", 100, false),
                            "{}", java.util.List.of(), 2, false));
            when(runtime.forSession(8L)).thenReturn(new AgentRuntimeConfigService.RuntimeConfig(false,
                    com.sistemapos.sistematextil.model.CrmWhatsappAgentActivationStage.LOCAL_TEST,
                    java.util.Set.of(), 10, 5000, 240, 85, false, 3, 5, 15, 12, 6));
            when(gateway.respond(eq(7L), any(), eq(12))).thenReturn(new AgentResponseResult(
                    "GEMINI", "gemini", 100, 2, new AgentTurnResponse("Claro", "Busca Emma", false,
                            java.util.List.of())));
            when(jobs.findGeneratedTurn(41L, "worker:lease")).thenReturn(Optional.empty());
        }

        void batch(String messageType, boolean image) {
            when(batches.build(8L, 20L, 99L)).thenReturn(
                    AgentConversationOrchestratorTest.batch(messageType, image));
        }

        void current(boolean... values) {
            Boolean[] boxed = new Boolean[values.length];
            for (int i = 0; i < values.length; i++) boxed[i] = values[i];
            when(coordinator.isCurrent(lease)).thenReturn(boxed[0], java.util.Arrays.copyOfRange(boxed, 1, boxed.length));
            when(jobs.saveGeneratedTurn(eq(41L), eq("worker:lease"), any(), any())).thenReturn(true);
            when(coordinator.complete(lease)).thenReturn(values.length > 2 && values[2]);
        }
    }

    private static AgentMessageBatch batch(String messageType, boolean image) {
        return new AgentMessageBatch(8L, 20L, 99L,
                List.of(new AgentMessageBatch.Item(99L, messageType,
                        image ? "[IMAGEN ADJUNTA]" : "[DOCUMENT ADJUNTO]",
                        image, false, false, LocalDateTime.now())),
                image ? "[IMAGEN ADJUNTA]" : "[DOCUMENT ADJUNTO]",
                image, false, false);
    }
}
