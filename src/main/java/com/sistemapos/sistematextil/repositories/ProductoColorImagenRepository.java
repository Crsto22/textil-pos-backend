package com.sistemapos.sistematextil.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sistemapos.sistematextil.model.ProductoColorImagen;
import com.sistemapos.sistematextil.util.producto.ProductoImagenColorRow;

public interface ProductoColorImagenRepository extends JpaRepository<ProductoColorImagen, Integer> {
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
