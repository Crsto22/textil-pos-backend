package com.sistemapos.sistematextil.model;

import java.util.Collection;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.sistemapos.sistematextil.util.usuario.Rol;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
public class CustomUser implements UserDetails {

    @Getter
    private Usuario usuario;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        Rol rol = usuario.getRol();
        if (rol == Rol.VENTAS_ALMACEN) {
            return List.of(
                    new SimpleGrantedAuthority(Rol.VENTAS_ALMACEN.name()),
                    new SimpleGrantedAuthority(Rol.VENTAS.name()),
                    new SimpleGrantedAuthority(Rol.ALMACEN.name()));
        }
        return List.of(new SimpleGrantedAuthority(rol.name()));
    }

    @Override
    public @Nullable String getPassword() {
        return usuario.getPassword();
    }

    @Override
    public String getUsername() {
        return usuario.getCorreo();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return "ACTIVO".equalsIgnoreCase(usuario.getEstado()) && usuario.getDeletedAt() == null;
    }

}
