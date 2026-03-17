package com.sistemapos.sistematextil.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
                        .requestMatchers("/api/auth/autenticarse", "/api/auth/refresh").permitAll()
                        .requestMatchers("/api/auth/registro").hasAuthority("ADMINISTRADOR")
                        .requestMatchers("/api/auth/logout").authenticated()
                        .requestMatchers("/api/usuario/**").hasAuthority("ADMINISTRADOR")
                        .requestMatchers("/api/cliente/**").hasAnyAuthority("ADMINISTRADOR", "VENTAS")
                        .requestMatchers("/api/sucursal/**").hasAuthority("ADMINISTRADOR")
                        .requestMatchers(HttpMethod.GET, "/api/empresa/publico").permitAll()
                        .requestMatchers("/api/empresa/**").hasAuthority("ADMINISTRADOR")
                        .requestMatchers("/api/talla/**").hasAnyAuthority("ADMINISTRADOR", "ALMACEN")
                        .requestMatchers("/api/color/**").hasAnyAuthority("ADMINISTRADOR", "ALMACEN")
                        .requestMatchers("/api/pago/**").hasAnyAuthority("ADMINISTRADOR", "VENTAS")
                        .requestMatchers(HttpMethod.GET, "/api/config/metodos-pago/**").hasAnyAuthority("ADMINISTRADOR", "VENTAS")
                        .requestMatchers("/api/config/metodos-pago/**").hasAuthority("ADMINISTRADOR")
                        .requestMatchers(HttpMethod.GET, "/api/config/comprobantes/**").hasAnyAuthority("ADMINISTRADOR", "VENTAS")
                        .requestMatchers("/api/config/comprobantes/**").hasAuthority("ADMINISTRADOR")
                        .requestMatchers("/api/config/sunat/**").hasAuthority("ADMINISTRADOR")
                        .requestMatchers(HttpMethod.PUT, "/api/producto/**").hasAnyAuthority("ADMINISTRADOR", "ALMACEN")
                        .requestMatchers(HttpMethod.DELETE, "/api/producto/**").hasAnyAuthority("ADMINISTRADOR", "ALMACEN")
                        .requestMatchers("/api/producto/**").hasAnyAuthority("ADMINISTRADOR", "VENTAS", "ALMACEN")
                        .requestMatchers("/api/variante/listar-resumen").hasAnyAuthority("ADMINISTRADOR", "VENTAS", "ALMACEN")
                        .requestMatchers("/api/venta/**").hasAnyAuthority("ADMINISTRADOR", "VENTAS")
                        .requestMatchers("/api/cotizacion/**").hasAnyAuthority("ADMINISTRADOR", "VENTAS")
                        .requestMatchers("/api/documento/**").hasAnyAuthority("ADMINISTRADOR", "VENTAS", "ALMACEN")
                        .requestMatchers("/api/variante/**").permitAll()
                        .requestMatchers("/api/categoria/**").hasAnyAuthority("ADMINISTRADOR", "VENTAS", "ALMACEN")
                        .anyRequest().authenticated())
                .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
