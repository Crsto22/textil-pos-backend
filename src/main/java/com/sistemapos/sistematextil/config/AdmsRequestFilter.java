package com.sistemapos.sistematextil.config;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class AdmsRequestFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AdmsRequestFilter.class);
    private static final Set<String> CONTENT_TYPES = Set.of(
            MediaType.TEXT_PLAIN_VALUE,
            MediaType.APPLICATION_OCTET_STREAM_VALUE,
            MediaType.APPLICATION_FORM_URLENCODED_VALUE);

    @Value("${asistencia.adms.max-body-bytes:262144}")
    private int maxBodyBytes = 256 * 1024;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getServletPath().startsWith("/iclock/")
                || !"POST".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (maxBodyBytes < 1) {
            throw new IllegalStateException("Configuracion de tamano ADMS invalida");
        }
        if (request.getContentLengthLong() > maxBodyBytes) {
            registrarRechazo(request, "carga demasiado grande");
            responder(response, 413, "ERROR: carga ADMS demasiado grande");
            return;
        }
        String contentType = request.getContentType();
        if (contentType != null) {
            String baseType = contentType.split(";", 2)[0].trim().toLowerCase();
            if (!CONTENT_TYPES.contains(baseType)) {
                registrarRechazo(request, "tipo de contenido no permitido");
                responder(response, 415, "ERROR: tipo de contenido ADMS no permitido");
                return;
            }
        }
        byte[] body = request.getInputStream().readNBytes(maxBodyBytes + 1);
        if (body.length > maxBodyBytes) {
            registrarRechazo(request, "carga demasiado grande");
            responder(response, 413, "ERROR: carga ADMS demasiado grande");
            return;
        }
        filterChain.doFilter(new CachedBodyRequest(request, body), response);
    }

    private void responder(HttpServletResponse response, int status, String body) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.TEXT_PLAIN_VALUE);
        response.getWriter().write(body);
    }

    private void registrarRechazo(HttpServletRequest request, String motivo) {
        String serial = serialDesdeQuery(request.getQueryString());
        String serialOculto = serial == null || serial.isBlank() ? "sin-serial"
                : serial.length() <= 4 ? "****" : "****" + serial.substring(serial.length() - 4);
        log.warn("ADMS rechazado ip={} serial={} motivo={}", request.getRemoteAddr(), serialOculto, motivo);
    }

    private String serialDesdeQuery(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        for (String parametro : query.split("&")) {
            String[] par = parametro.split("=", 2);
            if (par.length == 2 && "SN".equalsIgnoreCase(par[0])) {
                return URLDecoder.decode(par[1], StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private static final class CachedBodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        private CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return input.read();
                }

                @Override
                public int read(byte[] bytes, int offset, int length) {
                    return input.read(bytes, offset, length);
                }

                @Override
                public boolean isFinished() {
                    return input.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                    // ADMS requests are consumed synchronously by Spring MVC.
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            String encoding = getCharacterEncoding();
            return new BufferedReader(new InputStreamReader(
                    getInputStream(), encoding != null ? java.nio.charset.Charset.forName(encoding) : StandardCharsets.UTF_8));
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }
    }
}
