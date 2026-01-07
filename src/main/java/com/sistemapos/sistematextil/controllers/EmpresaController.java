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

import com.sistemapos.sistematextil.model.Empresa;
import com.sistemapos.sistematextil.services.EmpresaService;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

@RestController
@RequestMapping(value = "api/empresa", produces = MediaType.APPLICATION_JSON_VALUE)
@AllArgsConstructor
public class EmpresaController {

    private final EmpresaService empresaService;

    @GetMapping("listar")
    public ResponseEntity<List<Empresa>> listarEmpresa() {
        List<Empresa> empresas = empresaService.listarTodas();
        return ResponseEntity.ok(empresas);
    }

    @PostMapping("insertar")
    public ResponseEntity<Empresa> crearEmpresa(@Valid @RequestBody Empresa empresa) {
        Empresa nuevaEmpresa = empresaService.insertar(empresa);
        return new ResponseEntity<>(nuevaEmpresa, HttpStatus.CREATED);
    }

    @GetMapping("buscar/{id}")
    public ResponseEntity<Empresa> obtener(@PathVariable Integer id) {
        try {
            Empresa empresa = empresaService.obtenerPorId(id);
            return ResponseEntity.ok(empresa);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
    }

    
    @PutMapping("actualizar/{id}")
    public ResponseEntity<Empresa> actualizar(@PathVariable Integer id, @Valid @RequestBody Empresa empresa) {
        try {
            Empresa empresaActualizada = empresaService.actualizar(id, empresa);
            return ResponseEntity.ok(empresaActualizada);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
    }


    @DeleteMapping("eliminar/{id}")
    public ResponseEntity<String> eliminar(@PathVariable Integer id) {
        try {
            empresaService.eliminar(id);
            return ResponseEntity.ok("Empresa eliminada correctamente");
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }



}
