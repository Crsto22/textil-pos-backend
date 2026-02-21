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

    private final CategoriaRepository categoriaRepository;
    private final SucursalRepository sucursalRepository;
    private final UsuarioRepository usuarioRepository;

    @Value("${application.pagination.default-size:10}")
    private int defaultPageSize;

    public PagedResponse<CategoriaListItemResponse> listarPaginado(int page, String correoUsuarioAutenticado) {
        validarPagina(page);
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolPermitido(usuarioAutenticado);

        PageRequest pageable = PageRequest.of(page, defaultPageSize, Sort.by("idCategoria").ascending());
        Page<Categoria> categorias = esAdministrador(usuarioAutenticado)
                ? categoriaRepository.findAllByOrderByIdCategoriaAsc(pageable)
                : categoriaRepository.findBySucursal_IdSucursalOrderByIdCategoriaAsc(
                        obtenerIdSucursalUsuario(usuarioAutenticado),
                        pageable);

        return PagedResponse.fromPage(categorias.map(this::toListItemResponse));
    }

    public PagedResponse<CategoriaListItemResponse> buscarPaginado(String q, int page, String correoUsuarioAutenticado) {
        validarPagina(page);
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolPermitido(usuarioAutenticado);

        String term = normalizar(q);
        if (term == null) {
            return listarPaginado(page, correoUsuarioAutenticado);
        }

        PageRequest pageable = PageRequest.of(page, defaultPageSize, Sort.by("idCategoria").ascending());
        Page<Categoria> categorias = esAdministrador(usuarioAutenticado)
                ? categoriaRepository.findByNombreCategoriaContainingIgnoreCaseOrderByIdCategoriaAsc(term, pageable)
                : categoriaRepository.findBySucursal_IdSucursalAndNombreCategoriaContainingIgnoreCaseOrderByIdCategoriaAsc(
                        obtenerIdSucursalUsuario(usuarioAutenticado),
                        term,
                        pageable);

        return PagedResponse.fromPage(categorias.map(this::toListItemResponse));
    }

    @Transactional
    public CategoriaListItemResponse insertar(CategoriaCreateRequest request, String correoUsuarioAutenticado) {
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolPermitido(usuarioAutenticado);

        Sucursal sucursal = resolverSucursalParaEscritura(request.idSucursal(), usuarioAutenticado);
        String nombreCategoria = normalizarNombreCategoria(request.nombreCategoria());
        String descripcion = normalizar(request.descripcion());

        if (categoriaRepository.existsBySucursal_IdSucursalAndNombreCategoriaIgnoreCase(
                sucursal.getIdSucursal(),
                nombreCategoria)) {
            throw new RuntimeException("La categoria '" + nombreCategoria + "' ya existe en esta sucursal");
        }

        Categoria categoria = new Categoria();
        categoria.setSucursal(sucursal);
        categoria.setNombreCategoria(nombreCategoria);
        categoria.setDescripcion(descripcion);
        categoria.setEstado("ACTIVO");
        categoria.setFechaRegistro(LocalDateTime.now());

        try {
            Categoria creada = categoriaRepository.save(categoria);
            return toListItemResponse(creada);
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
        validarRolPermitido(usuarioAutenticado);

        Categoria categoria = obtenerCategoriaConAlcance(idCategoria, usuarioAutenticado);
        Sucursal sucursalDestino = resolverSucursalParaActualizacion(categoria, request.idSucursal(), usuarioAutenticado);

        String nombreCategoria = normalizarNombreCategoria(request.nombreCategoria());
        String descripcion = normalizar(request.descripcion());

        if (categoriaRepository.existsBySucursal_IdSucursalAndNombreCategoriaIgnoreCaseAndIdCategoriaNot(
                sucursalDestino.getIdSucursal(),
                nombreCategoria,
                idCategoria)) {
            throw new RuntimeException("La categoria '" + nombreCategoria + "' ya existe en esta sucursal");
        }

        categoria.setSucursal(sucursalDestino);
        categoria.setNombreCategoria(nombreCategoria);
        categoria.setDescripcion(descripcion);

        try {
            Categoria actualizada = categoriaRepository.save(categoria);
            return toListItemResponse(actualizada);
        } catch (DataIntegrityViolationException e) {
            throw new RuntimeException("La categoria '" + nombreCategoria + "' ya existe en esta sucursal");
        }
    }

    @Transactional
    public void eliminar(Integer idCategoria, String correoUsuarioAutenticado) {
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolPermitido(usuarioAutenticado);

        Categoria categoria = obtenerCategoriaConAlcance(idCategoria, usuarioAutenticado);
        if (categoriaRepository.estaEnUso(categoria.getIdCategoria())) {
            throw new RuntimeException("No se puede eliminar la categoria '" + categoria.getNombreCategoria()
                    + "' porque esta asociada a productos. Te sugiero desactivarla.");
        }

        categoriaRepository.deleteById(categoria.getIdCategoria());
    }

    public Categoria obtenerPorId(Integer id) {
        return categoriaRepository.findByIdCategoria(id)
                .orElseThrow(() -> new RuntimeException("Categoria con ID " + id + " no encontrada"));
    }

    private Categoria obtenerCategoriaConAlcance(Integer idCategoria, Usuario usuarioAutenticado) {
        if (esAdministrador(usuarioAutenticado)) {
            return categoriaRepository.findByIdCategoria(idCategoria)
                    .orElseThrow(() -> new RuntimeException("Categoria con ID " + idCategoria + " no encontrada"));
        }
        Integer idSucursalUsuario = obtenerIdSucursalUsuario(usuarioAutenticado);
        return categoriaRepository.findByIdCategoriaAndSucursal_IdSucursal(idCategoria, idSucursalUsuario)
                .orElseThrow(() -> new RuntimeException("Categoria con ID " + idCategoria + " no encontrada"));
    }

    private Sucursal resolverSucursalParaEscritura(Integer idSucursalRequest, Usuario usuarioAutenticado) {
        Integer idSucursalDestino = esAdministrador(usuarioAutenticado)
                ? idSucursalRequeridaParaAdmin(idSucursalRequest)
                : obtenerIdSucursalUsuario(usuarioAutenticado);
        return obtenerSucursalActiva(idSucursalDestino);
    }

    private Sucursal resolverSucursalParaActualizacion(
            Categoria categoriaOriginal,
            Integer idSucursalRequest,
            Usuario usuarioAutenticado) {
        Integer idSucursalDestino;
        if (esAdministrador(usuarioAutenticado)) {
            idSucursalDestino = idSucursalRequest != null
                    ? idSucursalRequest
                    : categoriaOriginal.getSucursal().getIdSucursal();
        } else {
            idSucursalDestino = obtenerIdSucursalUsuario(usuarioAutenticado);
        }
        return obtenerSucursalActiva(idSucursalDestino);
    }

    private Sucursal obtenerSucursalActiva(Integer idSucursal) {
        Sucursal sucursal = sucursalRepository.findByIdSucursalAndDeletedAtIsNull(idSucursal)
                .orElseThrow(() -> new RuntimeException("Sucursal no encontrada"));
        if (!"ACTIVO".equalsIgnoreCase(sucursal.getEstado())) {
            throw new RuntimeException("No se pueden gestionar categorias en una sucursal INACTIVA");
        }
        return sucursal;
    }

    private Integer idSucursalRequeridaParaAdmin(Integer idSucursalRequest) {
        if (idSucursalRequest == null) {
            throw new RuntimeException("Ingrese idSucursal");
        }
        return idSucursalRequest;
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

    private void validarRolPermitido(Usuario usuario) {
        if (usuario.getRol() != Rol.ADMINISTRADOR
                && usuario.getRol() != Rol.VENTAS
                && usuario.getRol() != Rol.ALMACEN) {
            throw new RuntimeException("El usuario autenticado no tiene permisos para gestionar categorias");
        }
    }

    private boolean esAdministrador(Usuario usuario) {
        return usuario.getRol() == Rol.ADMINISTRADOR;
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

    private CategoriaListItemResponse toListItemResponse(Categoria categoria) {
        Integer idSucursal = categoria.getSucursal() != null ? categoria.getSucursal().getIdSucursal() : null;
        String nombreSucursal = categoria.getSucursal() != null ? categoria.getSucursal().getNombre() : null;

        return new CategoriaListItemResponse(
                categoria.getIdCategoria(),
                categoria.getNombreCategoria(),
                categoria.getDescripcion(),
                categoria.getEstado(),
                categoria.getFechaRegistro(),
                idSucursal,
                nombreSucursal);
    }
}
