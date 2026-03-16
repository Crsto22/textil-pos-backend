package com.sistemapos.sistematextil.config;

import java.util.Locale;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "sunat")
public class SunatProperties {

    private String mode = "disabled";
    private String certBasePath = "storage/sunat/certificados";
    private int certMaxFileSizeMb = 5;
    private String cryptoKey = "";

    public String normalizedMode() {
        if (mode == null || mode.isBlank()) {
            return "DISABLED";
        }
        return mode.trim().toUpperCase(Locale.ROOT);
    }
}
