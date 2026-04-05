package com.sistemapos.sistematextil.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sistemapos.sistematextil.model.HistorialStock;
import com.sistemapos.sistematextil.model.ProductoVariante;
import com.sistemapos.sistematextil.model.Sucursal;
import com.sistemapos.sistematextil.model.SucursalStock;
import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.repositories.HistorialStockRepository;
import com.sistemapos.sistematextil.repositories.ProductoVarianteRepository;
import com.sistemapos.sistematextil.repositories.SucursalRepository;
import com.sistemapos.sistematextil.repositories.SucursalStockRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class StockMovimientoService {

    private final SucursalStockRepository sucursalStockRepository;
    private final SucursalRepository sucursalRepository;
    private final ProductoVarianteRepository productoVarianteRepository;
    private final HistorialStockRepository historialStockRepository;

    @Transactional
    public MovimientoStock descontar(
            Integer idSucursal,
            Integer idProductoVariante,
            Integer cantidad,
            HistorialStock.TipoMovimiento tipoMovimiento,
            String motivo,
            Usuario usuario) {
        if (cantidad == null || cantidad <= 0) {
            throw new RuntimeException("La cantidad debe ser mayor a 0");
        }

        SucursalStock stock = obtenerStockConBloqueo(idSucursal, idProductoVariante);
        int stockAnterior = valorEntero(stock.getCantidad());
        if (stockAnterior < cantidad) {
            throw new RuntimeException("Stock insuficiente. Disponible: " + stockAnterior + ", solicitado: " + cantidad);
        }

        int stockNuevo = stockAnterior - cantidad;
        stock.setCantidad(stockNuevo);
        SucursalStock guardado = sucursalStockRepository.save(stock);
        HistorialStock historial = registrarHistorial(tipoMovimiento, motivo, guardado, usuario, cantidad, stockAnterior, stockNuevo);
        sincronizarContextoVariante(guardado);
        return new MovimientoStock(guardado, historial, stockAnterior, stockNuevo);
    }

    @Transactional
    public MovimientoStock incrementar(
            Integer idSucursal,
            Integer idProductoVariante,
            Integer cantidad,
            HistorialStock.TipoMovimiento tipoMovimiento,
            String motivo,
            Usuario usuario) {
        if (cantidad == null || cantidad <= 0) {
            throw new RuntimeException("La cantidad debe ser mayor a 0");
        }

        SucursalStock stock = obtenerStockConBloqueo(idSucursal, idProductoVariante);
        int stockAnterior = valorEntero(stock.getCantidad());
        int stockNuevo = stockAnterior + cantidad;
        stock.setCantidad(stockNuevo);
        SucursalStock guardado = sucursalStockRepository.save(stock);
        HistorialStock historial = registrarHistorial(tipoMovimiento, motivo, guardado, usuario, cantidad, stockAnterior, stockNuevo);
        sincronizarContextoVariante(guardado);
        return new MovimientoStock(guardado, historial, stockAnterior, stockNuevo);
    }

    @Transactional
    public MovimientoStock ajustar(
            Integer idSucursal,
            Integer idProductoVariante,
            Integer nuevoStock,
            String motivo,
            Usuario usuario) {
        if (nuevoStock == null || nuevoStock < 0) {
            throw new RuntimeException("El stock no puede ser negativo");
        }

        SucursalStock stock = obtenerStockConBloqueo(idSucursal, idProductoVariante);
        int stockAnterior = valorEntero(stock.getCantidad());
        stock.setCantidad(nuevoStock);
        SucursalStock guardado = sucursalStockRepository.save(stock);
        int cantidadMovimiento = Math.abs(nuevoStock - stockAnterior);
        HistorialStock historial = registrarHistorial(
                HistorialStock.TipoMovimiento.AJUSTE,
                motivo,
                guardado,
                usuario,
                cantidadMovimiento,
                stockAnterior,
                nuevoStock);
        sincronizarContextoVariante(guardado);
        return new MovimientoStock(guardado, historial, stockAnterior, nuevoStock);
    }

    @Transactional(readOnly = true)
    public StockContexto obtenerContexto(Integer idSucursal, Integer idProductoVariante) {
        SucursalStock stock = sucursalStockRepository
                .findBySucursalIdSucursalAndProductoVarianteIdProductoVariante(idSucursal, idProductoVariante)
                .orElseGet(() -> crearContextoVirtual(idSucursal, idProductoVariante));
        sincronizarContextoVariante(stock);
        return new StockContexto(stock, valorEntero(stock.getCantidad()));
    }

    @Transactional
    public StockContexto obtenerContextoConBloqueo(Integer idSucursal, Integer idProductoVariante) {
        SucursalStock stock = obtenerStockConBloqueo(idSucursal, idProductoVariante);
        sincronizarContextoVariante(stock);
        return new StockContexto(stock, valorEntero(stock.getCantidad()));
    }

    private SucursalStock obtenerStockConBloqueo(Integer idSucursal, Integer idProductoVariante) {
        return sucursalStockRepository
                .findBySucursalIdSucursalAndProductoVarianteIdProductoVarianteForUpdate(idSucursal, idProductoVariante)
                .orElseGet(() -> crearFilaStock(idSucursal, idProductoVariante));
    }

    private SucursalStock crearContextoVirtual(Integer idSucursal, Integer idProductoVariante) {
        Sucursal sucursal = obtenerSucursalActiva(idSucursal);
        ProductoVariante variante = obtenerVarianteActiva(idProductoVariante);
        SucursalStock stock = new SucursalStock();
        stock.setSucursal(sucursal);
        stock.setProductoVariante(variante);
        stock.setCantidad(0);
        return stock;
    }

    private SucursalStock crearFilaStock(Integer idSucursal, Integer idProductoVariante) {
        Sucursal sucursal = obtenerSucursalActiva(idSucursal);
        ProductoVariante variante = obtenerVarianteActiva(idProductoVariante);

        SucursalStock nuevo = new SucursalStock();
        nuevo.setSucursal(sucursal);
        nuevo.setProductoVariante(variante);
        nuevo.setCantidad(0);
        return sucursalStockRepository.saveAndFlush(nuevo);
    }

    private Sucursal obtenerSucursalActiva(Integer idSucursal) {
        return sucursalRepository.findByIdSucursalAndDeletedAtIsNull(idSucursal)
                .filter(sucursal -> "ACTIVO".equalsIgnoreCase(sucursal.getEstado()))
                .orElseThrow(() -> new RuntimeException("Sucursal no encontrada o inactiva"));
    }

    private ProductoVariante obtenerVarianteActiva(Integer idProductoVariante) {
        return productoVarianteRepository.findByIdProductoVarianteAndDeletedAtIsNull(idProductoVariante)
                .orElseThrow(() -> new RuntimeException("La variante con ID " + idProductoVariante + " no existe"));
    }

    private HistorialStock registrarHistorial(
            HistorialStock.TipoMovimiento tipoMovimiento,
            String motivo,
            SucursalStock stock,
            Usuario usuario,
            Integer cantidad,
            Integer stockAnterior,
            Integer stockNuevo) {
        HistorialStock movimiento = new HistorialStock();
        movimiento.setTipoMovimiento(tipoMovimiento);
        movimiento.setMotivo(motivo);
        movimiento.setProductoVariante(stock.getProductoVariante());
        movimiento.setSucursal(stock.getSucursal());
        movimiento.setUsuario(usuario);
        movimiento.setCantidad(cantidad);
        movimiento.setStockAnterior(stockAnterior);
        movimiento.setStockNuevo(stockNuevo);
        return historialStockRepository.save(movimiento);
    }

    private void sincronizarContextoVariante(SucursalStock stock) {
        if (stock.getProductoVariante() == null) {
            return;
        }
        stock.getProductoVariante().setSucursal(stock.getSucursal());
        stock.getProductoVariante().setStock(stock.getCantidad());
    }

    private int valorEntero(Integer value) {
        return value == null ? 0 : value;
    }

    public record MovimientoStock(
            SucursalStock sucursalStock,
            HistorialStock historial,
            Integer stockAnterior,
            Integer stockNuevo) {
    }

    public record StockContexto(
            SucursalStock sucursalStock,
            Integer stockActual) {
    }
}
