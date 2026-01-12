package com.sistemapos.sistematextil.controllers;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sistemapos.sistematextil.model.Producto;
import com.sistemapos.sistematextil.services.ProductoService;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

@RestController
@RequestMapping("api/producto")
@AllArgsConstructor
public class ProductoController {

    private final ProductoService productoService;

    @GetMapping("listar")
    public ResponseEntity<List<Producto>> listar() {
        return ResponseEntity.ok(productoService.listarTodos());
    }

    @PostMapping("insertar")
    public ResponseEntity<Producto> crear(@Valid @RequestBody Producto producto) {
        return new ResponseEntity<>(productoService.insertar(producto), HttpStatus.CREATED);
    }

    @PutMapping("actualizar/{id}")
    public ResponseEntity<Producto> actualizar(@PathVariable Integer id, @Valid @RequestBody Producto producto) {
        return ResponseEntity.ok(productoService.actualizar(id, producto));
    }

    @PutMapping("estado/{id}")
    public ResponseEntity<Producto> cambiarEstado(@PathVariable Integer id) {
        return ResponseEntity.ok(productoService.cambiarEstado(id));
    }

    @DeleteMapping("eliminar/{id}")
    public ResponseEntity<String> eliminar(@PathVariable Integer id) {
        productoService.eliminar(id);
        return ResponseEntity.ok("Producto eliminado correctamente");
    }
}