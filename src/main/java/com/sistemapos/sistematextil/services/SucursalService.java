package com.sistemapos.sistematextil.services;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sistemapos.sistematextil.model.Empresa;
import com.sistemapos.sistematextil.model.Sucursal;
import com.sistemapos.sistematextil.repositories.EmpresaRepository;
import com.sistemapos.sistematextil.repositories.SucursalRepository;
import com.sistemapos.sistematextil.repositories.UsuarioRepository;
import com.sistemapos.sistematextil.util.paginacion.PagedResponse;
import com.sistemapos.sistematextil.util.sucursal.SucursalCreateRequest;
import com.sistemapos.sistematextil.util.sucursal.SucursalListItemResponse;
import com.sistemapos.sistematextil.util.sucursal.SucursalUpdateRequest;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SucursalService {

    private final SucursalRepository sucursalRepository;
    private final EmpresaRepository empresaRepository;
    private final UsuarioRepository usuarioRepository;

    @Value("${application.pagination.default-size:10}")
    private int defaultPageSize;

    public PagedResponse<SucursalListItemResponse> listarPaginado(int page) {
        PageRequest pageable = PageRequest.of(page, defaultPageSize, Sort.by("idSucursal").ascending());
        Page<SucursalListItemResponse> sucursales = sucursalRepository.findByDeletedAtIsNull(pageable)
                .map(this::toListItemResponse);
        return PagedResponse.fromPage(sucursales);
    }

    public PagedResponse<SucursalListItemResponse> buscarPaginado(String term, int page) {
        if (term == null || term.isBlank()) {
            throw new RuntimeException("Debe ingresar nombre de sucursal para buscar");
        }

        PageRequest pageable = PageRequest.of(page, defaultPageSize, Sort.by("idSucursal").ascending());
        Page<SucursalListItemResponse> sucursales = sucursalRepository
                .findByDeletedAtIsNullAndNombreStartingWithIgnoreCase(term.trim(), pageable)
                .map(this::toListItemResponse);

        return PagedResponse.fromPage(sucursales);
    }

    @Transactional
    public SucursalListItemResponse insertar(SucursalCreateRequest request) {
        String nombreNormalizado = request.nombre().trim();
        validarNombreDuplicado(request.idEmpresa(), nombreNormalizado, null);

        Empresa empresa = empresaRepository.findById(request.idEmpresa())
                .orElseThrow(() -> new RuntimeException("Empresa no encontrada"));

        Sucursal sucursal = new Sucursal();
        sucursal.setNombre(nombreNormalizado);
        sucursal.setDescripcion(request.descripcion());
        sucursal.setDireccion(request.direccion().trim());
        sucursal.setTelefono(request.telefono().trim());
        sucursal.setCorreo(request.correo().trim());
        sucursal.setEstado("ACTIVO");
        sucursal.setFechaCreacion(LocalDateTime.now());
        sucursal.setDeletedAt(null);
        sucursal.setEmpresa(empresa);

        Sucursal creada = sucursalRepository.save(sucursal);
        return toListItemResponse(creada);
    }

    @Transactional
    public SucursalListItemResponse actualizar(Integer idSucursal, SucursalUpdateRequest request) {
        Sucursal sucursal = sucursalRepository.findByIdSucursalAndDeletedAtIsNull(idSucursal)
                .orElseThrow(() -> new RuntimeException("Sucursal con ID " + idSucursal + " no encontrada"));

        String nombreNormalizado = request.nombre().trim();
        validarNombreDuplicado(request.idEmpresa(), nombreNormalizado, idSucursal);

        Empresa empresa = empresaRepository.findById(request.idEmpresa())
                .orElseThrow(() -> new RuntimeException("Empresa no encontrada"));

        sucursal.setNombre(nombreNormalizado);
        sucursal.setDescripcion(request.descripcion());
        sucursal.setDireccion(request.direccion().trim());
        sucursal.setTelefono(request.telefono().trim());
        sucursal.setCorreo(request.correo().trim());
        sucursal.setEstado(request.estado().toUpperCase());
        sucursal.setEmpresa(empresa);

        Sucursal actualizada = sucursalRepository.save(sucursal);
        return toListItemResponse(actualizada);
    }

    @Transactional
    public void eliminarLogico(Integer idSucursal) {
        Sucursal sucursal = sucursalRepository.findByIdSucursalAndDeletedAtIsNull(idSucursal)
                .orElseThrow(() -> new RuntimeException(
                        "Sucursal con ID " + idSucursal + " no encontrada o ya eliminada"));

        sucursal.setEstado("INACTIVO");
        sucursal.setDeletedAt(LocalDateTime.now());
        liberarNombreUnico(sucursal);
        sucursalRepository.save(sucursal);
    }

    private void validarNombreDuplicado(Integer idEmpresa, String nombre, Integer idSucursalActual) {
        sucursalRepository.findByEmpresa_IdEmpresaAndNombreIgnoreCaseAndDeletedAtIsNull(idEmpresa, nombre)
                .ifPresent(existing -> {
                    if (idSucursalActual == null || !existing.getIdSucursal().equals(idSucursalActual)) {
                        throw new RuntimeException("Ya existe una sucursal con ese nombre para la empresa");
                    }
                });
    }

    private void liberarNombreUnico(Sucursal sucursal) {
        String nombreBase = sucursal.getNombre() == null ? "SUCURSAL" : sucursal.getNombre().trim();
        String sufijo = "_ELIMINADA_" + sucursal.getIdSucursal();
        int maxBase = Math.max(1, 100 - sufijo.length());
        if (nombreBase.length() > maxBase) {
            nombreBase = nombreBase.substring(0, maxBase);
        }
        sucursal.setNombre(nombreBase + sufijo);
    }

    private SucursalListItemResponse toListItemResponse(Sucursal sucursal) {
        Integer idSucursal = sucursal.getIdSucursal();
        Integer idEmpresa = sucursal.getEmpresa() != null ? sucursal.getEmpresa().getIdEmpresa() : null;
        String nombreEmpresa = sucursal.getEmpresa() != null ? sucursal.getEmpresa().getNombre() : null;
        List<String> usuarios = idSucursal != null
                ? usuarioRepository.findTop5NombresCompletosRandomBySucursal(idSucursal)
                : Collections.emptyList();
        long usuariosTotal = idSucursal != null
                ? usuarioRepository.countBySucursalIdSucursalAndDeletedAtIsNullAndEstado(idSucursal, "ACTIVO")
                : 0L;
        long usuariosFaltantes = Math.max(usuariosTotal - usuarios.size(), 0L);

        return new SucursalListItemResponse(
                idSucursal,
                sucursal.getNombre(),
                sucursal.getDescripcion(),
                sucursal.getDireccion(),
                sucursal.getTelefono(),
                sucursal.getCorreo(),
                sucursal.getEstado(),
                sucursal.getFechaCreacion(),
                idEmpresa,
                nombreEmpresa,
                usuarios,
                usuariosTotal,
                usuariosFaltantes);
    }
}
