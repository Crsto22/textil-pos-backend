package com.sistemapos.sistematextil.repositories;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.repositories.projection.SucursalUsuarioResumenProjection;
import com.sistemapos.sistematextil.util.usuario.Rol;


public interface UsuarioRepository extends JpaRepository <Usuario, Integer>{

    Optional <Usuario> findByCorreo(String correo);
    Optional<Usuario> findByCorreoAndDeletedAtIsNull(String correo);
    Optional<Usuario> findByDni(String dni);
    Optional<Usuario> findByTelefono(String telefono);
    Optional<Usuario> findByDniAndDeletedAtIsNull(String dni);
    Optional<Usuario> findByTelefonoAndDeletedAtIsNull(String telefono);
    Page<Usuario> findByDeletedAtIsNull(Pageable pageable);
    @Query("""
        SELECT u
        FROM Usuario u
        LEFT JOIN u.sucursal s
        WHERE u.deletedAt IS NULL
          AND (
                :term IS NULL
                OR LOWER(u.nombre) LIKE LOWER(CONCAT('%', :term, '%'))
                OR LOWER(u.apellido) LIKE LOWER(CONCAT('%', :term, '%'))
                OR CONCAT(CONCAT(LOWER(u.nombre), ' '), LOWER(u.apellido)) LIKE LOWER(CONCAT('%', :term, '%'))
                OR CONCAT(CONCAT(LOWER(u.apellido), ' '), LOWER(u.nombre)) LIKE LOWER(CONCAT('%', :term, '%'))
                OR u.dni LIKE CONCAT(:term, '%')
          )
          AND (:rol IS NULL OR u.rol = :rol)
          AND (:idSucursal IS NULL OR s.idSucursal = :idSucursal)
        """)
    Page<Usuario> buscarConFiltros(
            @Param("term") String term,
            @Param("rol") Rol rol,
            @Param("idSucursal") Integer idSucursal,
            Pageable pageable);
    Optional<Usuario> findByIdUsuarioAndDeletedAtIsNull(Integer idUsuario);
    long countBySucursalIdSucursalAndDeletedAtIsNullAndEstado(Integer idSucursal, String estado);

    @Query(value = """
            SELECT
              u.id_usuario AS idUsuario,
              CONCAT(u.nombre, ' ', u.apellido) AS nombreCompleto,
              u.foto_perfil_url AS fotoPerfilUrl
            FROM usuario u
            WHERE u.id_sucursal = :idSucursal
              AND u.deleted_at IS NULL
              AND u.activo = 1
            ORDER BY RAND()
            LIMIT 5
            """, nativeQuery = true)
    List<SucursalUsuarioResumenProjection> findUsuariosResumenRandomBySucursal(
            @Param("idSucursal") Integer idSucursal);

    @Query(value = """
            SELECT
              u.id_usuario,
              CONCAT(u.nombre, ' ', u.apellido) AS usuario,
              u.rol,
              s.id_sucursal,
              s.nombre AS sucursal,
              COALESCE(SUM(CASE WHEN v.estado = 'EMITIDA' THEN 1 ELSE 0 END), 0) AS ventas_emitidas,
              COALESCE(SUM(CASE WHEN v.estado = 'EMITIDA' THEN v.total ELSE 0 END), 0) AS monto_emitido,
              COALESCE(SUM(CASE WHEN v.estado = 'ANULADA' THEN 1 ELSE 0 END), 0) AS anulaciones,
              COALESCE(SUM(CASE WHEN v.estado = 'ANULADA' THEN v.total ELSE 0 END), 0) AS monto_anulado
            FROM usuario u
            LEFT JOIN sucursal s ON s.id_sucursal = u.id_sucursal
            LEFT JOIN venta v ON v.id_usuario = u.id_usuario
              AND v.deleted_at IS NULL
              AND (:idSucursal IS NULL OR v.id_sucursal = :idSucursal)
              AND (:fechaInicio IS NULL OR v.fecha >= :fechaInicio)
              AND (:fechaFinExclusive IS NULL OR v.fecha < :fechaFinExclusive)
            WHERE u.deleted_at IS NULL
              AND u.rol IN ('ADMINISTRADOR', 'VENTAS')
              AND (
                    :idSucursal IS NULL
                    OR u.id_sucursal = :idSucursal
                    OR EXISTS (
                          SELECT 1
                          FROM venta vx
                          WHERE vx.id_usuario = u.id_usuario
                            AND vx.deleted_at IS NULL
                            AND vx.id_sucursal = :idSucursal
                            AND (:fechaInicio IS NULL OR vx.fecha >= :fechaInicio)
                            AND (:fechaFinExclusive IS NULL OR vx.fecha < :fechaFinExclusive)
                    )
              )
            GROUP BY u.id_usuario, u.nombre, u.apellido, u.rol, s.id_sucursal, s.nombre
            ORDER BY monto_emitido DESC, ventas_emitidas DESC, usuario ASC
            """, nativeQuery = true)
    List<Object[]> obtenerResumenReporteUsuarios(
            @Param("idSucursal") Integer idSucursal,
            @Param("fechaInicio") LocalDateTime fechaInicio,
            @Param("fechaFinExclusive") LocalDateTime fechaFinExclusive);

    @Query(value = """
            SELECT
              DATE(v.fecha) AS fecha,
              u.id_usuario,
              CONCAT(u.nombre, ' ', u.apellido) AS usuario,
              u.rol,
              COALESCE(SUM(CASE WHEN v.estado = 'EMITIDA' THEN 1 ELSE 0 END), 0) AS ventas_emitidas,
              COALESCE(SUM(CASE WHEN v.estado = 'EMITIDA' THEN v.total ELSE 0 END), 0) AS monto_emitido,
              COALESCE(SUM(CASE WHEN v.estado = 'ANULADA' THEN 1 ELSE 0 END), 0) AS anulaciones,
              COALESCE(SUM(CASE WHEN v.estado = 'ANULADA' THEN v.total ELSE 0 END), 0) AS monto_anulado
            FROM venta v
            JOIN usuario u ON u.id_usuario = v.id_usuario
            WHERE v.deleted_at IS NULL
              AND u.deleted_at IS NULL
              AND u.rol IN ('ADMINISTRADOR', 'VENTAS')
              AND (:idSucursal IS NULL OR v.id_sucursal = :idSucursal)
              AND (:fechaInicio IS NULL OR v.fecha >= :fechaInicio)
              AND (:fechaFinExclusive IS NULL OR v.fecha < :fechaFinExclusive)
            GROUP BY DATE(v.fecha), u.id_usuario, u.nombre, u.apellido, u.rol
            ORDER BY DATE(v.fecha) ASC, usuario ASC
            """, nativeQuery = true)
    List<Object[]> obtenerEvolucionDiariaReporteUsuarios(
            @Param("idSucursal") Integer idSucursal,
            @Param("fechaInicio") LocalDateTime fechaInicio,
            @Param("fechaFinExclusive") LocalDateTime fechaFinExclusive);
}
