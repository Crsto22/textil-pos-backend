package com.sistemapos.sistematextil.repositories;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sistemapos.sistematextil.model.NotaCreditoDetalle;
import com.sistemapos.sistematextil.util.sunat.SunatEstado;

public interface NotaCreditoDetalleRepository extends JpaRepository<NotaCreditoDetalle, Integer> {

    List<NotaCreditoDetalle> findByNotaCredito_IdNotaCreditoAndDeletedAtIsNull(Integer idNotaCredito);

    long countByNotaCredito_IdNotaCreditoAndDeletedAtIsNull(Integer idNotaCredito);

    @Query("""
            SELECT COALESCE(SUM(ncd.cantidad), 0)
            FROM NotaCreditoDetalle ncd
            JOIN ncd.notaCredito nc
            WHERE ncd.deletedAt IS NULL
              AND nc.deletedAt IS NULL
              AND ncd.ventaDetalleReferencia.idVentaDetalle = :idVentaDetalle
              AND nc.codigoMotivo IN :codigosMotivo
              AND nc.sunatEstado IN :estados
            """)
    Integer obtenerCantidadAplicadaPorVentaDetalle(
            @Param("idVentaDetalle") Integer idVentaDetalle,
            @Param("codigosMotivo") Collection<String> codigosMotivo,
            @Param("estados") Collection<SunatEstado> estados);
}
