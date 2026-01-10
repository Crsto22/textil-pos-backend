package com.sistemapos.sistematextil.services;

import org.springframework.stereotype.Service;

import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.repositories.UsuarioRepository;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;


    public Usuario findByCorreo (String correo){
        return usuarioRepository.findByCorreo(correo).orElseThrow();
    }

}
