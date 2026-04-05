package com.sistemapos.sistematextil.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sistemapos.sistematextil.model.CanalVenta;

public interface CanalVentaRepository extends JpaRepository<CanalVenta, Integer> {

    List<CanalVenta> findByDeletedAtIsNullOrderByIdCanalVentaAsc();

    List<CanalVenta> findBySucursalIdSucursalAndDeletedAtIsNullOrderByIdCanalVentaAsc(Integer idSucursal);

    Optional<CanalVenta> findByIdCanalVentaAndDeletedAtIsNull(Integer idCanalVenta);

    Optional<CanalVenta> findByIdCanalVentaAndSucursalIdSucursalAndDeletedAtIsNull(
            Integer idCanalVenta,
            Integer idSucursal);

    boolean existsBySucursalIdSucursalAndNombreIgnoreCaseAndDeletedAtIsNull(Integer idSucursal, String nombre);

    boolean existsBySucursalIdSucursalAndNombreIgnoreCaseAndDeletedAtIsNullAndIdCanalVentaNot(
            Integer idSucursal,
            String nombre,
            Integer idCanalVenta);
}
