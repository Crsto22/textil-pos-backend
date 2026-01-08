package com.sistemapos.sistematextil.services;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.sistemapos.sistematextil.model.Sucursal;
import com.sistemapos.sistematextil.repositories.SucursalRepository;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class SucursalService {

    private final SucursalRepository sucursalRepository;

    public List<Sucursal> listarTodas() {
        return sucursalRepository.findAll();
    }

    public Sucursal insertar(Sucursal sucursal) {
        if (sucursal.getFechaCreacion() == null) {
            sucursal.setFechaCreacion(LocalDateTime.now());
        }
        // toda sucursal nueva entrara como ACTIVO
        if (sucursal.getEstado() == null) {
            sucursal.setEstado("ACTIVO");
        }
        return sucursalRepository.save(sucursal);
    }

    public Sucursal obtenerPorId(Integer id) {
        return sucursalRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("La sucursal con ID " + id + " no existe"));
    }

    public Sucursal actualizar(Integer id, Sucursal sucursal) {
        Sucursal original = obtenerPorId(id); // Reutilizamos el método que lanza la excepción
        
        sucursal.setIdSucursal(id);
        sucursal.setFechaCreacion(original.getFechaCreacion());
        // Mantenemos el estado original a menos que el objeto traiga uno nuevo
        if (sucursal.getEstado() == null) {
            sucursal.setEstado(original.getEstado());
        }
        
        return sucursalRepository.save(sucursal);
    }

    public Sucursal cambiarEstado(Integer id) {
        Sucursal sucursal = sucursalRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("La sucursal con ID " + id + " no existe"));

        // Lógica de alternancia (Toggle)
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