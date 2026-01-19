package com.sistemapos.sistematextil.services;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    // NUEVO: Método para activar/desactivar (Toggle)
    @Transactional
    public Talla cambiarEstado(Integer id) {
        Talla talla = obtenerPorId(id);
        String nuevoEstado = "ACTIVO".equals(talla.getEstado()) ? "INACTIVO" : "ACTIVO";
        talla.setEstado(nuevoEstado);
        return tallaRepository.save(talla);
    }
    
    public void eliminar(Integer id) {
    Talla talla = obtenerPorId(id);
    
    // 1. Verificamos si la talla está siendo usada por algún producto
    if (tallaRepository.estaEnUso(id)) {
        throw new RuntimeException("No se puede eliminar la talla '" + talla.getNombre() + 
            "' porque ya está asociada a productos. ¡Te sugiero desactivarla!");
    }
    
    // 2. Si no está en uso, se borra físicamente
    tallaRepository.deleteById(id);
}
}