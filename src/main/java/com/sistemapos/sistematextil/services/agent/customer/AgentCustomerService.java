package com.sistemapos.sistematextil.services.agent.customer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
import com.sistemapos.sistematextil.model.CrmWhatsappConversation;
import com.sistemapos.sistematextil.model.Empresa;
import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.repositories.ClienteRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAgentSessionRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappConversationRepository;
import com.sistemapos.sistematextil.repositories.EmpresaRepository;
import com.sistemapos.sistematextil.repositories.UsuarioRepository;
import com.sistemapos.sistematextil.services.agent.CrmWhatsappAgentStateMachine;
import com.sistemapos.sistematextil.services.agent.customer.AgentCustomerOperationStore.OperationRow;
import com.sistemapos.sistematextil.util.cliente.TipoDocumento;
import com.sistemapos.sistematextil.util.crm.CrmWhatsappPhoneUtils;

@Service
public class AgentCustomerService {
    private static final String QUICK_CUSTOMER_PREFIX = "CLIENTE ";
    private static final String AGENT_USER_EMAIL = "ia-kiments@system.local";

    private final CrmWhatsappAgentSessionRepository sessionRepository;
    private final CrmWhatsappConversationRepository conversationRepository;
    private final ClienteRepository customerRepository;
    private final EmpresaRepository companyRepository;
    private final UsuarioRepository userRepository;
    private final CrmWhatsappAgentStateMachine stateMachine;
    private final AgentCustomerOperationStore operationStore;
    private final ObjectMapper objectMapper;

    @Autowired
    public AgentCustomerService(
            CrmWhatsappAgentSessionRepository sessionRepository,
            CrmWhatsappConversationRepository conversationRepository,
            ClienteRepository customerRepository,
            EmpresaRepository companyRepository,
            UsuarioRepository userRepository,
            CrmWhatsappAgentStateMachine stateMachine,
            AgentCustomerOperationStore operationStore) {
        this(sessionRepository, conversationRepository, customerRepository, companyRepository, userRepository,
                stateMachine, operationStore, new ObjectMapper().findAndRegisterModules());
    }

    AgentCustomerService(
            CrmWhatsappAgentSessionRepository sessionRepository,
            CrmWhatsappConversationRepository conversationRepository,
            ClienteRepository customerRepository,
            EmpresaRepository companyRepository,
            UsuarioRepository userRepository,
            CrmWhatsappAgentStateMachine stateMachine,
            AgentCustomerOperationStore operationStore,
            ObjectMapper objectMapper) {
        this.sessionRepository = sessionRepository;
        this.conversationRepository = conversationRepository;
        this.customerRepository = customerRepository;
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
        this.stateMachine = stateMachine;
        this.operationStore = operationStore;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public CustomerSearchResult search(Long sessionId, String phone, TipoDocumento documentType,
            String documentNumber) {
        CrmWhatsappAgentSession session = requireSession(sessionId, false);
        Integer companyId = companyId(session);
        String normalizedPhone = optionalPhone(phone);
        DocumentIdentity document = normalizeDocument(documentType, documentNumber, true);
        if (normalizedPhone == null && document == null) {
            throw new IllegalArgumentException("Indica un celular o un documento para buscar al cliente");
        }

        List<Cliente> matches = findMatches(companyId, normalizedPhone, document);
        List<CustomerView> views = matches.stream().map(customer -> view(customer, session)).toList();
        if (views.isEmpty()) {
            return new CustomerSearchResult("NOT_FOUND", views,
                    "No existe un cliente con esos datos en la empresa de esta conversacion");
        }
        if (views.size() > 1) {
            return new CustomerSearchResult("CONFLICT", views,
                    "El celular y el documento pertenecen a clientes diferentes; no se relaciono ninguno");
        }
        return new CustomerSearchResult("FOUND", views, "Cliente encontrado");
    }

    @Transactional
    public CustomerResult register(Long sessionId, String names, String phone, TipoDocumento documentType,
            String documentNumber, String operationId) {
        return registerAtStage(sessionId,names,phone,documentType,documentNumber,operationId,false);
    }

    @Transactional
    public CustomerResult registerFromShalom(Long sessionId,String names,String phone,TipoDocumento documentType,
            String documentNumber,String operationId) {
        return registerAtStage(sessionId,names,phone,documentType,documentNumber,operationId,true);
    }

    private CustomerResult registerAtStage(Long sessionId,String names,String phone,TipoDocumento documentType,
            String documentNumber,String operationId,boolean shalomStage) {
        String normalizedNames = requireNames(names);
        String normalizedPhone = requirePhone(phone);
        DocumentIdentity document = normalizeDocument(documentType, documentNumber, false);
        String request = normalizedNames + ":" + normalizedPhone + ":" + documentKey(document);
        String tool=shalomStage?"registrar_cliente_desde_shalom":"registrar_cliente";
        return execute(sessionId, operationId, tool, request, () -> {
            LockedScope scope = lockCustomerStage(sessionId,shalomStage);
            List<Cliente> matches = findMatches(scope.company().getIdEmpresa(), normalizedPhone, document);
            if (matches.size() > 1) {
                throw new IllegalStateException(
                        "El celular y el documento pertenecen a clientes diferentes; se requiere revision");
            }

            Cliente customer;
            String outcome;
            if (matches.isEmpty()) {
                customer = new Cliente();
                customer.setEmpresa(scope.company());
                customer.setUsuarioCreacion(requireActor(scope.session()));
                customer.setNombres(normalizedNames);
                customer.setTelefono(normalizedPhone);
                customer.setTipoDocumento(document == null ? TipoDocumento.SIN_DOC : document.type());
                customer.setNroDocumento(document == null ? null : document.number());
                customer.setEstado("ACTIVO");
                customer = customerRepository.saveAndFlush(customer);
                outcome = "CREATED";
            } else {
                customer = matches.getFirst();
                fillMissing(customer, normalizedNames, normalizedPhone, document);
                customer = customerRepository.saveAndFlush(customer);
                outcome = "REUSED";
            }
            linkLocked(scope, customer);
            return result(outcome, customer, scope.session(), scope.conversation());
        });
    }

    @Transactional
    public CustomerResult completeMissing(Long sessionId, String names, String phone, TipoDocumento documentType,
            String documentNumber, String operationId) {
        String normalizedNames = optionalNames(names);
        String normalizedPhone = optionalPhone(phone);
        DocumentIdentity document = normalizeDocument(documentType, documentNumber, true);
        if (normalizedNames == null && normalizedPhone == null && document == null) {
            throw new IllegalArgumentException("Indica al menos un dato faltante para completar");
        }
        String request = Objects.toString(normalizedNames, "") + ":" + Objects.toString(normalizedPhone, "")
                + ":" + documentKey(document);
        return execute(sessionId, operationId, "actualizar_datos_cliente", request, () -> {
            LockedScope scope = lockCustomerStage(sessionId);
            Cliente customer = linkedCustomer(scope);
            ensureIdentityAvailable(scope.company().getIdEmpresa(), customer, normalizedPhone, document);
            fillMissing(customer, normalizedNames, normalizedPhone, document);
            customer = customerRepository.saveAndFlush(customer);
            linkLocked(scope, customer);
            return result("UPDATED", customer, scope.session(), scope.conversation());
        });
    }

    @Transactional
    public CustomerResult link(Long sessionId, Integer customerId, String operationId) {
        if (customerId == null) throw new IllegalArgumentException("El cliente es obligatorio");
        return execute(sessionId, operationId, "relacionar_cliente_conversacion", customerId.toString(), () -> {
            LockedScope scope = lockCustomerStage(sessionId);
            Cliente customer = customerRepository
                    .findByIdClienteAndDeletedAtIsNullAndEmpresa_IdEmpresa(
                            customerId, scope.company().getIdEmpresa())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "El cliente no existe en la empresa de esta conversacion"));
            linkLocked(scope, customer);
            return result("LINKED", customer, scope.session(), scope.conversation());
        });
    }

    private LockedScope lockCustomerStage(Long sessionId) {
        return lockCustomerStage(sessionId,false);
    }

    private LockedScope lockCustomerStage(Long sessionId,boolean shalomStage) {
        CrmWhatsappAgentSession session = requireSession(sessionId, true);
        boolean validState=session.getState()==CrmWhatsappAgentState.ESPERANDO_DATOS_CLIENTE
                ||shalomStage&&(session.getState()==CrmWhatsappAgentState.ESPERANDO_DATOS_SHALOM
                ||session.getState()==CrmWhatsappAgentState.ESPERANDO_CONFIRMACION_AGENCIA_SHALOM);
        if (!validState) {
            throw new IllegalStateException("La sesion no esta en la etapa de datos del cliente");
        }
        Integer companyId = companyId(session);
        Empresa company = companyRepository.findForUpdate(companyId)
                .orElseThrow(() -> new IllegalStateException("La empresa de la conexion ya no existe"));
        Long conversationId = session.getConversation() == null
                ? null : session.getConversation().getIdConversation();
        if (conversationId == null) throw new IllegalStateException("La sesion no tiene conversacion");
        CrmWhatsappConversation conversation = conversationRepository.findForUpdateById(conversationId)
                .orElseThrow(() -> new IllegalStateException("La conversacion ya no existe"));
        return new LockedScope(session, conversation, company);
    }

    private CrmWhatsappAgentSession requireSession(Long sessionId, boolean lock) {
        if (sessionId == null) throw new IllegalArgumentException("La sesion es obligatoria");
        CrmWhatsappAgentSession session = (lock
                ? sessionRepository.findForUpdate(sessionId)
                : sessionRepository.findById(sessionId))
                .orElseThrow(() -> new IllegalArgumentException("La sesion del agente no existe"));
        if (!Boolean.TRUE.equals(session.getActiveSlot())) {
            throw new IllegalStateException("La sesion del agente ya no esta activa");
        }
        companyId(session);
        return session;
    }

    private Integer companyId(CrmWhatsappAgentSession session) {
        if (session.getConnection() == null || session.getConnection().getEmpresa() == null
                || session.getConnection().getEmpresa().getIdEmpresa() == null) {
            throw new IllegalStateException("La conexion WhatsApp no tiene empresa configurada");
        }
        return session.getConnection().getEmpresa().getIdEmpresa();
    }

    private Usuario requireActor(CrmWhatsappAgentSession session) {
        Usuario agentUser = userRepository.findByCorreoAndDeletedAtIsNull(AGENT_USER_EMAIL).orElse(null);
        if (agentUser != null && "ACTIVO".equalsIgnoreCase(agentUser.getEstado())) return agentUser;
        Integer branchId = session.getConnection().getSucursal() == null
                ? null : session.getConnection().getSucursal().getIdSucursal();
        if (branchId == null) throw new IllegalStateException("La conexion WhatsApp no tiene sucursal configurada");
        return userRepository.findFirstActiveBySucursal(branchId)
                .orElseThrow(() -> new IllegalStateException(
                        "No existe un usuario activo en la sucursal para auditar el registro del cliente"));
    }

    private List<Cliente> findMatches(Integer companyId, String phone, DocumentIdentity document) {
        Map<Integer, Cliente> matches = new LinkedHashMap<>();
        if (phone != null) {
            customerRepository.findByEmpresa_IdEmpresaAndTelefonoInAndDeletedAtIsNullOrderByIdClienteAsc(
                    companyId, CrmWhatsappPhoneUtils.variants(phone))
                    .forEach(customer -> matches.put(customer.getIdCliente(), customer));
        }
        if (document != null) {
            customerRepository
                    .findFirstByEmpresa_IdEmpresaAndTipoDocumentoAndNroDocumentoAndDeletedAtIsNullOrderByIdClienteAsc(
                            companyId, document.type(), document.number())
                    .ifPresent(customer -> matches.put(customer.getIdCliente(), customer));
        }
        return matches.values().stream()
                .sorted(Comparator.comparing(Cliente::getIdCliente)).toList();
    }

    private void ensureIdentityAvailable(Integer companyId, Cliente current, String phone,
            DocumentIdentity document) {
        for (Cliente match : findMatches(companyId, phone, document)) {
            if (!Objects.equals(match.getIdCliente(), current.getIdCliente())) {
                throw new IllegalStateException("Los datos indicados ya pertenecen a otro cliente");
            }
        }
    }

    private Cliente linkedCustomer(LockedScope scope) {
        Cliente sessionCustomer = scope.session().getCliente();
        Cliente conversationCustomer = scope.conversation().getCliente();
        if (sessionCustomer != null && conversationCustomer != null
                && !Objects.equals(sessionCustomer.getIdCliente(), conversationCustomer.getIdCliente())) {
            throw new IllegalStateException("La sesion y la conversacion tienen clientes diferentes");
        }
        Cliente customer = sessionCustomer != null ? sessionCustomer : conversationCustomer;
        if (customer == null) {
            throw new IllegalStateException("Primero busca y relaciona al cliente o registralo");
        }
        return customerRepository.findByIdClienteAndDeletedAtIsNullAndEmpresa_IdEmpresa(
                        customer.getIdCliente(), scope.company().getIdEmpresa())
                .orElseThrow(() -> new IllegalStateException("El cliente relacionado ya no esta disponible"));
    }

    private void fillMissing(Cliente customer, String names, String phone, DocumentIdentity document) {
        if (names != null) {
            if (isMissingName(customer.getNombres())) customer.setNombres(names);
            else if (!sameText(customer.getNombres(), names)) {
                // Valid data is immutable in this tool; preserving it prevents accidental identity changes.
            }
        }
        if (phone != null) {
            String existing = storedPhone(customer.getTelefono());
            if (existing == null) customer.setTelefono(phone);
            else if (!existing.equals(phone)) {
                throw new IllegalStateException("El cliente ya tiene otro celular registrado");
            }
        }
        if (document != null) {
            String existingNumber = clean(customer.getNroDocumento());
            TipoDocumento existingType = customer.getTipoDocumento();
            if (existingNumber == null || existingType == null || existingType == TipoDocumento.SIN_DOC) {
                customer.setTipoDocumento(document.type());
                customer.setNroDocumento(document.number());
            } else if (existingType != document.type() || !existingNumber.equalsIgnoreCase(document.number())) {
                throw new IllegalStateException("El cliente ya tiene otro documento registrado");
            }
        } else if (customer.getTipoDocumento() == null) {
            customer.setTipoDocumento(TipoDocumento.SIN_DOC);
        }
    }

    private void linkLocked(LockedScope scope, Cliente customer) {
        rejectDifferentLink(scope.session().getCliente(), customer);
        rejectDifferentLink(scope.conversation().getCliente(), customer);
        scope.session().setCliente(customer);
        scope.conversation().setCliente(customer);
        LocalDateTime now = LocalDateTime.now();
        scope.session().setLastActivityAt(now);
        if (isValidForPayment(customer)
                && scope.session().getState() == CrmWhatsappAgentState.ESPERANDO_DATOS_CLIENTE) {
            scope.session().setState(stateMachine.transition(scope.session().getState(),
                    CrmWhatsappAgentEvent.DATOS_CLIENTE_COMPLETADOS));
            scope.session().setLastEvent(CrmWhatsappAgentEvent.DATOS_CLIENTE_COMPLETADOS);
            scope.session().setStateChangedAt(now);
        }
        conversationRepository.save(scope.conversation());
        sessionRepository.save(scope.session());
    }

    private void rejectDifferentLink(Cliente current, Cliente requested) {
        if (current != null && !Objects.equals(current.getIdCliente(), requested.getIdCliente())) {
            throw new IllegalStateException("La conversacion ya esta relacionada con otro cliente");
        }
    }

    private CustomerResult result(String outcome, Cliente customer, CrmWhatsappAgentSession session,
            CrmWhatsappConversation conversation) {
        List<String> missing = missingFields(customer);
        return new CustomerResult(outcome, customer.getIdCliente(), customer.getNombres(), customer.getTelefono(),
                customer.getTipoDocumento(), customer.getNroDocumento(),
                conversation.getCliente() != null
                        && Objects.equals(conversation.getCliente().getIdCliente(), customer.getIdCliente()),
                missing.isEmpty(), session.getState().name(), missing);
    }

    private CustomerView view(Cliente customer, CrmWhatsappAgentSession session) {
        boolean linked = (session.getCliente() != null
                && Objects.equals(session.getCliente().getIdCliente(), customer.getIdCliente()))
                || (session.getConversation() != null && session.getConversation().getCliente() != null
                && Objects.equals(session.getConversation().getCliente().getIdCliente(), customer.getIdCliente()));
        return new CustomerView(customer.getIdCliente(), customer.getNombres(), customer.getTelefono(),
                customer.getTipoDocumento(), customer.getNroDocumento(), linked,
                isValidForPayment(customer), missingFields(customer));
    }

    private List<String> missingFields(Cliente customer) {
        List<String> missing = new ArrayList<>();
        if (isMissingName(customer.getNombres())) missing.add("nombres");
        if (storedPhone(customer.getTelefono()) == null) missing.add("celular");
        return List.copyOf(missing);
    }

    private boolean isValidForPayment(Cliente customer) {
        return customer != null && "ACTIVO".equalsIgnoreCase(customer.getEstado())
                && customer.getDeletedAt() == null && missingFields(customer).isEmpty();
    }

    private boolean isMissingName(String value) {
        String name = clean(value);
        return name == null || name.toUpperCase(Locale.ROOT).startsWith(QUICK_CUSTOMER_PREFIX);
    }

    private String requireNames(String value) {
        String names = optionalNames(value);
        if (names == null) throw new IllegalArgumentException("Los nombres son obligatorios");
        return names;
    }

    private String optionalNames(String value) {
        String names = clean(value);
        if (names == null) return null;
        names = names.replaceAll("\\s+", " ");
        if (names.length() < 2 || names.length() > 150) {
            throw new IllegalArgumentException("Los nombres deben tener entre 2 y 150 caracteres");
        }
        return names;
    }

    private String requirePhone(String value) {
        String phone = optionalPhone(value);
        if (phone == null) throw new IllegalArgumentException("Ingresa un celular peruano valido de 9 digitos");
        return phone;
    }

    private String optionalPhone(String value) {
        if (clean(value) == null) return null;
        String phone = CrmWhatsappPhoneUtils.normalizePeruvianMobile(value);
        if (phone.isBlank()) throw new IllegalArgumentException("Ingresa un celular peruano valido de 9 digitos");
        return phone;
    }

    private String storedPhone(String value) {
        if (clean(value) == null) return null;
        String phone = CrmWhatsappPhoneUtils.normalizePeruvianMobile(value);
        return phone.isBlank() ? null : phone;
    }

    private DocumentIdentity normalizeDocument(TipoDocumento type, String number, boolean optional) {
        String normalized = clean(number);
        if (normalized == null) {
            if (type != null && type != TipoDocumento.SIN_DOC && !optional) {
                throw new IllegalArgumentException("El numero de documento es obligatorio");
            }
            return null;
        }
        normalized = normalized.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
        TipoDocumento resolved = type;
        if (resolved == null || resolved == TipoDocumento.SIN_DOC) {
            if (normalized.matches("\\d{8}")) resolved = TipoDocumento.DNI;
            else if (normalized.matches("\\d{11}")) resolved = TipoDocumento.RUC;
            else resolved = TipoDocumento.CE;
        }
        boolean valid = switch (resolved) {
            case DNI -> normalized.matches("\\d{8}");
            case RUC -> normalized.matches("\\d{11}");
            case CE -> normalized.matches("[A-Z0-9]{6,20}");
            case SIN_DOC -> false;
        };
        if (!valid) throw new IllegalArgumentException("El documento no tiene un formato valido para " + resolved);
        return new DocumentIdentity(resolved, normalized);
    }

    private String clean(String value) {
        if (value == null) return null;
        String cleaned = value.trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    private boolean sameText(String left, String right) {
        return left != null && right != null && left.trim().equalsIgnoreCase(right.trim());
    }

    private String documentKey(DocumentIdentity document) {
        return document == null ? "" : document.type() + ":" + document.number();
    }

    private CustomerResult execute(Long sessionId, String operationId, String toolName, String request,
            Supplier<CustomerResult> action) {
        if (operationId == null || operationId.isBlank()) {
            throw new IllegalStateException("Falta el identificador seguro de la operacion del agente");
        }
        String key = sha256("customer:" + sessionId + ":" + operationId.trim());
        String fingerprint = sha256(toolName + ":" + request);
        Optional<OperationRow> existing = operationStore.find(key);
        if (existing.isPresent()) return replay(existing.get(), toolName, fingerprint);
        LocalDateTime now = LocalDateTime.now();
        if (!operationStore.claim(sessionId, key, toolName, fingerprint, now)) {
            OperationRow concurrent = operationStore.find(key)
                    .orElseThrow(() -> new IllegalStateException("La operacion del cliente esta en curso"));
            return replay(concurrent, toolName, fingerprint);
        }
        CustomerResult result = action.get();
        operationStore.complete(key, write(result), LocalDateTime.now());
        return result;
    }

    private CustomerResult replay(OperationRow operation, String toolName, String fingerprint) {
        if (!toolName.equals(operation.toolName()) || !fingerprint.equals(operation.requestFingerprint())) {
            throw new IllegalStateException("El identificador de operacion ya fue usado con otra solicitud");
        }
        if (!"COMPLETED".equals(operation.status()) || operation.responseJson() == null) {
            throw new IllegalStateException("La operacion del cliente aun esta en curso");
        }
        try {
            return objectMapper.readValue(operation.responseJson(), CustomerResult.class);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("No se pudo recuperar el resultado idempotente del cliente", error);
        }
    }

    private String write(CustomerResult result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("No se pudo guardar el resultado del cliente", error);
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 no esta disponible", error);
        }
    }

    private record LockedScope(CrmWhatsappAgentSession session, CrmWhatsappConversation conversation,
            Empresa company) {
    }

    private record DocumentIdentity(TipoDocumento type, String number) {
    }

    public record CustomerSearchResult(String status, List<CustomerView> customers, String message) {
    }

    public record CustomerView(Integer customerId, String names, String phone, TipoDocumento documentType,
            String documentNumber, boolean linked, boolean validForPayment, List<String> missingFields) {
    }

    public record CustomerResult(String outcome, Integer customerId, String names, String phone,
            TipoDocumento documentType, String documentNumber, boolean linked, boolean validForPayment,
            String agentState, List<String> missingFields) {
    }
}
