package com.sistemapos.sistematextil.controllers;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sistemapos.sistematextil.services.UsuarioService;

import lombok.AllArgsConstructor;

@RestController
@RequestMapping(value = "api/usuario", produces = MediaType.APPLICATION_JSON_VALUE)
@AllArgsConstructor
public class UsuarioController {

    private final UsuarioService usuarioService;


    //-------Página de acceso para usuarios publicos y privados--------
    @GetMapping("/publico")
    public String paginaPublica() {
        return "Pagina Pública";
    }

    @GetMapping("/privado")
    public String paginaPrivada() {
        return "Página Privada solo con accesos";
    }


}
