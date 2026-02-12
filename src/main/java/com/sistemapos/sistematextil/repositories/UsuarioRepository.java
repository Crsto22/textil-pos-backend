package com.sistemapos.sistematextil.repositories;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.sistemapos.sistematextil.model.Usuario;


public interface UsuarioRepository extends JpaRepository <Usuario, Integer>{

    Optional <Usuario> findByCorreo(String correo);
    Optional<Usuario> findByCorreoAndDeletedAtIsNull(String correo);
    Optional<Usuario> findByDni(String dni);
    Optional<Usuario> findByTelefono(String telefono);
    Optional<Usuario> findByDniAndDeletedAtIsNull(String dni);
    Optional<Usuario> findByTelefonoAndDeletedAtIsNull(String telefono);
    Page<Usuario> findByDeletedAtIsNull(Pageable pageable);
    @Query("""
        SELECT u FROM Usuario u
        WHERE u.deletedAt IS NULL
          AND (
                LOWER(u.nombre) LIKE LOWER(CONCAT('%', :term, '%'))
                OR LOWER(u.apellido) LIKE LOWER(CONCAT('%', :term, '%'))
                OR CONCAT(CONCAT(LOWER(u.nombre), ' '), LOWER(u.apellido)) LIKE LOWER(CONCAT('%', :term, '%'))
                OR CONCAT(CONCAT(LOWER(u.apellido), ' '), LOWER(u.nombre)) LIKE LOWER(CONCAT('%', :term, '%'))
                OR u.dni LIKE CONCAT(:term, '%')
          )
        """)
    Page<Usuario> buscarPorNombreODni(@Param("term") String term, Pageable pageable);
    Optional<Usuario> findByIdUsuarioAndDeletedAtIsNull(Integer idUsuario);
}
