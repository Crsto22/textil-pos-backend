package com.sistemapos.sistematextil.model;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.sistemapos.sistematextil.util.turno.DiaSemana;

import com.sistemapos.sistematextil.model.converter.EstadoActivoConverter;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "turno")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class Turno {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_turno")
    private Integer idTurno;

    @Column(nullable = false, length = 80)
    private String nombre;

    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;

    @Column(name = "hora_fin", nullable = false)
    private LocalTime horaFin;

    @Column(name = "tolerancia_minutos", nullable = false)
    private Integer toleranciaMinutos = 10;

    @Convert(converter = EstadoActivoConverter.class)
    @Column(name = "activo", nullable = false)
    private String estado = "ACTIVO";

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @OneToMany(mappedBy = "turno", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<TurnoDia> diasSemana = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (this.fechaCreacion == null) {
            this.fechaCreacion = now;
        }
        this.updatedAt = now;
        if (this.estado == null || this.estado.isBlank()) {
            this.estado = "ACTIVO";
        }
        if (this.toleranciaMinutos == null) {
            this.toleranciaMinutos = 10;
        }
        this.deletedAt = null;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void addDiaSemana(TurnoDia diaSemana) {
        if (diaSemana == null) {
            return;
        }
        diaSemana.setTurno(this);
        this.diasSemana.add(diaSemana);
    }

    public void sincronizarHorariosSemana(Map<DiaSemana, HorarioDia> horariosObjetivo) {
        Map<DiaSemana, HorarioDia> horariosNormalizados = horariosObjetivo == null
                ? Map.of()
                : horariosObjetivo;

        diasSemana.removeIf(turnoDia -> turnoDia.getDiaSemana() == null
                || !horariosNormalizados.containsKey(turnoDia.getDiaSemana()));

        for (TurnoDia turnoDia : diasSemana) {
            HorarioDia horario = horariosNormalizados.get(turnoDia.getDiaSemana());
            if (horario != null) {
                turnoDia.setHoraInicio(horario.horaInicio());
                turnoDia.setHoraFin(horario.horaFin());
            }
        }

        for (Map.Entry<DiaSemana, HorarioDia> entry : horariosNormalizados.entrySet()) {
            DiaSemana dia = entry.getKey();
            boolean existe = diasSemana.stream().anyMatch(turnoDia -> dia.equals(turnoDia.getDiaSemana()));
            if (existe) {
                continue;
            }
            TurnoDia turnoDia = new TurnoDia();
            turnoDia.setDiaSemana(dia);
            turnoDia.setHoraInicio(entry.getValue().horaInicio());
            turnoDia.setHoraFin(entry.getValue().horaFin());
            addDiaSemana(turnoDia);
        }
    }

    public record HorarioDia(LocalTime horaInicio, LocalTime horaFin) {
    }
}
