package com.sistemapos.sistematextil.services;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
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
import com.sistemapos.sistematextil.util.turno.TurnoListItemResponse;
import com.sistemapos.sistematextil.util.turno.TurnoUpdateRequest;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TurnoService {

    private static final String ESTADO_ACTIVO = "ACTIVO";
    private static final String ESTADO_INACTIVO = "INACTIVO";

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

        Turno existente = turnoRepository.findByNombreIgnoreCase(nombre).orElse(null);
        if (existente != null) {
            if (existente.getDeletedAt() == null) {
                throw new RuntimeException("El turno '" + nombre + "' ya existe");
            }

            existente.setNombre(nombre);
            existente.setHoraInicio(request.horaInicio());
            existente.setHoraFin(request.horaFin());
            existente.setEstado(ESTADO_ACTIVO);
            existente.setDeletedAt(null);
            existente.sincronizarDiasSemana(diasSemana);
            return toListItemResponse(turnoRepository.save(existente));
        }

        Turno turno = new Turno();
        turno.setNombre(nombre);
        turno.setHoraInicio(request.horaInicio());
        turno.setHoraFin(request.horaFin());
        turno.setEstado(ESTADO_ACTIVO);
        turno.setDeletedAt(null);
        turno.sincronizarDiasSemana(diasSemana);
        return toListItemResponse(turnoRepository.save(turno));
    }

    @Transactional
    public TurnoListItemResponse actualizar(Integer idTurno, TurnoUpdateRequest request) {
        Turno turno = obtenerTurnoPorId(idTurno);
        String nombre = normalizarNombre(request.nombre());
        validarHorario(request.horaInicio(), request.horaFin());
        Set<DiaSemana> diasSemana = normalizarDiasSemana(request.dias());

        turnoRepository.findByNombreIgnoreCaseAndDeletedAtIsNull(nombre).ifPresent(existente -> {
            if (!existente.getIdTurno().equals(idTurno)) {
                throw new RuntimeException("El turno '" + nombre + "' ya existe");
            }
        });

        turno.setNombre(nombre);
        turno.setHoraInicio(request.horaInicio());
        turno.setHoraFin(request.horaFin());
        turno.setEstado(request.estado().trim().toUpperCase());
        turno.sincronizarDiasSemana(diasSemana);

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

        List<DiaSemana> diasSemana = obtenerDias(turno);
        if (!diasSemana.isEmpty()) {
            DiaSemana hoy = DiaSemana.fromJavaDayOfWeek(LocalDate.now().getDayOfWeek());
            if (!diasSemana.contains(hoy)) {
                throw new RuntimeException("El usuario no puede ingresar en un dia no habilitado para su turno");
            }
        }

        LocalTime ahora = LocalTime.now();
        if (ahora.isBefore(turno.getHoraInicio()) || ahora.isAfter(turno.getHoraFin())) {
            throw new RuntimeException("El usuario no puede ingresar fuera del horario de su turno");
        }
    }

    private Turno obtenerTurnoPorId(Integer idTurno) {
        return turnoRepository.findByIdTurnoAndDeletedAtIsNull(idTurno)
                .orElseThrow(() -> new RuntimeException("Turno con ID " + idTurno + " no encontrado"));
    }

    private void validarHorario(LocalTime horaInicio, LocalTime horaFin) {
        if (horaFin.equals(horaInicio) || horaFin.isBefore(horaInicio)) {
            throw new RuntimeException("horaFin debe ser mayor que horaInicio");
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
                obtenerDias(turno),
                turno.getEstado(),
                turno.getFechaCreacion());
    }
}
