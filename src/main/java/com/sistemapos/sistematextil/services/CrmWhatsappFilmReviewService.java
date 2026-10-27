package com.sistemapos.sistematextil.services;

import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.sistemapos.sistematextil.model.*;
import com.sistemapos.sistematextil.repositories.*;
import com.sistemapos.sistematextil.util.usuario.Rol;
import com.sistemapos.sistematextil.services.agent.film.AgentFilmService;

import lombok.RequiredArgsConstructor;

@Service @RequiredArgsConstructor
public class CrmWhatsappFilmReviewService {
    private final CrmWhatsappFilmEvidenceRepository evidenceRepository;
    private final CrmWhatsappFilmAddonRepository addonRepository;
    private final UsuarioRepository users;
    private final S3StorageService storage;
    private final AgentFilmService filmService;

    @Transactional
    public AgentFilmService.FilmResult reactivate(Long addonId,Usuario user){requireUser(user);return filmService.reactivate(addonId);}

    @Transactional
    public AgentFilmService.FilmResult confirmManually(Long addonId,ManualConfirmationRequest request,Usuario user){
        Usuario actor=requireUser(user);
        if(request==null||!Boolean.TRUE.equals(request.confirmedExternal()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Confirma que verificaste el pago del film para embalaje o su cobertura en un pedido anterior");
        return filmService.confirmManually(addonId,actor,request.coveredBySaleId());
    }

    @Transactional(readOnly=true)
    public FilmEvidenceResponse get(Long id,Usuario user){requireUser(user);return response(requireEvidence(id));}
    @Transactional(readOnly=true)
    public MediaDownload download(Long id,Usuario user){requireUser(user);var e=requireEvidence(id);return new MediaDownload(storage.readBytes(e.getStoragePath()),e.getMimeType(),e.getFileName()==null?"comprobante-film":e.getFileName());}

    @Transactional
    public FilmEvidenceResponse decide(Long id,DecisionRequest request,Usuario user){Usuario actor=requireUser(user);CrmWhatsappFilmEvidence e=evidenceRepository.findForUpdate(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Comprobante de film para embalaje no encontrado"));
        CrmWhatsappFilmAddon addon=addonRepository.findForUpdate(e.getFilmAddon().getIdFilmAddon()).orElseThrow();
        if(addon.getStatus()!=CrmWhatsappFilmStatus.PAID_PENDING_REVIEW||addon.getVenta()==null
                ||"ANULADA".equalsIgnoreCase(addon.getVenta().getEstado()))throw new ResponseStatusException(HttpStatus.CONFLICT,"El film ya no está pendiente de revisión");
        if(e.getValidationStatus()!=CrmWhatsappPaymentEvidenceStatus.ACEPTABLE||e.getProcessingStatus()!=CrmWhatsappPaymentProcessingStatus.COMPLETED)throw new ResponseStatusException(HttpStatus.CONFLICT,"El comprobante de film para embalaje no está pendiente de revisión");
        String action=request==null?"":clean(request.action()).toUpperCase();String note=request==null?"":clean(request.note());
        if("ACCEPT".equals(action)){if(!Boolean.TRUE.equals(request.confirmedExternal()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Confirma que verificaste el abono de S/2");e.setValidationStatus(CrmWhatsappPaymentEvidenceStatus.VALIDADO);filmService.confirmManually(addon.getIdFilmAddon(),actor,null);}
        else if("REJECT".equals(action)){if(note.length()<5)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Indica el motivo del rechazo con al menos 5 caracteres");e.setValidationStatus(CrmWhatsappPaymentEvidenceStatus.RECHAZADO);addon.setStatus(CrmWhatsappFilmStatus.REJECTED_WITHOUT_FILM);}
        else throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Decisión de film no válida");
        e.setReviewedBy(actor);e.setReviewNote(note.isBlank()?null:note);e.setReviewedAt(LocalDateTime.now());evidenceRepository.save(e);addon.setResolvedAt(LocalDateTime.now());addonRepository.save(addon);return response(e);}
    private CrmWhatsappFilmEvidence requireEvidence(Long id){return evidenceRepository.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Comprobante de film para embalaje no encontrado"));}
    private Usuario requireUser(Usuario user){if(user==null||user.getIdUsuario()==null)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"No autenticado");Usuario actor=users.findByIdUsuarioAndDeletedAtIsNull(user.getIdUsuario()).orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED,"No autenticado"));if(!(actor.getRol()==Rol.ADMINISTRADOR||actor.getRol()==Rol.SISTEMA||Boolean.TRUE.equals(actor.getAccesoCrm())))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Sin acceso al CRM");return actor;}
    private FilmEvidenceResponse response(CrmWhatsappFilmEvidence e){return new FilmEvidenceResponse(e.getIdFilmEvidence(),e.getFilmAddon().getIdFilmAddon(),e.getValidationStatus().name(),e.getProcessingStatus().name(),e.getMimeType(),e.getFileName(),e.getDetectedAmount(),e.getOperationCode(),e.getOperationAt(),e.getConfidence(),e.getReviewNote(),e.getReviewedAt(),"/api/crm/whatsapp/film-evidences/"+e.getIdFilmEvidence()+"/media");}
    private String clean(String v){return v==null?"":v.trim();}
    public record DecisionRequest(String action,Boolean confirmedExternal,String note){}
    public record ManualConfirmationRequest(Boolean confirmedExternal,Integer coveredBySaleId){}
    public record FilmEvidenceResponse(Long evidenceId,Long filmAddonId,String validationStatus,String processingStatus,String mimeType,String fileName,java.math.BigDecimal detectedAmount,String operationCode,LocalDateTime operationAt,Integer confidence,String reviewNote,LocalDateTime reviewedAt,String mediaUrl){}
    public record MediaDownload(byte[] bytes,String contentType,String fileName){}
}
