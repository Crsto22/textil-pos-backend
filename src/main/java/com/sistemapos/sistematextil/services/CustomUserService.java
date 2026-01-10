package com.sistemapos.sistematextil.services;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.sistemapos.sistematextil.model.CustomUser;
import com.sistemapos.sistematextil.model.Usuario;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CustomUserService implements UserDetailsService {

    private final UsuarioService service;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Usuario usuario;
        try {
            usuario = service.Buscarporemail(username);//Busca por email al usuario en la base de datos
            return new CustomUser(usuario); //Envolvemos en CustomUser porque Spring security usa ese CustomUser internamente para validar el PasswordEncoder y el getAuthorities
        } catch (Exception e) {
            throw new UsernameNotFoundException("Usuario no encontrado");
        }
    }

}
