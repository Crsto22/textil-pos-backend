package com.sistemapos.sistematextil.repositories;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.sistemapos.sistematextil.model.Usuario;


public interface UsuarioRepository extends JpaRepository <Usuario, Integer>{

    Optional <Usuario> findByCorreo(String correo);
}
