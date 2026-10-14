package com.sistemapos.sistematextil.services.agent.film;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sistemapos.sistematextil.model.*;
import com.sistemapos.sistematextil.repositories.*;
import com.sistemapos.sistematextil.services.S3StorageService;
import com.sistemapos.sistematextil.services.agent.CrmWhatsappAgentStateMachine;
import com.sistemapos.sistematextil.services.agent.config.AgentRuntimeConfigService;
import com.sistemapos.sistematextil.services.agent.outbox.AgentOutboxService;
import com.sistemapos.sistematextil.services.agent.payment.AgentPaymentReceiptExtraction;
import com.sistemapos.sistematextil.services.agent.payment.AgentPaymentReceiptVisionGateway;

@Service
public class AgentFilmEvidenceService {
    private static final int MAX_BYTES = 10 * 1024 * 1024;
    private final CrmWhatsappFilmAddonRepository addons;
    private final CrmWhatsappFilmEvidenceRepository evidence;
    private final CrmWhatsappPaymentEvidenceRepository mainEvidence;
    private final CrmWhatsappMessageRepository messages;
    private final CrmWhatsappAgentSessionRepository sessions;
    private final CrmWhatsappFulfillmentRepository fulfillments;
    private final S3StorageService storage;
    private final AgentPaymentReceiptVisionGateway vision;
    private final AgentRuntimeConfigService config;
    private final CrmWhatsappAgentStateMachine stateMachine;
    private final AgentFilmFinalizationService finalizer;
    private final AgentOutboxService outbox;
    private final TransactionTemplate tx;
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final JdbcTemplate jdbc;

    public AgentFilmEvidenceService(CrmWhatsappFilmAddonRepository addons,
            CrmWhatsappFilmEvidenceRepository evidence, CrmWhatsappPaymentEvidenceRepository mainEvidence,
            CrmWhatsappMessageRepository messages, CrmWhatsappAgentSessionRepository sessions,
            CrmWhatsappFulfillmentRepository fulfillments,
            S3StorageService storage, AgentPaymentReceiptVisionGateway vision,
            AgentRuntimeConfigService config, CrmWhatsappAgentStateMachine stateMachine,
            AgentFilmFinalizationService finalizer, AgentOutboxService outbox,
            JdbcTemplate jdbc,
            org.springframework.transaction.PlatformTransactionManager manager) {
        this.addons=addons; this.evidence=evidence; this.mainEvidence=mainEvidence; this.messages=messages;
        this.sessions=sessions; this.fulfillments=fulfillments; this.storage=storage; this.vision=vision; this.config=config;
        this.stateMachine=stateMachine; this.finalizer=finalizer; this.outbox=outbox; this.jdbc=jdbc;
        this.tx=new TransactionTemplate(manager);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean registerIncomingMedia(Long messageId) {
        if (messageId == null || evidence.findByMessage_IdMessage(messageId).isPresent()) return false;
        CrmWhatsappMessage message = messages.findForUpdate(messageId).orElse(null);
        if (message == null || !"INCOMING".equals(message.getDirection()) || message.getDeletedAt()!=null
                || !supported(message)) return false;
        CrmWhatsappFilmAddon addon = addons.findByAgentSession_IdAgentSession(
                activeSessionId(message.getConversation().getIdConversation())).orElse(null);
        if (addon == null || addon.getStatus()!=CrmWhatsappFilmStatus.AWAITING_PAYMENT) return false;
        byte[] bytes=storage.readBytes(message.getMediaStoragePath());
        if(bytes.length==0||bytes.length>MAX_BYTES) return false;
        String stored=storage.upload(bytes,"crm/film-evidence/"+message.getConversation().getIdConversation()+"/"+UUID.randomUUID()+extension(message.getMediaMimeType()),message.getMediaMimeType());
        CrmWhatsappFilmEvidence e=new CrmWhatsappFilmEvidence(); e.setFilmAddon(addon); e.setMessage(message);
        e.setStoragePath(stored); e.setMimeType(message.getMediaMimeType());
        e.setFileName(message.getMediaFileName()); e.setSha256(hash(bytes));
        try { evidence.save(e); return true; }
        catch (RuntimeException error) { storage.deleteByKey(stored); throw error; }
    }

    @Transactional(readOnly=true)
    public List<Long> readyIds(int limit) { return evidence.findReadyIds(CrmWhatsappPaymentProcessingStatus.QUEUED,
            LocalDateTime.now(), PageRequest.of(0, limit)); }

    public void process(Long evidenceId) {
        Claimed claimed=tx.execute(status->claim(evidenceId)); if(claimed==null)return;
        Completion completion;
        try {
            byte[] bytes=storage.readBytes(claimed.path());
            if(bytes.length==0||bytes.length>MAX_BYTES)throw new IllegalArgumentException("Archivo vacío o mayor a 10 MB");
            var result=vision.extract(claimed.connectionId(),bytes,claimed.mime());
            completion=tx.execute(status->complete(evidenceId,result.extraction(),result.provider()));
        } catch(RuntimeException ex) { tx.executeWithoutResult(status->fail(evidenceId,ex)); return; }
        if(completion!=null&&completion.approved()) {
            finalizer.finalizeSale(completion.addonId(), true, "✅ Pago del film protector confirmado.");
        }
    }

    private Claimed claim(Long id) {
        CrmWhatsappFilmEvidence e=evidence.findForUpdate(id).orElse(null); if(e==null)return null;
        if(e.getProcessingStatus()!=CrmWhatsappPaymentProcessingStatus.QUEUED)return null;
        if(e.getAttempts()>=3){e.setProcessingStatus(CrmWhatsappPaymentProcessingStatus.FAILED);e.setLastError("Máximo de intentos alcanzado");evidence.save(e);return null;}
        e.setProcessingStatus(CrmWhatsappPaymentProcessingStatus.PROCESSING);e.setAttempts(e.getAttempts()+1);evidence.save(e);
        return new Claimed(id,e.getFilmAddon().getAgentSession().getConnection().getIdConnection(),e.getStoragePath(),e.getMimeType());
    }

    private Completion complete(Long id, AgentPaymentReceiptExtraction x, String provider) {
        CrmWhatsappFilmEvidence e=evidence.findForUpdate(id).orElseThrow(); CrmWhatsappFilmAddon addon=addons
                .findForUpdate(e.getFilmAddon().getIdFilmAddon()).orElseThrow();
        e.setDetectedProvider(provider); e.setDetectedAmount(amount(x.amount())); e.setDetectedCurrency(x.currency());
        e.setOperationCode(clean(x.operationNumber())); e.setOperationAt(operationAt(x)); e.setRecipient(clean(x.recipient()));
        e.setConfidence(x.confidence()); e.setExtractedText(x.visibleText()); e.setExtractionJson(write(x));
        List<String> errors=validate(e,addon,x); e.setWarningsJson(write(errors));
        e.setProcessingStatus(CrmWhatsappPaymentProcessingStatus.COMPLETED); e.setLastError(null);
        if(!errors.isEmpty()) {
            e.setValidationStatus(CrmWhatsappPaymentEvidenceStatus.OBSERVADO); evidence.save(e);
            outbox.enqueueOperationalText(addon.getAgentSession().getIdAgentSession(),null,e.getMessage().getIdMessage(),
                    100_000+Math.toIntExact(id%500_000),"❌ No pude validar el pago del film: "
                            +errors.getFirst()+". Puedes enviar otro comprobante mientras quede tiempo.");
            return new Completion(addon.getIdFilmAddon(),false);
        }
        e.setValidationStatus(CrmWhatsappPaymentEvidenceStatus.VALIDADO); evidence.save(e);
        addon.setStatus(CrmWhatsappFilmStatus.CONFIRMED);addon.setPaidAt(LocalDateTime.now());addon.setResolvedAt(LocalDateTime.now());
        addon.setResolutionSource("RECEIPT_AI");addons.save(addon);
        jdbc.update("""
                INSERT INTO crm_whatsapp_film_payment
                  (id_film_addon,id_venta,id_film_evidence,amount,currency,provider,operation_code,paid_at)
                VALUES (?,?,?,?,?,?,?,?)
                ON DUPLICATE KEY UPDATE id_film_payment=id_film_payment
                """,addon.getIdFilmAddon(),addon.getVenta().getIdVenta(),e.getIdFilmEvidence(),AgentFilmService.PRICE,
                "PEN","YAPE",e.getOperationCode(),e.getOperationAt());
        CrmWhatsappFulfillment fulfillment=fulfillments.findForUpdateBySessionId(addon.getAgentSession().getIdAgentSession()).orElseThrow();
        fulfillment.setStatus(CrmWhatsappFulfillmentStatus.READY);fulfillments.save(fulfillment);
        CrmWhatsappAgentSession session=sessions.findForUpdate(addon.getAgentSession().getIdAgentSession()).orElseThrow();
        if(session.getState()==CrmWhatsappAgentState.ESPERANDO_PAGO_FILM){session.setState(stateMachine.transition(session.getState(),CrmWhatsappAgentEvent.PAGO_FILM_APROBADO));session.setLastEvent(CrmWhatsappAgentEvent.PAGO_FILM_APROBADO);session.setStateChangedAt(LocalDateTime.now());sessions.save(session);}
        return new Completion(addon.getIdFilmAddon(),true);
    }

    private List<String> validate(CrmWhatsappFilmEvidence e,CrmWhatsappFilmAddon addon,AgentPaymentReceiptExtraction x){
        java.util.ArrayList<String> errors=new java.util.ArrayList<>(); Integer company=addon.getConversation().getConnection().getEmpresa().getIdEmpresa();
        if(!x.paymentReceipt())errors.add("la imagen no parece ser un comprobante");
        if(e.getDetectedAmount()==null||e.getDetectedAmount().compareTo(AgentFilmService.PRICE)!=0)errors.add("el monto debe ser exactamente S/2.00");
        if(!"PEN".equalsIgnoreCase(clean(e.getDetectedCurrency())))errors.add("la moneda debe ser PEN");
        if(!"YAPE".equalsIgnoreCase(clean(x.paymentMethod())))errors.add("el proveedor debe ser Yape");
        if(clean(e.getOperationCode()).isBlank())errors.add("no se encontró el código de operación");
        if(e.getConfidence()==null||e.getConfidence()<config.forSession(addon.getAgentSession().getIdAgentSession()).receiptMinimumConfidence())errors.add("la imagen no se lee con suficiente claridad");
        if(e.getMessage().getCreatedAt()==null||addon.getExpiresAt()==null||e.getMessage().getCreatedAt().isAfter(addon.getExpiresAt()))errors.add("el comprobante llegó después del plazo de 1 hora");
        if(e.getOperationAt()==null||e.getOperationAt().isBefore(addon.getPaymentStartedAt().minusMinutes(15))||e.getOperationAt().isAfter(e.getMessage().getCreatedAt().plusMinutes(15)))errors.add("la fecha u hora no corresponde a esta solicitud");
        if(!mainEvidence.findOtherByHash(-1L,e.getSha256(),company,PageRequest.of(0,1)).isEmpty()||!evidence.findOtherByHash(e.getIdFilmEvidence(),e.getSha256(),company,PageRequest.of(0,1)).isEmpty())errors.add("el archivo del comprobante ya fue utilizado");
        if(!clean(e.getOperationCode()).isBlank()&&(!mainEvidence.findOtherByOperation(-1L,e.getOperationCode(),company,PageRequest.of(0,1)).isEmpty()||!evidence.findOtherByOperation(e.getIdFilmEvidence(),e.getOperationCode(),company,PageRequest.of(0,1)).isEmpty()))errors.add("el código de operación ya fue utilizado");
        return errors.stream().distinct().toList();
    }
    private void fail(Long id,RuntimeException ex){CrmWhatsappFilmEvidence e=evidence.findForUpdate(id).orElse(null);if(e==null)return;e.setLastError(ex.getMessage());if(e.getAttempts()<3){e.setProcessingStatus(CrmWhatsappPaymentProcessingStatus.QUEUED);e.setAvailableAt(LocalDateTime.now().plusSeconds((long)e.getAttempts()*5));}else{e.setProcessingStatus(CrmWhatsappPaymentProcessingStatus.FAILED);e.setValidationStatus(CrmWhatsappPaymentEvidenceStatus.OBSERVADO);}evidence.save(e);}
    private Long activeSessionId(Long conversationId){return sessions.findByConversation_IdConversationAndActiveSlotTrue(conversationId).map(CrmWhatsappAgentSession::getIdAgentSession).orElse(-1L);}
    private boolean supported(CrmWhatsappMessage m){String mime=clean(m.getMediaMimeType()).toLowerCase();return m.getMediaStoragePath()!=null&&(mime.equals("image/jpeg")||mime.equals("image/png")||mime.equals("image/webp")||mime.equals("application/pdf"));}
    private String extension(String mime){return switch(clean(mime).toLowerCase()){case "image/png"->".png";case "image/webp"->".webp";case "application/pdf"->".pdf";default->".jpg";};}
    private BigDecimal amount(String v){try{String n=clean(v).replaceAll("[^0-9,.-]","").replace(",",".");return n.isBlank()?null:new BigDecimal(n).setScale(2);}catch(Exception e){return null;}}
    private LocalDateTime operationAt(AgentPaymentReceiptExtraction x){try{LocalDate d=LocalDate.parse(clean(x.operationDate()),DateTimeFormatter.ISO_LOCAL_DATE);LocalTime t=LocalTime.parse(clean(x.operationTime()),DateTimeFormatter.ISO_LOCAL_TIME);return LocalDateTime.of(d,t);}catch(Exception e){return null;}}
    private String hash(byte[] b){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));}catch(Exception e){throw new IllegalStateException(e);}}
    private String write(Object o){try{return json.writeValueAsString(o);}catch(Exception e){return "[]";}}
    private String clean(String v){return v==null?"":v.trim();}
    private record Claimed(Long id,Long connectionId,String path,String mime){}
    private record Completion(Long addonId,boolean approved){}
}
