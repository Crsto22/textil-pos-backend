package com.sistemapos.sistematextil.services;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CrmWhatsappAiSaleDraftRunner {
    private final CrmWhatsappAiSaleDraftService draftService;

    @Scheduled(initialDelay = 60_000, fixedDelay = 300_000)
    public void expire() {
        draftService.expireDrafts();
    }
}
