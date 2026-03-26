package com.sistemapos.sistematextil.repositories;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sistemapos.sistematextil.model.Cliente;
import com.sistemapos.sistematextil.util.cliente.TipoDocumento;

public interface ClienteRepository extends JpaRepository<Cliente, Integer> {

    Page<Cliente> findByDeletedAtIsNull(Pageable pageable);
    Page<Cliente> findByDeletedAtIsNullAndEmpresa_IdEmpresa(Pageable pageable, Integer idEmpresa);

    @Query("""
            SELECT c
            FROM Cliente c
            LEFT JOIN c.empresa e
            WHERE c.deletedAt IS NULL
              AND (:idEmpresa IS NULL OR e.idEmpresa = :idEmpresa)
              AND (:tipoDocumentoFiltro IS NULL OR c.tipoDocumento = :tipoDocumentoFiltro)
              AND (
                    :term IS NULL
                    OR LOWER(c.nombres) LIKE LOWER(CONCAT('%', :term, '%'))
                    OR UPPER(COALESCE(c.nroDocumento, '')) LIKE CONCAT(UPPER(:term), '%')
                    OR COALESCE(c.telefono, '') LIKE CONCAT('%', :term, '%')
              )
            """)
    Page<Cliente> buscarConFiltros(
            @Param("term") String term,
            @Param("idEmpresa") Integer idEmpresa,
            @Param("tipoDocumentoFiltro") TipoDocumento tipoDocumentoFiltro,
            Pageable pageable);

    Optional<Cliente> findByIdClienteAndDeletedAtIsNull(Integer idCliente);
    Optional<Cliente> findByIdClienteAndDeletedAtIsNullAndEmpresa_IdEmpresa(Integer idCliente, Integer idEmpresa);
    Optional<Cliente> findFirstByTelefonoAndDeletedAtIsNullAndEmpresa_IdEmpresaOrderByIdClienteAsc(
            String telefono,
            Integer idEmpresa);

    @Query("""
            SELECT COUNT(c)
            FROM Cliente c
            LEFT JOIN c.usuarioCreacion uc
            LEFT JOIN uc.sucursal s
            WHERE c.deletedAt IS NULL
              AND c.estado = 'ACTIVO'
              AND (:idEmpresa IS NULL OR c.empresa.idEmpresa = :idEmpresa)
              AND (:idSucursal IS NULL OR s.idSucursal = :idSucursal)
              AND c.fechaCreacion >= :inicioMes
              AND c.fechaCreacion < :finMesExclusive
            """)
    long contarNuevosMesParaReporte(
            @Param("idEmpresa") Integer idEmpresa,
            @Param("idSucursal") Integer idSucursal,
            @Param("inicioMes") LocalDateTime inicioMes,
            @Param("finMesExclusive") LocalDateTime finMesExclusive);

    @Query(value = """
            SELECT
              c.id_cliente,
              c.nombres AS cliente,
              c.tipo_documento,
              c.nro_documento,
              MAX(v.fecha) AS ultima_compra,
              COALESCE(COUNT(v.id_venta), 0) AS compras,
              COALESCE(SUM(v.total), 0) AS total_gastado,
              COALESCE(AVG(v.total), 0) AS ticket_promedio,
              CASE
                WHEN MAX(v.fecha) IS NULL THEN NULL
                ELSE DATEDIFF(DATE(:fechaReferencia), DATE(MAX(v.fecha)))
              END AS recencia_dias
            FROM cliente c
            LEFT JOIN usuario uc ON uc.id_usuario = c.id_usuario_creacion
            LEFT JOIN venta v ON v.id_cliente = c.id_cliente
              AND v.deleted_at IS NULL
              AND v.estado = 'EMITIDA'
              AND (:fechaInicio IS NULL OR v.fecha >= :fechaInicio)
              AND (:fechaFinExclusive IS NULL OR v.fecha < :fechaFinExclusive)
              AND (:idSucursal IS NULL OR v.id_sucursal = :idSucursal)
            WHERE c.deleted_at IS NULL
              AND c.activo = TRUE
              AND (:idEmpresa IS NULL OR c.id_empresa = :idEmpresa)
              AND (
                    :idSucursal IS NULL
                    OR uc.id_sucursal = :idSucursal
                    OR v.id_venta IS NOT NULL
              )
            GROUP BY c.id_cliente, c.nombres, c.tipo_documento, c.nro_documento
            ORDER BY total_gastado DESC, compras DESC, c.nombres ASC
            """, nativeQuery = true)
    List<Object[]> obtenerResumenReporteClientes(
            @Param("idEmpresa") Integer idEmpresa,
            @Param("idSucursal") Integer idSucursal,
            @Param("fechaInicio") LocalDateTime fechaInicio,
            @Param("fechaFinExclusive") LocalDateTime fechaFinExclusive,
            @Param("fechaReferencia") LocalDateTime fechaReferencia);

    @Query(value = """
            SELECT
              DATE_FORMAT(c.created_at, '%x-%v') AS cohorte_semana,
              DATE(DATE_SUB(c.created_at, INTERVAL WEEKDAY(c.created_at) DAY)) AS inicio_semana,
              COUNT(*) AS clientes_nuevos,
              SUM(
                CASE
                  WHEN EXISTS (
                    SELECT 1
                    FROM venta v
                    WHERE v.id_cliente = c.id_cliente
                      AND v.deleted_at IS NULL
                      AND v.estado = 'EMITIDA'
                      AND (:idSucursal IS NULL OR v.id_sucursal = :idSucursal)
                      AND v.fecha < :fechaFinExclusive
                    GROUP BY v.id_cliente
                    HAVING COUNT(v.id_venta) > 1
                  )
                  THEN 1
                  ELSE 0
                END
              ) AS clientes_recompran
            FROM cliente c
            LEFT JOIN usuario uc ON uc.id_usuario = c.id_usuario_creacion
            WHERE c.deleted_at IS NULL
              AND c.activo = TRUE
              AND (:idEmpresa IS NULL OR c.id_empresa = :idEmpresa)
              AND (:idSucursal IS NULL OR uc.id_sucursal = :idSucursal)
              AND c.created_at >= :fechaInicio
              AND c.created_at < :fechaFinExclusive
            GROUP BY DATE_FORMAT(c.created_at, '%x-%v'), DATE(DATE_SUB(c.created_at, INTERVAL WEEKDAY(c.created_at) DAY))
            ORDER BY inicio_semana ASC
            """, nativeQuery = true)
    List<Object[]> obtenerCohorteSemanalReporte(
            @Param("idEmpresa") Integer idEmpresa,
            @Param("idSucursal") Integer idSucursal,
            @Param("fechaInicio") LocalDateTime fechaInicio,
            @Param("fechaFinExclusive") LocalDateTime fechaFinExclusive);
}
