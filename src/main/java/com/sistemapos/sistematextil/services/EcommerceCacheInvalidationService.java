package com.sistemapos.sistematextil.services;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class EcommerceCacheInvalidationService {

    private static final String TX_RESOURCE_KEY = EcommerceCacheInvalidationService.class.getName() + ".pending";
    private static final Duration TIMEOUT = Duration.ofSeconds(2);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(TIMEOUT)
            .build();

    @Value("${ecommerce.cache.revalidate-url:}")
    private String revalidateUrl;

    @Value("${ecommerce.cache.revalidate-secret:}")
    private String revalidateSecret;

    public void invalidate() {
        if (!enabled()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            send();
            return;
        }
        if (TransactionSynchronizationManager.hasResource(TX_RESOURCE_KEY)) {
            return;
        }
        TransactionSynchronizationManager.bindResource(TX_RESOURCE_KEY, Boolean.TRUE);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                send();
            }

            @Override
            public void afterCompletion(int status) {
                if (TransactionSynchronizationManager.hasResource(TX_RESOURCE_KEY)) {
                    TransactionSynchronizationManager.unbindResource(TX_RESOURCE_KEY);
                }
            }
        });
    }

    private boolean enabled() {
        return revalidateUrl != null && !revalidateUrl.isBlank()
                && revalidateSecret != null && !revalidateSecret.isBlank();
    }

    private void send() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(revalidateUrl.trim()))
                    .timeout(TIMEOUT)
                    .header("x-revalidate-secret", revalidateSecret)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{}"))
                    .build();
            httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                    .thenAccept(response -> {
                        if (response.statusCode() >= 400) {
                            log.warn("Invalidacion cache ecommerce fallo con HTTP {}", response.statusCode());
                        }
                    })
                    .exceptionally(error -> {
                        log.warn("No se pudo invalidar cache ecommerce: {}", error.getMessage());
                        return null;
                    });
        } catch (RuntimeException e) {
            log.warn("No se pudo preparar invalidacion cache ecommerce: {}", e.getMessage());
        }
    }
}
