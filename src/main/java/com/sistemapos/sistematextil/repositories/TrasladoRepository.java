package com.sistemapos.sistematextil.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sistemapos.sistematextil.model.Traslado;

public interface TrasladoRepository extends JpaRepository<Traslado, Integer> {

    @Query("""
            SELECT t
            FROM Traslado t
            JOIN FETCH t.sucursalOrigen
            JOIN FETCH t.sucursalDestino
            JOIN FETCH t.productoVariante v
            JOIN FETCH v.producto p
            LEFT JOIN FETCH v.color
            LEFT JOIN FETCH v.talla
            JOIN FETCH t.usuario
            WHERE :idSucursal IS NULL
               OR t.sucursalOrigen.idSucursal = :idSucursal
               OR t.sucursalDestino.idSucursal = :idSucursal
            ORDER BY t.fecha DESC, t.idTraslado DESC
            """)
    Page<Traslado> listarConFiltros(@Param("idSucursal") Integer idSucursal, Pageable pageable);

    @Query(
            value = """
                    SELECT COUNT(*), COALESCE(SUM(cantidad), 0)
                    FROM traslado
                    WHERE id_sucursal_destino = :idSucursal
                    """,
            nativeQuery = true)
    Object[] obtenerResumenEntradas(@Param("idSucursal") Integer idSucursal);

    @Query(
            value = """
                    SELECT COUNT(*), COALESCE(SUM(cantidad), 0)
                    FROM traslado
                    WHERE id_sucursal_origen = :idSucursal
                    """,
            nativeQuery = true)
    Object[] obtenerResumenSalidas(@Param("idSucursal") Integer idSucursal);
}
