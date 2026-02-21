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

    private final ColorRepository colorRepository;

    @Value("${application.pagination.default-size:10}")
    private int defaultPageSize;

    public PagedResponse<Color> listarPaginado(int page) {
        validarPagina(page);
        PageRequest pageable = PageRequest.of(page, defaultPageSize, Sort.by("idColor").ascending());
        Page<Color> colores = colorRepository.findAll(pageable);
        return PagedResponse.fromPage(colores);
    }

    public PagedResponse<Color> buscarPaginado(String q, int page) {
        validarPagina(page);
        String term = normalizar(q);
        if (term == null) {
            return listarPaginado(page);
        }

        PageRequest pageable = PageRequest.of(page, defaultPageSize, Sort.by("idColor").ascending());
        Page<Color> colores = colorRepository.findByNombreContainingIgnoreCase(term, pageable);
        return PagedResponse.fromPage(colores);
    }

    public Color obtenerPorId(Integer id) {
        return colorRepository.findById(id)
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

        if (colorRepository.existsByNombreIgnoreCase(nombre)) {
            throw new RuntimeException("El color '" + nombre + "' ya existe");
        }

        Color color = new Color();
        color.setNombre(nombre);
        color.setCodigo(codigo);
        color.setEstado("ACTIVO");

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

        if (colorRepository.existsByNombreIgnoreCaseAndIdColorNot(nombre, id)) {
            throw new RuntimeException("El color '" + nombre + "' ya existe");
        }

        color.setNombre(nombre);
        color.setCodigo(codigo);

        try {
            return colorRepository.save(color);
        } catch (DataIntegrityViolationException e) {
            throw new RuntimeException("El color '" + nombre + "' ya existe");
        }
    }

    @Transactional
    public void eliminar(Integer id) {
        Color color = obtenerPorId(id);
        if (colorRepository.estaEnUso(id)) {
            throw new RuntimeException("No se puede eliminar el color '" + color.getNombre()
                    + "' porque esta asociado a productos. Te sugiero desactivarlo.");
        }
        colorRepository.deleteById(id);
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
}
