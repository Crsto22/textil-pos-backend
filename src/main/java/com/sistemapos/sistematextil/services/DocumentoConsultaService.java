package com.sistemapos.sistematextil.services;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sistemapos.sistematextil.util.documento.ConsultaDniResponse;
import com.sistemapos.sistematextil.util.documento.ConsultaRucResponse;

@Service
public class DocumentoConsultaService {

    private static final String DEFAULT_SUNAT_CONSULTA_BASE_URL =
            "https://ww1.sunat.gob.pe/ol-ti-itfisdenreg/itfisdenreg.htm";
    private static final List<String> APELLIDO_CONNECTORS = List.of(
            "DA", "DAS", "DE", "DEL", "DI", "DO", "DOS", "LA", "LAS", "LOS", "MAC", "MC", "SAN", "SANTA", "VAN",
            "VON");
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final String baseUrl;

    public DocumentoConsultaService(
            @Value("${external.sunat-consulta.base-url:" + DEFAULT_SUNAT_CONSULTA_BASE_URL + "}") String baseUrl) {
        this.baseUrl = (baseUrl == null || baseUrl.isBlank())
                ? DEFAULT_SUNAT_CONSULTA_BASE_URL
                : baseUrl.trim();
    }

    public ConsultaDniResponse consultarDni(String dni) {
        String valor = validarDocumento(dni, 8, "DNI");
        JsonNode node = consultar("obtenerDatosDni", "numDocumento", valor);
        JsonNode item = extraerPrimerResultado(node, "DNI", valor);

        String nombresApellidos = limpiarTexto(item.path("nombresapellidos").asText(null));
        if (nombresApellidos == null || nombresApellidos.isBlank()) {
            throw new RuntimeException("No se pudo obtener informacion para el DNI " + valor);
        }
        NombrePersona nombrePersona = parsearNombrePersona(nombresApellidos);

        return new ConsultaDniResponse(
                true,
                valor,
                nombrePersona.nombres(),
                nombrePersona.apellidoPaterno(),
                nombrePersona.apellidoMaterno(),
                null,
                null);
    }

    public ConsultaRucResponse consultarRuc(String ruc) {
        String valor = validarDocumento(ruc, 11, "RUC");
        JsonNode node = consultar("obtenerDatosRuc", "nroRuc", valor);
        JsonNode item = extraerPrimerResultado(node, "RUC", valor);

        String razonSocial = limpiarTexto(item.path("apenomdenunciado").asText(null));
        if (razonSocial == null || razonSocial.isBlank()) {
            throw new RuntimeException("No se pudo obtener informacion para el RUC " + valor);
        }

        return new ConsultaRucResponse(
                valor,
                razonSocial,
                null,
                List.of(),
                null,
                null,
                null,
                limpiarTexto(item.path("direstablecimiento").asText(null)),
                limpiarTexto(item.path("desdepartamento").asText(null)),
                limpiarTexto(item.path("desprovincia").asText(null)),
                limpiarTexto(item.path("desdistrito").asText(null)),
                construirUbigeo(
                        limpiarTexto(item.path("iddepartamento").asText(null)),
                        limpiarTexto(item.path("idprovincia").asText(null)),
                        limpiarTexto(item.path("iddistrito").asText(null))),
                null);
    }

    private JsonNode consultar(String accion, String nombreParametro, String numeroDocumento) {
        URI uri = UriComponentsBuilder.fromUriString(baseUrl)
                .queryParam("accion", accion)
                .queryParam(nombreParametro, numeroDocumento)
                .build(true)
                .toUri();

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/json")
                .GET()
                .build();

        try {
            HttpResponse<String> response = HTTP_CLIENT.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RuntimeException(
                        "Error consultando SUNAT (HTTP " + response.statusCode() + "): "
                                + limitarTexto(response.body(), 200));
            }
            JsonNode node = OBJECT_MAPPER.readTree(response.body());
            String mensajeError = limpiarTexto(node.path("error").asText(null));
            if (mensajeError != null && !mensajeError.isBlank()) {
                throw new RuntimeException(mensajeError);
            }
            return node;
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

    private JsonNode extraerPrimerResultado(JsonNode node, String etiqueta, String numeroDocumento) {
        JsonNode lista = node.path("lista");
        if (!lista.isArray() || lista.size() == 0 || lista.get(0) == null || lista.get(0).isNull()) {
            throw new RuntimeException("No se pudo obtener informacion para el " + etiqueta + " " + numeroDocumento);
        }
        return lista.get(0);
    }

    private NombrePersona parsearNombrePersona(String nombresApellidos) {
        String[] partes = nombresApellidos.split(",", 2);
        String apellidos = limpiarTexto(partes[0]);
        String nombres = partes.length > 1 ? limpiarTexto(partes[1]) : null;

        if (apellidos == null || apellidos.isBlank()) {
            return new NombrePersona(nombres, null, null);
        }
        if (nombres == null || nombres.isBlank()) {
            return new NombrePersona(apellidos, null, null);
        }

        ApellidosPersona apellidosPersona = separarApellidos(apellidos);
        return new NombrePersona(nombres, apellidosPersona.apellidoPaterno(), apellidosPersona.apellidoMaterno());
    }

    private ApellidosPersona separarApellidos(String apellidosCompletos) {
        String normalizado = limpiarTexto(apellidosCompletos);
        if (normalizado == null || normalizado.isBlank()) {
            return new ApellidosPersona(null, null);
        }

        String[] tokens = normalizado.split(" ");
        if (tokens.length == 1) {
            return new ApellidosPersona(normalizado, null);
        }

        // SUNAT devuelve ambos apellidos juntos; separamos de derecha a izquierda para
        // conservar mejor apellidos compuestos como "DE LA CRUZ" o "SAN MARTIN".
        int inicioApellidoMaterno = tokens.length - 1;
        while (inicioApellidoMaterno > 0 && esConectorApellido(tokens[inicioApellidoMaterno - 1])) {
            inicioApellidoMaterno--;
        }

        String apellidoPaterno = unirTokens(tokens, 0, inicioApellidoMaterno);
        String apellidoMaterno = unirTokens(tokens, inicioApellidoMaterno, tokens.length);
        if (apellidoPaterno == null || apellidoPaterno.isBlank()) {
            return new ApellidosPersona(apellidoMaterno, null);
        }
        return new ApellidosPersona(apellidoPaterno, apellidoMaterno);
    }

    private boolean esConectorApellido(String token) {
        return token != null && APELLIDO_CONNECTORS.contains(token.toUpperCase(Locale.ROOT));
    }

    private String unirTokens(String[] tokens, int inicio, int fin) {
        if (tokens == null || inicio < 0 || fin > tokens.length || inicio >= fin) {
            return null;
        }
        return limpiarTexto(String.join(" ", java.util.Arrays.copyOfRange(tokens, inicio, fin)));
    }

    private String construirUbigeo(String idDepartamento, String idProvincia, String idDistrito) {
        if (esCodigoUbigeo(idDepartamento) && esCodigoUbigeo(idProvincia) && esCodigoUbigeo(idDistrito)) {
            return idDepartamento + idProvincia + idDistrito;
        }
        return null;
    }

    private boolean esCodigoUbigeo(String valor) {
        return valor != null && valor.matches("\\d{2}");
    }

    private String limpiarTexto(String valor) {
        if (valor == null) {
            return null;
        }
        String limpio = valor.trim().replaceAll("\\s+", " ");
        return limpio.isEmpty() ? null : limpio;
    }

    private String limitarTexto(String valor, int maxLen) {
        if (valor == null) {
            return "";
        }
        return valor.length() > maxLen ? valor.substring(0, maxLen) + "..." : valor;
    }

    private record NombrePersona(String nombres, String apellidoPaterno, String apellidoMaterno) {
    }

    private record ApellidosPersona(String apellidoPaterno, String apellidoMaterno) {
    }
}
