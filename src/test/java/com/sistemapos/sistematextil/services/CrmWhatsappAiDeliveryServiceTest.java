package com.sistemapos.sistematextil.services;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.sistemapos.sistematextil.model.CrmWhatsappAiJob;
import com.sistemapos.sistematextil.model.CrmWhatsappAiRun;
import com.sistemapos.sistematextil.model.CrmWhatsappAiRunOutcome;
import com.sistemapos.sistematextil.model.CrmWhatsappAiDeliveryType;
import com.sistemapos.sistematextil.model.CrmWhatsappAiDeliveryStatus;
import com.sistemapos.sistematextil.model.CrmWhatsappConversation;
import com.sistemapos.sistematextil.model.CrmWhatsappMessage;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAiConfigRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAiDeliveryRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAiMemoryRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappMessageRepository;

class CrmWhatsappAiDeliveryServiceTest {

    private CrmWhatsappAiDeliveryRepository deliveryRepository;
    private CrmWhatsappAiDeliveryService service;

    @BeforeEach
    void setUp() {
        deliveryRepository = mock(CrmWhatsappAiDeliveryRepository.class);
        service = new CrmWhatsappAiDeliveryService(
                deliveryRepository,
                mock(CrmWhatsappAiConfigRepository.class),
                mock(CrmWhatsappAiMemoryRepository.class),
                mock(CrmWhatsappMessageRepository.class),
                mock(CrmWhatsappChatService.class),
                mock(CrmWhatsappAiMemoryService.class),
                mock(CrmWhatsappAiSaleDraftService.class),
                mock(CrmWhatsappEventService.class),
                mock(CrmWhatsappAiOperationsService.class),
                mock(S3StorageService.class));
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
                        && Long.valueOf(643L).equals(delivery.getSourceMessageId())));
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
}
