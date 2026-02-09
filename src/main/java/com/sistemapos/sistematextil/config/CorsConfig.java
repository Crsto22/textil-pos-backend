package com.sistemapos.sistematextil.config;

import java.util.Arrays;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Configuración de CORS (Cross-Origin Resource Sharing) para Spring Boot 4.0.1 y Java 21.
 *
 * Esta clase es OPCIONAL. Si prefieres tener CORS configurado en SecurityConfig directamente,
 * puedes eliminar esta clase y usar el método corsConfigurationSource() en SecurityConfig.
 *
 * Ventajas de esta clase separada:
 * - Separación de responsabilidades (CORS aparte de Security)
 * - Más fácil de testear individualmente
 * - Configuración externalizada en application.properties
 */
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

        // Orígenes permitidos desde application.properties
        // Usar setAllowedOriginPatterns en lugar de setAllowedOrigins para soportar "*" con credentials
        configuration.setAllowedOriginPatterns(Arrays.asList(allowedOrigins.split(",")));

        // Métodos HTTP permitidos desde application.properties
        configuration.setAllowedMethods(Arrays.asList(allowedMethods.split(",")));

        // Headers permitidos (específicos para seguridad)
        configuration.setAllowedHeaders(Arrays.asList(
            "Authorization",
            "Content-Type",
            "Accept",
            "X-Requested-With",
            "Cache-Control"
        ));

        // Headers expuestos al cliente
        configuration.setExposedHeaders(Arrays.asList(
            "Authorization",
            "Content-Type"
        ));

        // Permitir credenciales
        configuration.setAllowCredentials(true);

        // Tiempo de caché para preflight requests desde application.properties
        configuration.setMaxAge(maxAge);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
