package com.sistemapos.sistematextil.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sistemapos.sistematextil.model.ProductoVariante;
import com.sistemapos.sistematextil.util.producto.ProductoVarianteResumenRow;

public interface ProductoVarianteRepository extends JpaRepository<ProductoVariante, Integer> {
    List<ProductoVariante> findByDeletedAtIsNull();

    List<ProductoVariante> findByProductoIdProducto(Integer idProducto);

    List<ProductoVariante> findByProductoIdProductoAndDeletedAtIsNull(Integer idProducto);

    Optional<ProductoVariante> findByIdProductoVariante(Integer idProductoVariante);

    Optional<ProductoVariante> findByIdProductoVarianteAndDeletedAtIsNull(Integer idProductoVariante);

    Optional<ProductoVariante> findByIdProductoVarianteAndSucursal_IdSucursal(
            Integer idProductoVariante,
            Integer idSucursal);

    Optional<ProductoVariante> findByProductoIdProductoAndTallaIdTallaAndColorIdColorAndSucursalIdSucursal(
            Integer idProducto,
            Integer idTalla,
            Integer idColor,
            Integer idSucursal);

    @Query(
            value = """
                    SELECT *
                    FROM producto_variante
                    WHERE id_producto_variante = :idProductoVariante
                    FOR UPDATE
                    """,
            nativeQuery = true)
    Optional<ProductoVariante> findByIdProductoVarianteForUpdate(
            @Param("idProductoVariante") Integer idProductoVariante);

    void deleteByProductoIdProducto(Integer idProducto);

    Optional<ProductoVariante> findFirstByProductoIdProductoOrderByIdProductoVarianteAsc(Integer idProducto);

    Optional<ProductoVariante> findFirstByProductoIdProductoAndDeletedAtIsNullOrderByIdProductoVarianteAsc(Integer idProducto);

    // Metodo optimizado para verificar duplicados sin traer toda la lista a memoria
    boolean existsByProductoIdProductoAndTallaIdTallaAndColorIdColorAndSucursalIdSucursal(
            Integer idProducto, Integer idTalla, Integer idColor, Integer idSucursal);

    boolean existsBySucursalIdSucursalAndSku(Integer idSucursal, String sku);

    boolean existsBySucursalIdSucursalAndSkuAndIdProductoVarianteNot(
            Integer idSucursal,
            String sku,
            Integer idProductoVariante);

    @Query("""
            SELECT COUNT(v) > 0
            FROM ProductoVariante v
            WHERE v.sucursal.idSucursal = :idSucursal
              AND v.sku = :sku
              AND (:idProductoExcluir IS NULL OR v.producto.idProducto <> :idProductoExcluir)
            """)
    boolean existsSkuEnSucursalParaOtroProducto(
            @Param("idSucursal") Integer idSucursal,
            @Param("sku") String sku,
            @Param("idProductoExcluir") Integer idProductoExcluir);

    @Query("""
            SELECT COUNT(v) > 0
            FROM ProductoVariante v
            WHERE v.producto.idProducto = :idProducto
              AND v.color.idColor = :idColor
              AND v.activo = true
              AND v.deletedAt IS NULL
            """)
    boolean existsVarianteActivaPorProductoYColor(
            @Param("idProducto") Integer idProducto,
            @Param("idColor") Integer idColor);

    @Query("""
            SELECT new com.sistemapos.sistematextil.util.producto.ProductoVarianteResumenRow(
                v.producto.idProducto,
                v.idProductoVariante,
                v.sku,
                v.color.idColor,
                v.color.nombre,
                v.color.codigo,
                v.talla.idTalla,
                v.talla.nombre,
                v.precio,
                v.precioOferta,
                v.stock,
                v.estado
            )
            FROM ProductoVariante v
            WHERE v.producto.idProducto IN :productoIds
              AND v.activo = true
              AND v.deletedAt IS NULL
            """)
    List<ProductoVarianteResumenRow> obtenerResumenPorProductos(@Param("productoIds") List<Integer> productoIds);
}
