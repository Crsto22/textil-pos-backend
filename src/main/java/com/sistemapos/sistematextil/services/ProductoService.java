package com.sistemapos.sistematextil.services;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.sistemapos.sistematextil.model.Categoria;
import com.sistemapos.sistematextil.model.Producto;
import com.sistemapos.sistematextil.model.Sucursal;
import com.sistemapos.sistematextil.repositories.CategoriaRepository;
import com.sistemapos.sistematextil.repositories.ProductoRepository;
import com.sistemapos.sistematextil.repositories.SucursalRepository;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class ProductoService {

    private final ProductoRepository productoRepository;
    private final CategoriaRepository categoriaRepository;
    private final SucursalRepository sucursalRepository;

    public List<Producto> listarTodos() {
        return productoRepository.findAll();
    }

    public Producto insertar(Producto producto) {
        Categoria cat = categoriaRepository.findById(producto.getCategoria().getIdCategoria())
                .orElseThrow(() -> new RuntimeException("Categoria no encontrada"));
        if (!"ACTIVO".equalsIgnoreCase(cat.getEstado())) {
            throw new RuntimeException("No se pueden crear productos en una categoria inactiva");
        }

        Sucursal suc = sucursalRepository.findById(producto.getSucursal().getIdSucursal())
                .orElseThrow(() -> new RuntimeException("Sucursal no encontrada"));
        if (!"ACTIVO".equalsIgnoreCase(suc.getEstado())) {
            throw new RuntimeException("No se pueden registrar productos en una sucursal inactiva");
        }

        if (productoRepository.findBySkuAndSucursalIdSucursal(producto.getSku(), suc.getIdSucursal()).isPresent()) {
            throw new RuntimeException("El SKU '" + producto.getSku() + "' ya existe en esta sucursal");
        }

        producto.setCategoria(cat);
        producto.setSucursal(suc);
        producto.setFechaCreacion(LocalDateTime.now());
        producto.setEstado("ACTIVO");

        return productoRepository.save(producto);
    }

    public Producto obtenerPorId(Integer id) {
        return productoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Producto con ID " + id + " no encontrado"));
    }

    public Producto actualizar(Integer id, Producto producto) {
        Producto original = obtenerPorId(id);
        Integer idSucursal = original.getSucursal().getIdSucursal();

        productoRepository.findBySkuAndSucursalIdSucursal(producto.getSku(), idSucursal)
                .ifPresent(p -> {
                    if (!p.getIdProducto().equals(id)) {
                        throw new RuntimeException("El SKU ya pertenece a otro producto en esta sucursal");
                    }
                });

        if (producto.getCategoria() != null) {
            Categoria cat = categoriaRepository.findById(producto.getCategoria().getIdCategoria())
                    .orElseThrow(() -> new RuntimeException("Categoria no encontrada"));
            if (!"ACTIVO".equalsIgnoreCase(cat.getEstado())) {
                throw new RuntimeException("Categoria inactiva");
            }
            original.setCategoria(cat);
        }

        original.setNombre(producto.getNombre());
        original.setDescripcion(producto.getDescripcion());
        original.setSku(producto.getSku());
        original.setCodigoExterno(producto.getCodigoExterno());

        if (producto.getImagen() != null) {
            original.setImagen(producto.getImagen());
        }

        return productoRepository.save(original);
    }

    public Producto cambiarEstado(Integer id) {
        Producto p = obtenerPorId(id);
        p.setEstado("ACTIVO".equalsIgnoreCase(p.getEstado()) ? "ARCHIVADO" : "ACTIVO");
        return productoRepository.save(p);
    }

    public void eliminar(Integer id) {
        if (!productoRepository.existsById(id)) {
            throw new RuntimeException("El producto no existe");
        }
        productoRepository.deleteById(id);
    }
}
