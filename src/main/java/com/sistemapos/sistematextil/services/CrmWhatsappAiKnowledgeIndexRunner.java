package com.sistemapos.sistematextil.services;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CrmWhatsappAiKnowledgeIndexRunner {
    private final CrmWhatsappAiKnowledgeService knowledgeService;
    private volatile boolean applicationReady;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        applicationReady = true;
    }

    @Scheduled(initialDelay = 5_000L, fixedDelayString = "${crm.whatsapp.ai.knowledge.fixed-delay-ms:5000}")
    public void indexPendingArticles() {
        if (!applicationReady) return;
        knowledgeService.indexNextBatch();
    }
}
