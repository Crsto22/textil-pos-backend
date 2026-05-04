package com.sistemapos.sistematextil.services;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sistemapos.sistematextil.model.SunatConfig;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SunatRestApiClientService {

    private static final Logger log = LoggerFactory.getLogger(SunatRestApiClientService.class);
    private static final int MAX_NETWORK_ATTEMPTS = 3;
    private static final long TOKEN_SKEW_SECONDS = 30L;
    private static final long BASE_BACKOFF_MILLIS = 1_000L;
    private static final String BETA_GRE_TOKEN_BASE_URL = "https://gre-test.nubefact.com/v1/clientessol";
    private static final String PRODUCTION_GRE_TOKEN_BASE_URL = "https://api-seguridad.sunat.gob.pe/v1/clientessol";
    private static final String BETA_GRE_CPE_BASE_URL =
            "https://gre-test.nubefact.com/v1/contribuyente/gem/comprobantes";
    private static final String PRODUCTION_GRE_CPE_BASE_URL =
            "https://api-cpe.sunat.gob.pe/v1/contribuyente/gem/comprobantes";
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ConcurrentMap<String, CachedToken> TOKEN_CACHE = new ConcurrentHashMap<>();

    private final SunatSecretCryptoService sunatSecretCryptoService;

    /**
     * Obtiene un token OAuth2 de la API REST de SUNAT usando client credentials + usuario SOL.
     */
    public TokenResponse obtenerToken(SunatConfig config) {
        String cacheKey = buildTokenCacheKey(config);
        CachedToken cachedToken = TOKEN_CACHE.get(cacheKey);
        if (cachedToken != null && cachedToken.isValidAt(Instant.now())) {
            return new TokenResponse(
                    cachedToken.accessToken(),
                    cachedToken.tokenType(),
                    cachedToken.expiresInSeconds());
        }

        String tokenUrl = buildTokenUrl(config);
        String clientId = requireText(config.getClientId(), "La configuracion SUNAT no tiene clientId");
        String clientSecret = requireText(
                sunatSecretCryptoService.decrypt(config.getClientSecret()),
                "La configuracion SUNAT no tiene clientSecret");
        String username = resolveUsername(config);
        String password = requireText(
                sunatSecretCryptoService.decrypt(config.getClaveSol()),
                "La configuracion SUNAT no tiene claveSol");

        String body = String.join("&",
                "grant_type=password",
                "scope=https://api-cpe.sunat.gob.pe",
                "client_id=" + urlEncode(clientId),
                "client_secret=" + urlEncode(clientSecret),
                "username=" + urlEncode(username),
                "password=" + urlEncode(password));

        HttpRequest request = HttpRequest.newBuilder(URI.create(tokenUrl))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        log.info("SUNAT REST obtenerToken -> endpoint={}", tokenUrl);

        try {
            HttpResponse<String> response = sendWithRetry(
                    request,
                    "obtener token SUNAT");
            log.info("SUNAT token response HTTP status={}", response.statusCode());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                JsonNode json = MAPPER.readTree(response.body());
                String accessToken = json.has("access_token") ? json.get("access_token").asText() : null;
                if (accessToken == null || accessToken.isBlank()) {
                    throw new RuntimeException("SUNAT devolvio un token vacio");
                }
                String tokenType = json.has("token_type") ? json.get("token_type").asText() : "Bearer";
                long expiresIn = json.path("expires_in").asLong(300L);
                Instant expiresAt = Instant.now().plusSeconds(Math.max(60L, expiresIn) - TOKEN_SKEW_SECONDS);
                TOKEN_CACHE.put(cacheKey, new CachedToken(accessToken, tokenType, expiresIn, expiresAt));
                return new TokenResponse(accessToken, tokenType, expiresIn);
            }

            throw new RuntimeException("Error al obtener token SUNAT: HTTP " + response.statusCode()
                    + " - " + limitText(response.body(), 300));
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error de conexion al obtener token SUNAT: {}", e.getMessage(), e);
            throw new RuntimeException("No se pudo conectar con SUNAT para obtener token: " + e.getMessage());
        }
    }

    /**
     * Envía el ZIP firmado a la API REST de SUNAT para CPE (guías de remisión).
     */
    public SendCpeResponse enviarCpe(SunatConfig config, String token, String zipFileName, byte[] zipBytes) {
        return enviarCpe(config, token, zipFileName, zipBytes, true);
    }

    private SendCpeResponse enviarCpe(
            SunatConfig config,
            String token,
            String zipFileName,
            byte[] zipBytes,
            boolean allowTokenRefresh) {
        String cpeUrl = resolveCpeUrl(config);

        String fullUrl = cpeUrl.endsWith("/") ? cpeUrl : cpeUrl + "/";
        fullUrl += stripExtension(zipFileName);

        String zipBase64 = Base64.getEncoder().encodeToString(zipBytes);
        String hashZip = sha256Hex(zipBytes);

        String jsonBody;
        try {
            jsonBody = MAPPER.writeValueAsString(new CpeRequest(new ArchivoRequest(zipFileName, zipBase64, hashZip)));
        } catch (Exception e) {
            throw new RuntimeException("No se pudo serializar el request para SUNAT API CPE");
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(fullUrl))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();

        log.info("SUNAT REST enviarCpe -> endpoint={}, zipFile={}, zipSize={} bytes",
                fullUrl, zipFileName, zipBytes.length);

        try {
            HttpResponse<String> response = sendWithRetry(
                    request,
                    "enviar GRE a SUNAT");
            log.info("SUNAT CPE response HTTP status={}, bodyLength={}",
                    response.statusCode(), response.body() == null ? 0 : response.body().length());
            log.debug("SUNAT CPE response body: {}", response.body());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return parseSuccessResponse(response.body());
            }
            if (response.statusCode() == 401 && allowTokenRefresh) {
                log.warn("SUNAT rechazo el token al enviar GRE. Se refrescara el token y se reintentara una vez.");
                evictToken(config);
                TokenResponse refreshedToken = obtenerToken(config);
                return enviarCpe(config, refreshedToken.accessToken(), zipFileName, zipBytes, false);
            }

            String errorMsg = parseErrorMessage(response.body(), response.statusCode());
            throw new RuntimeException(errorMsg);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error de conexion al enviar CPE a SUNAT: {}", e.getMessage(), e);
            throw new RuntimeException("No se pudo conectar con SUNAT API CPE: " + e.getMessage());
        }
    }

    /**
     * Consulta el ticket de una guía de remisión enviada a SUNAT.
     */
    public TicketResponse consultarTicket(SunatConfig config, String token, String ticket) {
        return consultarTicket(config, token, ticket, true);
    }

    private TicketResponse consultarTicket(
            SunatConfig config,
            String token,
            String ticket,
            boolean allowTokenRefresh) {
        String cpeUrl = resolveCpeUrl(config);

        String fullUrl = cpeUrl.endsWith("/") ? cpeUrl : cpeUrl + "/";
        fullUrl += "envios/" + urlEncode(ticket);

        HttpRequest request = HttpRequest.newBuilder(URI.create(fullUrl))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();

        log.info("SUNAT REST consultarTicket -> endpoint={}, ticket={}", fullUrl, ticket);

        try {
            HttpResponse<String> response = sendWithRetry(
                    request,
                    "consultar ticket SUNAT");
            log.info("SUNAT ticket response HTTP status={}", response.statusCode());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return parseTicketResponse(response.body());
            }
            if (response.statusCode() == 401 && allowTokenRefresh) {
                log.warn("SUNAT rechazo el token al consultar ticket. Se refrescara el token y se reintentara una vez.");
                evictToken(config);
                TokenResponse refreshedToken = obtenerToken(config);
                return consultarTicket(config, refreshedToken.accessToken(), ticket, false);
            }

            String errorMsg = parseErrorMessage(response.body(), response.statusCode());
            throw new RuntimeException(errorMsg);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error de conexion al consultar ticket SUNAT: {}", e.getMessage(), e);
            throw new RuntimeException("No se pudo conectar con SUNAT para consultar ticket: " + e.getMessage());
        }
    }

    private SendCpeResponse parseSuccessResponse(String body) {
        try {
            JsonNode json = MAPPER.readTree(body);
            String numTicket = json.has("numTicket") ? json.get("numTicket").asText() : null;
            String fecRecepcion = json.has("fecRecepcion") ? json.get("fecRecepcion").asText() : null;
            String codRespuesta = json.has("codRespuesta") ? json.get("codRespuesta").asText() : null;
            return new SendCpeResponse(numTicket, fecRecepcion, codRespuesta);
        } catch (Exception e) {
            throw new RuntimeException("No se pudo interpretar la respuesta de SUNAT API CPE");
        }
    }

    private TicketResponse parseTicketResponse(String body) {
        try {
            JsonNode json = MAPPER.readTree(body);
            String codRespuesta = json.has("codRespuesta") ? json.get("codRespuesta").asText() : null;
            String arcCdr = json.has("arcCdr") ? json.get("arcCdr").asText() : null;
            String indCdrGenerado = json.has("indCdrGenerado") ? json.get("indCdrGenerado").asText() : null;
            byte[] cdrBytes = null;
            if (arcCdr != null && !arcCdr.isBlank()) {
                cdrBytes = Base64.getDecoder().decode(arcCdr);
            }
            return new TicketResponse(codRespuesta, cdrBytes, indCdrGenerado);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("No se pudo interpretar la respuesta del ticket SUNAT");
        }
    }

    private String parseErrorMessage(String body, int httpStatus) {
        try {
            JsonNode json = MAPPER.readTree(body);
            if (json.has("cod") && json.has("msg")) {
                return "SUNAT error " + json.get("cod").asText() + ": " + json.get("msg").asText();
            }
            if (json.has("error_description")) {
                return "SUNAT error: " + json.get("error_description").asText();
            }
        } catch (Exception ignored) {
        }
        return "SUNAT devolvio error HTTP " + httpStatus + ": " + limitText(body, 200);
    }

    private HttpResponse<String> sendWithRetry(HttpRequest request, String operation) {
        for (int attempt = 1; attempt <= MAX_NETWORK_ATTEMPTS; attempt++) {
            try {
                return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("La operacion fue interrumpida al " + operation, e);
            } catch (Exception e) {
                if (attempt >= MAX_NETWORK_ATTEMPTS) {
                    throw new RuntimeException(
                            "No se pudo " + operation + " tras " + MAX_NETWORK_ATTEMPTS + " intentos: "
                                    + e.getMessage(),
                            e);
                }
                long backoffMillis = BASE_BACKOFF_MILLIS * attempt;
                log.warn("Error de red al {} (intento {}/{}): {}. Reintentando en {} ms.",
                        operation, attempt, MAX_NETWORK_ATTEMPTS, e.getMessage(), backoffMillis);
                sleep(backoffMillis);
            }
        }
        throw new RuntimeException("No se pudo " + operation);
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new RuntimeException(message);
        }
        return value.trim();
    }

    private String buildTokenCacheKey(SunatConfig config) {
        String idConfig = config.getIdSunatConfig() == null ? "0" : String.valueOf(config.getIdSunatConfig());
        String ruc = config.getEmpresa() != null ? normalizeKeyValue(config.getEmpresa().getRuc()) : "sinruc";
        return idConfig + "|" + ruc + "|" + normalizeKeyValue(config.getClientId()) + "|"
                + normalizeKeyValue(config.getUsuarioSol());
    }

    private String normalizeKeyValue(String value) {
        return value == null ? "" : value.trim();
    }

    private String buildTokenUrl(SunatConfig config) {
        String baseUrl = resolveTokenBaseUrl(config);
        String clientId = requireText(config.getClientId(), "La configuracion SUNAT no tiene clientId");
        if (baseUrl.contains("/oauth2/token")) {
            return baseUrl;
        }
        String normalized = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return normalized + "/" + clientId + "/oauth2/token";
    }

    private String resolveUsername(SunatConfig config) {
        String ruc = requireText(config.getEmpresa().getRuc(), "La empresa no tiene RUC");
        String usuarioSol = requireText(config.getUsuarioSol(), "La configuracion SUNAT no tiene usuarioSol");
        if (!usuarioSol.startsWith(ruc)) {
            return ruc + usuarioSol;
        }
        return usuarioSol;
    }

    private String resolveTokenBaseUrl(SunatConfig config) {
        String configured = normalizeOptional(config.getUrlApiToken());
        if (configured != null) {
            return configured;
        }
        if (isBeta(config)) {
            return BETA_GRE_TOKEN_BASE_URL;
        }
        if (isProduction(config)) {
            return PRODUCTION_GRE_TOKEN_BASE_URL;
        }
        throw new RuntimeException("La configuracion SUNAT no tiene urlApiToken");
    }

    private String resolveCpeUrl(SunatConfig config) {
        String configured = normalizeOptional(config.getUrlApiCpe());
        if (configured != null) {
            return configured;
        }
        if (isBeta(config)) {
            return BETA_GRE_CPE_BASE_URL;
        }
        if (isProduction(config)) {
            return PRODUCTION_GRE_CPE_BASE_URL;
        }
        throw new RuntimeException("La configuracion SUNAT no tiene urlApiCpe");
    }

    private boolean isBeta(SunatConfig config) {
        return config != null
                && config.getAmbiente() != null
                && "BETA".equalsIgnoreCase(config.getAmbiente().trim());
    }

    private boolean isProduction(SunatConfig config) {
        return config != null
                && config.getAmbiente() != null
                && "PRODUCCION".equalsIgnoreCase(config.getAmbiente().trim());
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String urlEncode(String value) {
        try {
            return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return value;
        }
    }

    private String stripExtension(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new RuntimeException("El nombre del archivo ZIP es obligatorio");
        }
        int lastDot = fileName.lastIndexOf('.');
        return lastDot > 0 ? fileName.substring(0, lastDot) : fileName;
    }

    private String sha256Hex(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (Exception e) {
            throw new RuntimeException("No se pudo calcular el hash SHA-256 del ZIP");
        }
    }

    private String limitText(String value, int maxLen) {
        if (value == null) return "";
        return value.length() > maxLen ? value.substring(0, maxLen) + "..." : value;
    }

    private void evictToken(SunatConfig config) {
        TOKEN_CACHE.remove(buildTokenCacheKey(config));
    }

    private void sleep(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("La espera de reintento fue interrumpida", e);
        }
    }

    public record TokenResponse(String accessToken, String tokenType, long expiresInSeconds) {
    }

    public record SendCpeResponse(String numTicket, String fecRecepcion, String codRespuesta) {
    }

    public record TicketResponse(String codRespuesta, byte[] cdrBytes, String indCdrGenerado) {
    }

    private record CpeRequest(ArchivoRequest archivo) {
    }

    private record ArchivoRequest(String nomArchivo, String arcGreZip, String hashZip) {
    }

    private record CachedToken(
            String accessToken,
            String tokenType,
            long expiresInSeconds,
            Instant expiresAt) {

        private boolean isValidAt(Instant instant) {
            return accessToken != null
                    && !accessToken.isBlank()
                    && expiresAt != null
                    && instant != null
                    && instant.isBefore(expiresAt);
        }
    }
}
