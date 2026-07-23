package com.sistemapos.sistematextil.services;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sistemapos.sistematextil.model.Turno;
import com.sistemapos.sistematextil.model.TurnoDia;
import com.sistemapos.sistematextil.repositories.TurnoRepository;
import com.sistemapos.sistematextil.util.paginacion.PagedResponse;
import com.sistemapos.sistematextil.util.turno.DiaSemana;
import com.sistemapos.sistematextil.util.turno.TurnoCreateRequest;
import com.sistemapos.sistematextil.util.turno.TurnoDiaHorarioRequest;
import com.sistemapos.sistematextil.util.turno.TurnoDiaHorarioResponse;
import com.sistemapos.sistematextil.util.turno.TurnoListItemResponse;
import com.sistemapos.sistematextil.util.turno.TurnoUpdateRequest;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TurnoService {

    private static final String ESTADO_ACTIVO = "ACTIVO";
    private static final String ESTADO_INACTIVO = "INACTIVO";
    private static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");

    private final TurnoRepository turnoRepository;

    @Value("${application.pagination.default-size:10}")
    private int defaultPageSize;

    public PagedResponse<TurnoListItemResponse> listarPaginado(int page) {
        validarPagina(page);
        PageRequest pageable = PageRequest.of(page, defaultPageSize, Sort.by("idTurno").ascending());
        Page<TurnoListItemResponse> turnos = turnoRepository.findByDeletedAtIsNull(pageable)
                .map(this::toListItemResponse);
        return PagedResponse.fromPage(turnos);
    }

    public PagedResponse<TurnoListItemResponse> buscarPaginado(String q, int page) {
        validarPagina(page);
        String term = normalizar(q);
        if (term == null) {
            return listarPaginado(page);
        }

        PageRequest pageable = PageRequest.of(page, defaultPageSize, Sort.by("idTurno").ascending());
        Page<TurnoListItemResponse> turnos = turnoRepository
                .findByDeletedAtIsNullAndNombreContainingIgnoreCase(term, pageable)
                .map(this::toListItemResponse);
        return PagedResponse.fromPage(turnos);
    }

    @Transactional
    public TurnoListItemResponse insertar(TurnoCreateRequest request) {
        String nombre = normalizarNombre(request.nombre());
        validarHorario(request.horaInicio(), request.horaFin());
        Set<DiaSemana> diasSemana = normalizarDiasSemana(request.dias());
        Map<DiaSemana, Turno.HorarioDia> horariosSemana = normalizarHorariosSemana(
                diasSemana,
                request.horaInicio(),
                request.horaFin(),
                request.horariosDias());

        Turno existente = turnoRepository.findByNombreIgnoreCase(nombre).orElse(null);
        if (existente != null) {
            if (existente.getDeletedAt() == null) {
                throw new RuntimeException("El turno '" + nombre + "' ya existe");
            }

            existente.setNombre(nombre);
            existente.setHoraInicio(request.horaInicio());
            existente.setHoraFin(request.horaFin());
            existente.setToleranciaMinutos(request.toleranciaMinutos() != null ? request.toleranciaMinutos() : 10);
            existente.setEstado(ESTADO_ACTIVO);
            existente.setDeletedAt(null);
            existente.sincronizarHorariosSemana(horariosSemana);
            return toListItemResponse(turnoRepository.save(existente));
        }

        Turno turno = new Turno();
        turno.setNombre(nombre);
        turno.setHoraInicio(request.horaInicio());
        turno.setHoraFin(request.horaFin());
        turno.setToleranciaMinutos(request.toleranciaMinutos() != null ? request.toleranciaMinutos() : 10);
        turno.setEstado(ESTADO_ACTIVO);
        turno.setDeletedAt(null);
        turno.sincronizarHorariosSemana(horariosSemana);
        return toListItemResponse(turnoRepository.save(turno));
    }

    @Transactional
    public TurnoListItemResponse actualizar(Integer idTurno, TurnoUpdateRequest request) {
        Turno turno = obtenerTurnoPorId(idTurno);
        String nombre = normalizarNombre(request.nombre());
        validarHorario(request.horaInicio(), request.horaFin());
        Set<DiaSemana> diasSemana = normalizarDiasSemana(request.dias());
        Map<DiaSemana, Turno.HorarioDia> horariosSemana = normalizarHorariosSemana(
                diasSemana,
                request.horaInicio(),
                request.horaFin(),
                request.horariosDias());

        turnoRepository.findByNombreIgnoreCaseAndDeletedAtIsNull(nombre).ifPresent(existente -> {
            if (!existente.getIdTurno().equals(idTurno)) {
                throw new RuntimeException("El turno '" + nombre + "' ya existe");
            }
        });

        turno.setNombre(nombre);
        turno.setHoraInicio(request.horaInicio());
        turno.setHoraFin(request.horaFin());
        if (request.toleranciaMinutos() != null) {
            turno.setToleranciaMinutos(request.toleranciaMinutos());
        }
        turno.setEstado(request.estado().trim().toUpperCase());
        turno.sincronizarHorariosSemana(horariosSemana);

        return toListItemResponse(turnoRepository.save(turno));
    }

    @Transactional
    public void eliminar(Integer idTurno) {
        Turno turno = obtenerTurnoPorId(idTurno);
        turno.setEstado(ESTADO_INACTIVO);
        turno.setDeletedAt(LocalDateTime.now());
        turnoRepository.save(turno);
    }

    public Turno resolverTurnoAsignable(Integer idTurno) {
        if (idTurno == null) {
            return null;
        }

        Turno turno = turnoRepository.findByIdTurnoAndDeletedAtIsNull(idTurno)
                .orElseThrow(() -> new RuntimeException("Turno no encontrado"));
        if (!ESTADO_ACTIVO.equalsIgnoreCase(turno.getEstado())) {
            throw new RuntimeException("El turno seleccionado esta inactivo");
        }
        return turno;
    }

    public void validarAccesoPorTurno(Turno turno) {
        if (turno == null) {
            return;
        }
        if (turno.getDeletedAt() != null || !ESTADO_ACTIVO.equalsIgnoreCase(turno.getEstado())) {
            throw new RuntimeException("El turno asignado al usuario esta inactivo");
        }

        LocalDateTime ahoraLima = LocalDateTime.now(ZONA_LIMA);
        LocalDate fechaHoy = ahoraLima.toLocalDate();
        LocalTime ahora = ahoraLima.toLocalTime();
        TurnoDia horarioHoy = obtenerHorarioPorDia(
                turno, DiaSemana.fromJavaDayOfWeek(fechaHoy.getDayOfWeek()));
        TurnoDia horarioAyer = obtenerHorarioPorDia(
                turno, DiaSemana.fromJavaDayOfWeek(fechaHoy.minusDays(1).getDayOfWeek()));
        if (!estaDentroDelHorario(turno, horarioHoy, ahora, false)
                && !estaDentroDelHorario(turno, horarioAyer, ahora, true)) {
            throw new RuntimeException("El usuario no puede ingresar fuera del horario de su turno");
        }
    }

    private Turno obtenerTurnoPorId(Integer idTurno) {
        return turnoRepository.findByIdTurnoAndDeletedAtIsNull(idTurno)
                .orElseThrow(() -> new RuntimeException("Turno con ID " + idTurno + " no encontrado"));
    }

    private void validarHorario(LocalTime horaInicio, LocalTime horaFin) {
        if (horaInicio == null || horaFin == null) {
            throw new RuntimeException("Ingrese horaInicio y horaFin");
        }
        if (horaFin.equals(horaInicio)) {
            throw new RuntimeException("horaInicio y horaFin deben ser diferentes");
        }
    }

    private void validarPagina(int page) {
        if (page < 0) {
            throw new RuntimeException("El parametro page debe ser mayor o igual a 0");
        }
    }

    private String normalizarNombre(String value) {
        String nombre = normalizar(value);
        if (nombre == null) {
            throw new RuntimeException("El nombre del turno es obligatorio");
        }
        return nombre;
    }

    private Set<DiaSemana> normalizarDiasSemana(List<DiaSemana> dias) {
        if (dias == null || dias.isEmpty()) {
            throw new RuntimeException("Ingrese al menos un dia");
        }

        EnumSet<DiaSemana> diasNormalizados = EnumSet.noneOf(DiaSemana.class);
        for (DiaSemana dia : dias) {
            if (dia == null) {
                throw new RuntimeException("Todos los dias enviados deben ser validos");
            }
            diasNormalizados.add(dia);
        }

        if (diasNormalizados.isEmpty()) {
            throw new RuntimeException("Ingrese al menos un dia");
        }

        return diasNormalizados;
    }

    private Map<DiaSemana, Turno.HorarioDia> normalizarHorariosSemana(
            Set<DiaSemana> diasSemana,
            LocalTime horaInicioBase,
            LocalTime horaFinBase,
            List<TurnoDiaHorarioRequest> horariosDias) {
        EnumMap<DiaSemana, Turno.HorarioDia> horarios = new EnumMap<>(DiaSemana.class);
        for (DiaSemana dia : diasSemana) {
            horarios.put(dia, new Turno.HorarioDia(horaInicioBase, horaFinBase));
        }

        if (horariosDias == null || horariosDias.isEmpty()) {
            return horarios;
        }

        EnumSet<DiaSemana> diasPersonalizados = EnumSet.noneOf(DiaSemana.class);
        for (TurnoDiaHorarioRequest horarioDia : horariosDias) {
            if (horarioDia == null) {
                throw new RuntimeException("Todos los horarios por dia deben ser validos");
            }
            DiaSemana dia = horarioDia.dia();
            if (dia == null) {
                throw new RuntimeException("Ingrese dia en cada horario por dia");
            }
            if (!diasSemana.contains(dia)) {
                throw new RuntimeException("El horario personalizado solo puede enviarse para dias habilitados");
            }
            if (!diasPersonalizados.add(dia)) {
                throw new RuntimeException("No se permiten horarios duplicados para el mismo dia");
            }
            validarHorario(horarioDia.horaInicio(), horarioDia.horaFin());
            horarios.put(dia, new Turno.HorarioDia(horarioDia.horaInicio(), horarioDia.horaFin()));
        }

        return horarios;
    }

    public List<DiaSemana> obtenerDias(Turno turno) {
        if (turno == null || turno.getDiasSemana() == null) {
            return List.of();
        }
        return turno.getDiasSemana().stream()
                .map(TurnoDia::getDiaSemana)
                .filter(Objects::nonNull)
                .sorted()
                .toList();
    }

    public List<TurnoDiaHorarioResponse> obtenerHorarios(Turno turno) {
        if (turno == null || turno.getDiasSemana() == null) {
            return List.of();
        }
        return turno.getDiasSemana().stream()
                .filter(turnoDia -> turnoDia.getDiaSemana() != null)
                .sorted((left, right) -> left.getDiaSemana().compareTo(right.getDiaSemana()))
                .map(turnoDia -> new TurnoDiaHorarioResponse(
                        turnoDia.getDiaSemana(),
                        turnoDia.getHoraInicio() != null ? turnoDia.getHoraInicio() : turno.getHoraInicio(),
                        turnoDia.getHoraFin() != null ? turnoDia.getHoraFin() : turno.getHoraFin()))
                .toList();
    }

    private TurnoDia obtenerHorarioPorDia(Turno turno, DiaSemana dia) {
        if (turno.getDiasSemana() == null) {
            return null;
        }
        return turno.getDiasSemana().stream()
                .filter(turnoDia -> dia.equals(turnoDia.getDiaSemana()))
                .findFirst()
                .orElse(null);
    }

    private boolean estaDentroDelHorario(Turno turno, TurnoDia horario, LocalTime ahora, boolean diaAnterior) {
        if (horario == null) {
            return false;
        }
        LocalTime inicio = horario.getHoraInicio() != null ? horario.getHoraInicio() : turno.getHoraInicio();
        LocalTime fin = horario.getHoraFin() != null ? horario.getHoraFin() : turno.getHoraFin();
        boolean nocturno = !fin.isAfter(inicio);
        if (diaAnterior) {
            return nocturno && !ahora.isAfter(fin);
        }
        return nocturno ? !ahora.isBefore(inicio) : !ahora.isBefore(inicio) && !ahora.isAfter(fin);
    }

    private String normalizar(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private TurnoListItemResponse toListItemResponse(Turno turno) {
        return new TurnoListItemResponse(
                turno.getIdTurno(),
                turno.getNombre(),
                turno.getHoraInicio(),
                turno.getHoraFin(),
                turno.getToleranciaMinutos(),
                obtenerDias(turno),
                obtenerHorarios(turno),
                turno.getEstado(),
                turno.getFechaCreacion());
    }
}
