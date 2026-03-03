package com.sistemapos.sistematextil.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sistemapos.sistematextil.model.ProductoColorImagen;
import com.sistemapos.sistematextil.util.producto.ProductoImagenColorRow;

public interface ProductoColorImagenRepository extends JpaRepository<ProductoColorImagen, Integer> {

    List<ProductoColorImagen> findByProductoIdProducto(Integer idProducto);

    List<ProductoColorImagen> findByProductoIdProductoAndColorIdColor(Integer idProducto, Integer idColor);

    Optional<ProductoColorImagen> findByIdColorImagen(Integer idColorImagen);

    void deleteByProductoIdProducto(Integer idProducto);

    @Query("""
            SELECT new com.sistemapos.sistematextil.util.producto.ProductoImagenColorRow(
                pci.producto.idProducto,
                c.idColor,
                c.nombre,
                c.codigo,
                pci.url,
                pci.urlThumb,
                pci.orden,
                pci.esPrincipal
            )
            FROM ProductoColorImagen pci
            JOIN pci.color c
            WHERE pci.producto.idProducto IN :productoIds
            """)
    List<ProductoImagenColorRow> obtenerResumenPorProductos(@Param("productoIds") List<Integer> productoIds);
}
