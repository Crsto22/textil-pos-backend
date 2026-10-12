package com.sistemapos.sistematextil.services.agent.fulfillment;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.Normalizer;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sistemapos.sistematextil.model.ShalomAgency;
import com.sistemapos.sistematextil.model.ShalomAgencySync;
import com.sistemapos.sistematextil.repositories.ShalomAgencyRepository;
import com.sistemapos.sistematextil.repositories.ShalomAgencySyncRepository;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class ShalomAgencySyncService {
    private static final int MAX_RESPONSE_BYTES = 5 * 1024 * 1024;
    private final ShalomAgencyRepository agencies;
    private final ShalomAgencySyncRepository syncs;
    private final TransactionTemplate transactions;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final HttpClient client;
    private final URI apiUri;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public ShalomAgencySyncService(ShalomAgencyRepository agencies, ShalomAgencySyncRepository syncs,
            PlatformTransactionManager transactionManager,
            @Value("${crm.whatsapp.shalom.api-url:https://api.shalom-api.lat/public/agencies}") String apiUrl,
            @Value("${crm.whatsapp.shalom.connect-timeout-seconds:8}") int connectTimeoutSeconds) {
        this.agencies = agencies;
        this.syncs = syncs;
        this.transactions = new TransactionTemplate(transactionManager);
        this.apiUri = URI.create(apiUrl);
        if (!"https".equalsIgnoreCase(apiUri.getScheme())) {
            throw new IllegalStateException("La API de agencias Shalom debe usar HTTPS");
        }
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(2, connectTimeoutSeconds))).build();
    }

    @Scheduled(initialDelayString = "${crm.whatsapp.shalom.initial-delay-ms:12000}",
            fixedDelayString = "${crm.whatsapp.shalom.sync-delay-ms:86400000}")
    public void scheduledSync() {
        try { synchronize(); }
        catch (RuntimeException error) { log.warn("No se pudieron sincronizar las agencias Shalom: {}", error.getMessage()); }
    }

    public SyncStatus synchronize() {
        if (!running.compareAndSet(false, true)) throw new IllegalStateException("Ya existe una sincronizacion en curso");
        ShalomAgencySync execution = new ShalomAgencySync();
        execution.setStatus("RUNNING");
        execution.setStartedAt(LocalDateTime.now());
        execution = syncs.saveAndFlush(execution);
        Long executionId = execution.getId();
        try {
            HttpResponse<byte[]> response = fetchWithRetries();
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Shalom respondio HTTP " + response.statusCode());
            }
            if (response.body().length > MAX_RESPONSE_BYTES) throw new IllegalStateException("Respuesta Shalom demasiado grande");
            JsonNode root = mapper.readTree(response.body());
            JsonNode data = root.path("data");
            if (!root.path("success").asBoolean(false) || !data.isArray()) {
                throw new IllegalStateException("La API de Shalom devolvio una estructura invalida");
            }
            int declared = root.path("total").asInt(-1);
            if (declared >= 0 && declared != data.size()) throw new IllegalStateException("El total de agencias no coincide");
            SyncStatus result = transactions.execute(status -> apply(executionId, declared, data));
            return result == null ? status() : result;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            fail(executionId, error);
            throw new IllegalStateException("La sincronizacion de Shalom fue interrumpida", error);
        } catch (Exception error) {
            fail(executionId, error);
            throw error instanceof RuntimeException runtime ? runtime
                    : new IllegalStateException("No se pudo sincronizar Shalom", error);
        } finally {
            running.set(false);
        }
    }

    private HttpResponse<byte[]> fetchWithRetries() throws Exception {
        Exception last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder(apiUri).timeout(Duration.ofSeconds(20))
                        .header("Accept", "application/json").GET().build();
                HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() >= 500 && attempt < 3) {
                    Thread.sleep(attempt * 750L);
                    continue;
                }
                return response;
            } catch (java.io.IOException error) {
                last = error;
                if (attempt < 3) Thread.sleep(attempt * 750L);
            }
        }
        throw last == null ? new IllegalStateException("Shalom no respondio") : last;
    }

    public SyncStatus status() {
        ShalomAgencySync latest = syncs.findFirstByOrderByStartedAtDesc().orElse(null);
        ShalomAgencySync success = syncs.findFirstByStatusOrderByCompletedAtDesc("SUCCESS").orElse(null);
        return new SyncStatus(running.get(), agencies.count(), agencies.countByActiveTrueAndDestinationEnabledTrue(),
                latest == null ? "NEVER" : latest.getStatus(), latest == null ? null : latest.getStartedAt(),
                success == null ? null : success.getCompletedAt(), latest == null ? null : latest.getErrorMessage(),
                latest == null ? 0 : latest.getReceivedCount(), latest == null ? 0 : latest.getCreatedCount(),
                latest == null ? 0 : latest.getUpdatedCount(), latest == null ? 0 : latest.getDeactivatedCount());
    }

    private SyncStatus apply(Long executionId, int declared, JsonNode data) {
        LocalDateTime now = LocalDateTime.now();
        int created = 0, updated = 0;
        Set<Integer> seen = new HashSet<>();
        Iterator<JsonNode> iterator = data.elements();
        while (iterator.hasNext()) {
            JsonNode node = iterator.next();
            int externalId = node.path("ter_id").asInt(0);
            if (externalId <= 0 || !seen.add(externalId)) throw new IllegalStateException("ter_id Shalom invalido o duplicado");
            ShalomAgency agency = agencies.findByExternalId(externalId).orElse(null);
            if (agency == null) { agency = new ShalomAgency(); agency.setExternalId(externalId); created++; }
            else updated++;
            map(agency, node, now);
            agencies.save(agency);
        }
        int deactivated = 0;
        for (ShalomAgency agency : agencies.findAll()) {
            if (agency.isActive() && !seen.contains(agency.getExternalId())) {
                agency.setActive(false); agency.setSyncedAt(now); agencies.save(agency); deactivated++;
            }
        }
        ShalomAgencySync execution = syncs.findById(executionId).orElseThrow();
        execution.setStatus("SUCCESS"); execution.setSourceTotal(declared); execution.setReceivedCount(data.size());
        execution.setCreatedCount(created); execution.setUpdatedCount(updated); execution.setDeactivatedCount(deactivated);
        execution.setCompletedAt(now); syncs.save(execution);
        return status();
    }

    private void map(ShalomAgency agency, JsonNode node, LocalDateTime now) {
        agency.setAbbreviation(text(node, "ter_abrebiatura"));
        agency.setName(required(node, "nombre"));
        agency.setDepartment(text(node, "departamento")); agency.setProvince(text(node, "provincia"));
        agency.setZone(first(text(node, "zona"), text(node, "ter_zona")));
        agency.setPlace(first(text(node, "lugar_over"), text(node, "lugar")));
        agency.setAddress(text(node, "direccion")); agency.setPhone(text(node, "telefono"));
        agency.setAttentionHours(text(node, "hora_atencion"));
        agency.setLatitude(decimal(node, "latitud")); agency.setLongitude(decimal(node, "longitud"));
        agency.setUbigeoId(integer(node, "ubi_id")); agency.setOriginEnabled(flag(node, "origen"));
        agency.setDestinationEnabled(flag(node, "destino")); agency.setDeliveryEnabled(flag(node, "ter_reparto_habilitado"));
        agency.setPrincipal(flag(node, "ter_principal")); agency.setServiceStatus(text(node, "estadoAgencia"));
        agency.setNormalizedSearch(normalize(String.join(" ", safe(agency.getName()), safe(agency.getAbbreviation()),
                safe(agency.getDepartment()), safe(agency.getProvince()), safe(agency.getZone()),
                safe(agency.getPlace()), safe(agency.getAddress()))));
        agency.setRawPayload(node.toString()); agency.setActive(true); agency.setLastSeenAt(now); agency.setSyncedAt(now);
    }

    private void fail(Long id, Exception error) {
        transactions.executeWithoutResult(status -> syncs.findById(id).ifPresent(row -> {
            row.setStatus("FAILED"); row.setErrorMessage(limit(error.getMessage(), 1000));
            row.setCompletedAt(LocalDateTime.now()); syncs.save(row);
        }));
    }
    private String text(JsonNode n,String f){String v=n.path(f).isNull()?null:n.path(f).asText(null);return v==null||v.isBlank()?null:v.trim();}
    private String required(JsonNode n,String f){String v=text(n,f);if(v==null)throw new IllegalStateException("Agencia sin "+f);return v;}
    private Integer integer(JsonNode n,String f){return n.path(f).isNumber()?n.path(f).asInt():null;}
    private BigDecimal decimal(JsonNode n,String f){try{String v=text(n,f);return v==null?null:new BigDecimal(v);}catch(Exception e){return null;}}
    private boolean flag(JsonNode n,String f){String v=n.path(f).asText("0");return "1".equals(v)||"true".equalsIgnoreCase(v);}
    private String first(String a,String b){return a!=null?a:b;}
    private String safe(String v){return v==null?"":v;}
    private String limit(String v,int max){if(v==null)return "Error desconocido";return v.length()>max?v.substring(0,max):v;}
    static String normalize(String value){return Normalizer.normalize(value==null?"":value,Normalizer.Form.NFD)
            .replaceAll("\\p{M}+","").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+"," ").trim();}

    public record SyncStatus(boolean running, long totalAgencies, long availableDestinations, String lastStatus,
            LocalDateTime lastStartedAt, LocalDateTime lastSuccessfulAt, String lastError,
            int received, int created, int updated, int deactivated) {}
}
