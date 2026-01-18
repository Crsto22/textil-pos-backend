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

    // Listar absolutamente todas (Para el administrador)
    @GetMapping("listar")
    public ResponseEntity<List<Talla>> listar() {
        return ResponseEntity.ok(tallaService.listarTodas());
    }

    // NUEVO: Listar solo las que están en ACTIVO (Para formularios de productos)
    @GetMapping("activas")
    public ResponseEntity<List<Talla>> listarActivas() {
        return ResponseEntity.ok(tallaService.listarActivas());
    }

    @PostMapping("insertar")
    public ResponseEntity<Talla> crear(@RequestBody Talla talla) {
        return ResponseEntity.ok(tallaService.insertar(talla));
    }

    @PutMapping("actualizar/{id}")
    public ResponseEntity<Talla> actualizar(@PathVariable Integer id, @RequestBody Talla talla) {
        return ResponseEntity.ok(tallaService.actualizar(id, talla));
    }

    // NUEVO: Cambiar estado (Desactivar/Activar sin borrar)
    @PutMapping("estado/{id}")
    public ResponseEntity<Talla> cambiarEstado(@PathVariable Integer id) {
        return ResponseEntity.ok(tallaService.cambiarEstado(id));
    }

    // Eliminar físicamente (Solo si no tiene productos asociados)
    @DeleteMapping("eliminar/{id}")
    public ResponseEntity<String> eliminar(@PathVariable Integer id) {
        try {
            tallaService.eliminar(id);
            return ResponseEntity.ok("Talla eliminada correctamente");
        } catch (RuntimeException e) {
            // Aquí atrapamos el error si la base de datos prohíbe el borrado
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}