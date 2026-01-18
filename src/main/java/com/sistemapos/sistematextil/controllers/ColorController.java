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

    @GetMapping("activos")
    public ResponseEntity<List<Color>> listarActivos() {
        return ResponseEntity.ok(colorService.listarActivos());
    }

    @PostMapping("insertar")
    public ResponseEntity<Color> crear(@RequestBody Color color) {
        return ResponseEntity.ok(colorService.insertar(color));
    }

    @PutMapping("actualizar/{id}")
    public ResponseEntity<Color> actualizar(@PathVariable Integer id, @RequestBody Color color) {
        return ResponseEntity.ok(colorService.actualizar(id, color));
    }

    @PutMapping("estado/{id}")
    public ResponseEntity<Color> cambiarEstado(@PathVariable Integer id) {
        return ResponseEntity.ok(colorService.cambiarEstado(id));
    }

    @DeleteMapping("eliminar/{id}")
    public ResponseEntity<String> eliminar(@PathVariable Integer id) {
        try {
            colorService.eliminar(id);
            return ResponseEntity.ok("Color eliminado correctamente");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}