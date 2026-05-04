package com.sistemapos.sistematextil.services;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import com.sistemapos.sistematextil.model.GuiaRemision;
import com.sistemapos.sistematextil.model.NotaCredito;
import com.sistemapos.sistematextil.model.SunatBajaLote;
import com.sistemapos.sistematextil.model.Venta;
import com.sistemapos.sistematextil.util.sunat.SunatComprobanteHelper;

import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Service
@RequiredArgsConstructor
public class SunatDocumentStorageService {

    private static final DateTimeFormatter YEAR_FORMAT = DateTimeFormatter.ofPattern("yyyy");
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("MM");

    private final ObjectProvider<S3Client> s3ClientProvider;
    private final S3StorageService storageService;

    @Value("${aws.s3.bucket}")
    private String defaultBucket;

    @Value("${aws.s3.sunat.bucket:}")
    private String configuredBucket;

    public StoredDocument storeXml(Venta venta, String fileName, byte[] bytes) {
        return upload(buildKey(venta, fileName), fileName, bytes, "application/xml");
    }

    public StoredDocument storeCdr(Venta venta, String fileName, byte[] bytes) {
        return upload(buildKey(venta, fileName), fileName, bytes, "application/zip");
    }

    public StoredDocument storeXml(NotaCredito notaCredito, String fileName, byte[] bytes) {
        return upload(buildKey(notaCredito, fileName), fileName, bytes, "application/xml");
    }

    public StoredDocument storeCdr(NotaCredito notaCredito, String fileName, byte[] bytes) {
        return upload(buildKey(notaCredito, fileName), fileName, bytes, "application/zip");
    }

    public StoredDocument storeGuiaRemisionXml(GuiaRemision guia, String fileName, byte[] bytes) {
        return upload(buildKey(guia, fileName), fileName, bytes, "application/xml");
    }

    public StoredDocument storeGuiaRemisionCdr(GuiaRemision guia, String fileName, byte[] bytes) {
        return upload(buildKey(guia, fileName), fileName, bytes, "application/zip");
    }

    public StoredDocument storeSunatBajaXml(SunatBajaLote lote, String fileName, byte[] bytes) {
        return upload(buildKey(lote, fileName), fileName, bytes, "application/xml");
    }

    public StoredDocument storeSunatBajaCdr(SunatBajaLote lote, String fileName, byte[] bytes) {
        return upload(buildKey(lote, fileName), fileName, bytes, "application/zip");
    }

    public byte[] download(String key) {
        if (key == null || key.isBlank()) {
            throw new RuntimeException("No hay archivo SUNAT registrado");
        }

        if (storageService.isManagedLocalReference(key)) {
            return storageService.readBytes(key);
        }

        S3Client s3Client = requireS3Client();
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(resolveBucket())
                .key(key.trim())
                .build();

        try {
            ResponseBytes<?> response = s3Client.getObjectAsBytes(request);
            return response.asByteArray();
        } catch (NoSuchKeyException e) {
            throw new RuntimeException("El archivo SUNAT no existe en S3");
        } catch (S3Exception e) {
            if ("NoSuchKey".equalsIgnoreCase(e.awsErrorDetails() != null ? e.awsErrorDetails().errorCode() : null)) {
                throw new RuntimeException("El archivo SUNAT no existe en S3");
            }
            throw new RuntimeException("No se pudo descargar el archivo SUNAT desde S3");
        }
    }

    public void deleteQuietly(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        if (storageService.isManagedLocalReference(key)) {
            try {
                storageService.deleteByUrl(key);
            } catch (Exception ignored) {
            }
            return;
        }

        S3Client s3Client = s3ClientProvider.getIfAvailable();
        if (s3Client == null) {
            return;
        }

        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(resolveBucket())
                    .key(key.trim())
                    .build());
        } catch (Exception ignored) {
        }
    }

    private StoredDocument upload(String key, String fileName, byte[] bytes, String contentType) {
        if (bytes == null || bytes.length == 0) {
            throw new RuntimeException("No hay contenido para guardar en disco local");
        }

        String storedPath = storageService.upload(bytes, key, contentType);
        return new StoredDocument(fileName, storedPath);
    }

    private String buildKey(Venta venta, String fileName) {
        String year = venta != null && venta.getFecha() != null
                ? venta.getFecha().format(YEAR_FORMAT)
                : "sin-fecha";
        String month = venta != null && venta.getFecha() != null
                ? venta.getFecha().format(MONTH_FORMAT)
                : "sin-mes";
        String tipo = sanitizeSegment(SunatComprobanteHelper.carpetaTipoComprobante(venta));

        return buildKey(tipo, year, month, fileName);
    }

    private String buildKey(NotaCredito notaCredito, String fileName) {
        String year = notaCredito != null && notaCredito.getFecha() != null
                ? notaCredito.getFecha().format(YEAR_FORMAT)
                : "sin-fecha";
        String month = notaCredito != null && notaCredito.getFecha() != null
                ? notaCredito.getFecha().format(MONTH_FORMAT)
                : "sin-mes";
        String tipo = sanitizeSegment(SunatComprobanteHelper.carpetaTipoComprobante(notaCredito));

        return buildKey(tipo, year, month, fileName);
    }

    private String buildKey(GuiaRemision guia, String fileName) {
        String year = guia != null && guia.getFechaEmision() != null
                ? guia.getFechaEmision().format(YEAR_FORMAT)
                : "sin-fecha";
        String month = guia != null && guia.getFechaEmision() != null
                ? guia.getFechaEmision().format(MONTH_FORMAT)
                : "sin-mes";

        return buildKey("guias-remision", year, month, fileName);
    }

    private String buildKey(SunatBajaLote lote, String fileName) {
        String year = lote != null && lote.getFechaGeneracion() != null
                ? lote.getFechaGeneracion().format(YEAR_FORMAT)
                : "sin-fecha";
        String month = lote != null && lote.getFechaGeneracion() != null
                ? lote.getFechaGeneracion().format(MONTH_FORMAT)
                : "sin-mes";
        String tipo = sanitizeSegment(SunatComprobanteHelper.carpetaTipoComprobante(lote));
        return buildKey(tipo, year, month, fileName);
    }

    private String buildKey(
            String tipo,
            String year,
            String month,
            String fileName) {
        String safeFileName = sanitizeFileName(fileName);

        return String.join("/",
                "sunat",
                sanitizeSegment(tipo),
                year,
                month,
                safeFileName);
    }

    private String resolveBucket() {
        if (configuredBucket != null && !configuredBucket.isBlank()) {
            return configuredBucket.trim();
        }
        if (defaultBucket == null || defaultBucket.isBlank()) {
            throw new RuntimeException("Configure aws.s3.bucket o aws.s3.sunat.bucket para acceder a documentos SUNAT heredados");
        }
        return defaultBucket.trim();
    }

    private S3Client requireS3Client() {
        S3Client s3Client = s3ClientProvider.getIfAvailable();
        if (s3Client == null) {
            throw new RuntimeException("No hay cliente AWS disponible para leer documentos SUNAT heredados");
        }
        return s3Client;
    }

    private String sanitizeSegment(String value) {
        if (value == null || value.isBlank()) {
            return "default";
        }
        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replace("\\", "-")
                .replace("/", "-")
                .replace(" ", "-");
    }

    private String sanitizeFileName(String value) {
        if (value == null || value.isBlank()) {
            return "archivo.bin";
        }
        return value.trim()
                .replace("\\", "-")
                .replace("/", "-")
                .replace(":", "-");
    }

    public record StoredDocument(
            String fileName,
            String key) {
    }
}
