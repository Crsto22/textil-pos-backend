package com.sistemapos.sistematextil.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.sistemapos.sistematextil.model.MetodoPagoConfig;
import com.sistemapos.sistematextil.model.MetodoPagoCuenta;
import com.sistemapos.sistematextil.repositories.MetodoPagoConfigRepository;
import com.sistemapos.sistematextil.util.metodopago.MetodoPagoConfigUpdateRequest;
import com.sistemapos.sistematextil.util.metodopago.MetodoPagoCuentaRequest;

class MetodoPagoConfigServiceTest {

    private final MetodoPagoConfigRepository repository = mock(MetodoPagoConfigRepository.class);
    private final MetodoPagoConfigService service = new MetodoPagoConfigService(repository);

    @Test
    void actualizarReutilizaCuentaExistenteSinEliminarla() {
        MetodoPagoConfig metodo = metodoYape();
        MetodoPagoCuenta cuentaOriginal = metodo.getCuentas().getFirst();
        when(repository.findByIdMetodoPagoAndDeletedAtIsNull(1)).thenReturn(Optional.of(metodo));
        when(repository.findAllByNombreNormalizado("YAPE")).thenReturn(List.of(metodo));
        when(repository.save(any(MetodoPagoConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.actualizar(1, new MetodoPagoConfigUpdateRequest(
                "Yape", "Pago movil", "ACTIVO",
                List.of(new MetodoPagoCuentaRequest("932899985", "Kiments", null))));

        assertSame(cuentaOriginal, metodo.getCuentas().getFirst());
        assertTrue(cuentaOriginal.getActivo());
        assertEquals("Kiments", cuentaOriginal.getTitular());
        assertEquals(1, response.cuentas().size());
    }

    @Test
    void retirarCuentaLaInactivaSinBorrarElRegistroHistorico() {
        MetodoPagoConfig metodo = metodoYape();
        MetodoPagoCuenta cuentaOriginal = metodo.getCuentas().getFirst();
        when(repository.findByIdMetodoPagoAndDeletedAtIsNull(1)).thenReturn(Optional.of(metodo));
        when(repository.findAllByNombreNormalizado("YAPE")).thenReturn(List.of(metodo));
        when(repository.save(any(MetodoPagoConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.actualizar(1, new MetodoPagoConfigUpdateRequest(
                "Yape", "Pago movil", "ACTIVO", List.of()));

        assertFalse(cuentaOriginal.getActivo());
        assertEquals(1, metodo.getCuentas().size());
        assertTrue(response.cuentas().isEmpty());
    }

    private MetodoPagoConfig metodoYape() {
        MetodoPagoConfig metodo = new MetodoPagoConfig();
        metodo.setIdMetodoPago(1);
        metodo.setNombre("YAPE");
        metodo.setEstado("ACTIVO");
        metodo.setCuentas(new ArrayList<>());
        MetodoPagoCuenta cuenta = new MetodoPagoCuenta();
        cuenta.setIdMetodoPagoCuenta(10);
        cuenta.setNumeroCuenta("932899985");
        cuenta.setActivo(true);
        metodo.addCuenta(cuenta);
        return metodo;
    }
}
