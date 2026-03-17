package com.sistemapos.sistematextil.repositories;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    Optional<ProductoVariante> findByIdProductoVarianteAndDeletedAtIsNullAndSucursal_IdSucursal(
            Integer idProductoVariante,
            Integer idSucursal);

    List<ProductoVariante> findByIdProductoVarianteInAndDeletedAtIsNull(List<Integer> idsProductoVariante);

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

    List<ProductoVariante> findByPrecioOfertaIsNotNullAndOfertaFinLessThanEqualAndDeletedAtIsNull(LocalDateTime fechaHora);

    Page<ProductoVariante> findByPrecioOfertaIsNotNullAndDeletedAtIsNull(Pageable pageable);

    // Metodo optimizado para verificar duplicados sin traer toda la lista a memoria
    boolean existsByProductoIdProductoAndTallaIdTallaAndColorIdColorAndSucursalIdSucursal(
            Integer idProducto, Integer idTalla, Integer idColor, Integer idSucursal);

    boolean existsBySucursalIdSucursalAndSku(Integer idSucursal, String sku);

    boolean existsBySucursalIdSucursalAndSkuAndIdProductoVarianteNot(
            Integer idSucursal,
            String sku,
            Integer idProductoVariante);

    boolean existsByProductoIdProductoAndTallaIdTallaAndColorIdColorAndSucursalIdSucursalAndIdProductoVarianteNot(
            Integer idProducto,
            Integer idTalla,
            Integer idColor,
            Integer idSucursal,
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
                v.precioMayor,
                v.precioOferta,
                v.ofertaInicio,
                v.ofertaFin,
                v.stock,
                v.estado
            )
            FROM ProductoVariante v
            WHERE v.producto.idProducto IN :productoIds
              AND v.activo = true
              AND v.deletedAt IS NULL
            """)
    List<ProductoVarianteResumenRow> obtenerResumenPorProductos(@Param("productoIds") List<Integer> productoIds);

    @Query(
            value = """
                    SELECT v
                    FROM ProductoVariante v
                    JOIN FETCH v.producto p
                    LEFT JOIN FETCH p.categoria
                    LEFT JOIN FETCH p.sucursal
                    LEFT JOIN FETCH v.color
                    LEFT JOIN FETCH v.talla
                    WHERE v.deletedAt IS NULL
                      AND v.activo = true
                      AND p.estado <> :estadoProductoExcluido
                      AND (:idSucursal IS NULL OR p.sucursal.idSucursal = :idSucursal)
                      AND (:idCategoria IS NULL OR p.categoria.idCategoria = :idCategoria)
                      AND (:idColor IS NULL OR v.color.idColor = :idColor)
                      AND (
                            :term IS NULL
                            OR LOWER(p.nombre) LIKE LOWER(CONCAT('%', :term, '%'))
                            OR v.sku LIKE CONCAT(:term, '%')
                      )
                      AND (
                            :conOferta IS NULL
                            OR (:conOferta = true AND v.precioOferta IS NOT NULL AND (v.ofertaInicio IS NULL OR v.ofertaInicio <= CURRENT_TIMESTAMP) AND (v.ofertaFin IS NULL OR v.ofertaFin >= CURRENT_TIMESTAMP))
                      )
                    """,
            countQuery = """
                    SELECT COUNT(v.idProductoVariante)
                    FROM ProductoVariante v
                    JOIN v.producto p
                    WHERE v.deletedAt IS NULL
                      AND v.activo = true
                      AND p.estado <> :estadoProductoExcluido
                      AND (:idSucursal IS NULL OR p.sucursal.idSucursal = :idSucursal)
                      AND (:idCategoria IS NULL OR p.categoria.idCategoria = :idCategoria)
                      AND (:idColor IS NULL OR v.color.idColor = :idColor)
                      AND (
                            :term IS NULL
                            OR LOWER(p.nombre) LIKE LOWER(CONCAT('%', :term, '%'))
                            OR v.sku LIKE CONCAT(:term, '%')
                      )
                      AND (
                            :conOferta IS NULL
                            OR (:conOferta = true AND v.precioOferta IS NOT NULL AND (v.ofertaInicio IS NULL OR v.ofertaInicio <= CURRENT_TIMESTAMP) AND (v.ofertaFin IS NULL OR v.ofertaFin >= CURRENT_TIMESTAMP))
                      )
                    """)
    Page<ProductoVariante> buscarResumenPaginado(
            @Param("term") String term,
            @Param("idSucursal") Integer idSucursal,
            @Param("idCategoria") Integer idCategoria,
            @Param("idColor") Integer idColor,
            @Param("conOferta") Boolean conOferta,
            @Param("estadoProductoExcluido") String estadoProductoExcluido,
            Pageable pageable);
}
