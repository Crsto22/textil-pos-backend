package com.sistemapos.sistematextil.util.anulacion;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnulacionInfoResponse {

    private Integer idVenta;
    private String serieCorrelativo;
    private String tipoComprobante;
    private String estadoVenta;
    private String sunatEstadoVenta;
    private LocalDateTime fechaEmision;
    private long diasDesdeEmision;
    private boolean puedeAnularse;
    private String metodoAnulacion;
    private String razonNoAnulable;
}
