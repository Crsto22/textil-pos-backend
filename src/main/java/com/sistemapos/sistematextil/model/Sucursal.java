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
import jakarta.persistence.Table;
import jakarta.persistence.Convert;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import com.sistemapos.sistematextil.model.converter.EstadoActivoConverter;

@Entity
@Table(name = "sucursal")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class Sucursal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_sucursal")
    private Integer idSucursal;

    @NotBlank(message = "El nombre es obligatorio")
    @Size(min = 3, max = 100)
    private String nombre;

    private String descripcion;

    // NUEVOS CAMPOS
    @NotBlank(message = "La dirección es obligatoria")
    private String direccion;

    @NotBlank(message = "El teléfono es obligatorio")
    @Size(min = 7, max = 15, message = "El teléfono debe tener entre 7 y 15 caracteres")
    private String telefono;

    @NotBlank(message = "El correo es obligatorio")
    @Email(message = "Correo no válido")
    private String correo;

    @Convert(converter = EstadoActivoConverter.class)
    @Column(name = "activo", nullable = false)
    private String estado = "ACTIVO";

    @Column(name = "created_at")
    private LocalDateTime fechaCreacion;

    // se Cambio FetchType.LAZY por FetchType.EAGER
@ManyToOne(fetch = FetchType.EAGER)
@JoinColumn(name = "id_empresa", nullable = false)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
private Empresa empresa;

}
