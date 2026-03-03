package com.sistemapos.sistematextil.util.venta;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record VentaCreateRequest(
        Integer idSucursal,

        Integer idCliente,

        @Size(max = 10, message = "El tipoComprobante no debe superar 10 caracteres")
        String tipoComprobante,

        @Size(max = 10, message = "La serie no debe superar 10 caracteres")
        String serie,

        Integer correlativo,

        @DecimalMin(value = "0.00", message = "El igvPorcentaje no puede ser negativo")
        Double igvPorcentaje,

        @DecimalMin(value = "0.00", message = "El descuentoTotal no puede ser negativo")
        Double descuentoTotal,

        @Size(max = 10, message = "El tipoDescuento no debe superar 10 caracteres")
        String tipoDescuento,

        @NotEmpty(message = "Ingrese al menos un item en detalles")
        @Valid
        List<VentaDetalleCreateItem> detalles,

        @NotEmpty(message = "Ingrese al menos un pago")
        @Valid
        List<VentaPagoCreateItem> pagos
) {
}
