package com.sistemapos.sistematextil.util.cotizacion;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record CotizacionCreateRequest(
        Integer idSucursal,

        Integer idCliente,

        @Size(max = 10, message = "La serie no debe superar 10 caracteres")
        String serie,

        Integer correlativo,

        LocalDateTime fechaVencimiento,

        @DecimalMin(value = "0.00", message = "El igvPorcentaje no puede ser negativo")
        Double igvPorcentaje,

        @DecimalMin(value = "0.00", message = "El descuentoTotal no puede ser negativo")
        Double descuentoTotal,

        @Size(max = 10, message = "El tipoDescuento no debe superar 10 caracteres")
        String tipoDescuento,

        @Size(max = 20, message = "El estado no debe superar 20 caracteres")
        String estado,

        @Size(max = 500, message = "La observacion no debe superar 500 caracteres")
        String observacion,

        @NotEmpty(message = "Ingrese al menos un item en detalles")
        @Valid
        List<CotizacionDetalleCreateItem> detalles
) {
}
