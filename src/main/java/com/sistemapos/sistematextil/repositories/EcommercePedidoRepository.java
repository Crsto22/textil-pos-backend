package com.sistemapos.sistematextil.repositories;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sistemapos.sistematextil.model.EcommercePedido;

public interface EcommercePedidoRepository extends JpaRepository<EcommercePedido, Integer> {
    Optional<EcommercePedido> findByCodigo(String codigo);
    Optional<EcommercePedido> findByComprobanteTokenHash(String comprobanteTokenHash);
    boolean existsByCodigo(String codigo);
    List<EcommercePedido> findByEstadoAndReservaExpiraAtLessThanEqual(String estado, LocalDateTime fecha);

    @Query(value = """
            SELECT *
            FROM ecommerce_pedido
            WHERE id_ecommerce_pedido = :id
            LIMIT 1
            FOR UPDATE
            """, nativeQuery = true)
    Optional<EcommercePedido> findByIdForUpdate(@Param("id") Integer id);

    @Query("""
            SELECT p
            FROM EcommercePedido p
            WHERE (:estado IS NULL OR p.estado = :estado)
              AND (
                    :term IS NULL
                    OR LOWER(p.codigo) LIKE LOWER(CONCAT('%', :term, '%'))
                    OR LOWER(p.clienteNombres) LIKE LOWER(CONCAT('%', :term, '%'))
                    OR LOWER(p.clienteApellidos) LIKE LOWER(CONCAT('%', :term, '%'))
                    OR p.clienteDni LIKE CONCAT('%', :term, '%')
                    OR p.clienteTelefono LIKE CONCAT('%', :term, '%')
              )
              AND (:fechaInicio IS NULL OR p.fecha >= :fechaInicio)
              AND (:fechaFinExclusive IS NULL OR p.fecha < :fechaFinExclusive)
            """)
    Page<EcommercePedido> buscarAdmin(
            @Param("estado") String estado,
            @Param("term") String term,
            @Param("fechaInicio") LocalDateTime fechaInicio,
            @Param("fechaFinExclusive") LocalDateTime fechaFinExclusive,
            Pageable pageable);

    @Query("""
            SELECT p
            FROM EcommercePedido p
            WHERE (:fechaInicio IS NULL OR p.fecha >= :fechaInicio)
              AND (:fechaFinExclusive IS NULL OR p.fecha < :fechaFinExclusive)
            ORDER BY p.fecha DESC
            """)
    List<EcommercePedido> listarReporteExcel(
            @Param("fechaInicio") LocalDateTime fechaInicio,
            @Param("fechaFinExclusive") LocalDateTime fechaFinExclusive);

    @Query(value = """
            SELECT
                CAST(COALESCE(SUM(CASE WHEN estado = 'ACEPTADO' THEN 1 ELSE 0 END), 0) AS SIGNED),
                CAST(COALESCE(SUM(CASE WHEN estado = 'CANCELADO_POR_TIEMPO' THEN 1 ELSE 0 END), 0) AS SIGNED),
                CAST(COALESCE(SUM(CASE WHEN estado = 'PAGO_EN_REVISION' THEN 1 ELSE 0 END), 0) AS SIGNED),
                COALESCE(SUM(CASE WHEN estado = 'ACEPTADO' THEN total ELSE 0 END), 0)
            FROM ecommerce_pedido
            WHERE (
                    :term IS NULL
                    OR LOWER(codigo) LIKE LOWER(CONCAT('%', :term, '%'))
                    OR LOWER(cliente_nombres) LIKE LOWER(CONCAT('%', :term, '%'))
                    OR LOWER(cliente_apellidos) LIKE LOWER(CONCAT('%', :term, '%'))
                    OR cliente_dni LIKE CONCAT('%', :term, '%')
                    OR cliente_telefono LIKE CONCAT('%', :term, '%')
              )
              AND (:fechaInicio IS NULL OR fecha >= :fechaInicio)
              AND (:fechaFinExclusive IS NULL OR fecha < :fechaFinExclusive)
            """, nativeQuery = true)
    Object[] obtenerEstadisticasAdmin(
            @Param("term") String term,
            @Param("fechaInicio") LocalDateTime fechaInicio,
            @Param("fechaFinExclusive") LocalDateTime fechaFinExclusive);
}
