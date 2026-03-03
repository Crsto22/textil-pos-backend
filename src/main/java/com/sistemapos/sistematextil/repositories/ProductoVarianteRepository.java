package com.sistemapos.sistematextil.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sistemapos.sistematextil.model.ProductoVariante;
import com.sistemapos.sistematextil.util.producto.ProductoVarianteResumenRow;

public interface ProductoVarianteRepository extends JpaRepository<ProductoVariante, Integer> {
    List<ProductoVariante> findByProductoIdProducto(Integer idProducto);

    Optional<ProductoVariante> findByIdProductoVariante(Integer idProductoVariante);

    Optional<ProductoVariante> findByIdProductoVarianteAndSucursal_IdSucursal(
            Integer idProductoVariante,
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

    // Metodo optimizado para verificar duplicados sin traer toda la lista a memoria
    boolean existsByProductoIdProductoAndTallaIdTallaAndColorIdColorAndSucursalIdSucursal(
            Integer idProducto, Integer idTalla, Integer idColor, Integer idSucursal);

    boolean existsBySucursalIdSucursalAndSku(Integer idSucursal, String sku);

    boolean existsByCodigoExterno(String codigoExterno);

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
            WHERE v.codigoExterno = :codigoExterno
              AND (:idProductoExcluir IS NULL OR v.producto.idProducto <> :idProductoExcluir)
            """)
    boolean existsCodigoExternoParaOtroProducto(
            @Param("codigoExterno") String codigoExterno,
            @Param("idProductoExcluir") Integer idProductoExcluir);

    @Query("""
            SELECT new com.sistemapos.sistematextil.util.producto.ProductoVarianteResumenRow(
                v.producto.idProducto,
                v.idProductoVariante,
                v.sku,
                v.codigoExterno,
                v.color.idColor,
                v.color.nombre,
                v.color.codigo,
                v.talla.idTalla,
                v.talla.nombre,
                v.precio,
                v.stock,
                v.estado
            )
            FROM ProductoVariante v
            WHERE v.producto.idProducto IN :productoIds
            """)
    List<ProductoVarianteResumenRow> obtenerResumenPorProductos(@Param("productoIds") List<Integer> productoIds);
}
