package com.sistemapos.sistematextil.services.agent.fulfillment;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sistemapos.sistematextil.model.Cliente;
import com.sistemapos.sistematextil.model.CrmWhatsappAgentEvent;
import com.sistemapos.sistematextil.model.CrmWhatsappAgentSession;
import com.sistemapos.sistematextil.model.CrmWhatsappAgentState;
import com.sistemapos.sistematextil.model.CrmWhatsappFulfillment;
import com.sistemapos.sistematextil.model.CrmWhatsappFulfillmentMethod;
import com.sistemapos.sistematextil.model.CrmWhatsappFulfillmentStatus;
import com.sistemapos.sistematextil.model.CrmWhatsappPaymentRequest;
import com.sistemapos.sistematextil.model.CrmWhatsappPaymentRequestStatus;
import com.sistemapos.sistematextil.model.CrmWhatsappPaymentReservationStatus;
import com.sistemapos.sistematextil.model.CrmWhatsappShalomProfile;
import com.sistemapos.sistematextil.model.ShalomAgency;
import com.sistemapos.sistematextil.model.Sucursal;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAgentSessionRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappFulfillmentRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappPaymentRequestRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappShalomProfileRepository;
import com.sistemapos.sistematextil.repositories.ShalomAgencyRepository;
import com.sistemapos.sistematextil.repositories.SucursalRepository;
import com.sistemapos.sistematextil.services.agent.CrmWhatsappAgentStateMachine;
import com.sistemapos.sistematextil.services.agent.ai.AgentShalomDataField;
import com.sistemapos.sistematextil.services.agent.cart.AgentCartStore;
import com.sistemapos.sistematextil.services.agent.cart.AgentCartStore.OperationRow;
import com.sistemapos.sistematextil.services.agent.fulfillment.ShalomAgencySearchService.AgencyView;
import com.sistemapos.sistematextil.services.agent.fulfillment.ShalomAgencySearchService.SearchResult;
import com.sistemapos.sistematextil.util.crm.CrmWhatsappPhoneUtils;

@Service
public class AgentFulfillmentService {
    private static final List<CrmWhatsappPaymentRequestStatus> APPROVED_PAYMENT_STATUSES = List.of(
            CrmWhatsappPaymentRequestStatus.UNDER_REVIEW, CrmWhatsappPaymentRequestStatus.READY_FOR_SALE);

    private final CrmWhatsappAgentSessionRepository sessions;
    private final CrmWhatsappPaymentRequestRepository payments;
    private final CrmWhatsappFulfillmentRepository fulfillments;
    private final SucursalRepository branches;
    private final ShalomAgencyRepository agencies;
    private final CrmWhatsappShalomProfileRepository profiles;
    private final ShalomAgencySearchService agencySearch;
    private final AgentCartStore operations;
    private final CrmWhatsappAgentStateMachine stateMachine;
    private final ObjectMapper mapper;
    private final Clock clock;

    @Autowired
    public AgentFulfillmentService(CrmWhatsappAgentSessionRepository sessions,
            CrmWhatsappPaymentRequestRepository payments, CrmWhatsappFulfillmentRepository fulfillments,
            SucursalRepository branches, ShalomAgencyRepository agencies,
            CrmWhatsappShalomProfileRepository profiles, ShalomAgencySearchService agencySearch,
            AgentCartStore operations, CrmWhatsappAgentStateMachine stateMachine) {
        this(sessions, payments, fulfillments, branches, agencies, profiles, agencySearch, operations,
                stateMachine, new ObjectMapper().findAndRegisterModules(), Clock.system(ZoneId.of("America/Lima")));
    }

    AgentFulfillmentService(CrmWhatsappAgentSessionRepository sessions,
            CrmWhatsappPaymentRequestRepository payments, CrmWhatsappFulfillmentRepository fulfillments,
            SucursalRepository branches, ShalomAgencyRepository agencies,
            CrmWhatsappShalomProfileRepository profiles, ShalomAgencySearchService agencySearch,
            AgentCartStore operations, CrmWhatsappAgentStateMachine stateMachine, ObjectMapper mapper, Clock clock) {
        this.sessions=sessions; this.payments=payments; this.fulfillments=fulfillments; this.branches=branches;
        this.agencies=agencies; this.profiles=profiles; this.agencySearch=agencySearch; this.operations=operations;
        this.stateMachine=stateMachine; this.mapper=mapper; this.clock=clock;
    }

    @Transactional(readOnly = true)
    public PickupBranchList listPickupBranches(Long sessionId) {
        CrmWhatsappAgentSession session=requireSession(sessionId,false);
        List<PickupBranch> result=branches.findByEmpresa_IdEmpresaAndDeletedAtIsNullAndEstadoOrderByIdSucursalAsc(
                companyId(session),"ACTIVO").stream().map(this::branchView).toList();
        return new PickupBranchList(result,result.isEmpty()?"No hay sucursales habilitadas para recojo":"Sucursales habilitadas para recojo");
    }

    @Transactional
    public FulfillmentResult selectPickup(Long sessionId,Integer branchId,String recipientName,String phone,String operationId){
        if(branchId==null)throw new IllegalArgumentException("La sucursal de recojo es obligatoria");
        return execute(sessionId,operationId,"seleccionar_recojo_tienda",branchId+":"+clean(recipientName)+":"+clean(phone),()->{
            Scope scope=lockDeliverySelection(sessionId);
            Sucursal branch=branches.findByIdSucursalAndDeletedAtIsNull(branchId)
                    .filter(item->Objects.equals(companyId(scope.session()),item.getEmpresa().getIdEmpresa()))
                    .filter(item->"ACTIVO".equalsIgnoreCase(item.getEstado()))
                    .orElseThrow(()->new IllegalArgumentException("La sucursal de recojo no pertenece a la empresa o no esta activa"));
            CrmWhatsappFulfillment f=getOrCreate(scope,operationId); f.setMethod(CrmWhatsappFulfillmentMethod.PICKUP);
            f.setStatus(CrmWhatsappFulfillmentStatus.READY); f.setPickupBranch(branch);
            f.setRecipientName(resolvePickupName(scope.session().getCliente(),recipientName));
            f.setPhone(resolvePickupPhone(scope.session().getCliente(),phone)); clearShalom(f); f.setConfirmedAt(now());
            f=fulfillments.save(f); apply(scope.session(),CrmWhatsappAgentEvent.RECOJO_TIENDA_SELECCIONADO); return result(f,scope.session());
        });
    }

    @Transactional
    public FulfillmentResult selectShalom(Long sessionId,String operationId){
        return execute(sessionId,operationId,"seleccionar_envio_shalom","SHALOM",()->{
            Scope scope=lockDeliverySelection(sessionId); CrmWhatsappFulfillment f=getOrCreate(scope,operationId);
            f.setMethod(CrmWhatsappFulfillmentMethod.SHALOM); f.setStatus(CrmWhatsappFulfillmentStatus.DRAFT);
            f.setPickupBranch(null); clearShalom(f); f.setDocumentType("DNI"); f.setConfirmedAt(null); f=fulfillments.save(f);
            if(scope.session().getCliente()!=null) {
                Optional<CrmWhatsappShalomProfile> saved=profiles.findByCliente_IdCliente(
                        scope.session().getCliente().getIdCliente());
                if(saved.isPresent()) {
                    CrmWhatsappShalomProfile profile=saved.get(); ShalomAgency agency=profile.getAgency();
                    if(agency!=null&&agency.isActive()&&agency.isDestinationEnabled()) {
                        f.setRecipientName(profile.getRecipientName()); f.setDocumentNumber(profile.getDocumentNumber());
                        f.setPhone(profile.getPhone()); bindAgency(f,agency);
                        f.setShalomCandidateIds(agency.getIdShalomAgency().toString()); f=fulfillments.save(f);
                    }
                }
            }
            apply(scope.session(),CrmWhatsappAgentEvent.ENVIO_SHALOM_SELECCIONADO); return result(f,scope.session());
        });
    }

    @Transactional(readOnly = true)
    public SavedProfileView savedProfile(Long sessionId){
        CrmWhatsappAgentSession session=requireSession(sessionId,false);
        if(session.getCliente()==null)return null;
        return profiles.findByCliente_IdCliente(session.getCliente().getIdCliente()).map(this::profileView)
                .filter(SavedProfileView::agencyAvailable).orElse(null);
    }

    @Transactional
    public FulfillmentResult saveShalomData(Long sessionId,String names,String dni,String phone,String agencyQuery,String operationId){
        String fingerprint=clean(names)+":"+clean(dni)+":"+clean(phone)+":"+clean(agencyQuery);
        return execute(sessionId,operationId,"guardar_datos_shalom",fingerprint,()->{
            CrmWhatsappAgentSession session=requireSession(sessionId,true);
            if(session.getState()==CrmWhatsappAgentState.ESPERANDO_CONFIRMACION_AGENCIA_SHALOM) {
                apply(session,CrmWhatsappAgentEvent.CORRECCION_DATOS_SHALOM_SOLICITADA);
            }
            if(session.getState()!=CrmWhatsappAgentState.ESPERANDO_DATOS_SHALOM)
                throw new IllegalStateException("La sesion no esta esperando los datos de Shalom");
            requireApprovedPayment(session);
            CrmWhatsappFulfillment f=requireShalom(sessionId);
            ShalomData data=validate(firstPresent(names,f.getRecipientName()),firstPresent(dni,f.getDocumentNumber()),
                    firstPresent(phone,f.getPhone()),agencyQuery);
            SearchResult search=agencySearch.search(data.agencyQuery());
            f.setRecipientName(data.names()); f.setDocumentType("DNI"); f.setDocumentNumber(data.dni()); f.setPhone(data.phone());
            f.setShalomAgencyReference(null); f.setShalomAgency(null); f.setShalomDestination(null); f.setShalomAgencyAddress(null);
            f.setShalomCandidateIds(joinIds(search.candidates())); f.setStatus(CrmWhatsappFulfillmentStatus.AWAITING_AGENCY_CONFIRMATION);
            f.setConfirmedAt(null); f=fulfillments.save(f);
            if(!search.candidates().isEmpty()) apply(session,CrmWhatsappAgentEvent.DATOS_SHALOM_COMPLETADOS);
            return result(f,session,search.candidates());
        });
    }

    /**
     * Guarda de forma acumulativa los datos de Shalom recibidos en uno o varios
     * mensajes. Un campo inválido no revierte los demás campos válidos del mismo
     * turno y la agencia encontrada se conserva hasta completar los datos
     * personales.
     */
    @Transactional
    public ShalomDataProgress savePartialShalomData(Long sessionId,String names,String dni,String phone,String agencyQuery){
        CrmWhatsappAgentSession session=requireSession(sessionId,true);
        if(session.getState()!=CrmWhatsappAgentState.ESPERANDO_DATOS_SHALOM)
            throw new IllegalStateException("La sesion no esta esperando los datos de Shalom");
        requireApprovedPayment(session);
        CrmWhatsappFulfillment f=requireShalom(sessionId);
        List<String> validationErrors=new ArrayList<>();

        if(!clean(names).isBlank()) {
            try { f.setRecipientName(validateName(names)); }
            catch(IllegalArgumentException error) { validationErrors.add(error.getMessage()); }
        }
        if(!clean(dni).isBlank()) {
            try { f.setDocumentNumber(validateDni(dni)); }
            catch(IllegalArgumentException error) { validationErrors.add(error.getMessage()); }
        }
        if(!clean(phone).isBlank()) {
            try { f.setPhone(normalizePhone(phone)); }
            catch(IllegalArgumentException error) { validationErrors.add(error.getMessage()); }
        }

        boolean agencySearchAttempted=!clean(agencyQuery).isBlank();
        List<AgencyView> candidates=existingCandidateViews(f);
        if(agencySearchAttempted) {
            f.setShalomAgencyReference(null);f.setShalomAgency(null);f.setShalomDestination(null);
            f.setShalomAgencyAddress(null);f.setShalomCandidateIds(null);
            try {
                String query=required(agencyQuery,"La agencia Shalom",2,300);
                candidates=agencySearch.search(query).candidates();
                f.setShalomCandidateIds(joinIds(candidates));
            } catch(IllegalArgumentException error) {
                candidates=List.of();validationErrors.add(error.getMessage());
            }
        }

        boolean personalComplete=!clean(f.getRecipientName()).isBlank()
                && clean(f.getDocumentNumber()).matches("\\d{8}")
                && !CrmWhatsappPhoneUtils.normalizePeruvianMobile(f.getPhone()).isBlank();
        boolean agencyReady=!candidates.isEmpty();
        f.setDocumentType("DNI");f.setConfirmedAt(null);
        f.setStatus(personalComplete&&agencyReady?CrmWhatsappFulfillmentStatus.AWAITING_AGENCY_CONFIRMATION
                :CrmWhatsappFulfillmentStatus.DRAFT);
        f=fulfillments.save(f);
        if(personalComplete&&agencyReady)apply(session,CrmWhatsappAgentEvent.DATOS_SHALOM_COMPLETADOS);

        List<String> missing=new ArrayList<>();
        if(clean(f.getRecipientName()).isBlank())missing.add("nombres y apellidos");
        if(!clean(f.getDocumentNumber()).matches("\\d{8}"))missing.add("DNI");
        if(CrmWhatsappPhoneUtils.normalizePeruvianMobile(f.getPhone()).isBlank())missing.add("celular");
        if(!agencyReady)missing.add("agencia de Shalom");
        return new ShalomDataProgress(result(f,session,candidates),List.copyOf(missing),
                List.copyOf(validationErrors),agencySearchAttempted&&!agencyReady);
    }

    @Transactional
    public FulfillmentResult editShalomData(Long sessionId,List<AgentShalomDataField> requestedFields,
            String names,String dni,String phone,String agencyQuery,String operationId){
        List<AgentShalomDataField> fields=expandFields(requestedFields);
        if(fields.isEmpty())throw new IllegalArgumentException("Indica que dato de Shalom deseas cambiar");
        String fingerprint=fields+":"+clean(names)+":"+clean(dni)+":"+clean(phone)+":"+clean(agencyQuery);
        return execute(sessionId,operationId,"editar_datos_shalom",fingerprint,()->{
            CrmWhatsappAgentSession session=requireSession(sessionId,true);
            if(session.getState()==CrmWhatsappAgentState.ESPERANDO_CONFIRMACION_AGENCIA_SHALOM)
                apply(session,CrmWhatsappAgentEvent.CORRECCION_DATOS_SHALOM_SOLICITADA);
            if(session.getState()!=CrmWhatsappAgentState.ESPERANDO_DATOS_SHALOM)
                throw new IllegalStateException("La sesion no esta esperando datos de Shalom");
            requireApprovedPayment(session); CrmWhatsappFulfillment f=requireShalom(sessionId);

            if(fields.contains(AgentShalomDataField.NAME))
                f.setRecipientName(clean(names).isBlank()?null:validateName(names));
            if(fields.contains(AgentShalomDataField.DNI))
                f.setDocumentNumber(clean(dni).isBlank()?null:validateDni(dni));
            if(fields.contains(AgentShalomDataField.PHONE))
                f.setPhone(clean(phone).isBlank()?null:normalizePhone(phone));

            List<AgencyView> candidates=List.of();
            if(fields.contains(AgentShalomDataField.AGENCY)) {
                f.setShalomAgencyReference(null); f.setShalomAgency(null); f.setShalomDestination(null);
                f.setShalomAgencyAddress(null); f.setShalomCandidateIds(null);
                if(!clean(agencyQuery).isBlank()) {
                    SearchResult search=agencySearch.search(agencyQuery); candidates=search.candidates();
                    f.setShalomCandidateIds(joinIds(candidates));
                }
            } else if(f.getShalomAgencyReference()!=null) {
                try {
                    candidates=List.of(agencySearch.get(f.getShalomAgencyReference().getIdShalomAgency()));
                    f.setShalomCandidateIds(joinIds(candidates));
                } catch(IllegalArgumentException unavailable) {
                    f.setShalomAgencyReference(null);f.setShalomAgency(null);f.setShalomDestination(null);
                    f.setShalomAgencyAddress(null);f.setShalomCandidateIds(null);
                }
            }

            boolean personalComplete=!clean(f.getRecipientName()).isBlank()
                    && clean(f.getDocumentNumber()).matches("\\d{8}")
                    && !CrmWhatsappPhoneUtils.normalizePeruvianMobile(f.getPhone()).isBlank();
            boolean agencyReady=!candidates.isEmpty();
            f.setDocumentType("DNI"); f.setConfirmedAt(null);
            f.setStatus(personalComplete&&agencyReady?CrmWhatsappFulfillmentStatus.AWAITING_AGENCY_CONFIRMATION
                    :CrmWhatsappFulfillmentStatus.DRAFT);
            f=fulfillments.save(f);
            if(personalComplete&&agencyReady)apply(session,CrmWhatsappAgentEvent.DATOS_SHALOM_COMPLETADOS);
            return result(f,session,candidates);
        });
    }

    @Transactional
    public FulfillmentResult confirmShalomAgency(Long sessionId,Long agencyId,String operationId){
        if(agencyId==null)throw new IllegalArgumentException("La agencia Shalom es obligatoria");
        return execute(sessionId,operationId,"confirmar_agencia_shalom",agencyId.toString(),()->{
            CrmWhatsappAgentSession session=requireSession(sessionId,true);
            if(session.getState()!=CrmWhatsappAgentState.ESPERANDO_CONFIRMACION_AGENCIA_SHALOM)
                throw new IllegalStateException("La sesion no esta esperando confirmar una agencia Shalom");
            requireApprovedPayment(session); CrmWhatsappFulfillment f=requireShalom(sessionId);
            if(!candidateIds(f).contains(agencyId))throw new IllegalArgumentException("La agencia no fue ofrecida para esta solicitud");
            ShalomAgency agency=requireAvailableAgency(agencyId); bindAgency(f,agency); f.setStatus(CrmWhatsappFulfillmentStatus.READY);
            f.setConfirmedAt(now()); f.setShalomCandidateIds(null); f=fulfillments.save(f); saveProfile(session,f,agency);
            apply(session,CrmWhatsappAgentEvent.AGENCIA_SHALOM_CONFIRMADA); return result(f,session);
        });
    }

    @Transactional
    public FulfillmentResult confirmShalomAgency(Long sessionId,String agencyReference,String operationId){
        CrmWhatsappFulfillment f=fulfillments.findByAgentSession_IdAgentSession(sessionId)
                .orElseThrow(()->new IllegalStateException("No existen datos Shalom pendientes"));
        List<Long> offered=candidateIds(f);
        if(offered.isEmpty())throw new IllegalStateException("No hay agencias Shalom ofrecidas para confirmar");
        Long selected=null;
        String reference=ShalomAgencySyncService.normalize(agencyReference);
        if(offered.size()==1&&(reference.isBlank()||reference.equals("si")||reference.equals("esa")))selected=offered.getFirst();
        if(selected==null){
            List<ShalomAgency> matches=offered.stream().map(agencies::findById).flatMap(Optional::stream)
                    .filter(a->a.isActive()&&a.isDestinationEnabled())
                    .filter(a->{String haystack=ShalomAgencySyncService.normalize(a.getName()+" "+Objects.toString(a.getAddress(),""));
                        return !reference.isBlank()&&(haystack.contains(reference)||reference.contains(ShalomAgencySyncService.normalize(a.getName())));})
                    .toList();
            if(matches.size()==1)selected=matches.getFirst().getIdShalomAgency();
        }
        if(selected==null)throw new IllegalArgumentException("Indica cual de las agencias ofrecidas deseas confirmar");
        return confirmShalomAgency(sessionId,selected,operationId);
    }

    @Transactional
    public FulfillmentResult reuseSavedProfile(Long sessionId,String operationId){
        return execute(sessionId,operationId,"reutilizar_datos_shalom","PROFILE",()->{
            CrmWhatsappAgentSession session=requireSession(sessionId,true);
            if(session.getState()!=CrmWhatsappAgentState.ESPERANDO_DATOS_SHALOM)
                throw new IllegalStateException("La sesion no esta esperando datos de Shalom");
            requireApprovedPayment(session);
            if(session.getCliente()==null)throw new IllegalStateException("La sesion no tiene cliente relacionado");
            CrmWhatsappShalomProfile profile=profiles.findByCliente_IdCliente(session.getCliente().getIdCliente())
                    .orElseThrow(()->new IllegalArgumentException("No existen datos Shalom guardados"));
            ShalomAgency agency=requireAvailableAgency(profile.getAgency().getIdShalomAgency());
            CrmWhatsappFulfillment f=requireShalom(sessionId); f.setRecipientName(profile.getRecipientName());
            f.setDocumentType("DNI"); f.setDocumentNumber(profile.getDocumentNumber()); f.setPhone(profile.getPhone());
            bindAgency(f,agency); f.setStatus(CrmWhatsappFulfillmentStatus.READY); f.setConfirmedAt(now()); f.setShalomCandidateIds(null);
            f=fulfillments.save(f); profile.setConfirmedAt(now()); profiles.save(profile);
            apply(session,CrmWhatsappAgentEvent.PERFIL_SHALOM_REUTILIZADO); return result(f,session);
        });
    }

    @Transactional(readOnly = true)
    public SearchResult searchAgencies(String query){return agencySearch.search(query);}

    @Transactional(readOnly = true)
    public AgencyView getAgency(Long agencyId){return agencySearch.get(agencyId);}

    @Transactional(readOnly = true)
    public boolean hasCompletePendingShalomData(Long sessionId) {
        return fulfillments.findByAgentSession_IdAgentSession(sessionId)
                .filter(f -> !clean(f.getRecipientName()).isBlank()
                        && clean(f.getDocumentNumber()).matches("\\d{8}")
                        && !CrmWhatsappPhoneUtils.normalizePeruvianMobile(f.getPhone()).isBlank()).isPresent();
    }

    private void saveProfile(CrmWhatsappAgentSession session,CrmWhatsappFulfillment f,ShalomAgency agency){
        Cliente customer=session.getCliente(); if(customer==null)throw new IllegalStateException("La sesion no tiene cliente");
        CrmWhatsappShalomProfile p=profiles.findByCliente_IdCliente(customer.getIdCliente()).orElseGet(CrmWhatsappShalomProfile::new);
        p.setCliente(customer); p.setAgency(agency); p.setRecipientName(f.getRecipientName()); p.setDocumentNumber(f.getDocumentNumber());
        p.setPhone(f.getPhone()); p.setAgencyNameSnapshot(agency.getName()); p.setAgencyAddressSnapshot(agency.getAddress());
        p.setConfirmedAt(now()); profiles.save(p);
    }
    private SavedProfileView profileView(CrmWhatsappShalomProfile p){ShalomAgency a=p.getAgency();return new SavedProfileView(
            p.getRecipientName(),p.getDocumentNumber(),p.getPhone(),a.getIdShalomAgency(),a.getExternalId(),
            a.getName(),a.getAddress(),a.isActive()&&a.isDestinationEnabled(),p.getConfirmedAt());}
    private void bindAgency(CrmWhatsappFulfillment f,ShalomAgency a){f.setShalomAgencyReference(a);f.setShalomAgency(a.getName());
        f.setShalomDestination(location(a));f.setShalomAgencyAddress(a.getAddress());}
    private String location(ShalomAgency a){return java.util.stream.Stream.of(a.getDepartment(),a.getProvince(),a.getPlace())
            .filter(v->v!=null&&!v.isBlank()).distinct().reduce((x,y)->x+" / "+y).orElse(null);}
    private ShalomAgency requireAvailableAgency(Long id){return agencies.findById(id).filter(ShalomAgency::isActive)
            .filter(ShalomAgency::isDestinationEnabled).orElseThrow(()->new IllegalArgumentException("La agencia Shalom ya no esta disponible"));}
    private CrmWhatsappFulfillment requireShalom(Long sessionId){return fulfillments.findForUpdateBySessionId(sessionId)
            .filter(f->f.getMethod()==CrmWhatsappFulfillmentMethod.SHALOM)
            .orElseThrow(()->new IllegalStateException("Primero selecciona el envio por Shalom"));}
    private String joinIds(List<AgencyView> values){return values.stream().map(v->v.agencyId().toString()).reduce((a,b)->a+","+b).orElse("");}
    private List<Long> candidateIds(CrmWhatsappFulfillment f){if(f.getShalomCandidateIds()==null||f.getShalomCandidateIds().isBlank())return List.of();
        return List.of(f.getShalomCandidateIds().split(",")).stream().map(String::trim).filter(v->v.matches("\\d+"))
                .map(Long::valueOf).toList();}
    private List<AgencyView> existingCandidateViews(CrmWhatsappFulfillment f){
        List<AgencyView> result=new ArrayList<>();
        for(Long id:candidateIds(f))try{result.add(agencySearch.get(id));}catch(IllegalArgumentException ignored){ }
        if(result.size()!=candidateIds(f).size())f.setShalomCandidateIds(joinIds(result));
        return List.copyOf(result);
    }

    private Scope lockDeliverySelection(Long id){CrmWhatsappAgentSession s=requireSession(id,true);
        if(s.getState()!=CrmWhatsappAgentState.ESPERANDO_METODO_ENTREGA)throw new IllegalStateException("La sesion no esta esperando el metodo de entrega");
        return new Scope(s,requireApprovedPayment(s));}
    private CrmWhatsappPaymentRequest requireApprovedPayment(CrmWhatsappAgentSession s){CrmWhatsappPaymentRequest r=payments
            .findFirstByAgentSession_IdAgentSessionAndStatusInOrderByCreatedAtDesc(s.getIdAgentSession(),APPROVED_PAYMENT_STATUSES)
            .flatMap(x->payments.findForUpdate(x.getIdPaymentRequest())).orElseThrow(()->new IllegalStateException("No existe un comprobante aprobado para esta sesion"));
        if(r.getReservationStatus()!=CrmWhatsappPaymentReservationStatus.ACTIVE||r.getEvidenceAcceptedAt()==null
                ||r.getReviewExpiresAt()==null||!r.getReviewExpiresAt().isAfter(now()))throw new IllegalStateException("La reserva aprobada ya no esta vigente");return r;}
    private CrmWhatsappFulfillment getOrCreate(Scope s,String op){return fulfillments.findForUpdateBySessionId(s.session().getIdAgentSession()).orElseGet(()->{
        CrmWhatsappFulfillment f=new CrmWhatsappFulfillment();f.setAgentSession(s.session());f.setConversation(s.session().getConversation());
        f.setPaymentRequest(s.payment());f.setIdempotencyKey(sha256("fulfillment:"+s.session().getIdAgentSession()+":"+op));return f;});}
    private CrmWhatsappAgentSession requireSession(Long id,boolean lock){if(id==null)throw new IllegalArgumentException("La sesion es obligatoria");
        CrmWhatsappAgentSession s=(lock?sessions.findForUpdate(id):sessions.findById(id)).orElseThrow(()->new IllegalArgumentException("La sesion del agente no existe"));
        if(!Boolean.TRUE.equals(s.getActiveSlot()))throw new IllegalStateException("La sesion del agente ya no esta activa");companyId(s);return s;}
    private Integer companyId(CrmWhatsappAgentSession s){if(s.getConnection()==null||s.getConnection().getEmpresa()==null)return fail("La conexion WhatsApp no tiene empresa configurada");return s.getConnection().getEmpresa().getIdEmpresa();}
    private <T>T fail(String message){throw new IllegalStateException(message);}
    private ShalomData validate(String names,String dni,String phone,String query){String n=validateName(names);
        String d=validateDni(dni);String p=normalizePhone(phone);
        String q=required(query,"La agencia Shalom",2,300);return new ShalomData(n,d,p,q);}
    private String validateName(String value){String name=required(value,"Los nombres y apellidos",3,180);
        if(name.split("\\s+").length<2)throw new IllegalArgumentException("Ingresa nombres y apellidos completos");return name;}
    private String validateDni(String value){String dni=clean(value).replaceAll("\\D","");
        if(!dni.matches("\\d{8}"))throw new IllegalArgumentException("El DNI debe tener 8 digitos");return dni;}
    private List<AgentShalomDataField> expandFields(List<AgentShalomDataField> requested){
        if(requested==null||requested.isEmpty())return List.of();
        if(requested.contains(AgentShalomDataField.ALL))return List.of(AgentShalomDataField.NAME,
                AgentShalomDataField.DNI,AgentShalomDataField.PHONE,AgentShalomDataField.AGENCY);
        return requested.stream().filter(Objects::nonNull).distinct().toList();}
    private String resolvePickupName(Cliente c,String supplied){String v=clean(supplied);if(v.isBlank()&&c!=null)v=clean(c.getNombres());return required(v,"El nombre para recojo",2,180);}
    private String resolvePickupPhone(Cliente c,String supplied){String v=clean(supplied);if(v.isBlank()&&c!=null)v=clean(c.getTelefono());return normalizePhone(v);}
    private String normalizePhone(String v){String p=CrmWhatsappPhoneUtils.normalizePeruvianMobile(v);if(p.isBlank())throw new IllegalArgumentException("El celular es incorrecto. Ingresa un numero celular peruano valido de exactamente 9 digitos");return p;}
    private String required(String v,String label,int min,int max){String s=clean(v).replaceAll("\\s+"," ");if(s.length()<min)throw new IllegalArgumentException(label+" es obligatorio");if(s.length()>max)throw new IllegalArgumentException(label+" supera el maximo permitido");return s;}
    private String firstPresent(String supplied,String stored){return clean(supplied).isBlank()?stored:supplied;}
    private void clearShalom(CrmWhatsappFulfillment f){f.setDocumentType(null);f.setDocumentNumber(null);f.setRecipientName(null);f.setPhone(null);
        f.setShalomAgencyReference(null);f.setShalomAgency(null);f.setShalomDestination(null);f.setShalomAgencyAddress(null);f.setShalomCandidateIds(null);}
    private PickupBranch branchView(Sucursal b){return new PickupBranch(b.getIdSucursal(),b.getNombre(),b.getDireccion(),b.getCiudad(),b.getTelefono());}
    private FulfillmentResult result(CrmWhatsappFulfillment f,CrmWhatsappAgentSession s){return result(f,s,List.of());}
    private FulfillmentResult result(CrmWhatsappFulfillment f,CrmWhatsappAgentSession s,List<AgencyView> candidates){Sucursal b=f.getPickupBranch();
        return new FulfillmentResult(s.getIdAgentSession(),f.getIdFulfillment(),f.getMethod().name(),f.getStatus().name(),
                f.getRecipientName(),f.getDocumentType(),f.getDocumentNumber(),f.getPhone(),b==null?null:branchView(b),
                f.getShalomAgencyReference()==null?null:f.getShalomAgencyReference().getIdShalomAgency(),f.getShalomAgency(),
                f.getShalomDestination(),f.getShalomAgencyAddress(),candidates,s.getState().name(),savedProfile(s.getIdAgentSession()));}
    private void apply(CrmWhatsappAgentSession s,CrmWhatsappAgentEvent e){LocalDateTime n=now();s.setState(stateMachine.transition(s.getState(),e));
        s.setLastEvent(e);s.setLastActivityAt(n);s.setStateChangedAt(n);sessions.save(s);}
    private FulfillmentResult execute(Long sid,String op,String tool,String request,Supplier<FulfillmentResult> action){String key=operationKey(sid,op),fp=sha256(tool+":"+request);
        Optional<OperationRow> old=operations.findOperation(key);if(old.isPresent())return replay(old.get(),tool,fp);
        if(!operations.claimOperation(sid,key,tool,fp,now()))return replay(operations.findOperation(key).orElseThrow(),tool,fp);
        FulfillmentResult r=action.get();operations.completeOperation(key,write(r),now());return r;}
    private FulfillmentResult replay(OperationRow o,String tool,String fp){if(!tool.equals(o.toolName())||!fp.equals(o.requestFingerprint()))throw new IllegalStateException("El identificador de operacion ya fue usado con otra solicitud");
        if(!"COMPLETED".equals(o.status())||o.responseJson()==null)throw new IllegalStateException("La operacion de entrega aun esta en curso");try{return mapper.readValue(o.responseJson(),FulfillmentResult.class);}catch(JsonProcessingException e){throw new IllegalStateException("No se pudo recuperar la operacion de entrega",e);}}
    private String operationKey(Long sid,String op){if(sid==null||op==null||op.isBlank())throw new IllegalStateException("Falta el identificador seguro de la operacion del agente");return sha256("fulfillment-operation:"+sid+":"+op.trim());}
    private String sha256(String v){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private String write(Object v){try{return mapper.writeValueAsString(v);}catch(Exception e){throw new IllegalStateException("No se pudo serializar la modalidad de entrega",e);}}
    private LocalDateTime now(){return LocalDateTime.now(clock);} private String clean(String v){return v==null?"":v.trim();}

    private record Scope(CrmWhatsappAgentSession session,CrmWhatsappPaymentRequest payment){}
    private record ShalomData(String names,String dni,String phone,String agencyQuery){}
    public record PickupBranch(Integer branchId,String name,String address,String city,String phone){}
    public record PickupBranchList(List<PickupBranch> branches,String message){}
    public record SavedProfileView(String recipientName,String documentNumber,String phone,Long agencyId,Integer externalAgencyId,
            String agencyName,String agencyAddress,boolean agencyAvailable,LocalDateTime confirmedAt){}
    public record FulfillmentResult(Long sessionId,Long fulfillmentId,String method,String status,String recipientName,
            String documentType,String documentNumber,String phone,PickupBranch pickupBranch,Long shalomAgencyId,
            String shalomAgency,String shalomDestination,String shalomAgencyAddress,List<AgencyView> agencyCandidates,
            String agentState,SavedProfileView savedProfile){}
    public record ShalomDataProgress(FulfillmentResult fulfillment,List<String> missingFields,
            List<String> validationErrors,boolean agencyNotFound){
        public ShalomDataProgress {
            missingFields=missingFields==null?List.of():List.copyOf(missingFields);
            validationErrors=validationErrors==null?List.of():List.copyOf(validationErrors);
        }
    }
}
