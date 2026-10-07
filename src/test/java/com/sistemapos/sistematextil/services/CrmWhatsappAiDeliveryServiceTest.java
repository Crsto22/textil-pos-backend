package com.sistemapos.sistematextil.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import com.sistemapos.sistematextil.model.CrmWhatsappAiJob;
import com.sistemapos.sistematextil.model.CrmWhatsappAiRun;
import com.sistemapos.sistematextil.model.CrmWhatsappAiRunOutcome;
import com.sistemapos.sistematextil.model.CrmWhatsappAiDeliveryType;
import com.sistemapos.sistematextil.model.CrmWhatsappAiDeliveryStatus;
import com.sistemapos.sistematextil.model.CrmWhatsappAiDelivery;
import com.sistemapos.sistematextil.model.CrmWhatsappAiConfig;
import com.sistemapos.sistematextil.model.CrmWhatsappConnection;
import com.sistemapos.sistematextil.model.CrmWhatsappConversation;
import com.sistemapos.sistematextil.model.CrmWhatsappMessage;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAiConfigRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAiDeliveryRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAiMemoryRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappMessageRepository;
import com.sistemapos.sistematextil.services.CrmWhatsappAiDeliveryService.PreparedDelivery;
import com.sistemapos.sistematextil.services.CrmWhatsappChatService.MessageResponse;

class CrmWhatsappAiDeliveryServiceTest {

    private CrmWhatsappAiDeliveryRepository deliveryRepository;
    private CrmWhatsappAiConfigRepository configRepository;
    private CrmWhatsappMessageRepository messageRepository;
    private CrmWhatsappChatService chatService;
    private CrmWhatsappAiMemoryService memoryService;
    private CrmWhatsappAiHandoffService handoffService;
    private CrmWhatsappAiSaleDraftService saleDraftService;
    private CrmWhatsappAiCommercialQueryService commercialQueryService;
    private S3StorageService storageService;
    private CrmWhatsappAiDeliveryService service;

    @BeforeEach
    void setUp() {
        deliveryRepository = mock(CrmWhatsappAiDeliveryRepository.class);
        configRepository = mock(CrmWhatsappAiConfigRepository.class);
        messageRepository = mock(CrmWhatsappMessageRepository.class);
        chatService = mock(CrmWhatsappChatService.class);
        memoryService = mock(CrmWhatsappAiMemoryService.class);
        handoffService = mock(CrmWhatsappAiHandoffService.class);
        saleDraftService = mock(CrmWhatsappAiSaleDraftService.class);
        commercialQueryService = mock(CrmWhatsappAiCommercialQueryService.class);
        storageService = mock(S3StorageService.class);
        service = new CrmWhatsappAiDeliveryService(
                deliveryRepository,
                configRepository,
                mock(CrmWhatsappAiMemoryRepository.class),
                messageRepository,
                chatService,
                memoryService,
                handoffService,
                saleDraftService,
                commercialQueryService,
                mock(CrmWhatsappEventService.class),
                mock(CrmWhatsappAiOperationsService.class),
                storageService);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "PRODUCTOS", "COLORES_TALLAS", "OFERTAS", "INSTITUCIONAL",
            "MI_CUENTA", "HISTORIAL_VENTAS", "ESTADO_VENTA", "FAQ"
    })
    void creaEntregaParaConsultasComercialesAutomaticas(String intent) {
        CrmWhatsappConversation conversation = new CrmWhatsappConversation();
        conversation.setIdConversation(80L);
        CrmWhatsappMessage message = new CrmWhatsappMessage();
        message.setIdMessage(643L);
        CrmWhatsappAiJob job = new CrmWhatsappAiJob();
        job.setTriggerType("AUTOMATIC");
        CrmWhatsappAiRun run = new CrmWhatsappAiRun();
        run.setIdAiRun(58L);
        run.setJob(job);
        run.setConversation(conversation);
        run.setMessage(message);
        run.setOutcome(CrmWhatsappAiRunOutcome.DRAFT_READY);
        run.setRequiresHuman(false);
        run.setIntent(intent);
        when(deliveryRepository.findByRun_IdAiRun(58L)).thenReturn(Optional.empty());

        service.enqueue(run);

        verify(deliveryRepository).save(argThat(delivery ->
                delivery.getRun() == run
                        && delivery.getConversation() == conversation
                        && Long.valueOf(643L).equals(delivery.getSourceMessageId())
                        && "AI_RESPONSE:643".equals(delivery.getIdempotencyKey())));
    }

    @Test
    void catalogoConImagenesEncolaTresProductosAntesDeLaRespuestaNormal() {
        CrmWhatsappConnection connection = new CrmWhatsappConnection();
        connection.setIdConnection(7L);
        CrmWhatsappConversation conversation = new CrmWhatsappConversation();
        conversation.setIdConversation(80L);
        conversation.setConnection(connection);
        CrmWhatsappMessage message = new CrmWhatsappMessage();
        message.setIdMessage(644L);
        message.setBody("Muéstrame tu catálogo, por favor");
        CrmWhatsappAiJob job = new CrmWhatsappAiJob();
        job.setTriggerType("AUTOMATIC");
        CrmWhatsappAiRun run = new CrmWhatsappAiRun();
        run.setIdAiRun(59L);
        run.setJob(job);
        run.setConversation(conversation);
        run.setMessage(message);
        run.setOutcome(CrmWhatsappAiRunOutcome.DRAFT_READY);
        run.setRequiresHuman(false);
        run.setIntent("PRODUCTOS");
        run.setDraftResponse("Claro, bella. Estos son nuestros productos más recientes.");
        CrmWhatsappAiConfig config = new CrmWhatsappAiConfig();
        config.setMandarCatalogoImagenes(true);
        when(configRepository.findByConnection_IdConnection(7L)).thenReturn(Optional.of(config));
        when(deliveryRepository.findByRun_IdAiRun(59L)).thenReturn(Optional.empty());
        when(messageRepository.existsByConversation_IdConversationAndDirection(80L, "OUTGOING"))
                .thenReturn(true);
        when(commercialQueryService.latestCatalogProducts(conversation, 3)).thenReturn(List.of(
                catalogProduct(31, "ALESSIA ENTERO", "/storage/alessia.webp", true),
                catalogProduct(32, "EMMA", "/storage/emma.webp", false),
                catalogProduct(33, "BELEN", "/storage/belen.webp", false)));

        service.enqueue(run);

        ArgumentCaptor<CrmWhatsappAiDelivery> captor = ArgumentCaptor.forClass(CrmWhatsappAiDelivery.class);
        verify(deliveryRepository, times(4)).save(captor.capture());
        List<CrmWhatsappAiDelivery> deliveries = captor.getAllValues();
        assertEquals(List.of(
                CrmWhatsappAiDeliveryType.CATALOG_PRODUCT_CARD,
                CrmWhatsappAiDeliveryType.CATALOG_PRODUCT_CARD,
                CrmWhatsappAiDeliveryType.CATALOG_PRODUCT_CARD,
                CrmWhatsappAiDeliveryType.AUTOMATIC_RESPONSE),
                deliveries.stream().map(CrmWhatsappAiDelivery::getDeliveryType).toList());
        assertTrue(deliveries.get(0).getTextBody().contains("ALESSIA ENTERO"));
        assertTrue(deliveries.get(0).getTextBody().contains("Tela Aruba"));
        assertTrue(deliveries.get(0).getTextBody().contains("Preventa"));
        assertTrue(deliveries.get(0).getTextBody().contains("14 de octubre de 2026"));
        assertTrue(deliveries.get(0).getTextBody().contains("S/75.00"));
        assertTrue(deliveries.get(3).getAvailableAt().isAfter(deliveries.get(2).getAvailableAt()));
    }

    @Test
    void nuevoChatEncolaAvisoComercialSoloAntesDeLaPrimeraRespuesta() {
        CrmWhatsappConversation conversation = new CrmWhatsappConversation();
        conversation.setIdConversation(80L);
        CrmWhatsappMessage message = new CrmWhatsappMessage();
        message.setIdMessage(650L);
        CrmWhatsappAiJob job = new CrmWhatsappAiJob();
        job.setTriggerType("AUTOMATIC");
        CrmWhatsappAiRun run = new CrmWhatsappAiRun();
        run.setIdAiRun(65L);
        run.setJob(job);
        run.setConversation(conversation);
        run.setMessage(message);
        run.setOutcome(CrmWhatsappAiRunOutcome.DRAFT_READY);
        run.setRequiresHuman(false);
        run.setIntent("PRODUCTOS");
        when(deliveryRepository.findByRun_IdAiRun(65L)).thenReturn(Optional.empty());
        when(messageRepository.existsByConversation_IdConversationAndDirection(80L, "OUTGOING"))
                .thenReturn(false);
        service.enqueue(run);

        verify(deliveryRepository).save(argThat(delivery ->
                delivery.getTextBody() != null
                        && delivery.getTextBody().contains("mensajes temporales")
                        && delivery.getTextBody().contains("Shalom")
                        && delivery.getTextBody().contains("liquidación")));
    }

    @Test
    void despuesDeLaPrimeraRespuestaEncolaProductosNuevosConPreventa() {
        CrmWhatsappConnection connection = new CrmWhatsappConnection();
        connection.setIdConnection(7L);
        CrmWhatsappConversation conversation = new CrmWhatsappConversation();
        conversation.setIdConversation(80L);
        conversation.setConnection(connection);
        conversation.setStatus("ESPERA");
        CrmWhatsappAiRun run = new CrmWhatsappAiRun();
        run.setIdAiRun(70L);
        run.setIntent("SALUDO");
        run.setConversation(conversation);
        CrmWhatsappAiDelivery delivery = new CrmWhatsappAiDelivery();
        delivery.setIdAiDelivery(90L);
        delivery.setRun(run);
        delivery.setConversation(conversation);
        delivery.setDeliveryType(CrmWhatsappAiDeliveryType.AUTOMATIC_RESPONSE);
        delivery.setStatus(CrmWhatsappAiDeliveryStatus.SENDING);
        delivery.setInitialConversationResponse(true);
        CrmWhatsappMessage outgoing = new CrmWhatsappMessage();
        outgoing.setIdMessage(900L);
        CrmWhatsappAiConfig config = new CrmWhatsappAiConfig();
        config.setMostrarProductosNuevos(true);
        when(deliveryRepository.findDetailedById(90L)).thenReturn(Optional.of(delivery));
        when(messageRepository.findById(900L)).thenReturn(Optional.of(outgoing));
        when(configRepository.findByConnection_IdConnection(7L)).thenReturn(Optional.of(config));
        when(commercialQueryService.newProducts(eq(conversation), any())).thenReturn(List.of(
                new CrmWhatsappAiCommercialQueryService.NewProductResult(
                        31, "ALESSIA ENTERO", "/storage/alessia.webp", "", true,
                        LocalDate.of(2026, 10, 14), LocalDateTime.now())));
        when(deliveryRepository.findByIdempotencyKey("NEW_PRODUCT:80:31")).thenReturn(Optional.empty());

        service.complete(90L, 900L);

        verify(deliveryRepository, atLeastOnce()).save(argThat(saved ->
                saved.getDeliveryType() == CrmWhatsappAiDeliveryType.NEW_PRODUCT_ANNOUNCEMENT
                        && "NEW_PRODUCT:80:31".equals(saved.getIdempotencyKey())
                        && "/storage/alessia.webp".equals(saved.getMediaReference())
                        && saved.getTextBody().contains("Preventa")
                        && saved.getTextBody().contains("14 de octubre de 2026")));
    }

    @Test
    void despuesDelCarritoEncolaHastaUnaSugerenciaIdempotente() {
        CrmWhatsappConnection connection = new CrmWhatsappConnection();
        connection.setIdConnection(7L);
        CrmWhatsappConversation conversation = new CrmWhatsappConversation();
        conversation.setIdConversation(80L);
        conversation.setConnection(connection);
        conversation.setStatus("ESPERA");
        CrmWhatsappAiRun run = new CrmWhatsappAiRun();
        run.setIdAiRun(71L);
        run.setIntent("INTENCION_COMPRA");
        run.setConversation(conversation);
        CrmWhatsappAiDelivery delivery = new CrmWhatsappAiDelivery();
        delivery.setIdAiDelivery(91L);
        delivery.setRun(run);
        delivery.setConversation(conversation);
        delivery.setDeliveryType(CrmWhatsappAiDeliveryType.AUTOMATIC_RESPONSE);
        delivery.setStatus(CrmWhatsappAiDeliveryStatus.SENDING);
        CrmWhatsappMessage outgoing = new CrmWhatsappMessage();
        outgoing.setIdMessage(901L);
        CrmWhatsappAiConfig config = new CrmWhatsappAiConfig();
        config.setSugerirPromocionesCarrito(true);
        when(deliveryRepository.findDetailedById(91L)).thenReturn(Optional.of(delivery));
        when(messageRepository.findById(901L)).thenReturn(Optional.of(outgoing));
        when(configRepository.findByConnection_IdConnection(7L)).thenReturn(Optional.of(config));
        when(saleDraftService.promotionSuggestion(80L)).thenReturn(
                new CrmWhatsappAiSaleDraftService.PromotionSuggestion(
                        "CART_PROMOTION:15:2", "Promoción real para completar tu pedido"));
        when(deliveryRepository.findByIdempotencyKey("CART_PROMOTION:15:2")).thenReturn(Optional.empty());

        service.complete(91L, 901L);

        verify(deliveryRepository, atLeastOnce()).save(argThat(saved ->
                saved.getDeliveryType() == CrmWhatsappAiDeliveryType.CART_PROMOTION_SUGGESTION
                        && "CART_PROMOTION:15:2".equals(saved.getIdempotencyKey())
                        && saved.getTextBody().contains("Promoción real")));
    }

    @Test
    void despuesDeMostrarProductoEncolaPromocionDeDosIgualesFueraDelContexto() {
        CrmWhatsappConversation conversation = new CrmWhatsappConversation();
        conversation.setIdConversation(80L);
        conversation.setStatus("ESPERA");
        CrmWhatsappAiRun run = new CrmWhatsappAiRun();
        run.setIdAiRun(72L);
        run.setIntent("PRODUCTOS");
        run.setConversation(conversation);
        run.setSuggestedMediaJson("""
                [{"type":"SIZE_GUIDE","productId":30,"product":"EMMA","url":"/storage/emma-guia.webp"},
                 {"type":"PRODUCT_GLOBAL_IMAGE","productId":30,"product":"EMMA","url":"/storage/emma.webp"}]
                """);
        CrmWhatsappAiDelivery delivery = new CrmWhatsappAiDelivery();
        delivery.setIdAiDelivery(92L);
        delivery.setRun(run);
        delivery.setConversation(conversation);
        delivery.setSourceMessageId(650L);
        delivery.setDeliveryType(CrmWhatsappAiDeliveryType.PRODUCT_IMAGE);
        delivery.setStatus(CrmWhatsappAiDeliveryStatus.SENDING);
        CrmWhatsappMessage outgoing = new CrmWhatsappMessage();
        outgoing.setIdMessage(902L);
        var promotion = new CrmWhatsappAiCommercialQueryService.PromotionResult(
                28, "combo 28", "2 iguales", new BigDecimal("120.00"),
                new BigDecimal("130.00"), new BigDecimal("10.00"), List.of(
                        new CrmWhatsappAiCommercialQueryService.PromotionProductResult(
                                30, "EMMA", 2, "", "")));
        when(deliveryRepository.findDetailedById(92L)).thenReturn(Optional.of(delivery));
        when(messageRepository.findById(902L)).thenReturn(Optional.of(outgoing));
        when(commercialQueryService.sameProductPromotion(conversation, 30, 2)).thenReturn(promotion);
        when(deliveryRepository.findByIdempotencyKey("PRODUCT_PROMOTION:650:28"))
                .thenReturn(Optional.empty());

        service.complete(92L, 902L);

        verify(deliveryRepository, atLeastOnce()).save(argThat(saved ->
                saved.getDeliveryType() == CrmWhatsappAiDeliveryType.PRODUCT_PROMOTION_SUGGESTION
                        && "PRODUCT_PROMOTION:650:28".equals(saved.getIdempotencyKey())
                        && saved.getTextBody().contains("Lleva 2 de *EMMA*")
                        && saved.getTextBody().contains("S/120.00")
                        && saved.getTextBody().contains("Ahorras S/10.00")
                        && saved.getTextBody().contains("Lleva 2 ahora")
                        && !saved.getTextBody().contains("¿Deseas aprovechar")));
    }

    @Test
    void errorEnPromocionOpcionalNoReintentaProductoYaEnviado() {
        CrmWhatsappConversation conversation = new CrmWhatsappConversation();
        conversation.setIdConversation(80L);
        conversation.setStatus("ESPERA");
        CrmWhatsappAiRun run = new CrmWhatsappAiRun();
        run.setIdAiRun(73L);
        run.setIntent("PRODUCTOS");
        run.setConversation(conversation);
        run.setSuggestedMediaJson("""
                [{"type":"PRODUCT_GLOBAL_IMAGE","productId":59,"product":"EMMA","url":"/storage/emma.webp"}]
                """);
        CrmWhatsappAiDelivery delivery = new CrmWhatsappAiDelivery();
        delivery.setIdAiDelivery(93L);
        delivery.setRun(run);
        delivery.setConversation(conversation);
        delivery.setSourceMessageId(651L);
        delivery.setDeliveryType(CrmWhatsappAiDeliveryType.PRODUCT_IMAGE);
        delivery.setStatus(CrmWhatsappAiDeliveryStatus.SENDING);
        CrmWhatsappMessage outgoing = new CrmWhatsappMessage();
        outgoing.setIdMessage(903L);
        when(deliveryRepository.findDetailedById(93L)).thenReturn(Optional.of(delivery));
        when(messageRepository.findById(903L)).thenReturn(Optional.of(outgoing));
        when(commercialQueryService.sameProductPromotion(conversation, 59, 2))
                .thenThrow(new org.hibernate.LazyInitializationException("Sucursal sin sesion"));

        assertDoesNotThrow(() -> service.complete(93L, 903L));

        assertEquals(CrmWhatsappAiDeliveryStatus.SENT, delivery.getStatus());
        verify(deliveryRepository, atLeastOnce()).save(argThat(saved ->
                saved.getIdAiDelivery().equals(93L)
                        && saved.getStatus() == CrmWhatsappAiDeliveryStatus.SENT));
    }

    @Test
    void conversacionConRespuestaPreviaNoRepiteElAviso() {
        CrmWhatsappConversation conversation = new CrmWhatsappConversation();
        conversation.setIdConversation(80L);
        CrmWhatsappMessage message = new CrmWhatsappMessage();
        message.setIdMessage(651L);
        CrmWhatsappAiJob job = new CrmWhatsappAiJob();
        job.setTriggerType("AUTOMATIC");
        CrmWhatsappAiRun run = new CrmWhatsappAiRun();
        run.setIdAiRun(66L);
        run.setJob(job);
        run.setConversation(conversation);
        run.setMessage(message);
        run.setOutcome(CrmWhatsappAiRunOutcome.DRAFT_READY);
        run.setRequiresHuman(false);
        run.setIntent("SALUDO");
        when(deliveryRepository.findByRun_IdAiRun(66L)).thenReturn(Optional.empty());
        when(messageRepository.existsByConversation_IdConversationAndDirection(80L, "OUTGOING"))
                .thenReturn(true);

        service.enqueue(run);

        verify(deliveryRepository).save(argThat(delivery -> delivery.getTextBody() == null));
    }

    @Test
    void productoEnPreventaEncolaAvisoConFechasAntesDeLaFichaNormal() {
        CrmWhatsappConversation conversation = new CrmWhatsappConversation();
        conversation.setIdConversation(81L);
        CrmWhatsappMessage message = new CrmWhatsappMessage();
        message.setIdMessage(652L);
        CrmWhatsappAiJob job = new CrmWhatsappAiJob();
        job.setTriggerType("AUTOMATIC");
        CrmWhatsappAiRun run = new CrmWhatsappAiRun();
        run.setIdAiRun(67L);
        run.setJob(job);
        run.setConversation(conversation);
        run.setMessage(message);
        run.setOutcome(CrmWhatsappAiRunOutcome.DRAFT_READY);
        run.setRequiresHuman(false);
        run.setIntent("PRODUCTOS");
        run.setEvidenceJson("""
                [{"tool":"buscar_productos","products":[{
                  "name":"LYANA","preventa":true,"fechaEnvioPreventa":"2026-10-14"
                }]}]
                """);
        when(deliveryRepository.findByRun_IdAiRun(67L)).thenReturn(Optional.empty());

        service.enqueue(run);

        verify(deliveryRepository).save(argThat(delivery ->
                delivery.getTextBody() != null
                        && delivery.getTextBody().contains("LYANA")
                        && delivery.getTextBody().contains("PREVENTA")
                        && delivery.getTextBody().contains("14 de octubre de 2026")
                        && delivery.getTextBody().contains("15 de octubre de 2026")));
    }

    @Test
    void productoRegularNoRecibeAvisoDePreventa() {
        CrmWhatsappConversation conversation = new CrmWhatsappConversation();
        conversation.setIdConversation(81L);
        CrmWhatsappMessage message = new CrmWhatsappMessage();
        message.setIdMessage(653L);
        CrmWhatsappAiJob job = new CrmWhatsappAiJob();
        job.setTriggerType("AUTOMATIC");
        CrmWhatsappAiRun run = new CrmWhatsappAiRun();
        run.setIdAiRun(68L);
        run.setJob(job);
        run.setConversation(conversation);
        run.setMessage(message);
        run.setOutcome(CrmWhatsappAiRunOutcome.DRAFT_READY);
        run.setRequiresHuman(false);
        run.setIntent("PRODUCTOS");
        run.setEvidenceJson("""
                [{"tool":"buscar_productos","products":[{
                  "name":"LYANA","preventa":false,"fechaEnvioPreventa":null
                }]}]
                """);
        when(deliveryRepository.findByRun_IdAiRun(68L)).thenReturn(Optional.empty());
        when(messageRepository.existsByConversation_IdConversationAndDirection(81L, "OUTGOING"))
                .thenReturn(true);

        service.enqueue(run);

        verify(deliveryRepository).save(argThat(delivery -> delivery.getTextBody() == null));
    }

    @Test
    void creaAvisoUnicoCuandoLaIaTransfiereAUnAsesor() {
        CrmWhatsappConversation conversation = new CrmWhatsappConversation();
        conversation.setIdConversation(80L);
        CrmWhatsappMessage message = new CrmWhatsappMessage();
        message.setIdMessage(644L);
        CrmWhatsappAiJob job = new CrmWhatsappAiJob();
        job.setTriggerType("AUTOMATIC");
        CrmWhatsappAiRun run = new CrmWhatsappAiRun();
        run.setIdAiRun(59L);
        run.setJob(job);
        run.setConversation(conversation);
        run.setMessage(message);
        run.setOutcome(CrmWhatsappAiRunOutcome.HUMAN_REQUIRED);
        run.setRequiresHuman(true);
        when(deliveryRepository.findByRun_IdAiRun(59L)).thenReturn(Optional.empty());

        service.enqueueHandoff(run);

        verify(deliveryRepository).save(argThat(delivery ->
                delivery.getRun() == run
                        && delivery.getDeliveryType() == CrmWhatsappAiDeliveryType.HANDOFF_NOTICE
                        && Long.valueOf(644L).equals(delivery.getSourceMessageId())));
    }

    @Test
    void creaAvisoFinancieroIdempotenteSinEjecucionDeGemini() {
        CrmWhatsappConversation conversation = new CrmWhatsappConversation();
        conversation.setIdConversation(80L);
        when(deliveryRepository.findByIdempotencyKey("payment-registered:25"))
                .thenReturn(Optional.empty());

        service.enqueueNotice(conversation, CrmWhatsappAiDeliveryType.PAYMENT_REGISTERED,
                "payment-registered:25", "Comprobante registrado correctamente.");

        verify(deliveryRepository).save(argThat(delivery ->
                delivery.getRun() == null
                        && delivery.getConversation() == conversation
                        && delivery.getDeliveryType() == CrmWhatsappAiDeliveryType.PAYMENT_REGISTERED
                        && "payment-registered:25".equals(delivery.getIdempotencyKey())
                        && "Comprobante registrado correctamente.".equals(delivery.getTextBody())));
    }

    @Test
    void falloDefinitivoDeEntregaPasaLaConversacionAUnaAsesora() {
        CrmWhatsappConversation conversation = new CrmWhatsappConversation();
        conversation.setIdConversation(80L);
        CrmWhatsappAiRun run = new CrmWhatsappAiRun();
        run.setIdAiRun(59L);
        CrmWhatsappAiDelivery delivery = new CrmWhatsappAiDelivery();
        delivery.setIdAiDelivery(93L);
        delivery.setConversation(conversation);
        delivery.setRun(run);
        delivery.setDeliveryType(CrmWhatsappAiDeliveryType.AUTOMATIC_RESPONSE);
        delivery.setStatus(CrmWhatsappAiDeliveryStatus.SENDING);
        delivery.setAttempts(3);
        when(deliveryRepository.findById(93L)).thenReturn(Optional.of(delivery));

        service.fail(93L, new IllegalStateException("WhatsApp desconectado"));

        assertEquals(CrmWhatsappAiDeliveryStatus.FAILED, delivery.getStatus());
        verify(handoffService).requireAdvisorForFailure(80L, "WhatsApp desconectado", 59L);
    }

    @Test
    void creaAvisoPersistenteParaReintentarUnComprobanteInvalido() {
        CrmWhatsappConversation conversation = new CrmWhatsappConversation();
        conversation.setIdConversation(80L);
        when(deliveryRepository.findByIdempotencyKey("payment-retry:26"))
                .thenReturn(Optional.empty());

        service.enqueueNotice(conversation, CrmWhatsappAiDeliveryType.PAYMENT_RETRY,
                "payment-retry:26", "No se puede procesar tu pago. Intenta otra vez.");

        verify(deliveryRepository).save(argThat(delivery ->
                delivery.getRun() == null
                        && delivery.getDeliveryType() == CrmWhatsappAiDeliveryType.PAYMENT_RETRY
                        && delivery.getStatus() == CrmWhatsappAiDeliveryStatus.PENDING
                        && "payment-retry:26".equals(delivery.getIdempotencyKey())));
    }

    @Test
    void creaEntregaDeImagenParaGuiaDeTallasValidada() {
        CrmWhatsappConversation conversation = new CrmWhatsappConversation();
        conversation.setIdConversation(80L);
        CrmWhatsappMessage message = new CrmWhatsappMessage();
        message.setIdMessage(645L);
        CrmWhatsappAiJob job = new CrmWhatsappAiJob();
        job.setTriggerType("AUTOMATIC");
        CrmWhatsappAiRun run = new CrmWhatsappAiRun();
        run.setIdAiRun(60L);
        run.setJob(job);
        run.setConversation(conversation);
        run.setMessage(message);
        run.setOutcome(CrmWhatsappAiRunOutcome.DRAFT_READY);
        run.setRequiresHuman(false);
        run.setIntent("GUIA_TALLAS");
        run.setDraftResponse("Guia de tallas de JULIETA");
        run.setSuggestedMediaJson("[{\"type\":\"SIZE_GUIDE\",\"url\":\"/storage/productos/10/guia.webp\"}]");
        when(deliveryRepository.findByRun_IdAiRun(60L)).thenReturn(Optional.empty());

        service.enqueue(run);

        verify(deliveryRepository).save(argThat(delivery ->
                delivery.getDeliveryType() == CrmWhatsappAiDeliveryType.SIZE_GUIDE_IMAGE
                        && "/storage/productos/10/guia.webp".equals(delivery.getMediaReference())
                        && "image/webp".equals(delivery.getMediaMimeType())));
    }

    @Test
    void creaEntregaDeUnaImagenValidadaDelProducto() {
        CrmWhatsappConversation conversation = new CrmWhatsappConversation();
        conversation.setIdConversation(80L);
        CrmWhatsappMessage message = new CrmWhatsappMessage();
        message.setIdMessage(646L);
        CrmWhatsappAiJob job = new CrmWhatsappAiJob();
        job.setTriggerType("AUTOMATIC");
        CrmWhatsappAiRun run = new CrmWhatsappAiRun();
        run.setIdAiRun(61L);
        run.setJob(job);
        run.setConversation(conversation);
        run.setMessage(message);
        run.setOutcome(CrmWhatsappAiRunOutcome.DRAFT_READY);
        run.setRequiresHuman(false);
        run.setIntent("STOCK");
        run.setDraftResponse("Si, hay stock disponible.");
        run.setSuggestedMediaJson(
                "[{\"type\":\"PRODUCT_COLOR_IMAGE\",\"url\":\"/storage/productos/annie-plata.webp\"}]");
        when(deliveryRepository.findByRun_IdAiRun(61L)).thenReturn(Optional.empty());

        service.enqueue(run);

        verify(deliveryRepository).save(argThat(delivery ->
                delivery.getDeliveryType() == CrmWhatsappAiDeliveryType.PRODUCT_IMAGE
                        && "/storage/productos/annie-plata.webp".equals(delivery.getMediaReference())
                        && "image/webp".equals(delivery.getMediaMimeType())));
    }

    @Test
    void creaEntregaOrdenadaConGuiaYLuegoImagenGlobalDelMismoProducto() {
        CrmWhatsappConversation conversation = new CrmWhatsappConversation();
        conversation.setIdConversation(80L);
        CrmWhatsappMessage message = new CrmWhatsappMessage();
        message.setIdMessage(647L);
        CrmWhatsappAiJob job = new CrmWhatsappAiJob();
        job.setTriggerType("AUTOMATIC");
        CrmWhatsappAiRun run = new CrmWhatsappAiRun();
        run.setIdAiRun(62L);
        run.setJob(job);
        run.setConversation(conversation);
        run.setMessage(message);
        run.setOutcome(CrmWhatsappAiRunOutcome.DRAFT_READY);
        run.setRequiresHuman(false);
        run.setIntent("PRODUCTOS");
        run.setDraftResponse("Modelo BELEN: precio, colores, tallas y stock.");
        run.setSuggestedMediaJson("""
                [{"type":"SIZE_GUIDE","productId":30,"product":"BELEN","url":"/storage/productos/belen-guia.webp"},
                 {"type":"PRODUCT_GLOBAL_IMAGE","productId":30,"product":"BELEN","url":"/storage/productos/belen.webp"}]
                """);
        when(deliveryRepository.findByRun_IdAiRun(62L)).thenReturn(Optional.empty());

        service.enqueue(run);

        verify(deliveryRepository).save(argThat(delivery ->
                delivery.getDeliveryType() == CrmWhatsappAiDeliveryType.PRODUCT_IMAGE
                        && "/storage/productos/belen-guia.webp".equals(delivery.getMediaReference())
                        && "/storage/productos/belen.webp".equals(delivery.getSecondaryMediaReference())
                        && "Guía de tallas de BELEN".equals(delivery.getMediaCaption())));
    }

    @Test
    void enviaGuiaSinTextoYLuegoImagenGlobalConDatosDelProducto() {
        CrmWhatsappConversation conversation = new CrmWhatsappConversation();
        conversation.setIdConversation(80L);
        CrmWhatsappAiDelivery delivery = sendingDelivery(70L, conversation);
        when(storageService.readBytes("/storage/belen-guia.webp")).thenReturn(new byte[] {1});
        when(storageService.readBytes("/storage/belen.webp")).thenReturn(new byte[] {2});
        when(chatService.enviarMediaAutomatico(eq(80L), any(byte[].class), eq("belen-guia.webp"), eq("image/webp"), eq("Guía de tallas de BELEN")))
                .thenReturn(messageResponse(901L));
        when(chatService.enviarMediaAutomatico(eq(80L), any(byte[].class), eq("belen.webp"), eq("image/webp"), eq("Datos BELEN")))
                .thenReturn(messageResponse(902L));
        when(deliveryRepository.findById(70L)).thenReturn(Optional.of(delivery));
        when(deliveryRepository.findDetailedById(70L)).thenReturn(Optional.of(delivery));

        service.send(new PreparedDelivery(70L, 80L, "Datos BELEN", "",
                false, true, false, false,
                "/storage/belen-guia.webp", "image/webp", "belen-guia.webp",
                "/storage/belen.webp", "image/webp", "belen.webp", null, null, "",
                "Guía de tallas de BELEN"));

        var order = inOrder(chatService);
        order.verify(chatService).enviarMediaAutomatico(eq(80L), any(byte[].class),
                eq("belen-guia.webp"), eq("image/webp"), eq("Guía de tallas de BELEN"));
        order.verify(chatService).enviarMediaAutomatico(eq(80L), any(byte[].class),
                eq("belen.webp"), eq("image/webp"), eq("Datos BELEN"));
        verify(deliveryRepository, atLeastOnce()).save(argThat(saved -> Long.valueOf(901L)
                .equals(saved.getGuideOutgoingMessageId())));
    }

    @Test
    void guiaSolicitadaSeEnviaUnaSolaVezConSuCaption() {
        CrmWhatsappConversation conversation = new CrmWhatsappConversation();
        conversation.setIdConversation(80L);
        CrmWhatsappAiDelivery delivery = sendingDelivery(73L, conversation);
        when(storageService.readBytes("/storage/alessia-guia.webp")).thenReturn(new byte[] {1});
        when(chatService.enviarMediaAutomatico(eq(80L), any(byte[].class), eq("alessia-guia.webp"),
                eq("image/webp"), eq("Guía de tallas de ALESSIA ENTERO")))
                .thenReturn(messageResponse(906L));
        when(deliveryRepository.findDetailedById(73L)).thenReturn(Optional.of(delivery));

        service.send(new PreparedDelivery(73L, 80L, "Guía de tallas de ALESSIA ENTERO", "",
                false, true, false, false,
                "/storage/alessia-guia.webp", "image/webp", "alessia-guia.webp",
                "", "", "", null, null, "",
                "Guía de tallas de ALESSIA ENTERO"));

        verify(chatService).enviarMediaAutomatico(eq(80L), any(byte[].class), eq("alessia-guia.webp"),
                eq("image/webp"), eq("Guía de tallas de ALESSIA ENTERO"));
        verify(chatService, never()).enviarMensajeAutomatico(eq(80L), any());
    }

    @Test
    void reintentoNoVuelveAEnviarLaGuiaYaRegistrada() {
        CrmWhatsappConversation conversation = new CrmWhatsappConversation();
        conversation.setIdConversation(80L);
        CrmWhatsappAiDelivery delivery = sendingDelivery(71L, conversation);
        delivery.setGuideOutgoingMessageId(901L);
        when(storageService.readBytes("/storage/belen.webp")).thenReturn(new byte[] {2});
        when(chatService.enviarMediaAutomatico(eq(80L), any(byte[].class), eq("belen.webp"), eq("image/webp"), eq("Datos BELEN")))
                .thenReturn(messageResponse(903L));
        when(deliveryRepository.findDetailedById(71L)).thenReturn(Optional.of(delivery));

        service.send(new PreparedDelivery(71L, 80L, "Datos BELEN", "",
                false, true, false, false,
                "/storage/belen-guia.webp", "image/webp", "belen-guia.webp",
                "/storage/belen.webp", "image/webp", "belen.webp", 901L, null, "",
                "Guía de tallas de BELEN"));

        verify(storageService, never()).readBytes("/storage/belen-guia.webp");
        verify(chatService, never()).enviarMediaAutomatico(eq(80L), any(),
                eq("belen-guia.webp"), eq("image/webp"), eq(""));
        verify(chatService).enviarMediaAutomatico(eq(80L), any(byte[].class),
                eq("belen.webp"), eq("image/webp"), eq("Datos BELEN"));
    }

    @Test
    void enviaAvisoYSoloDespuesElSaludo() {
        CrmWhatsappConversation conversation = new CrmWhatsappConversation();
        conversation.setIdConversation(80L);
        CrmWhatsappAiDelivery delivery = sendingDelivery(72L, conversation);
        when(chatService.enviarMensajeAutomatico(80L, "Aviso de compra"))
                .thenReturn(messageResponse(904L));
        when(chatService.enviarMensajeAutomatico(80L, "Hola bella"))
                .thenReturn(messageResponse(905L));
        when(deliveryRepository.findById(72L)).thenReturn(Optional.of(delivery));
        when(deliveryRepository.findDetailedById(72L)).thenReturn(Optional.of(delivery));

        service.send(new PreparedDelivery(72L, 80L, "Hola bella", "",
                false, false, false, false,
                "", "", "", "", "", "", null, null, "Aviso de compra", ""));

        var order = inOrder(chatService);
        order.verify(chatService).enviarMensajeAutomatico(80L, "Aviso de compra");
        order.verify(chatService).enviarMensajeAutomatico(80L, "Hola bella");
        verify(deliveryRepository, atLeastOnce()).save(argThat(saved -> Long.valueOf(904L)
                .equals(saved.getPreludeOutgoingMessageId())));
    }

    @Test
    void reintentoDelSaludoNoRepiteElAvisoYaEnviado() {
        CrmWhatsappConversation conversation = new CrmWhatsappConversation();
        conversation.setIdConversation(80L);
        CrmWhatsappAiDelivery delivery = sendingDelivery(73L, conversation);
        delivery.setPreludeOutgoingMessageId(904L);
        when(chatService.enviarMensajeAutomatico(80L, "Hola bella"))
                .thenReturn(messageResponse(906L));
        when(deliveryRepository.findDetailedById(73L)).thenReturn(Optional.of(delivery));

        service.send(new PreparedDelivery(73L, 80L, "Hola bella", "",
                false, false, false, false,
                "", "", "", "", "", "", null, 904L, "Aviso de compra", ""));

        verify(chatService, never()).enviarMensajeAutomatico(80L, "Aviso de compra");
        verify(chatService).enviarMensajeAutomatico(80L, "Hola bella");
    }

    @Test
    void cancelaLaSegundaEntregaCuandoElMensajeAutomaticoYaFueRegistrado() {
        CrmWhatsappConversation conversation = new CrmWhatsappConversation();
        conversation.setIdConversation(80L);
        CrmWhatsappAiDelivery delivery = sendingDelivery(74L, conversation);
        when(deliveryRepository.findDetailedById(74L)).thenReturn(Optional.of(delivery));
        when(deliveryRepository.existsByOutgoingMessage_IdMessage(907L)).thenReturn(true);

        service.complete(74L, 907L);

        verify(deliveryRepository).save(argThat(saved ->
                saved.getStatus() == CrmWhatsappAiDeliveryStatus.CANCELLED
                        && saved.getFailureReason().contains("identica ya enviada")));
        verify(messageRepository, never()).findById(907L);
        verify(memoryService, never()).markAutomaticSent(any(), any());
    }

    private CrmWhatsappAiCommercialQueryService.ProductResult catalogProduct(
            int id, String name, String image, boolean preorder) {
        var variant = new CrmWhatsappAiCommercialQueryService.VariantResult(
                id, "SKU-" + id, "", "MARRON", "M", 5, true,
                new BigDecimal("80.00"), new BigDecimal("75.00"), null, null, "", "", "");
        return new CrmWhatsappAiCommercialQueryService.ProductResult(
                id, name, "CONJUNTOS", name.toLowerCase(), "", preorder,
                preorder ? LocalDate.of(2026, 10, 14) : null,
                image, "", "", "", List.of("MARRON"), List.of("M"), List.of(variant), "Tela Aruba");
    }

    private CrmWhatsappAiDelivery sendingDelivery(Long id, CrmWhatsappConversation conversation) {
        CrmWhatsappAiDelivery delivery = new CrmWhatsappAiDelivery();
        delivery.setIdAiDelivery(id);
        delivery.setConversation(conversation);
        delivery.setDeliveryType(CrmWhatsappAiDeliveryType.PRODUCT_IMAGE);
        delivery.setStatus(CrmWhatsappAiDeliveryStatus.SENDING);
        delivery.setAttempts(1);
        delivery.setAvailableAt(LocalDateTime.now());
        return delivery;
    }

    private MessageResponse messageResponse(Long id) {
        return new MessageResponse(id, "OUTGOING", "AI_AUTOMATIC", "IMAGE", "", "sent",
                LocalDateTime.now(), "image/webp", "image.webp", "/storage/image.webp",
                "/media/image.webp", null, false);
    }
}
