package com.sistemapos.sistematextil.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sistemapos.sistematextil.model.UsuarioSucursal;

public interface UsuarioSucursalRepository extends JpaRepository<UsuarioSucursal, Integer> {

    @Query("""
            SELECT us
            FROM UsuarioSucursal us
            JOIN FETCH us.sucursal s
            WHERE us.usuario.idUsuario = :idUsuario
              AND s.deletedAt IS NULL
            ORDER BY s.idSucursal ASC
            """)
    List<UsuarioSucursal> findActivasByUsuarioId(@Param("idUsuario") Integer idUsuario);

    @Modifying
    @Query("DELETE FROM UsuarioSucursal us WHERE us.usuario.idUsuario = :idUsuario")
    void deleteByUsuarioId(@Param("idUsuario") Integer idUsuario);

    boolean existsByUsuario_IdUsuarioAndSucursal_IdSucursal(Integer idUsuario, Integer idSucursal);
}
