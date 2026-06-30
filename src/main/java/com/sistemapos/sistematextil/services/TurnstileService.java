package com.sistemapos.sistematextil.services;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sistemapos.sistematextil.config.TurnstileProperties;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TurnstileService {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private static final ObjectMapper JSON = new ObjectMapper();

    private final TurnstileProperties properties;

    public void validar(String token, String ipCliente) {
        if (!properties.enabled()) {
            return;
        }
        if (isBlank(properties.secretKey()) || isBlank(token)) {
            throw new RuntimeException("Verificacion de seguridad invalida");
        }

        try {
            String body = "secret=" + enc(properties.secretKey())
                    + "&response=" + enc(token)
                    + (isBlank(ipCliente) ? "" : "&remoteip=" + enc(ipCliente));
            HttpRequest request = HttpRequest.newBuilder(URI.create(properties.siteverifyUrl()))
                    .timeout(Duration.ofSeconds(8))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode json = JSON.readTree(response.body());
            if (!json.path("success").asBoolean(false)) {
                throw new RuntimeException("Verificacion de seguridad invalida");
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("No se pudo validar la verificacion de seguridad");
        }
    }

    private String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
