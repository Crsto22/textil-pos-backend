package com.sistemapos.sistematextil.services;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sistemapos.sistematextil.model.Empresa;
import com.sistemapos.sistematextil.model.Sucursal;
import com.sistemapos.sistematextil.repositories.EmpresaRepository;
import com.sistemapos.sistematextil.repositories.SucursalRepository;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class SucursalService {

    private final SucursalRepository sucursalRepository;
    private final EmpresaRepository empresaRepository; // Inyectado para evitar los NULLs

    public List<Sucursal> listarTodas() {
        return sucursalRepository.findAll();
    }

    @Transactional
    public Sucursal insertar(Sucursal sucursal) {
        // 1. HIDRATAR: Buscamos la empresa completa para que no devuelva campos null
        if (sucursal.getEmpresa() != null && sucursal.getEmpresa().getIdEmpresa() != null) {
            Empresa empresaCompleta = empresaRepository.findById(sucursal.getEmpresa().getIdEmpresa())
                .orElseThrow(() -> new RuntimeException("La empresa especificada no existe"));
            sucursal.setEmpresa(empresaCompleta);
        }

        // 2. Valores por defecto
        if (sucursal.getFechaCreacion() == null) {
            sucursal.setFechaCreacion(LocalDateTime.now());
        }
        
        if (sucursal.getEstado() == null || sucursal.getEstado().isBlank()) {
            sucursal.setEstado("ACTIVO");
        }

        // 3. Guardar (Ahora la respuesta incluirá nombre y RUC de la empresa)
        return sucursalRepository.save(sucursal);
    }

    public Sucursal obtenerPorId(Integer id) {
        return sucursalRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("La sucursal con ID " + id + " no existe"));
    }

    @Transactional
    public Sucursal actualizar(Integer id, Sucursal sucursalDetalles) {
        // Buscamos el objeto original
        Sucursal original = obtenerPorId(id); 
        
        // Actualizamos campos básicos
        original.setNombre(sucursalDetalles.getNombre());
        original.setDescripcion(sucursalDetalles.getDescripcion());
        original.setDireccion(sucursalDetalles.getDireccion());
        original.setTelefono(sucursalDetalles.getTelefono());
        original.setCorreo(sucursalDetalles.getCorreo());
        
        // Manejo del estado
        if (sucursalDetalles.getEstado() != null && !sucursalDetalles.getEstado().isBlank()) {
            original.setEstado(sucursalDetalles.getEstado());
        }

        // Si el Front intenta cambiar la empresa, la actualizamos también
        if (sucursalDetalles.getEmpresa() != null && sucursalDetalles.getEmpresa().getIdEmpresa() != null) {
            Empresa nuevaEmpresa = empresaRepository.findById(sucursalDetalles.getEmpresa().getIdEmpresa())
                .orElseThrow(() -> new RuntimeException("La nueva empresa no existe"));
            original.setEmpresa(nuevaEmpresa);
        }
        
        return sucursalRepository.save(original);
    }

    @Transactional
    public Sucursal cambiarEstado(Integer id) {
        Sucursal sucursal = obtenerPorId(id);

        if ("ACTIVO".equalsIgnoreCase(sucursal.getEstado())) {
            sucursal.setEstado("INACTIVO");
        } else {
            sucursal.setEstado("ACTIVO");
        }

        return sucursalRepository.save(sucursal);
    }

    public void eliminar(Integer id) {
        if (!sucursalRepository.existsById(id)) {
            throw new RuntimeException("La sucursal " + id + " no existe");
        }
        sucursalRepository.deleteById(id);
    }
}