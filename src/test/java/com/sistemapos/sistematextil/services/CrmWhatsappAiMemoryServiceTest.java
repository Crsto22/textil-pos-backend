package com.sistemapos.sistematextil.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.sistemapos.sistematextil.model.CrmWhatsappAiAttentionState;
import com.sistemapos.sistematextil.model.CrmWhatsappAiMemory;
import com.sistemapos.sistematextil.model.CrmWhatsappAiPendingQuestion;
import com.sistemapos.sistematextil.model.CrmWhatsappAiRun;
import com.sistemapos.sistematextil.model.CrmWhatsappAiRunOutcome;
import com.sistemapos.sistematextil.model.CrmWhatsappConversation;
import com.sistemapos.sistematextil.model.CrmWhatsappMessage;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAiDeliveryRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAiJobRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAiMemoryRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappConversationRepository;
import com.sistemapos.sistematextil.services.CrmWhatsappAiEngineService.ProcessingResult;
import com.sistemapos.sistematextil.services.ai.AiModelProvider.Usage;

class CrmWhatsappAiMemoryServiceTest {

    private final CrmWhatsappAiMemoryRepository memories = mock(CrmWhatsappAiMemoryRepository.class);
    private final CrmWhatsappConversationRepository conversations = mock(CrmWhatsappConversationRepository.class);
    private final CrmWhatsappAiJobRepository jobs = mock(CrmWhatsappAiJobRepository.class);
    private final CrmWhatsappAiDeliveryRepository deliveries = mock(CrmWhatsappAiDeliveryRepository.class);
    private final CrmWhatsappEventService events = mock(CrmWhatsappEventService.class);
    private final CrmWhatsappAiSaleDraftService saleDrafts = mock(CrmWhatsappAiSaleDraftService.class);
    private final CrmWhatsappAiMemoryService service = new CrmWhatsappAiMemoryService(
            memories, conversations, jobs, deliveries, events, saleDrafts);

    @BeforeEach
    void lockConversation() {
        when(conversations.findForUpdateById(anyLong())).thenAnswer(invocation -> {
            CrmWhatsappConversation conversation = new CrmWhatsappConversation();
            conversation.setIdConversation(invocation.getArgument(0));
            conversation.setStatus("ESPERA");
            return Optional.of(conversation);
        });
    }

    @Test
    void bloqueaLaConversacionAntesDeCrearLaPrimeraMemoria() {
        CrmWhatsappConversation conversation = new CrmWhatsappConversation();
        conversation.setIdConversation(10L);
        conversation.setStatus("ESPERA");
        CrmWhatsappMessage message = new CrmWhatsappMessage();
        message.setIdMessage(20L);
        when(conversations.findForUpdateById(10L)).thenReturn(Optional.of(conversation));
        when(memories.findForUpdate(10L)).thenReturn(Optional.empty());
        when(memories.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.registerIncoming(conversation, message);

        InOrder lockOrder = inOrder(conversations, memories);
        lockOrder.verify(conversations).findForUpdateById(10L);
        lockOrder.verify(memories).findForUpdate(10L);
        verify(memories).save(any(CrmWhatsappAiMemory.class));
    }

    @Test
    void conservaColorCuandoElSiguienteMensajeSoloIndicaLaTalla() {
        CrmWhatsappConversation conversation = new CrmWhatsappConversation();
        conversation.setIdConversation(10L);
        conversation.setStatus("ESPERA");
        CrmWhatsappAiMemory memory = new CrmWhatsappAiMemory();
        memory.setConversation(conversation);
        memory.setAttentionState(CrmWhatsappAiAttentionState.AUTOMATICA);
        memory.setConsecutiveAutoResponses(0);
        memory.setExpiresAt(LocalDateTime.now().plusHours(24));
        when(memories.findForUpdate(10L)).thenReturn(Optional.of(memory));
        when(memories.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.updateFromRun(run(conversation, 20L, "Beige"), result(productEvidence()));

        assertEquals("MELISSA", memory.getProductName());
        assertEquals("Beige", memory.getColor());
        assertEquals("", value(memory.getSize()));
        assertNull(memory.getVariantId());

        service.updateFromRun(run(conversation, 21L, "XS"), result(productEvidence()));

        assertEquals("Beige", memory.getColor());
        assertEquals("XS", memory.getSize());
        assertEquals(101, memory.getVariantId());
    }

    @Test
    void unaEjecucionAntiguaNoSobrescribeLaSeleccionReciente() {
        CrmWhatsappConversation conversation = new CrmWhatsappConversation();
        conversation.setIdConversation(10L);
        CrmWhatsappAiMemory memory = new CrmWhatsappAiMemory();
        memory.setConversation(conversation);
        memory.setAttentionState(CrmWhatsappAiAttentionState.AUTOMATICA);
        memory.setConsecutiveAutoResponses(0);
        memory.setExpiresAt(LocalDateTime.now().plusHours(24));
        memory.setLastIncomingMessageId(30L);
        memory.setProductName("MELISSA");
        memory.setColor("Beige");
        memory.setSize("XS");
        when(memories.findForUpdate(10L)).thenReturn(Optional.of(memory));

        service.updateFromRun(run(conversation, 29L, "Negro"), result(productEvidence()));

        assertEquals("Beige", memory.getColor());
        assertEquals("XS", memory.getSize());
        assertEquals(30L, memory.getLastIncomingMessageId());
    }

    @Test
    void unaCantidadPendienteSeAplicaUnaSolaVezSinConsultarAlModelo() {
        CrmWhatsappConversation conversation = new CrmWhatsappConversation();
        conversation.setIdConversation(10L);
        conversation.setStatus("ESPERA");
        CrmWhatsappAiMemory memory = memory(conversation, CrmWhatsappAiPendingQuestion.QUANTITY);
        memory.setProductName("MELISSA");
        memory.setColor("AZUL");
        memory.setSize("XS");
        when(memories.findForUpdate(10L)).thenReturn(Optional.of(memory));
        when(memories.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(saleDrafts.applyAiAction(any(), any())).thenReturn(
                new CrmWhatsappAiSaleDraftService.ActionOutcome(
                        "Resumen de tu pedido:\n- 1 x MELISSA AZUL talla XS\n¿Confirmas este pedido?",
                        false, null));

        var first = service.resolvePendingReply(conversation, 40L, "Solo quiero una unidad");
        var repeated = service.resolvePendingReply(conversation, 40L, "Solo quiero una unidad");

        assertEquals("MODIFICAR_CARRITO", first.intent());
        assertEquals(first.response(), repeated.response());
        assertEquals(CrmWhatsappAiPendingQuestion.ORDER_CONFIRMATION, memory.getPendingQuestion());
        verify(saleDrafts, times(1)).applyAiAction(any(), any());
    }

    @Test
    void perfectoConfirmaElResumenPendiente() {
        CrmWhatsappConversation conversation = new CrmWhatsappConversation();
        conversation.setIdConversation(10L);
        conversation.setStatus("ESPERA");
        CrmWhatsappAiMemory memory = memory(conversation, CrmWhatsappAiPendingQuestion.ORDER_CONFIRMATION);
        when(memories.findForUpdate(10L)).thenReturn(Optional.of(memory));
        when(memories.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(saleDrafts.applyAiAction(any(), any())).thenReturn(
                new CrmWhatsappAiSaleDraftService.ActionOutcome(
                        "¿Cómo deseas pagar? Opciones: YAPE, PLIN.", false, null));

        var result = service.resolvePendingReply(conversation, 41L, "Perfecto");

        assertEquals("CONFIRMAR_PEDIDO", result.intent());
        assertEquals(CrmWhatsappAiPendingQuestion.PAYMENT_METHOD, memory.getPendingQuestion());
        verify(saleDrafts, times(1)).applyAiAction(any(), any());
    }

    @Test
    void siDespuesDeProductoNoEncontradoSolicitaElCatalogoReal() {
        CrmWhatsappConversation conversation = new CrmWhatsappConversation();
        conversation.setIdConversation(10L);
        CrmWhatsappAiMemory memory = memory(conversation, CrmWhatsappAiPendingQuestion.CATALOG_CONFIRMATION);
        when(memories.findForUpdate(10L)).thenReturn(Optional.of(memory));

        var result = service.resolvePendingReply(conversation, 42L, "Si por favor");

        assertEquals("PRODUCTOS", result.intent());
        assertEquals(true, result.showCatalog());
        assertEquals("", result.response());
    }

    @Test
    void modeloElegidoDelCatalogoSeConsultaDirectamenteSinGemini() {
        CrmWhatsappConversation conversation = new CrmWhatsappConversation();
        conversation.setIdConversation(10L);
        CrmWhatsappAiMemory memory = memory(conversation, CrmWhatsappAiPendingQuestion.CATALOG_PRODUCT);
        when(memories.findForUpdate(10L)).thenReturn(Optional.of(memory));
        when(saleDrafts.matchesCatalogProduct(conversation, "Julieta por favor")).thenReturn(true);

        var result = service.resolvePendingReply(conversation, 43L, "Julieta por favor");

        assertEquals("PRODUCTOS", result.intent());
        assertEquals("Julieta por favor", result.catalogQuery());
        assertEquals(true, result.requestsCatalog());
        assertEquals("", result.response());
    }

    @Test
    void unaConsultaNuevaNoSeFuerzaComoRespuestaDeCantidad() {
        CrmWhatsappConversation conversation = new CrmWhatsappConversation();
        conversation.setIdConversation(10L);
        CrmWhatsappAiMemory memory = memory(conversation, CrmWhatsappAiPendingQuestion.QUANTITY);
        memory.setProductName("ALICE LISO");
        memory.setColor("CHOCOLATE");
        memory.setSize("M");
        when(memories.findForUpdate(10L)).thenReturn(Optional.of(memory));

        var result = service.resolvePendingReply(conversation, 45L, "¿Qué promociones tienen?");

        assertNull(result);
        verify(saleDrafts, times(0)).applyAiAction(any(), any());
    }

    @Test
    void unaConsultaDeProductoNoConfirmaNiCancelaElPedidoPendiente() {
        CrmWhatsappConversation conversation = new CrmWhatsappConversation();
        conversation.setIdConversation(10L);
        CrmWhatsappAiMemory memory = memory(conversation, CrmWhatsappAiPendingQuestion.ORDER_CONFIRMATION);
        when(memories.findForUpdate(10L)).thenReturn(Optional.of(memory));

        var result = service.resolvePendingReply(conversation, 46L, "Quiero Alice Rayas");

        assertNull(result);
        verify(saleDrafts, times(0)).applyAiAction(any(), any());
    }

    @Test
    void unaConsultaSecundariaConservaLaConfirmacionPendiente() {
        CrmWhatsappConversation conversation = new CrmWhatsappConversation();
        conversation.setIdConversation(10L);
        CrmWhatsappAiMemory memory = memory(conversation, CrmWhatsappAiPendingQuestion.ORDER_CONFIRMATION);
        when(memories.findForUpdate(10L)).thenReturn(Optional.of(memory));
        when(memories.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        ProcessingResult shipping = new ProcessingResult(
                CrmWhatsappAiRunOutcome.DRAFT_READY, "ENVIOS", 100, false, "", "Realizamos envíos.",
                List.of(), List.of(), List.of(), Usage.empty(), 0L, false);

        service.updateFromRun(run(conversation, 47L, "¿Hacen envíos?"), shipping);

        assertEquals(CrmWhatsappAiPendingQuestion.ORDER_CONFIRMATION, memory.getPendingQuestion());
    }

    @Test
    void recuerdaElSaludoDuranteLaSesionActual() {
        CrmWhatsappConversation conversation = new CrmWhatsappConversation();
        conversation.setIdConversation(10L);
        CrmWhatsappAiMemory memory = memory(conversation, CrmWhatsappAiPendingQuestion.NONE);
        when(memories.findForUpdate(10L)).thenReturn(Optional.of(memory));
        when(memories.findByConversation_IdConversation(10L)).thenReturn(Optional.of(memory));
        when(memories.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        ProcessingResult greeting = new ProcessingResult(
                CrmWhatsappAiRunOutcome.DRAFT_READY, "SALUDO", 100, false, "", "Hola",
                List.of(), List.of(), List.of(), Usage.empty(), 0L, false);

        service.updateFromRun(run(conversation, 44L, "Hola"), greeting);

        assertTrue(service.greetingSentInCurrentSession(10L));
    }

    private CrmWhatsappAiMemory memory(
            CrmWhatsappConversation conversation, CrmWhatsappAiPendingQuestion pendingQuestion) {
        CrmWhatsappAiMemory memory = new CrmWhatsappAiMemory();
        memory.setConversation(conversation);
        memory.setAttentionState(CrmWhatsappAiAttentionState.AUTOMATICA);
        memory.setPendingQuestion(pendingQuestion);
        memory.setConsecutiveAutoResponses(0);
        memory.setExpiresAt(LocalDateTime.now().plusHours(24));
        return memory;
    }

    private CrmWhatsappAiRun run(CrmWhatsappConversation conversation, Long messageId, String body) {
        CrmWhatsappMessage message = new CrmWhatsappMessage();
        message.setIdMessage(messageId);
        message.setBody(body);
        CrmWhatsappAiRun run = new CrmWhatsappAiRun();
        run.setConversation(conversation);
        run.setMessage(message);
        return run;
    }

    private ProcessingResult result(List<Map<String, Object>> evidence) {
        return new ProcessingResult(CrmWhatsappAiRunOutcome.DRAFT_READY, "STOCK", 100, false,
                "", "", List.<Map<String, Object>>of(), evidence, List.of(),
                new Usage(null, null, null), 0L, false);
    }

    private List<Map<String, Object>> productEvidence() {
        Map<String, Object> product = Map.of(
                "productId", 7,
                "name", "MELISSA",
                "variants", List.of(
                        Map.of("variantId", 101, "color", "Beige", "size", "XS", "stock", 4),
                        Map.of("variantId", 102, "color", "Beige", "size", "S", "stock", 3),
                        Map.of("variantId", 103, "color", "Negro", "size", "XS", "stock", 2)));
        return List.of(Map.of("tool", "buscar_productos", "products", List.of(product)));
    }

    private String value(String value) {
        return value == null ? "" : value;
    }
}
