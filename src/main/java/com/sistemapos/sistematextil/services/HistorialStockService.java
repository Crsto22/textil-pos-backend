package com.sistemapos.sistematextil.services;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sistemapos.sistematextil.model.HistorialStock;
import com.sistemapos.sistematextil.repositories.HistorialStockRepository;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class HistorialStockService {

    private final HistorialStockRepository repository;

    public List<HistorialStock> listarTodo() {
        return repository.findAll();
    }

    public List<HistorialStock> listarPorProducto(Integer idProducto) {
        return repository.findByProductoIdProductoOrderByFechaDesc(idProducto);
    }

    @Transactional
    public void registrarMovimiento(HistorialStock historial) {
        repository.save(historial);
    }
}