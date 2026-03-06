package com.sistemapos.sistematextil.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sistemapos.sistematextil.model.Pago;

public interface PagoRepository extends JpaRepository<Pago, Integer> {

    List<Pago> findByVenta_IdVentaAndDeletedAtIsNullOrderByIdPagoAsc(Integer idVenta);

    @Query("""
            SELECT p
            FROM Pago p
            WHERE p.deletedAt IS NULL
              AND p.venta.idVenta IN :ventaIds
            ORDER BY p.venta.idVenta ASC, p.idPago ASC
            """)
    List<Pago> findActivosByVentaIds(@Param("ventaIds") List<Integer> ventaIds);

    long countByVenta_IdVentaAndDeletedAtIsNull(Integer idVenta);
}
