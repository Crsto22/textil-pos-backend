package com.sistemapos.sistematextil.services;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sistemapos.sistematextil.model.HistorialStock;
import com.sistemapos.sistematextil.model.ProductoVariante;
import com.sistemapos.sistematextil.model.Sucursal;
import com.sistemapos.sistematextil.model.Traslado;
import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.repositories.ProductoVarianteRepository;
import com.sistemapos.sistematextil.repositories.SucursalRepository;
import com.sistemapos.sistematextil.repositories.TrasladoRepository;
import com.sistemapos.sistematextil.repositories.UsuarioRepository;
import com.sistemapos.sistematextil.util.paginacion.PagedResponse;
import com.sistemapos.sistematextil.util.traslado.TrasladoBatchResponse;
import com.sistemapos.sistematextil.util.traslado.TrasladoCreateRequest;
import com.sistemapos.sistematextil.util.traslado.TrasladoItemCreateRequest;
import com.sistemapos.sistematextil.util.traslado.TrasladoResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TrasladoService {

    private final TrasladoRepository trasladoRepository;
    private final SucursalRepository sucursalRepository;
    private final ProductoVarianteRepository productoVarianteRepository;
    private final UsuarioRepository usuarioRepository;
    private final StockMovimientoService stockMovimientoService;

    @Value("${application.pagination.default-size:10}")
    private int defaultPageSize;

    public PagedResponse<TrasladoResponse> listarPaginado(int page, Integer idSucursal, String correoUsuarioAutenticado) {
        validarPagina(page);
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolLectura(usuarioAutenticado);
        Integer idSucursalFiltro = resolverIdSucursalFiltro(usuarioAutenticado, idSucursal);
        Pageable pageable = PageRequest.of(
                page,
                defaultPageSize,
                Sort.by(Sort.Order.desc("fecha"), Sort.Order.desc("idTraslado")));
        Page<TrasladoResponse> result = trasladoRepository.listarConFiltros(idSucursalFiltro, pageable)
                .map(this::toResponse);
        return PagedResponse.fromPage(result);
    }

    @Transactional
    public TrasladoBatchResponse registrar(TrasladoCreateRequest request, String correoUsuarioAutenticado) {
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolEscritura(usuarioAutenticado);

        if (request.idSucursalOrigen().equals(request.idSucursalDestino())) {
            throw new RuntimeException("La sucursal origen y destino no pueden ser la misma");
        }

        Sucursal sucursalOrigen = obtenerSucursalActiva(request.idSucursalOrigen());
        Sucursal sucursalDestino = obtenerSucursalActiva(request.idSucursalDestino());
        validarAlcanceTraslado(usuarioAutenticado, sucursalOrigen.getIdSucursal(), sucursalDestino.getIdSucursal());
        validarItemsTraslado(request.items());

        String motivoNormalizado = normalizar(request.motivo());
        String motivo = motivoNormalizado != null ? motivoNormalizado : "TRASLADO DE INVENTARIO";

        List<TrasladoResponse> traslados = new ArrayList<>();
        Set<Integer> variantesProcesadas = new HashSet<>();
        int totalCantidad = 0;

        for (TrasladoItemCreateRequest item : request.items()) {
            if (!variantesProcesadas.add(item.idProductoVariante())) {
                throw new RuntimeException("No se permite repetir la misma variante en un traslado grupal");
            }

            ProductoVariante variante = productoVarianteRepository
                    .findByIdProductoVarianteAndDeletedAtIsNull(item.idProductoVariante())
                    .orElseThrow(() -> new RuntimeException("La variante seleccionada no existe"));

            stockMovimientoService.descontar(
                    sucursalOrigen.getIdSucursal(),
                    variante.getIdProductoVariante(),
                    item.cantidad(),
                    HistorialStock.TipoMovimiento.SALIDA,
                    "TRASLADO SALIDA: " + motivo,
                    usuarioAutenticado);
            stockMovimientoService.incrementar(
                    sucursalDestino.getIdSucursal(),
                    variante.getIdProductoVariante(),
                    item.cantidad(),
                    HistorialStock.TipoMovimiento.ENTRADA,
                    "TRASLADO ENTRADA: " + motivo,
                    usuarioAutenticado);

            Traslado traslado = new Traslado();
            traslado.setSucursalOrigen(sucursalOrigen);
            traslado.setSucursalDestino(sucursalDestino);
            traslado.setProductoVariante(variante);
            traslado.setCantidad(item.cantidad());
            traslado.setMotivo(motivo);
            traslado.setUsuario(usuarioAutenticado);
            traslados.add(toResponse(trasladoRepository.save(traslado)));
            totalCantidad += item.cantidad();
        }

        return new TrasladoBatchResponse(
                sucursalOrigen.getIdSucursal(),
                sucursalOrigen.getNombre(),
                sucursalDestino.getIdSucursal(),
                sucursalDestino.getNombre(),
                motivo,
                traslados.size(),
                totalCantidad,
                traslados);
    }

    private void validarRolLectura(Usuario usuario) {
        if (!usuario.getRol().esAdministrador() && !usuario.getRol().permiteAlmacen()) {
            throw new RuntimeException("El usuario autenticado no tiene permisos para consultar traslados");
        }
    }

    private void validarRolEscritura(Usuario usuario) {
        if (!usuario.getRol().esAdministrador() && !usuario.getRol().permiteAlmacen()) {
            throw new RuntimeException("El usuario autenticado no tiene permisos para registrar traslados");
        }
    }

    private void validarPagina(int page) {
        if (page < 0) {
            throw new RuntimeException("El parametro page debe ser mayor o igual a 0");
        }
    }

    private Integer resolverIdSucursalFiltro(Usuario usuario, Integer idSucursalRequest) {
        if (usuario.getRol().esAdministrador()) {
            return idSucursalRequest;
        }
        Integer idSucursalUsuario = obtenerIdSucursalUsuario(usuario);
        if (idSucursalRequest != null && !idSucursalUsuario.equals(idSucursalRequest)) {
            throw new RuntimeException("No tiene permisos para consultar otra sucursal");
        }
        return idSucursalUsuario;
    }

    private void validarAlcanceTraslado(Usuario usuario, Integer idSucursalOrigen, Integer idSucursalDestino) {
        if (usuario.getRol().esAdministrador()) {
            return;
        }
        Integer idSucursalUsuario = obtenerIdSucursalUsuario(usuario);
        if (!idSucursalUsuario.equals(idSucursalOrigen) && !idSucursalUsuario.equals(idSucursalDestino)) {
            throw new RuntimeException("Solo puede registrar traslados donde participe su propia sucursal");
        }
    }

    private void validarItemsTraslado(List<TrasladoItemCreateRequest> items) {
        if (items == null || items.isEmpty()) {
            throw new RuntimeException("Ingrese al menos un producto para trasladar");
        }
    }

    private Sucursal obtenerSucursalActiva(Integer idSucursal) {
        return sucursalRepository.findByIdSucursalAndDeletedAtIsNull(idSucursal)
                .filter(sucursal -> "ACTIVO".equalsIgnoreCase(sucursal.getEstado()))
                .orElseThrow(() -> new RuntimeException("Sucursal no encontrada o inactiva"));
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

    private TrasladoResponse toResponse(Traslado traslado) {
        ProductoVariante variante = traslado.getProductoVariante();
        String nombreUsuario = traslado.getUsuario() != null
                ? (valor(traslado.getUsuario().getNombre()) + " " + valor(traslado.getUsuario().getApellido())).trim()
                : null;
        return new TrasladoResponse(
                traslado.getIdTraslado(),
                traslado.getSucursalOrigen() != null ? traslado.getSucursalOrigen().getIdSucursal() : null,
                traslado.getSucursalOrigen() != null ? traslado.getSucursalOrigen().getNombre() : null,
                traslado.getSucursalDestino() != null ? traslado.getSucursalDestino().getIdSucursal() : null,
                traslado.getSucursalDestino() != null ? traslado.getSucursalDestino().getNombre() : null,
                variante != null ? variante.getIdProductoVariante() : null,
                variante != null && variante.getProducto() != null ? variante.getProducto().getNombre() : null,
                variante != null ? variante.getSku() : null,
                variante != null && variante.getColor() != null ? variante.getColor().getNombre() : null,
                variante != null && variante.getTalla() != null ? variante.getTalla().getNombre() : null,
                traslado.getCantidad(),
                traslado.getMotivo(),
                traslado.getUsuario() != null ? traslado.getUsuario().getIdUsuario() : null,
                nombreUsuario,
                traslado.getFecha());
    }

    private String normalizar(String value) {
        if (value == null) {
            return null;
        }
        String normalizado = value.trim();
        return normalizado.isEmpty() ? null : normalizado;
    }

    private String valor(String value) {
        return value == null ? "" : value;
    }
}
