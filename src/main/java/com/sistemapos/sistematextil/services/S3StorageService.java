package com.sistemapos.sistematextil.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
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

    private String buildPublicUrl(String key) {
        String host = "us-east-1".equalsIgnoreCase(region)
                ? "s3.amazonaws.com"
                : "s3." + region + ".amazonaws.com";
        return "https://" + bucket + "." + host + "/" + key;
    }
}
