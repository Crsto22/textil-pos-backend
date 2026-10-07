package com.sistemapos.sistematextil.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalTime;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import com.sistemapos.sistematextil.model.CrmWhatsappAiConfig;
import com.sistemapos.sistematextil.model.CrmWhatsappAiAttentionMode;
import com.sistemapos.sistematextil.model.CrmWhatsappAiJob;
import com.sistemapos.sistematextil.model.CrmWhatsappAiJobStatus;
import com.sistemapos.sistematextil.model.CrmWhatsappAiMode;
import com.sistemapos.sistematextil.model.CrmWhatsappAiPendingQuestion;
import com.sistemapos.sistematextil.model.CrmWhatsappAiRun;
import com.sistemapos.sistematextil.model.CrmWhatsappAiRunOutcome;
import com.sistemapos.sistematextil.model.CrmWhatsappAiTone;
import com.sistemapos.sistematextil.model.CrmWhatsappConnection;
import com.sistemapos.sistematextil.model.CrmWhatsappConversation;
import com.sistemapos.sistematextil.model.CrmWhatsappMessage;
import com.sistemapos.sistematextil.model.CrmWhatsappPaymentEvidence;
import com.sistemapos.sistematextil.model.CrmWhatsappWaitingReason;
import com.sistemapos.sistematextil.model.Empresa;
import com.sistemapos.sistematextil.model.Sucursal;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAiConfigRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAiJobRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAiRunRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappMessageRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappPaymentEvidenceRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAiProductQueryRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappConversationRepository;
import com.sistemapos.sistematextil.services.ai.AiModelProvider;
import com.sistemapos.sistematextil.services.ai.AiModelProvider.AudioTranscriptionResult;
import com.sistemapos.sistematextil.services.ai.AiModelProvider.ClassificationResult;
import com.sistemapos.sistematextil.services.ai.AiModelProvider.DraftResult;
import com.sistemapos.sistematextil.services.ai.AiModelProvider.MediaSuggestion;
import com.sistemapos.sistematextil.services.ai.AiModelProvider.SaleActionResult;
import com.sistemapos.sistematextil.services.ai.AiModelProvider.ToolCall;
import com.sistemapos.sistematextil.services.ai.AiModelProvider.Usage;
import com.sistemapos.sistematextil.services.ai.AiProviderException;
import com.sistemapos.sistematextil.services.CrmWhatsappAiToolService.ExecutionResult;
import com.sistemapos.sistematextil.services.CrmWhatsappAiToolService.MediaReference;
import com.sistemapos.sistematextil.services.CrmWhatsappAiMemoryService.MemorySelection;
import com.sistemapos.sistematextil.services.CrmWhatsappAiMemoryService.PendingReplyResolution;

class CrmWhatsappAiEngineServiceTest {

    private final CrmWhatsappAiJobRepository jobs = mock(CrmWhatsappAiJobRepository.class);
    private final CrmWhatsappAiRunRepository runs = mock(CrmWhatsappAiRunRepository.class);
    private final CrmWhatsappAiConfigRepository configs = mock(CrmWhatsappAiConfigRepository.class);
    private final CrmWhatsappMessageRepository messages = mock(CrmWhatsappMessageRepository.class);
    private final CrmWhatsappPaymentEvidenceRepository paymentEvidences = mock(CrmWhatsappPaymentEvidenceRepository.class);
    private final CrmWhatsappEcommerceOrderParser ecommerceOrderParser = new CrmWhatsappEcommerceOrderParser();
    private final CrmWhatsappAiToolService tools = mock(CrmWhatsappAiToolService.class);
    private final AiModelProvider provider = mock(AiModelProvider.class);
    private final CrmWhatsappEventService events = mock(CrmWhatsappEventService.class);
    private final CrmWhatsappAiMemoryService memory = mock(CrmWhatsappAiMemoryService.class);
    private final CrmWhatsappAiHandoffService handoff = mock(CrmWhatsappAiHandoffService.class);
    private final CrmWhatsappAiDeliveryService delivery = mock(CrmWhatsappAiDeliveryService.class);
    private final CrmWhatsappAiSaleDraftService saleDrafts = mock(CrmWhatsappAiSaleDraftService.class);
    private final CrmWhatsappAiOperationsService operations = mock(CrmWhatsappAiOperationsService.class);
    private final CrmWhatsappAiSafetyService safety = mock(CrmWhatsappAiSafetyService.class);
    private final CrmWhatsappAiProductQueryRepository productQueries = mock(CrmWhatsappAiProductQueryRepository.class);
    private final CrmWhatsappAiAuditService audit = mock(CrmWhatsappAiAuditService.class);
    private final CrmWhatsappConversationRepository conversations = mock(CrmWhatsappConversationRepository.class);
    private final S3StorageService storage = mock(S3StorageService.class);
    private final CrmWhatsappAiEngineService service = new CrmWhatsappAiEngineService(
            jobs, runs, configs, messages, paymentEvidences, ecommerceOrderParser, tools, provider, events, memory, handoff, delivery, saleDrafts,
            operations, safety, productQueries, audit, conversations, storage);

    @BeforeEach
    void setupOperations() { when(operations.automaticAllowedForConversation(any(), any())).thenReturn(true); }

    @Test
    void agrupaMensajesEntrantesConsecutivosEnOrdenCronologico() {
        CrmWhatsappAiJob job = job("talla M");
        LocalDateTime now = LocalDateTime.of(2026, 10, 5, 12, 0, 6);
        job.getMessage().setCreatedAt(now);
        CrmWhatsappMessage color = incomingMessage(job.getConversation(), 19L, "en color negro", now.minusSeconds(2));
        CrmWhatsappMessage product = incomingMessage(job.getConversation(), 18L, "Quiero el vestido Alice", now.minusSeconds(4));
        CrmWhatsappMessage greeting = incomingMessage(job.getConversation(), 17L, "Hola bella", now.minusSeconds(6));
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(config()));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findActiveMessagesEndingAt(any(), any(), any()))
                .thenReturn(List.of(job.getMessage(), color, product, greeting));

        var prepared = service.prepare(50L);

        assertEquals("Hola bella\nQuiero el vestido Alice\nen color negro\ntalla M", prepared.latestMessage());
    }

    @Test
    void nuevaCompraNoRecibeElContextoAnteriorALaVentaCompletada() {
        CrmWhatsappAiJob job = job("Quiero Emma");
        LocalDateTime now = LocalDateTime.of(2026, 10, 6, 15, 0);
        job.getMessage().setCreatedAt(now);
        CrmWhatsappMessage saleBoundary = incomingMessage(job.getConversation(), 29L,
                "El cliente realizo una compra por S/75.00.", now.minusMinutes(1));
        saleBoundary.setDirection("SYSTEM");
        saleBoundary.setOrigin("CRM_SYSTEM");
        saleBoundary.setRelatedSaleId(501);
        CrmWhatsappMessage oldReply = incomingMessage(job.getConversation(), 28L,
                "Tu pedido de ALESSIA ENTERO quedó registrado.", now.minusMinutes(2));
        oldReply.setDirection("OUTGOING");
        CrmWhatsappMessage oldRequest = incomingMessage(job.getConversation(), 27L,
                "Quiero Alessia marron talla M", now.minusMinutes(3));
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(config()));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findRecentActiveMessages(any(), any()))
                .thenReturn(List.of(job.getMessage(), saleBoundary, oldReply, oldRequest));

        var prepared = service.prepare(50L);

        assertTrue(prepared.conversationContext().contains("Quiero Emma"));
        assertFalse(prepared.conversationContext().contains("ALESSIA"), prepared.conversationContext());
        assertFalse(prepared.conversationContext().contains("marron"), prepared.conversationContext());
        assertFalse(prepared.conversationContext().contains("S/75.00"), prepared.conversationContext());
    }

    @Test
    void contextoOmiteAvisosYSugerenciasAutomaticasPeroConservaClienteYRespuestaIa() {
        CrmWhatsappAiJob job = job("Que tal");
        CrmWhatsappMessage welcome = incomingMessage(job.getConversation(), 19L,
                "Antes de comprar en Kiments enviamos por Shalom", LocalDateTime.now().minusSeconds(4));
        welcome.setDirection("OUTGOING");
        welcome.setOrigin("AI_AUTOMATIC");
        CrmWhatsappMessage suggestion = incomingMessage(job.getConversation(), 18L,
                "Aprovecha nuestro nuevo conjunto", LocalDateTime.now().minusSeconds(8));
        suggestion.setDirection("OUTGOING");
        suggestion.setOrigin("AI_AUTOMATIC");
        CrmWhatsappMessage aiReply = incomingMessage(job.getConversation(), 17L,
                "Hola, bella. En que puedo ayudarte?", LocalDateTime.now().minusSeconds(12));
        aiReply.setDirection("OUTGOING");
        aiReply.setOrigin("AI_AUTOMATIC");
        CrmWhatsappMessage humanReply = incomingMessage(job.getConversation(), 16L,
                "Mensaje escrito por una asesora", LocalDateTime.now().minusSeconds(16));
        humanReply.setDirection("OUTGOING");
        humanReply.setOrigin("HUMAN");
        CrmWhatsappMessage customer = incomingMessage(job.getConversation(), 15L,
                "Hola", LocalDateTime.now().minusSeconds(20));
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(config()));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findRecentActiveMessages(any(), any())).thenReturn(
                List.of(job.getMessage(), welcome, suggestion, aiReply, humanReply, customer));
        when(delivery.messageIdsExcludedFromAiContext(10L)).thenReturn(Set.of(18L, 19L));

        var prepared = service.prepare(50L);

        assertTrue(prepared.conversationContext().contains("Que tal"));
        assertTrue(prepared.conversationContext().contains("Hola"));
        assertTrue(prepared.conversationContext().contains("En que puedo ayudarte"));
        assertFalse(prepared.conversationContext().contains("Antes de comprar"));
        assertFalse(prepared.conversationContext().contains("Shalom"));
        assertFalse(prepared.conversationContext().contains("nuevo conjunto"));
        assertFalse(prepared.conversationContext().contains("escrito por una asesora"));
    }

    @Test
    void unMensajeNuevoConElMismoTextoSeProcesaNuevamente() {
        CrmWhatsappAiJob job = job("Buenas noches de casualidad tendrá shorts?");
        job.setTriggerType("AUTOMATIC");
        job.getConversation().setStatus("ESPERA");
        job.getMessage().setCreatedAt(LocalDateTime.of(2026, 10, 5, 20, 28, 30));
        CrmWhatsappAiConfig config = config();
        config.setModo(CrmWhatsappAiMode.AUTOMATICA);
        CrmWhatsappMessage first = incomingMessage(job.getConversation(), 17L,
                "Buenas noches de casualidad tendrá shorts?",
                job.getMessage().getCreatedAt().minusMinutes(1));
        CrmWhatsappMessage automaticReply = incomingMessage(job.getConversation(), 18L,
                "No encontré ese producto.", job.getMessage().getCreatedAt().minusSeconds(50));
        automaticReply.setDirection("OUTGOING");
        automaticReply.setOrigin("AI_AUTOMATIC");
        CrmWhatsappMessage repeated = incomingMessage(job.getConversation(), 19L,
                "Buenas noches de casualidad tendrá shorts?",
                job.getMessage().getCreatedAt().minusSeconds(2));
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(config));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findActiveMessagesEndingAt(any(), any(), any()))
                .thenReturn(List.of(job.getMessage(), automaticReply, first));
        when(messages.findRecentActiveMessages(any(), any()))
                .thenReturn(List.of(job.getMessage(), automaticReply, first));

        var prepared = service.prepare(50L);

        assertNull(prepared.precomputedResult());
        assertTrue(prepared.latestMessage().contains("shorts"));
    }

    @Test
    void mensajeSalienteRompeLaAgrupacion() {
        CrmWhatsappAiJob job = job("talla M");
        LocalDateTime now = LocalDateTime.of(2026, 10, 5, 12, 0, 6);
        job.getMessage().setCreatedAt(now);
        CrmWhatsappMessage outgoing = incomingMessage(job.getConversation(), 19L, "¿Qué talla deseas?", now.minusSeconds(2));
        outgoing.setDirection("OUTGOING");
        CrmWhatsappMessage oldIncoming = incomingMessage(job.getConversation(), 18L, "Quiero Alice", now.minusSeconds(4));
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(config()));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findActiveMessagesEndingAt(any(), any(), any()))
                .thenReturn(List.of(job.getMessage(), outgoing, oldIncoming));

        var prepared = service.prepare(50L);

        assertEquals("talla M", prepared.latestMessage());
    }

    @Test
    void pausaMayorALaEsperaIniciaUnLoteNuevo() {
        CrmWhatsappAiJob job = job("talla M");
        LocalDateTime now = LocalDateTime.of(2026, 10, 5, 12, 0, 10);
        job.getMessage().setCreatedAt(now);
        CrmWhatsappMessage oldIncoming = incomingMessage(
                job.getConversation(), 19L, "Quiero Alice", now.minusSeconds(6));
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(config()));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findActiveMessagesEndingAt(any(), any(), any()))
                .thenReturn(List.of(job.getMessage(), oldIncoming));

        var prepared = service.prepare(50L);

        assertEquals("talla M", prepared.latestMessage());
    }

    @Test
    void imagenEnElLoteConTransferenciaDeshabilitadaGeneraUnSoloAviso() {
        CrmWhatsappAiJob job = job("¿Tienen este modelo?");
        LocalDateTime now = LocalDateTime.of(2026, 10, 5, 12, 0, 4);
        job.getMessage().setCreatedAt(now);
        CrmWhatsappMessage image = incomingMessage(job.getConversation(), 19L, "", now.minusSeconds(2));
        image.setMessageType("IMAGE");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(config()));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findActiveMessagesEndingAt(any(), any(), any()))
                .thenReturn(List.of(job.getMessage(), image));

        var result = service.execute(service.prepare(50L));

        assertEquals("ADJUNTO_NO_COMPATIBLE", result.intent());
        assertTrue(result.draft().contains("interpretar"));
        verifyNoInteractions(provider, tools);
    }

    @Test
    void ofreceAsesorParaCapturaSinTransferirNiConsumirGemini() {
        CrmWhatsappAiJob job = job("Te envio la captura de pago");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(config()));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);

        var result = service.execute(service.prepare(50L));

        assertTrue(!result.requiresHuman());
        assertEquals("CONSULTA_SENSIBLE", result.intent());
        assertTrue(result.draft().contains("¿Te parece si te comunico con una?"));
        verifyNoInteractions(provider);
    }

    @Test
    void rechazaConsultaFinancieraInternaYPermiteContinuar() {
        CrmWhatsappAiJob job = job("Cuanto se recaudo de dinero el dia de hoy en Kiments");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(config()));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);

        var result = service.execute(service.prepare(50L));

        assertTrue(!result.requiresHuman());
        assertEquals("INFORMACION_INTERNA", result.intent());
        assertTrue(result.draft().contains("¿Deseas realizar otra consulta?"));
        assertTrue(result.reason().contains("informacion financiera interna"));
        verifyNoInteractions(provider, tools);
    }

    @Test
    void pedirOtroProductoAbreLaConversacionSinForzarTodosLosDatos() {
        CrmWhatsappAiJob job = job("Quiero otro producto");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(config()));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findRecentActiveMessages(any(), any())).thenReturn(List.of(job.getMessage()));

        var result = service.execute(service.prepare(50L));

        assertEquals("PRODUCTOS", result.intent());
        assertEquals("Claro 💛 ¿Qué producto deseas consultar?", result.draft());
        verifyNoInteractions(provider, tools);
    }

    @Test
    void noPermiteSepararElConjuntoNiCombinarTallasDistintas() {
        CrmWhatsappAiJob job = job("Puede elegir chaleco M y pantalón L?");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(config()));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findRecentActiveMessages(any(), any())).thenReturn(List.of(job.getMessage()));

        var result = service.execute(service.prepare(50L));

        assertEquals("POLITICAS", result.intent());
        assertTrue(result.draft().contains("conjunto completo"));
        assertTrue(result.draft().contains("una sola talla"));
        assertTrue(result.draft().contains("chaleco M con pantalón L"));
        assertTrue(result.draft().contains("guía de medidas"));
        verifyNoInteractions(provider, tools);
    }

    @Test
    void noPermiteComprarSoloUnaPiezaDelConjunto() {
        CrmWhatsappAiJob job = job("Puedo comprar solo el chaleco por separado?");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(config()));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findRecentActiveMessages(any(), any())).thenReturn(List.of(job.getMessage()));

        var result = service.execute(service.prepare(50L));

        assertEquals("POLITICAS", result.intent());
        assertTrue(result.draft().contains("No vendemos el chaleco"));
        verifyNoInteractions(provider, tools);
    }

    @Test
    void unaConsultaNormalNoEsInterceptadaMientrasEsperaComprobante() {
        CrmWhatsappAiJob job = job("¿Qué colores tiene Alice Rayas?");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(config()));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findRecentActiveMessages(any(), any())).thenReturn(List.of(job.getMessage()));
        when(saleDrafts.pendingPaymentEvidenceReminder(10L)).thenReturn("Envía la captura del comprobante.");
        when(memory.resolvePendingReply(any(), any(), any())).thenReturn(
                new PendingReplyResolution("COLORES_TALLAS", "Respuesta sobre colores", false));

        var result = service.execute(service.prepare(50L));

        assertEquals("COLORES_TALLAS", result.intent());
        assertEquals("Respuesta sobre colores", result.draft());
        verifyNoInteractions(provider);
    }

    @Test
    void respondeAdjuntosNoCompatiblesSinConsumirGemini() {
        List<String[]> cases = List.of(
                new String[] { "IMAGE", "image/jpeg", "foto.jpg", "interpretar imágenes" },
                new String[] { "VIDEO", "video/mp4", "video.mp4", "revisar videos" },
                new String[] { "DOCUMENT", "application/msword", "archivo.doc", "revisar archivos" },
                new String[] { "STICKER", "image/webp", "sticker.webp", "consultas escritas" });

        for (String[] mediaCase : cases) {
            CrmWhatsappAiJob job = mediaJob(mediaCase[0], mediaCase[1], mediaCase[2], "");
            when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
            when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(config()));
            when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L))
                    .thenReturn(false);

            var result = service.execute(service.prepare(50L));

            assertEquals("ADJUNTO_NO_COMPATIBLE", result.intent());
            assertTrue(result.draft().contains(mediaCase[3]));
            assertTrue(!result.requiresHuman());
        }
        verifyNoInteractions(provider, tools);
    }

    @Test
    void transcribeAudioDeHastaSesentaSegundosYReutilizaElTextoComoMensaje() {
        CrmWhatsappAiJob job = mediaJob("AUDIO", "audio/ogg; codecs=opus", "audio.ogg", "");
        job.getMessage().setMediaDurationSeconds(60);
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(config()));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(storage.readBytes("crm/media/audio.ogg")).thenReturn(new byte[] { 1, 2, 3 });
        when(provider.transcribeAudio(any())).thenReturn(new AudioTranscriptionResult(
                "UNDERSTOOD", "Quiero una Belinda talla M", "es", 94, new Usage(20, 8, 28)));
        when(messages.findRecentActiveMessages(any(), any())).thenReturn(List.of(job.getMessage()));

        var prepared = service.prepare(50L);

        assertEquals("Quiero una Belinda talla M", prepared.latestMessage());
        assertTrue(prepared.conversationContext().contains("Cliente (audio transcrito): Quiero una Belinda talla M"));
        assertEquals(28, prepared.initialUsage().totalTokens());
        assertEquals("UNDERSTOOD", job.getMessage().getAudioTranscriptionStatus());
        assertEquals("Quiero una Belinda talla M", job.getMessage().getAudioTranscription());
        verify(messages).save(job.getMessage());
    }

    @Test
    void rechazaAudioMayorAUnMinutoSinConsumirGemini() {
        CrmWhatsappAiJob job = mediaJob("AUDIO", "audio/ogg", "audio.ogg", "");
        job.getMessage().setMediaDurationSeconds(61);
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(config()));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);

        var result = service.execute(service.prepare(50L));

        assertEquals("AUDIO_NO_PROCESABLE", result.intent());
        assertTrue(result.draft().contains("mas de 1 minuto"));
        verifyNoInteractions(provider, tools);
    }

    @Test
    void solicitaRepetirAudioCuandoGeminiNoLoEntiende() {
        CrmWhatsappAiJob job = mediaJob("PTT", "audio/ogg", "audio.ogg", "");
        job.getMessage().setMediaDurationSeconds(25);
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(config()));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(storage.readBytes("crm/media/audio.ogg")).thenReturn(new byte[] { 1, 2, 3 });
        when(provider.transcribeAudio(any())).thenReturn(new AudioTranscriptionResult(
                "UNCLEAR", "", "es", 35, new Usage(16, 4, 20)));

        var result = service.execute(service.prepare(50L));

        assertEquals("AUDIO_NO_ENTENDIDO", result.intent());
        assertTrue(result.draft().contains("No pude entender bien el audio"));
        assertEquals(20, result.usage().totalTokens());
        assertEquals("UNCLEAR", job.getMessage().getAudioTranscriptionStatus());
    }

    @Test
    void dejaLaCapturaCompatibleAlFlujoDePagosSinRespuestaDuplicada() {
        CrmWhatsappAiJob job = mediaJob("IMAGE", "image/jpeg", "pago.jpg", "");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(config()));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(saleDrafts.hasActivePaymentFlow(10L)).thenReturn(true);

        var result = service.execute(service.prepare(50L));

        assertEquals(CrmWhatsappAiRunOutcome.SKIPPED, result.outcome());
        assertTrue(result.reason().contains("flujo activo de pagos"));
        verifyNoInteractions(provider, tools);
    }

    @Test
    void noRespondeImagenComunCuandoLaCapturaYaFueRegistradaComoEvidencia() {
        CrmWhatsappAiJob job = mediaJob("IMAGE", "image/jpeg", "pago.jpg", "");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(config()));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(paymentEvidences.findByMessage_IdMessage(job.getMessage().getIdMessage()))
                .thenReturn(Optional.of(mock(CrmWhatsappPaymentEvidence.class)));

        var result = service.execute(service.prepare(50L));

        assertEquals(CrmWhatsappAiRunOutcome.SKIPPED, result.outcome());
        assertTrue(result.reason().contains("comprobante ya fue registrado"));
        verifyNoInteractions(provider, tools);
    }

    @Test
    void ignoraAdjuntoNoCompatibleDuranteElFlujoActivoDePagos() {
        CrmWhatsappAiJob job = mediaJob("IMAGE", "image/jpg", "pago.jpg", "");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(config()));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(saleDrafts.hasActivePaymentFlow(10L)).thenReturn(true);

        var result = service.execute(service.prepare(50L));

        assertEquals(CrmWhatsappAiRunOutcome.SKIPPED, result.outcome());
        assertTrue(result.reason().contains("flujo activo de pagos"));
        verifyNoInteractions(provider, tools);
    }

    @Test
    void ignoraImagenMientrasLaConversacionEsperaValidacionDePago() {
        CrmWhatsappAiJob job = mediaJob("IMAGE", "image/jpeg", "pago.jpg", "");
        job.getMessage().getConversation().setWaitingReason(CrmWhatsappWaitingReason.PAYMENT_VERIFICATION);
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(config()));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);

        var result = service.execute(service.prepare(50L));

        assertEquals(CrmWhatsappAiRunOutcome.SKIPPED, result.outcome());
        assertTrue(result.reason().contains("flujo activo de pagos"));
        verifyNoInteractions(provider, tools);
    }

    @Test
    void respondeImagenGenericaSinFlujoDePagoActivo() {
        CrmWhatsappAiJob job = mediaJob("IMAGE", "image/jpeg", "foto.jpg", "");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(config()));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);

        var result = service.execute(service.prepare(50L));

        assertEquals("ADJUNTO_NO_COMPATIBLE", result.intent());
        assertTrue(result.draft().contains("interpretar imágenes"));
        verifyNoInteractions(provider, tools);
    }

    @Test
    void procesaPedidoOficialDelEcommerceSinConsumirGemini() {
        CrmWhatsappAiJob job = job("""
                Hola KIMENTS, quiero comprar por WhatsApp:
                1. CIELO
                Color: TOPO | Talla: L
                Cantidad: 1 x S/ 70.00 = S/ 70.00
                Total: S/ 70.00
                """);
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(configWithIntent("INTENCION_COMPRA")));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(saleDrafts.applyEcommerceOrder(any(), any(), any()))
                .thenReturn(new CrmWhatsappAiSaleDraftService.ActionOutcome(
                        "✅ Preparé tu pedido: CIELO", false, null));

        var result = service.execute(service.prepare(50L));

        assertEquals("INTENCION_COMPRA", result.intent());
        assertTrue(result.draft().contains("CIELO"));
        verifyNoInteractions(provider, tools);
        verify(saleDrafts).applyEcommerceOrder(any(), any(), any());
    }

    @Test
    void procesaPedidoCompactoSinEnviarSaludoNiConsumirGemini() {
        CrmWhatsappAiJob job = job(
                "Hola, quiero comprar ALESSIA RAYAS Color: AZUL talla L Cantidad: 1");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(configWithIntent("INTENCION_COMPRA")));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(saleDrafts.applyEcommerceOrder(any(), any(), any()))
                .thenReturn(new CrmWhatsappAiSaleDraftService.ActionOutcome(
                        "✅ Preparé tu pedido: ALESSIA RAYAS", false, null));

        var result = service.execute(service.prepare(50L));

        assertEquals("INTENCION_COMPRA", result.intent());
        assertTrue(result.draft().contains("ALESSIA RAYAS"));
        verifyNoInteractions(provider, tools);
        verify(saleDrafts).applyEcommerceOrder(any(), any(), any());
    }

    @Test
    void pedidoConNombreParcialPreguntaElModeloAntesDeCrearElPedido() {
        CrmWhatsappAiJob job = job("Buenas tardes quiero hacer pedido de Alessia color morocho talla m");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L))
                .thenReturn(Optional.of(configWithIntent("INTENCION_COMPRA")));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findRecentActiveMessages(any(), any())).thenReturn(List.of(job.getMessage()));
        when(provider.classify(any())).thenReturn(new ClassificationResult(
                "INTENCION_COMPRA", 100, false, "solicitud de pedido", List.of(), new Usage(10, 5, 15)));
        when(tools.execute(any(), any())).thenReturn(new ExecutionResult(
                List.of(java.util.Map.of(
                        "tool", "buscar_productos",
                        "resolution", "AMBIGUOUS",
                        "candidates", List.of(
                                java.util.Map.of("productId", 30, "name", "ALESSIA ENTERO"),
                                java.util.Map.of("productId", 31, "name", "ALESSIA RAYAS")),
                        "products", List.of())),
                List.of(), List.of(), List.of()));

        var result = service.execute(service.prepare(50L));

        assertEquals("INTENCION_COMPRA", result.intent());
        assertTrue(result.draft().contains("ALESSIA ENTERO"));
        assertTrue(result.draft().contains("ALESSIA RAYAS"));
        assertTrue(result.draft().contains("Cuál de estos modelos"));
        verify(provider, org.mockito.Mockito.never()).interpretSaleAction(any());
        verify(saleDrafts, org.mockito.Mockito.never()).applyAiAction(any(), any());
    }

    @Test
    void conservaElColorEscritoPorLaClientaSinInventarUnAlias() {
        CrmWhatsappAiJob job = job("Quiero Alessia entero color morocho talla m");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L))
                .thenReturn(Optional.of(configWithIntent("INTENCION_COMPRA")));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findRecentActiveMessages(any(), any())).thenReturn(List.of(job.getMessage()));
        when(provider.classify(any())).thenReturn(new ClassificationResult(
                "INTENCION_COMPRA", 100, false, "solicitud de pedido", List.of(), new Usage(10, 5, 15)));
        when(tools.execute(any(), any())).thenReturn(new ExecutionResult(
                List.of(java.util.Map.of("tool", "buscar_productos", "resolution", "EXACT",
                        "products", List.of(java.util.Map.of(
                                "name", "ALESSIA ENTERO",
                                "availableColors", List.of("MARRON", "NEGRO"),
                                "availableSizes", List.of("M"))))),
                List.of(), List.of(), List.of()));
        when(provider.interpretSaleAction(any())).thenReturn(new SaleActionResult(
                "ADD", "ALESSIA ENTERO", null, "MARRON", "M", 1,
                "", 100, "pedido", Usage.empty()));
        when(saleDrafts.applyAiAction(any(), argThat(action -> "morocho".equals(action.color()))))
                .thenReturn(new CrmWhatsappAiSaleDraftService.ActionOutcome(
                        "El color morocho no está disponible. Colores disponibles: MARRON, NEGRO.", false, null));

        var result = service.execute(service.prepare(50L));

        assertTrue(result.draft().contains("morocho no está disponible"));
        verify(saleDrafts).applyAiAction(any(), argThat(action -> "morocho".equals(action.color())));
    }

    @Test
    void agregaDosTallasDelMismoProductoEnUnaSolaOperacion() {
        CrmWhatsappAiJob job = job("Agrega EMMA chocolate talla S y talla XS");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L))
                .thenReturn(Optional.of(configWithIntent("INTENCION_COMPRA")));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(saleDrafts.applyEcommerceOrder(any(), any(), argThat(order ->
                order.items().size() == 2
                        && "S".equalsIgnoreCase(order.items().getFirst().size())
                        && "XS".equalsIgnoreCase(order.items().get(1).size()))))
                .thenReturn(new CrmWhatsappAiSaleDraftService.ActionOutcome(
                        "Pedido con EMMA CHOCOLATE talla S y XS", false, null));

        var result = service.execute(service.prepare(50L));

        assertTrue(result.draft().contains("talla S y XS"));
        verifyNoInteractions(provider, tools);
    }

    @Test
    void losDosReutilizaLasTallasOfrecidasSinConvertirlasEnCantidadDos() {
        CrmWhatsappAiJob job = job("Los dos");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L))
                .thenReturn(Optional.of(configWithIntent("INTENCION_COMPRA")));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(memory.pendingQuestionFor(10L)).thenReturn(CrmWhatsappAiPendingQuestion.SIZE);
        when(memory.selectionFor(10L)).thenReturn(new MemorySelection(11, "EMMA", "CHOCOLATE", "", null));
        CrmWhatsappMessage previousReply = incomingMessage(job.getConversation(), 19L,
                "¿Deseas llevarlo en talla S o XS?", LocalDateTime.of(2026, 10, 5, 22, 1));
        previousReply.setDirection("OUTGOING");
        previousReply.setOrigin("AI_AUTOMATIC");
        when(messages.findRecentActiveMessages(any(), any()))
                .thenReturn(List.of(job.getMessage(), previousReply));
        when(saleDrafts.applyEcommerceOrder(any(), any(), argThat(order ->
                order.items().size() == 2
                        && Integer.valueOf(1).equals(order.items().getFirst().quantity())
                        && Integer.valueOf(1).equals(order.items().get(1).quantity()))))
                .thenReturn(new CrmWhatsappAiSaleDraftService.ActionOutcome(
                        "Pedido con una EMMA talla S y una EMMA talla XS", false, null));

        var result = service.execute(service.prepare(50L));

        assertTrue(result.draft().contains("una EMMA talla S"));
        verifyNoInteractions(provider, tools);
    }

    @Test
    void transfiereCuandoElClienteSolicitaExpresamenteUnAsesor() {
        CrmWhatsappAiJob job = job("Deseo hablar con una asesora");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(config()));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);

        var result = service.execute(service.prepare(50L));

        assertTrue(result.requiresHuman());
        assertEquals("ASESOR_SOLICITADO", result.intent());
        verifyNoInteractions(provider, tools);
    }

    @Test
    void confirmaAsesorSoloDespuesDeUnaOfertaPrevia() {
        CrmWhatsappAiJob job = job("Si");
        CrmWhatsappMessage offer = new CrmWhatsappMessage();
        offer.setIdMessage(19L);
        offer.setDirection("OUTGOING");
        offer.setBody("Para ayudarte con esta consulta necesito el apoyo de un asesor.\n\n"
                + "¿Te parece si te comunico con uno?");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(config()));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findRecentActiveMessages(any(), any())).thenReturn(List.of(job.getMessage(), offer));

        var result = service.execute(service.prepare(50L));

        assertTrue(result.requiresHuman());
        assertEquals("ASESOR_SOLICITADO", result.intent());
        verifyNoInteractions(provider, tools);
    }

    @Test
    void transferenciaAutomaticaPasaElChatAEsperaYDesactivaLaIa() {
        CrmWhatsappAiJob job = job("Deseo hablar con una asesora");
        job.setTriggerType("AUTOMATIC");
        job.getConversation().setStatus("ESPERA");
        job.getConversation().setAiAttentionMode(CrmWhatsappAiAttentionMode.AUTOMATICA);
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        CrmWhatsappAiConfig config = config();
        config.setModo(CrmWhatsappAiMode.AUTOMATICA);
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(config));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(runs.save(any())).thenAnswer(invocation -> {
            CrmWhatsappAiRun run = invocation.getArgument(0);
            run.setIdAiRun(62L);
            return run;
        });

        var result = service.execute(service.prepare(50L));
        assertTrue(result.requiresHuman(), result.intent() + ": " + result.reason());
        assertEquals("ASESOR_SOLICITADO", result.intent());
        service.complete(50L, result);

        verify(handoff).requireAdvisor(10L, CrmWhatsappWaitingReason.ADVISOR_REQUIRED,
                result.reason(), 62L);
        verify(delivery).enqueueHandoff(any());
    }

    @Test
    void saludoActualNoHeredaLaIntencionFinancieraAnterior() {
        CrmWhatsappAiJob job = job("Hola");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(config()));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findRecentActiveMessages(any(), any())).thenReturn(List.of(job.getMessage()));
        when(provider.classify(any())).thenReturn(new ClassificationResult(
                "INSTITUCIONAL", 100, false, "contexto anterior", List.of(), new Usage(10, 5, 15)));
        when(tools.execute(any(), any())).thenReturn(new ExecutionResult(
                List.of(), List.of(), List.of(), List.of()));

        var result = service.execute(service.prepare(50L));

        assertEquals("SALUDO", result.intent());
        assertEquals("👋 ¡Hola, bella! 😊\n\n"
                + "Qué gusto tenerte por aquí. Cuéntame, ¿en qué puedo ayudarte?", result.draft());
        verify(tools).execute(any(), any());
    }

    @Test
    void queTalNoSeConfundeConEnviosAunqueElAvisoInicialMencioneShalom() {
        CrmWhatsappAiJob job = job("Que tal");
        CrmWhatsappMessage welcome = new CrmWhatsappMessage();
        welcome.setIdMessage(19L);
        welcome.setDirection("OUTGOING");
        welcome.setBody("Realizamos envios a provincia mediante la agencia Shalom.");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(config()));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findRecentActiveMessages(any(), any())).thenReturn(List.of(welcome, job.getMessage()));
        when(provider.classify(any())).thenReturn(new ClassificationResult(
                "SALUDO", 100, false, "saludo social", List.of(), new Usage(10, 5, 15)));
        when(tools.execute(any(), any())).thenReturn(new ExecutionResult(
                List.of(), List.of(), List.of(), List.of()));

        var result = service.execute(service.prepare(50L));

        assertEquals("SALUDO", result.intent());
        assertFalse(result.draft().contains("envio"));
        assertFalse(result.draft().contains("Shalom"));
    }

    @Test
    void descartaResultadoSiElTrabajoFueEliminadoDuranteElProcesamiento() {
        when(jobs.findDetailedById(999L)).thenReturn(Optional.empty());

        service.complete(999L, CrmWhatsappAiEngineService.ProcessingResult.skip("Trabajo obsoleto", false));
        service.fail(999L, new IllegalStateException("Fallo tardio"), false);

        verifyNoInteractions(runs, delivery);
    }

    @Test
    void falloDefinitivoDelProveedorPasaLaConversacionAUnaAsesora() {
        CrmWhatsappAiJob job = job("Hola");
        job.setTriggerType("AUTOMATIC");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(runs.save(any())).thenAnswer(invocation -> {
            CrmWhatsappAiRun run = invocation.getArgument(0);
            run.setIdAiRun(81L);
            return run;
        });

        service.fail(50L, new AiProviderException("Gemini no disponible", false), false);

        verify(handoff).requireAdvisorForFailure(eq(10L), any(), eq(81L));
    }

    @Test
    void generaBorradorSinEnviarMensajes() {
        CrmWhatsappAiJob job = job("Hola");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(config()));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findRecentActiveMessages(any(), any())).thenReturn(List.of(job.getMessage()));
        when(provider.classify(any())).thenReturn(new ClassificationResult(
                "SALUDO", 96, false, "saludo", List.of(), new Usage(10, 5, 15)));
        when(tools.execute(any(), any())).thenReturn(new ExecutionResult(
                List.of(), List.of(), List.of(), List.of()));
        when(provider.generateDraft(any())).thenReturn(new DraftResult(
                "Hola, ¿en que prenda te ayudo?", false, "", List.of(), new Usage(12, 8, 20)));

        var result = service.execute(service.prepare(50L));

        assertEquals("👋 ¡Hola, bella! 😊\n\n"
                + "Qué gusto tenerte por aquí. Cuéntame, ¿en qué puedo ayudarte?", result.draft());
        assertEquals(15, result.usage().totalTokens());
        verifyNoInteractions(events);
    }

    @Test
    void seleccionaMetodoDelPedidoConfirmadoSinConsultarGemini() {
        CrmWhatsappAiJob job = job("Yape por favor");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(config()));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findRecentActiveMessages(any(), any())).thenReturn(List.of(job.getMessage()));
        when(saleDrafts.selectConfirmedPaymentMethod(job.getConversation(), "Yape por favor"))
                .thenReturn(new CrmWhatsappAiSaleDraftService.ActionOutcome(
                        "Envia la captura del comprobante dentro de los proximos 10 minutos.", false, null));

        var result = service.execute(service.prepare(50L));

        assertEquals("METODOS_PAGO", result.intent());
        assertTrue(result.draft().contains("Envia la captura"));
        verifyNoInteractions(provider, tools);
    }

    @Test
    void respuestaYapeAPreguntaPendienteSeleccionaPagoSinListarMetodos() {
        CrmWhatsappAiJob job = job("Yape");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(config()));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findRecentActiveMessages(any(), any())).thenReturn(List.of(job.getMessage()));
        when(memory.pendingQuestionFor(10L)).thenReturn(CrmWhatsappAiPendingQuestion.PAYMENT_METHOD);
        when(saleDrafts.selectConfirmedPaymentMethod(job.getConversation(), "Yape", true))
                .thenReturn(new CrmWhatsappAiSaleDraftService.ActionOutcome(
                        "Paga por YAPE a la cuenta configurada y envía la captura del comprobante.",
                        false, null));

        var result = service.execute(service.prepare(50L));

        assertEquals("METODOS_PAGO", result.intent());
        assertTrue(result.draft().contains("Paga por YAPE"));
        assertTrue(!result.draft().contains("PLIN"));
        assertTrue(!result.draft().contains("TRANSFERENCIA"));
        verifyNoInteractions(provider, tools);
    }

    @Test
    void mientrasEsperaComprobanteSolicitaOtraCapturaSinConsumirGemini() {
        CrmWhatsappAiJob job = job("Ya pague, que hago ahora?");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(config()));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(saleDrafts.pendingPaymentEvidenceReminder(10L)).thenReturn(
                "No se puede procesar tu pago con ese mensaje. Intenta otra vez enviando una captura clara "
                        + "con el monto exacto de S/75.00.");

        var result = service.execute(service.prepare(50L));

        assertEquals("PAGO_PENDIENTE", result.intent());
        assertTrue(result.draft().contains("S/75.00"));
        verifyNoInteractions(provider, tools);
    }

    @Test
    void conservaSoloImagenesDevueltasPorLasHerramientas() {
        CrmWhatsappAiJob job = job("Muéstrame el polo azul");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(config()));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findRecentActiveMessages(any(), any())).thenReturn(List.of(job.getMessage()));
        when(provider.classify(any())).thenReturn(new ClassificationResult(
                "PRODUCTOS", 96, false, "producto", List.of(), new Usage(10, 5, 15)));
        MediaReference valid = new MediaReference(
                "PRODUCT_IMAGE", 10, 20, "Polo", "AZUL", "https://cdn.test/polo.jpg", "https://cdn.test/polo-thumb.jpg");
        when(tools.execute(any(), any())).thenReturn(new ExecutionResult(
                List.of(java.util.Map.of("tool", "buscar_productos")),
                List.of(java.util.Map.of("tool", "buscar_productos", "resultCount", 1)),
                List.of(java.util.Map.of("tool", "buscar_productos")),
                List.of(valid)));
        when(provider.generateDraft(any())).thenReturn(new DraftResult(
                "Tenemos el polo azul disponible.", false, "",
                List.of(
                        new MediaSuggestion(10, 20, "AZUL", "https://inventada.test/falsa.jpg", ""),
                        new MediaSuggestion(10, 20, "AZUL", "https://cdn.test/polo.jpg", "")),
                new Usage(12, 8, 20)));

        var result = service.execute(service.prepare(50L));

        assertEquals(1, result.suggestedMedia().size());
        assertEquals("https://cdn.test/polo.jpg", result.suggestedMedia().getFirst().url());
    }

    @Test
    void preparaGuiaDeTallasValidadaSinPermitirUnaUrlInventada() {
        CrmWhatsappAiJob job = job("Mandame la guia de tallas de Julieta");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(configWithIntent("GUIA_TALLAS")));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findRecentActiveMessages(any(), any())).thenReturn(List.of(job.getMessage()));
        when(provider.classify(any())).thenReturn(new ClassificationResult(
                "COLORES_TALLAS", 98, false, "guia", List.of(), new Usage(10, 5, 15)));
        MediaReference guide = new MediaReference("SIZE_GUIDE", 10, null, "JULIETA", "",
                "/storage/productos/10/guia-tallas.webp", "/storage/productos/10/guia-tallas-thumb.webp");
        when(tools.execute(any(), any())).thenReturn(new ExecutionResult(
                List.of(java.util.Map.of("tool", "buscar_productos", "products", List.of(java.util.Map.of(
                        "productId", 10, "name", "JULIETA", "sizeGuideUrl", guide.url())))),
                List.of(java.util.Map.of("tool", "buscar_productos", "resultCount", 1)),
                List.of(java.util.Map.of("tool", "buscar_productos")), List.of(guide)));

        var result = service.execute(service.prepare(50L));

        assertEquals("GUIA_TALLAS", result.intent());
        assertEquals("Guía de tallas de JULIETA", result.draft());
        assertEquals(List.of(guide), result.suggestedMedia());
    }

    @Test
    void pidePrecisarModeloCompuestoAntesDeEnviarUnaGuia() {
        CrmWhatsappAiJob job = job("Las tablas de medidas de Alessia");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(configWithIntent("GUIA_TALLAS")));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findRecentActiveMessages(any(), any())).thenReturn(List.of(job.getMessage()));
        when(provider.classify(any())).thenReturn(new ClassificationResult(
                "PRODUCTOS", 98, false, "guia", List.of(), new Usage(10, 5, 15)));
        when(tools.execute(any(), any())).thenReturn(new ExecutionResult(
                List.of(java.util.Map.of(
                        "tool", "buscar_productos",
                        "resolution", "AMBIGUOUS",
                        "candidates", List.of(
                                java.util.Map.of("productId", 30, "name", "ALESSIA ENTERO"),
                                java.util.Map.of("productId", 31, "name", "ALESSIA RAYAS")),
                        "products", List.of())),
                List.of(java.util.Map.of("tool", "buscar_productos", "resultCount", 0)),
                List.of(java.util.Map.of("tool", "buscar_productos")), List.of()));

        var result = service.execute(service.prepare(50L));

        assertEquals("GUIA_TALLAS", result.intent());
        assertTrue(result.draft().contains("ALESSIA ENTERO"));
        assertTrue(result.draft().contains("ALESSIA RAYAS"));
        assertEquals(List.of(), result.suggestedMedia());
    }

    @Test
    void limitaConsultaEspecificaAlProductoMencionadoAunqueGeminiPidaCatalogoGlobal() {
        CrmWhatsappAiJob job = job("Julieta dame colores");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(config()));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findRecentActiveMessages(any(), any())).thenReturn(List.of(job.getMessage()));
        when(provider.classify(any())).thenReturn(new ClassificationResult(
                "PRODUCTOS", 98, false, "consulta especifica",
                List.of(new ToolCall("buscar_productos", java.util.Map.of("q", ""))),
                new Usage(10, 5, 15)));
        when(tools.execute(any(), any())).thenAnswer(invocation -> {
            List<ToolCall> calls = invocation.getArgument(1);
            assertEquals(1, calls.size());
            assertEquals("Julieta dame colores", calls.getFirst().arguments().get("q"));
            return new ExecutionResult(List.of(java.util.Map.of(
                    "tool", "buscar_productos",
                    "productName", "JULIETA")),
                    List.of(), List.of(), List.of());
        });
        when(provider.generateDraft(any())).thenReturn(new DraftResult(
                "JULIETA esta disponible.", false, "", List.of(), new Usage(12, 8, 20)));

        var result = service.execute(service.prepare(50L));

        assertEquals("JULIETA esta disponible.", result.draft());
    }

    @Test
    void reconocePreguntaGeneralPorPrendasComoSolicitudDeCatalogo() {
        CrmWhatsappAiJob job = job("Que prenda tienes ?");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(config()));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findRecentActiveMessages(any(), any())).thenReturn(List.of(job.getMessage()));
        when(tools.execute(any(), any())).thenAnswer(invocation -> {
            List<ToolCall> calls = invocation.getArgument(1);
            assertEquals("", calls.getFirst().arguments().get("q"));
            assertEquals(0, calls.getFirst().arguments().get("page"));
            return new ExecutionResult(
                    List.of(java.util.Map.of("tool", "buscar_productos", "query", "", "products",
                            List.of(java.util.Map.of("name", "BELEN")))),
                    List.of(), List.of(), List.of());
        });

        var result = service.execute(service.prepare(50L));

        assertTrue(result.draft().contains("BELEN"));
        assertTrue(result.draft().contains("sus fotos, precio, colores, tallas disponibles y su guía de medidas"));
        verifyNoInteractions(provider);
    }

    @Test
    void quieroComprarProductosMuestraCatalogoEnLugarDeBuscarLaFraseComoModelo() {
        CrmWhatsappAiJob job = job("Quiero comprar productos");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(config()));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findRecentActiveMessages(any(), any())).thenReturn(List.of(job.getMessage()));
        when(tools.execute(any(), any())).thenAnswer(invocation -> {
            List<ToolCall> calls = invocation.getArgument(1);
            assertEquals("", calls.getFirst().arguments().get("q"));
            return new ExecutionResult(List.of(java.util.Map.of(
                    "tool", "buscar_productos", "products", List.of(
                            java.util.Map.of("name", "ALESSIA ENTERO"),
                            java.util.Map.of("name", "EMMA")))),
                    List.of(), List.of(), List.of());
        });

        var result = service.execute(service.prepare(50L));

        assertTrue(result.draft().contains("ALESSIA ENTERO"));
        assertTrue(result.draft().contains("EMMA"));
        verifyNoInteractions(provider);
    }

    @Test
    void siPorfaDespuesDeOfrecerCatalogoMuestraProductosSinConsultarRag() {
        CrmWhatsappAiJob job = job("Si porfa");
        CrmWhatsappAiConfig config = config();
        var prepared = new CrmWhatsappAiEngineService.PreparedJob(
                job, config,
                "Asesor: Si deseas, puedo mostrarte los productos disponibles.\nCliente: Si porfa\n",
                "Si porfa", List.of("PRODUCTOS"), "Instruccion segura", Usage.empty(), null);
        when(tools.execute(any(), any())).thenReturn(new ExecutionResult(
                List.of(java.util.Map.of("tool", "buscar_productos", "products",
                        List.of(java.util.Map.of("name", "BELEN")))),
                List.of(), List.of(), List.of()));

        var result = service.execute(prepared);

        assertTrue(result.draft().contains("BELEN"));
        verifyNoInteractions(provider);
    }

    @Test
    void reconocePreguntaGeneralPorConjuntosSinBuscarlaComoNombreDeProducto() {
        CrmWhatsappAiJob job = job("Que conjuntos tienes ?");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(config()));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findRecentActiveMessages(any(), any())).thenReturn(List.of(job.getMessage()));
        when(provider.classify(any())).thenReturn(new ClassificationResult(
                "PRODUCTOS", 100, false, "catalogo general",
                List.of(new ToolCall("buscar_productos", java.util.Map.of("q", "Que conjuntos tienes ?"))),
                new Usage(10, 5, 15)));
        when(tools.execute(any(), any())).thenAnswer(invocation -> {
            List<ToolCall> calls = invocation.getArgument(1);
            assertEquals("", calls.getFirst().arguments().get("q"));
            assertEquals(0, calls.getFirst().arguments().get("page"));
            return new ExecutionResult(
                    List.of(java.util.Map.of("tool", "buscar_productos", "query", "", "products",
                            List.of(java.util.Map.of("name", "MODELO DISPONIBLE")))),
                    List.of(), List.of(), List.of());
        });

        var result = service.execute(service.prepare(50L));

        assertTrue(!result.requiresHuman());
        assertTrue(result.draft().contains("MODELO DISPONIBLE"));
    }

    @Test
    void confirmacionParaMostrarCatalogoConsultaProductosSinVolverAClasificar() {
        CrmWhatsappAiJob job = job("Si por favor");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(config()));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findRecentActiveMessages(any(), any())).thenReturn(List.of(job.getMessage()));
        when(memory.resolvePendingReply(any(), any(), any()))
                .thenReturn(new PendingReplyResolution("PRODUCTOS", "", true));
        when(tools.execute(any(), any())).thenAnswer(invocation -> {
            List<ToolCall> calls = invocation.getArgument(1);
            assertEquals(1, calls.size());
            assertEquals("", calls.getFirst().arguments().get("q"));
            assertEquals(0, calls.getFirst().arguments().get("page"));
            return new ExecutionResult(
                    List.of(java.util.Map.of("tool", "buscar_productos", "query", "", "products",
                            List.of(java.util.Map.of("name", "BELINDA"), java.util.Map.of("name", "JULIETA")))),
                    List.of(), List.of(), List.of());
        });

        var result = service.execute(service.prepare(50L));

        assertEquals("PRODUCTOS", result.intent());
        assertTrue(result.draft().contains("BELINDA"));
        assertTrue(result.draft().contains("JULIETA"));
        verifyNoInteractions(provider);
    }

    @Test
    void modeloElegidoRespondePrecioColoresYTallasSinPreguntaIntermedia() {
        CrmWhatsappAiJob job = job("Julieta por favor");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(config()));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findRecentActiveMessages(any(), any())).thenReturn(List.of(job.getMessage()));
        when(memory.resolvePendingReply(any(), any(), any()))
                .thenReturn(new PendingReplyResolution("PRODUCTOS", "", false, "Julieta por favor"));
        when(tools.execute(any(), any())).thenAnswer(invocation -> {
            List<ToolCall> calls = invocation.getArgument(1);
            assertEquals("Julieta por favor", calls.getFirst().arguments().get("q"));
            return new ExecutionResult(
                    List.of(java.util.Map.of("tool", "buscar_productos", "products", List.of(java.util.Map.of(
                            "name", "JULIETA",
                            "availableColors", List.of("Negro", "Beige", "LACRE"),
                            "availableSizes", List.of("XS", "S", "M", "L"),
                            "variants", List.of(java.util.Map.of(
                                    "currentPrice", "75.00", "offerPrice", "", "stock", 4)))))),
                    List.of(), List.of(), List.of());
        });

        var result = service.execute(service.prepare(50L));

        assertEquals("PRODUCTOS", result.intent());
        assertTrue(result.draft().contains("JULIETA"));
        assertTrue(result.draft().contains("S/75.00"));
        assertTrue(result.draft().contains("Negro, Beige, LACRE"));
        assertTrue(result.draft().contains("XS, S, M, L"));
        assertTrue(!result.draft().contains("Deseas conocer"));
        verifyNoInteractions(provider);
    }

    @Test
    void quieroModeloMuestraFichaCompletaYAdjuntaGuiaAntesDeImagenGlobal() {
        CrmWhatsappAiJob job = job("Quiero Alessia entero");
        CrmWhatsappAiConfig config = config();
        config.setNaturalResponseEnabled(true);
        config.setNaturalResponseRolloutPercent(100);
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(config));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findRecentActiveMessages(any(), any())).thenReturn(List.of(job.getMessage()));
        MediaReference guide = new MediaReference("SIZE_GUIDE", 30, null,
                "ALESSIA ENTERO", "", "/storage/productos/alessia-guia.webp", "");
        MediaReference global = new MediaReference("PRODUCT_GLOBAL_IMAGE", 30, null,
                "ALESSIA ENTERO", "", "/storage/productos/alessia.webp", "");
        java.util.Map<String, Object> alessia = java.util.Map.of(
                "productId", 30,
                "name", "ALESSIA ENTERO",
                "description", "Tela Aruba con diseño de pierna amplia",
                "availableColors", List.of("Negro", "MARRON", "GRIS OSCURO"),
                "availableSizes", List.of("XS", "S", "M", "L"),
                "variants", List.of(java.util.Map.of(
                        "currentPrice", "89.00", "offerPrice", "", "stock", 4)));
        when(tools.execute(any(), any())).thenAnswer(invocation -> {
            List<ToolCall> calls = invocation.getArgument(1);
            assertEquals("Quiero Alessia entero", calls.getFirst().arguments().get("q"));
            return new ExecutionResult(
                    List.of(java.util.Map.of("tool", "buscar_productos", "products", List.of(alessia))),
                    List.of(), List.of(), List.of(guide, global));
        });

        var result = service.execute(service.prepare(50L));

        assertEquals("PRODUCTOS", result.intent());
        assertTrue(result.draft().contains("ALESSIA ENTERO"));
        assertTrue(result.draft().contains("📝 *Descripción:* Tela Aruba con diseño de pierna amplia"));
        assertTrue(result.draft().contains("S/89.00"));
        assertTrue(result.draft().contains("Negro, MARRON, GRIS OSCURO"));
        assertTrue(result.draft().contains("XS, S, M, L"));
        assertTrue(result.draft().contains("talla y color deseas"));
        assertEquals(List.of(guide, global), result.suggestedMedia());
        verifyNoInteractions(provider);
    }

    @Test
    void consultaDeMaterialSoloRespondeDescripcionSinPreventaNiAdjuntos() {
        CrmWhatsappAiJob job = job("Sabes de que tela es Alessia entero");
        CrmWhatsappAiConfig config = config();
        config.setNaturalResponseEnabled(true);
        config.setNaturalResponseRolloutPercent(100);
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(config));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findRecentActiveMessages(any(), any())).thenReturn(List.of(job.getMessage()));
        MediaReference guide = new MediaReference("SIZE_GUIDE", 30, null,
                "ALESSIA ENTERO", "", "/storage/productos/alessia-guia.webp", "");
        MediaReference global = new MediaReference("PRODUCT_GLOBAL_IMAGE", 30, null,
                "ALESSIA ENTERO", "", "/storage/productos/alessia.webp", "");
        java.util.Map<String, Object> alessia = java.util.Map.of(
                "productId", 30,
                "name", "ALESSIA ENTERO",
                "description", "ARUBA",
                "preventa", true,
                "fechaEnvioPreventa", "2026-10-14",
                "availableColors", List.of("Negro", "MARRON"),
                "availableSizes", List.of("S", "M", "L"));
        when(tools.execute(any(), any())).thenReturn(new ExecutionResult(
                List.of(java.util.Map.of("tool", "buscar_productos", "products", List.of(alessia))),
                List.of(), List.of(), List.of(guide, global)));

        var result = service.execute(service.prepare(50L));

        assertEquals("MATERIAL_PRODUCTO", result.intent());
        assertTrue(result.draft().contains("ALESSIA ENTERO"));
        assertTrue(result.draft().contains("ARUBA"));
        assertFalse(result.draft().contains("Preventa"), result.draft());
        assertFalse(result.draft().contains("14 de octubre"), result.draft());
        assertFalse(result.draft().contains("Colores"), result.draft());
        assertFalse(result.draft().contains("Tallas"), result.draft());
        assertFalse(result.draft().contains("¿"), result.draft());
        assertTrue(result.suggestedMedia().isEmpty());
        verifyNoInteractions(provider);
    }

    @Test
    void fichaDeProductoOmiteDescripcionCuandoEstaVacia() {
        CrmWhatsappAiJob job = job("Quiero Julieta");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(config()));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findRecentActiveMessages(any(), any())).thenReturn(List.of(job.getMessage()));
        java.util.Map<String, Object> julieta = java.util.Map.of(
                "name", "JULIETA", "description", "",
                "availableColors", List.of("Negro"), "availableSizes", List.of("M"),
                "variants", List.of(java.util.Map.of("currentPrice", "75.00", "stock", 2)));
        when(tools.execute(any(), any())).thenReturn(new ExecutionResult(
                List.of(java.util.Map.of("tool", "buscar_productos", "products", List.of(julieta))),
                List.of(), List.of(), List.of()));

        var result = service.execute(service.prepare(50L));

        assertFalse(result.draft().contains("Descripción:"), result.draft());
    }

    @Test
    void conjuntoConNombreMuestraFichaCompletaAntesDeInterpretarloComoEnvio() {
        CrmWhatsappAiJob job = job("Conjunto aria tienes disponible?");
        job.getMessage().setCreatedAt(LocalDateTime.of(2026, 10, 6, 16, 9));
        CrmWhatsappMessage previous = incomingMessage(job.getConversation(), 19L,
                "Realizamos envios a nivel nacional mediante la agencia Shalom.",
                job.getMessage().getCreatedAt().minusMinutes(1));
        previous.setDirection("OUTGOING");
        previous.setOrigin("AI_AUTOMATIC");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(config()));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findRecentActiveMessages(any(), any())).thenReturn(List.of(job.getMessage(), previous));
        MediaReference guide = new MediaReference("SIZE_GUIDE", 90, null,
                "ARIA", "", "/storage/productos/aria-guia.webp", "");
        MediaReference global = new MediaReference("PRODUCT_GLOBAL_IMAGE", 90, null,
                "ARIA", "", "/storage/productos/aria.webp", "");
        java.util.Map<String, Object> aria = java.util.Map.of(
                "productId", 90,
                "name", "ARIA",
                "description", "Tela Catania",
                "preventa", true,
                "fechaEnvioPreventa", "2026-10-16",
                "availableColors", List.of("NEGRO", "BEIGE"),
                "availableSizes", List.of("S", "M", "L"),
                "variants", List.of(java.util.Map.of("currentPrice", "70.00", "stock", 2)));
        when(tools.execute(any(), any())).thenReturn(new ExecutionResult(
                List.of(java.util.Map.of("tool", "buscar_productos", "products", List.of(aria))),
                List.of(), List.of(), List.of(guide, global)));

        var result = service.execute(service.prepare(50L));

        assertEquals("PRODUCTOS", result.intent());
        assertTrue(result.draft().contains("ARIA"), result.draft());
        assertTrue(result.draft().contains("Tela Catania"), result.draft());
        assertTrue(result.draft().contains("S/70.00"), result.draft());
        assertTrue(result.draft().contains("NEGRO, BEIGE"), result.draft());
        assertTrue(result.draft().contains("S, M, L"), result.draft());
        assertTrue(result.draft().contains("Preventa"), result.draft());
        assertTrue(result.draft().contains("16 de octubre de 2026"), result.draft());
        assertEquals(List.of(guide, global), result.suggestedMedia());
        verifyNoInteractions(provider);
    }

    @Test
    void modeloPendienteSeResuelveAntesDeUnSeguimientoDeEnvio() {
        CrmWhatsappAiJob job = job("Nhara liso");
        job.getMessage().setCreatedAt(LocalDateTime.of(2026, 10, 6, 16, 26));
        CrmWhatsappMessage previous = incomingMessage(job.getConversation(), 19L,
                "Realizamos envios a provincia mediante Shalom.",
                job.getMessage().getCreatedAt().minusMinutes(1));
        previous.setDirection("OUTGOING");
        previous.setOrigin("AI_AUTOMATIC");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(config()));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findRecentActiveMessages(any(), any())).thenReturn(List.of(job.getMessage(), previous));
        when(memory.pendingQuestionFor(10L)).thenReturn(CrmWhatsappAiPendingQuestion.CATALOG_PRODUCT);
        when(memory.resolvePendingReply(any(), any(), any())).thenReturn(
                new PendingReplyResolution("PRODUCTOS", "", false, "Nhara liso"));
        java.util.Map<String, Object> nhara = java.util.Map.of(
                "productId", 91,
                "name", "Conjunto NHARA _ LISO",
                "availableColors", List.of("NEGRO", "BEIGE"),
                "availableSizes", List.of("S", "M", "L"),
                "variants", List.of(java.util.Map.of("currentPrice", "70.00", "stock", 3)));
        when(tools.execute(any(), any())).thenReturn(new ExecutionResult(
                List.of(java.util.Map.of("tool", "buscar_productos", "resolution", "EXACT",
                        "products", List.of(nhara))),
                List.of(), List.of(), List.of()));

        var result = service.execute(service.prepare(50L));

        assertEquals("PRODUCTOS", result.intent());
        assertTrue(result.draft().contains("NHARA _ LISO"), result.draft());
        assertTrue(result.draft().contains("NEGRO, BEIGE"), result.draft());
        assertFalse(result.draft().contains("costo exacto del envío"), result.draft());
        verifyNoInteractions(provider);
    }

    @Test
    void productoInexistenteSinParecidosListaModelosDisponibles() {
        CrmWhatsappAiJob job = job("Conjunto inexistente tienes disponible?");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(config()));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findRecentActiveMessages(any(), any())).thenReturn(List.of(job.getMessage()));
        when(tools.execute(any(), any())).thenAnswer(invocation -> {
            List<ToolCall> calls = invocation.getArgument(1);
            String query = String.valueOf(calls.getFirst().arguments().get("q"));
            if (!query.isBlank()) {
                return new ExecutionResult(
                        List.of(java.util.Map.of("tool", "buscar_productos", "query", query,
                                "resolution", "NONE", "products", List.of(), "candidates", List.of())),
                        List.of(), List.of(), List.of());
            }
            return new ExecutionResult(
                    List.of(java.util.Map.of("tool", "buscar_productos", "query", "",
                            "products", List.of(
                                    java.util.Map.of("name", "ALESSIA ENTERO"),
                                    java.util.Map.of("name", "Conjunto NHARA _ LISO")))),
                    List.of(), List.of(), List.of());
        });

        var result = service.execute(service.prepare(50L));

        assertEquals("PRODUCTOS", result.intent());
        assertTrue(result.draft().contains("No encontré ese modelo exacto"), result.draft());
        assertTrue(result.draft().contains("ALESSIA ENTERO"), result.draft());
        assertTrue(result.draft().contains("NHARA _ LISO"), result.draft());
        assertFalse(result.draft().contains("http"), result.draft());
        verifyNoInteractions(provider);
    }

    @Test
    void palabraDeColorNoDespliegaTodoElCatalogo() {
        CrmWhatsappAiJob job = job("Oscuro");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L))
                .thenReturn(Optional.of(configWithIntent("COLORES_TALLAS")));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findRecentActiveMessages(any(), any())).thenReturn(List.of(job.getMessage()));
        when(provider.classify(any())).thenReturn(new ClassificationResult(
                "COLORES_TALLAS", 100, false, "color aislado", List.of(), new Usage(10, 5, 15)));
        when(tools.execute(any(), any())).thenReturn(new ExecutionResult(
                List.of(java.util.Map.of("tool", "buscar_productos", "products", List.of(
                        java.util.Map.of("name", "ALESSIA ENTERO",
                                "availableColors", List.of("GRIS OSCURO"),
                                "availableSizes", List.of("L")),
                        java.util.Map.of("name", "ALICE LISO",
                                "availableColors", List.of("GRIS OSCURO"),
                                "availableSizes", List.of("L"))))),
                List.of(), List.of(), List.of()));

        var result = service.execute(service.prepare(50L));

        assertEquals("COLORES_TALLAS", result.intent());
        assertTrue(result.draft().contains("nombre exacto del modelo"));
        assertTrue(!result.draft().contains("ALESSIA ENTERO"));
        assertTrue(!result.draft().contains("ALICE LISO"));
    }

    @Test
    void colorAbreviadoSeResuelveYConLaTallaPreguntaSoloLaCantidad() {
        CrmWhatsappAiJob job = job("Deseo el beige en modelo Lyana en talla S");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        CrmWhatsappAiConfig config = configWithIntent("COLORES_TALLAS");
        config.setNaturalResponseEnabled(true);
        config.setNaturalResponseRolloutPercent(100);
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(config));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findRecentActiveMessages(any(), any())).thenReturn(List.of(job.getMessage()));
        when(provider.classify(any())).thenReturn(new ClassificationResult(
                "COLORES_TALLAS", 100, false, "variante solicitada", List.of(), new Usage(10, 5, 15)));
        java.util.Map<String, Object> lyana = java.util.Map.of(
                "productId", 31,
                "name", "LYANA",
                "availableColors", List.of("BEIGE CLARO", "NEGRO"),
                "availableSizes", List.of("S", "M"),
                "variants", List.of(
                        java.util.Map.of("variantId", 301, "color", "BEIGE CLARO", "size", "S", "stock", 4),
                        java.util.Map.of("variantId", 302, "color", "NEGRO", "size", "S", "stock", 2)));
        when(tools.execute(any(), any())).thenReturn(new ExecutionResult(
                List.of(java.util.Map.of("tool", "buscar_productos", "products", List.of(lyana))),
                List.of(), List.of(), List.of()));

        var result = service.execute(service.prepare(50L));

        assertEquals("COLORES_TALLAS", result.intent());
        assertTrue(result.draft().contains("LYANA"));
        assertTrue(result.draft().contains("BEIGE CLARO"));
        assertTrue(result.draft().contains("talla S"));
        assertTrue(result.draft().contains("¿Cuántas unidades deseas?"));
        assertTrue(!result.draft().contains("¿Qué color"));
        assertTrue(!result.draft().contains("¿Qué talla"));
        assertTrue(!result.draft().contains("otro producto o tema"));
        verify(provider, org.mockito.Mockito.never()).generateDraft(any());
    }

    @Test
    void modeloElegidoAdjuntaPrimeroGuiaYLuegoImagenGlobalDelMismoProducto() {
        CrmWhatsappAiJob job = job("Annie entero");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(config()));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findRecentActiveMessages(any(), any())).thenReturn(List.of(job.getMessage()));
        when(memory.resolvePendingReply(any(), any(), any()))
                .thenReturn(new PendingReplyResolution("PRODUCTOS", "", false, "Annie entero"));
        MediaReference global = new MediaReference("PRODUCT_GLOBAL_IMAGE", 25, null,
                "ANNIE ENTERO", "", "/storage/productos/annie.webp", "/storage/productos/annie-thumb.webp");
        MediaReference guide = new MediaReference("SIZE_GUIDE", 25, null,
                "ANNIE ENTERO", "", "/storage/productos/annie-guia.webp", "/storage/productos/annie-guia-thumb.webp");
        java.util.Map<String, Object> product = java.util.Map.of(
                "productId", 25,
                "name", "ANNIE ENTERO",
                "availableColors", List.of("PLATA"),
                "availableSizes", List.of("S"),
                "variants", List.of(java.util.Map.of("currentPrice", "75.00", "stock", 3)));
        when(tools.execute(any(), any())).thenReturn(new ExecutionResult(
                List.of(java.util.Map.of("tool", "buscar_productos", "products", List.of(product))),
                List.of(), List.of(), List.of(global, guide)));

        var result = service.execute(service.prepare(50L));

        assertEquals(List.of(guide, global), result.suggestedMedia());
        assertTrue(result.draft().contains("talla y color deseas"));
        verifyNoInteractions(provider);
    }

    @Test
    void quieroUnoResuelveLaCantidadPendienteAntesDeBuscarProductos() {
        CrmWhatsappAiJob job = job("Quiero uno");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(config()));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findRecentActiveMessages(any(), any())).thenReturn(List.of(job.getMessage()));
        when(memory.resolvePendingReply(any(), any(), any()))
                .thenReturn(new PendingReplyResolution("MODIFICAR_CARRITO",
                        "🛒 Así quedaría tu pedido con 1 unidad de ALESSIA ENTERO.", false));

        var result = service.execute(service.prepare(50L));

        assertEquals("MODIFICAR_CARRITO", result.intent());
        assertTrue(result.draft().contains("1 unidad de ALESSIA ENTERO"));
        verifyNoInteractions(provider, tools);
    }

    @Test
    void catalogoVirtualEnviaLaWebCuandoLaIntencionEstaHabilitada() {
        CrmWhatsappAiJob job = job("Deseo ver el catálogo virtual");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L))
                .thenReturn(Optional.of(configWithIntent("ENLACE_ECOMMERCE")));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findRecentActiveMessages(any(), any())).thenReturn(List.of(job.getMessage()));
        when(tools.execute(any(), any())).thenReturn(new ExecutionResult(
                List.of(java.util.Map.of(
                        "tool", "buscar_productos",
                        "query", "",
                        "hasMore", true,
                        "products", List.of(
                                java.util.Map.of("name", "ALESSIA ENTERO"),
                                java.util.Map.of("name", "ANNIE ENTERO")))),
                List.of(), List.of(), List.of()));

        var result = service.execute(service.prepare(50L));

        assertEquals("ENLACE_ECOMMERCE", result.intent());
        assertTrue(result.draft().contains("https://kiments.com.pe"));
        assertTrue(result.draft().contains("ALESSIA ENTERO"));
        assertTrue(result.draft().contains("ANNIE ENTERO"));
        assertTrue(result.draft().contains("ver más productos"));
        assertTrue(result.draft().contains("sus fotos, precio, colores, tallas disponibles y su guía de medidas"));
        verifyNoInteractions(provider);
    }

    @Test
    void catalogoVirtualNoExponeElEnlaceCuandoLaIntencionEstaDeshabilitada() {
        CrmWhatsappAiJob job = job("Quiero ver el catálogo");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(config()));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findRecentActiveMessages(any(), any())).thenReturn(List.of(job.getMessage()));
        when(tools.execute(any(), any())).thenReturn(new ExecutionResult(
                List.of(java.util.Map.of(
                        "tool", "buscar_productos",
                        "query", "",
                        "hasMore", false,
                        "products", List.of(java.util.Map.of("name", "BELEN")))),
                List.of(), List.of(), List.of()));

        var result = service.execute(service.prepare(50L));

        assertEquals("PRODUCTOS", result.intent());
        assertTrue(!result.draft().contains("https://kiments.com.pe"));
        assertTrue(result.draft().contains("BELEN"));
        assertTrue(result.draft().contains("sus fotos, precio, colores, tallas disponibles y su guía de medidas"));
        verifyNoInteractions(provider);
    }

    @Test
    void listadoGeneralSiempreMuestraElCatalogoValidadoSinRedaccionLibre() {
        CrmWhatsappAiJob job = job("Que productos tienes?");
        CrmWhatsappAiConfig config = config();
        config.setNaturalResponseEnabled(true);
        config.setNaturalResponseRolloutPercent(100);
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(config));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findRecentActiveMessages(any(), any())).thenReturn(List.of(job.getMessage()));
        when(tools.execute(any(), any())).thenReturn(new ExecutionResult(
                List.of(java.util.Map.of("tool", "buscar_productos", "hasMore", false,
                        "products", List.of(
                                java.util.Map.of("name", "BELEN"),
                                java.util.Map.of("name", "EMMA")))),
                List.of(), List.of(), List.of()));

        var result = service.execute(service.prepare(50L));

        assertTrue(result.draft().contains("BELEN"), result.draft());
        assertTrue(result.draft().contains("EMMA"), result.draft());
        assertTrue(result.draft().contains("Modelos disponibles"));
        assertFalse(result.naturalResponseUsed());
        assertFalse(result.fallbackUsed());
        verifyNoInteractions(provider);
    }

    @Test
    void consultaAjenaAlNegocioNoConsumeGeminiNiMezclaElContextoAnterior() {
        CrmWhatsappAiJob job = job("Cuanto es 2+2");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(config()));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findRecentActiveMessages(any(), any())).thenReturn(List.of(job.getMessage()));

        var result = service.execute(service.prepare(50L));

        assertEquals("FUERA_DE_ALCANCE", result.intent());
        assertEquals("Lo siento, no tengo información sobre ese tema. ¡Pero puedo ayudarte con nuestros productos o pedidos!",
                result.draft());
        verifyNoInteractions(provider, tools);
    }

    @Test
    void productosEnPreventaSeConsultanDesdeElCatalogoReal() {
        CrmWhatsappAiJob job = job("Productos en preventa tienes");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(config()));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findRecentActiveMessages(any(), any())).thenReturn(List.of(job.getMessage()));
        when(tools.execute(any(), any())).thenAnswer(invocation -> {
            List<ToolCall> calls = invocation.getArgument(1);
            assertEquals(true, calls.getFirst().arguments().get("preorderOnly"));
            return new ExecutionResult(List.of(java.util.Map.of(
                    "tool", "buscar_productos",
                    "products", List.of(java.util.Map.of(
                            "name", "LYANA", "preventa", true,
                            "fechaEnvioPreventa", "2026-10-14")))),
                    List.of(), List.of(), List.of());
        });

        var result = service.execute(service.prepare(50L));

        assertEquals("PRODUCTOS", result.intent());
        assertTrue(result.draft().contains("LYANA"));
        assertTrue(result.draft().contains("14 de octubre de 2026"));
        verifyNoInteractions(provider);
    }

    @Test
    void catalogoGeneralNoDependeDeGeminiAunqueLaRedaccionNaturalEsteHabilitada() {
        CrmWhatsappAiJob job = job("Que productos tienes?");
        CrmWhatsappAiConfig config = config();
        config.setNaturalResponseEnabled(true);
        config.setNaturalResponseRolloutPercent(100);
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(config));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findRecentActiveMessages(any(), any())).thenReturn(List.of(job.getMessage()));
        when(tools.execute(any(), any())).thenReturn(new ExecutionResult(
                List.of(java.util.Map.of("tool", "buscar_productos", "hasMore", false,
                        "products", List.of(java.util.Map.of("name", "EMMA")))),
                List.of(), List.of(), List.of()));
        var result = service.execute(service.prepare(50L));

        assertTrue(result.draft().contains("EMMA"));
        assertFalse(result.naturalResponseUsed());
        assertFalse(result.fallbackUsed());
        verifyNoInteractions(provider);
    }

    @Test
    void politicaDeCambiosPasaDirectamenteAUnaAsesora() {
        CrmWhatsappAiJob job = job("Cual es la politica de cambios?");
        CrmWhatsappAiConfig config = configWithIntent("POLITICAS");
        config.setNaturalResponseEnabled(true);
        config.setNaturalResponseRolloutPercent(100);
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(config));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findRecentActiveMessages(any(), any())).thenReturn(List.of(job.getMessage()));
        when(provider.classify(any())).thenReturn(new ClassificationResult(
                "POLITICAS", 100, false, "consulta de politica", List.of(), new Usage(10, 5, 15)));
        when(tools.execute(any(), any())).thenReturn(new ExecutionResult(
                List.of(java.util.Map.of("tool", "consultar_informacion_negocio", "sources", List.of(
                        java.util.Map.of("title", "Cambios", "category", "POLITICAS",
                                "content", "No realizamos cambios de talla, modelo ni color.")))),
                List.of(), List.of(), List.of()));
        when(provider.generateDraft(any())).thenThrow(new AiProviderException("Gemini no disponible", false));

        var result = service.execute(service.prepare(50L));

        assertEquals("ASESOR_SOLICITADO", result.intent());
        assertTrue(result.requiresHuman());
        assertTrue(result.reason().contains("posventa"));
        verifyNoInteractions(provider, tools);
    }

    @Test
    void falloDeClasificacionConsultaHerramientasYRespondeConRespaldo() {
        CrmWhatsappAiJob job = job("Cuanto cuesta BELEN?");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(config()));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findRecentActiveMessages(any(), any())).thenReturn(List.of(job.getMessage()));
        when(provider.classify(any())).thenThrow(new AiProviderException("Respuesta JSON incompleta", false));
        when(tools.execute(any(), any())).thenReturn(new ExecutionResult(
                List.of(java.util.Map.of("tool", "buscar_productos", "products", List.of(
                        java.util.Map.of("name", "BELEN",
                                "availableColors", List.of("NEGRO"), "availableSizes", List.of("S", "M"),
                                "variants", List.of(java.util.Map.of("currentPrice", 75.00)))))),
                List.of(), List.of(), List.of()));

        var result = service.execute(service.prepare(50L));

        assertEquals("PRECIO", result.intent());
        assertTrue(result.draft().contains("BELEN"));
        assertTrue(result.draft().contains("75.00"));
        assertTrue(result.fallbackUsed());
        assertTrue(result.fallbackReason().contains("JSON incompleta"));
        verify(safety).recordFallback(any(), any());
    }

    @Test
    void verMasProductosConsultaLaSegundaPaginaSinRepetirLaWeb() {
        CrmWhatsappAiJob job = job("Ver más productos");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L))
                .thenReturn(Optional.of(configWithIntent("ENLACE_ECOMMERCE")));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findRecentActiveMessages(any(), any())).thenReturn(List.of(job.getMessage()));
        when(tools.execute(any(), any())).thenAnswer(invocation -> {
            List<ToolCall> calls = invocation.getArgument(1);
            assertEquals(1, calls.getFirst().arguments().get("page"));
            return new ExecutionResult(
                    List.of(java.util.Map.of(
                            "tool", "buscar_productos",
                            "query", "",
                            "hasMore", false,
                            "products", List.of(java.util.Map.of("name", "LYANA")))),
                    List.of(), List.of(), List.of());
        });

        var result = service.execute(service.prepare(50L));

        assertEquals("ENLACE_ECOMMERCE", result.intent());
        assertTrue(result.draft().contains("LYANA"));
        assertTrue(!result.draft().contains("https://kiments.com.pe"));
        verifyNoInteractions(provider);
    }

    @Test
    void entregaProntaExcluyePreventaYFiltraLaTallaSolicitadaSinGemini() {
        CrmWhatsappAiJob job = job("Hola me puedes mostrar lo que tienes disponible para entrega pronta\n"
                + "No lo que es preventa porque tengo un compromiso\n"
                + "Entonces necesito lo que tienes en stock\n"
                + "En talla M");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(config()));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findRecentActiveMessages(any(), any())).thenReturn(List.of(job.getMessage()));
        when(tools.execute(any(), any())).thenAnswer(invocation -> {
            List<ToolCall> calls = invocation.getArgument(1);
            assertEquals(true, calls.getFirst().arguments().get("readyStockOnly"));
            assertEquals("M", calls.getFirst().arguments().get("size"));
            assertEquals("", calls.getFirst().arguments().get("q"));
            return new ExecutionResult(
                    List.of(java.util.Map.of(
                            "tool", "buscar_productos",
                            "hasMore", false,
                            "products", List.of(
                                    java.util.Map.of("name", "BELEN", "preventa", false,
                                            "availableSizes", List.of("M")),
                                    java.util.Map.of("name", "EMMA", "preventa", false,
                                            "availableSizes", List.of("M"))))),
                    List.of(), List.of(), List.of());
        });

        var result = service.execute(service.prepare(50L));

        assertEquals("PRODUCTOS", result.intent());
        assertTrue(result.draft().contains("entrega inmediata en talla M"));
        assertTrue(result.draft().contains("BELEN"));
        assertTrue(result.draft().contains("EMMA"));
        assertTrue(result.draft().contains("no son preventa"));
        verifyNoInteractions(provider);
    }

    @Test
    void productoParaMandarHoyListaNombresSinEnlaces() {
        CrmWhatsappAiJob job = job("Producto para mandar hoy");
        CrmWhatsappAiConfig config = configWithIntent("ENLACE_ECOMMERCE");
        config.setNaturalResponseEnabled(true);
        config.setNaturalResponseRolloutPercent(100);
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(config));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findRecentActiveMessages(any(), any())).thenReturn(List.of(job.getMessage()));
        when(tools.execute(any(), any())).thenAnswer(invocation -> {
            List<ToolCall> calls = invocation.getArgument(1);
            assertEquals(true, calls.getFirst().arguments().get("readyStockOnly"));
            return new ExecutionResult(
                    List.of(java.util.Map.of("tool", "buscar_productos", "products", List.of(
                            java.util.Map.of("name", "BELEN", "ecommerceUrl",
                                    "https://kiments.com.pe/productos/belen"),
                            java.util.Map.of("name", "EMMA", "ecommerceUrl",
                                    "https://kiments.com.pe/productos/emma")))),
                    List.of(), List.of(), List.of());
        });

        var result = service.execute(service.prepare(50L));

        assertTrue(result.draft().contains("BELEN"), result.draft());
        assertTrue(result.draft().contains("EMMA"), result.draft());
        assertFalse(result.draft().contains("http"), result.draft());
        verifyNoInteractions(provider);
    }

    @Test
    void productosParaEnvioHoyListaNombresSinEnlaces() {
        CrmWhatsappAiJob job = job("Que productos tienes para envios el dia hoy");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L))
                .thenReturn(Optional.of(configWithIntent("ENLACE_ECOMMERCE")));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findRecentActiveMessages(any(), any())).thenReturn(List.of(job.getMessage()));
        when(tools.execute(any(), any())).thenReturn(new ExecutionResult(
                List.of(java.util.Map.of("tool", "buscar_productos", "products", List.of(
                        java.util.Map.of("name", "MARIA ENTERO", "ecommerceUrl",
                                "https://kiments.com.pe/productos/maria-entero"),
                        java.util.Map.of("name", "FATIMA", "ecommerceUrl",
                                "https://kiments.com.pe/productos/fatima")))),
                List.of(), List.of(), List.of()));

        var result = service.execute(service.prepare(50L));

        assertTrue(result.draft().contains("MARIA ENTERO"), result.draft());
        assertTrue(result.draft().contains("FATIMA"), result.draft());
        assertFalse(result.draft().contains("http"), result.draft());
        verifyNoInteractions(provider);
    }

    @Test
    void listadoGeneralConEnlacesHabilitadosMuestraNombresSinUrl() {
        CrmWhatsappAiJob job = job("Que productos tienes");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L))
                .thenReturn(Optional.of(configWithIntent("ENLACE_ECOMMERCE")));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findRecentActiveMessages(any(), any())).thenReturn(List.of(job.getMessage()));
        when(tools.execute(any(), any())).thenReturn(new ExecutionResult(
                List.of(java.util.Map.of("tool", "buscar_productos", "products", List.of(
                        java.util.Map.of("name", "BELEN", "ecommerceUrl",
                                "https://kiments.com.pe/productos/belen"),
                        java.util.Map.of("name", "EMMA", "ecommerceUrl",
                                "https://kiments.com.pe/productos/emma")))),
                List.of(), List.of(), List.of()));

        var result = service.execute(service.prepare(50L));

        assertTrue(result.draft().contains("BELEN"));
        assertTrue(result.draft().contains("EMMA"));
        assertFalse(result.draft().contains("http"), result.draft());
        verifyNoInteractions(provider);
    }

    @Test
    void productosQueNoSonPreventaConsultaStockRealSinBuscarLaFraseComoProducto() {
        CrmWhatsappAiJob job = job("Quiero productos que no son preventa por favor");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(config()));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findRecentActiveMessages(any(), any())).thenReturn(List.of(job.getMessage()));
        when(tools.execute(any(), any())).thenAnswer(invocation -> {
            List<ToolCall> calls = invocation.getArgument(1);
            assertEquals("", calls.getFirst().arguments().get("q"));
            assertEquals(true, calls.getFirst().arguments().get("readyStockOnly"));
            return new ExecutionResult(
                    List.of(java.util.Map.of(
                            "tool", "buscar_productos",
                            "hasMore", false,
                            "products", List.of(
                                    java.util.Map.of("name", "BELEN", "preventa", false),
                                    java.util.Map.of("name", "EMMA", "preventa", false)))),
                    List.of(), List.of(), List.of());
        });

        var result = service.execute(service.prepare(50L));

        assertEquals("PRODUCTOS", result.intent());
        assertTrue(result.draft().contains("BELEN"));
        assertTrue(result.draft().contains("EMMA"));
        assertTrue(result.draft().contains("no son preventa"));
        assertTrue(!result.draft().contains("No encontré ese producto"));
        verifyNoInteractions(provider);
    }

    @Test
    void tallaEnMensajePosteriorConservaElFiltroDeEntregaInmediata() {
        CrmWhatsappAiJob job = job("En talla M");
        LocalDateTime currentTime = LocalDateTime.of(2026, 10, 5, 9, 41, 20);
        job.getMessage().setCreatedAt(currentTime);
        CrmWhatsappMessage previousReply = incomingMessage(job.getConversation(), 19L,
                "✨ Disponibles para entrega inmediata. Estos modelos tienen stock actual y no son preventa.",
                currentTime.minusSeconds(8));
        previousReply.setDirection("OUTGOING");
        previousReply.setOrigin("AI_AUTOMATIC");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(config()));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findRecentActiveMessages(any(), any()))
                .thenReturn(List.of(job.getMessage(), previousReply));
        when(tools.execute(any(), any())).thenAnswer(invocation -> {
            List<ToolCall> calls = invocation.getArgument(1);
            assertEquals(true, calls.getFirst().arguments().get("readyStockOnly"));
            assertEquals("M", calls.getFirst().arguments().get("size"));
            return new ExecutionResult(
                    List.of(java.util.Map.of("tool", "buscar_productos", "hasMore", false,
                            "products", List.of(java.util.Map.of("name", "BELEN")))),
                    List.of(), List.of(), List.of());
        });

        var result = service.execute(service.prepare(50L));

        assertTrue(result.draft().contains("entrega inmediata en talla M"));
        assertTrue(result.draft().contains("BELEN"));
        verifyNoInteractions(provider);
    }

    @Test
    void tallaNumericaReutilizaElProductoRecordadoYEnviaSuGuia() {
        CrmWhatsappAiJob job = job("Yo soy talla 30 cuál debería usar?");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L))
                .thenReturn(Optional.of(configWithIntent("GUIA_TALLAS")));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findRecentActiveMessages(any(), any())).thenReturn(List.of(job.getMessage()));
        when(memory.rememberedProductName(10L)).thenReturn("BELEN");
        when(provider.classify(any())).thenReturn(new ClassificationResult(
                "PRODUCTOS", 98, false, "consulta de talla", List.of(), new Usage(10, 5, 15)));
        MediaReference guide = new MediaReference("SIZE_GUIDE", 45, null,
                "BELEN", "", "/storage/productos/belen-guia.webp", "/storage/productos/belen-guia-thumb.webp");
        when(tools.execute(any(), any())).thenAnswer(invocation -> {
            List<ToolCall> calls = invocation.getArgument(1);
            assertEquals("BELEN", calls.getFirst().arguments().get("q"));
            return new ExecutionResult(
                    List.of(java.util.Map.of("tool", "buscar_productos", "products", List.of(java.util.Map.of(
                            "productId", 45, "name", "BELEN", "sizeGuideUrl", guide.url())))),
                    List.of(), List.of(), List.of(guide));
        });

        var result = service.execute(service.prepare(50L));

        assertEquals("GUIA_TALLAS", result.intent());
        assertEquals(List.of(guide), result.suggestedMedia());
        assertTrue(result.draft().contains("BELEN"));
    }

    @Test
    void productoEspecificoIncluyeEnlaceEcommerceCuandoLaIntencionEstaActiva() {
        CrmWhatsappAiJob job = job("Julieta por favor");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L))
                .thenReturn(Optional.of(configWithIntent("ENLACE_ECOMMERCE")));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findRecentActiveMessages(any(), any())).thenReturn(List.of(job.getMessage()));
        when(memory.resolvePendingReply(any(), any(), any()))
                .thenReturn(new PendingReplyResolution("PRODUCTOS", "", false, "Julieta por favor"));
        when(tools.execute(any(), any())).thenReturn(new ExecutionResult(
                List.of(java.util.Map.of("tool", "buscar_productos", "products", List.of(java.util.Map.of(
                        "name", "JULIETA",
                        "ecommerceUrl", "https://kiments.com.pe/productos/julieta",
                        "availableColors", List.of("Negro", "Beige"),
                        "availableSizes", List.of("XS", "S", "M", "L"),
                        "variants", List.of(java.util.Map.of("currentPrice", "75.00", "offerPrice", "")))))),
                List.of(), List.of(), List.of()));

        var result = service.execute(service.prepare(50L));

        assertTrue(result.draft().contains("https://kiments.com.pe/productos/julieta"));
        assertTrue(result.draft().contains("S/75.00"));
        verifyNoInteractions(provider);
    }

    @Test
    void solicitudDeLinkReutilizaProductoRecordadoSinConsumirGemini() {
        CrmWhatsappAiJob job = job("Pasame el link por favor");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L))
                .thenReturn(Optional.of(configWithIntent("ENLACE_ECOMMERCE")));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findRecentActiveMessages(any(), any())).thenReturn(List.of(job.getMessage()));
        when(memory.rememberedProductName(10L)).thenReturn("JULIETA");
        when(tools.execute(any(), any())).thenAnswer(invocation -> {
            List<ToolCall> calls = invocation.getArgument(1);
            assertEquals("JULIETA", calls.getFirst().arguments().get("q"));
            return new ExecutionResult(
                    List.of(java.util.Map.of("tool", "buscar_productos", "products", List.of(java.util.Map.of(
                            "name", "JULIETA",
                            "ecommerceUrl", "https://kiments.com.pe/productos/julieta")))),
                    List.of(), List.of(), List.of());
        });

        var result = service.execute(service.prepare(50L));

        assertEquals("ENLACE_ECOMMERCE", result.intent());
        assertEquals("🛍️ *JULIETA*\nhttps://kiments.com.pe/productos/julieta", result.draft());
        verifyNoInteractions(provider);
    }

    @Test
    void catalogoVacioRespondeSinTransferirAUnAsesor() {
        CrmWhatsappAiJob job = job("Que prendas tienes ?");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(config()));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findRecentActiveMessages(any(), any())).thenReturn(List.of(job.getMessage()));
        when(provider.classify(any())).thenReturn(new ClassificationResult(
                "PRODUCTOS", 100, false, "catalogo general", List.of(), new Usage(10, 5, 15)));
        when(tools.execute(any(), any())).thenReturn(new ExecutionResult(
                List.of(java.util.Map.of("tool", "buscar_productos", "query", "", "products", List.of())),
                List.of(), List.of(), List.of()));

        var result = service.execute(service.prepare(50L));

        assertTrue(!result.requiresHuman());
        assertTrue(result.draft().contains("no encuentro productos disponibles"));
        verify(provider, org.mockito.Mockito.never()).generateDraft(any());
    }

    @Test
    void listaSoloDiezPromocionesYOfreceContinuar() {
        CrmWhatsappAiJob job = job("Tienes promociones disponibles?");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(configWithIntent("PROMOCIONES")));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findRecentActiveMessages(any(), any())).thenReturn(List.of(job.getMessage()));
        List<java.util.Map<String, Object>> promotions = java.util.stream.IntStream.rangeClosed(1, 10)
                .mapToObj(index -> java.util.Map.<String, Object>of(
                        "promotionId", index,
                        "name", "Combo " + index,
                        "comboPrice", 140,
                        "products", List.of(java.util.Map.of(
                                "name", "EMMA", "quantity", 2))))
                .toList();
        when(tools.execute(any(), any())).thenReturn(new ExecutionResult(
                List.of(new java.util.LinkedHashMap<>(java.util.Map.of(
                        "tool", "consultar_promociones",
                        "page", 0,
                        "pageSize", 10,
                        "total", 54,
                        "hasMore", true,
                        "promotions", promotions))),
                List.of(), List.of(), List.of()));

        var result = service.execute(service.prepare(50L));

        assertTrue(result.draft().contains("Promociones disponibles (1-10 de 54)"), result.draft());
        assertTrue(result.draft().contains("2 x EMMA"), result.draft());
        assertTrue(result.draft().contains("ver más promociones"), result.draft());
        verifyNoInteractions(provider);
    }

    @Test
    void ofertaGenericaPriorizaElMensajeActualSobreProductoYPreguntaPendiente() {
        CrmWhatsappAiJob job = job("Hay ofertas?");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L))
                .thenReturn(Optional.of(configWithIntent("PROMOCIONES")));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        CrmWhatsappMessage previous = incomingMessage(job.getConversation(), 19L,
                "El producto EMMA esta disponible. Que color deseas?", LocalDateTime.now().minusMinutes(1));
        previous.setDirection("OUTGOING");
        previous.setOrigin("AI_AUTOMATIC");
        when(messages.findRecentActiveMessages(any(), any())).thenReturn(List.of(job.getMessage(), previous));
        when(memory.pendingQuestionFor(10L)).thenReturn(CrmWhatsappAiPendingQuestion.COLOR);
        when(memory.selectionFor(10L)).thenReturn(new MemorySelection(30, "EMMA", "", "", null));
        when(tools.execute(any(), any())).thenAnswer(invocation -> {
            List<ToolCall> calls = invocation.getArgument(1);
            assertEquals("consultar_promociones", calls.getFirst().name());
            assertEquals("", calls.getFirst().arguments().get("q"));
            return promotionExecution("", List.of(promotionMap(29, "combo 29", 125, 130, 5,
                    List.of(java.util.Map.of("name", "EMMA", "quantity", 2)))));
        });

        var prepared = service.prepare(50L);
        var result = service.execute(prepared);

        assertTrue(prepared.conversationContext().contains("MENSAJE ACTUAL DEL CLIENTE (PRIORIDAD):\nHay ofertas?"));
        assertEquals("PROMOCIONES", result.intent());
        assertTrue(result.draft().contains("combo 29"), result.draft());
        assertFalse(result.draft().contains("QuÃ© color"), result.draft());
        verifyNoInteractions(provider);
    }

    @Test
    void promocionesDeEseProductoUsanElModeloRecordado() {
        CrmWhatsappAiJob job = job("Tienes promociones con ese producto?");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L))
                .thenReturn(Optional.of(configWithIntent("PROMOCIONES")));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findRecentActiveMessages(any(), any())).thenReturn(List.of(job.getMessage()));
        when(memory.selectionFor(10L)).thenReturn(
                new MemorySelection(31, "ALESSIA RAYAS", "", "", null));
        when(tools.execute(any(), any())).thenAnswer(invocation -> {
            List<ToolCall> calls = invocation.getArgument(1);
            assertEquals("ALESSIA RAYAS", calls.getFirst().arguments().get("q"));
            return promotionExecutionForAlessia();
        });

        var result = service.execute(service.prepare(50L));

        assertEquals("PROMOCIONES", result.intent());
        assertTrue(result.draft().contains("Promociones que incluyen ALESSIA RAYAS"), result.draft());
        assertTrue(result.draft().contains("combo 46"), result.draft());
        assertFalse(result.draft().contains("combo 54"), result.draft());
        verifyNoInteractions(provider);
    }

    @Test
    void promocionesConProductoExplicitoFiltranPorSuNombre() {
        CrmWhatsappAiJob job = job("Con Alessia rayas tienes promociones?");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L))
                .thenReturn(Optional.of(configWithIntent("PROMOCIONES")));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findRecentActiveMessages(any(), any())).thenReturn(List.of(job.getMessage()));
        when(tools.execute(any(), any())).thenAnswer(invocation -> {
            List<ToolCall> calls = invocation.getArgument(1);
            assertEquals("alessia rayas", calls.getFirst().arguments().get("q"));
            return promotionExecutionForAlessia();
        });

        var result = service.execute(service.prepare(50L));

        assertEquals("PROMOCIONES", result.intent());
        assertTrue(result.draft().contains("combo 46"), result.draft());
        verifyNoInteractions(provider);
    }

    @Test
    void descuentoPorDosUnidadesUsaElProductoYPreciosDeLaPromocionReal() {
        CrmWhatsappAiJob job = job("Si llevo 2 Emma hay descuento?");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L))
                .thenReturn(Optional.of(configWithIntent("PROMOCIONES")));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findRecentActiveMessages(any(), any())).thenReturn(List.of(job.getMessage()));
        when(tools.execute(any(), any())).thenAnswer(invocation -> {
            List<ToolCall> calls = invocation.getArgument(1);
            assertEquals("emma", calls.getFirst().arguments().get("q"));
            java.util.Map<String, Object> promotion = new java.util.LinkedHashMap<>();
            promotion.put("promotionId", 60);
            promotion.put("name", "2 EMMA");
            promotion.put("regularPrice", 130);
            promotion.put("savings", 10);
            promotion.put("comboPrice", 120);
            promotion.put("products", List.of(java.util.Map.of("name", "EMMA", "quantity", 2)));
            java.util.Map<String, Object> toolResult = new java.util.LinkedHashMap<>();
            toolResult.put("tool", "consultar_promociones");
            toolResult.put("query", "emma");
            toolResult.put("page", 0);
            toolResult.put("pageSize", 10);
            toolResult.put("total", 1);
            toolResult.put("hasMore", false);
            toolResult.put("promotions", List.of(promotion));
            return new ExecutionResult(
                    List.of(toolResult),
                    List.of(), List.of(), List.of());
        });

        var result = service.execute(service.prepare(50L));

        assertEquals("PROMOCIONES", result.intent());
        assertTrue(result.draft().contains("2 unidades de *EMMA*"), result.draft());
        assertTrue(result.draft().contains("Precio normal: S/130.00"), result.draft());
        assertTrue(result.draft().contains("Descuento: -S/10.00"), result.draft());
        assertTrue(result.draft().contains("Precio final: S/120.00"), result.draft());
        verifyNoInteractions(provider);
    }

    @Test
    void descuentoGenericoPorDosListaProductosSinConfundirHayDescuentoConUnModelo() {
        CrmWhatsappAiJob job = job("Quiero llevar 2 hay descuento?");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L))
                .thenReturn(Optional.of(configWithIntent("PROMOCIONES")));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findRecentActiveMessages(any(), any())).thenReturn(List.of(job.getMessage()));
        when(tools.execute(any(), any())).thenAnswer(invocation -> {
            List<ToolCall> calls = invocation.getArgument(1);
            assertEquals("", calls.getFirst().arguments().get("q"));
            assertEquals(2, calls.getFirst().arguments().get("sameProductQuantity"));
            return promotionExecution("", List.of(promotionMap(28, "combo 28", 120, 130, 10,
                    List.of(java.util.Map.of("name", "EMMA", "quantity", 2)))));
        });

        var result = service.execute(service.prepare(50L));

        assertTrue(result.draft().contains("2 unidades del mismo producto"), result.draft());
        assertTrue(result.draft().contains("EMMA"), result.draft());
        assertFalse(result.draft().contains("HAY DESCUENTO"), result.draft());
        verifyNoInteractions(provider);
    }

    @Test
    void correccionAliceRayasConDosUnidadesConsultaEseProducto() {
        CrmWhatsappAiJob job = job("Me refiero Alice rayas si llevo 2");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L))
                .thenReturn(Optional.of(configWithIntent("PROMOCIONES")));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findRecentActiveMessages(any(), any())).thenReturn(List.of(job.getMessage()));
        when(tools.execute(any(), any())).thenAnswer(invocation -> {
            List<ToolCall> calls = invocation.getArgument(1);
            assertEquals("alice rayas", calls.getFirst().arguments().get("q"));
            assertEquals(2, calls.getFirst().arguments().get("sameProductQuantity"));
            return promotionExecution("alice rayas", List.of());
        });

        var result = service.execute(service.prepare(50L));

        assertEquals("PROMOCIONES", result.intent());
        assertTrue(result.draft().contains("ALICE RAYAS"), result.draft());
        verifyNoInteractions(provider);
    }

    @Test
    void promocionesHayEnAliceRayasFiltraEseModelo() {
        CrmWhatsappAiJob job = job("Que promociones hay en Alice rayas");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L))
                .thenReturn(Optional.of(configWithIntent("PROMOCIONES")));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findRecentActiveMessages(any(), any())).thenReturn(List.of(job.getMessage()));
        when(tools.execute(any(), any())).thenAnswer(invocation -> {
            List<ToolCall> calls = invocation.getArgument(1);
            assertEquals("alice rayas", calls.getFirst().arguments().get("q"));
            return promotionExecution("alice rayas", List.of(
                    promotionMap(49, "combo 49", 145, 150, 5,
                            List.of(java.util.Map.of("name", "ALICE RAYAS", "quantity", 1),
                                    java.util.Map.of("name", "EMMA", "quantity", 1)))));
        });

        var result = service.execute(service.prepare(50L));

        assertTrue(result.draft().contains("Promociones que incluyen ALICE RAYAS (1-1 de 1)"), result.draft());
        assertFalse(result.draft().contains("combo 54"), result.draft());
        verifyNoInteractions(provider);
    }

    @Test
    void comboPorNumeroConsultaLaPromocionExacta() {
        CrmWhatsappAiJob job = job("El combo 28");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L))
                .thenReturn(Optional.of(configWithIntent("PROMOCIONES")));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findRecentActiveMessages(any(), any())).thenReturn(List.of(job.getMessage()));
        when(tools.execute(any(), any())).thenAnswer(invocation -> {
            List<ToolCall> calls = invocation.getArgument(1);
            assertEquals(28, calls.getFirst().arguments().get("promotionNumber"));
            return promotionExecution("", List.of(promotionMap(28, "combo 28", 120, 130, 10,
                    List.of(java.util.Map.of("name", "EMMA", "quantity", 2)))));
        });

        var result = service.execute(service.prepare(50L));

        assertTrue(result.draft().contains("combo 28"), result.draft());
        assertTrue(result.draft().contains("2 x EMMA"), result.draft());
        assertTrue(result.draft().contains("Descuento: -S/10.00"), result.draft());
        verifyNoInteractions(provider);
    }

    @Test
    void anademeloRecuperaElUltimoComboMostradoEIniciaSuSeleccion() {
        CrmWhatsappAiJob job = job("añádemelo");
        CrmWhatsappAiConfig config = configWithIntent("PROMOCIONES");
        config.setIntencionesPermitidas(config.getIntencionesPermitidas() + ",INTENCION_COMPRA");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(config));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        CrmWhatsappMessage previous = incomingMessage(job.getConversation(), 19L,
                "🎁 *combo 42*\n\n👗 Incluye: ALICE RAYAS + LIA RAYAS\n💰 Precio: S/155.00",
                LocalDateTime.now().minusSeconds(30));
        previous.setDirection("OUTGOING");
        previous.setOrigin("AI_AUTOMATIC");
        when(messages.findRecentActiveMessages(any(), any())).thenReturn(List.of(job.getMessage(), previous));
        when(tools.execute(any(), any())).thenAnswer(invocation -> {
            List<ToolCall> calls = invocation.getArgument(1);
            assertEquals("consultar_promociones", calls.getFirst().name());
            assertEquals(42, calls.getFirst().arguments().get("promotionNumber"));
            return promotionExecution("", List.of(promotionMap(42, "combo 42", 155, 170, 15,
                    List.of(java.util.Map.of("name", "ALICE RAYAS", "quantity", 1),
                            java.util.Map.of("name", "LIA RAYAS", "quantity", 1)))));
        });
        when(saleDrafts.applyAiAction(any(), any())).thenReturn(
                new CrmWhatsappAiSaleDraftService.ActionOutcome(
                        "Para completar el combo 42, elige color y talla de ALICE RAYAS.", false, null));

        var result = service.execute(service.prepare(50L));

        assertEquals("INTENCION_COMPRA", result.intent());
        assertTrue(result.draft().contains("ALICE RAYAS"), result.draft());
        verify(saleDrafts).applyAiAction(any(), argThat(action ->
                "ADD_COMBO".equals(action.action())
                        && Integer.valueOf(42).equals(action.promotionId())));
        verifyNoInteractions(provider);
    }

    @Test
    void deseoEsaPromoRecuperaElUltimoComboMostradoEIniciaLaCompra() {
        CrmWhatsappAiJob job = job("Deseo esa promo");
        CrmWhatsappAiConfig config = configWithIntent("PROMOCIONES");
        config.setIntencionesPermitidas(config.getIntencionesPermitidas() + ",INTENCION_COMPRA");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(config));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        CrmWhatsappMessage previous = incomingMessage(job.getConversation(), 19L,
                "🎁 *combo 54*\n\n👗 Incluye: LIA RAYAS + NHARA RAYAS\n💰 Precio: S/140.00",
                LocalDateTime.now().minusSeconds(30));
        previous.setDirection("OUTGOING");
        previous.setOrigin("AI_AUTOMATIC");
        when(messages.findRecentActiveMessages(any(), any())).thenReturn(List.of(job.getMessage(), previous));
        when(tools.execute(any(), any())).thenAnswer(invocation -> {
            List<ToolCall> calls = invocation.getArgument(1);
            assertEquals("consultar_promociones", calls.getFirst().name());
            assertEquals(54, calls.getFirst().arguments().get("promotionNumber"));
            return promotionExecution("", List.of(promotionMap(154, "combo 54", 140, 150, 10,
                    List.of(java.util.Map.of("name", "LIA RAYAS", "quantity", 1),
                            java.util.Map.of("name", "NHARA RAYAS", "quantity", 1)))));
        });
        when(saleDrafts.applyAiAction(any(), any())).thenReturn(
                new CrmWhatsappAiSaleDraftService.ActionOutcome(
                        "Para completar el combo 54, elige color y talla de LIA RAYAS.", false, null));

        var result = service.execute(service.prepare(50L));

        assertEquals("INTENCION_COMPRA", result.intent());
        assertTrue(result.draft().contains("combo 54"), result.draft());
        verify(saleDrafts).applyAiAction(any(), argThat(action ->
                "ADD_COMBO".equals(action.action())
                        && Integer.valueOf(154).equals(action.promotionId())));
        verifyNoInteractions(provider);
    }

    @Test
    void colorYTallaContinuanElComboPendienteSinVolverAInterpretarLaPromocion() {
        CrmWhatsappAiJob job = job("plata y L");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L))
                .thenReturn(Optional.of(configWithIntent("INTENCION_COMPRA")));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findRecentActiveMessages(any(), any())).thenReturn(List.of(job.getMessage()));
        when(memory.pendingQuestionFor(10L)).thenReturn(CrmWhatsappAiPendingQuestion.COMBO_ITEM);
        when(saleDrafts.applyPendingComboItem(any(), eq("plata y L"))).thenReturn(
                new CrmWhatsappAiSaleDraftService.ActionOutcome(
                        "Para completar el combo 45, elige color y talla de ALESSIA ENTERO.",
                        false, null));

        var result = service.execute(service.prepare(50L));

        assertEquals("INTENCION_COMPRA", result.intent());
        assertTrue(result.draft().contains("combo 45"), result.draft());
        assertTrue(result.draft().contains("ALESSIA ENTERO"), result.draft());
        verify(saleDrafts).applyPendingComboItem(any(), eq("plata y L"));
        verifyNoInteractions(provider);
    }

    @Test
    void compraExplicitaDeComboNoSeConfundeConConsultaDePromociones() {
        CrmWhatsappAiJob job = job("El combo 42 quiero comprar");
        CrmWhatsappAiConfig config = configWithIntent("PROMOCIONES");
        config.setIntencionesPermitidas(config.getIntencionesPermitidas() + ",INTENCION_COMPRA");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(config));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findRecentActiveMessages(any(), any())).thenReturn(List.of(job.getMessage()));
        when(tools.execute(any(), any())).thenReturn(promotionExecution("", List.of(
                promotionMap(142, "combo 42", 155, 170, 15,
                        List.of(java.util.Map.of("name", "ALICE RAYAS", "quantity", 1),
                                java.util.Map.of("name", "LIA RAYAS", "quantity", 1))))));
        when(saleDrafts.applyAiAction(any(), any())).thenReturn(
                new CrmWhatsappAiSaleDraftService.ActionOutcome(
                        "Elige color y talla de ALICE RAYAS.", false, null));

        var result = service.execute(service.prepare(50L));

        assertEquals("INTENCION_COMPRA", result.intent());
        verify(saleDrafts).applyAiAction(any(), argThat(action ->
                "ADD_COMBO".equals(action.action())
                        && Integer.valueOf(142).equals(action.promotionId())));
        verifyNoInteractions(provider);
    }

    @Test
    void respondeProductoMasEconomicoConPrecioEImagen() {
        CrmWhatsappAiJob job = job("Cual es el producto más barato que tienes");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(configWithIntent("PRECIO")));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findRecentActiveMessages(any(), any())).thenReturn(List.of(job.getMessage()));
        MediaReference image = new MediaReference("PRODUCT_GLOBAL_IMAGE", 59, null,
                "EMMA", "", "/storage/productos/emma.webp", "");
        when(tools.execute(any(), any())).thenReturn(new ExecutionResult(
                List.of(java.util.Map.of(
                        "tool", "consultar_extremo_precio_producto",
                        "order", "MIN",
                        "product", java.util.Map.of(
                                "productId", 59, "name", "EMMA", "price", 65))),
                List.of(), List.of(), List.of(image)));

        var result = service.execute(service.prepare(50L));

        assertTrue(result.draft().contains("producto más económico"), result.draft());
        assertTrue(result.draft().contains("EMMA"), result.draft());
        assertTrue(result.draft().contains("S/65.00"), result.draft());
        assertEquals(List.of(image), result.suggestedMedia());
        verifyNoInteractions(provider);
    }

    @Test
    void preguntaGeneralDeModelosNoUsaElProductoRecordadoComoConsultaDeStock() {
        CrmWhatsappAiJob job = job("Una consulta que modelos tienes disponible bella");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(config()));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findRecentActiveMessages(any(), any())).thenReturn(List.of(job.getMessage()));
        when(memory.selectionFor(10L)).thenReturn(new MemorySelection(30, "EMMA", "LACRE", "L", 1));
        when(memory.rememberedProductName(10L)).thenReturn("EMMA");
        when(provider.classify(any())).thenReturn(new ClassificationResult(
                "STOCK", 100, false, "consulta general", List.of(), new Usage(10, 5, 15)));
        when(tools.execute(any(), any())).thenAnswer(invocation -> {
            List<ToolCall> calls = invocation.getArgument(1);
            assertEquals("", calls.getFirst().arguments().get("q"));
            return new ExecutionResult(
                    List.of(java.util.Map.of("tool", "buscar_productos", "products",
                            List.of(java.util.Map.of("name", "ALESSIA"), java.util.Map.of("name", "EMMA")))),
                    List.of(), List.of(), List.of());
        });

        var result = service.execute(service.prepare(50L));

        assertEquals("PRODUCTOS", result.intent());
        assertTrue(result.draft().contains("ALESSIA"));
        assertTrue(result.draft().contains("EMMA"));
    }

    @Test
    void respuestaDeEnviosNoSolicitaDatosQueElBackendNoProcesa() {
        CrmWhatsappAiJob job = job("Y que metodo de envio tienes");
        CrmWhatsappAiConfig config = configWithIntent("ENVIOS");
        config.setNaturalResponseEnabled(true);
        config.setNaturalResponseRolloutPercent(100);
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(config));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findRecentActiveMessages(any(), any())).thenReturn(List.of(job.getMessage()));
        when(provider.classify(any())).thenReturn(new ClassificationResult(
                "ENVIOS", 100, false, "consulta de envio", List.of(), new Usage(10, 5, 15)));
        when(tools.execute(any(), any())).thenReturn(shippingKnowledge());
        when(provider.generateDraft(any())).thenReturn(new DraftResult(
                "Realizamos envios por Shalom y ofrecemos recojo en almacen.\n\n"
                        + "¿A que ciudad o distrito deseas el envio?",
                false, "respuesta fundamentada", List.of(), new Usage(12, 8, 20)));

        var result = service.execute(service.prepare(50L));

        assertEquals("ENVIOS", result.intent());
        assertFalse(result.draft().toLowerCase().contains("ciudad o distrito"), result.draft());
        assertTrue(result.draft().contains("¿Qué otro producto o consulta deseas realizar?"));
    }

    @Test
    void consultaDePlazoRespondeConRangoGeneralSinConsultarProgramacion() {
        CrmWhatsappAiJob job = job("Que dia estara listo mi pedido para recoger");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(configWithIntent("ENVIOS")));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findRecentActiveMessages(any(), any())).thenReturn(List.of(job.getMessage()));

        var result = service.execute(service.prepare(50L));

        assertEquals("ENVIOS", result.intent());
        assertTrue(result.draft().contains("1 a 2 días"));
        assertTrue(result.draft().contains("corroborar tus datos"));
        assertTrue(result.draft().contains("productos en preventa"));
        verifyNoInteractions(provider, tools);
    }

    @Test
    void solicitudDeFechaExactaRequiereAsesora() {
        CrmWhatsappAiJob job = job("Necesito la fecha exacta para recoger mi pedido");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(configWithIntent("ENVIOS")));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findRecentActiveMessages(any(), any())).thenReturn(List.of(job.getMessage()));

        var result = service.execute(service.prepare(50L));

        assertTrue(result.requiresHuman());
        assertEquals(com.sistemapos.sistematextil.model.CrmWhatsappAiRunOutcome.HUMAN_REQUIRED, result.outcome());
        assertTrue(result.reason().contains("fecha u hora exacta"));
        verifyNoInteractions(provider, tools);
    }

    @Test
    void cambioDeTallaPasaDirectamenteAUnaAsesora() {
        CrmWhatsappAiJob job = job("Quiero cambiar la talla porque me queda grande");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(config()));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findRecentActiveMessages(any(), any())).thenReturn(List.of(job.getMessage()));

        var result = service.execute(service.prepare(50L));

        assertTrue(result.requiresHuman());
        assertEquals("ASESOR_SOLICITADO", result.intent());
        assertTrue(result.reason().contains("posventa"));
        verifyNoInteractions(provider, tools);
    }

    @Test
    void consultaMayoristaPasaDirectamenteAUnaAsesora() {
        CrmWhatsappAiJob job = job("Quiero una cotizacion por docena para revender en mi boutique");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(config()));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findRecentActiveMessages(any(), any())).thenReturn(List.of(job.getMessage()));

        var result = service.execute(service.prepare(50L));

        assertTrue(result.requiresHuman());
        assertEquals("ASESOR_SOLICITADO", result.intent());
        assertTrue(result.reason().contains("mayorista"));
        verifyNoInteractions(provider, tools);
    }

    @Test
    void respuestaNaturalQueYaTieneCierreGenericoNoAgregaOtraPregunta() {
        CrmWhatsappAiJob job = job("Como debo cuidar el Alessia Entero");
        CrmWhatsappAiConfig config = configWithIntent("CUIDADOS");
        config.setNaturalResponseEnabled(true);
        config.setNaturalResponseRolloutPercent(100);
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(config));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findRecentActiveMessages(any(), any())).thenReturn(List.of(job.getMessage()));
        when(provider.classify(any())).thenReturn(new ClassificationResult(
                "CUIDADOS", 100, false, "consulta de cuidados", List.of(), new Usage(10, 5, 15)));
        when(tools.execute(any(), any())).thenReturn(shippingKnowledge());
        when(provider.generateDraft(any())).thenReturn(new DraftResult(
                "Para cuidarlo, lavalo a mano con agua fria y secalo a la sombra.\n\n"
                        + "¿Te gustaria consultar algun otro tema o producto? ✨",
                false, "respuesta fundamentada", List.of(), new Usage(12, 8, 20)));

        var result = service.execute(service.prepare(50L));

        assertEquals(1, result.draft().chars().filter(character -> character == '?').count(), result.draft());
        assertFalse(result.draft().contains("¿Qué otro producto o consulta deseas realizar?"), result.draft());
    }

    @Test
    void ubicacionCortaDespuesDeEnviosNoSeInterpretaComoProducto() {
        CrmWhatsappAiJob job = job("Arequipa");
        job.getMessage().setCreatedAt(LocalDateTime.of(2026, 10, 6, 10, 9));
        CrmWhatsappMessage previous = incomingMessage(job.getConversation(), 19L,
                "Realizamos envios a nivel nacional mediante la agencia Shalom. "
                        + "¿A que ciudad o distrito deseas que enviemos tu pedido?",
                job.getMessage().getCreatedAt().minusMinutes(1));
        previous.setDirection("OUTGOING");
        previous.setOrigin("AI_AUTOMATIC");
        CrmWhatsappAiConfig config = configWithIntent("ENVIOS");
        config.setNaturalResponseEnabled(true);
        config.setNaturalResponseRolloutPercent(100);
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(config));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findRecentActiveMessages(any(), any())).thenReturn(List.of(job.getMessage(), previous));
        when(tools.execute(any(), any())).thenReturn(shippingKnowledge());
        when(provider.generateDraft(any())).thenReturn(new DraftResult(
                "Realizamos envios a provincia mediante Shalom.",
                false, "respuesta fundamentada", List.of(), new Usage(12, 8, 20)));

        var result = service.execute(service.prepare(50L));

        assertEquals("ENVIOS", result.intent());
        assertFalse(result.draft().contains("modelo no está disponible"));
        assertTrue(result.draft().contains("¿Qué otro producto o consulta deseas realizar?"));
        verify(provider, org.mockito.Mockito.never()).classify(any());
    }

    @Test
    void productoNoEncontradoConsultaLaBaseDeConocimientoAntesDeResponder() {
        CrmWhatsappAiJob job = job("Buenas noches de casualidad tendrá shorts?");
        CrmWhatsappAiConfig config = config();
        config.setNaturalResponseEnabled(true);
        config.setNaturalResponseRolloutPercent(100);
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(config));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findRecentActiveMessages(any(), any())).thenReturn(List.of(job.getMessage()));
        when(provider.classify(any())).thenReturn(new ClassificationResult(
                "PRODUCTOS", 100, false, "consulta de producto", List.of(), new Usage(10, 5, 15)));
        ExecutionResult emptyCatalog = new ExecutionResult(
                List.of(java.util.Map.of("tool", "buscar_productos", "query", "shorts", "products", List.of())),
                List.of(), List.of(), List.of());
        ExecutionResult knowledge = new ExecutionResult(
                List.of(java.util.Map.of("tool", "consultar_informacion_negocio", "sources", List.of(
                        java.util.Map.of("title", "Productos disponibles", "category", "FAQ",
                                "content", "Actualmente trabajamos enterizos y conjuntos; no ofrecemos shorts.")))),
                List.of(java.util.Map.of("tool", "consultar_informacion_negocio", "status", "OK")),
                List.of(java.util.Map.of("tool", "consultar_informacion_negocio")), List.of());
        when(tools.execute(any(), any())).thenReturn(emptyCatalog, knowledge);
        when(provider.generateDraft(any())).thenReturn(new DraftResult(
                "Buenas noches 💛 Por el momento no contamos con shorts; trabajamos enterizos y conjuntos.",
                false, "base de conocimiento", List.of(), new Usage(12, 9, 21)));

        var result = service.execute(service.prepare(50L));

        assertEquals("INFORMACION_NEGOCIO", result.intent());
        assertTrue(result.draft().contains("no contamos con shorts"));
        assertEquals(36, result.usage().totalTokens());
        verify(tools, org.mockito.Mockito.times(2)).execute(any(), any());
    }

    @Test
    void ignoraTransferenciaDelModeloEnConsultaComercialSegura() {
        CrmWhatsappAiJob job = job("Que productos tienes ?");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(config()));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findRecentActiveMessages(any(), any())).thenReturn(List.of(job.getMessage()));
        when(provider.classify(any())).thenReturn(new ClassificationResult(
                "PRODUCTOS", 100, true, "Prefiero que responda un asesor", List.of(), new Usage(10, 5, 15)));
        when(tools.execute(any(), any())).thenReturn(new ExecutionResult(
                List.of(java.util.Map.of("tool", "buscar_productos", "query", "", "products",
                        List.of(java.util.Map.of("name", "MODELO DISPONIBLE")))),
                List.of(), List.of(), List.of()));

        var result = service.execute(service.prepare(50L));

        assertTrue(!result.requiresHuman());
        assertTrue(result.draft().contains("MODELO DISPONIBLE"));
    }

    @Test
    void bajaConfianzaComercialSolicitaDatosSinTransferir() {
        CrmWhatsappAiJob job = job("Hay en mi talla?");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(configWithIntent("STOCK")));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findRecentActiveMessages(any(), any())).thenReturn(List.of(job.getMessage()));
        when(provider.classify(any())).thenReturn(new ClassificationResult(
                "STOCK", 55, false, "Faltan datos", List.of(), new Usage(10, 5, 15)));

        var result = service.execute(service.prepare(50L));

        assertTrue(!result.requiresHuman());
        assertTrue(result.draft().contains("producto, color y talla"));
        verifyNoInteractions(tools);
    }

    @Test
    void reutilizaProductoRecordadoEnConsultaBreveDeColores() {
        CrmWhatsappAiJob job = job("Colores disponibles");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(configWithIntent("COLORES_TALLAS")));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findRecentActiveMessages(any(), any())).thenReturn(List.of(job.getMessage()));
        when(memory.rememberedProductName(10L)).thenReturn("JULIETA");
        when(provider.classify(any())).thenReturn(new ClassificationResult(
                "COLORES_TALLAS", 100, false, "continuacion",
                List.of(new ToolCall("buscar_productos", java.util.Map.of("q", "Colores disponibles"))),
                new Usage(10, 5, 15)));
        when(tools.execute(any(), any())).thenAnswer(invocation -> {
            List<ToolCall> calls = invocation.getArgument(1);
            assertEquals("JULIETA", calls.getFirst().arguments().get("q"));
            return new ExecutionResult(List.of(java.util.Map.of(
                    "tool", "buscar_productos",
                    "productName", "JULIETA", "availableColors", List.of("NEGRO", "BEIGE"))),
                    List.of(), List.of(), List.of());
        });
        when(provider.generateDraft(any())).thenReturn(new DraftResult(
                "Colores de JULIETA.", false, "", List.of(), new Usage(12, 8, 20)));

        var result = service.execute(service.prepare(50L));

        assertEquals("Colores de JULIETA.", result.draft());
    }

    @Test
    void seguimientoDeColoresUsaTodosLosValoresDelBackendSinResumirConGemini() {
        CrmWhatsappAiJob job = job("Solo hay 4 colores?");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(configWithIntent("COLORES_TALLAS")));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findRecentActiveMessages(any(), any())).thenReturn(List.of(job.getMessage()));
        when(memory.rememberedProductName(10L)).thenReturn("BELINDA");
        when(provider.classify(any())).thenReturn(new ClassificationResult(
                "PRODUCTOS", 100, false, "seguimiento", List.of(), new Usage(10, 5, 15)));
        when(tools.execute(any(), any())).thenAnswer(invocation -> {
            List<ToolCall> calls = invocation.getArgument(1);
            assertEquals("BELINDA", calls.getFirst().arguments().get("q"));
            return new ExecutionResult(
                    List.of(java.util.Map.of(
                            "tool", "buscar_productos",
                            "products", List.of(java.util.Map.of(
                                    "name", "BELINDA",
                                    "availableColors", List.of("VINO", "MARRON", "PALO ROSA", "CHOCOLATE", "GUINDA", "NEGRO"),
                                    "availableSizes", List.of("XS", "S", "M", "L"))))),
                    List.of(), List.of(), List.of());
        });

        var result = service.execute(service.prepare(50L));

        assertEquals("COLORES_TALLAS", result.intent());
        assertTrue(result.draft().contains("VINO"));
        assertTrue(result.draft().contains("MARRON"));
        assertTrue(result.draft().contains("PALO ROSA"));
        assertTrue(result.draft().contains("CHOCOLATE"));
        assertTrue(result.draft().contains("GUINDA"));
        assertTrue(result.draft().contains("NEGRO"));
        assertTrue(result.draft().contains("XS, S, M, L"));
        verify(provider, org.mockito.Mockito.never()).generateDraft(any());
    }

    @Test
    void confirmaStockExactoSinRevelarLaCantidadDisponible() {
        CrmWhatsappAiJob job = job("En lacre hay S?");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(configWithIntent("STOCK")));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findRecentActiveMessages(any(), any())).thenReturn(List.of(job.getMessage()));
        when(memory.rememberedProductName(10L)).thenReturn("JULIETA");
        when(provider.classify(any())).thenReturn(new ClassificationResult(
                "COLORES_TALLAS", 100, false, "consulta de variante", List.of(), new Usage(10, 5, 15)));
        when(tools.execute(any(), any())).thenReturn(stockExecution(3));

        var result = service.execute(service.prepare(50L));

        assertEquals("STOCK", result.intent());
        assertTrue(result.draft().contains("Sí, hay stock disponible"));
        assertTrue(!result.draft().contains("3 unidades"));
        verify(provider, org.mockito.Mockito.never()).generateDraft(any());
    }

    @Test
    void consultaTallasDeUnColorListaTodasLasDisponiblesAntesDeElegir() {
        CrmWhatsappAiJob job = job("Que tallas tienes en chocolate");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(configWithIntent("COLORES_TALLAS")));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findRecentActiveMessages(any(), any())).thenReturn(List.of(job.getMessage()));
        when(memory.selectionFor(10L)).thenReturn(new MemorySelection(30, "EMMA", "", "", null));
        when(provider.classify(any())).thenReturn(new ClassificationResult(
                "COLORES_TALLAS", 100, false, "consulta de tallas por color", List.of(), new Usage(10, 5, 15)));
        java.util.Map<String, Object> product = java.util.Map.of(
                "productId", 30, "name", "EMMA",
                "variants", List.of(
                        java.util.Map.of("color", "CHOCOLATE", "size", "L", "stock", 2),
                        java.util.Map.of("color", "CHOCOLATE", "size", "M", "stock", 1),
                        java.util.Map.of("color", "CHOCOLATE", "size", "S", "stock", 3),
                        java.util.Map.of("color", "CHOCOLATE", "size", "XS", "stock", 4),
                        java.util.Map.of("color", "CHOCOLATE", "size", "XL", "stock", 0),
                        java.util.Map.of("color", "TOPO", "size", "XS", "stock", 5)));
        when(tools.execute(any(), any())).thenReturn(new ExecutionResult(
                List.of(java.util.Map.of("tool", "buscar_productos", "products", List.of(product))),
                List.of(), List.of(), List.of()));

        var result = service.execute(service.prepare(50L));

        assertTrue(result.draft().contains("Tallas disponibles"), result.draft());
        assertTrue(result.draft().contains("L, M, S, XS"), result.draft());
        assertTrue(!result.draft().contains("XL"), result.draft());
        assertTrue(result.draft().contains("Puedes elegir una o más"), result.draft());
        verify(provider, org.mockito.Mockito.never()).generateDraft(any());
    }

    @Test
    void quieroColorYQueTallasUsaElProductoRecordadoSinVolverAPreguntarlo() {
        CrmWhatsappAiJob job = job("Quiero marrón por favor que tallas tienes disponible");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L))
                .thenReturn(Optional.of(configWithIntent("COLORES_TALLAS")));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findRecentActiveMessages(any(), any())).thenReturn(List.of(job.getMessage()));
        when(memory.selectionFor(10L)).thenReturn(
                new MemorySelection(44, "Conjunto NHARA _ LISO", "", "", null));
        when(provider.classify(any())).thenReturn(new ClassificationResult(
                "INTENCION_COMPRA", 100, false, "consulta de tallas de un color",
                List.of(), new Usage(10, 5, 15)));
        java.util.Map<String, Object> product = java.util.Map.of(
                "productId", 44, "name", "Conjunto NHARA _ LISO",
                "variants", List.of(
                        java.util.Map.of("color", "MARRON", "size", "XS", "stock", 2),
                        java.util.Map.of("color", "MARRON", "size", "S", "stock", 3),
                        java.util.Map.of("color", "MARRON", "size", "M", "stock", 1),
                        java.util.Map.of("color", "MARRON", "size", "L", "stock", 0),
                        java.util.Map.of("color", "PLATA", "size", "L", "stock", 4)));
        when(tools.execute(any(), any())).thenAnswer(invocation -> {
            List<ToolCall> calls = invocation.getArgument(1);
            assertEquals("Conjunto NHARA _ LISO",
                    calls.getFirst().arguments().get("fallbackProduct"));
            return new ExecutionResult(
                    List.of(java.util.Map.of("tool", "buscar_productos", "products", List.of(product))),
                    List.of(), List.of(), List.of());
        });

        var result = service.execute(service.prepare(50L));

        assertEquals("COLORES_TALLAS", result.intent());
        assertTrue(result.draft().contains("Conjunto NHARA _ LISO"), result.draft());
        assertTrue(result.draft().contains("MARRON"), result.draft());
        assertTrue(result.draft().contains("XS, S, M"), result.draft());
        assertFalse(result.draft().contains("¿Qué producto deseas"), result.draft());
        verify(provider, org.mockito.Mockito.never()).interpretSaleAction(any());
    }

    @Test
    void stockDisponibleAdjuntaSoloLaImagenDelColorSolicitado() {
        CrmWhatsappAiJob job = job("Plata talla S");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(configWithIntent("STOCK")));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findRecentActiveMessages(any(), any())).thenReturn(List.of(job.getMessage()));
        when(memory.selectionFor(10L)).thenReturn(new MemorySelection(25, "ANNIE ENTERO", "", "", null));
        when(provider.classify(any())).thenReturn(new ClassificationResult(
                "STOCK", 100, false, "consulta de variante", List.of(), new Usage(10, 5, 15)));
        MediaReference plata = new MediaReference("PRODUCT_COLOR_IMAGE", 25, 201,
                "ANNIE ENTERO", "PLATA", "/storage/productos/annie-plata.webp", "");
        MediaReference negro = new MediaReference("PRODUCT_COLOR_IMAGE", 25, 202,
                "ANNIE ENTERO", "NEGRO", "/storage/productos/annie-negro.webp", "");
        java.util.Map<String, Object> product = java.util.Map.of(
                "productId", 25, "name", "ANNIE ENTERO",
                "variants", List.of(
                        java.util.Map.of("color", "PLATA", "size", "S", "stock", 3),
                        java.util.Map.of("color", "NEGRO", "size", "S", "stock", 4)));
        when(tools.execute(any(), any())).thenReturn(new ExecutionResult(
                List.of(java.util.Map.of("tool", "buscar_productos", "products", List.of(product))),
                List.of(), List.of(), List.of(plata, negro)));

        var result = service.execute(service.prepare(50L));

        assertTrue(result.draft().contains("stock disponible"), result.draft());
        assertEquals(List.of(plata), result.suggestedMedia());
    }

    @Test
    void solicitudDeFotoUsaElProductoRecordadoYAdjuntaLaImagenDelColor() {
        CrmWhatsappAiJob job = job("Quiero ver la foto del color beige");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(configWithIntent("PRODUCTOS")));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findRecentActiveMessages(any(), any())).thenReturn(List.of(job.getMessage()));
        when(memory.selectionFor(10L)).thenReturn(
                new MemorySelection(26, "ANNIE RAYAS", "BEIGE CLARO", "", null));
        MediaReference beige = new MediaReference("PRODUCT_COLOR_IMAGE", 26, 211,
                "ANNIE RAYAS", "BEIGE CLARO", "/storage/productos/annie-rayas-beige.webp", "");
        MediaReference vino = new MediaReference("PRODUCT_COLOR_IMAGE", 26, 212,
                "ANNIE RAYAS", "VINO", "/storage/productos/annie-rayas-vino.webp", "");
        when(tools.execute(any(), any())).thenAnswer(invocation -> {
            List<ToolCall> calls = invocation.getArgument(1);
            assertEquals("ANNIE RAYAS", calls.getFirst().arguments().get("q"));
            return new ExecutionResult(
                    List.of(java.util.Map.of("tool", "buscar_productos", "products", List.of())),
                    List.of(), List.of(), List.of(beige, vino));
        });

        var result = service.execute(service.prepare(50L));

        assertEquals("PRODUCTOS", result.intent());
        assertTrue(result.draft().contains("ANNIE RAYAS"), result.draft());
        assertTrue(result.draft().contains("BEIGE CLARO"), result.draft());
        assertEquals(List.of(beige), result.suggestedMedia());
        verify(provider, org.mockito.Mockito.never()).classify(any());
    }

    @Test
    void stockAgotadoNoAdjuntaImagenDelProducto() {
        CrmWhatsappAiJob job = job("Plata talla S");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(configWithIntent("STOCK")));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findRecentActiveMessages(any(), any())).thenReturn(List.of(job.getMessage()));
        when(memory.selectionFor(10L)).thenReturn(new MemorySelection(25, "ANNIE ENTERO", "", "", null));
        when(provider.classify(any())).thenReturn(new ClassificationResult(
                "STOCK", 100, false, "consulta de variante", List.of(), new Usage(10, 5, 15)));
        MediaReference plata = new MediaReference("PRODUCT_COLOR_IMAGE", 25, 201,
                "ANNIE ENTERO", "PLATA", "/storage/productos/annie-plata.webp", "");
        java.util.Map<String, Object> product = java.util.Map.of(
                "productId", 25, "name", "ANNIE ENTERO",
                "variants", List.of(java.util.Map.of("color", "PLATA", "size", "S", "stock", 0)));
        when(tools.execute(any(), any())).thenReturn(new ExecutionResult(
                List.of(java.util.Map.of("tool", "buscar_productos", "products", List.of(product))),
                List.of(), List.of(), List.of(plata)));

        var result = service.execute(service.prepare(50L));

        assertTrue(result.draft().contains("No hay stock"), result.draft());
        assertTrue(result.suggestedMedia().isEmpty());
    }

    @Test
    void informaStockRestanteCuandoElClientePideMasDeLoDisponible() {
        CrmWhatsappAiJob job = job("Hay 4 en lacre talla S?");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(configWithIntent("STOCK")));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findRecentActiveMessages(any(), any())).thenReturn(List.of(job.getMessage()));
        when(memory.rememberedProductName(10L)).thenReturn("JULIETA");
        when(provider.classify(any())).thenReturn(new ClassificationResult(
                "STOCK", 100, false, "consulta de cantidad", List.of(), new Usage(10, 5, 15)));
        when(tools.execute(any(), any())).thenReturn(stockExecution(3));

        var result = service.execute(service.prepare(50L));

        assertTrue(!result.requiresHuman(), result.reason() + " / " + result.draft());
        assertTrue(result.draft().contains("solo tenemos 3 unidades"), result.draft());
    }

    @Test
    void combinaTallaActualConColorRecordadoSinVolverAPreguntarElColor() {
        CrmWhatsappAiJob job = job("XS");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(configWithIntent("STOCK")));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findRecentActiveMessages(any(), any())).thenReturn(List.of(job.getMessage()));
        when(memory.selectionFor(10L)).thenReturn(new MemorySelection(7, "MELISSA", "Beige", "", null));
        when(provider.classify(any())).thenReturn(new ClassificationResult(
                "STOCK", 100, false, "continuacion de variante", List.of(), new Usage(10, 5, 15)));
        java.util.Map<String, Object> product = java.util.Map.of(
                "name", "MELISSA",
                "variants", List.of(
                        java.util.Map.of("color", "Beige", "size", "XS", "stock", 4),
                        java.util.Map.of("color", "Negro", "size", "XS", "stock", 3)));
        when(tools.execute(any(), any())).thenReturn(new ExecutionResult(
                List.of(java.util.Map.of("tool", "buscar_productos", "products", List.of(product))),
                List.of(), List.of(), List.of()));

        var result = service.execute(service.prepare(50L));

        assertTrue(result.draft().contains("Beige"), result.draft());
        assertTrue(result.draft().contains("XS"), result.draft());
        assertTrue(result.draft().contains("stock disponible"), result.draft());
        assertTrue(!result.draft().contains("QuÃ© color"), result.draft());
    }

    @Test
    void derivaCantidadMayoristaSinRevelarElInventario() {
        CrmWhatsappAiJob job = job("Hay 10 en lacre talla S?");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(configWithIntent("STOCK")));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findRecentActiveMessages(any(), any())).thenReturn(List.of(job.getMessage()));
        when(memory.rememberedProductName(10L)).thenReturn("JULIETA");
        when(provider.classify(any())).thenReturn(new ClassificationResult(
                "STOCK", 100, false, "consulta mayorista", List.of(), new Usage(10, 5, 15)));
        when(tools.execute(any(), any())).thenReturn(stockExecution(20));

        var result = service.execute(service.prepare(50L));

        assertTrue(result.requiresHuman());
        assertTrue(result.reason().contains("precio mayorista"), result.reason());
        assertTrue(!result.reason().contains("20"));
    }

    private ExecutionResult stockExecution(int stock) {
        return new ExecutionResult(
                List.of(java.util.Map.of(
                        "tool", "buscar_productos",
                        "products", List.of(java.util.Map.of(
                                "name", "JULIETA",
                                "variants", List.of(java.util.Map.of(
                                        "color", "LACRE", "size", "S", "stock", stock,
                                        "available", stock > 0)))))),
                List.of(), List.of(), List.of());
    }

    @Test
    void interpretaQuieroProductoComoIntencionDeCompraAunqueGeminiDigaProductos() {
        CrmWhatsappAiJob job = job("Quiero Melisa color beige xs");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(configWithIntent("INTENCION_COMPRA")));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findRecentActiveMessages(any(), any())).thenReturn(List.of(job.getMessage()));
        when(provider.classify(any())).thenReturn(new ClassificationResult(
                "PRODUCTOS", 100, false, "producto",
                List.of(new ToolCall("buscar_productos", java.util.Map.of("q", "Quiero Melisa color beige xs"))),
                new Usage(10, 5, 15)));
        when(tools.execute(any(), any())).thenReturn(new ExecutionResult(
                List.of(java.util.Map.of("tool", "buscar_productos", "corrected", true,
                        "interpretedProduct", "MELISSA")),
                List.of(), List.of(), List.of()));
        when(provider.interpretSaleAction(any())).thenReturn(new SaleActionResult(
                "ADD", "MELISSA", null, "BEIGE", "XS", 1, "", 100, "producto corregido", new Usage(8, 4, 12)));
        when(saleDrafts.applyAiAction(any(), any())).thenReturn(
                new CrmWhatsappAiSaleDraftService.ActionOutcome("Resumen MELISSA", false, null));

        var result = service.execute(service.prepare(50L));

        assertEquals("INTENCION_COMPRA", result.intent());
        assertEquals("🔎 Entendí que te refieres a *MELISSA*.\n\nResumen MELISSA", result.draft());
    }

    @Test
    void agregaVarianteElegidaUsandoElProductoRecordado() {
        CrmWhatsappAiJob job = job("Quiero negro y xs");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(configWithIntent("INTENCION_COMPRA")));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findRecentActiveMessages(any(), any())).thenReturn(List.of(job.getMessage()));
        when(memory.rememberedProductName(10L)).thenReturn("JULIETA");
        when(provider.classify(any())).thenReturn(new ClassificationResult(
                "COLORES_TALLAS", 100, false, "seleccion de variante",
                List.of(new ToolCall("buscar_productos", java.util.Map.of("q", "Quiero negro y xs"))),
                new Usage(10, 5, 15)));
        when(tools.execute(any(), any())).thenAnswer(invocation -> {
            List<ToolCall> calls = invocation.getArgument(1);
            assertEquals("JULIETA", calls.getFirst().arguments().get("q"));
            return new ExecutionResult(List.of(), List.of(), List.of(), List.of());
        });
        when(provider.interpretSaleAction(any())).thenReturn(new SaleActionResult(
                "ADD", "JULIETA", null, "NEGRO", "XS", 1, "", 100, "variante elegida", new Usage(8, 4, 12)));
        when(saleDrafts.applyAiAction(any(), any())).thenReturn(
                new CrmWhatsappAiSaleDraftService.ActionOutcome(
                        "Resumen de tu pedido:\n- 1 x JULIETA NEGRO talla XS - S/75.00", false, null));

        var result = service.execute(service.prepare(50L));

        assertEquals("INTENCION_COMPRA", result.intent());
        assertTrue(!result.requiresHuman());
        assertTrue(result.draft().contains("1 x JULIETA NEGRO talla XS"));
    }

    @Test
    void delegaPedidoEstructuradoSinEmitirVenta() {
        CrmWhatsappAiJob job = job("Quiero un polo azul talla M");
        CrmWhatsappAiConfig config = config();
        config.setIntencionesPermitidas("INTENCION_COMPRA");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(config));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findRecentActiveMessages(any(), any())).thenReturn(List.of(job.getMessage()));
        when(provider.classify(any())).thenReturn(new ClassificationResult(
                "INTENCION_COMPRA", 95, false, "compra", List.of(), new Usage(10, 5, 15)));
        when(tools.execute(any(), any())).thenReturn(new ExecutionResult(List.of(), List.of(), List.of(), List.of()));
        when(provider.interpretSaleAction(any())).thenReturn(new SaleActionResult(
                "ADD", "polo", null, "azul", "M", 1, "", 96, "producto identificado", new Usage(8, 4, 12)));
        when(saleDrafts.applyAiAction(any(), any())).thenReturn(
                new CrmWhatsappAiSaleDraftService.ActionOutcome("Resumen seguro", false, null));

        var result = service.execute(service.prepare(50L));

        assertEquals("Resumen seguro", result.draft());
        assertEquals(27, result.usage().totalTokens());
        verifyNoInteractions(delivery);
    }

    @Test
    void solicitaConsentimientoAntesDeTransferirAlAsesor() {
        CrmWhatsappAiJob job = job("Quiero comprar al por mayor");
        job.setTriggerType("AUTOMATIC");
        job.getConversation().setStatus("ESPERA");
        job.getConversation().setAiAttentionMode(CrmWhatsappAiAttentionMode.AUTOMATICA);
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(config()));
        when(runs.save(any())).thenAnswer(invocation -> {
            var run = (com.sistemapos.sistematextil.model.CrmWhatsappAiRun) invocation.getArgument(0);
            run.setIdAiRun(61L);
            return run;
        });
        var result = CrmWhatsappAiEngineService.ProcessingResult.human(
                "INTENCION_COMPRA", 100, "Requiere confirmar condiciones mayoristas",
                List.of(), List.of(), Usage.empty(), 20L);

        service.complete(50L, result);

        assertEquals(CrmWhatsappAiAttentionMode.AUTOMATICA, job.getConversation().getAiAttentionMode());
        verify(delivery).enqueue(argThat(run -> run.getOutcome() == com.sistemapos.sistematextil.model.CrmWhatsappAiRunOutcome.DRAFT_READY
                && run.getDraftResponse().contains("¿Te parece si te comunico con una?")));
    }

    private CrmWhatsappAiJob job(String body) {
        Empresa empresa = new Empresa();
        empresa.setIdEmpresa(1);
        Sucursal branch = new Sucursal();
        branch.setIdSucursal(3);
        branch.setNombre("Kiments Centro");
        branch.setEmpresa(empresa);
        branch.setEstado("ACTIVO");
        CrmWhatsappConnection connection = new CrmWhatsappConnection();
        connection.setIdConnection(7L);
        connection.setEmpresa(empresa);
        connection.setSucursal(branch);
        CrmWhatsappConversation conversation = new CrmWhatsappConversation();
        conversation.setIdConversation(10L);
        conversation.setConnection(connection);
        conversation.setStatus("ATENDIDO");
        CrmWhatsappMessage message = new CrmWhatsappMessage();
        message.setIdMessage(20L);
        message.setConversation(conversation);
        message.setDirection("INCOMING");
        message.setMessageType("TEXT");
        message.setBody(body);
        CrmWhatsappAiJob job = new CrmWhatsappAiJob();
        job.setIdAiJob(50L);
        job.setConversation(conversation);
        job.setMessage(message);
        job.setStatus(CrmWhatsappAiJobStatus.PROCESSING);
        job.setTriggerType("MANUAL");
        job.setAttempts(1);
        return job;
    }

    private CrmWhatsappAiJob mediaJob(String messageType, String mimeType, String fileName, String caption) {
        CrmWhatsappAiJob job = job(caption);
        job.getMessage().setMessageType(messageType);
        job.getMessage().setMediaMimeType(mimeType);
        job.getMessage().setMediaFileName(fileName);
        job.getMessage().setMediaStoragePath("crm/media/" + fileName);
        job.getMessage().setMediaDurationSeconds(30);
        return job;
    }

    private CrmWhatsappMessage incomingMessage(
            CrmWhatsappConversation conversation,
            Long id,
            String body,
            LocalDateTime createdAt) {
        CrmWhatsappMessage message = new CrmWhatsappMessage();
        message.setIdMessage(id);
        message.setConversation(conversation);
        message.setDirection("INCOMING");
        message.setMessageType("TEXT");
        message.setBody(body);
        message.setCreatedAt(createdAt);
        return message;
    }

    private CrmWhatsappAiConfig config() {
        CrmWhatsappAiConfig config = new CrmWhatsappAiConfig();
        config.setModo(CrmWhatsappAiMode.SUGERENCIAS);
        config.setZonaHoraria("America/Lima");
        config.setDiasAtencion("LUNES,MARTES,MIERCOLES,JUEVES,VIERNES,SABADO,DOMINGO");
        config.setHoraInicio(LocalTime.MIN);
        config.setHoraFin(LocalTime.MAX);
        config.setTono(CrmWhatsappAiTone.CERCANO);
        config.setIntencionesPermitidas("SALUDO,PRODUCTOS,PRECIO,STOCK");
        config.setConfianzaMinima(75);
        config.setMaxRespuestasAutomaticas(5);
        return config;
    }

    private CrmWhatsappAiConfig configWithIntent(String intent) {
        CrmWhatsappAiConfig config = config();
        config.setIntencionesPermitidas(config.getIntencionesPermitidas() + "," + intent);
        return config;
    }

    private ExecutionResult shippingKnowledge() {
        return new ExecutionResult(
                List.of(java.util.Map.of("tool", "consultar_informacion_negocio", "sources", List.of(
                        java.util.Map.of("title", "Envios", "category", "ENVIOS",
                                "content", "Realizamos envios a provincia mediante Shalom y recojo en almacen.")))),
                List.of(java.util.Map.of("tool", "consultar_informacion_negocio", "status", "OK")),
                List.of(java.util.Map.of("tool", "consultar_informacion_negocio")), List.of());
    }

    private ExecutionResult promotionExecutionForAlessia() {
        return new ExecutionResult(
                List.of(java.util.Map.of(
                        "tool", "consultar_promociones",
                        "query", "ALESSIA RAYAS",
                        "page", 0,
                        "pageSize", 10,
                        "total", 1,
                        "hasMore", false,
                        "promotions", List.of(java.util.Map.of(
                                "promotionId", 46,
                                "name", "combo 46",
                                "comboPrice", 155,
                                "products", List.of(
                                        java.util.Map.of("name", "ALICE RAYAS", "quantity", 1),
                                        java.util.Map.of("name", "ALESSIA RAYAS", "quantity", 1)))))),
                List.of(), List.of(), List.of());
    }

    private ExecutionResult promotionExecution(String query, List<java.util.Map<String, Object>> promotions) {
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("tool", "consultar_promociones");
        result.put("query", query);
        result.put("page", 0);
        result.put("pageSize", 10);
        result.put("total", promotions.size());
        result.put("hasMore", false);
        result.put("promotions", promotions);
        return new ExecutionResult(List.of(result), List.of(), List.of(), List.of());
    }

    private java.util.Map<String, Object> promotionMap(int id, String name, int comboPrice,
            int regularPrice, int savings, List<java.util.Map<String, Object>> products) {
        java.util.Map<String, Object> promotion = new java.util.LinkedHashMap<>();
        promotion.put("promotionId", id);
        promotion.put("name", name);
        promotion.put("comboPrice", comboPrice);
        promotion.put("regularPrice", regularPrice);
        promotion.put("savings", savings);
        promotion.put("products", products);
        return promotion;
    }
}
