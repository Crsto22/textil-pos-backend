package com.sistemapos.sistematextil.services;

import java.time.LocalDateTime;
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
        return repository.findByDeletedAtIsNull();
    }

    public List<ProductoVariante> listarPorProducto(Integer idProducto) {
        return repository.findByProductoIdProductoAndDeletedAtIsNull(idProducto);
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
        Double precioOferta = normalizarPrecioOferta(variante.getPrecioOferta());
        validarPrecioOferta(variante.getPrecio(), precioOferta);

        ProductoVariante existente = repository
                .findByProductoIdProductoAndTallaIdTallaAndColorIdColorAndSucursalIdSucursal(
                        variante.getProducto().getIdProducto(),
                        variante.getTalla().getIdTalla(),
                        variante.getColor().getIdColor(),
                        variante.getSucursal().getIdSucursal())
                .orElse(null);

        boolean skuDuplicado = existente == null
                ? repository.existsBySucursalIdSucursalAndSku(variante.getSucursal().getIdSucursal(), sku)
                : repository.existsBySucursalIdSucursalAndSkuAndIdProductoVarianteNot(
                        variante.getSucursal().getIdSucursal(),
                        sku,
                        existente.getIdProductoVariante());
        if (skuDuplicado) {
            throw new RuntimeException("El SKU '" + sku + "' ya existe en esta sucursal");
        }

        ProductoVariante destino;
        if (existente != null) {
            if ("ACTIVO".equalsIgnoreCase(existente.getActivo()) && existente.getDeletedAt() == null) {
                throw new RuntimeException("Ya existe esta variante (talla/color) en esta sucursal");
            }
            destino = existente;
        } else {
            destino = variante;
        }

        destino.setProducto(producto);
        destino.setTalla(talla);
        destino.setColor(color);
        destino.setSku(sku);
        destino.setPrecio(variante.getPrecio());
        destino.setPrecioOferta(precioOferta);
        destino.setStock(variante.getStock());
        destino.setEstado("ACTIVO");
        destino.setActivo("ACTIVO");
        destino.setDeletedAt(null);

        ProductoVariante guardado = repository.saveAndFlush(destino);
        entityManager.clear();

        return repository.findById(guardado.getIdProductoVariante())
                .orElseThrow(() -> new RuntimeException("Error al recuperar la variante guardada"));
    }

    public ProductoVariante actualizarStock(Integer id, Integer nuevoStock) {
        ProductoVariante variante = repository.findByIdProductoVarianteAndDeletedAtIsNull(id)
                .orElseThrow(() -> new RuntimeException("Variante no encontrada"));

        variante.setStock(nuevoStock);
        return repository.save(variante);
    }

    public ProductoVariante actualizarPrecio(Integer id, Double nuevoPrecio) {
        ProductoVariante variante = repository.findByIdProductoVarianteAndDeletedAtIsNull(id)
                .orElseThrow(() -> new RuntimeException("Variante no encontrada"));

        if (nuevoPrecio == null || nuevoPrecio < 0) {
            throw new RuntimeException("El precio no puede ser negativo");
        }
        validarPrecioOferta(nuevoPrecio, variante.getPrecioOferta());
        variante.setPrecio(nuevoPrecio);
        return repository.save(variante);
    }

    public void eliminar(Integer id) {
        ProductoVariante variante = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Variante con ID " + id + " no encontrada"));

        if (!"ACTIVO".equalsIgnoreCase(variante.getActivo()) || variante.getDeletedAt() != null) {
            throw new RuntimeException("Variante con ID " + id + " ya se encuentra eliminada");
        }

        Integer idProducto = variante.getProducto() != null ? variante.getProducto().getIdProducto() : null;
        Integer idColor = variante.getColor() != null ? variante.getColor().getIdColor() : null;

        variante.setEstado("AGOTADO");
        variante.setActivo("INACTIVO");
        variante.setDeletedAt(LocalDateTime.now());
        repository.save(variante);

        productoService.limpiarImagenesColorSiNoHayVariantesActivas(idProducto, idColor);
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

    private Double normalizarPrecioOferta(Double precioOferta) {
        if (precioOferta == null) {
            return null;
        }
        if (precioOferta <= 0) {
            throw new RuntimeException("El precio de oferta debe ser mayor a 0");
        }
        return precioOferta;
    }

    private void validarPrecioOferta(Double precio, Double precioOferta) {
        if (precioOferta == null) {
            return;
        }
        if (precio == null) {
            throw new RuntimeException("El precio es obligatorio para validar el precio de oferta");
        }
        if (precioOferta >= precio) {
            throw new RuntimeException("El precio de oferta debe ser menor al precio regular");
        }
    }
}
