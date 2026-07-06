package com.sistemapos.sistematextil.controllers;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.sistemapos.sistematextil.services.EcommercePedidoService;
import com.sistemapos.sistematextil.services.EcommerceProductoPublicService;
import com.sistemapos.sistematextil.util.ecommerce.EcommerceCarritoValidarRequest;
import com.sistemapos.sistematextil.util.ecommerce.EcommerceInicioResponse;
import com.sistemapos.sistematextil.util.ecommerce.EcommercePedidoCreateRequest;
import com.sistemapos.sistematextil.util.ecommerce.EcommercePedidoResponse;
import com.sistemapos.sistematextil.util.ecommerce.EcommerceProductoColorStockResponse;
import com.sistemapos.sistematextil.util.ecommerce.EcommerceProductoDetalleSlugResponse;
import com.sistemapos.sistematextil.util.ecommerce.EcommerceProductoListadoResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping(value = "api/public/ecommerce", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class EcommercePublicController {

    private static final String TIENDA_NO_CONFIGURADA = "TIENDA_NO_CONFIGURADA";

    private final EcommerceProductoPublicService ecommerceProductoPublicService;
    private final EcommercePedidoService ecommercePedidoService;

    @GetMapping("productos")
    public ResponseEntity<EcommerceProductoListadoResponse> listarProductos(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "idCategoria", required = false) Integer idCategoria,
            @RequestParam(name = "idColor", required = false) Integer idColor,
            @RequestParam(name = "tallas", required = false) List<String> tallas,
            @RequestParam(name = "precioMax", required = false) Double precioMax,
            @RequestParam(name = "soloDisponibles", required = false) Boolean soloDisponibles) {
        return ResponseEntity.ok(ecommerceProductoPublicService.listarProductos(
                q,
                page,
                size,
                idCategoria,
                idColor,
                tallas,
                precioMax,
                soloDisponibles));
    }

    @GetMapping("inicio")
    public ResponseEntity<EcommerceInicioResponse> inicio() {
        return ResponseEntity.ok(ecommerceProductoPublicService.obtenerInicio());
    }

    @GetMapping("productos/{slug}")
    public ResponseEntity<?> obtenerDetallePorSlug(@PathVariable String slug) {
        try {
            EcommerceProductoDetalleSlugResponse response = ecommerceProductoPublicService.obtenerDetallePorSlug(slug);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Producto no encontrado" : e.getMessage();
            HttpStatus status = TIENDA_NO_CONFIGURADA.equals(message)
                    ? HttpStatus.CONFLICT
                    : HttpStatus.NOT_FOUND;
            String publicMessage = TIENDA_NO_CONFIGURADA.equals(message)
                    ? "Tienda ecommerce no configurada"
                    : message;
            return ResponseEntity.status(status).body(Map.of("message", publicMessage, "code", message));
        }
    }

    @GetMapping("productos/{slug}/colores/{idColor}/stock")
    public ResponseEntity<?> obtenerStockColorPorSlug(
            @PathVariable String slug,
            @PathVariable Integer idColor) {
        try {
            EcommerceProductoColorStockResponse response = ecommerceProductoPublicService.obtenerStockColorPorSlug(slug, idColor);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Producto no encontrado" : e.getMessage();
            HttpStatus status = TIENDA_NO_CONFIGURADA.equals(message)
                    ? HttpStatus.CONFLICT
                    : HttpStatus.NOT_FOUND;
            String publicMessage = TIENDA_NO_CONFIGURADA.equals(message)
                    ? "Tienda ecommerce no configurada"
                    : message;
            return ResponseEntity.status(status).body(Map.of("message", publicMessage, "code", message));
        }
    }

    @GetMapping("productos/{slug}/variantes/{idProductoVariante}/stock")
    public ResponseEntity<?> obtenerStockVariantePorSlug(
            @PathVariable String slug,
            @PathVariable Integer idProductoVariante) {
        try {
            return ResponseEntity.ok(ecommerceProductoPublicService.obtenerStockVariantePorSlug(slug, idProductoVariante));
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "Producto no encontrado" : e.getMessage();
            HttpStatus status = TIENDA_NO_CONFIGURADA.equals(message)
                    ? HttpStatus.CONFLICT
                    : HttpStatus.NOT_FOUND;
            String publicMessage = TIENDA_NO_CONFIGURADA.equals(message)
                    ? "Tienda ecommerce no configurada"
                    : message;
            return ResponseEntity.status(status).body(Map.of("message", publicMessage, "code", message));
        }
    }

    @PostMapping("pedidos")
    public ResponseEntity<?> crearPedido(
            @Valid @RequestBody EcommercePedidoCreateRequest request,
            HttpServletRequest servletRequest) {
        try {
            EcommercePedidoResponse response = ecommercePedidoService.crear(request, clienteIp(servletRequest));
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", mensaje(e)));
        }
    }

    @PostMapping("carrito/validar")
    public ResponseEntity<?> validarCarrito(@Valid @RequestBody EcommerceCarritoValidarRequest request) {
        try {
            return ResponseEntity.ok(ecommercePedidoService.validarCarrito(request));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", mensaje(e)));
        }
    }

    @PostMapping(value = "pedidos/{codigo}/comprobante", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> subirComprobante(
            @PathVariable String codigo,
            @RequestParam("file") MultipartFile file) {
        try {
            return ResponseEntity.ok(ecommercePedidoService.subirComprobante(codigo, file));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", mensaje(e)));
        }
    }

    @GetMapping("pedidos/actual")
    public ResponseEntity<?> obtenerPedidoActual(@RequestParam("token") String token) {
        try {
            return ResponseEntity.ok(ecommercePedidoService.obtenerPorToken(token));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", mensaje(e)));
        }
    }

    @PostMapping(value = "pedidos/comprobante", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> subirComprobantePorToken(
            @RequestParam("token") String token,
            @RequestParam("file") MultipartFile file) {
        try {
            return ResponseEntity.ok(ecommercePedidoService.subirComprobantePorToken(token, file));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", mensaje(e)));
        }
    }

    private String mensaje(RuntimeException e) {
        return e.getMessage() == null ? "No se pudo procesar la solicitud" : e.getMessage();
    }

    private String clienteIp(HttpServletRequest request) {
        String cfIp = request.getHeader("CF-Connecting-IP");
        if (cfIp != null && !cfIp.isBlank()) {
            return cfIp.trim();
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
