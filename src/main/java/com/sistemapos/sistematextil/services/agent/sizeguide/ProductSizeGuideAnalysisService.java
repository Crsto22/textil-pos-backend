package com.sistemapos.sistematextil.services.agent.sizeguide;

import java.security.MessageDigest;
import java.util.HexFormat;

import org.springframework.stereotype.Service;

import com.sistemapos.sistematextil.services.S3StorageService;
import com.sistemapos.sistematextil.services.agent.sizeguide.JdbcProductSizeGuideStore.ClaimedJob;

@Service
public class ProductSizeGuideAnalysisService {
    private static final int MAX_BYTES = 12 * 1024 * 1024;

    private final JdbcProductSizeGuideStore store;
    private final ProductSizeGuideVisionGateway vision;
    private final ProductSizeGuideValidator validator;
    private final S3StorageService storage;

    public ProductSizeGuideAnalysisService(JdbcProductSizeGuideStore store,
            ProductSizeGuideVisionGateway vision, ProductSizeGuideValidator validator, S3StorageService storage) {
        this.store = store;
        this.vision = vision;
        this.validator = validator;
        this.storage = storage;
    }

    public void process(ClaimedJob job, int maxAttempts) {
        try {
            byte[] bytes = storage.readBytes(job.sourceUrl());
            if (bytes.length == 0 || bytes.length > MAX_BYTES) {
                throw new IllegalArgumentException("La guia esta vacia o supera 12 MB");
            }
            if (!hash(bytes).equalsIgnoreCase(job.sourceHash())) {
                throw new IllegalStateException("La imagen de la guia cambio mientras se procesaba");
            }
            var result = vision.extract(job.connectionId(), bytes, mimeType(job.sourceUrl()));
            var validated = validator.validate(result.extraction());
            store.complete(job, result, validated);
        } catch (RuntimeException error) {
            store.fail(job, error, maxAttempts);
        }
    }

    public static String hash(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception error) {
            throw new IllegalStateException("No se pudo calcular el hash de la guia", error);
        }
    }

    static String mimeType(String url) {
        String value = url == null ? "" : url.toLowerCase();
        if (value.contains(".png")) return "image/png";
        if (value.contains(".webp")) return "image/webp";
        return "image/jpeg";
    }
}
