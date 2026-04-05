package com.sistemapos.sistematextil.util.venta;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VentaCreateRequest(
        Integer idSucursal,

        Integer idCliente,

        @Size(max = 20, message = "El tipoComprobante no debe superar 20 caracteres")
        String tipoComprobante,

        @NotBlank(message = "La serie es obligatoria")
        @Size(max = 10, message = "La serie no debe superar 10 caracteres")
        String serie,

        Integer correlativo,

        @Size(min = 3, max = 3, message = "La moneda debe tener 3 caracteres")
        String moneda,

        @Size(max = 10, message = "La formaPago no debe superar 10 caracteres")
        String formaPago,

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
