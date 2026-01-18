package com.sistemapos.sistematextil.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "colores")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class Color {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "color_id")
    private Integer idColor;

    @NotBlank(message = "El nombre del color es obligatorio")
    private String nombre; // Ej: Rojo, Azul, Negro, Blanco

    @NotBlank(message= "El código del color es obligatorio")
    private String codigo;

    private String estado = "ACTIVO";
}