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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
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

    // encripta la contraseña ingresada para ser comparada con la de la database
    /*
     Luego, Spring usa el PasswordEncoder (el BCryptPasswordEncoder) para comparar la contraseña ingresada (request.password()) con la contraseña encriptada que viene del usuario de la base de datos que fue envolvida en CustomUser.
     */
    @Bean
    BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }


    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(Customizer.withDefaults()) // HABILITAR y CREAMOS EN LINK DE CONFIGURACION , QUE SERIA LO DE LA LINEA 63 PARA ABAJO: UrlBasedCorsConfigurationSource
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/api/auth/**").permitAll() // Ingresar sin token
                    .requestMatchers("/api/sucursal/**").permitAll()
                    .requestMatchers("/api/empresa/**").permitAll()
                    .requestMatchers("/api/talla/**").permitAll()
                    .requestMatchers("/api/color/**").permitAll()
                    .requestMatchers("/api/producto/**").permitAll()
                    .requestMatchers("/api/variante/**").permitAll()
                    .requestMatchers("/api/categoria/**").permitAll()

                    
                    
                    .anyRequest().authenticated()) // cualquier otra ruta no mencionada necesita que el usuario sea autenticado pero sin importar si es admin o client
                .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

}
