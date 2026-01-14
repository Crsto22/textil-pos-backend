package com.sistemapos.sistematextil.services;

import java.util.List;

import org.springframework.stereotype.Service;

import com.sistemapos.sistematextil.model.Talla;
import com.sistemapos.sistematextil.repositories.TallaRepository;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class TallaService {
    private final TallaRepository tallaRepository;

    public List<Talla> listarTodas() {
        return tallaRepository.findAll();
    }

    public Talla obtenerPorId(Integer id) {
        return tallaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Talla con ID " + id + " no encontrada"));
    }

    public Talla insertar(Talla talla) {
        return tallaRepository.save(talla);
    }

    public Talla actualizar(Integer id, Talla tallaActualizada) {
    Talla tallaExistente = obtenerPorId(id); // Reutiliza tu método de validar ID
    tallaExistente.setNombre(tallaActualizada.getNombre());
    return tallaRepository.save(tallaExistente);
}
    
    public void eliminar(Integer id) {
        tallaRepository.deleteById(id);
    }
}