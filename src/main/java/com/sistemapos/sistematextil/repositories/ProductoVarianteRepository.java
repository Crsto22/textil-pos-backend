package com.sistemapos.sistematextil.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sistemapos.sistematextil.model.ProductoVariante;
import com.sistemapos.sistematextil.util.producto.ProductoVarianteResumenRow;

public interface ProductoVarianteRepository extends JpaRepository<ProductoVariante, Integer> {
    List<ProductoVariante> findByProductoIdProducto(Integer idProducto);

    // Método optimizado para verificar duplicados sin traer toda la lista a memoria
    boolean existsByProductoIdProductoAndTallaIdTallaAndColorIdColorAndSucursalIdSucursal(
        Integer idProducto, Integer idTalla, Integer idColor, Integer idSucursal
    );

    @Query("""
            SELECT new com.sistemapos.sistematextil.util.producto.ProductoVarianteResumenRow(
                v.producto.idProducto,
                v.color.idColor,
                v.talla.idTalla,
                v.talla.nombre,
                v.precio
            )
            FROM ProductoVariante v
            WHERE v.producto.idProducto IN :productoIds
            """)
    List<ProductoVarianteResumenRow> obtenerResumenPorProductos(@Param("productoIds") List<Integer> productoIds);
}
