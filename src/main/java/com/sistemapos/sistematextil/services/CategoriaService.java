package com.sistemapos.sistematextil.services;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sistemapos.sistematextil.model.Categoria;
import com.sistemapos.sistematextil.model.Sucursal;
import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.repositories.CategoriaRepository;
import com.sistemapos.sistematextil.repositories.SucursalRepository;
import com.sistemapos.sistematextil.repositories.UsuarioRepository;
import com.sistemapos.sistematextil.util.categoria.CategoriaCreateRequest;
import com.sistemapos.sistematextil.util.categoria.CategoriaListItemResponse;
import com.sistemapos.sistematextil.util.categoria.CategoriaUpdateRequest;
import com.sistemapos.sistematextil.util.paginacion.PagedResponse;
import com.sistemapos.sistematextil.util.usuario.Rol;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CategoriaService {

    private static final String ESTADO_ACTIVO = "ACTIVO";
    private static final String ESTADO_INACTIVO = "INACTIVO";

    private final CategoriaRepository categoriaRepository;
    private final SucursalRepository sucursalRepository;
    private final UsuarioRepository usuarioRepository;
    private final UsuarioSucursalAccessService usuarioSucursalAccessService;

    @Value("${application.pagination.default-size:10}")
    private int defaultPageSize;

    public PagedResponse<CategoriaListItemResponse> listarPaginado(
            int page,
            Integer idSucursal,
            String correoUsuarioAutenticado) {
        validarPagina(page);
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolLecturaPermitido(usuarioAutenticado);

        PageRequest pageable = PageRequest.of(page, defaultPageSize, Sort.by("idCategoria").descending());
        Integer idSucursalFiltro = resolverIdSucursalFiltroListado(usuarioAutenticado, idSucursal);
        Sucursal sucursalContexto = resolverSucursalContexto(idSucursalFiltro, usuarioAutenticado);
        Page<Categoria> categorias = idSucursalFiltro == null
                ? categoriaRepository.findByDeletedAtIsNullAndEstadoOrderByIdCategoriaDesc(ESTADO_ACTIVO, pageable)
                : categoriaRepository.findBySucursal_IdSucursalAndDeletedAtIsNullAndEstadoOrderByIdCategoriaDesc(
                        idSucursalFiltro,
                        ESTADO_ACTIVO,
                        pageable);

        return PagedResponse.fromPage(categorias.map(categoria -> toListItemResponse(categoria, sucursalContexto)));
    }

    public PagedResponse<CategoriaListItemResponse> buscarPaginado(
            String q,
            int page,
            Integer idSucursal,
            String correoUsuarioAutenticado) {
        validarPagina(page);
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolLecturaPermitido(usuarioAutenticado);

        String term = normalizar(q);
        if (term == null) {
            return listarPaginado(page, idSucursal, correoUsuarioAutenticado);
        }

        PageRequest pageable = PageRequest.of(page, defaultPageSize, Sort.by("idCategoria").descending());
        Integer idSucursalFiltro = resolverIdSucursalFiltroListado(usuarioAutenticado, idSucursal);
        Sucursal sucursalContexto = resolverSucursalContexto(idSucursalFiltro, usuarioAutenticado);
        Page<Categoria> categorias = idSucursalFiltro == null
                ? categoriaRepository
                        .findByDeletedAtIsNullAndEstadoAndNombreCategoriaContainingIgnoreCaseOrderByIdCategoriaDesc(
                                ESTADO_ACTIVO,
                                term,
                                pageable)
                : categoriaRepository
                        .findBySucursal_IdSucursalAndDeletedAtIsNullAndEstadoAndNombreCategoriaContainingIgnoreCaseOrderByIdCategoriaDesc(
                                idSucursalFiltro,
                                ESTADO_ACTIVO,
                                term,
                                pageable);

        return PagedResponse.fromPage(categorias.map(categoria -> toListItemResponse(categoria, sucursalContexto)));
    }

    @Transactional
    public CategoriaListItemResponse insertar(CategoriaCreateRequest request, String correoUsuarioAutenticado) {
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolEdicionPermitido(usuarioAutenticado);

        Sucursal sucursal = resolverSucursalContexto(request.idSucursal(), usuarioAutenticado);
        String nombreCategoria = normalizarNombreCategoria(request.nombreCategoria());
        String descripcion = normalizar(request.descripcion());

        Categoria categoriaExistente = categoriaRepository
                .findBySucursal_IdSucursalAndNombreCategoriaIgnoreCase(sucursal.getIdSucursal(), nombreCategoria)
                .orElse(null);

        if (categoriaExistente != null) {
            if (estaDisponible(categoriaExistente)) {
                throw new RuntimeException("La categoria '" + nombreCategoria + "' ya existe en esta sucursal");
            }

            categoriaExistente.setSucursal(sucursal);
            categoriaExistente.setNombreCategoria(nombreCategoria);
            categoriaExistente.setDescripcion(descripcion);
            categoriaExistente.setEstado(ESTADO_ACTIVO);
            categoriaExistente.setDeletedAt(null);

            try {
                Categoria reactivada = categoriaRepository.save(categoriaExistente);
                return toListItemResponse(reactivada, sucursal);
            } catch (DataIntegrityViolationException e) {
                throw new RuntimeException("La categoria '" + nombreCategoria + "' ya existe en esta sucursal");
            }
        }

        Categoria categoria = new Categoria();
        categoria.setSucursal(sucursal);
        categoria.setNombreCategoria(nombreCategoria);
        categoria.setDescripcion(descripcion);
        categoria.setEstado(ESTADO_ACTIVO);
        categoria.setDeletedAt(null);
        categoria.setFechaRegistro(LocalDateTime.now());

        try {
            Categoria creada = categoriaRepository.save(categoria);
            return toListItemResponse(creada, sucursal);
        } catch (DataIntegrityViolationException e) {
            throw new RuntimeException("La categoria '" + nombreCategoria + "' ya existe en esta sucursal");
        }
    }

    @Transactional
    public CategoriaListItemResponse actualizar(
            Integer idCategoria,
            CategoriaUpdateRequest request,
            String correoUsuarioAutenticado) {
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolEdicionPermitido(usuarioAutenticado);

        Categoria categoria = obtenerCategoriaConAlcance(idCategoria, usuarioAutenticado);
        Sucursal sucursalDestino = resolverSucursalContexto(request.idSucursal(), usuarioAutenticado);

        String nombreCategoria = normalizarNombreCategoria(request.nombreCategoria());
        String descripcion = normalizar(request.descripcion());

        Categoria categoriaConMismoNombre = categoriaRepository
                .findBySucursal_IdSucursalAndNombreCategoriaIgnoreCase(sucursalDestino.getIdSucursal(), nombreCategoria)
                .orElse(null);
        if (categoriaConMismoNombre != null && !categoriaConMismoNombre.getIdCategoria().equals(idCategoria)) {
            if (estaDisponible(categoriaConMismoNombre)) {
                throw new RuntimeException("La categoria '" + nombreCategoria + "' ya existe en esta sucursal");
            }
            throw new RuntimeException(
                    "Ya existe una categoria eliminada con el nombre '" + nombreCategoria + "' en esta sucursal");
        }

        categoria.setSucursal(sucursalDestino);
        categoria.setNombreCategoria(nombreCategoria);
        categoria.setDescripcion(descripcion);

        try {
            Categoria actualizada = categoriaRepository.save(categoria);
            return toListItemResponse(actualizada, sucursalDestino);
        } catch (DataIntegrityViolationException e) {
            throw new RuntimeException("La categoria '" + nombreCategoria + "' ya existe en esta sucursal");
        }
    }

    @Transactional
    public void eliminar(Integer idCategoria, String correoUsuarioAutenticado) {
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolEdicionPermitido(usuarioAutenticado);

        Categoria categoria = obtenerCategoriaConAlcance(idCategoria, usuarioAutenticado);
        categoria.setEstado(ESTADO_INACTIVO);
        categoria.setDeletedAt(LocalDateTime.now());
        categoriaRepository.save(categoria);
    }

    public Categoria obtenerPorId(Integer id) {
        return categoriaRepository.findByIdCategoriaAndDeletedAtIsNullAndEstado(id, ESTADO_ACTIVO)
                .orElseThrow(() -> new RuntimeException("Categoria con ID " + id + " no encontrada"));
    }

    private Categoria obtenerCategoriaConAlcance(Integer idCategoria, Usuario usuarioAutenticado) {
        return categoriaRepository.findByIdCategoriaAndDeletedAtIsNullAndEstado(idCategoria, ESTADO_ACTIVO)
                .orElseThrow(() -> new RuntimeException("Categoria con ID " + idCategoria + " no encontrada"));
    }

    private Sucursal obtenerSucursalActiva(Integer idSucursal) {
        Sucursal sucursal = sucursalRepository.findByIdSucursalAndDeletedAtIsNull(idSucursal)
                .orElseThrow(() -> new RuntimeException("Sucursal no encontrada"));
        if (!ESTADO_ACTIVO.equalsIgnoreCase(sucursal.getEstado())) {
            throw new RuntimeException("No se pueden gestionar categorias en una sucursal INACTIVA");
        }
        return sucursal;
    }

    private Sucursal resolverSucursalContexto(Integer idSucursalRequest, Usuario usuarioAutenticado) {
        Integer idSucursalDestino = resolverIdSucursalFiltroListado(usuarioAutenticado, idSucursalRequest);
        return idSucursalDestino == null ? null : obtenerSucursalActiva(idSucursalDestino);
    }

    private Integer resolverIdSucursalFiltroListado(Usuario usuarioAutenticado, Integer idSucursalRequest) {
        return usuarioSucursalAccessService.resolverIdSucursalFiltro(
                usuarioAutenticado,
                idSucursalRequest,
                "No tiene permisos para consultar otra sucursal");
    }

    private Usuario obtenerUsuarioAutenticado(String correoUsuarioAutenticado) {
        if (correoUsuarioAutenticado == null || correoUsuarioAutenticado.isBlank()) {
            throw new RuntimeException("No autenticado");
        }
        return usuarioRepository.findByCorreoAndDeletedAtIsNull(correoUsuarioAutenticado)
                .orElseThrow(() -> new RuntimeException("Usuario autenticado no encontrado"));
    }

    private void validarRolLecturaPermitido(Usuario usuario) {
        if (!usuario.getRol().permiteVentas() && !usuario.getRol().permiteAlmacen()) {
            throw new RuntimeException("El usuario autenticado no tiene permisos para consultar categorias");
        }
    }

    private void validarRolEdicionPermitido(Usuario usuario) {
        if (!usuario.getRol().permiteAlmacen()) {
            throw new RuntimeException("El usuario autenticado no tiene permisos para editar categorias");
        }
    }

    private boolean esAdministrador(Usuario usuario) {
        return usuario.getRol().esAdministrador();
    }

    private void validarPagina(int page) {
        if (page < 0) {
            throw new RuntimeException("El parametro page debe ser mayor o igual a 0");
        }
    }

    private String normalizarNombreCategoria(String value) {
        String normalizada = normalizar(value);
        if (normalizada == null) {
            throw new RuntimeException("El nombre de la categoria es obligatorio");
        }
        return normalizada;
    }

    private String normalizar(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private CategoriaListItemResponse toListItemResponse(Categoria categoria, Sucursal sucursalContexto) {
        Integer idSucursal = sucursalContexto != null ? sucursalContexto.getIdSucursal() : null;
        String nombreSucursal = sucursalContexto != null ? sucursalContexto.getNombre() : null;

        return new CategoriaListItemResponse(
                categoria.getIdCategoria(),
                categoria.getNombreCategoria(),
                categoria.getDescripcion(),
                categoria.getEstado(),
                categoria.getFechaRegistro(),
                idSucursal,
                nombreSucursal);
    }

    private boolean estaDisponible(Categoria categoria) {
        return categoria.getDeletedAt() == null && ESTADO_ACTIVO.equalsIgnoreCase(categoria.getEstado());
    }
}
