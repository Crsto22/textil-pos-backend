package com.sistemapos.sistematextil.repositories;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sistemapos.sistematextil.model.Producto;

public interface ProductoRepository extends JpaRepository<Producto, Integer> {

    Page<Producto> findByEstadoNotOrderByIdProductoAsc(String estado, Pageable pageable);

    Page<Producto> findBySucursal_IdSucursalAndEstadoNotOrderByIdProductoAsc(
            Integer idSucursal,
            String estado,
            Pageable pageable);

    @Query(
            value = """
                    SELECT DISTINCT p
                    FROM Producto p
                    LEFT JOIN p.sucursal s
                    LEFT JOIN ProductoVariante v ON v.producto = p
                    WHERE p.estado <> :estadoExcluido
                      AND (:idSucursal IS NULL OR s.idSucursal = :idSucursal)
                      AND (
                            :term IS NULL
                            OR LOWER(p.nombre) LIKE LOWER(CONCAT('%', :term, '%'))
                            OR v.sku LIKE CONCAT(:term, '%')
                            OR (v.codigoExterno IS NOT NULL AND v.codigoExterno LIKE CONCAT(:term, '%'))
                      )
                    """,
            countQuery = """
                    SELECT COUNT(DISTINCT p.idProducto)
                    FROM Producto p
                    LEFT JOIN p.sucursal s
                    LEFT JOIN ProductoVariante v ON v.producto = p
                    WHERE p.estado <> :estadoExcluido
                      AND (:idSucursal IS NULL OR s.idSucursal = :idSucursal)
                      AND (
                            :term IS NULL
                            OR LOWER(p.nombre) LIKE LOWER(CONCAT('%', :term, '%'))
                            OR v.sku LIKE CONCAT(:term, '%')
                            OR (v.codigoExterno IS NOT NULL AND v.codigoExterno LIKE CONCAT(:term, '%'))
                      )
                    """)
    Page<Producto> buscarConFiltros(
            @Param("term") String term,
            @Param("idSucursal") Integer idSucursal,
            @Param("estadoExcluido") String estadoExcluido,
            Pageable pageable);

    Optional<Producto> findFirstBySucursal_IdSucursalAndCategoria_IdCategoriaAndNombreIgnoreCaseAndEstadoNotOrderByIdProductoAsc(
            Integer idSucursal,
            Integer idCategoria,
            String nombre,
            String estado);

    Optional<Producto> findByIdProductoAndEstadoNot(Integer idProducto, String estado);

    Optional<Producto> findByIdProductoAndSucursal_IdSucursalAndEstadoNot(
            Integer idProducto,
            Integer idSucursal,
            String estado);

    @Query("SELECT COUNT(v) > 0 FROM ProductoVariante v WHERE v.producto.idProducto = :idProducto")
    boolean estaEnUso(Integer idProducto);
}
