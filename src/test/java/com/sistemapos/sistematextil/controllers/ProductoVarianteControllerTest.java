package com.sistemapos.sistematextil.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.sistemapos.sistematextil.config.JwtAuthenticationFilter;
import com.sistemapos.sistematextil.config.JwtService;
import com.sistemapos.sistematextil.config.SecurityConfig;
import com.sistemapos.sistematextil.services.ProductoVarianteService;
import com.sistemapos.sistematextil.util.producto.ProductoVariantePosResponse;

@WebMvcTest(
        controllers = ProductoVarianteController.class,
        properties = "spring.main.allow-bean-definition-overriding=true")
@Import({SecurityConfig.class, ProductoVarianteControllerTest.TestSecurityBeans.class})
class ProductoVarianteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductoVarianteService service;

    @Test
    void escanearNoEsPublico() throws Exception {
        mockMvc.perform(get("/api/variante/escanear")
                .param("codigoBarras", "ABC-123")
                .param("idSucursal", "7"))
                .andExpect(status().isForbidden());

        verify(service, never()).escanearPorCodigoBarras(any(), any(), any());
    }

    @Test
    void escanearRechazaUsuarioSinRolPermitido() throws Exception {
        mockMvc.perform(get("/api/variante/escanear")
                .param("codigoBarras", "ABC-123")
                .param("idSucursal", "7")
                .with(user("almacen@test.com").authorities(() -> "ALMACEN")))
                .andExpect(status().isForbidden());

        verify(service, never()).escanearPorCodigoBarras(any(), any(), any());
    }

    @Test
    void escanearDevuelveVariantePos() throws Exception {
        ProductoVariantePosResponse response = new ProductoVariantePosResponse(
                99,
                7,
                "ABC-123",
                "SKU-1",
                5,
                "ACTIVO",
                120.0,
                95.0,
                99.0,
                null,
                null,
                99.0,
                new ProductoVariantePosResponse.ProductoItem(10, "Polo Premium", "Algodon peinado"),
                new ProductoVariantePosResponse.ColorItem(20, "Negro", "#111111"),
                new ProductoVariantePosResponse.TallaItem(30, "M"),
                new ProductoVariantePosResponse.ImagenItem(
                        "https://cdn.test/polo.jpg",
                        "https://cdn.test/polo-thumb.jpg"));
        when(service.escanearPorCodigoBarras("ABC-123", 7, "ventas@test.com")).thenReturn(response);

        mockMvc.perform(get("/api/variante/escanear")
                .param("codigoBarras", "ABC-123")
                .param("idSucursal", "7")
                .with(user("ventas@test.com").authorities(() -> "VENTAS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idProductoVariante").value(99))
                .andExpect(jsonPath("$.precioVigente").value(99.0))
                .andExpect(jsonPath("$.producto.nombre").value("Polo Premium"))
                .andExpect(jsonPath("$.imagenPrincipal.url").value("https://cdn.test/polo.jpg"));
    }

    @Test
    void escanearDevuelveBadRequestSiCodigoBarrasEstaVacio() throws Exception {
        when(service.escanearPorCodigoBarras("", 7, "ventas@test.com"))
                .thenThrow(new RuntimeException("Ingrese codigoBarras"));

        mockMvc.perform(get("/api/variante/escanear")
                .param("codigoBarras", "")
                .param("idSucursal", "7")
                .with(user("ventas@test.com").authorities(() -> "VENTAS")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Ingrese codigoBarras"));
    }

    @Test
    void escanearDevuelveNotFoundCuandoNoExisteLaVariante() throws Exception {
        when(service.escanearPorCodigoBarras("ABC-404", 7, "ventas@test.com"))
                .thenThrow(new RuntimeException("No existe una variante con el codigo de barras 'ABC-404' en la sucursal"));

        mockMvc.perform(get("/api/variante/escanear")
                .param("codigoBarras", "ABC-404")
                .param("idSucursal", "7")
                .with(user("ventas@test.com").authorities(() -> "VENTAS")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No existe una variante con el codigo de barras 'ABC-404' en la sucursal"));
    }

    @Test
    void escanearDevuelveConflictCuandoLaVarianteNoEsVendible() throws Exception {
        when(service.escanearPorCodigoBarras("ABC-000", 7, "ventas@test.com"))
                .thenThrow(new RuntimeException("El producto 'Polo Premium Negro Talla M' no esta disponible"));

        mockMvc.perform(get("/api/variante/escanear")
                .param("codigoBarras", "ABC-000")
                .param("idSucursal", "7")
                .with(user("ventas@test.com").authorities(() -> "VENTAS")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("El producto 'Polo Premium Negro Talla M' no esta disponible"));
    }

    @Test
    void escanearDevuelveForbiddenCuandoServiceDetectaOtraSucursal() throws Exception {
        when(service.escanearPorCodigoBarras("ABC-123", 8, "ventas@test.com"))
                .thenThrow(new RuntimeException("No tiene permisos para consultar otra sucursal"));

        mockMvc.perform(get("/api/variante/escanear")
                .param("codigoBarras", "ABC-123")
                .param("idSucursal", "8")
                .with(user("ventas@test.com").authorities(() -> "VENTAS")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("No tiene permisos para consultar otra sucursal"));

        verify(service).escanearPorCodigoBarras(eq("ABC-123"), eq(8), eq("ventas@test.com"));
    }

    @TestConfiguration
    static class TestSecurityBeans {

        @Bean
        UserDetailsService userDetailsService() {
            return username -> User.withUsername(username)
                    .password("{noop}test")
                    .authorities("VENTAS")
                    .build();
        }

        @Bean
        JwtService jwtService() {
            return Mockito.mock(JwtService.class);
        }

        @Bean
        JwtAuthenticationFilter jwtAuthenticationFilter(JwtService jwtService, UserDetailsService userDetailsService) {
            return new JwtAuthenticationFilter(jwtService, userDetailsService);
        }

        @Bean(name = "commandLineRunner")
        CommandLineRunner commandLineRunner() {
            return args -> {
            };
        }
    }
}
