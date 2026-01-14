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

import com.sistemapos.sistematextil.model.Talla;
import com.sistemapos.sistematextil.services.TallaService;

import lombok.AllArgsConstructor;

@RestController
@RequestMapping("api/talla")
@AllArgsConstructor
public class TallaController {
    private final TallaService tallaService;

    @GetMapping("listar")
    public ResponseEntity<List<Talla>> listar() {
        return ResponseEntity.ok(tallaService.listarTodas());
    }

    @PostMapping("insertar")
    public ResponseEntity<Talla> crear(@RequestBody Talla talla) {
        return ResponseEntity.ok(tallaService.insertar(talla));
    }

    // NUEVO: Método para actualizar (Corregir nombres)
    @PutMapping("actualizar/{id}")
    public ResponseEntity<Talla> actualizar(@PathVariable Integer id, @RequestBody Talla talla) {
        return ResponseEntity.ok(tallaService.actualizar(id, talla));
    }

    // NUEVO: Método para eliminar
    @DeleteMapping("eliminar/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Integer id) {
        tallaService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}