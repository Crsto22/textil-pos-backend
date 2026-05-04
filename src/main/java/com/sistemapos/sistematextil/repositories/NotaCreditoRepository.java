package com.sistemapos.sistematextil.repositories;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sistemapos.sistematextil.model.NotaCredito;
import com.sistemapos.sistematextil.util.sunat.SunatEstado;

public interface NotaCreditoRepository extends JpaRepository<NotaCredito, Integer> {

    Optional<NotaCredito> findByIdNotaCreditoAndDeletedAtIsNull(Integer idNotaCredito);

    Optional<NotaCredito> findByIdNotaCreditoAndDeletedAtIsNullAndSucursal_IdSucursal(Integer idNotaCredito, Integer idSucursal);

    List<NotaCredito> findByVentaReferencia_IdVentaAndDeletedAtIsNullOrderByIdNotaCreditoDesc(Integer idVenta);

    Optional<NotaCredito> findTopByVentaReferencia_IdVentaAndDeletedAtIsNullOrderByIdNotaCreditoDesc(Integer idVenta);

    @Query("""
            SELECT nc
            FROM NotaCredito nc
            LEFT JOIN nc.cliente c
            LEFT JOIN nc.ventaReferencia v
            WHERE nc.deletedAt IS NULL
              AND (:idSucursal IS NULL OR nc.sucursal.idSucursal = :idSucursal)
              AND (:idUsuario IS NULL OR nc.usuario.idUsuario = :idUsuario)
              AND (:idCliente IS NULL OR c.idCliente = :idCliente)
              AND (:idVenta IS NULL OR v.idVenta = :idVenta)
              AND (:codigoMotivo IS NULL OR nc.codigoMotivo = :codigoMotivo)
              AND (:fechaInicio IS NULL OR nc.fecha >= :fechaInicio)
              AND (:fechaFinExclusive IS NULL OR nc.fecha < :fechaFinExclusive)
              AND (
                    :term IS NULL
                    OR LOWER(CAST(c.nombres AS string)) LIKE LOWER(CONCAT('%', :term, '%'))
                    OR LOWER(CAST(nc.descripcionMotivo AS string)) LIKE LOWER(CONCAT('%', :term, '%'))
                    OR TRIM(str(nc.correlativo)) LIKE CONCAT(:term, '%')
                    OR LOWER(CAST(
                            CONCAT(
                                    CONCAT(COALESCE(nc.serie, ''), '-'),
                                    COALESCE(TRIM(str(nc.correlativo)), '')
                            )
                    AS string)) LIKE LOWER(CONCAT('%', :term, '%'))
                    OR LOWER(CAST(
                            CONCAT(
                                    CONCAT(COALESCE(nc.serieRef, ''), '-'),
                                    COALESCE(TRIM(str(nc.correlativoRef)), '')
                            )
                    AS string)) LIKE LOWER(CONCAT('%', :term, '%'))
              )
            """)
    Page<NotaCredito> buscarConFiltros(
            @Param("term") String term,
            @Param("idSucursal") Integer idSucursal,
            @Param("idUsuario") Integer idUsuario,
            @Param("idCliente") Integer idCliente,
            @Param("idVenta") Integer idVenta,
            @Param("codigoMotivo") String codigoMotivo,
            @Param("fechaInicio") java.time.LocalDateTime fechaInicio,
            @Param("fechaFinExclusive") java.time.LocalDateTime fechaFinExclusive,
            Pageable pageable);

    @Query("""
            SELECT COALESCE(MAX(nc.correlativo), 0)
            FROM NotaCredito nc
            WHERE nc.deletedAt IS NULL
              AND nc.tipoComprobante = :tipoComprobante
              AND nc.serie = :serie
            """)
    Integer obtenerMaxCorrelativoPorDocumento(
            @Param("tipoComprobante") String tipoComprobante,
            @Param("serie") String serie);

    @Query("""
            SELECT nc.idNotaCredito
            FROM NotaCredito nc
            WHERE nc.deletedAt IS NULL
              AND nc.sunatEstado IN :estadosSunat
              AND NOT EXISTS (
                    SELECT 1
                    FROM SunatJob j
                    WHERE j.tipoDocumento = com.sistemapos.sistematextil.util.sunat.SunatJobTipoDocumento.NOTA_CREDITO
                      AND j.documentoId = nc.idNotaCredito
              )
            ORDER BY nc.updatedAt ASC, nc.idNotaCredito ASC
            """)
    List<Integer> findPendingSunatIdsWithoutJob(
            @Param("estadosSunat") Collection<SunatEstado> estadosSunat,
            Pageable pageable);
}
