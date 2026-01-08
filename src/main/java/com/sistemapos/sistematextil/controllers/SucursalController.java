package com.sistemapos.sistematextil.controllers;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sistemapos.sistematextil.model.Sucursal;
import com.sistemapos.sistematextil.services.SucursalService;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

@RestController
@RequestMapping(value = "api/sucursal", produces = MediaType.APPLICATION_JSON_VALUE)
@AllArgsConstructor
public class SucursalController {

    private final SucursalService sucursalService;

    @GetMapping("listar")
    public ResponseEntity<List<Sucursal>> listar() {
        return ResponseEntity.ok(sucursalService.listarTodas());
    }

    @PostMapping("insertar")
    public ResponseEntity<Sucursal> crear(@Valid @RequestBody Sucursal sucursal) {
        return new ResponseEntity<>(sucursalService.insertar(sucursal), HttpStatus.CREATED);
    }

    @GetMapping("buscar/{id}")
    public ResponseEntity<Sucursal> obtener(@PathVariable Integer id) {
        try {
            return ResponseEntity.ok(sucursalService.obtenerPorId(id));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
    }

    @PutMapping("actualizar/{id}")
    public ResponseEntity<Sucursal> actualizar(@PathVariable Integer id, @Valid @RequestBody Sucursal sucursal) {
        try {
            return ResponseEntity.ok(sucursalService.actualizar(id, sucursal));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
    }

    @DeleteMapping("eliminar/{id}")
    public ResponseEntity<String> eliminar(@PathVariable Integer id) {
        try {
            sucursalService.eliminar(id);
            return ResponseEntity.ok("Sucursal eliminada correctamente");
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    @PutMapping("estado/{id}")
public ResponseEntity<Sucursal> cambiarEstado(@PathVariable Integer id) {
    try {
        Sucursal sucursalActualizada = sucursalService.cambiarEstado(id);
        return ResponseEntity.ok(sucursalActualizada);
    } catch (RuntimeException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
    }
}




}