package com.sistemapos.sistematextil.services;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sistemapos.sistematextil.model.HistorialStock;
import com.sistemapos.sistematextil.model.ProductoVariante;
import com.sistemapos.sistematextil.model.SucursalStock;
import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.repositories.SucursalStockRepository;
import com.sistemapos.sistematextil.repositories.UsuarioRepository;
import com.sistemapos.sistematextil.util.sucursalstock.SucursalStockAjusteRequest;
import com.sistemapos.sistematextil.util.sucursalstock.MovimientoStockRequest;
import com.sistemapos.sistematextil.util.sucursalstock.MovimientoStockResponse;
import com.sistemapos.sistematextil.util.sucursalstock.SucursalStockResponse;
import com.sistemapos.sistematextil.util.usuario.Rol;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SucursalStockService {

    private final SucursalStockRepository sucursalStockRepository;
    private final UsuarioRepository usuarioRepository;
    private final StockMovimientoService stockMovimientoService;

    public List<SucursalStockResponse> listar(Integer idSucursal, String q, String correoUsuarioAutenticado) {
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolLectura(usuarioAutenticado);
        Integer idSucursalFiltro = resolverIdSucursalPermitida(usuarioAutenticado, idSucursal);
        String term = normalizar(q);
        return sucursalStockRepository.buscarPorSucursal(idSucursalFiltro, term).stream()
                .map(this::toResponse)
                .toList();
    }

    public SucursalStockResponse obtener(Integer idSucursal, Integer idProductoVariante, String correoUsuarioAutenticado) {
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolLectura(usuarioAutenticado);
        Integer idSucursalFiltro = resolverIdSucursalPermitida(usuarioAutenticado, idSucursal);
        StockMovimientoService.StockContexto contexto = stockMovimientoService.obtenerContexto(idSucursalFiltro, idProductoVariante);
        return toResponse(contexto.sucursalStock());
    }

    @Transactional
    public SucursalStockResponse ajustar(SucursalStockAjusteRequest request, String correoUsuarioAutenticado) {
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolEscritura(usuarioAutenticado);
        Integer idSucursalPermitida = resolverIdSucursalPermitida(usuarioAutenticado, request.idSucursal());
        StockMovimientoService.MovimientoStock movimiento = stockMovimientoService.ajustar(
                idSucursalPermitida,
                request.idProductoVariante(),
                request.cantidad(),
                normalizar(request.motivo()) != null ? normalizar(request.motivo()) : "AJUSTE MANUAL DE INVENTARIO",
                usuarioAutenticado);
        return toResponse(movimiento.sucursalStock());
    }

    @Transactional
    public MovimientoStockResponse registrarMovimiento(MovimientoStockRequest request, String correoUsuarioAutenticado) {
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolEscritura(usuarioAutenticado);

        Integer idSucursalPermitida = resolverIdSucursalPermitida(usuarioAutenticado, request.idSucursal());
        HistorialStock.TipoMovimiento tipoMovimiento = normalizarTipoMovimientoManual(request.tipoMovimiento());
        String motivo = normalizar(request.motivo());

        StockMovimientoService.MovimientoStock movimiento = switch (tipoMovimiento) {
            case ENTRADA -> stockMovimientoService.incrementar(
                    idSucursalPermitida,
                    request.idProductoVariante(),
                    request.cantidad(),
                    tipoMovimiento,
                    motivo != null ? motivo : "ENTRADA MANUAL DE INVENTARIO",
                    usuarioAutenticado);
            case SALIDA -> stockMovimientoService.descontar(
                    idSucursalPermitida,
                    request.idProductoVariante(),
                    request.cantidad(),
                    tipoMovimiento,
                    motivo != null ? motivo : "SALIDA MANUAL DE INVENTARIO",
                    usuarioAutenticado);
            default -> throw new RuntimeException("tipoMovimiento permitido: ENTRADA o SALIDA");
        };

        return toMovimientoResponse(movimiento);
    }

    private void validarRolLectura(Usuario usuario) {
        if (!usuario.getRol().esAdministrador()
                && !usuario.getRol().permiteVentas()
                && !usuario.getRol().permiteAlmacen()) {
            throw new RuntimeException("El usuario autenticado no tiene permisos para consultar stock");
        }
    }

    private void validarRolEscritura(Usuario usuario) {
        if (!usuario.getRol().esAdministrador() && !usuario.getRol().permiteAlmacen()) {
            throw new RuntimeException("El usuario autenticado no tiene permisos para ajustar stock");
        }
    }

    private Integer resolverIdSucursalPermitida(Usuario usuario, Integer idSucursalRequest) {
        if (idSucursalRequest == null) {
            throw new RuntimeException("Ingrese idSucursal");
        }
        if (usuario.getRol().esAdministrador()) {
            return idSucursalRequest;
        }
        Integer idSucursalUsuario = obtenerIdSucursalUsuario(usuario);
        if (!idSucursalUsuario.equals(idSucursalRequest)) {
            throw new RuntimeException("No tiene permisos para gestionar otra sucursal");
        }
        return idSucursalUsuario;
    }

    private Usuario obtenerUsuarioAutenticado(String correoUsuarioAutenticado) {
        if (correoUsuarioAutenticado == null || correoUsuarioAutenticado.isBlank()) {
            throw new RuntimeException("No autenticado");
        }
        return usuarioRepository.findByCorreoAndDeletedAtIsNull(correoUsuarioAutenticado)
                .orElseThrow(() -> new RuntimeException("Usuario autenticado no encontrado"));
    }

    private Integer obtenerIdSucursalUsuario(Usuario usuario) {
        if (usuario.getSucursal() == null || usuario.getSucursal().getIdSucursal() == null) {
            throw new RuntimeException("El usuario autenticado no tiene sucursal asignada");
        }
        return usuario.getSucursal().getIdSucursal();
    }

    private SucursalStockResponse toResponse(SucursalStock stock) {
        ProductoVariante variante = stock.getProductoVariante();
        String tipoSucursal = stock.getSucursal() != null && stock.getSucursal().getTipo() != null
                ? stock.getSucursal().getTipo().name()
                : null;
        return new SucursalStockResponse(
                stock.getIdSucursalStock(),
                stock.getSucursal() != null ? stock.getSucursal().getIdSucursal() : null,
                stock.getSucursal() != null ? stock.getSucursal().getNombre() : null,
                tipoSucursal,
                variante != null ? variante.getIdProductoVariante() : null,
                variante != null && variante.getProducto() != null ? variante.getProducto().getIdProducto() : null,
                variante != null && variante.getProducto() != null ? variante.getProducto().getNombre() : null,
                variante != null ? variante.getSku() : null,
                variante != null ? variante.getCodigoBarras() : null,
                variante != null && variante.getColor() != null ? variante.getColor().getNombre() : null,
                variante != null && variante.getTalla() != null ? variante.getTalla().getNombre() : null,
                stock.getCantidad(),
                variante != null ? variante.getPrecio() : null,
                variante != null ? variante.getEstado() : null);
    }

    private MovimientoStockResponse toMovimientoResponse(StockMovimientoService.MovimientoStock movimiento) {
        SucursalStock stock = movimiento.sucursalStock();
        HistorialStock historial = movimiento.historial();
        ProductoVariante variante = stock.getProductoVariante();
        return new MovimientoStockResponse(
                historial != null ? historial.getIdHistorial() : null,
                historial != null ? historial.getFecha() : null,
                historial != null && historial.getTipoMovimiento() != null ? historial.getTipoMovimiento().name() : null,
                historial != null ? historial.getMotivo() : null,
                stock.getSucursal() != null ? stock.getSucursal().getIdSucursal() : null,
                stock.getSucursal() != null ? stock.getSucursal().getNombre() : null,
                variante != null ? variante.getIdProductoVariante() : null,
                variante != null && variante.getProducto() != null ? variante.getProducto().getIdProducto() : null,
                variante != null && variante.getProducto() != null ? variante.getProducto().getNombre() : null,
                variante != null ? variante.getSku() : null,
                variante != null && variante.getColor() != null ? variante.getColor().getNombre() : null,
                variante != null && variante.getTalla() != null ? variante.getTalla().getNombre() : null,
                historial != null ? historial.getCantidad() : null,
                movimiento.stockAnterior(),
                movimiento.stockNuevo());
    }

    private String normalizar(String value) {
        if (value == null) {
            return null;
        }
        String normalizado = value.trim();
        return normalizado.isEmpty() ? null : normalizado;
    }

    private HistorialStock.TipoMovimiento normalizarTipoMovimientoManual(String value) {
        String normalizado = normalizar(value);
        if (normalizado == null) {
            throw new RuntimeException("Ingrese tipoMovimiento");
        }
        return switch (normalizado.toUpperCase()) {
            case "ENTRADA" -> HistorialStock.TipoMovimiento.ENTRADA;
            case "SALIDA" -> HistorialStock.TipoMovimiento.SALIDA;
            default -> throw new RuntimeException("tipoMovimiento permitido: ENTRADA o SALIDA");
        };
    }
}
