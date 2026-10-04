package com.sistemapos.sistematextil.services;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sistemapos.sistematextil.model.EcommercePromocionCombo;
import com.sistemapos.sistematextil.model.EcommercePromocionComboItem;
import com.sistemapos.sistematextil.model.Producto;
import com.sistemapos.sistematextil.model.ProductoVariante;
import com.sistemapos.sistematextil.model.Sucursal;
import com.sistemapos.sistematextil.model.SucursalTipo;
import com.sistemapos.sistematextil.repositories.EcommercePromocionComboRepository;
import com.sistemapos.sistematextil.repositories.ProductoVarianteRepository;
import com.sistemapos.sistematextil.repositories.SucursalRepository;
import com.sistemapos.sistematextil.repositories.SucursalStockRepository;

@ExtendWith(MockitoExtension.class)
class EcommercePromotionLifecycleServiceTest {

    @Mock
    private ProductoVarianteRepository productoVarianteRepository;
    @Mock
    private EcommercePromocionComboRepository comboRepository;
    @Mock
    private SucursalRepository sucursalRepository;
    @Mock
    private SucursalStockRepository sucursalStockRepository;
    @Mock
    private EcommerceCacheInvalidationService cacheInvalidationService;

    private EcommercePromotionLifecycleService service;

    @BeforeEach
    void setUp() {
        service = new EcommercePromotionLifecycleService(
                productoVarianteRepository,
                comboRepository,
                sucursalRepository,
                sucursalStockRepository,
                cacheInvalidationService);
        when(comboRepository.findByDeletedAtIsNullAndFechaFinLessThanEqual(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());
    }

    @Test
    void clearsStockLimitedOfferWhenEcommerceProductRunsOut() {
        Sucursal branch = new Sucursal();
        branch.setIdSucursal(4);
        branch.setTipo(SucursalTipo.VENTA);
        Producto product = new Producto();
        product.setIdProducto(18);
        ProductoVariante variant = new ProductoVariante();
        variant.setProducto(product);
        variant.setPrecioOferta(59.0);
        variant.setOfertaInicio(LocalDateTime.now().minusDays(1));
        variant.setOfertaHastaAgotarStock(true);

        when(sucursalRepository.findFirstByPublicarEcommerceTrueAndDeletedAtIsNullAndEstadoAndTipoOrderByIdSucursalAsc(
                "ACTIVO", SucursalTipo.VENTA)).thenReturn(Optional.of(branch));
        when(productoVarianteRepository.findByPrecioOfertaIsNotNullAndOfertaHastaAgotarStockTrueAndDeletedAtIsNull())
                .thenReturn(List.of(variant));
        when(sucursalStockRepository.sumarStockEcommercePorProducto(4, 18)).thenReturn(0L);
        when(comboRepository.findByDeletedAtIsNullAndEstadoAndHastaAgotarStockTrue("ACTIVO"))
                .thenReturn(List.of());

        service.retirarPromocionesFinalizadas();

        assertNull(variant.getPrecioOferta());
        assertNull(variant.getOfertaInicio());
        assertFalse(variant.getOfertaHastaAgotarStock());
        verify(productoVarianteRepository).saveAll(List.of(variant));
        verify(cacheInvalidationService).invalidate();
    }

    @Test
    void expiresComboEvenWhenNoEcommerceBranchExists() {
        EcommercePromocionCombo expired = new EcommercePromocionCombo();
        expired.setEstado("ACTIVO");
        when(comboRepository.findByDeletedAtIsNullAndFechaFinLessThanEqual(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(expired));
        when(sucursalRepository.findFirstByPublicarEcommerceTrueAndDeletedAtIsNullAndEstadoAndTipoOrderByIdSucursalAsc(
                "ACTIVO", SucursalTipo.VENTA)).thenReturn(Optional.empty());

        service.retirarPromocionesFinalizadas();

        assertFalse("ACTIVO".equals(expired.getEstado()));
        verify(comboRepository).saveAll(List.of(expired));
        verify(productoVarianteRepository, never()).saveAll(anyList());
        verify(cacheInvalidationService).invalidate();
    }

    @Test
    void softDeletesStockLimitedComboWhenOneProductCannotMeetItsRule() {
        Sucursal branch = new Sucursal();
        branch.setIdSucursal(4);
        Producto product = new Producto();
        product.setIdProducto(22);
        EcommercePromocionComboItem item = new EcommercePromocionComboItem();
        item.setProducto(product);
        item.setCantidadRequerida(2);
        EcommercePromocionCombo combo = new EcommercePromocionCombo();
        combo.setEstado("ACTIVO");
        combo.setHastaAgotarStock(true);
        combo.addItem(item);

        when(sucursalRepository.findFirstByPublicarEcommerceTrueAndDeletedAtIsNullAndEstadoAndTipoOrderByIdSucursalAsc(
                "ACTIVO", SucursalTipo.VENTA)).thenReturn(Optional.of(branch));
        when(productoVarianteRepository.findByPrecioOfertaIsNotNullAndOfertaHastaAgotarStockTrueAndDeletedAtIsNull())
                .thenReturn(List.of());
        when(comboRepository.findByDeletedAtIsNullAndEstadoAndHastaAgotarStockTrue("ACTIVO"))
                .thenReturn(List.of(combo));
        when(sucursalStockRepository.sumarStockEcommercePorProducto(4, 22)).thenReturn(1L);

        service.retirarPromocionesFinalizadas();

        assertFalse("ACTIVO".equals(combo.getEstado()));
        verify(comboRepository).saveAll(List.of(combo));
        verify(cacheInvalidationService).invalidate();
    }
}
