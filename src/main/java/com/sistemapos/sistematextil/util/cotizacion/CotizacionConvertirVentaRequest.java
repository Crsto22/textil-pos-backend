package com.sistemapos.sistematextil.util.cotizacion;

import java.util.List;

import com.sistemapos.sistematextil.util.venta.VentaPagoCreateItem;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record CotizacionConvertirVentaRequest(
        @Size(max = 20, message = "El tipoComprobante no debe superar 20 caracteres")
        String tipoComprobante,

        @NotEmpty(message = "Ingrese al menos un pago")
        @Valid
        List<VentaPagoCreateItem> pagos
) {
}
