package com.sistemapos.sistematextil.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.sistemapos.sistematextil.model.SucursalMetodoPagoConfig;

@Repository
public interface SucursalMetodoPagoConfigRepository extends JpaRepository<SucursalMetodoPagoConfig, Integer> {

    @EntityGraph(attributePaths = "metodoPago")
    @Query("""
            SELECT config
            FROM SucursalMetodoPagoConfig config
            JOIN config.metodoPago metodo
            WHERE config.sucursal.idSucursal = :idSucursal
              AND config.deletedAt IS NULL
            ORDER BY metodo.nombre ASC
            """)
    List<SucursalMetodoPagoConfig> findActivosBySucursal(@Param("idSucursal") Integer idSucursal);

    @EntityGraph(attributePaths = "metodoPago")
    Optional<SucursalMetodoPagoConfig> findBySucursal_IdSucursalAndMetodoPago_IdMetodoPagoAndDeletedAtIsNull(
            Integer idSucursal,
            Integer idMetodoPago);
}
