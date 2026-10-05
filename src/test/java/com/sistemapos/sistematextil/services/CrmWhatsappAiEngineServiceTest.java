package com.sistemapos.sistematextil.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import com.sistemapos.sistematextil.model.CrmWhatsappAiConfig;
import com.sistemapos.sistematextil.model.CrmWhatsappAiAttentionMode;
import com.sistemapos.sistematextil.model.CrmWhatsappAiJob;
import com.sistemapos.sistematextil.model.CrmWhatsappAiJobStatus;
import com.sistemapos.sistematextil.model.CrmWhatsappAiMode;
import com.sistemapos.sistematextil.model.CrmWhatsappAiRun;
import com.sistemapos.sistematextil.model.CrmWhatsappAiRunOutcome;
import com.sistemapos.sistematextil.model.CrmWhatsappAiTone;
import com.sistemapos.sistematextil.model.CrmWhatsappConnection;
import com.sistemapos.sistematextil.model.CrmWhatsappConversation;
import com.sistemapos.sistematextil.model.CrmWhatsappMessage;
import com.sistemapos.sistematextil.model.Empresa;
import com.sistemapos.sistematextil.model.Sucursal;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAiConfigRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAiJobRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAiRunRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappMessageRepository;
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
import com.sistemapos.sistematextil.services.CrmWhatsappAiToolService.ExecutionResult;
import com.sistemapos.sistematextil.services.CrmWhatsappAiToolService.MediaReference;
import com.sistemapos.sistematextil.services.CrmWhatsappAiMemoryService.MemorySelection;
import com.sistemapos.sistematextil.services.CrmWhatsappAiMemoryService.PendingReplyResolution;

class CrmWhatsappAiEngineServiceTest {

    private final CrmWhatsappAiJobRepository jobs = mock(CrmWhatsappAiJobRepository.class);
    private final CrmWhatsappAiRunRepository runs = mock(CrmWhatsappAiRunRepository.class);
    private final CrmWhatsappAiConfigRepository configs = mock(CrmWhatsappAiConfigRepository.class);
    private final CrmWhatsappMessageRepository messages = mock(CrmWhatsappMessageRepository.class);
    private final CrmWhatsappEcommerceOrderParser ecommerceOrderParser = new CrmWhatsappEcommerceOrderParser();
    private final CrmWhatsappAiToolService tools = mock(CrmWhatsappAiToolService.class);
    private final AiModelProvider provider = mock(AiModelProvider.class);
    private final CrmWhatsappEventService events = mock(CrmWhatsappEventService.class);
    private final CrmWhatsappAiMemoryService memory = mock(CrmWhatsappAiMemoryService.class);
    private final CrmWhatsappAiDeliveryService delivery = mock(CrmWhatsappAiDeliveryService.class);
    private final CrmWhatsappAiSaleDraftService saleDrafts = mock(CrmWhatsappAiSaleDraftService.class);
    private final CrmWhatsappAiOperationsService operations = mock(CrmWhatsappAiOperationsService.class);
    private final CrmWhatsappAiSafetyService safety = mock(CrmWhatsappAiSafetyService.class);
    private final CrmWhatsappAiProductQueryRepository productQueries = mock(CrmWhatsappAiProductQueryRepository.class);
    private final CrmWhatsappAiAuditService audit = mock(CrmWhatsappAiAuditService.class);
    private final CrmWhatsappConversationRepository conversations = mock(CrmWhatsappConversationRepository.class);
    private final S3StorageService storage = mock(S3StorageService.class);
    private final CrmWhatsappAiEngineService service = new CrmWhatsappAiEngineService(
            jobs, runs, configs, messages, ecommerceOrderParser, tools, provider, events, memory, delivery, saleDrafts,
            operations, safety, productQueries, audit, conversations, storage);

    @BeforeEach
    void setupOperations() { when(operations.automaticAllowedForConversation(any(), any())).thenReturn(true); }

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
        when(saleDrafts.pendingPaymentEvidenceReminder(10L))
                .thenReturn("No se puede procesar tu pago con ese mensaje.");

        var result = service.execute(service.prepare(50L));

        assertEquals(CrmWhatsappAiRunOutcome.SKIPPED, result.outcome());
        assertTrue(result.reason().contains("flujo de pagos"));
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
    void transfiereCuandoElClienteSolicitaExpresamenteUnAsesor() {
        CrmWhatsappAiJob job = job("Deseo hablar con un asesor");
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
    void descartaResultadoSiElTrabajoFueEliminadoDuranteElProcesamiento() {
        when(jobs.findDetailedById(999L)).thenReturn(Optional.empty());

        service.complete(999L, CrmWhatsappAiEngineService.ProcessingResult.skip("Trabajo obsoleto", false));
        service.fail(999L, new IllegalStateException("Fallo tardio"), false);

        verifyNoInteractions(runs, delivery);
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
            return new ExecutionResult(List.of(), List.of(), List.of(), List.of());
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
        when(provider.classify(any())).thenReturn(new ClassificationResult(
                "PRODUCTOS", 98, false, "catalogo general",
                List.of(new ToolCall("buscar_productos", java.util.Map.of("q", "Que prenda tienes ?"))),
                new Usage(10, 5, 15)));
        when(tools.execute(any(), any())).thenAnswer(invocation -> {
            List<ToolCall> calls = invocation.getArgument(1);
            assertEquals("", calls.getFirst().arguments().get("q"));
            assertEquals(0, calls.getFirst().arguments().get("page"));
            return new ExecutionResult(List.of(), List.of(), List.of(), List.of());
        });
        when(provider.generateDraft(any())).thenReturn(new DraftResult(
                "Catalogo disponible.", false, "", List.of(), new Usage(12, 8, 20)));

        var result = service.execute(service.prepare(50L));

        assertEquals("Catalogo disponible.", result.draft());
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
    void modeloElegidoAdjuntaUnaSolaImagenGlobalValidada() {
        CrmWhatsappAiJob job = job("Annie entero");
        when(jobs.findDetailedById(50L)).thenReturn(Optional.of(job));
        when(configs.findByConnection_IdConnection(7L)).thenReturn(Optional.of(config()));
        when(jobs.existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(10L, 20L)).thenReturn(false);
        when(messages.findRecentActiveMessages(any(), any())).thenReturn(List.of(job.getMessage()));
        when(memory.resolvePendingReply(any(), any(), any()))
                .thenReturn(new PendingReplyResolution("PRODUCTOS", "", false, "Annie entero"));
        MediaReference global = new MediaReference("PRODUCT_GLOBAL_IMAGE", 25, null,
                "ANNIE ENTERO", "", "/storage/productos/annie.webp", "/storage/productos/annie-thumb.webp");
        java.util.Map<String, Object> product = java.util.Map.of(
                "productId", 25,
                "name", "ANNIE ENTERO",
                "availableColors", List.of("PLATA"),
                "availableSizes", List.of("S"),
                "variants", List.of(java.util.Map.of("currentPrice", "75.00", "stock", 3)));
        when(tools.execute(any(), any())).thenReturn(new ExecutionResult(
                List.of(java.util.Map.of("tool", "buscar_productos", "products", List.of(product))),
                List.of(), List.of(), List.of(global)));

        var result = service.execute(service.prepare(50L));

        assertEquals(List.of(global), result.suggestedMedia());
        verifyNoInteractions(provider);
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
            return new ExecutionResult(List.of(), List.of(), List.of(), List.of());
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
}
