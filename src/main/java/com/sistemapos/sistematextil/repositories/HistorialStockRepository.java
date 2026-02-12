package com.sistemapos.sistematextil.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.sistemapos.sistematextil.model.HistorialStock;

@Repository
public interface HistorialStockRepository extends JpaRepository<HistorialStock, Integer> {
    
    // Buscar historial de un producto específico ordenado por lo más reciente
    List<HistorialStock> findByProductoVarianteProductoIdProductoOrderByFechaDesc(Integer idProducto);
    List<HistorialStock> findByProductoVarianteIdProductoVarianteOrderByFechaDesc(Integer idProductoVariante);
    
    // Buscar movimientos realizados en una sucursal específica
    List<HistorialStock> findBySucursalIdSucursalOrderByFechaDesc(Integer idSucursal);
}
