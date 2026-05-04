package com.sistemapos.sistematextil.config;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class StorageWebConfig implements WebMvcConfigurer {

    private final StorageProperties storageProperties;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path basePath = resolveBasePath();

        registry.addResourceHandler("/storage/empresa/**")
                .addResourceLocations(directoryLocation(basePath.resolve("empresa")));
        registry.addResourceHandler("/storage/productos/**")
                .addResourceLocations(directoryLocation(basePath.resolve("productos")));
        registry.addResourceHandler("/storage/usuarios/**")
                .addResourceLocations(directoryLocation(basePath.resolve("usuarios")));
    }

    private Path resolveBasePath() {
        String configured = storageProperties.getBasePath();
        if (configured == null || configured.isBlank()) {
            return Paths.get("storage").toAbsolutePath().normalize();
        }
        return Paths.get(configured.trim()).toAbsolutePath().normalize();
    }

    private String directoryLocation(Path path) {
        String location = path.toUri().toString();
        return location.endsWith("/") ? location : location + "/";
    }
}
