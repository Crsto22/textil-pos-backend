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
import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.repositories.CategoriaRepository;
import com.sistemapos.sistematextil.repositories.UsuarioRepository;
import com.sistemapos.sistematextil.util.categoria.CategoriaCreateRequest;
import com.sistemapos.sistematextil.util.categoria.CategoriaListItemResponse;
import com.sistemapos.sistematextil.util.categoria.CategoriaUpdateRequest;
import com.sistemapos.sistematextil.util.paginacion.PagedResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CategoriaService {

    private static final String ESTADO_ACTIVO = "ACTIVO";
    private static final String ESTADO_INACTIVO = "INACTIVO";

    private final CategoriaRepository categoriaRepository;
    private final UsuarioRepository usuarioRepository;

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
        Page<Categoria> categorias = categoriaRepository
                .findByDeletedAtIsNullAndEstadoOrderByIdCategoriaDesc(ESTADO_ACTIVO, pageable);

        return PagedResponse.fromPage(categorias.map(this::toListItemResponse));
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
        Page<Categoria> categorias = categoriaRepository
                .findByDeletedAtIsNullAndEstadoAndNombreCategoriaContainingIgnoreCaseOrderByIdCategoriaDesc(
                        ESTADO_ACTIVO,
                        term,
                        pageable);

        return PagedResponse.fromPage(categorias.map(this::toListItemResponse));
    }

    @Transactional
    public CategoriaListItemResponse insertar(CategoriaCreateRequest request, String correoUsuarioAutenticado) {
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolEdicionPermitido(usuarioAutenticado);

        String nombreCategoria = normalizarNombreCategoria(request.nombreCategoria());
        String descripcion = normalizar(request.descripcion());

        Categoria categoriaExistente = categoriaRepository
                .findByNombreCategoriaIgnoreCase(nombreCategoria)
                .orElse(null);

        if (categoriaExistente != null) {
            if (estaDisponible(categoriaExistente)) {
                throw new RuntimeException("La categoria '" + nombreCategoria + "' ya existe");
            }

            categoriaExistente.setNombreCategoria(nombreCategoria);
            categoriaExistente.setDescripcion(descripcion);
            categoriaExistente.setEstado(ESTADO_ACTIVO);
            categoriaExistente.setDeletedAt(null);

            try {
                Categoria reactivada = categoriaRepository.save(categoriaExistente);
                return toListItemResponse(reactivada);
            } catch (DataIntegrityViolationException e) {
                throw new RuntimeException("La categoria '" + nombreCategoria + "' ya existe");
            }
        }

        Categoria categoria = new Categoria();
        categoria.setNombreCategoria(nombreCategoria);
        categoria.setDescripcion(descripcion);
        categoria.setEstado(ESTADO_ACTIVO);
        categoria.setDeletedAt(null);
        categoria.setFechaRegistro(LocalDateTime.now());

        try {
            Categoria creada = categoriaRepository.save(categoria);
            return toListItemResponse(creada);
        } catch (DataIntegrityViolationException e) {
            throw new RuntimeException("La categoria '" + nombreCategoria + "' ya existe");
        }
    }

    @Transactional
    public CategoriaListItemResponse actualizar(
            Integer idCategoria,
            CategoriaUpdateRequest request,
            String correoUsuarioAutenticado) {
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolEdicionPermitido(usuarioAutenticado);

        Categoria categoria = obtenerCategoriaActiva(idCategoria);

        String nombreCategoria = normalizarNombreCategoria(request.nombreCategoria());
        String descripcion = normalizar(request.descripcion());

        Categoria categoriaConMismoNombre = categoriaRepository
                .findByNombreCategoriaIgnoreCase(nombreCategoria)
                .orElse(null);
        if (categoriaConMismoNombre != null && !categoriaConMismoNombre.getIdCategoria().equals(idCategoria)) {
            if (estaDisponible(categoriaConMismoNombre)) {
                throw new RuntimeException("La categoria '" + nombreCategoria + "' ya existe");
            }
            throw new RuntimeException(
                    "Ya existe una categoria eliminada con el nombre '" + nombreCategoria + "'");
        }

        categoria.setNombreCategoria(nombreCategoria);
        categoria.setDescripcion(descripcion);

        try {
            Categoria actualizada = categoriaRepository.save(categoria);
            return toListItemResponse(actualizada);
        } catch (DataIntegrityViolationException e) {
            throw new RuntimeException("La categoria '" + nombreCategoria + "' ya existe");
        }
    }

    @Transactional
    public void eliminar(Integer idCategoria, String correoUsuarioAutenticado) {
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolEdicionPermitido(usuarioAutenticado);

        Categoria categoria = obtenerCategoriaActiva(idCategoria);
        categoria.setEstado(ESTADO_INACTIVO);
        categoria.setDeletedAt(LocalDateTime.now());
        categoriaRepository.save(categoria);
    }

    public Categoria obtenerPorId(Integer id) {
        return categoriaRepository.findByIdCategoriaAndDeletedAtIsNullAndEstado(id, ESTADO_ACTIVO)
                .orElseThrow(() -> new RuntimeException("Categoria con ID " + id + " no encontrada"));
    }

    private Categoria obtenerCategoriaActiva(Integer idCategoria) {
        return categoriaRepository.findByIdCategoriaAndDeletedAtIsNullAndEstado(idCategoria, ESTADO_ACTIVO)
                .orElseThrow(() -> new RuntimeException("Categoria con ID " + idCategoria + " no encontrada"));
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
        return new CategoriaListItemResponse(
                categoria.getIdCategoria(),
                categoria.getNombreCategoria(),
                categoria.getDescripcion(),
                categoria.getEstado(),
                categoria.getFechaRegistro(),
                null,
                null);
    }

    private boolean estaDisponible(Categoria categoria) {
        return categoria.getDeletedAt() == null && ESTADO_ACTIVO.equalsIgnoreCase(categoria.getEstado());
    }
}
