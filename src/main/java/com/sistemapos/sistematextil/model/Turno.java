package com.sistemapos.sistematextil.model;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

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

    public void sincronizarDiasSemana(Set<DiaSemana> diasObjetivo) {
        Set<DiaSemana> diasNormalizados = diasObjetivo == null
                ? EnumSet.noneOf(DiaSemana.class)
                : EnumSet.copyOf(diasObjetivo);

        diasSemana.removeIf(turnoDia -> turnoDia.getDiaSemana() == null
                || !diasNormalizados.contains(turnoDia.getDiaSemana()));

        EnumSet<DiaSemana> diasExistentes = EnumSet.noneOf(DiaSemana.class);
        for (TurnoDia turnoDia : diasSemana) {
            if (turnoDia.getDiaSemana() != null) {
                diasExistentes.add(turnoDia.getDiaSemana());
            }
        }

        for (DiaSemana dia : diasNormalizados) {
            if (diasExistentes.contains(dia)) {
                continue;
            }
            TurnoDia turnoDia = new TurnoDia();
            turnoDia.setDiaSemana(dia);
            addDiaSemana(turnoDia);
        }
    }
}
