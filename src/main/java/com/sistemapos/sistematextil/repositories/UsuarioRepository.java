package com.sistemapos.sistematextil.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.sistemapos.sistematextil.model.Usuario;
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
            SELECT CONCAT(u.nombre, ' ', u.apellido)
            FROM usuario u
            WHERE u.id_sucursal = :idSucursal
              AND u.deleted_at IS NULL
              AND u.activo = 1
            ORDER BY RAND()
            LIMIT 5
            """, nativeQuery = true)
    List<String> findTop5NombresCompletosRandomBySucursal(@Param("idSucursal") Integer idSucursal);
}
