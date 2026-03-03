package com.sistemapos.sistematextil.services;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sistemapos.sistematextil.model.Color;
import com.sistemapos.sistematextil.model.Producto;
import com.sistemapos.sistematextil.model.ProductoVariante;
import com.sistemapos.sistematextil.model.Talla;
import com.sistemapos.sistematextil.repositories.ProductoVarianteRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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
        if (variante.getProducto() == null || variante.getProducto().getIdProducto() == null) {
            throw new RuntimeException("Ingrese producto.idProducto");
        }
        if (variante.getSucursal() == null || variante.getSucursal().getIdSucursal() == null) {
            throw new RuntimeException("Ingrese sucursal.idSucursal");
        }
        if (variante.getTalla() == null || variante.getTalla().getIdTalla() == null) {
            throw new RuntimeException("Ingrese talla.idTalla");
        }
        if (variante.getColor() == null || variante.getColor().getIdColor() == null) {
            throw new RuntimeException("Ingrese color.idColor");
        }

        Producto producto = productoService.obtenerPorId(variante.getProducto().getIdProducto());
        Talla talla = tallaService.obtenerPorId(variante.getTalla().getIdTalla());
        Color color = colorService.obtenerPorId(variante.getColor().getIdColor());

        if (!"ACTIVO".equalsIgnoreCase(talla.getEstado())) {
            throw new RuntimeException("No se puede usar la talla '" + talla.getNombre() + "' porque esta INACTIVA");
        }
        if (!"ACTIVO".equalsIgnoreCase(color.getEstado())) {
            throw new RuntimeException("No se puede usar el color '" + color.getNombre() + "' porque esta INACTIVO");
        }

        String sku = normalizarRequerido(variante.getSku(), "El SKU de la variante es obligatorio");
        String codigoExterno = normalizar(variante.getCodigoExterno());

        boolean existeCombinacion = repository.existsByProductoIdProductoAndTallaIdTallaAndColorIdColorAndSucursalIdSucursal(
                variante.getProducto().getIdProducto(),
                variante.getTalla().getIdTalla(),
                variante.getColor().getIdColor(),
                variante.getSucursal().getIdSucursal());
        if (existeCombinacion) {
            throw new RuntimeException("Ya existe esta variante (talla/color) en esta sucursal");
        }

        if (repository.existsBySucursalIdSucursalAndSku(variante.getSucursal().getIdSucursal(), sku)) {
            throw new RuntimeException("El SKU '" + sku + "' ya existe en esta sucursal");
        }
        if (codigoExterno != null && repository.existsByCodigoExterno(codigoExterno)) {
            throw new RuntimeException("El codigo externo '" + codigoExterno + "' ya pertenece a otra variante");
        }

        variante.setProducto(producto);
        variante.setTalla(talla);
        variante.setColor(color);
        variante.setSku(sku);
        variante.setCodigoExterno(codigoExterno);

        ProductoVariante guardado = repository.saveAndFlush(variante);
        entityManager.clear();

        return repository.findById(guardado.getIdProductoVariante())
                .orElseThrow(() -> new RuntimeException("Error al recuperar la variante guardada"));
    }

    public ProductoVariante actualizarStock(Integer id, Integer nuevoStock) {
        ProductoVariante variante = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Variante no encontrada"));

        variante.setStock(nuevoStock);
        return repository.save(variante);
    }

    public ProductoVariante actualizarPrecio(Integer id, Double nuevoPrecio) {
        ProductoVariante variante = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Variante no encontrada"));

        variante.setPrecio(nuevoPrecio);
        return repository.save(variante);
    }

    public void eliminar(Integer id) {
        if (!repository.existsById(id)) {
            throw new RuntimeException("Variante con ID " + id + " no encontrada");
        }
        repository.deleteById(id);
    }

    private String normalizarRequerido(String value, String message) {
        String normalizado = normalizar(value);
        if (normalizado == null) {
            throw new RuntimeException(message);
        }
        return normalizado;
    }

    private String normalizar(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
