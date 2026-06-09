package com.sistemapos.sistematextil.util.guiaremision;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/**
 * Request para editar un borrador de Guia de Remision Remitente (09).
 * Todos los campos son opcionales: solo se actualizan los enviados.
 */
public record GuiaRemisionUpdateRequest(

        @Pattern(regexp = "01|02|03|04|05|06|07|13|14|17", message = "motivoTraslado permitido: 01, 02, 03, 04, 05, 06, 07, 13, 14 o 17")
        String motivoTraslado,

        String descripcionMotivo,

        Integer idSucursalPartida,

        Integer idSucursalLlegada,

        String direccionPartida,

        String direccionLlegada,

        String destinatarioTipoDoc,

        String destinatarioNroDoc,

        String destinatarioRazonSocial,

        LocalDate fechaInicioTraslado,

        LocalDate fechaEntregaTransportista,

        @Pattern(regexp = "01|02", message = "modalidadTransporte debe ser 01 (publica) o 02 (privada)")
        String modalidadTransporte,

        BigDecimal pesoBrutoTotal,

        String unidadPeso,

        Integer numeroBultos,

        String observaciones,

        @Pattern(regexp = "\\d{6}", message = "ubigeoPartida debe tener 6 digitos")
        String ubigeoPartida,

        @Pattern(regexp = "\\d{6}", message = "ubigeoLlegada debe tener 6 digitos")
        String ubigeoLlegada,

        @Valid
        List<GuiaRemisionDetalleCreateItem> detalles,

        @Valid
        List<GuiaRemisionDocumentoRelacionadoRequest> documentosRelacionados,

        @Valid
        List<GuiaRemisionConductorRequest> conductores,

        List<@Positive(message = "idsCatalogoConductores debe contener IDs positivos") Integer> idsCatalogoConductores,

        @Valid
        List<GuiaRemisionTransportistaRequest> transportistas,

        List<@Positive(message = "idsCatalogoTransportistas debe contener IDs positivos") Integer> idsCatalogoTransportistas,

        @Valid
        List<GuiaRemisionVehiculoRequest> vehiculos,

        List<@Positive(message = "idsCatalogoVehiculos debe contener IDs positivos") Integer> idsCatalogoVehiculos) {
}
