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
@Table(name = "tallas")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class Talla {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "talla_id")
    private Integer idTalla;

    @NotBlank(message = "El nombre de la talla es obligatorio")
    private String nombre; // Ej: S, M, L, XL, 32, 34

    private String estado = "ACTIVO";


}