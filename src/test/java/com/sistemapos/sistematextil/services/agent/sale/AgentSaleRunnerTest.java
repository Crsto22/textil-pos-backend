package com.sistemapos.sistematextil.services.agent.sale;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.sistemapos.sistematextil.model.CrmWhatsappAgentActivationStage;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAgentSessionRepository;
import com.sistemapos.sistematextil.services.agent.config.AgentRuntimeConfigService;
import com.sistemapos.sistematextil.services.agent.config.AgentRuntimeConfigService.RuntimeConfig;

class AgentSaleRunnerTest {
    @Test
    void blocksAutomaticSaleWhenAgentIsDisabled() {
        CrmWhatsappAgentSessionRepository sessions = mock(CrmWhatsappAgentSessionRepository.class);
        AgentSaleService sales = mock(AgentSaleService.class);
        AgentRuntimeConfigService runtime = mock(AgentRuntimeConfigService.class);
        when(sessions.findReadyForSaleSessionIds(any(), any(), any())).thenReturn(List.of(41L));
        when(runtime.forSession(41L)).thenReturn(new RuntimeConfig(false,
                CrmWhatsappAgentActivationStage.AUTOMATIC_SALES, Set.of(), 500,
                5000, 240, 85, false, 3, 5, 15, 12, 6));

        new AgentSaleRunner(sessions, sales, runtime).createReadySales();

        verify(sales, never()).createSalesNote(any(), any());
    }

    @Test
    void createsTheSaleInAutomaticSalesStage() {
        CrmWhatsappAgentSessionRepository sessions = mock(CrmWhatsappAgentSessionRepository.class);
        AgentSaleService sales = mock(AgentSaleService.class);
        AgentRuntimeConfigService runtime = mock(AgentRuntimeConfigService.class);
        when(sessions.findReadyForSaleSessionIds(any(), any(), any())).thenReturn(List.of(41L));
        when(runtime.forSession(41L)).thenReturn(config(CrmWhatsappAgentActivationStage.AUTOMATIC_SALES));

        new AgentSaleRunner(sessions, sales, runtime).createReadySales();

        verify(sales).createSalesNote(eq(41L), eq("automatic-ready-sale"));
    }

    private RuntimeConfig config(CrmWhatsappAgentActivationStage stage) {
        return new RuntimeConfig(true, stage, Set.of(), 10, 5000, 240, 85,
                false, 3, 5, 15, 12, 6);
    }
}
