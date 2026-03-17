package com.sistemapos.sistematextil.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sistemapos.sistematextil.model.ComunicacionBaja;

public interface ComunicacionBajaRepository extends JpaRepository<ComunicacionBaja, Integer> {

    Optional<ComunicacionBaja> findByIdBajaAndDeletedAtIsNull(Integer idBaja);

    List<ComunicacionBaja> findByVenta_IdVentaAndDeletedAtIsNull(Integer idVenta);

    List<ComunicacionBaja> findBySunatEstadoAndDeletedAtIsNull(String sunatEstado);

    @Query("""
            SELECT cb
            FROM ComunicacionBaja cb
            WHERE cb.deletedAt IS NULL
              AND cb.sunatEstado = 'PENDIENTE'
              AND cb.sunatTicket IS NOT NULL
            """)
    List<ComunicacionBaja> findPendientesConTicket();

    @Query(value = """
            SELECT COALESCE(MAX(
                CAST(SUBSTRING(identificador_baja, LENGTH(CONCAT('RA-', DATE_FORMAT(:fecha, '%Y%m%d'), '-')) + 1) AS UNSIGNED)
            ), 0)
            FROM comunicacion_baja
            WHERE identificador_baja LIKE CONCAT('RA-', DATE_FORMAT(:fecha, '%Y%m%d'), '-%')
              AND deleted_at IS NULL
            """, nativeQuery = true)
    Integer obtenerMaxCorrelativoBaja(@Param("fecha") java.time.LocalDate fecha);
}
