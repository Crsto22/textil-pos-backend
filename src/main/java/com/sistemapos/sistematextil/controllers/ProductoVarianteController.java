package com.sistemapos.sistematextil.controllers;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sistemapos.sistematextil.model.ProductoVariante;
import com.sistemapos.sistematextil.services.ProductoVarianteService;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

@RestController
@RequestMapping("api/variante")
@AllArgsConstructor
public class ProductoVarianteController {

    private final ProductoVarianteService service;

    @GetMapping("listar")
    public ResponseEntity<List<ProductoVariante>> listar() {
        return ResponseEntity.ok(service.listarTodas());
    }

    @GetMapping("producto/{id}")
    public ResponseEntity<List<ProductoVariante>> listarPorProducto(@PathVariable Integer id) {
        return ResponseEntity.ok(service.listarPorProducto(id));
    }

    @PostMapping("insertar")
    public ResponseEntity<ProductoVariante> crear(@Valid @RequestBody ProductoVariante variante) {
        return new ResponseEntity<>(service.insertar(variante), HttpStatus.CREATED);
    }

    @PatchMapping("stock/{id}")
    public ResponseEntity<ProductoVariante> actualizarStock(@PathVariable Integer id, @RequestBody Integer stock) {
        return ResponseEntity.ok(service.actualizarStock(id, stock));
    }
    @DeleteMapping("eliminar/{id}")
public ResponseEntity<String> eliminar(@PathVariable Integer id) {
    service.eliminar(id);
    return ResponseEntity.ok("Variante eliminada correctamente");
}

@PatchMapping("precio/{id}")
public ResponseEntity<ProductoVariante> actualizarPrecio(@PathVariable Integer id, @RequestBody Double precio) {
    return ResponseEntity.ok(service.actualizarPrecio(id, precio));
}

}