package com.sistemapos.sistematextil.repositories;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sistemapos.sistematextil.model.Cotizacion;

public interface CotizacionRepository extends JpaRepository<Cotizacion, Integer> {

    @Query("""
            SELECT c
            FROM Cotizacion c
            LEFT JOIN c.cliente cl
            WHERE c.deletedAt IS NULL
              AND (:idSucursal IS NULL OR c.sucursal.idSucursal = :idSucursal)
              AND (:idUsuario IS NULL OR c.usuario.idUsuario = :idUsuario)
              AND (:estado IS NULL OR c.estado = :estado)
              AND (:fechaInicio IS NULL OR c.fecha >= :fechaInicio)
              AND (:fechaFinExclusive IS NULL OR c.fecha < :fechaFinExclusive)
              AND (
                    :term IS NULL
                    OR LOWER(COALESCE(cl.nombres, '')) LIKE LOWER(CONCAT('%', :term, '%'))
                    OR LOWER(COALESCE(c.observacion, '')) LIKE LOWER(CONCAT('%', :term, '%'))
                    OR TRIM(str(c.correlativo)) LIKE CONCAT(:term, '%')
                    OR LOWER(CAST(
                            CONCAT(
                                    CONCAT(COALESCE(c.serie, ''), '-'),
                                    COALESCE(TRIM(str(c.correlativo)), '')
                            )
                    AS string)) LIKE LOWER(CONCAT('%', :term, '%'))
              )
            """)
    Page<Cotizacion> buscarConFiltros(
            @Param("term") String term,
            @Param("idSucursal") Integer idSucursal,
            @Param("idUsuario") Integer idUsuario,
            @Param("estado") String estado,
            @Param("fechaInicio") LocalDateTime fechaInicio,
            @Param("fechaFinExclusive") LocalDateTime fechaFinExclusive,
            Pageable pageable);

    @Query("""
            SELECT c
            FROM Cotizacion c
            WHERE c.deletedAt IS NULL
              AND (:idSucursal IS NULL OR c.sucursal.idSucursal = :idSucursal)
              AND (:estado IS NULL OR c.estado = :estado)
              AND c.fecha >= :fechaInicio
              AND c.fecha < :fechaFinExclusive
            ORDER BY c.fecha ASC
            """)
    List<Cotizacion> buscarParaReporte(
            @Param("idSucursal") Integer idSucursal,
            @Param("estado") String estado,
            @Param("fechaInicio") LocalDateTime fechaInicio,
            @Param("fechaFinExclusive") LocalDateTime fechaFinExclusive);

    Optional<Cotizacion> findByIdCotizacionAndDeletedAtIsNull(Integer idCotizacion);

    Optional<Cotizacion> findByIdCotizacionAndDeletedAtIsNullAndSucursal_IdSucursal(
            Integer idCotizacion,
            Integer idSucursal);

    boolean existsBySucursal_IdSucursalAndSerieAndCorrelativoAndDeletedAtIsNull(
            Integer idSucursal,
            String serie,
            Integer correlativo);

    boolean existsBySucursal_IdSucursalAndSerieAndCorrelativoAndDeletedAtIsNullAndIdCotizacionNot(
            Integer idSucursal,
            String serie,
            Integer correlativo,
            Integer idCotizacion);

    @Query("""
            SELECT COALESCE(MAX(c.correlativo), 0)
            FROM Cotizacion c
            WHERE c.deletedAt IS NULL
              AND c.sucursal.idSucursal = :idSucursal
              AND c.serie = :serie
            """)
    Integer obtenerMaxCorrelativoPorSerie(
            @Param("idSucursal") Integer idSucursal,
            @Param("serie") String serie);
}
