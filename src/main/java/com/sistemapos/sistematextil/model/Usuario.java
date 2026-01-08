package com.sistemapos.sistematextil.model;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/*

public class Usuario {
    
    @Id
    @GeneratedValue (strategy = GenerationType.IDENTITY)
    private Integer idUsuario;

    @NotBlank(message = "Ingrese un Nombre")
    @Pattern(regexp = "^[A-Za-zÁÉÍÓÚáéíóúÑñ ]+$", message = "No puede contener numeros")
    @Size(min = 2, max = 50, message = "Ingrese un nombre entre 2 y 50 caracteres")
    private String nombre;

    private String apellido;



}
*/