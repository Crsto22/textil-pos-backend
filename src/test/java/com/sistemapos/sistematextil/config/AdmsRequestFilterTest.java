package com.sistemapos.sistematextil.config;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import jakarta.servlet.FilterChain;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

class AdmsRequestFilterTest {

    @Test
    void rechazaTipoDeContenidoNoCompatible() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/iclock/cdata");
        request.setServletPath("/iclock/cdata");
        request.setContentType("application/json");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        new AdmsRequestFilter().doFilter(request, response, chain);

        assertEquals(415, response.getStatus());
        verifyNoInteractions(chain);
    }

    @Test
    void rechazaCuerpoAdmsDemasiadoGrande() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/iclock/cdata");
        request.setServletPath("/iclock/cdata");
        request.setContent(new byte[256 * 1024 + 1]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        new AdmsRequestFilter().doFilter(request, response, chain);

        assertEquals(413, response.getStatus());
        verifyNoInteractions(chain);
    }

    @Test
    void permiteLimiteExactoYConservaElCuerpoParaElControlador() throws Exception {
        byte[] body = new byte[256 * 1024];
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/iclock/cdata");
        request.setServletPath("/iclock/cdata");
        request.setContentType("text/plain");
        request.setContent(body);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<byte[]> recibido = new AtomicReference<>();
        FilterChain chain = (forwarded, ignored) -> recibido.set(forwarded.getInputStream().readAllBytes());

        new AdmsRequestFilter().doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        assertArrayEquals(body, recibido.get());
    }

    @Test
    void rechazaCuerpoChunkedSinContentLength() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/iclock/cdata") {
            @Override
            public int getContentLength() {
                return -1;
            }

            @Override
            public long getContentLengthLong() {
                return -1;
            }
        };
        request.setServletPath("/iclock/cdata");
        request.setContentType("text/plain");
        request.setContent(new byte[256 * 1024 + 1]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        new AdmsRequestFilter().doFilter(request, response, chain);

        assertEquals(413, response.getStatus());
        verifyNoInteractions(chain);
    }

    @Test
    void mideTextoUtf8PorBytes() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/iclock/cdata") {
            @Override
            public int getContentLength() {
                return -1;
            }

            @Override
            public long getContentLengthLong() {
                return -1;
            }
        };
        request.setServletPath("/iclock/cdata");
        request.setContentType("text/plain; charset=UTF-8");
        request.setContent("ááá".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        AdmsRequestFilter filter = new AdmsRequestFilter();
        ReflectionTestUtils.setField(filter, "maxBodyBytes", 4);

        filter.doFilter(request, response, chain);

        assertEquals(413, response.getStatus());
        verifyNoInteractions(chain);
    }
}
