package com.sistemapos.sistematextil.controllers;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sistemapos.sistematextil.model.HistorialStock;
import com.sistemapos.sistematextil.services.HistorialStockService;

import lombok.AllArgsConstructor;

@RestController
@RequestMapping("api/historial-stock")
@AllArgsConstructor
public class HistorialStockController {

    private final HistorialStockService service;

    @GetMapping("listar")
    public ResponseEntity<List<HistorialStock>> listarTodo() {
        return ResponseEntity.ok(service.listarTodo());
    }

    @GetMapping("producto/{id}")
    public ResponseEntity<List<HistorialStock>> porProducto(@PathVariable Integer id) {
        return ResponseEntity.ok(service.listarPorProducto(id));
    }
}