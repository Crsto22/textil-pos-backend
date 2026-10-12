package com.sistemapos.sistematextil.services.agent.film;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.sistemapos.sistematextil.model.CrmWhatsappFilmStatus;
import com.sistemapos.sistematextil.repositories.CrmWhatsappFilmAddonRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component @RequiredArgsConstructor @Slf4j
public class AgentFilmCompletionRunner {
    private static final List<CrmWhatsappFilmStatus> READY=List.of(
            CrmWhatsappFilmStatus.PAID_PENDING_REVIEW,
            CrmWhatsappFilmStatus.EXPIRED_WITHOUT_FILM,
            CrmWhatsappFilmStatus.DECLINED,
            CrmWhatsappFilmStatus.UNAVAILABLE_WITHOUT_FILM);
    private final CrmWhatsappFilmAddonRepository addons;
    private final AgentFilmFinalizationService finalizer;
    @Scheduled(initialDelayString="${crm.whatsapp.agent.film-completion.initial-delay-ms:5000}",fixedDelayString="${crm.whatsapp.agent.film-completion.delay-ms:3000}")
    public void complete(){for(Long id:addons.findPendingFinalizationIds(READY,PageRequest.of(0,20))){try{finalizer.finalizeSale(id,true,prefix(id));}catch(RuntimeException e){log.warn("No se pudo completar la venta del film {}: {}",id,e.getMessage());}}}
    private String prefix(Long id){return addons.findById(id).filter(a->a.getStatus()==CrmWhatsappFilmStatus.PAID_PENDING_REVIEW).isPresent()?"✅ Pago adicional validado correctamente.":"";}
}
