package com.sistemapos.sistematextil.services;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sistemapos.sistematextil.model.Talla;
import com.sistemapos.sistematextil.repositories.TallaRepository;
import com.sistemapos.sistematextil.util.PagedResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TallaService {
    private final TallaRepository tallaRepository;

    @Value("${application.pagination.default-size:10}")
    private int defaultPageSize;

    public PagedResponse<Talla> listarPaginado(int page) {
        PageRequest pageable = PageRequest.of(page, defaultPageSize, Sort.by("idTalla").ascending());
        Page<Talla> tallas = tallaRepository.findAll(pageable);
        return PagedResponse.fromPage(tallas);
    }

    // NUEVO: Para que el Front solo muestre las tallas que se pueden usar
    public List<Talla> listarActivas() {
        return tallaRepository.findByEstado("ACTIVO");
    }

    public Talla obtenerPorId(Integer id) {
        return tallaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Talla con ID " + id + " no encontrada"));
    }

    @Transactional
    public Talla insertar(Talla talla) {
        // Forzamos que siempre sea ACTIVO al crear
        talla.setEstado("ACTIVO");
        return tallaRepository.save(talla);
    }

    @Transactional
    public Talla actualizar(Integer id, Talla tallaActualizada) {
        Talla tallaExistente = obtenerPorId(id);
        tallaExistente.setNombre(tallaActualizada.getNombre());

        // Si el front manda un estado nuevo, lo actualizamos, si no, mantenemos el actual
        if (tallaActualizada.getEstado() != null) {
            tallaExistente.setEstado(tallaActualizada.getEstado());
        }

        return tallaRepository.save(tallaExistente);
    }

    // NUEVO: Metodo para activar/desactivar (Toggle)
    @Transactional
    public Talla cambiarEstado(Integer id) {
        Talla talla = obtenerPorId(id);
        String nuevoEstado = "ACTIVO".equals(talla.getEstado()) ? "INACTIVO" : "ACTIVO";
        talla.setEstado(nuevoEstado);
        return tallaRepository.save(talla);
    }

    public void eliminar(Integer id) {
        Talla talla = obtenerPorId(id);

        // 1. Verificamos si la talla esta siendo usada por algun producto
        if (tallaRepository.estaEnUso(id)) {
            throw new RuntimeException("No se puede eliminar la talla '" + talla.getNombre() +
                    "' porque ya esta asociada a productos. Te sugiero desactivarla.");
        }

        // 2. Si no esta en uso, se borra fisicamente
        tallaRepository.deleteById(id);
    }
}
