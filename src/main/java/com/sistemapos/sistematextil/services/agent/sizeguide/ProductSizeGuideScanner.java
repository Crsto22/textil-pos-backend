package com.sistemapos.sistematextil.services.agent.sizeguide;

import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.sistemapos.sistematextil.model.CrmWhatsappAiConfig;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAiConfigRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappConnectionRepository;
import com.sistemapos.sistematextil.services.CrmWhatsappAiCredentialService;
import com.sistemapos.sistematextil.services.S3StorageService;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class ProductSizeGuideScanner {
    private static final long INACCESSIBLE_SOURCE_RETRY_MS = 6L * 60 * 60 * 1000;

    private final JdbcProductSizeGuideStore store;
    private final S3StorageService storage;
    private final CrmWhatsappAiConfigRepository configs;
    private final CrmWhatsappConnectionRepository connections;
    private final CrmWhatsappAiCredentialService credentials;
    private final Map<String, Long> inaccessibleUntil = new ConcurrentHashMap<>();

    public ProductSizeGuideScanner(JdbcProductSizeGuideStore store, S3StorageService storage,
            CrmWhatsappAiConfigRepository configs, CrmWhatsappConnectionRepository connections,
            CrmWhatsappAiCredentialService credentials) {
        this.store = store;
        this.storage = storage;
        this.configs = configs;
        this.connections = connections;
        this.credentials = credentials;
    }

    @Scheduled(initialDelayString = "${crm.whatsapp.agent.size-guide.scan-initial-delay-ms:15000}",
            fixedDelayString = "${crm.whatsapp.agent.size-guide.scan-delay-ms:60000}")
    public void scan() {
        Long connectionId = resolveConnectionId();
        if (connectionId == null) return;
        for (var product : store.productsWithGuide()) {
            String sourceKey = product.productId() + ":" + product.sourceUrl();
            if (inaccessibleUntil.getOrDefault(sourceKey, 0L) > System.currentTimeMillis()) continue;
            try {
                byte[] bytes = storage.readBytes(product.sourceUrl());
                if (bytes.length == 0 || bytes.length > 12 * 1024 * 1024) continue;
                String hash = ProductSizeGuideAnalysisService.hash(bytes);
                if (store.alreadyTracksHash(product.productId(), hash)) continue;
                store.enqueue(product.productId(), connectionId, product.sourceUrl(), hash, false);
                inaccessibleUntil.remove(sourceKey);
            } catch (RuntimeException error) {
                inaccessibleUntil.put(sourceKey, System.currentTimeMillis() + INACCESSIBLE_SOURCE_RETRY_MS);
                log.debug("No se pudo acceder a la guia de tallas del producto {}: {}",
                        product.productId(), error.getMessage());
            }
        }
    }

    public void reanalyze(Integer productId) {
        Long connectionId = resolveConnectionId();
        if (connectionId == null) throw new IllegalStateException("No existe una credencial Gemini disponible");
        var source = store.productsWithGuide().stream().filter(item -> item.productId().equals(productId))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("El producto no tiene guia de tallas"));
        byte[] bytes = storage.readBytes(source.sourceUrl());
        store.enqueue(productId, connectionId, source.sourceUrl(),
                ProductSizeGuideAnalysisService.hash(bytes), true);
    }

    private Long resolveConnectionId() {
        var configured = configs.findAll().stream()
                .filter(item -> item.getConnection() != null && item.getConnection().getIdConnection() != null)
                .sorted(Comparator.comparing((CrmWhatsappAiConfig item) -> !Boolean.TRUE.equals(item.getAgentEnabled()))
                        .thenComparing(item -> item.getConnection().getIdConnection()))
                .map(item -> item.getConnection().getIdConnection())
                .filter(credentials::hasUsableCredential).findFirst();
        if (configured.isPresent()) return configured.get();
        return connections.findAll().stream().map(item -> item.getIdConnection())
                .filter(credentials::hasUsableCredential).findFirst().orElse(null);
    }
}
