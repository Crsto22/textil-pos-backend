package com.sistemapos.sistematextil.model;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class Sucursal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idSucursal;

    @NotBlank(message = "El nombre es obligatorio")
    @Size(min = 3, max = 100)
    private String nombre;

    private String descripcion;

    @NotBlank(message = "El correo es obligatorio")
    @Email(message = "Correo no válido")
    private String correo;

    @NotBlank(message = "El estado es obligatorio")
    private String estado;

    @Column(name = "fecha_creacion")
    private LocalDateTime fechaCreacion;

    // Cambia FetchType.LAZY por FetchType.EAGER
@ManyToOne(fetch = FetchType.EAGER)
@JoinColumn(name = "id_empresa", nullable = false)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
private Empresa empresa;

}