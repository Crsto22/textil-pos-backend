package com.sistemapos.sistematextil.services.agent.runtime;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import com.sistemapos.sistematextil.model.CrmWhatsappAgentSession;
import com.sistemapos.sistematextil.model.CrmWhatsappAgentState;
import com.sistemapos.sistematextil.model.CrmWhatsappConversation;
import com.sistemapos.sistematextil.model.CrmWhatsappMessage;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAgentSessionRepository;
import com.sistemapos.sistematextil.services.agent.CrmWhatsappAgentStateMachine;
import com.sistemapos.sistematextil.services.agent.ai.AgentMediaRequest;
import com.sistemapos.sistematextil.services.agent.ai.AgentMediaRequest.MediaType;
import com.sistemapos.sistematextil.services.agent.batch.AgentJobStore.GeneratedTurn;
import com.sistemapos.sistematextil.services.agent.batch.AgentJobStore.JobLease;
import com.sistemapos.sistematextil.services.agent.catalog.AgentCatalogQueryService;
import com.sistemapos.sistematextil.services.agent.catalog.AgentCatalogQueryService.ProductDetail;
import com.sistemapos.sistematextil.services.agent.catalog.AgentCatalogQueryService.ProductImage;
import com.sistemapos.sistematextil.services.agent.catalog.AgentCatalogQueryService.ProductImageResult;
import com.sistemapos.sistematextil.services.agent.config.AgentRuntimeConfigService;
import com.sistemapos.sistematextil.services.agent.outbox.AgentOutboxService;

class AgentTurnFinalizerTest {
    @Test
    void resumesAnOldHelpSessionWhenCustomerReturnsToAutomaticSales() {
        AgentOutboxService outbox=mock(AgentOutboxService.class);
        CrmWhatsappAgentSessionRepository sessions=mock(CrmWhatsappAgentSessionRepository.class);
        CrmWhatsappAgentSession session=new CrmWhatsappAgentSession();session.setIdAgentSession(8L);
        session.setActiveSlot(true);session.setState(CrmWhatsappAgentState.REQUIERE_AYUDA);
        session.setResumeState(CrmWhatsappAgentState.EXPLORANDO_PRODUCTOS);
        CrmWhatsappConversation conversation=new CrmWhatsappConversation();conversation.setIdConversation(20L);
        session.setConversation(conversation);
        CrmWhatsappMessage anchor=new CrmWhatsappMessage();anchor.setIdMessage(99L);session.setLastCustomerMessage(anchor);
        when(sessions.findForUpdate(8L)).thenReturn(java.util.Optional.of(session));
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(),eq(Long.class),eq(20L),anyString(),eq(20L),anyString())).thenReturn(1L);
        AgentTurnFinalizer finalizer=new AgentTurnFinalizer(outbox,mock(AgentCatalogQueryService.class),sessions,
                mock(CrmWhatsappAgentStateMachine.class),mock(AgentRuntimeConfigService.class),jdbc);
        JobLease lease=new JobLease(41L,8L,99L,1,"worker:lease",LocalDateTime.now());
        GeneratedTurn turn=new GeneratedTurn("Claro, ¿que producto buscas?","Nueva compra",false,
                "GEMINI","gemini",10,0,"fp",List.of());

        finalizer.finalizeTurn(lease,turn);

        org.junit.jupiter.api.Assertions.assertEquals(CrmWhatsappAgentState.EXPLORANDO_PRODUCTOS,session.getState());
        org.junit.jupiter.api.Assertions.assertEquals(null,session.getResumeState());
        verify(outbox).enqueueText(8L,41L,99L,0,"Claro, ¿que producto buscas?");
    }

    @Test
    void appendsAdvisorPhoneToEveryHumanHandoff() {
        AgentOutboxService outbox=mock(AgentOutboxService.class);CrmWhatsappAgentSessionRepository sessions=mock(CrmWhatsappAgentSessionRepository.class);
        AgentRuntimeConfigService runtime=mock(AgentRuntimeConfigService.class);
        CrmWhatsappAgentSession session=new CrmWhatsappAgentSession();session.setIdAgentSession(8L);session.setActiveSlot(true);
        CrmWhatsappConversation conversation=new CrmWhatsappConversation();conversation.setIdConversation(20L);session.setConversation(conversation);
        CrmWhatsappMessage anchor=new CrmWhatsappMessage();anchor.setIdMessage(99L);session.setLastCustomerMessage(anchor);
        when(sessions.findForUpdate(8L)).thenReturn(java.util.Optional.of(session));
        JdbcTemplate jdbc=mock(JdbcTemplate.class);when(jdbc.queryForObject(anyString(),eq(Long.class),eq(20L),eq(99L),eq(20L))).thenReturn(1L);
        when(runtime.forSession(8L)).thenReturn(new AgentRuntimeConfigService.RuntimeConfig(false,
                com.sistemapos.sistematextil.model.CrmWhatsappAgentActivationStage.AUTOMATIC_SALES,
                java.util.Set.of(),100,5000,240,85,true,3,5,15,12,6));
        AgentTurnFinalizer finalizer=new AgentTurnFinalizer(outbox,mock(AgentCatalogQueryService.class),sessions,
                mock(CrmWhatsappAgentStateMachine.class),runtime,jdbc);
        JobLease lease=new JobLease(41L,8L,99L,1,"worker:lease",LocalDateTime.now());
        GeneratedTurn turn=new GeneratedTurn("Una asesora revisará el caso.","Resumen",true,"GEMINI","gemini",10,0,"fp",List.of());

        finalizer.finalizeTurn(lease,turn);

        verify(outbox).enqueueText(8L,41L,99L,0,"Una asesora revisará el caso.\n\n"+AgentTurnFinalizer.ADVISOR_CONTACT);
    }

    @Test
    void ignoresModelHandoffAndKeepsAgentActiveWhenAdvisorAttentionIsDisabled() {
        AgentOutboxService outbox=mock(AgentOutboxService.class);
        CrmWhatsappAgentSessionRepository sessions=mock(CrmWhatsappAgentSessionRepository.class);
        AgentRuntimeConfigService runtime=mock(AgentRuntimeConfigService.class);
        CrmWhatsappAgentSession session=new CrmWhatsappAgentSession();
        session.setIdAgentSession(8L);session.setActiveSlot(true);
        session.setState(CrmWhatsappAgentState.EXPLORANDO_PRODUCTOS);
        CrmWhatsappConversation conversation=new CrmWhatsappConversation();conversation.setIdConversation(20L);
        session.setConversation(conversation);
        CrmWhatsappMessage anchor=new CrmWhatsappMessage();anchor.setIdMessage(99L);session.setLastCustomerMessage(anchor);
        when(sessions.findForUpdate(8L)).thenReturn(java.util.Optional.of(session));
        when(runtime.forSession(8L)).thenReturn(new AgentRuntimeConfigService.RuntimeConfig(false,
                com.sistemapos.sistematextil.model.CrmWhatsappAgentActivationStage.AUTOMATIC_SALES,
                java.util.Set.of(),100,5000,240,85,false,3,5,15,12,6));
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(),eq(Long.class),eq(20L),eq(99L),eq(20L))).thenReturn(1L);
        AgentTurnFinalizer finalizer=new AgentTurnFinalizer(outbox,mock(AgentCatalogQueryService.class),sessions,
                mock(CrmWhatsappAgentStateMachine.class),runtime,jdbc);
        JobLease lease=new JobLease(41L,8L,99L,1,"worker:lease",LocalDateTime.now());
        GeneratedTurn turn=new GeneratedTurn("Puedes comunicarte al 936310946.","Resumen",true,
                "GEMINI","gemini",10,0,"fp",List.of());

        finalizer.finalizeTurn(lease,turn);

        org.junit.jupiter.api.Assertions.assertEquals(CrmWhatsappAgentState.EXPLORANDO_PRODUCTOS,session.getState());
        verify(outbox).enqueueText(8L,41L,99L,0,"Puedes comunicarte al 936310946.");
        verify(jdbc, never()).update(org.mockito.ArgumentMatchers.contains("crm_whatsapp_support_case"),
                any(Object[].class));
    }

    @Test
    void queuesConfiguredWelcomeOnlyForTheFirstMessageOfANewConversation() {
        AgentOutboxService outbox = mock(AgentOutboxService.class);
        CrmWhatsappAgentSessionRepository sessions = mock(CrmWhatsappAgentSessionRepository.class);
        AgentRuntimeConfigService runtime = mock(AgentRuntimeConfigService.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        CrmWhatsappAgentSession session = new CrmWhatsappAgentSession();
        session.setIdAgentSession(8L);
        session.setActiveSlot(true);
        CrmWhatsappConversation conversation = new CrmWhatsappConversation();
        conversation.setIdConversation(20L);
        session.setConversation(conversation);
        CrmWhatsappMessage anchor = new CrmWhatsappMessage();
        anchor.setIdMessage(99L);
        session.setLastCustomerMessage(anchor);
        when(sessions.findForUpdate(8L)).thenReturn(java.util.Optional.of(session));
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq(20L), eq(99L), eq(20L))).thenReturn(0L);
        when(runtime.forSession(8L)).thenReturn(new AgentRuntimeConfigService.RuntimeConfig(false,
                com.sistemapos.sistematextil.model.CrmWhatsappAgentActivationStage.AUTOMATIC_SALES,
                java.util.Set.of(), 100, 5000, 240, 85, false, 3, 5, 15, 12, 6, "Aviso inicial"));
        AgentTurnFinalizer finalizer = new AgentTurnFinalizer(outbox, mock(AgentCatalogQueryService.class), sessions,
                mock(CrmWhatsappAgentStateMachine.class), runtime, jdbc);
        JobLease lease = new JobLease(41L, 8L, 99L, 1, "worker:lease", LocalDateTime.now());
        GeneratedTurn turn = new GeneratedTurn("Respuesta natural", "Resumen", false,
                "GEMINI", "gemini", 10, 1, "fingerprint", List.of());

        finalizer.finalizeTurn(lease, turn);

        var order = inOrder(outbox);
        order.verify(outbox).enqueueOperationalText(8L, 41L, 99L, 0, "Aviso inicial");
        order.verify(outbox).enqueueText(8L, 41L, 99L, 1, "Respuesta natural");
    }

    @Test
    void doesNotSendWelcomeWhenTheConversationAlreadyHasHistoryEvenIfTextChanged() {
        AgentOutboxService outbox = mock(AgentOutboxService.class);
        CrmWhatsappAgentSessionRepository sessions = mock(CrmWhatsappAgentSessionRepository.class);
        AgentRuntimeConfigService runtime = mock(AgentRuntimeConfigService.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        CrmWhatsappAgentSession session = new CrmWhatsappAgentSession();
        session.setIdAgentSession(8L);
        session.setActiveSlot(true);
        CrmWhatsappConversation conversation = new CrmWhatsappConversation();
        conversation.setIdConversation(20L);
        session.setConversation(conversation);
        CrmWhatsappMessage anchor = new CrmWhatsappMessage();
        anchor.setIdMessage(101L);
        session.setLastCustomerMessage(anchor);
        when(sessions.findForUpdate(8L)).thenReturn(java.util.Optional.of(session));
        when(runtime.forSession(8L)).thenReturn(new AgentRuntimeConfigService.RuntimeConfig(false,
                com.sistemapos.sistematextil.model.CrmWhatsappAgentActivationStage.AUTOMATIC_SALES,
                java.util.Set.of(), 100, 5000, 240, 85, false, 3, 5, 15, 12, 6, "Aviso inicial"));
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq(20L), eq(101L), eq(20L))).thenReturn(7L);
        AgentTurnFinalizer finalizer = new AgentTurnFinalizer(outbox, mock(AgentCatalogQueryService.class), sessions,
                mock(CrmWhatsappAgentStateMachine.class), runtime, jdbc);
        JobLease lease = new JobLease(42L, 8L, 101L, 1, "worker:lease", LocalDateTime.now());
        GeneratedTurn turn = new GeneratedTurn("Segunda respuesta", "Resumen", false,
                "GEMINI", "gemini", 10, 1, "fingerprint", List.of());

        finalizer.finalizeTurn(lease, turn);

        verify(outbox, never()).enqueueOperationalText(any(), any(), any(), any(Integer.class), anyString());
        verify(outbox).enqueueText(8L, 42L, 101L, 0, "Segunda respuesta");
    }

    @Test
    void validatesAndQueuesSizeGuideProductImageThenText() {
        AgentOutboxService outbox = mock(AgentOutboxService.class);
        AgentCatalogQueryService catalog = mock(AgentCatalogQueryService.class);
        CrmWhatsappAgentSessionRepository sessions = mock(CrmWhatsappAgentSessionRepository.class);
        CrmWhatsappAgentSession session = new CrmWhatsappAgentSession();
        session.setIdAgentSession(8L);
        session.setActiveSlot(true);
        CrmWhatsappMessage anchor = new CrmWhatsappMessage();
        anchor.setIdMessage(99L);
        session.setLastCustomerMessage(anchor);
        when(sessions.findForUpdate(8L)).thenReturn(java.util.Optional.of(session));
        when(catalog.getProduct(8L, 50)).thenReturn(new ProductDetail(50, "EMMA", "ARUBA", "CONJUNTO",
                false, null, "/storage/guias/emma.webp", null, List.of(), List.of(), 5, 65D, 1));
        when(catalog.getProductImage(8L, 50, "CAMEL")).thenReturn(new ProductImageResult(50, "EMMA", "CAMEL",
                "/storage/productos/emma.webp", null,
                List.of(new ProductImage("CAMEL", "/storage/productos/emma-camel.webp", null, true))));
        AgentTurnFinalizer finalizer = new AgentTurnFinalizer(outbox, catalog, sessions,
                mock(CrmWhatsappAgentStateMachine.class), mock(AgentRuntimeConfigService.class),
                mock(JdbcTemplate.class));
        JobLease lease = new JobLease(41L, 8L, 99L, 1, "worker:lease", LocalDateTime.now());
        GeneratedTurn turn = new GeneratedTurn("¿Qué talla deseas?", "Consulta EMMA", false,
                "GEMINI", "gemini", 10, 2, "fingerprint", List.of(
                        new AgentMediaRequest(MediaType.SIZE_GUIDE, 50, null),
                        new AgentMediaRequest(MediaType.PRODUCT_IMAGE, 50, "CAMEL")));

        finalizer.finalizeTurn(lease, turn);

        var order = inOrder(outbox);
        order.verify(outbox).enqueueImage(8L, 41L, 99L, 0, "/storage/guias/emma.webp",
                "emma.webp", "image/webp", "Guía de tallas — EMMA");
        order.verify(outbox).enqueueImage(8L, 41L, 99L, 1, "/storage/productos/emma-camel.webp",
                "emma-camel.webp", "image/webp", "EMMA — CAMEL");
        order.verify(outbox).enqueueText(8L, 41L, 99L, 2, "¿Qué talla deseas?");
    }

    @Test
    void queuesAnEffectiveProductImageOnlyOnceWhenRequestsResolveToTheSameSource() {
        AgentOutboxService outbox = mock(AgentOutboxService.class);
        AgentCatalogQueryService catalog = mock(AgentCatalogQueryService.class);
        CrmWhatsappAgentSessionRepository sessions = mock(CrmWhatsappAgentSessionRepository.class);
        CrmWhatsappAgentSession session = new CrmWhatsappAgentSession();
        session.setIdAgentSession(8L);
        session.setActiveSlot(true);
        CrmWhatsappMessage anchor = new CrmWhatsappMessage();
        anchor.setIdMessage(99L);
        session.setLastCustomerMessage(anchor);
        when(sessions.findForUpdate(8L)).thenReturn(java.util.Optional.of(session));
        ProductImage shared = new ProductImage("AZUL", "/storage/productos/alessia-azul.webp", null, true);
        when(catalog.getProductImage(8L, 77, "")).thenReturn(new ProductImageResult(77, "ALESSIA RAYAS", "",
                "/storage/productos/alessia.webp", null, List.of(shared)));
        when(catalog.getProductImage(8L, 77, "AZUL")).thenReturn(new ProductImageResult(77, "ALESSIA RAYAS", "AZUL",
                "/storage/productos/alessia.webp", null, List.of(shared)));
        AgentTurnFinalizer finalizer = new AgentTurnFinalizer(outbox, catalog, sessions,
                mock(CrmWhatsappAgentStateMachine.class), mock(AgentRuntimeConfigService.class),
                mock(JdbcTemplate.class));
        JobLease lease = new JobLease(41L, 8L, 99L, 1, "worker:lease", LocalDateTime.now());
        GeneratedTurn turn = new GeneratedTurn("Alessia Rayas", "Consulta Alessia", false,
                "GEMINI", "gemini", 10, 2, "fingerprint", List.of(
                        new AgentMediaRequest(MediaType.PRODUCT_IMAGE, 77, ""),
                        new AgentMediaRequest(MediaType.PRODUCT_IMAGE, 77, "AZUL")));

        finalizer.finalizeTurn(lease, turn);

        verify(outbox, times(1)).enqueueImage(8L, 41L, 99L, 0,
                "/storage/productos/alessia-azul.webp", "alessia-azul.webp", "image/webp", "ALESSIA RAYAS");
        verify(outbox).enqueueText(8L, 41L, 99L, 1, "Alessia Rayas");
    }

    @Test
    void doesNotReplaceAMissingExactColorImageWithTheGlobalImage() {
        AgentOutboxService outbox = mock(AgentOutboxService.class);
        AgentCatalogQueryService catalog = mock(AgentCatalogQueryService.class);
        CrmWhatsappAgentSessionRepository sessions = mock(CrmWhatsappAgentSessionRepository.class);
        CrmWhatsappAgentSession session = new CrmWhatsappAgentSession();
        session.setIdAgentSession(8L);
        session.setActiveSlot(true);
        CrmWhatsappMessage anchor = new CrmWhatsappMessage();
        anchor.setIdMessage(99L);
        session.setLastCustomerMessage(anchor);
        when(sessions.findForUpdate(8L)).thenReturn(java.util.Optional.of(session));
        when(catalog.getProductImage(8L, 78, "NEGRO")).thenReturn(new ProductImageResult(
                78, "MELINA", "NEGRO", "/storage/productos/melina.webp", null, List.of()));
        AgentTurnFinalizer finalizer = new AgentTurnFinalizer(outbox, catalog, sessions,
                mock(CrmWhatsappAgentStateMachine.class), mock(AgentRuntimeConfigService.class),
                mock(JdbcTemplate.class));
        JobLease lease = new JobLease(41L, 8L, 99L, 1, "worker:lease", LocalDateTime.now());
        GeneratedTurn turn = new GeneratedTurn("Pedido actualizado", "MELINA negra", false,
                "JAVA_DETERMINISTIC", "cart", 10, 2, "fingerprint",
                List.of(new AgentMediaRequest(MediaType.PRODUCT_IMAGE, 78, "NEGRO")));

        finalizer.finalizeTurn(lease, turn);

        verify(outbox, times(0)).enqueueImage(8L, 41L, 99L, 0,
                "/storage/productos/melina.webp", "melina.webp", "image/webp", "MELINA — NEGRO");
        verify(outbox).enqueueText(8L, 41L, 99L, 0, "Pedido actualizado");
    }

    @Test
    void doesNotResendAnAutomaticProductImageShownAmongTheLastTwentyOutgoingMediaMessages() {
        AgentOutboxService outbox = mock(AgentOutboxService.class);
        AgentCatalogQueryService catalog = mock(AgentCatalogQueryService.class);
        CrmWhatsappAgentSessionRepository sessions = mock(CrmWhatsappAgentSessionRepository.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        CrmWhatsappAgentSession session = new CrmWhatsappAgentSession();
        session.setIdAgentSession(8L);
        session.setActiveSlot(true);
        CrmWhatsappConversation conversation = new CrmWhatsappConversation();
        conversation.setIdConversation(20L);
        session.setConversation(conversation);
        CrmWhatsappMessage anchor = new CrmWhatsappMessage();
        anchor.setIdMessage(99L);
        session.setLastCustomerMessage(anchor);
        when(sessions.findForUpdate(8L)).thenReturn(java.util.Optional.of(session));
        when(catalog.getProductImage(8L, 78, "NEGRO")).thenReturn(new ProductImageResult(
                78, "MELINA", "NEGRO", "/storage/productos/melina.webp", null,
                List.of(new ProductImage("NEGRO", "/storage/productos/melina-negro.webp", null, true))));
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq(20L), anyString(), eq(20L), anyString())).thenReturn(1L);
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq(20L),
                eq("/storage/productos/melina-negro.webp"))).thenReturn(1L);
        AgentTurnFinalizer finalizer = new AgentTurnFinalizer(outbox, catalog, sessions,
                mock(CrmWhatsappAgentStateMachine.class), mock(AgentRuntimeConfigService.class), jdbc);
        JobLease lease = new JobLease(41L, 8L, 99L, 1, "worker:lease", LocalDateTime.now());
        GeneratedTurn turn = new GeneratedTurn("Pedido actualizado", "MELINA negra", false,
                "JAVA_DETERMINISTIC", "cart", 10, 2, "fingerprint",
                List.of(new AgentMediaRequest(MediaType.PRODUCT_IMAGE, 78, "NEGRO", true)));

        finalizer.finalizeTurn(lease, turn);

        verify(outbox, times(0)).enqueueImage(8L, 41L, 99L, 0,
                "/storage/productos/melina-negro.webp", "melina-negro.webp", "image/webp", "MELINA — NEGRO");
        verify(outbox).enqueueText(8L, 41L, 99L, 0, "Pedido actualizado");
    }

    @Test
    void resendsARecentlyShownProductImageWhenTheCustomerExplicitlyRequestsIt() {
        AgentOutboxService outbox = mock(AgentOutboxService.class);
        AgentCatalogQueryService catalog = mock(AgentCatalogQueryService.class);
        CrmWhatsappAgentSessionRepository sessions = mock(CrmWhatsappAgentSessionRepository.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        CrmWhatsappAgentSession session = new CrmWhatsappAgentSession();
        session.setIdAgentSession(8L);
        session.setActiveSlot(true);
        session.setCatalogPreviewSentAt(LocalDateTime.now().minusMinutes(5));
        CrmWhatsappConversation conversation = new CrmWhatsappConversation();
        conversation.setIdConversation(20L);
        session.setConversation(conversation);
        CrmWhatsappMessage anchor = new CrmWhatsappMessage();
        anchor.setIdMessage(99L);
        session.setLastCustomerMessage(anchor);
        when(sessions.findForUpdate(8L)).thenReturn(java.util.Optional.of(session));
        when(catalog.getProductImage(8L, 78, "NEGRO")).thenReturn(new ProductImageResult(
                78, "MELINA", "NEGRO", "/storage/productos/melina.webp", null,
                List.of(new ProductImage("NEGRO", "/storage/productos/melina-negro.webp", null, true))));
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq(20L), anyString(), eq(20L), anyString())).thenReturn(1L);
        AgentTurnFinalizer finalizer = new AgentTurnFinalizer(outbox, catalog, sessions,
                mock(CrmWhatsappAgentStateMachine.class), mock(AgentRuntimeConfigService.class), jdbc);
        JobLease lease = new JobLease(41L, 8L, 99L, 1, "worker:lease", LocalDateTime.now());
        GeneratedTurn turn = new GeneratedTurn("Aquí tienes el color negro", "MELINA negra", false,
                "JAVA_DETERMINISTIC", "catalog", 10, 2, "fingerprint",
                List.of(new AgentMediaRequest(MediaType.PRODUCT_IMAGE, 78, "NEGRO")));

        finalizer.finalizeTurn(lease, turn);

        verify(outbox).enqueueImage(8L, 41L, 99L, 0,
                "/storage/productos/melina-negro.webp", "melina-negro.webp", "image/webp", "MELINA — NEGRO");
        verify(outbox).enqueueText(8L, 41L, 99L, 1, "Aquí tienes el color negro");
        verify(jdbc, times(0)).queryForObject(anyString(), eq(Long.class), eq(20L),
                eq("/storage/productos/melina-negro.webp"));
    }

    @Test
    void resendsCatalogPreviewImagesWhenTheCustomerRequestsTheCatalogAgain() {
        AgentOutboxService outbox = mock(AgentOutboxService.class);
        AgentCatalogQueryService catalog = mock(AgentCatalogQueryService.class);
        CrmWhatsappAgentSessionRepository sessions = mock(CrmWhatsappAgentSessionRepository.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        CrmWhatsappAgentSession session = new CrmWhatsappAgentSession();
        session.setIdAgentSession(8L);
        session.setActiveSlot(true);
        session.setCatalogPreviewSentAt(LocalDateTime.now().minusMinutes(5));
        CrmWhatsappConversation conversation = new CrmWhatsappConversation();
        conversation.setIdConversation(20L);
        session.setConversation(conversation);
        CrmWhatsappMessage anchor = new CrmWhatsappMessage();
        anchor.setIdMessage(99L);
        session.setLastCustomerMessage(anchor);
        when(sessions.findForUpdate(8L)).thenReturn(java.util.Optional.of(session));
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq(20L), anyString(), eq(20L), anyString())).thenReturn(1L);
        when(catalog.getProductImage(8L, 78, "")).thenReturn(new ProductImageResult(
                78, "MELINA", "", "/productos/melina-global.webp", null, List.of()));
        when(catalog.getProductImage(8L, 79, "")).thenReturn(new ProductImageResult(
                79, "ANNIE", "", "/productos/annie-global.webp", null, List.of()));
        when(catalog.getProductImage(8L, 80, "")).thenReturn(new ProductImageResult(
                80, "LYANA", "", "/productos/lyana-global.webp", null, List.of()));
        when(catalog.getProduct(8L, 78)).thenReturn(new ProductDetail(
                78, "MELINA", "", "CONJUNTO", false, null,
                null, null, List.of(), List.of(), 1, 80D, 1));
        when(catalog.getProduct(8L, 79)).thenReturn(new ProductDetail(
                79, "ANNIE", "", "CONJUNTO", false, null,
                null, null, List.of(), List.of(), 1, 75D, 1));
        when(catalog.getProduct(8L, 80)).thenReturn(new ProductDetail(
                80, "LYANA", "", "CONJUNTO", false, null,
                null, null, List.of(), List.of(), 1, 70D, 1));
        AgentTurnFinalizer finalizer = new AgentTurnFinalizer(outbox, catalog, sessions,
                mock(CrmWhatsappAgentStateMachine.class), mock(AgentRuntimeConfigService.class), jdbc);
        JobLease lease = new JobLease(41L, 8L, 99L, 1, "worker:lease", LocalDateTime.now());
        GeneratedTurn turn = new GeneratedTurn("Aquí tienes el catálogo", "Catálogo", false,
                "JAVA_DETERMINISTIC", "catalog", 10, 2, "fingerprint",
                List.of(
                        new AgentMediaRequest(MediaType.PRODUCT_IMAGE, 78, "", false, true),
                        new AgentMediaRequest(MediaType.PRODUCT_IMAGE, 79, "", false, true),
                        new AgentMediaRequest(MediaType.PRODUCT_IMAGE, 80, "", false, true)));

        finalizer.finalizeTurn(lease, turn);

        verify(outbox, times(3)).enqueueImage(eq(8L), eq(41L), eq(99L), anyInt(),
                anyString(), anyString(), anyString(), anyString());
        verify(outbox).enqueueText(8L, 41L, 99L, 3, "Aquí tienes el catálogo");
    }

    @Test
    void sendsGlobalCatalogImagesWithProductStatusAndMarksThePreviewAsSent() {
        AgentOutboxService outbox = mock(AgentOutboxService.class);
        AgentCatalogQueryService catalog = mock(AgentCatalogQueryService.class);
        CrmWhatsappAgentSessionRepository sessions = mock(CrmWhatsappAgentSessionRepository.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        CrmWhatsappAgentSession session = new CrmWhatsappAgentSession();
        session.setIdAgentSession(8L);
        session.setActiveSlot(true);
        CrmWhatsappConversation conversation = new CrmWhatsappConversation();
        conversation.setIdConversation(20L);
        session.setConversation(conversation);
        CrmWhatsappMessage anchor = new CrmWhatsappMessage();
        anchor.setIdMessage(99L);
        session.setLastCustomerMessage(anchor);
        when(sessions.findForUpdate(8L)).thenReturn(java.util.Optional.of(session));
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq(20L), anyString(), eq(20L), anyString())).thenReturn(1L);
        when(catalog.getProductImage(8L, 78, "")).thenReturn(new ProductImageResult(
                78, "MELINA", "", "/productos/melina-global.webp", null,
                List.of(new ProductImage("NEGRO", "/productos/melina-negro.webp", null, true))));
        when(catalog.getProductImage(8L, 79, "")).thenReturn(new ProductImageResult(
                79, "ANNIE", "", "/productos/annie-global.webp", null, List.of()));
        when(catalog.getProductImage(8L, 80, "")).thenReturn(new ProductImageResult(
                80, "LYANA", "", "/productos/lyana-global.webp", null, List.of()));
        when(catalog.getProduct(8L, 78)).thenReturn(new ProductDetail(
                78, "MELINA", "", "CONJUNTO", true, LocalDate.of(2026, 10, 26),
                null, null, List.of(), List.of(), 1, 79D, 1));
        when(catalog.getProduct(8L, 79)).thenReturn(new ProductDetail(
                79, "ANNIE", "", "CONJUNTO", false, null,
                null, null, List.of(), List.of(), 1, 75D, 1));
        when(catalog.getProduct(8L, 80)).thenReturn(new ProductDetail(
                80, "LYANA", "", "CONJUNTO", false, null,
                null, null, List.of(), List.of(), 1, 70D, 1));
        AgentTurnFinalizer finalizer = new AgentTurnFinalizer(outbox, catalog, sessions,
                mock(CrmWhatsappAgentStateMachine.class), mock(AgentRuntimeConfigService.class), jdbc);
        JobLease lease = new JobLease(41L, 8L, 99L, 1, "worker:lease", LocalDateTime.now());
        GeneratedTurn turn = new GeneratedTurn("Aquí tienes el catálogo", "Catálogo", false,
                "JAVA_DETERMINISTIC", "catalog", 10, 2, "fingerprint",
                List.of(
                        new AgentMediaRequest(MediaType.PRODUCT_IMAGE, 78, "", false, true),
                        new AgentMediaRequest(MediaType.PRODUCT_IMAGE, 79, "", false, true),
                        new AgentMediaRequest(MediaType.PRODUCT_IMAGE, 80, "", false, true)));

        finalizer.finalizeTurn(lease, turn);

        org.mockito.InOrder order = inOrder(outbox);
        order.verify(outbox).enqueueImage(8L, 41L, 99L, 0,
                "/productos/melina-global.webp", "melina-global.webp", "image/webp",
                "MELINA\nPreventa: desde el 26 de octubre de 2026, entrega a Shalom al día siguiente de atención; domingos y feriados pasan al siguiente día laborable. Esa es la primera fecha disponible y puede coordinarse una posterior.");
        order.verify(outbox).enqueueImage(8L, 41L, 99L, 1,
                "/productos/annie-global.webp", "annie-global.webp", "image/webp",
                "ANNIE\nDisponible: entrega a Shalom al día siguiente de atención; domingos y feriados pasan al siguiente día laborable. Esa es la primera fecha disponible y puede coordinarse una posterior.");
        order.verify(outbox).enqueueImage(8L, 41L, 99L, 2,
                "/productos/lyana-global.webp", "lyana-global.webp", "image/webp",
                "LYANA\nDisponible: entrega a Shalom al día siguiente de atención; domingos y feriados pasan al siguiente día laborable. Esa es la primera fecha disponible y puede coordinarse una posterior.");
        order.verify(outbox).enqueueText(8L, 41L, 99L, 3, "Aquí tienes el catálogo");
        org.junit.jupiter.api.Assertions.assertNotNull(session.getCatalogPreviewSentAt());
    }

    @Test
    void queuesSafeMessageWhenJobFailsAndAdvisorAttentionIsDisabled() {
        AgentOutboxService outbox = mock(AgentOutboxService.class);
        CrmWhatsappAgentSessionRepository sessions = mock(CrmWhatsappAgentSessionRepository.class);
        AgentRuntimeConfigService runtime = mock(AgentRuntimeConfigService.class);
        CrmWhatsappAgentSession session = new CrmWhatsappAgentSession();
        session.setIdAgentSession(8L);
        session.setActiveSlot(true);
        CrmWhatsappConversation conversation = new CrmWhatsappConversation();
        conversation.setIdConversation(20L);
        session.setConversation(conversation);
        when(sessions.findForUpdate(8L)).thenReturn(java.util.Optional.of(session));
        when(runtime.forSession(8L)).thenReturn(new AgentRuntimeConfigService.RuntimeConfig(false,
                com.sistemapos.sistematextil.model.CrmWhatsappAgentActivationStage.AUTOMATIC_SALES,
                java.util.Set.of(), 100, 5000, 240, 85, false, 3, 5, 15, 12, 6));
        AgentTurnFinalizer finalizer = new AgentTurnFinalizer(outbox, mock(AgentCatalogQueryService.class), sessions,
                mock(CrmWhatsappAgentStateMachine.class), runtime, mock(JdbcTemplate.class));
        JobLease lease = new JobLease(41L, 8L, 99L, 3, "worker:lease", LocalDateTime.now());

        finalizer.failPermanently(lease, "Proveedor no disponible");

        int expectedOrdinal = Math.floorMod(java.util.Objects.hash("AGENT_FAILURE", 41L), Integer.MAX_VALUE);
        verify(outbox).enqueueText(8L, null, 99L, expectedOrdinal,
                "No pude completar esta acción, bella. Tu mensaje sí fue recibido; "
                + "puedes intentarlo nuevamente o elegir otro producto.");
    }

    @Test
    void sendsOneMaintenanceNoticeWhenGeminiCreditsAreDepletedWithoutEscalating() {
        AgentOutboxService outbox = mock(AgentOutboxService.class);
        CrmWhatsappAgentSessionRepository sessions = mock(CrmWhatsappAgentSessionRepository.class);
        AgentRuntimeConfigService runtime = mock(AgentRuntimeConfigService.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        CrmWhatsappAgentSession session = new CrmWhatsappAgentSession();
        session.setIdAgentSession(8L);
        session.setActiveSlot(true);
        session.setState(CrmWhatsappAgentState.EXPLORANDO_PRODUCTOS);
        CrmWhatsappConversation conversation = new CrmWhatsappConversation();
        conversation.setIdConversation(20L);
        session.setConversation(conversation);
        when(sessions.findForUpdate(8L)).thenReturn(java.util.Optional.of(session));
        when(runtime.forSession(8L)).thenReturn(new AgentRuntimeConfigService.RuntimeConfig(false,
                com.sistemapos.sistematextil.model.CrmWhatsappAgentActivationStage.AUTOMATIC_SALES,
                java.util.Set.of(), 100, 5000, 240, 85, true, 3, 5, 15, 12, 6));
        when(jdbc.queryForObject(anyString(), eq(Long.class),
                eq(20L), eq(AgentTurnFinalizer.PROVIDER_MAINTENANCE_MESSAGE), any(LocalDateTime.class),
                eq(20L), eq(AgentTurnFinalizer.PROVIDER_MAINTENANCE_MESSAGE), any(LocalDateTime.class)))
                .thenReturn(0L);
        AgentTurnFinalizer finalizer = new AgentTurnFinalizer(outbox, mock(AgentCatalogQueryService.class), sessions,
                mock(CrmWhatsappAgentStateMachine.class), runtime, jdbc);
        JobLease lease = new JobLease(41L, 8L, 99L, 1, "worker:lease", LocalDateTime.now());

        finalizer.failPermanently(lease,
                "Gemini respondio HTTP 402: Your prepayment credits are depleted");

        int expectedOrdinal = Math.floorMod(java.util.Objects.hash("AGENT_PROVIDER_MAINTENANCE", 41L),
                Integer.MAX_VALUE);
        verify(outbox).enqueueText(8L, null, 99L, expectedOrdinal,
                AgentTurnFinalizer.PROVIDER_MAINTENANCE_MESSAGE);
        verify(jdbc, never()).update(org.mockito.ArgumentMatchers.contains("crm_whatsapp_support_case"),
                any(Object[].class));
        org.junit.jupiter.api.Assertions.assertEquals(CrmWhatsappAgentState.EXPLORANDO_PRODUCTOS,
                session.getState());
    }

    @Test
    void doesNotRepeatMaintenanceNoticeDuringTheProviderOutageWindow() {
        AgentOutboxService outbox = mock(AgentOutboxService.class);
        CrmWhatsappAgentSessionRepository sessions = mock(CrmWhatsappAgentSessionRepository.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        CrmWhatsappAgentSession session = new CrmWhatsappAgentSession();
        session.setIdAgentSession(8L);
        session.setActiveSlot(true);
        CrmWhatsappConversation conversation = new CrmWhatsappConversation();
        conversation.setIdConversation(20L);
        session.setConversation(conversation);
        when(sessions.findForUpdate(8L)).thenReturn(java.util.Optional.of(session));
        when(jdbc.queryForObject(anyString(), eq(Long.class),
                eq(20L), eq(AgentTurnFinalizer.PROVIDER_MAINTENANCE_MESSAGE), any(LocalDateTime.class),
                eq(20L), eq(AgentTurnFinalizer.PROVIDER_MAINTENANCE_MESSAGE), any(LocalDateTime.class)))
                .thenReturn(1L);
        AgentTurnFinalizer finalizer = new AgentTurnFinalizer(outbox, mock(AgentCatalogQueryService.class), sessions,
                mock(CrmWhatsappAgentStateMachine.class), mock(AgentRuntimeConfigService.class), jdbc);
        JobLease lease = new JobLease(42L, 8L, 100L, 1, "worker:lease", LocalDateTime.now());

        finalizer.failPermanently(lease, "Gemini respondio HTTP 402");

        verify(outbox, never()).enqueueText(eq(8L), any(), eq(100L), any(Integer.class), anyString());
    }

    @Test
    void explainsRealStockFailureInsteadOfClaimingTheMessageWasNotUnderstood() {
        AgentOutboxService outbox = mock(AgentOutboxService.class);
        CrmWhatsappAgentSessionRepository sessions = mock(CrmWhatsappAgentSessionRepository.class);
        AgentRuntimeConfigService runtime = mock(AgentRuntimeConfigService.class);
        CrmWhatsappAgentSession session = new CrmWhatsappAgentSession();
        session.setIdAgentSession(8L);
        session.setActiveSlot(true);
        CrmWhatsappConversation conversation = new CrmWhatsappConversation();
        conversation.setIdConversation(20L);
        session.setConversation(conversation);
        when(sessions.findForUpdate(8L)).thenReturn(java.util.Optional.of(session));
        when(runtime.forSession(8L)).thenReturn(new AgentRuntimeConfigService.RuntimeConfig(false,
                com.sistemapos.sistematextil.model.CrmWhatsappAgentActivationStage.AUTOMATIC_SALES,
                java.util.Set.of(), 100, 5000, 240, 85, false, 3, 5, 15, 12, 6));
        AgentTurnFinalizer finalizer = new AgentTurnFinalizer(outbox, mock(AgentCatalogQueryService.class), sessions,
                mock(CrmWhatsappAgentStateMachine.class), runtime, mock(JdbcTemplate.class));
        JobLease lease = new JobLease(42L, 8L, 100L, 1, "worker:lease", LocalDateTime.now());

        finalizer.failPermanently(lease, "Stock insuficiente; disponible: 0");

        int expectedOrdinal = Math.floorMod(java.util.Objects.hash("AGENT_FAILURE", 42L), Integer.MAX_VALUE);
        verify(outbox).enqueueText(8L, null, 100L, expectedOrdinal,
                "El producto seleccionado ya no cuenta con stock suficiente, bella. "
                + "Puedes elegir otro color, talla o modelo y continuaré con tu pedido.");
    }
}
