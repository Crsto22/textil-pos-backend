package com.sistemapos.sistematextil.services;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.sistemapos.sistematextil.model.NotaCredito;
import com.sistemapos.sistematextil.model.Venta;
import com.sistemapos.sistematextil.util.sunat.SunatComprobanteHelper;

import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Service
@RequiredArgsConstructor
public class SunatDocumentStorageService {

    private static final DateTimeFormatter YEAR_FORMAT = DateTimeFormatter.ofPattern("yyyy");
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("MM");

    private final S3Client s3Client;

    @Value("${aws.s3.bucket}")
    private String defaultBucket;

    @Value("${aws.s3.sunat.bucket:}")
    private String configuredBucket;

    @Value("${aws.s3.sunat.prefix:sunat}")
    private String keyPrefix;

    public StoredDocument storeXml(Venta venta, String fileName, byte[] bytes) {
        return upload(venta, "xml", fileName, bytes, "application/xml");
    }

    public StoredDocument storeZip(Venta venta, String fileName, byte[] bytes) {
        return upload(venta, "zip", fileName, bytes, "application/zip");
    }

    public StoredDocument storeCdr(Venta venta, String fileName, byte[] bytes) {
        return upload(venta, "cdr", fileName, bytes, "application/xml");
    }

    public StoredDocument storeXml(NotaCredito notaCredito, String fileName, byte[] bytes) {
        return upload(notaCredito, "xml", fileName, bytes, "application/xml");
    }

    public StoredDocument storeZip(NotaCredito notaCredito, String fileName, byte[] bytes) {
        return upload(notaCredito, "zip", fileName, bytes, "application/zip");
    }

    public StoredDocument storeCdr(NotaCredito notaCredito, String fileName, byte[] bytes) {
        return upload(notaCredito, "cdr", fileName, bytes, "application/xml");
    }

    public byte[] download(String key) {
        if (key == null || key.isBlank()) {
            throw new RuntimeException("No hay archivo SUNAT registrado");
        }

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
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(resolveBucket())
                    .key(key.trim())
                    .build());
        } catch (Exception ignored) {
        }
    }

    public StoredUploadPair storeXmlAndZip(Venta venta, String xmlName, byte[] xmlBytes, String zipName, byte[] zipBytes) {
        List<String> uploadedKeys = new ArrayList<>();
        try {
            StoredDocument xml = storeXml(venta, xmlName, xmlBytes);
            uploadedKeys.add(xml.key());
            StoredDocument zip = storeZip(venta, zipName, zipBytes);
            uploadedKeys.add(zip.key());
            return new StoredUploadPair(xml, zip);
        } catch (RuntimeException e) {
            uploadedKeys.forEach(this::deleteQuietly);
            throw e;
        }
    }

    public StoredUploadPair storeXmlAndZip(
            NotaCredito notaCredito,
            String xmlName,
            byte[] xmlBytes,
            String zipName,
            byte[] zipBytes) {
        List<String> uploadedKeys = new ArrayList<>();
        try {
            StoredDocument xml = storeXml(notaCredito, xmlName, xmlBytes);
            uploadedKeys.add(xml.key());
            StoredDocument zip = storeZip(notaCredito, zipName, zipBytes);
            uploadedKeys.add(zip.key());
            return new StoredUploadPair(xml, zip);
        } catch (RuntimeException e) {
            uploadedKeys.forEach(this::deleteQuietly);
            throw e;
        }
    }

    private StoredDocument upload(Venta venta, String folder, String fileName, byte[] bytes, String contentType) {
        if (bytes == null || bytes.length == 0) {
            throw new RuntimeException("No hay contenido para guardar en S3");
        }
        String key = buildKey(venta, folder, fileName);
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(resolveBucket())
                .key(key)
                .contentType(contentType)
                .contentDisposition("attachment; filename=\"" + fileName + "\"")
                .build();
        try {
            s3Client.putObject(request, RequestBody.fromBytes(bytes));
            return new StoredDocument(fileName, key);
        } catch (S3Exception e) {
            throw new RuntimeException("No se pudo guardar el archivo SUNAT en S3");
        }
    }

    private StoredDocument upload(NotaCredito notaCredito, String folder, String fileName, byte[] bytes, String contentType) {
        if (bytes == null || bytes.length == 0) {
            throw new RuntimeException("No hay contenido para guardar en S3");
        }
        String key = buildKey(notaCredito, folder, fileName);
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(resolveBucket())
                .key(key)
                .contentType(contentType)
                .contentDisposition("attachment; filename=\"" + fileName + "\"")
                .build();
        try {
            s3Client.putObject(request, RequestBody.fromBytes(bytes));
            return new StoredDocument(fileName, key);
        } catch (S3Exception e) {
            throw new RuntimeException("No se pudo guardar el archivo SUNAT en S3");
        }
    }

    private String buildKey(Venta venta, String folder, String fileName) {
        String ruc = venta != null
                && venta.getSucursal() != null
                && venta.getSucursal().getEmpresa() != null
                && venta.getSucursal().getEmpresa().getRuc() != null
                && !venta.getSucursal().getEmpresa().getRuc().isBlank()
                        ? venta.getSucursal().getEmpresa().getRuc().trim()
                        : "sin-ruc";
        String year = venta != null && venta.getFecha() != null
                ? venta.getFecha().format(YEAR_FORMAT)
                : "sin-fecha";
        String month = venta != null && venta.getFecha() != null
                ? venta.getFecha().format(MONTH_FORMAT)
                : "sin-mes";
        String tipo = sanitizeSegment(SunatComprobanteHelper.carpetaTipoComprobante(venta));
        String numero = venta == null ? "sin-numero" : sanitizeSegment(SunatComprobanteHelper.numeroComprobante(venta));

        return buildKey(ruc, year, month, tipo, numero, folder, fileName);
    }

    private String buildKey(NotaCredito notaCredito, String folder, String fileName) {
        String ruc = notaCredito != null
                && notaCredito.getSucursal() != null
                && notaCredito.getSucursal().getEmpresa() != null
                && notaCredito.getSucursal().getEmpresa().getRuc() != null
                && !notaCredito.getSucursal().getEmpresa().getRuc().isBlank()
                        ? notaCredito.getSucursal().getEmpresa().getRuc().trim()
                        : "sin-ruc";
        String year = notaCredito != null && notaCredito.getFecha() != null
                ? notaCredito.getFecha().format(YEAR_FORMAT)
                : "sin-fecha";
        String month = notaCredito != null && notaCredito.getFecha() != null
                ? notaCredito.getFecha().format(MONTH_FORMAT)
                : "sin-mes";
        String tipo = sanitizeSegment(SunatComprobanteHelper.carpetaTipoComprobante(notaCredito));
        String numero = notaCredito == null
                ? "sin-numero"
                : sanitizeSegment(SunatComprobanteHelper.numeroComprobante(notaCredito));

        return buildKey(ruc, year, month, tipo, numero, folder, fileName);
    }

    private String buildKey(
            String ruc,
            String year,
            String month,
            String tipo,
            String numero,
            String folder,
            String fileName) {
        String prefix = keyPrefix == null || keyPrefix.isBlank() ? "sunat" : keyPrefix.trim();
        String safeFileName = sanitizeFileName(fileName);

        return String.join("/",
                sanitizeSegment(prefix),
                sanitizeSegment(ruc),
                year,
                month,
                tipo,
                numero,
                sanitizeSegment(folder),
                safeFileName);
    }

    private String resolveBucket() {
        if (configuredBucket != null && !configuredBucket.isBlank()) {
            return configuredBucket.trim();
        }
        if (defaultBucket == null || defaultBucket.isBlank()) {
            throw new RuntimeException("Configure aws.s3.bucket o aws.s3.sunat.bucket para guardar documentos SUNAT");
        }
        return defaultBucket.trim();
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

    public record StoredUploadPair(
            StoredDocument xml,
            StoredDocument zip) {
    }
}
