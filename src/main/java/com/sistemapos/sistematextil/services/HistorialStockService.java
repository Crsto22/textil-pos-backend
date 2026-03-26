package com.sistemapos.sistematextil.services;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sistemapos.sistematextil.model.HistorialStock;
import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.repositories.HistorialStockRepository;
import com.sistemapos.sistematextil.repositories.UsuarioRepository;
import com.sistemapos.sistematextil.util.usuario.Rol;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class HistorialStockService {

    private final HistorialStockRepository repository;
    private final UsuarioRepository usuarioRepository;

    public List<HistorialStock> listarTodo(String correoUsuarioAutenticado) {
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolPermitido(usuarioAutenticado);

        Integer idSucursalFiltro = resolverIdSucursalFiltro(usuarioAutenticado);
        return idSucursalFiltro == null
                ? repository.findAll()
                : repository.findBySucursalIdSucursalOrderByFechaDesc(idSucursalFiltro);
    }

    public List<HistorialStock> listarPorProducto(Integer idProducto, String correoUsuarioAutenticado) {
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolPermitido(usuarioAutenticado);

        Integer idSucursalFiltro = resolverIdSucursalFiltro(usuarioAutenticado);
        return idSucursalFiltro == null
                ? repository.findByProductoVarianteProductoIdProductoOrderByFechaDesc(idProducto)
                : repository.findByProductoVarianteProductoIdProductoAndSucursalIdSucursalOrderByFechaDesc(
                        idProducto,
                        idSucursalFiltro);
    }

    public List<HistorialStock> listarPorVariante(Integer idProductoVariante, String correoUsuarioAutenticado) {
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolPermitido(usuarioAutenticado);

        Integer idSucursalFiltro = resolverIdSucursalFiltro(usuarioAutenticado);
        return idSucursalFiltro == null
                ? repository.findByProductoVarianteIdProductoVarianteOrderByFechaDesc(idProductoVariante)
                : repository.findByProductoVarianteIdProductoVarianteAndSucursalIdSucursalOrderByFechaDesc(
                        idProductoVariante,
                        idSucursalFiltro);
    }

    @Transactional
    public void registrarMovimiento(HistorialStock historial) {
        repository.save(historial);
    }

    private Usuario obtenerUsuarioAutenticado(String correoUsuarioAutenticado) {
        if (correoUsuarioAutenticado == null || correoUsuarioAutenticado.isBlank()) {
            throw new RuntimeException("No autenticado");
        }
        return usuarioRepository.findByCorreoAndDeletedAtIsNull(correoUsuarioAutenticado)
                .orElseThrow(() -> new RuntimeException("Usuario autenticado no encontrado"));
    }

    private void validarRolPermitido(Usuario usuario) {
        if (usuario.getRol() != Rol.ADMINISTRADOR
                && usuario.getRol() != Rol.VENTAS
                && usuario.getRol() != Rol.ALMACEN) {
            throw new RuntimeException("El usuario autenticado no tiene permisos para consultar historial de stock");
        }
    }

    private Integer resolverIdSucursalFiltro(Usuario usuarioAutenticado) {
        if (usuarioAutenticado.getSucursal() != null && usuarioAutenticado.getSucursal().getIdSucursal() != null) {
            return usuarioAutenticado.getSucursal().getIdSucursal();
        }
        if (usuarioAutenticado.getRol() == Rol.ADMINISTRADOR) {
            return null;
        }
        throw new RuntimeException("El usuario autenticado no tiene sucursal asignada");
    }
}
