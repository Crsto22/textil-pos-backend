package com.sistemapos.sistematextil.services;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.sistemapos.sistematextil.model.Color;
import com.sistemapos.sistematextil.repositories.ColorRepository;
import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class ColorService {
    private final ColorRepository colorRepository;

    public List<Color> listarTodos() {
        return colorRepository.findAll();
    }

    public List<Color> listarActivos() {
        return colorRepository.findByEstado("ACTIVO");
    }

    public Color obtenerPorId(Integer id) {
        return colorRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Color con ID " + id + " no encontrado"));
    }

    @Transactional
    public Color insertar(Color color) {
        color.setEstado("ACTIVO"); // Garantizamos estado inicial
        return colorRepository.save(color);
    }
    
    @Transactional
    public Color actualizar(Integer id, Color colorActualizado) {
        Color colorExistente = obtenerPorId(id);
        colorExistente.setNombre(colorActualizado.getNombre());
        colorExistente.setCodigo(colorActualizado.getCodigo()); // Actualizamos el Hex
        
        if (colorActualizado.getEstado() != null) {
            colorExistente.setEstado(colorActualizado.getEstado());
        }
        
        return colorRepository.save(colorExistente);
    }

    @Transactional
    public Color cambiarEstado(Integer id) {
        Color color = obtenerPorId(id);
        color.setEstado("ACTIVO".equals(color.getEstado()) ? "INACTIVO" : "ACTIVO");
        return colorRepository.save(color);
    }
    
    public void eliminar(Integer id) {
        Color color = obtenerPorId(id);
        if (colorRepository.estaEnUso(id)) {
            throw new RuntimeException("No se puede eliminar el color '" + color.getNombre() + 
                "' porque está asociado a productos. Desactívelo en su lugar.");
        }
        colorRepository.deleteById(id);
    }
}