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
        return sucursalRepository.save(sucursal);
    }

    public Sucursal obtenerPorId(Integer id) {
        return sucursalRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("La sucursal con ID " + id + " no existe"));
    }

    public Sucursal actualizar(Integer id, Sucursal sucursal) {
        if (!sucursalRepository.existsById(id)) {
            throw new RuntimeException("La sucursal no existe");
        }
        Sucursal original = sucursalRepository.findById(id).get();
        sucursal.setIdSucursal(id);
        sucursal.setFechaCreacion(original.getFechaCreacion());
        return sucursalRepository.save(sucursal);
    }

    public void eliminar(Integer id) {
        if (!sucursalRepository.existsById(id)) {
            throw new RuntimeException("La sucursal con ID " + id + " no existe");
        }
        sucursalRepository.deleteById(id);
    }
}