package com.sistemapos.sistematextil.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/storage").permitAll()
                        .requestMatchers("/storage/empresa/**", "/storage/productos/**", "/storage/usuarios/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/public/ecommerce/**").permitAll()
                        .requestMatchers("/api/auth/autenticarse", "/api/auth/refresh").permitAll()
                        .requestMatchers("/api/auth/registro").hasAnyAuthority("ADMINISTRADOR", "SISTEMA")
                        .requestMatchers("/api/auth/logout").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/auth/me").authenticated()
                        .requestMatchers("/api/auth/cambiar-password", "/api/auth/foto-perfil")
                        .hasAnyAuthority("ADMINISTRADOR", "VENTAS", "ALMACEN", "VENTAS_ALMACEN", "SISTEMA")
                        .requestMatchers("/api/dashboard")
                        .hasAnyAuthority("ADMINISTRADOR", "VENTAS", "ALMACEN", "VENTAS_ALMACEN", "SISTEMA")
                        .requestMatchers("/api/turno/**").hasAnyAuthority("ADMINISTRADOR", "SISTEMA")
                        .requestMatchers("/api/usuario/**").hasAnyAuthority("ADMINISTRADOR", "SISTEMA")
                        .requestMatchers("/api/cliente/**").hasAnyAuthority("ADMINISTRADOR", "VENTAS", "SISTEMA")
                        .requestMatchers(HttpMethod.GET, "/api/sucursal/**").hasAnyAuthority("ADMINISTRADOR", "ALMACEN", "VENTAS_ALMACEN", "SISTEMA")
                        .requestMatchers("/api/sucursal/**").hasAnyAuthority("ADMINISTRADOR", "SISTEMA")
                        .requestMatchers(HttpMethod.GET, "/api/empresa/publico").permitAll()
                        .requestMatchers("/api/empresa/**").hasAnyAuthority("ADMINISTRADOR", "SISTEMA")
                        .requestMatchers(HttpMethod.GET, "/api/talla/**")
                        .hasAnyAuthority("ADMINISTRADOR", "ALMACEN", "VENTAS", "SISTEMA")
                        .requestMatchers("/api/talla/**").hasAnyAuthority("ADMINISTRADOR", "ALMACEN", "SISTEMA")
                        .requestMatchers(HttpMethod.GET, "/api/color/**")
                        .hasAnyAuthority("ADMINISTRADOR", "ALMACEN", "VENTAS", "SISTEMA")
                        .requestMatchers("/api/color/**").hasAnyAuthority("ADMINISTRADOR", "ALMACEN", "SISTEMA")
                        .requestMatchers("/api/pago/**").hasAnyAuthority("ADMINISTRADOR", "VENTAS", "SISTEMA")
                        .requestMatchers(HttpMethod.GET, "/api/config/metodos-pago/**").hasAnyAuthority("ADMINISTRADOR", "VENTAS", "SISTEMA")
                        .requestMatchers("/api/config/metodos-pago/**").hasAnyAuthority("ADMINISTRADOR", "SISTEMA")
                        .requestMatchers(HttpMethod.GET, "/api/config/comprobantes/**").hasAnyAuthority("ADMINISTRADOR", "VENTAS", "SISTEMA")
                        .requestMatchers("/api/config/comprobantes/**").hasAnyAuthority("ADMINISTRADOR", "SISTEMA")
                        .requestMatchers("/api/config/sunat/**").hasAnyAuthority("ADMINISTRADOR", "SISTEMA")
                        .requestMatchers(HttpMethod.PUT, "/api/producto/**").hasAnyAuthority("ADMINISTRADOR", "ALMACEN", "SISTEMA")
                        .requestMatchers(HttpMethod.DELETE, "/api/producto/**").hasAnyAuthority("ADMINISTRADOR", "ALMACEN", "SISTEMA")
                        .requestMatchers("/api/producto/**").hasAnyAuthority("ADMINISTRADOR", "VENTAS", "ALMACEN", "SISTEMA")
                        .requestMatchers(HttpMethod.POST, "/api/variante/*/imagenes")
                        .hasAnyAuthority("ADMINISTRADOR", "ALMACEN", "SISTEMA")
                        .requestMatchers(HttpMethod.DELETE, "/api/variante/*/imagenes/*")
                        .hasAnyAuthority("ADMINISTRADOR", "ALMACEN", "SISTEMA")
                        .requestMatchers("/api/variante/listar-resumen")
                        .hasAnyAuthority("ADMINISTRADOR", "VENTAS", "ALMACEN", "VENTAS_ALMACEN", "SISTEMA")
                        .requestMatchers(HttpMethod.GET, "/api/variante/escanear")
                        .hasAnyAuthority("ADMINISTRADOR", "VENTAS", "VENTAS_ALMACEN", "SISTEMA")
                        .requestMatchers(HttpMethod.GET, "/api/variante/**")
                        .hasAnyAuthority("ADMINISTRADOR", "VENTAS", "ALMACEN", "VENTAS_ALMACEN", "SISTEMA")
                        .requestMatchers("/api/variante/**")
                        .hasAnyAuthority("ADMINISTRADOR", "VENTAS", "ALMACEN", "VENTAS_ALMACEN", "SISTEMA")
                        .requestMatchers("/api/venta/**").hasAnyAuthority("ADMINISTRADOR", "VENTAS", "SISTEMA")
                        .requestMatchers("/api/nota-credito/**")
                        .hasAnyAuthority("ADMINISTRADOR", "VENTAS", "VENTAS_ALMACEN", "SISTEMA")
                        .requestMatchers("/api/cotizacion/**").hasAnyAuthority("ADMINISTRADOR", "VENTAS", "SISTEMA")
                        .requestMatchers("/api/guia-remision/**").hasAnyAuthority("ADMINISTRADOR", "VENTAS", "ALMACEN", "SISTEMA")
                        .requestMatchers("/api/historial-stock/**")
                        .hasAnyAuthority("ADMINISTRADOR", "VENTAS", "ALMACEN", "VENTAS_ALMACEN", "SISTEMA")
                        .requestMatchers("/api/traslado/**")
                        .hasAnyAuthority("ADMINISTRADOR", "ALMACEN", "VENTAS_ALMACEN", "SISTEMA")
                        .requestMatchers("/api/documento/**").hasAnyAuthority("ADMINISTRADOR", "VENTAS", "ALMACEN", "SISTEMA")
                        .requestMatchers(HttpMethod.GET, "/api/categoria/**")
                        .hasAnyAuthority("ADMINISTRADOR", "VENTAS", "ALMACEN", "SISTEMA")
                        .requestMatchers("/api/categoria/**").hasAnyAuthority("ADMINISTRADOR", "ALMACEN", "SISTEMA")
                        .requestMatchers("/api/**").hasAnyAuthority("ADMINISTRADOR", "SISTEMA")
                        .anyRequest().authenticated())
                .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(401);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.getWriter().write("{\"message\":\"No autenticado\"}");
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(403);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.getWriter().write("{\"message\":\"No tiene permisos\"}");
                        }))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
