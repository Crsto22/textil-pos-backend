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
                        .requestMatchers("/api/empresa/listar").hasAuthority("ADMINISTRADOR")
                        .requestMatchers("/api/empresa/actualizar/**").hasAuthority("ADMINISTRADOR")
                        .requestMatchers("/api/empresa/**").permitAll()
                        .requestMatchers("/api/talla/**").hasAuthority("ADMINISTRADOR")
                        .requestMatchers("/api/color/**").hasAuthority("ADMINISTRADOR")
                        .requestMatchers("/api/producto/**").permitAll()
                        .requestMatchers("/api/variante/**").permitAll()
                        .requestMatchers("/api/categoria/**").permitAll()
                        .anyRequest().authenticated())
                .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
