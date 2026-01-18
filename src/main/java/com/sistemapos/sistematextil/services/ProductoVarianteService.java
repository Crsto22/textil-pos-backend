package com.sistemapos.sistematextil.services;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional; // Necesario para la persistencia

import com.sistemapos.sistematextil.model.Color;
import com.sistemapos.sistematextil.model.Producto;
import com.sistemapos.sistematextil.model.ProductoVariante;
import com.sistemapos.sistematextil.model.Talla;
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
    // 1. Validar existencia y ESTADO (ACTIVO/INACTIVO)
    // Obtenemos los objetos completos para revisar su campo "estado"
    Producto producto = productoService.obtenerPorId(variante.getProducto().getIdProducto());
    Talla talla = tallaService.obtenerPorId(variante.getTalla().getIdTalla());
    Color color = colorService.obtenerPorId(variante.getColor().getIdColor());
    
    // --- NUEVAS VALIDACIONES DE ESTADO ---
    if (!"ACTIVO".equals(talla.getEstado())) {
        throw new RuntimeException("No se puede usar la talla '" + talla.getNombre() + "' porque está INACTIVA");
    }
    if (!"ACTIVO".equals(color.getEstado())) {
        throw new RuntimeException("No se puede usar el color '" + color.getNombre() + "' porque está INACTIVO");
    }
    // --------------------------------------

    // 2. Validar duplicados (Más eficiente que usar findAll().stream())
    // Es mejor crear un método en el Repository: existeCombinacion(...)
    boolean existe = repository.existsByProductoIdProductoAndTallaIdTallaAndColorIdColorAndSucursalIdSucursal(
        variante.getProducto().getIdProducto(),
        variante.getTalla().getIdTalla(),
        variante.getColor().getIdColor(),
        variante.getSucursal().getIdSucursal()
    );

    if (existe) {
        throw new RuntimeException("Ya existe esta variante (Talla/Color) en esta sucursal");
    }

    // 3. Guardar y sincronizar
    ProductoVariante guardado = repository.saveAndFlush(variante);
    entityManager.clear(); 

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