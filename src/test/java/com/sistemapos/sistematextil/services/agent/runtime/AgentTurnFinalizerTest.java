package com.sistemapos.sistematextil.services.agent.runtime;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import com.sistemapos.sistematextil.model.CrmWhatsappAgentSession;
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
    void queuesConfiguredWelcomeOnlyBeforeTheFirstAgentReply() {
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
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq(20L), eq(99L))).thenReturn(0L);
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
                "emma.webp", "image/webp", null);
        order.verify(outbox).enqueueImage(8L, 41L, 99L, 1, "/storage/productos/emma-camel.webp",
                "emma-camel.webp", "image/webp", null);
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
                "/storage/productos/alessia-azul.webp", "alessia-azul.webp", "image/webp", null);
        verify(outbox).enqueueText(8L, 41L, 99L, 1, "Alessia Rayas");
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
                "No entendi bien tu mensaje, bella. ¿Puedes indicarme nuevamente "
                + "el producto, color o talla que deseas?");
    }
}
