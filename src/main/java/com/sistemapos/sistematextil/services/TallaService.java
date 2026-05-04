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
import com.sistemapos.sistematextil.util.paginacion.PagedResponse;
import com.sistemapos.sistematextil.util.talla.TallaCreateRequest;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TallaService {

    private static final String ESTADO_ACTIVO = "ACTIVO";
    private static final String ESTADO_INACTIVO = "INACTIVO";

    private final TallaRepository tallaRepository;

    @Value("${application.pagination.default-size:10}")
    private int defaultPageSize;

    public PagedResponse<Talla> listarPaginado(int page) {
        validarPagina(page);
        PageRequest pageable = PageRequest.of(page, defaultPageSize, Sort.by("idTalla").descending());
        Page<Talla> tallas = tallaRepository.findByDeletedAtIsNullAndEstadoOrderByIdTallaDesc(ESTADO_ACTIVO, pageable);
        return PagedResponse.fromPage(tallas);
    }

    public PagedResponse<Talla> buscarPaginado(String q, int page) {
        validarPagina(page);
        String term = normalizar(q);
        if (term == null) {
            return listarPaginado(page);
        }

        PageRequest pageable = PageRequest.of(page, defaultPageSize, Sort.by("idTalla").descending());
        Page<Talla> tallas = tallaRepository.findByDeletedAtIsNullAndEstadoAndNombreContainingIgnoreCaseOrderByIdTallaDesc(
                ESTADO_ACTIVO,
                term,
                pageable);
        return PagedResponse.fromPage(tallas);
    }

    public Talla obtenerPorId(Integer id) {
        return tallaRepository.findByIdTallaAndDeletedAtIsNullAndEstado(id, ESTADO_ACTIVO)
                .orElseThrow(() -> new RuntimeException("Talla con ID " + id + " no encontrada"));
    }

    @Transactional
    public Talla insertar(TallaCreateRequest request) {
        String nombre = normalizar(request.nombre());
        if (nombre == null) {
            throw new RuntimeException("El nombre de la talla es obligatorio");
        }

        Talla tallaExistente = tallaRepository.findByNombreIgnoreCase(nombre).orElse(null);
        if (tallaExistente != null) {
            if (estaDisponible(tallaExistente)) {
                throw new RuntimeException("La talla '" + nombre + "' ya existe");
            }

            tallaExistente.setNombre(nombre);
            tallaExistente.setEstado(ESTADO_ACTIVO);
            tallaExistente.setDeletedAt(null);
            try {
                return tallaRepository.save(tallaExistente);
            } catch (DataIntegrityViolationException e) {
                throw new RuntimeException("La talla '" + nombre + "' ya existe");
            }
        }

        Talla talla = new Talla();
        talla.setNombre(nombre);
        talla.setEstado(ESTADO_ACTIVO);
        talla.setDeletedAt(null);

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

        Talla tallaConMismoNombre = tallaRepository.findByNombreIgnoreCase(nombre).orElse(null);
        if (tallaConMismoNombre != null && !tallaConMismoNombre.getIdTalla().equals(id)) {
            if (estaDisponible(tallaConMismoNombre)) {
                throw new RuntimeException("La talla '" + nombre + "' ya existe");
            }
            throw new RuntimeException("Ya existe una talla eliminada con el nombre '" + nombre + "'");
        }

        talla.setNombre(nombre);
        talla.setEstado(ESTADO_ACTIVO);
        talla.setDeletedAt(null);
        try {
            return tallaRepository.save(talla);
        } catch (DataIntegrityViolationException e) {
            throw new RuntimeException("La talla '" + nombre + "' ya existe");
        }
    }

    @Transactional
    public void eliminar(Integer id) {
        Talla talla = obtenerPorId(id);
        talla.setEstado(ESTADO_INACTIVO);
        talla.setDeletedAt(java.time.LocalDateTime.now());
        tallaRepository.save(talla);
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

    private boolean estaDisponible(Talla talla) {
        return talla.getDeletedAt() == null && ESTADO_ACTIVO.equalsIgnoreCase(talla.getEstado());
    }
}
