package com.sistemapos.sistematextil.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "brevo")
public record BrevoProperties(
        boolean enabled,
        String apiKey,
        String senderEmail,
        String senderName,
        String replyToEmail) {
}
