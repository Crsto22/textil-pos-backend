package com.sistemapos.sistematextil.services;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sistemapos.sistematextil.model.HistorialStock;
import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.repositories.HistorialStockRepository;
import com.sistemapos.sistematextil.repositories.UsuarioRepository;
import com.sistemapos.sistematextil.util.historialstock.HistorialStockListItemResponse;
import com.sistemapos.sistematextil.util.paginacion.PagedResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class HistorialStockService {

    private final HistorialStockRepository repository;
    private final UsuarioRepository usuarioRepository;

    @Value("${application.pagination.default-size:10}")
    private int defaultPageSize;

    public PagedResponse<HistorialStockListItemResponse> listarPaginado(int page, String correoUsuarioAutenticado) {
        validarPagina(page);
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolPermitido(usuarioAutenticado);

        Integer idSucursalFiltro = resolverIdSucursalFiltro(usuarioAutenticado);
        PageRequest pageable = PageRequest.of(
                page,
                defaultPageSize,
                Sort.by(Sort.Order.desc("fecha"), Sort.Order.desc("idHistorial")));
        Page<HistorialStock> historial = idSucursalFiltro == null
                ? repository.findAllByOrderByFechaDesc(pageable)
                : repository.findBySucursalIdSucursalOrderByFechaDesc(idSucursalFiltro, pageable);

        return PagedResponse.fromPage(historial.map(this::toListItemResponse));
    }

    public List<HistorialStock> listarPorProducto(Integer idProducto, String correoUsuarioAutenticado) {
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolPermitido(usuarioAutenticado);

        Integer idSucursalFiltro = resolverIdSucursalFiltro(usuarioAutenticado);
        return idSucursalFiltro == null
                ? repository.findByProductoVarianteProductoIdProductoOrderByFechaDesc(idProducto)
                : repository.findByProductoVarianteProductoIdProductoAndSucursalIdSucursalOrderByFechaDesc(
                        idProducto,
                        idSucursalFiltro);
    }

    public List<HistorialStock> listarPorVariante(Integer idProductoVariante, String correoUsuarioAutenticado) {
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolPermitido(usuarioAutenticado);

        Integer idSucursalFiltro = resolverIdSucursalFiltro(usuarioAutenticado);
        return idSucursalFiltro == null
                ? repository.findByProductoVarianteIdProductoVarianteOrderByFechaDesc(idProductoVariante)
                : repository.findByProductoVarianteIdProductoVarianteAndSucursalIdSucursalOrderByFechaDesc(
                        idProductoVariante,
                        idSucursalFiltro);
    }

    @Transactional
    public void registrarMovimiento(HistorialStock historial) {
        repository.save(historial);
    }

    private HistorialStockListItemResponse toListItemResponse(HistorialStock historial) {
        String nombreUsuario = historial.getUsuario() != null
                ? (valor(historial.getUsuario().getNombre()) + " " + valor(historial.getUsuario().getApellido())).trim()
                : null;
        return new HistorialStockListItemResponse(
                historial.getIdHistorial(),
                historial.getFecha(),
                historial.getTipoMovimiento() != null ? historial.getTipoMovimiento().name() : null,
                historial.getMotivo(),
                historial.getProductoVariante() != null ? historial.getProductoVariante().getIdProductoVariante() : null,
                historial.getProductoVariante() != null && historial.getProductoVariante().getProducto() != null
                        ? historial.getProductoVariante().getProducto().getIdProducto()
                        : null,
                historial.getProductoVariante() != null && historial.getProductoVariante().getProducto() != null
                        ? historial.getProductoVariante().getProducto().getNombre()
                        : null,
                historial.getProductoVariante() != null ? historial.getProductoVariante().getSku() : null,
                historial.getProductoVariante() != null ? historial.getProductoVariante().getCodigoBarras() : null,
                historial.getProductoVariante() != null && historial.getProductoVariante().getColor() != null
                        ? historial.getProductoVariante().getColor().getNombre()
                        : null,
                historial.getProductoVariante() != null && historial.getProductoVariante().getTalla() != null
                        ? historial.getProductoVariante().getTalla().getNombre()
                        : null,
                historial.getSucursal() != null ? historial.getSucursal().getIdSucursal() : null,
                historial.getSucursal() != null ? historial.getSucursal().getNombre() : null,
                historial.getSucursal() != null && historial.getSucursal().getTipo() != null
                        ? historial.getSucursal().getTipo().name()
                        : null,
                historial.getUsuario() != null ? historial.getUsuario().getIdUsuario() : null,
                nombreUsuario,
                historial.getCantidad(),
                historial.getStockAnterior(),
                historial.getStockNuevo());
    }

    private Usuario obtenerUsuarioAutenticado(String correoUsuarioAutenticado) {
        if (correoUsuarioAutenticado == null || correoUsuarioAutenticado.isBlank()) {
            throw new RuntimeException("No autenticado");
        }
        return usuarioRepository.findByCorreoAndDeletedAtIsNull(correoUsuarioAutenticado)
                .orElseThrow(() -> new RuntimeException("Usuario autenticado no encontrado"));
    }

    private void validarRolPermitido(Usuario usuario) {
        if (!usuario.getRol().permiteVentas() && !usuario.getRol().permiteAlmacen()) {
            throw new RuntimeException("El usuario autenticado no tiene permisos para consultar historial de stock");
        }
    }

    private Integer resolverIdSucursalFiltro(Usuario usuarioAutenticado) {
        if (usuarioAutenticado.getRol().esAdministrador()) {
            return null;
        }
        if (usuarioAutenticado.getSucursal() != null && usuarioAutenticado.getSucursal().getIdSucursal() != null) {
            return usuarioAutenticado.getSucursal().getIdSucursal();
        }
        throw new RuntimeException("El usuario autenticado no tiene sucursal asignada");
    }

    private void validarPagina(int page) {
        if (page < 0) {
            throw new RuntimeException("El parametro page debe ser mayor o igual a 0");
        }
    }

    private String valor(String value) {
        return value == null ? "" : value;
    }
}
