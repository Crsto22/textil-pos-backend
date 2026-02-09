package com.sistemapos.sistematextil.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class CorsConfig {

    @Value("${cors.allowed-origins}")
    private String allowedOrigins;

    @Value("${cors.allowed-methods}")
    private String allowedMethods;

    @Value("${cors.max-age}")
    private Long maxAge;

    @Bean
    public UrlBasedCorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Orígenes permitidos (NO usar "*" cuando allowCredentials=true)
        // En dev: http://localhost:3000 (Next.js), http://localhost:5173 (Vite)
        configuration.setAllowedOriginPatterns(Arrays.asList(allowedOrigins.split(",")));

        // Métodos HTTP permitidos
        configuration.setAllowedMethods(Arrays.asList(allowedMethods.split(",")));

        // Headers permitidos
        configuration.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "Accept",
                "X-Requested-With",
                "Cache-Control"
        ));

        // Headers expuestos al cliente
        configuration.setExposedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "Set-Cookie"
        ));

        // CRÍTICO: true para que el navegador envíe/reciba cookies cross-origin
        configuration.setAllowCredentials(true);

        configuration.setMaxAge(maxAge);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
