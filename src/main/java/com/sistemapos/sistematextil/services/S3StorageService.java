package com.sistemapos.sistematextil.services;

import java.net.URI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
@RequiredArgsConstructor
public class S3StorageService {

    private final S3Client s3Client;

    @Value("${aws.s3.bucket}")
    private String bucket;

    @Value("${aws.region:us-east-1}")
    private String region;

    public String upload(byte[] bytes, String key, String contentType) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .cacheControl("public, max-age=31536000")
                .build();
        s3Client.putObject(request, RequestBody.fromBytes(bytes));
        return buildPublicUrl(key);
    }

    public void deleteByUrl(String fileUrl) {
        String key = extractKeyFromUrl(fileUrl);
        if (key == null || key.isBlank()) {
            return;
        }
        deleteByKey(key);
    }

    public void deleteByKey(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();
        s3Client.deleteObject(request);
    }

    private String buildPublicUrl(String key) {
        String host = "us-east-1".equalsIgnoreCase(region)
                ? "s3.amazonaws.com"
                : "s3." + region + ".amazonaws.com";
        return "https://" + bucket + "." + host + "/" + key;
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
