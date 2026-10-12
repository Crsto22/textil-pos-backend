package com.sistemapos.sistematextil.services.agent.sale;

import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.sistemapos.sistematextil.model.CrmWhatsappAgentState;
import com.sistemapos.sistematextil.model.CrmWhatsappFulfillmentStatus;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAgentSessionRepository;
import com.sistemapos.sistematextil.services.agent.config.AgentRuntimeConfigService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class AgentSaleRunner {
    private final CrmWhatsappAgentSessionRepository sessionRepository;
    private final AgentSaleService saleService;
    private final AgentRuntimeConfigService runtimeConfig;

    @Scheduled(
            initialDelayString = "${crm.whatsapp.agent.sale.initial-delay-ms:3000}",
            fixedDelayString = "${crm.whatsapp.agent.sale.delay-ms:1000}")
    public void createReadySales() {
        for (Long sessionId : sessionRepository.findReadyForSaleSessionIds(
                CrmWhatsappAgentState.LISTO_PARA_VENTA,
                CrmWhatsappFulfillmentStatus.READY,
                PageRequest.of(0, 20))) {
            if (!runtimeConfig.forSession(sessionId).automaticSalesEnabled()) continue;
            try {
                saleService.createSalesNote(sessionId, "automatic-ready-sale");
            } catch (RuntimeException error) {
                log.warn("No se pudo generar la Nota de Venta de la sesion {}: {}",
                        sessionId, error.getMessage());
            }
        }
    }
}
