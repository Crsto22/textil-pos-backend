package com.sistemapos.sistematextil.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sistemapos.sistematextil.model.NotaCredito;

public interface NotaCreditoRepository extends JpaRepository<NotaCredito, Integer> {

    Optional<NotaCredito> findByIdNotaCreditoAndDeletedAtIsNull(Integer idNotaCredito);

    List<NotaCredito> findByVentaReferencia_IdVentaAndDeletedAtIsNull(Integer idVenta);

    @Query("""
            SELECT COALESCE(MAX(nc.correlativo), 0)
            FROM NotaCredito nc
            WHERE nc.deletedAt IS NULL
              AND nc.sucursal.idSucursal = :idSucursal
              AND nc.serie = :serie
            """)
    Integer obtenerMaxCorrelativo(
            @Param("idSucursal") Integer idSucursal,
            @Param("serie") String serie);
}
