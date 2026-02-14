package com.sistemapos.sistematextil.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sistemapos.sistematextil.model.Talla;
import com.sistemapos.sistematextil.repositories.TallaRepository;
import com.sistemapos.sistematextil.util.PagedResponse;
import com.sistemapos.sistematextil.util.TallaCreateRequest;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TallaService {

    private final TallaRepository tallaRepository;

    @Value("${application.pagination.default-size:10}")
    private int defaultPageSize;

    public PagedResponse<Talla> listarPaginado(int page) {
        validarPagina(page);
        PageRequest pageable = PageRequest.of(page, defaultPageSize, Sort.by("idTalla").ascending());
        Page<Talla> tallas = tallaRepository.findAll(pageable);
        return PagedResponse.fromPage(tallas);
    }

    public PagedResponse<Talla> buscarPaginado(String q, int page) {
        validarPagina(page);
        String term = normalizar(q);
        if (term == null) {
            return listarPaginado(page);
        }

        PageRequest pageable = PageRequest.of(page, defaultPageSize, Sort.by("idTalla").ascending());
        Page<Talla> tallas = tallaRepository.findByNombreContainingIgnoreCase(term, pageable);
        return PagedResponse.fromPage(tallas);
    }

    public Talla obtenerPorId(Integer id) {
        return tallaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Talla con ID " + id + " no encontrada"));
    }

    @Transactional
    public Talla insertar(TallaCreateRequest request) {
        String nombre = normalizar(request.nombre());
        if (nombre == null) {
            throw new RuntimeException("El nombre de la talla es obligatorio");
        }

        if (tallaRepository.existsByNombreIgnoreCase(nombre)) {
            throw new RuntimeException("La talla '" + nombre + "' ya existe");
        }

        Talla talla = new Talla();
        talla.setNombre(nombre);
        talla.setEstado("ACTIVO");

        try {
            return tallaRepository.save(talla);
        } catch (DataIntegrityViolationException e) {
            throw new RuntimeException("La talla '" + nombre + "' ya existe");
        }
    }

    @Transactional
    public Talla actualizar(Integer id, TallaCreateRequest request) {
        Talla talla = obtenerPorId(id);
        String nombre = normalizar(request.nombre());
        if (nombre == null) {
            throw new RuntimeException("El nombre de la talla es obligatorio");
        }

        if (tallaRepository.existsByNombreIgnoreCaseAndIdTallaNot(nombre, id)) {
            throw new RuntimeException("La talla '" + nombre + "' ya existe");
        }

        talla.setNombre(nombre);
        try {
            return tallaRepository.save(talla);
        } catch (DataIntegrityViolationException e) {
            throw new RuntimeException("La talla '" + nombre + "' ya existe");
        }
    }

    @Transactional
    public void eliminar(Integer id) {
        Talla talla = obtenerPorId(id);

        if (tallaRepository.estaEnUso(id)) {
            throw new RuntimeException("No se puede eliminar la talla '" + talla.getNombre()
                    + "' porque ya esta asociada a productos. Te sugiero desactivarla.");
        }

        tallaRepository.deleteById(id);
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
