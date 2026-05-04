package com.sistemapos.sistematextil.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sistemapos.sistematextil.model.Color;
import com.sistemapos.sistematextil.repositories.ColorRepository;
import com.sistemapos.sistematextil.util.color.ColorCreateRequest;
import com.sistemapos.sistematextil.util.paginacion.PagedResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ColorService {

    private static final String ESTADO_ACTIVO = "ACTIVO";
    private static final String ESTADO_INACTIVO = "INACTIVO";

    private final ColorRepository colorRepository;

    @Value("${application.pagination.default-size:10}")
    private int defaultPageSize;

    public PagedResponse<Color> listarPaginado(int page) {
        validarPagina(page);
        PageRequest pageable = PageRequest.of(page, defaultPageSize, Sort.by("idColor").descending());
        Page<Color> colores = colorRepository.findByDeletedAtIsNullAndEstadoOrderByIdColorDesc(ESTADO_ACTIVO, pageable);
        return PagedResponse.fromPage(colores);
    }

    public PagedResponse<Color> buscarPaginado(String q, int page) {
        validarPagina(page);
        String term = normalizar(q);
        if (term == null) {
            return listarPaginado(page);
        }

        PageRequest pageable = PageRequest.of(page, defaultPageSize, Sort.by("idColor").descending());
        Page<Color> colores = colorRepository.findByDeletedAtIsNullAndEstadoAndNombreContainingIgnoreCaseOrderByIdColorDesc(
                ESTADO_ACTIVO,
                term,
                pageable);
        return PagedResponse.fromPage(colores);
    }

    public Color obtenerPorId(Integer id) {
        return colorRepository.findByIdColorAndDeletedAtIsNullAndEstado(id, ESTADO_ACTIVO)
                .orElseThrow(() -> new RuntimeException("Color con ID " + id + " no encontrado"));
    }

    @Transactional
    public Color insertar(ColorCreateRequest request) {
        String nombre = normalizar(request.nombre());
        String codigo = normalizar(request.codigo());
        if (nombre == null) {
            throw new RuntimeException("El nombre del color es obligatorio");
        }
        if (codigo == null) {
            throw new RuntimeException("El codigo del color es obligatorio");
        }

        Color colorExistente = colorRepository.findByNombreIgnoreCase(nombre).orElse(null);
        if (colorExistente != null) {
            if (estaDisponible(colorExistente)) {
                throw new RuntimeException("El color '" + nombre + "' ya existe");
            }

            colorExistente.setNombre(nombre);
            colorExistente.setCodigo(codigo);
            colorExistente.setEstado(ESTADO_ACTIVO);
            colorExistente.setDeletedAt(null);
            try {
                return colorRepository.save(colorExistente);
            } catch (DataIntegrityViolationException e) {
                throw new RuntimeException("El color '" + nombre + "' ya existe");
            }
        }

        Color color = new Color();
        color.setNombre(nombre);
        color.setCodigo(codigo);
        color.setEstado(ESTADO_ACTIVO);
        color.setDeletedAt(null);

        try {
            return colorRepository.save(color);
        } catch (DataIntegrityViolationException e) {
            throw new RuntimeException("El color '" + nombre + "' ya existe");
        }
    }

    @Transactional
    public Color actualizar(Integer id, ColorCreateRequest request) {
        Color color = obtenerPorId(id);
        String nombre = normalizar(request.nombre());
        String codigo = normalizar(request.codigo());
        if (nombre == null) {
            throw new RuntimeException("El nombre del color es obligatorio");
        }
        if (codigo == null) {
            throw new RuntimeException("El codigo del color es obligatorio");
        }

        Color colorConMismoNombre = colorRepository.findByNombreIgnoreCase(nombre).orElse(null);
        if (colorConMismoNombre != null && !colorConMismoNombre.getIdColor().equals(id)) {
            if (estaDisponible(colorConMismoNombre)) {
                throw new RuntimeException("El color '" + nombre + "' ya existe");
            }
            throw new RuntimeException("Ya existe un color eliminado con el nombre '" + nombre + "'");
        }

        color.setNombre(nombre);
        color.setCodigo(codigo);
        color.setEstado(ESTADO_ACTIVO);
        color.setDeletedAt(null);

        try {
            return colorRepository.save(color);
        } catch (DataIntegrityViolationException e) {
            throw new RuntimeException("El color '" + nombre + "' ya existe");
        }
    }

    @Transactional
    public void eliminar(Integer id) {
        Color color = obtenerPorId(id);
        color.setEstado(ESTADO_INACTIVO);
        color.setDeletedAt(java.time.LocalDateTime.now());
        colorRepository.save(color);
    }

    private void validarPagina(int page) {
        if (page < 0) {
            throw new RuntimeException("El parametro page debe ser mayor o igual a 0");
        }
    }

    private String normalizar(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean estaDisponible(Color color) {
        return color.getDeletedAt() == null && ESTADO_ACTIVO.equalsIgnoreCase(color.getEstado());
    }
}
