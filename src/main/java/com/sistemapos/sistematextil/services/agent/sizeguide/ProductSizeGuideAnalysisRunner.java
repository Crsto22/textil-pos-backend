package com.sistemapos.sistematextil.services.agent.sizeguide;

import java.lang.management.ManagementFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ProductSizeGuideAnalysisRunner {
    private final JdbcProductSizeGuideStore store;
    private final ProductSizeGuideAnalysisService analysis;
    private final int maxAttempts;
    private final String worker = ManagementFactory.getRuntimeMXBean().getName();

    public ProductSizeGuideAnalysisRunner(JdbcProductSizeGuideStore store,
            ProductSizeGuideAnalysisService analysis,
            @Value("${crm.whatsapp.agent.size-guide.max-attempts:3}") int maxAttempts) {
        this.store = store;
        this.analysis = analysis;
        this.maxAttempts = Math.max(1, Math.min(5, maxAttempts));
    }

    @Scheduled(initialDelayString = "${crm.whatsapp.agent.size-guide.runner-initial-delay-ms:20000}",
            fixedDelayString = "${crm.whatsapp.agent.size-guide.runner-delay-ms:3000}")
    public void process() {
        var job = store.claim(worker, maxAttempts);
        if (job != null) analysis.process(job, maxAttempts);
    }
}
