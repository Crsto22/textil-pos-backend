package com.sistemapos.sistematextil.services;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sistemapos.sistematextil.util.documento.ConsultaDniResponse;
import com.sistemapos.sistematextil.util.documento.ConsultaRucResponse;

@Service
public class DocumentoConsultaService {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final String baseUrl;
    private final String token;

    public DocumentoConsultaService(
            @Value("${external.apisperu.base-url}") String baseUrl,
            @Value("${external.apisperu.token:}") String token) {
        this.baseUrl = baseUrl;
        this.token = token;
    }

    public ConsultaDniResponse consultarDni(String dni) {
        String valor = validarDocumento(dni, 8, "DNI");
        JsonNode node = consultar("dni", valor);

        String mensajeError = extraerMensajeError(node);
        if (mensajeError != null && !mensajeError.isBlank() && !node.hasNonNull("dni")) {
            throw new RuntimeException(mensajeError);
        }
        if (!node.hasNonNull("dni") || !node.hasNonNull("nombres")) {
            throw new RuntimeException("No se pudo obtener informacion para el DNI " + valor);
        }

        return new ConsultaDniResponse(
                node.path("success").asBoolean(false),
                node.path("dni").asText(null),
                node.path("nombres").asText(null),
                node.path("apellidoPaterno").asText(null),
                node.path("apellidoMaterno").asText(null),
                node.path("codVerifica").isMissingNode() || node.path("codVerifica").isNull()
                        ? null
                        : node.path("codVerifica").asInt(),
                node.path("codVerificaLetra").asText(null));
    }

    public ConsultaRucResponse consultarRuc(String ruc) {
        String valor = validarDocumento(ruc, 11, "RUC");
        JsonNode node = consultar("ruc", valor);

        String mensajeError = extraerMensajeError(node);
        if (mensajeError != null && !mensajeError.isBlank() && !node.hasNonNull("ruc")) {
            throw new RuntimeException(mensajeError);
        }
        if (!node.hasNonNull("ruc") || !node.hasNonNull("razonSocial")) {
            throw new RuntimeException("No se pudo obtener informacion para el RUC " + valor);
        }

        return new ConsultaRucResponse(
                node.path("ruc").asText(null),
                node.path("razonSocial").asText(null),
                node.path("nombreComercial").isNull() ? null : node.path("nombreComercial").asText(null),
                leerListaString(node, "telefonos"),
                node.path("tipo").isNull() ? null : node.path("tipo").asText(null),
                node.path("estado").isNull() ? null : node.path("estado").asText(null),
                node.path("condicion").isNull() ? null : node.path("condicion").asText(null),
                node.path("direccion").isNull() ? null : node.path("direccion").asText(null),
                node.path("departamento").isNull() ? null : node.path("departamento").asText(null),
                node.path("provincia").isNull() ? null : node.path("provincia").asText(null),
                node.path("distrito").isNull() ? null : node.path("distrito").asText(null),
                node.path("ubigeo").isNull() ? null : node.path("ubigeo").asText(null),
                node.path("capital").isNull() ? null : node.path("capital").asText(null));
    }

    private JsonNode consultar(String tipoDocumento, String numeroDocumento) {
        if (token == null || token.isBlank()) {
            throw new RuntimeException("No se configuro APIPERU_TOKEN");
        }

        URI uri = UriComponentsBuilder.fromUriString(baseUrl)
                .pathSegment(tipoDocumento, numeroDocumento)
                .queryParam("token", token)
                .build(true)
                .toUri();

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/json")
                .GET()
                .build();

        try {
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RuntimeException("Error consultando API de documentos (HTTP " + response.statusCode() + ")");
            }
            return OBJECT_MAPPER.readTree(response.body());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("No se pudo consultar el documento: " + e.getMessage());
        }
    }

    private String validarDocumento(String valor, int longitud, String etiqueta) {
        if (valor == null || valor.isBlank()) {
            throw new RuntimeException("Ingrese " + etiqueta);
        }
        String limpio = valor.trim();
        if (limpio.length() != longitud || !limpio.chars().allMatch(Character::isDigit)) {
            throw new RuntimeException(etiqueta + " invalido");
        }
        return limpio;
    }

    private String extraerMensajeError(JsonNode node) {
        JsonNode mensaje = node.path("message");
        if (mensaje.isMissingNode() || mensaje.isNull()) {
            return null;
        }
        return mensaje.asText(null);
    }

    private List<String> leerListaString(JsonNode node, String fieldName) {
        JsonNode listNode = node.path(fieldName);
        if (!listNode.isArray()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (JsonNode item : listNode) {
            if (!item.isNull()) {
                result.add(item.asText(""));
            }
        }
        return result;
    }
}
