package com.sistemapos.sistematextil.services;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.sistemapos.sistematextil.config.StorageProperties;

import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

@Service
@RequiredArgsConstructor
public class S3StorageService {

    private static final String PUBLIC_PREFIX = "/storage/";

    private final ObjectProvider<S3Client> s3ClientProvider;
    private final StorageProperties storageProperties;

    @Value("${aws.s3.bucket}")
    private String bucket;

    public String upload(byte[] bytes, String key, String contentType) {
        if (bytes == null || bytes.length == 0) {
            throw new RuntimeException("No hay contenido para guardar en disco local");
        }

        String normalizedKey = normalizeKey(key);
        Path destination = resolveRelativeKey(normalizedKey);

        try {
            Path parent = destination.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(destination, bytes);
            return buildManagedPath(normalizedKey);
        } catch (IOException e) {
            throw new RuntimeException("No se pudo guardar el archivo en disco local");
        }
    }

    public void deleteByUrl(String fileUrl) {
        if (isManagedLocalReference(fileUrl)) {
            deleteLocalFile(fileUrl);
            return;
        }

        String key = extractKeyFromUrl(fileUrl);
        if (key == null || key.isBlank()) {
            return;
        }
        deleteLegacyKey(key);
    }

    public void deleteByKey(String key) {
        if (key == null || key.isBlank()) {
            return;
        }

        if (isManagedLocalReference(key)) {
            deleteLocalFile(key);
            return;
        }

        deleteLegacyKey(key.trim());
    }

    public InputStream openStream(String storedReference) {
        if (storedReference == null || storedReference.isBlank()) {
            throw new RuntimeException("No hay archivo registrado");
        }

        String reference = storedReference.trim();
        try {
            if (isHttpUrl(reference)) {
                return URI.create(reference).toURL().openStream();
            }
            return Files.newInputStream(resolveLocalPath(reference));
        } catch (IOException | IllegalArgumentException e) {
            throw new RuntimeException("No se pudo acceder al archivo almacenado");
        }
    }

    public byte[] readBytes(String storedReference) {
        try (InputStream input = openStream(storedReference)) {
            return input.readAllBytes();
        } catch (IOException e) {
            throw new RuntimeException("No se pudo leer el archivo almacenado");
        }
    }

    public Instant getLastModified(String storedReference) {
        if (!isManagedLocalReference(storedReference)) {
            return null;
        }
        try {
            return Files.getLastModifiedTime(resolveLocalPath(storedReference.trim())).toInstant();
        } catch (IOException e) {
            return null;
        }
    }

    public boolean isManagedLocalReference(String storedReference) {
        if (storedReference == null || storedReference.isBlank()) {
            return false;
        }

        String reference = storedReference.trim();
        if (reference.startsWith(PUBLIC_PREFIX)) {
            return true;
        }

        try {
            Path path = Paths.get(reference).toAbsolutePath().normalize();
            return path.startsWith(resolveBasePath());
        } catch (InvalidPathException e) {
            return false;
        }
    }

    private void deleteLocalFile(String storedReference) {
        try {
            Files.deleteIfExists(resolveLocalPath(storedReference.trim()));
        } catch (IOException e) {
            throw new RuntimeException("No se pudo eliminar el archivo local");
        }
    }

    private void deleteLegacyKey(String key) {
        S3Client s3Client = s3ClientProvider.getIfAvailable();
        if (s3Client == null || bucket == null || bucket.isBlank()) {
            return;
        }

        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(key.trim())
                .build();
        s3Client.deleteObject(request);
    }

    private Path resolveRelativeKey(String key) {
        Path basePath = resolveBasePath();
        Path destination = basePath.resolve(key).normalize();
        if (!destination.startsWith(basePath)) {
            throw new RuntimeException("Ruta de almacenamiento invalida");
        }
        return destination;
    }

    private Path resolveLocalPath(String storedReference) {
        String reference = storedReference.trim();
        if (reference.startsWith(PUBLIC_PREFIX)) {
            String relativePath = reference.substring(PUBLIC_PREFIX.length());
            return resolveRelativeKey(relativePath);
        }

        try {
            Path path = Paths.get(reference).toAbsolutePath().normalize();
            if (!path.startsWith(resolveBasePath())) {
                throw new RuntimeException("La ruta del archivo no pertenece al almacenamiento administrado");
            }
            return path;
        } catch (InvalidPathException e) {
            throw new RuntimeException("Ruta de archivo invalida");
        }
    }

    private Path resolveBasePath() {
        String configured = storageProperties.getBasePath();
        Path path = configured == null || configured.isBlank()
                ? Paths.get("storage")
                : Paths.get(configured.trim());
        return path.toAbsolutePath().normalize();
    }

    private String buildManagedPath(String key) {
        return PUBLIC_PREFIX + key;
    }

    private String normalizeKey(String key) {
        if (key == null || key.isBlank()) {
            throw new RuntimeException("La ruta del archivo es obligatoria");
        }
        return key.trim()
                .replace("\\", "/")
                .replaceAll("^/+", "");
    }

    private boolean isHttpUrl(String value) {
        return value.startsWith("http://") || value.startsWith("https://");
    }

    private String extractKeyFromUrl(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(fileUrl.trim());
            String host = uri.getHost();
            if (host == null || !host.startsWith(bucket + ".")) {
                return null;
            }
            String path = uri.getPath();
            if (path == null || path.isBlank() || "/".equals(path)) {
                return null;
            }
            return path.startsWith("/") ? path.substring(1) : path;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
