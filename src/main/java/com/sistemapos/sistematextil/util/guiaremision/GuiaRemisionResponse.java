package com.sistemapos.sistematextil.util.guiaremision;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.sistemapos.sistematextil.util.sunat.SunatEstado;

public record GuiaRemisionResponse(
        Integer idGuiaRemision,
        String numeroGuiaRemision,
        LocalDateTime fechaEmision,
        LocalDate fechaInicioTraslado,
        LocalDate fechaEntregaTransportista,
        String motivoTraslado,
        String descripcionMotivo,
        String modalidadTransporte,
        BigDecimal pesoBrutoTotal,
        String unidadPeso,
        Integer numeroBultos,
        String observaciones,
        String ubigeoPartida,
        String direccionPartida,
        Integer idSucursalPartida,
        String nombreSucursalPartida,
        String ubigeoLlegada,
        String direccionLlegada,
        Integer idSucursalLlegada,
        String nombreSucursalLlegada,
        String destinatarioTipoDoc,
        String destinatarioNroDoc,
        String destinatarioRazonSocial,
        String estado,
        Integer idUsuario,
        String nombreUsuario,
        Integer idSucursal,
        String nombreSucursal,
        List<GuiaRemisionDetalleResponse> detalles,
        List<GuiaRemisionDocumentoRelacionadoResponse> documentosRelacionados,
        List<ConductorResponse> conductores,
        List<TransportistaResponse> transportistas,
        List<VehiculoResponse> vehiculos,
        SunatEstado sunatEstado,
        String sunatCodigo,
        String sunatMensaje,
        String sunatHash,
        String sunatTicket,
        String sunatXmlNombre,
        String sunatZipNombre,
        String sunatCdrNombre,
        LocalDateTime sunatEnviadoAt,
        LocalDateTime sunatRespondidoAt,
        String message) {

    public record ConductorResponse(
            Integer idGuiaConductor,
            String tipoDocumento,
            String nroDocumento,
            String nombres,
            String apellidos,
            String licencia,
            Boolean esPrincipal) {
    }

    public record TransportistaResponse(
            Integer idGuiaTransportista,
            String transportistaTipoDoc,
            String transportistaNroDoc,
            String transportistaRazonSocial,
            String transportistaRegistroMtc) {
    }

    public record VehiculoResponse(
            Integer idGuiaVehiculo,
            String placa,
            Boolean esPrincipal) {
    }
}
