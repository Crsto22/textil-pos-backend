package com.sistemapos.sistematextil.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sistemapos.sistematextil.model.VentaDetalle;

public interface VentaDetalleRepository extends JpaRepository<VentaDetalle, Integer> {

    List<VentaDetalle> findByVenta_IdVentaAndDeletedAtIsNullOrderByIdVentaDetalleAsc(Integer idVenta);

    @Query("""
            SELECT vd
            FROM VentaDetalle vd
            WHERE vd.deletedAt IS NULL
              AND vd.venta.idVenta IN :ventaIds
            ORDER BY vd.venta.idVenta ASC, vd.idVentaDetalle ASC
            """)
    List<VentaDetalle> findActivosByVentaIds(@Param("ventaIds") List<Integer> ventaIds);

    long countByVenta_IdVentaAndDeletedAtIsNull(Integer idVenta);
}
