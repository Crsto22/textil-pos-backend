package com.sistemapos.sistematextil.services;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional; // Necesario para la persistencia

import com.sistemapos.sistematextil.model.ProductoVariante;
import com.sistemapos.sistematextil.repositories.ProductoVarianteRepository;

import jakarta.persistence.EntityManager; // Para limpiar caché
import jakarta.persistence.PersistenceContext; // Para inyectar EntityManager
import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class ProductoVarianteService {

    private final ProductoVarianteRepository repository;
    private final ProductoService productoService;
    private final TallaService tallaService;
    private final ColorService colorService;

    @PersistenceContext
    private final EntityManager entityManager;

    public List<ProductoVariante> listarTodas() {
        return repository.findAll();
    }

    public List<ProductoVariante> listarPorProducto(Integer idProducto) {
        return repository.findByProductoIdProducto(idProducto);
    }

    @Transactional
    public ProductoVariante insertar(ProductoVariante variante) {
        // 1. Validar que el Producto, Talla, Color y Sucursal existan realmente
        productoService.obtenerPorId(variante.getProducto().getIdProducto());
        tallaService.obtenerPorId(variante.getTalla().getIdTalla());
        colorService.obtenerPorId(variante.getColor().getIdColor());

        // 2. Validar duplicados (Combinación Producto-Talla-Color-Sucursal)
        boolean existe = repository.findAll().stream().anyMatch(v -> 
            v.getProducto().getIdProducto().equals(variante.getProducto().getIdProducto()) &&
            v.getTalla().getIdTalla().equals(variante.getTalla().getIdTalla()) &&
            v.getColor().getIdColor().equals(variante.getColor().getIdColor()) &&
            v.getSucursal().getIdSucursal().equals(variante.getSucursal().getIdSucursal())
        );

        if (existe) {
            throw new RuntimeException("Ya existe esta variante (Talla/Color) en esta sucursal");
        }

        // 3. Guardar y sincronizar con la DB
        // Usamos saveAndFlush para forzar la escritura inmediata
        ProductoVariante guardado = repository.saveAndFlush(variante);

        // 4. LIMPIAR LA SESIÓN (Crucial para quitar los nulls)
        // Esto elimina el objeto "incompleto" de la memoria de Hibernate
        entityManager.clear(); 

        // 5. RECARGAR EL OBJETO COMPLETO
        // Al buscarlo de nuevo tras el clear, Hibernate hace los JOINs y trae los nombres
        return repository.findById(guardado.getIdProductoVariante())
                .orElseThrow(() -> new RuntimeException("Error al recuperar la variante hidratada"));
    }

    public ProductoVariante actualizarStock(Integer id, Integer nuevoStock) {
        ProductoVariante v = repository.findById(id)
            .orElseThrow(() -> new RuntimeException("Variante no encontrada"));
        
        v.setStock(nuevoStock);
        return repository.save(v);
    }

    public ProductoVariante actualizarPrecio(Integer id, Double nuevoPrecio) {
        ProductoVariante v = repository.findById(id)
            .orElseThrow(() -> new RuntimeException("Variante no encontrada"));
        
        v.setPrecio(nuevoPrecio);
        return repository.save(v);
    }

    public void eliminar(Integer id) {
        if (!repository.existsById(id)) {
            throw new RuntimeException("No se puede eliminar: La variante no existe");
        }
        repository.deleteById(id);
    }
}