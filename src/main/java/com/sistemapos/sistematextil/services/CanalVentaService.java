package com.sistemapos.sistematextil.services;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sistemapos.sistematextil.model.CanalVenta;
import com.sistemapos.sistematextil.model.Sucursal;
import com.sistemapos.sistematextil.model.SucursalTipo;
import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.repositories.CanalVentaRepository;
import com.sistemapos.sistematextil.repositories.SucursalRepository;
import com.sistemapos.sistematextil.repositories.UsuarioRepository;
import com.sistemapos.sistematextil.util.canalventa.CanalVentaCreateRequest;
import com.sistemapos.sistematextil.util.canalventa.CanalVentaResponse;
import com.sistemapos.sistematextil.util.canalventa.CanalVentaUpdateRequest;
import com.sistemapos.sistematextil.util.usuario.Rol;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CanalVentaService {

    private final CanalVentaRepository canalVentaRepository;
    private final SucursalRepository sucursalRepository;
    private final UsuarioRepository usuarioRepository;

    public List<CanalVentaResponse> listar(Integer idSucursal, String correoUsuarioAutenticado) {
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        Integer idSucursalFiltro = resolverSucursalConsulta(usuarioAutenticado, idSucursal);
        List<CanalVenta> canales = idSucursalFiltro == null
                ? canalVentaRepository.findByDeletedAtIsNullOrderByIdCanalVentaAsc()
                : canalVentaRepository.findBySucursalIdSucursalAndDeletedAtIsNullOrderByIdCanalVentaAsc(idSucursalFiltro);
        return canales.stream().map(this::toResponse).toList();
    }

    @Transactional
    public CanalVentaResponse insertar(CanalVentaCreateRequest request, String correoUsuarioAutenticado) {
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarAdministrador(usuarioAutenticado);

        Sucursal sucursal = obtenerSucursalVenta(request.idSucursal());
        String nombre = normalizarRequerido(request.nombre(), "El nombre es obligatorio");
        validarNombreDuplicado(sucursal.getIdSucursal(), nombre, null);

        CanalVenta canal = new CanalVenta();
        canal.setSucursal(sucursal);
        canal.setNombre(nombre);
        canal.setPlataforma(request.plataforma());
        canal.setDescripcion(normalizar(request.descripcion()));
        canal.setActivo(request.activo() == null ? Boolean.TRUE : request.activo());
        canal.setDeletedAt(null);
        return toResponse(canalVentaRepository.save(canal));
    }

    @Transactional
    public CanalVentaResponse actualizar(Integer idCanalVenta, CanalVentaUpdateRequest request, String correoUsuarioAutenticado) {
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarAdministrador(usuarioAutenticado);

        CanalVenta canal = canalVentaRepository.findByIdCanalVentaAndDeletedAtIsNull(idCanalVenta)
                .orElseThrow(() -> new RuntimeException("Canal de venta no encontrado"));
        Sucursal sucursal = obtenerSucursalVenta(request.idSucursal());
        String nombre = normalizarRequerido(request.nombre(), "El nombre es obligatorio");
        validarNombreDuplicado(sucursal.getIdSucursal(), nombre, idCanalVenta);

        canal.setSucursal(sucursal);
        canal.setNombre(nombre);
        canal.setPlataforma(request.plataforma());
        canal.setDescripcion(normalizar(request.descripcion()));
        canal.setActivo(request.activo() == null ? canal.getActivo() : request.activo());
        return toResponse(canalVentaRepository.save(canal));
    }

    @Transactional
    public void eliminar(Integer idCanalVenta, String correoUsuarioAutenticado) {
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarAdministrador(usuarioAutenticado);

        CanalVenta canal = canalVentaRepository.findByIdCanalVentaAndDeletedAtIsNull(idCanalVenta)
                .orElseThrow(() -> new RuntimeException("Canal de venta no encontrado"));
        canal.setActivo(Boolean.FALSE);
        canal.setDeletedAt(LocalDateTime.now());
        canalVentaRepository.save(canal);
    }

    private Integer resolverSucursalConsulta(Usuario usuarioAutenticado, Integer idSucursalRequest) {
        if (usuarioAutenticado.getRol() == Rol.ADMINISTRADOR) {
            return idSucursalRequest;
        }
        Integer idSucursalUsuario = obtenerIdSucursalUsuario(usuarioAutenticado);
        if (idSucursalRequest != null && !idSucursalUsuario.equals(idSucursalRequest)) {
            throw new RuntimeException("No tiene permisos para consultar otra sucursal");
        }
        return idSucursalUsuario;
    }

    private void validarAdministrador(Usuario usuario) {
        if (usuario.getRol() != Rol.ADMINISTRADOR) {
            throw new RuntimeException("El usuario autenticado no tiene permisos para gestionar canales de venta");
        }
    }

    private Sucursal obtenerSucursalVenta(Integer idSucursal) {
        return sucursalRepository.findByIdSucursalAndDeletedAtIsNull(idSucursal)
                .filter(sucursal -> "ACTIVO".equalsIgnoreCase(sucursal.getEstado()))
                .filter(sucursal -> sucursal.getTipo() == SucursalTipo.VENTA)
                .orElseThrow(() -> new RuntimeException("La sucursal debe existir, estar activa y ser tipo VENTA"));
    }

    private void validarNombreDuplicado(Integer idSucursal, String nombre, Integer idCanalVentaActual) {
        boolean existe = idCanalVentaActual == null
                ? canalVentaRepository.existsBySucursalIdSucursalAndNombreIgnoreCaseAndDeletedAtIsNull(idSucursal, nombre)
                : canalVentaRepository.existsBySucursalIdSucursalAndNombreIgnoreCaseAndDeletedAtIsNullAndIdCanalVentaNot(
                        idSucursal,
                        nombre,
                        idCanalVentaActual);
        if (existe) {
            throw new RuntimeException("Ya existe un canal de venta con ese nombre en la sucursal");
        }
    }

    private Usuario obtenerUsuarioAutenticado(String correoUsuarioAutenticado) {
        if (correoUsuarioAutenticado == null || correoUsuarioAutenticado.isBlank()) {
            throw new RuntimeException("No autenticado");
        }
        return usuarioRepository.findByCorreoAndDeletedAtIsNull(correoUsuarioAutenticado)
                .orElseThrow(() -> new RuntimeException("Usuario autenticado no encontrado"));
    }

    private Integer obtenerIdSucursalUsuario(Usuario usuario) {
        if (usuario.getSucursal() == null || usuario.getSucursal().getIdSucursal() == null) {
            throw new RuntimeException("El usuario autenticado no tiene sucursal asignada");
        }
        return usuario.getSucursal().getIdSucursal();
    }

    private CanalVentaResponse toResponse(CanalVenta canal) {
        String tipoSucursal = canal.getSucursal() != null && canal.getSucursal().getTipo() != null
                ? canal.getSucursal().getTipo().name()
                : null;
        return new CanalVentaResponse(
                canal.getIdCanalVenta(),
                canal.getSucursal() != null ? canal.getSucursal().getIdSucursal() : null,
                canal.getSucursal() != null ? canal.getSucursal().getNombre() : null,
                tipoSucursal,
                canal.getNombre(),
                canal.getPlataforma() != null ? canal.getPlataforma().name() : null,
                canal.getDescripcion(),
                canal.getActivo(),
                canal.getCreatedAt(),
                canal.getUpdatedAt());
    }

    private String normalizarRequerido(String value, String message) {
        String normalizado = normalizar(value);
        if (normalizado == null) {
            throw new RuntimeException(message);
        }
        return normalizado;
    }

    private String normalizar(String value) {
        if (value == null) {
            return null;
        }
        String normalizado = value.trim();
        return normalizado.isEmpty() ? null : normalizado;
    }
}
