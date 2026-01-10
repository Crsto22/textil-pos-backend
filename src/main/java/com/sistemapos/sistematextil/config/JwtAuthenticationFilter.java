package com.sistemapos.sistematextil.config;

import java.io.IOException;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.micrometer.common.lang.NonNull;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();

        return path.startsWith("/api/auth")
                || path.startsWith("/api/sucursal")
                || path.startsWith("/api/empresa");
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        // Esta parte es importante porque si no se envia el token o no comienza con
        // Bearer el filtro simplemente deja pasar la solicitud sin hacer nada
        // y luego la solicitud va al SecurityFilterChain, donde si tiene el
        // .permitAll() configurado
        final String authHeader = request.getHeader("Authorization"); // El Authorization y Bearer es lo que esta en el
                                                                      // postman
        if (authHeader == null || !authHeader.startsWith("Bearer")) { // verifica si el encabezado es nulo o mal formado
                                                                      // si no hay token o no empieza con Bearer no hace
                                                                      // nada y deja que siga la solicitud
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(7);
        final String userEmail = jwtService.extractUsername(jwt); // Extrae el token y saca el correo (username) del
                                                                  // token JWT.

        // de esta parte para abajo , captura el token asociado con el usuario y captura
        // el rol y eso es enviado al securityConfig para tener acceso a esa ruta segun
        // el rol
        // ESTe PROCESO SE HACE CUANDO UNA VEZ INICIADO SESION Y ENTRA A LA PAGINA
        // PRIVADA Y QUIERE HACER UN GET,POST,PUT,DELETE ETC VERIFICA EL TOKEN Y SI
        // EXISTE DEJA QUE HAGA EL PROCESO QUE QUIERA

        if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {

            UserDetails userDetails = this.userDetailsService.loadUserByUsername(userEmail);
            // carga desde la base de datos el usuario, su rol

            System.out.println(userDetails.getAuthorities()); // Capturo el rol y muestro en la consola

            if (jwtService.isTookenValid(jwt, userDetails)) { // Aca esta validando el token si es valido, si expiro ,
                                                              // el usuario porque ese isTookenValid viene de jwtService
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(userDetails,
                        null, userDetails.getAuthorities());
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }

        }
        filterChain.doFilter(request, response);

    }

}
