package com.sistemapos.sistematextil.services;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.sistemapos.sistematextil.model.Categoria;
import com.sistemapos.sistematextil.model.Sucursal;
import com.sistemapos.sistematextil.repositories.CategoriaRepository;
import com.sistemapos.sistematextil.repositories.SucursalRepository;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class CategoriaService {

    private final CategoriaRepository categoriaRepository;
    private final SucursalRepository sucursalRepository; // Inyectamos SucursalRepository

    public List<Categoria> listarTodas() {
        return categoriaRepository.findAll();
    }

    public Categoria insertar(Categoria categoria) {
    // 1. Validar que la sucursal exista
    if (categoria.getSucursal() == null || categoria.getSucursal().getIdSucursal() == null) {
        throw new RuntimeException("Debe especificar una sucursal válida");
    }
    
    Sucursal sucursal = sucursalRepository.findById(categoria.getSucursal().getIdSucursal())
            .orElseThrow(() -> new RuntimeException("La sucursal no existe"));

    // 2. VALIDACIÓN DE SEGURIDAD: ¿La sucursal está activa?
    if (!"ACTIVO".equalsIgnoreCase(sucursal.getEstado())) {
        throw new RuntimeException("No se pueden crear categorías en una sucursal INACTIVA");
    }

    // 3. Asignar la sucursal completa para que el JSON de respuesta sea rico en datos
    categoria.setSucursal(sucursal);

    // 4. Lógica de auditoría
    if (categoria.getFechaRegistro() == null) {
        categoria.setFechaRegistro(LocalDateTime.now());
    }
    if (categoria.getEstado() == null) {
        categoria.setEstado("ACTIVO");
    }

    return categoriaRepository.save(categoria);
}

    public Categoria obtenerPorId(Integer id) {
        return categoriaRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("La categoría con ID " + id + " no existe"));
    }

    public Categoria actualizar(Integer id, Categoria categoria) {
        Categoria original = obtenerPorId(id);
        
        // Validar sucursal si se intenta cambiar en la actualización
        if (categoria.getSucursal() != null && categoria.getSucursal().getIdSucursal() != null) {
            Sucursal sucursal = sucursalRepository.findById(categoria.getSucursal().getIdSucursal())
                    .orElseThrow(() -> new RuntimeException("La sucursal no existe"));
            categoria.setSucursal(sucursal);
        } else {
            categoria.setSucursal(original.getSucursal());
        }

        categoria.setIdCategoria(id);
        categoria.setFechaRegistro(original.getFechaRegistro());
        
        if (categoria.getEstado() == null) {
            categoria.setEstado(original.getEstado());
        }
        
        return categoriaRepository.save(categoria);
    }

    public Categoria cambiarEstado(Integer id) {
        Categoria categoria = obtenerPorId(id);
        
        // Toggle de estado
        categoria.setEstado("ACTIVO".equalsIgnoreCase(categoria.getEstado()) ? "INACTIVO" : "ACTIVO");
        
        return categoriaRepository.save(categoria);
    }

    public void eliminar(Integer id) {
        if (!categoriaRepository.existsById(id)) {
            throw new RuntimeException("La categoría " + id + " no existe");
        }
        categoriaRepository.deleteById(id);
    }
}