package com.sistemapos.sistematextil.util.guiaremision;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Request para crear Guia de Remision Remitente (09) con motivos operativos
 * permitidos por SUNAT para este modulo: 01, 02, 03, 04, 05, 06, 07, 13, 14 y 17.
 */
public record GuiaRemisionCreateRequest(

        @Size(max = 4, message = "La serie no debe superar 4 caracteres")
        String serie,

        @Pattern(regexp = "01|02|03|04|05|06|07|13|14|17", message = "motivoTraslado permitido: 01, 02, 03, 04, 05, 06, 07, 13, 14 o 17")
        String motivoTraslado,

        @Size(max = 255, message = "La descripcionMotivo no debe superar 255 caracteres")
        String descripcionMotivo,

        Integer idSucursalPartida,

        Integer idSucursalLlegada,

        @Size(max = 255, message = "La direccionPartida no debe superar 255 caracteres")
        String direccionPartida,

        @Size(max = 255, message = "La direccionLlegada no debe superar 255 caracteres")
        String direccionLlegada,

        @Size(max = 1, message = "destinatarioTipoDoc no debe superar 1 caracter")
        String destinatarioTipoDoc,

        @Size(max = 20, message = "destinatarioNroDoc no debe superar 20 caracteres")
        String destinatarioNroDoc,

        @Size(max = 255, message = "destinatarioRazonSocial no debe superar 255 caracteres")
        String destinatarioRazonSocial,

        @NotNull(message = "La fechaInicioTraslado es obligatoria")
        LocalDate fechaInicioTraslado,

        @NotNull(message = "La modalidadTransporte es obligatoria (01=publica, 02=privada)")
        @Pattern(regexp = "01|02", message = "modalidadTransporte debe ser 01 (publica) o 02 (privada)")
        String modalidadTransporte,

        @NotNull(message = "El pesoBrutoTotal es obligatorio")
        @DecimalMin(value = "0.001", message = "El pesoBrutoTotal debe ser mayor a 0")
        BigDecimal pesoBrutoTotal,

        @Size(min = 3, max = 3, message = "La unidadPeso debe tener 3 caracteres")
        String unidadPeso,

        Integer numeroBultos,

        @Size(max = 500, message = "Las observaciones no deben superar 500 caracteres")
        String observaciones,

        @Pattern(regexp = "\\d{6}", message = "ubigeoPartida debe tener 6 digitos")
        String ubigeoPartida,

        @Pattern(regexp = "\\d{6}", message = "ubigeoLlegada debe tener 6 digitos")
        String ubigeoLlegada,

        @NotEmpty(message = "Debe incluir al menos un detalle de bienes a trasladar")
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

        List<@Positive(message = "idsCatalogoVehiculos debe contener IDs positivos") Integer> idsCatalogoVehiculos,

        Boolean emitirDirectamente) {
}
