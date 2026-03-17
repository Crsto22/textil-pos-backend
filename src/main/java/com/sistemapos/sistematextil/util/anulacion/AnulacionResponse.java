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
public class AnulacionResponse {

    private Integer idVenta;
    private String serieCorrelativo;
    private String tipoComprobante;
    private String estadoVenta;
    private String anulacionTipo;
    private String anulacionMotivo;
    private LocalDateTime anulacionFecha;
    private String sunatEstado;
    private String sunatMensaje;
    private String sunatTicket;
    private Integer idComunicacionBaja;
    private Integer idNotaCredito;
    private boolean stockDevuelto;
    private String message;
}
