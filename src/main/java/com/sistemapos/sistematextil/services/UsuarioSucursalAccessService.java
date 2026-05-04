package com.sistemapos.sistematextil.services;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sistemapos.sistematextil.model.Sucursal;
import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.model.UsuarioSucursal;
import com.sistemapos.sistematextil.repositories.SucursalRepository;
import com.sistemapos.sistematextil.repositories.UsuarioSucursalRepository;
import com.sistemapos.sistematextil.util.usuario.Rol;
import com.sistemapos.sistematextil.util.usuario.SucursalPermitidaResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UsuarioSucursalAccessService {

    private final SucursalRepository sucursalRepository;
    private final UsuarioSucursalRepository usuarioSucursalRepository;

    @Transactional(readOnly = true)
    public AsignacionSucursal resolverAsignacion(Rol rol, Integer idSucursalPrincipal, List<Integer> idsSucursales) {
        if (rol.esAdministrador()) {
            return new AsignacionSucursal(null, List.of());
        }

        if (idSucursalPrincipal == null) {
            throw new RuntimeException("La sucursal principal es obligatoria para el rol " + rol.name());
        }

        LinkedHashSet<Integer> ids = new LinkedHashSet<>();
        ids.add(idSucursalPrincipal);
        if (idsSucursales != null) {
            idsSucursales.stream()
                    .filter(id -> id != null)
                    .forEach(ids::add);
        }

        if (ids.stream().anyMatch(id -> id <= 0)) {
            throw new RuntimeException("Los ids de sucursal deben ser mayores a 0");
        }

        Map<Integer, Sucursal> sucursales = obtenerSucursalesActivas(ids);
        Sucursal principal = sucursales.get(idSucursalPrincipal);
        if (principal == null) {
            throw new RuntimeException("Sucursal principal no encontrada");
        }

        List<Integer> idsNoEncontrados = ids.stream()
                .filter(id -> !sucursales.containsKey(id))
                .toList();
        if (!idsNoEncontrados.isEmpty()) {
            throw new RuntimeException("Sucursal no encontrada: " + idsNoEncontrados.get(0));
        }

        List<Sucursal> permitidas = new ArrayList<>(sucursales.values());
        permitidas.forEach(sucursal -> validarRolSegunTipoSucursal(rol, sucursal));

        return new AsignacionSucursal(principal, permitidas);
    }

    @Transactional
    public void sincronizarSucursales(Usuario usuario, List<Sucursal> sucursales) {
        usuarioSucursalRepository.deleteByUsuarioId(usuario.getIdUsuario());
        if (sucursales == null || sucursales.isEmpty()) {
            return;
        }

        List<UsuarioSucursal> asignaciones = sucursales.stream()
                .map(sucursal -> {
                    UsuarioSucursal usuarioSucursal = new UsuarioSucursal();
                    usuarioSucursal.setUsuario(usuario);
                    usuarioSucursal.setSucursal(sucursal);
                    return usuarioSucursal;
                })
                .toList();
        usuarioSucursalRepository.saveAll(asignaciones);
    }

    @Transactional(readOnly = true)
    public List<SucursalPermitidaResponse> obtenerSucursalesPermitidasResponse(Usuario usuario) {
        return obtenerSucursalesPermitidas(usuario).stream()
                .map(sucursal -> new SucursalPermitidaResponse(
                        sucursal.getIdSucursal(),
                        sucursal.getNombre(),
                        sucursal.getTipo() != null ? sucursal.getTipo().name() : null))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Sucursal> obtenerSucursalesPermitidas(Usuario usuario) {
        if (usuario.getRol().esAdministrador()) {
            return List.of();
        }

        Map<Integer, Sucursal> sucursales = new LinkedHashMap<>();
        if (usuario.getSucursal() != null && usuario.getSucursal().getIdSucursal() != null) {
            sucursales.put(usuario.getSucursal().getIdSucursal(), usuario.getSucursal());
        }

        usuarioSucursalRepository.findActivasByUsuarioId(usuario.getIdUsuario()).forEach(asignacion -> {
            Sucursal sucursal = asignacion.getSucursal();
            if (sucursal != null && sucursal.getIdSucursal() != null) {
                sucursales.put(sucursal.getIdSucursal(), sucursal);
            }
        });

        return sucursales.values().stream()
                .sorted(Comparator.comparing(Sucursal::getIdSucursal))
                .toList();
    }

    @Transactional(readOnly = true)
    public Set<Integer> obtenerIdsSucursalesPermitidas(Usuario usuario) {
        return obtenerSucursalesPermitidas(usuario).stream()
                .map(Sucursal::getIdSucursal)
                .collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);
    }

    @Transactional(readOnly = true)
    public Integer resolverIdSucursalPermitida(Usuario usuario, Integer idSucursalRequest, String mensajeNoPermitido) {
        if (usuario.getRol().esAdministrador()) {
            return resolverSucursalActiva(idSucursalRequest, true).getIdSucursal();
        }

        Integer idSucursal = idSucursalRequest != null ? idSucursalRequest : obtenerIdSucursalPrincipal(usuario);
        if (!obtenerIdsSucursalesPermitidas(usuario).contains(idSucursal)) {
            throw new RuntimeException(mensajeNoPermitido);
        }
        return idSucursal;
    }

    @Transactional(readOnly = true)
    public Integer resolverIdSucursalFiltro(Usuario usuario, Integer idSucursalRequest, String mensajeNoPermitido) {
        if (usuario.getRol().esAdministrador()) {
            return idSucursalRequest == null ? null : resolverSucursalActiva(idSucursalRequest, true).getIdSucursal();
        }
        return resolverIdSucursalPermitida(usuario, idSucursalRequest, mensajeNoPermitido);
    }

    @Transactional(readOnly = true)
    public void validarSucursalPermitida(Usuario usuario, Integer idSucursal, String mensajeNoPermitido) {
        if (usuario.getRol().esAdministrador()) {
            return;
        }
        if (idSucursal == null || !obtenerIdsSucursalesPermitidas(usuario).contains(idSucursal)) {
            throw new RuntimeException(mensajeNoPermitido);
        }
    }

    public Integer obtenerIdSucursalPrincipal(Usuario usuario) {
        if (usuario.getSucursal() == null || usuario.getSucursal().getIdSucursal() == null) {
            throw new RuntimeException("El usuario autenticado no tiene sucursal asignada");
        }
        return usuario.getSucursal().getIdSucursal();
    }

    private Map<Integer, Sucursal> obtenerSucursalesActivas(Set<Integer> ids) {
        List<Sucursal> sucursales = sucursalRepository.findByIdSucursalInAndDeletedAtIsNullOrderByIdSucursalAsc(ids);
        Map<Integer, Sucursal> porId = new LinkedHashMap<>();
        sucursales.forEach(sucursal -> porId.put(sucursal.getIdSucursal(), sucursal));
        return porId;
    }

    private Sucursal resolverSucursalActiva(Integer idSucursal, boolean obligatorio) {
        if (idSucursal == null) {
            if (obligatorio) {
                throw new RuntimeException("Ingrese idSucursal");
            }
            return null;
        }
        if (idSucursal <= 0) {
            throw new RuntimeException("idSucursal debe ser mayor a 0");
        }
        return sucursalRepository.findByIdSucursalAndDeletedAtIsNull(idSucursal)
                .orElseThrow(() -> new RuntimeException("Sucursal no encontrada"));
    }

    private void validarRolSegunTipoSucursal(Rol rol, Sucursal sucursal) {
        if (!rol.esCompatibleCon(sucursal.getTipo())) {
            throw new RuntimeException(rol.mensajeSucursalIncompatible(sucursal.getTipo()));
        }
    }

    public record AsignacionSucursal(Sucursal principal, List<Sucursal> permitidas) {
    }
}
