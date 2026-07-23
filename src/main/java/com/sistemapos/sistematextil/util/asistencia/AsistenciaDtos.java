package com.sistemapos.sistematextil.util.asistencia;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import com.sistemapos.sistematextil.util.paginacion.PagedResponse;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AsistenciaDtos {

    private AsistenciaDtos() {
    }

    public record TrabajadorRequest(
            @NotBlank(message = "Ingrese codigo ZKTeco")
            @Pattern(regexp = "\\d{1,24}", message = "El codigo ZKTeco debe contener solo numeros")
            String codigoZkteco,
            @NotBlank(message = "Ingrese DNI")
            @Pattern(regexp = "\\d{8}", message = "El DNI debe contener 8 digitos")
            String dni,
            @NotBlank(message = "Ingrese nombres") @Size(max = 100) String nombres,
            @NotBlank(message = "Ingrese apellidos") @Size(max = 100) String apellidos,
            Integer idSucursal,
            Integer idTurno,
            @NotNull(message = "Seleccione cargo") Integer idCargo,
            Boolean rotativo,
            @Pattern(regexp = "ACTIVO|INACTIVO", message = "Estado permitido: ACTIVO o INACTIVO") String estado,
            Integer idUsuario) {
    }

    public record TrabajadorResponse(
            Integer idTrabajador,
            String codigoZkteco,
            String dni,
            String nombres,
            String apellidos,
            String estado,
            Integer idSucursal,
            String sucursal,
            Integer idTurno,
            String turno,
            Integer idCargo,
            String cargo,
            String cargoEstado,
            boolean rotativo,
            Integer idUsuario,
            String usuarioNombre,
            String usuarioCorreo,
            String usuarioRol,
            String usuarioEstado,
            LocalDateTime fechaCreacion) {
    }

    public record CargoRequest(
            @NotBlank(message = "Ingrese nombre del cargo")
            @Size(max = 100, message = "El nombre del cargo no debe superar 100 caracteres") String nombre) {
    }

    public record CargoEstadoRequest(
            @NotBlank(message = "Ingrese estado")
            @Pattern(regexp = "ACTIVO|INACTIVO", message = "Estado permitido: ACTIVO o INACTIVO") String estado) {
    }

    public record CargoResponse(
            Integer idCargo,
            String nombre,
            String estado,
            LocalDateTime fechaCreacion) {
    }

    public record DispositivoRequest(
            @NotBlank(message = "Ingrese numero de serie") @Size(max = 80) String numeroSerie,
            @NotBlank(message = "Ingrese nombre") @Size(max = 100) String nombre,
            @NotNull(message = "Ingrese sucursal") Integer idSucursal,
            @Pattern(regexp = "ACTIVO|INACTIVO", message = "Estado permitido: ACTIVO o INACTIVO") String estado) {
    }

    public record DispositivoResponse(
            Integer idDispositivo,
            String numeroSerie,
            String nombre,
            String estado,
            Integer idSucursal,
            String sucursal,
            LocalDateTime ultimaConexion,
            LocalDateTime fechaCreacion) {
    }

    public record MarcacionResponse(
            Long idMarcacion,
            Integer idDispositivo,
            String dispositivo,
            Integer idSucursal,
            String sucursal,
            Integer idTrabajador,
            String trabajador,
            String codigoZkteco,
            LocalDateTime fechaHora,
            String tipoMarcacion,
            String tipoVerificacion,
            LocalDateTime recibidoAt,
            String origen,
            String tipoEvento,
            String estadoCalculo,
            String motivoRegistro,
            String usuarioRegistro,
            LocalDateTime anuladaAt,
            String motivoAnulacion,
            String usuarioAnula) {
    }

    public record MarcacionManualRequest(
            @NotNull(message = "Seleccione trabajador") Integer idTrabajador,
            @NotNull(message = "Seleccione sucursal") Integer idSucursal,
            @NotNull(message = "Ingrese fecha y hora") LocalDateTime fechaHora,
            @NotBlank(message = "Seleccione tipo de marcacion")
            @Pattern(regexp = "ENTRADA|SALIDA", message = "Tipo permitido: ENTRADA o SALIDA") String tipoEvento,
            @NotBlank(message = "Ingrese motivo")
            @Size(min = 10, max = 255, message = "El motivo debe tener entre 10 y 255 caracteres") String motivo) {
    }

    public record AnularMarcacionRequest(
            @NotBlank(message = "Ingrese motivo")
            @Size(min = 10, max = 255, message = "El motivo debe tener entre 10 y 255 caracteres") String motivo) {
    }

    public record ResumenResponse(
            Integer idTrabajador,
            String codigoZkteco,
            String trabajador,
            Integer idSucursal,
            String sucursal,
            Integer idTurno,
            String turno,
            LocalDate fecha,
            LocalTime horaProgramadaEntrada,
            LocalTime horaProgramadaSalida,
            LocalDateTime primeraMarcacion,
            LocalDateTime ultimaMarcacion,
            String estado,
            long minutosTardanza,
            long minutosTrabajados,
            long segundosTrabajados,
            int cantidadMarcaciones,
            boolean salidaAnticipada,
            long minutosSalidaAnticipada,
            boolean rotativo,
            List<SucursalMarcacionResponse> sucursalesMarcacion,
            List<SesionAsistenciaResponse> sesiones) {
    }

    public record SucursalMarcacionResponse(Integer idSucursal, String sucursal) {
    }

    public record SesionAsistenciaResponse(
            Integer idSucursal,
            String sucursal,
            LocalDateTime entrada,
            LocalDateTime salida,
            String dispositivoEntrada,
            String dispositivoSalida,
            Integer idSucursalSalida,
            String sucursalSalida,
            long minutosTrabajados,
            long segundosTrabajados,
            boolean completa) {
    }

    public record ResumenSemanalResponse(
            Integer idTrabajador,
            String codigoZkteco,
            String trabajador,
            Integer idSucursal,
            String sucursal,
            Integer idTurno,
            String turno,
            boolean rotativo,
            List<ResumenResponse> dias) {
    }

    public record AnalisisResponse(
            LocalDate desde,
            LocalDate hasta,
            AnalisisIndicadores indicadores,
            List<AnalisisEstado> distribucion,
            List<AnalisisEvolucion> evolucion,
            List<AnalisisSucursal> horasPorSucursal,
            PagedResponse<AnalisisTrabajador> trabajadores) {
    }

    public record AnalisisIndicadores(
            long trabajadoresEvaluados,
            long diasAsistidos,
            long faltas,
            long tardanzas,
            long salidasAnticipadas,
            long registrosIncompletos,
            long minutosTrabajados,
            long segundosTrabajados,
            double porcentajeAsistencia) {
    }

    public record AnalisisEstado(String estado, long cantidad) {
    }

    public record AnalisisEvolucion(
            LocalDate fecha,
            long asistencias,
            long tardanzas,
            long faltas,
            long registrosIncompletos) {
    }

    public record AnalisisSucursal(
            Integer idSucursal, String sucursal, long minutosTrabajados, long segundosTrabajados) {
    }

    public record AnalisisTrabajador(
            Integer idTrabajador,
            String codigoZkteco,
            String trabajador,
            String sucursal,
            long faltas,
            long tardanzas,
            long salidasAnticipadas,
            long registrosIncompletos,
            long minutosTrabajados,
            long segundosTrabajados,
            long totalIncidencias) {
    }

}
