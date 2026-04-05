package com.sistemapos.sistematextil.repositories;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.sistemapos.sistematextil.model.HistorialStock;

@Repository
public interface HistorialStockRepository extends JpaRepository<HistorialStock, Integer> {

    Page<HistorialStock> findAllByOrderByFechaDesc(Pageable pageable);

    Page<HistorialStock> findBySucursalIdSucursalOrderByFechaDesc(Integer idSucursal, Pageable pageable);

    List<HistorialStock> findByProductoVarianteProductoIdProductoOrderByFechaDesc(Integer idProducto);

    List<HistorialStock> findByProductoVarianteProductoIdProductoAndSucursalIdSucursalOrderByFechaDesc(
            Integer idProducto,
            Integer idSucursal);

    List<HistorialStock> findByProductoVarianteIdProductoVarianteOrderByFechaDesc(Integer idProductoVariante);

    List<HistorialStock> findByProductoVarianteIdProductoVarianteAndSucursalIdSucursalOrderByFechaDesc(
            Integer idProductoVariante,
            Integer idSucursal);

    List<HistorialStock> findBySucursalIdSucursalOrderByFechaDesc(Integer idSucursal);
}
