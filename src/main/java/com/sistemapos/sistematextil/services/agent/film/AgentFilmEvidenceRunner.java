package com.sistemapos.sistematextil.services.agent.film;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component @RequiredArgsConstructor @Slf4j
public class AgentFilmEvidenceRunner {
    private final AgentFilmEvidenceService service;
    @Scheduled(initialDelayString="${crm.whatsapp.agent.film.initial-delay-ms:3000}",fixedDelayString="${crm.whatsapp.agent.film.delay-ms:1000}")
    public void process(){for(Long id:service.readyIds(20)){try{service.process(id);}catch(RuntimeException e){log.warn("No se pudo analizar el comprobante de film {}: {}",id,e.getMessage());}}}
}
