package com.sistemapos.sistematextil.services;

import java.util.List;

import org.springframework.stereotype.Service;

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

    public Color obtenerPorId(Integer id) {
        return colorRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Color con ID " + id + " no encontrado"));
    }

    public Color insertar(Color color) {
        return colorRepository.save(color);
    }
    
    public Color actualizar(Integer id, Color colorActualizado) {
    Color colorExistente = obtenerPorId(id);
    colorExistente.setNombre(colorActualizado.getNombre());
    return colorRepository.save(colorExistente);
}
    
    public void eliminar(Integer id) {
        colorRepository.deleteById(id);
    }
}