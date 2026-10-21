package com.sistemapos.sistematextil.services;

import java.util.List;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import com.sistemapos.sistematextil.model.CrmWhatsappArea;

@Service
public class CrmWhatsappBridgeService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final String bridgeUrl;
    private final String bridgeToken;
    private final CrmWhatsappConnectionStateService connectionStateService;

    public CrmWhatsappBridgeService(
            @Value("${whatsapp.bridge.url}") String bridgeUrl,
            @Value("${whatsapp.bridge.token}") String bridgeToken,
            CrmWhatsappConnectionStateService connectionStateService) {
        this.bridgeUrl = bridgeUrl == null ? "" : bridgeUrl.replaceAll("/+$", "");
        this.bridgeToken = bridgeToken == null ? "" : bridgeToken.trim();
        this.connectionStateService = connectionStateService;
    }

    public ResponseEntity<String> obtenerEstado() {
        return request(HttpMethod.GET, "/status");
    }

    public ResponseEntity<String> obtenerQr() {
        return request(HttpMethod.GET, "/qr");
    }

    public ResponseEntity<String> cerrarSesion() {
        return request(HttpMethod.POST, "/logout");
    }

    public ResponseEntity<String> obtenerConexiones() {
        return request(HttpMethod.GET, "/connections");
    }

    public ResponseEntity<String> obtenerEstado(CrmWhatsappArea area) {
        return request(HttpMethod.GET, areaPath(area, "/status"));
    }

    public ResponseEntity<String> obtenerQr(CrmWhatsappArea area) {
        return request(HttpMethod.GET, areaPath(area, "/qr"));
    }

    public ResponseEntity<String> cerrarSesion(CrmWhatsappArea area) {
        return request(HttpMethod.POST, areaPath(area, "/logout"));
    }

    public boolean isBridgeToken(String token) {
        return !bridgeToken.isBlank() && bridgeToken.equals(token);
    }

    public ResponseEntity<String> enviarTexto(String to, String message, Object quotedMessage) {
        return enviarTexto(to, message, quotedMessage, null);
    }

    public ResponseEntity<String> enviarTexto(String to, String message, Object quotedMessage, String idempotencyKey) {
        return enviarTexto(CrmWhatsappArea.SALES, to, message, quotedMessage, idempotencyKey);
    }

    public ResponseEntity<String> enviarTexto(CrmWhatsappArea area, String to, String message,
            Object quotedMessage, String idempotencyKey) {
        connectionStateService.requireOperational(area);
        java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("to", to);
        body.put("message", message);
        if (quotedMessage != null) {
            body.put("quoted", quotedMessage);
        }
        return request(HttpMethod.POST, areaPath(area, "/send-text"), body, idempotencyKey);
    }

    public ResponseEntity<String> enviarMedia(String to, MultipartFile file, String caption, String quotedMessageJson) {
        try {
            return enviarMedia(to, file.getBytes(), file.getOriginalFilename(), file.getContentType(), caption, quotedMessageJson);
        } catch (Exception e) {
            return json(HttpStatus.SERVICE_UNAVAILABLE.value(),
                    "{\"message\":\"No se pudo leer el archivo\"}");
        }
    }

    public ResponseEntity<String> enviarMedia(String to, byte[] bytes, String originalFilename,
            String contentType, String caption, String quotedMessageJson) {
        return enviarMedia(to, bytes, originalFilename, contentType, caption, quotedMessageJson, null);
    }

    public ResponseEntity<String> enviarMedia(String to, byte[] bytes, String originalFilename,
            String contentType, String caption, String quotedMessageJson, String idempotencyKey) {
        return enviarMedia(CrmWhatsappArea.SALES, to, bytes, originalFilename, contentType, caption,
                quotedMessageJson, idempotencyKey);
    }

    public ResponseEntity<String> enviarMedia(CrmWhatsappArea area, String to, byte[] bytes, String originalFilename,
            String contentType, String caption, String quotedMessageJson, String idempotencyKey) {
        connectionStateService.requireOperational(area);
        if (bridgeUrl.isBlank()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "WHATSAPP_BRIDGE_URL no configurado");
        }
        if (bridgeToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "WHATSAPP_BRIDGE_TOKEN no configurado");
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.set("X-Bridge-Token", bridgeToken);
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                headers.set("X-Idempotency-Key", idempotencyKey);
            }

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("to", to);
            body.add("caption", caption == null ? "" : caption);
            if (quotedMessageJson != null && !quotedMessageJson.isBlank()) {
                body.add("quoted", quotedMessageJson);
            }

            HttpHeaders fileHeaders = new HttpHeaders();
            String fileName = originalFilename == null || originalFilename.isBlank()
                    ? "archivo"
                    : originalFilename;
            fileHeaders.setContentType(parseMediaType(contentType));
            fileHeaders.setContentDispositionFormData("file", fileName);
            ByteArrayResource fileResource = new ByteArrayResource(bytes) {
                @Override
                public String getFilename() {
                    return fileName;
                }
            };
            body.add("file", new HttpEntity<>(fileResource, fileHeaders));

            ResponseEntity<String> response = restTemplate.exchange(
                    bridgeUrl + areaPath(area, "/send-media"),
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    String.class);
            return json(response.getStatusCode().value(), response.getBody());
        } catch (HttpStatusCodeException e) {
            return json(e.getStatusCode().value(), e.getResponseBodyAsString());
        } catch (Exception e) {
            return json(HttpStatus.SERVICE_UNAVAILABLE.value(),
                    "{\"message\":\"Microservicio de WhatsApp no disponible\"}");
        }
    }

    public ResponseEntity<String> eliminarMensaje(String messageKeyJson) {
        connectionStateService.requireOperational();
        return request(HttpMethod.POST, "/delete-message", java.util.Map.of("messageKey", messageKeyJson));
    }

    private ResponseEntity<String> request(HttpMethod method, String path) {
        return request(method, path, null);
    }

    private ResponseEntity<String> request(HttpMethod method, String path, Object body) {
        return request(method, path, body, null);
    }

    private ResponseEntity<String> request(HttpMethod method, String path, Object body, String idempotencyKey) {
        if (bridgeUrl.isBlank()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "WHATSAPP_BRIDGE_URL no configurado");
        }
        if (bridgeToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "WHATSAPP_BRIDGE_TOKEN no configurado");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Bridge-Token", bridgeToken);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            headers.set("X-Idempotency-Key", idempotencyKey);
        }

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    bridgeUrl + path,
                    method,
                    new HttpEntity<>(body, headers),
                    String.class);

            return json(response.getStatusCode().value(), response.getBody());
        } catch (HttpStatusCodeException e) {
            return json(e.getStatusCode().value(), e.getResponseBodyAsString());
        } catch (ResourceAccessException e) {
            return json(HttpStatus.SERVICE_UNAVAILABLE.value(),
                    "{\"message\":\"Microservicio de WhatsApp no disponible\"}");
        }
    }

    private ResponseEntity<String> json(int status, String body) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body == null || body.isBlank() ? "{}" : body);
    }

    private String areaPath(CrmWhatsappArea area, String suffix) {
        if (area == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Area de WhatsApp requerida");
        return "/connections/" + area.path() + suffix;
    }

    private MediaType parseMediaType(String value) {
        if (value == null || value.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(value);
        } catch (IllegalArgumentException e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
