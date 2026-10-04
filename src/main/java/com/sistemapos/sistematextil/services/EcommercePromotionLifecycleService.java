package com.sistemapos.sistematextil.services;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sistemapos.sistematextil.model.EcommercePromocionCombo;
import com.sistemapos.sistematextil.model.EcommercePromocionComboItem;
import com.sistemapos.sistematextil.model.ProductoVariante;
import com.sistemapos.sistematextil.model.Sucursal;
import com.sistemapos.sistematextil.model.SucursalTipo;
import com.sistemapos.sistematextil.repositories.EcommercePromocionComboRepository;
import com.sistemapos.sistematextil.repositories.ProductoVarianteRepository;
import com.sistemapos.sistematextil.repositories.SucursalRepository;
import com.sistemapos.sistematextil.repositories.SucursalStockRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EcommercePromotionLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(EcommercePromotionLifecycleService.class);
    private static final String ACTIVO = "ACTIVO";
    private static final String INACTIVO = "INACTIVO";

    private final ProductoVarianteRepository productoVarianteRepository;
    private final EcommercePromocionComboRepository comboRepository;
    private final SucursalRepository sucursalRepository;
    private final SucursalStockRepository sucursalStockRepository;
    private final EcommerceCacheInvalidationService cacheInvalidationService;
    private final AtomicBoolean missingBranchReported = new AtomicBoolean(false);

    @Scheduled(cron = "15 * * * * *")
    @Transactional
    public void retirarPromocionesFinalizadas() {
        LocalDateTime now = LocalDateTime.now();
        boolean changed = retirarCombosVencidos(now);

        Sucursal ecommerceBranch = sucursalRepository
                .findFirstByPublicarEcommerceTrueAndDeletedAtIsNullAndEstadoAndTipoOrderByIdSucursalAsc(
                        ACTIVO,
                        SucursalTipo.VENTA)
                .orElse(null);
        if (ecommerceBranch == null) {
            if (missingBranchReported.compareAndSet(false, true)) {
                log.warn("No existe una sucursal VENTA activa publicada en ecommerce; no se retiraran promociones por stock");
            }
            if (changed) cacheInvalidationService.invalidate();
            return;
        }
        missingBranchReported.set(false);

        changed |= retirarOfertasSinStock(ecommerceBranch.getIdSucursal());
        changed |= retirarCombosSinStock(ecommerceBranch.getIdSucursal(), now);
        if (changed) cacheInvalidationService.invalidate();
    }

    private boolean retirarOfertasSinStock(Integer branchId) {
        List<ProductoVariante> configured = productoVarianteRepository
                .findByPrecioOfertaIsNotNullAndOfertaHastaAgotarStockTrueAndDeletedAtIsNull();
        Map<Integer, List<ProductoVariante>> byProduct = configured.stream()
                .filter(variant -> variant.getProducto() != null)
                .collect(Collectors.groupingBy(variant -> variant.getProducto().getIdProducto()));
        List<ProductoVariante> exhausted = new ArrayList<>();
        byProduct.forEach((productId, variants) -> {
            if (sucursalStockRepository.sumarStockEcommercePorProducto(branchId, productId) <= 0) {
                variants.forEach(variant -> {
                    clearOffer(variant);
                    exhausted.add(variant);
                });
            }
        });
        if (!exhausted.isEmpty()) productoVarianteRepository.saveAll(exhausted);
        return !exhausted.isEmpty();
    }

    private boolean retirarCombosVencidos(LocalDateTime now) {
        List<EcommercePromocionCombo> expired = comboRepository
                .findByDeletedAtIsNullAndFechaFinLessThanEqual(now);
        expired.forEach(combo -> softDelete(combo, now));
        if (!expired.isEmpty()) comboRepository.saveAll(expired);
        return !expired.isEmpty();
    }

    private boolean retirarCombosSinStock(Integer branchId, LocalDateTime now) {
        List<EcommercePromocionCombo> stockLimited = comboRepository
                .findByDeletedAtIsNullAndEstadoAndHastaAgotarStockTrue(ACTIVO);
        List<EcommercePromocionCombo> exhausted = stockLimited.stream()
                .filter(combo -> !hasRequiredStock(combo, branchId))
                .toList();
        exhausted.forEach(combo -> softDelete(combo, now));
        if (!exhausted.isEmpty()) comboRepository.saveAll(exhausted);
        return !exhausted.isEmpty();
    }

    private boolean hasRequiredStock(EcommercePromocionCombo combo, Integer branchId) {
        if (combo.getItems() == null || combo.getItems().isEmpty()) return false;
        for (EcommercePromocionComboItem item : combo.getItems()) {
            if (item.getProducto() == null || item.getCantidadRequerida() == null) return false;
            long stock = sucursalStockRepository.sumarStockEcommercePorProducto(
                    branchId,
                    item.getProducto().getIdProducto());
            if (stock < item.getCantidadRequerida()) return false;
        }
        return true;
    }

    private void clearOffer(ProductoVariante variant) {
        variant.setPrecioOferta(null);
        variant.setOfertaInicio(null);
        variant.setOfertaFin(null);
        variant.setOfertaHastaAgotarStock(false);
        variant.setUsuarioCreacion(null);
    }

    private void softDelete(EcommercePromocionCombo combo, LocalDateTime now) {
        combo.setEstado(INACTIVO);
        combo.setDeletedAt(now);
    }
}
