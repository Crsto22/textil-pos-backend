package com.sistemapos.sistematextil.controllers;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sistemapos.sistematextil.model.Color;
import com.sistemapos.sistematextil.services.ColorService;

import lombok.AllArgsConstructor;

@RestController
@RequestMapping("api/color")
@AllArgsConstructor
public class ColorController {
    private final ColorService colorService;

    @GetMapping("listar")
    public ResponseEntity<List<Color>> listar() {
        return ResponseEntity.ok(colorService.listarTodos());
    }

    @PostMapping("insertar")
    public ResponseEntity<Color> crear(@RequestBody Color color) {
        return ResponseEntity.ok(colorService.insertar(color));
    }

    // NUEVO: Método para actualizar (Corregir nombres)
    @PutMapping("actualizar/{id}")
    public ResponseEntity<Color> actualizar(@PathVariable Integer id, @RequestBody Color color) {
        return ResponseEntity.ok(colorService.actualizar(id, color));
    }

    // NUEVO: Método para eliminar
    @DeleteMapping("eliminar/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Integer id) {
        colorService.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    
}