package com.sistemapos.sistematextil.model;

import java.util.Collection;
import java.util.ArrayList;
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
        List<GrantedAuthority> authorities = new ArrayList<>();
        if (rol == Rol.VENTAS_ALMACEN) {
            authorities.add(new SimpleGrantedAuthority(Rol.VENTAS_ALMACEN.name()));
            authorities.add(new SimpleGrantedAuthority(Rol.VENTAS.name()));
            authorities.add(new SimpleGrantedAuthority(Rol.ALMACEN.name()));
        } else {
            authorities.add(new SimpleGrantedAuthority(rol.name()));
        }
        if (rol == Rol.ADMINISTRADOR || Boolean.TRUE.equals(usuario.getAccesoCrm())) {
            authorities.add(new SimpleGrantedAuthority("CRM_CHAT"));
        }
        return authorities;
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
