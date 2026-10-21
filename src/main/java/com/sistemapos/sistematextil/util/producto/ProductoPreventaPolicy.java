package com.sistemapos.sistematextil.util.producto;

import java.time.LocalDate;
import java.time.ZoneId;

import com.sistemapos.sistematextil.model.Producto;

/** Reglas comerciales compartidas para productos en preventa. */
public final class ProductoPreventaPolicy {
    public static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Lima");

    private ProductoPreventaPolicy() {
    }

    public static boolean activa(Producto producto) {
        return producto != null
                && Boolean.TRUE.equals(producto.getPreventa());
    }

    /**
     * Mantiene la fecha configurada mientras sea futura. Cuando se alcanza o queda
     * atrasada, expone siempre el dia siguiente en la zona horaria de Kiments.
     */
    public static LocalDate fechaEnvioVigente(Producto producto) {
        if (!activa(producto)) return null;
        LocalDate manana = LocalDate.now(BUSINESS_ZONE).plusDays(1);
        LocalDate configurada = producto.getFechaEnvioPreventa();
        if (configurada == null) return manana;
        return configurada.isBefore(manana) ? manana : configurada;
    }
}
