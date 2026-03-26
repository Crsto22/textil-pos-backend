package com.sistemapos.sistematextil.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.sistemapos.sistematextil.model.ImportacionProductoHistorial;
import com.sistemapos.sistematextil.model.Sucursal;
import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.repositories.ImportacionProductoHistorialRepository;
import com.sistemapos.sistematextil.repositories.SucursalRepository;
import com.sistemapos.sistematextil.repositories.UsuarioRepository;
import com.sistemapos.sistematextil.util.paginacion.PagedResponse;
import com.sistemapos.sistematextil.util.producto.ProductoImportHistorialListItemResponse;
import com.sistemapos.sistematextil.util.usuario.Rol;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ImportacionProductoHistorialService {

    public static final String ESTADO_EXITOSA = "EXITOSA";
    public static final String ESTADO_PARCIAL = "PARCIAL";
    public static final String ESTADO_FALLIDA = "FALLIDA";

    private final ImportacionProductoHistorialRepository historialRepository;
    private final UsuarioRepository usuarioRepository;
    private final SucursalRepository sucursalRepository;

    @Value("${application.pagination.default-size:10}")
    private int defaultPageSize;

    public PagedResponse<ProductoImportHistorialListItemResponse> listarPaginado(
            int page,
            Integer idSucursal,
            String correoUsuarioAutenticado) {
        validarPagina(page);
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolPermitido(usuarioAutenticado);

        PageRequest pageable = PageRequest.of(page, defaultPageSize, Sort.by("idImportacion").descending());
        Integer idSucursalFiltro = resolverIdSucursalFiltroListado(usuarioAutenticado, idSucursal);
        Page<ImportacionProductoHistorial> historial = idSucursalFiltro == null
                ? historialRepository.findByDeletedAtIsNull(pageable)
                : historialRepository.findByDeletedAtIsNullAndSucursal_IdSucursal(
                        idSucursalFiltro,
                        pageable);

        return PagedResponse.fromPage(historial.map(this::toListItemResponse));
    }

    @Transactional
    public void registrarExitosa(
            Usuario usuario,
            Integer idSucursal,
            String nombreArchivo,
            Long tamanoBytes,
            int filasProcesadas,
            int productosCreados,
            int productosActualizados,
            int variantesGuardadas,
            int categoriasCreadas,
            int coloresCreados,
            int tallasCreadas,
            Integer duracionMs) {
        guardar(
                usuario,
                idSucursal,
                nombreArchivo,
                tamanoBytes,
                filasProcesadas,
                productosCreados,
                productosActualizados,
                variantesGuardadas,
                categoriasCreadas,
                coloresCreados,
                tallasCreadas,
                ESTADO_EXITOSA,
                null,
                duracionMs);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarFallida(
            Usuario usuario,
            Integer idSucursal,
            String nombreArchivo,
            Long tamanoBytes,
            int filasProcesadas,
            int productosCreados,
            int productosActualizados,
            int variantesGuardadas,
            int categoriasCreadas,
            int coloresCreados,
            int tallasCreadas,
            String mensajeError,
            Integer duracionMs) {
        guardar(
                usuario,
                idSucursal,
                nombreArchivo,
                tamanoBytes,
                filasProcesadas,
                productosCreados,
                productosActualizados,
                variantesGuardadas,
                categoriasCreadas,
                coloresCreados,
                tallasCreadas,
                ESTADO_FALLIDA,
                mensajeError,
                duracionMs);
    }

    private void guardar(
            Usuario usuario,
            Integer idSucursal,
            String nombreArchivo,
            Long tamanoBytes,
            int filasProcesadas,
            int productosCreados,
            int productosActualizados,
            int variantesGuardadas,
            int categoriasCreadas,
            int coloresCreados,
            int tallasCreadas,
            String estado,
            String mensajeError,
            Integer duracionMs) {
        if (usuario == null || usuario.getIdUsuario() == null) {
            return;
        }

        ImportacionProductoHistorial historial = new ImportacionProductoHistorial();
        historial.setUsuario(usuarioRepository.getReferenceById(usuario.getIdUsuario()));
        historial.setSucursal(obtenerSucursalReferencia(idSucursal));
        historial.setNombreArchivo(normalizarNombreArchivo(nombreArchivo));
        historial.setTamanoBytes(tamanoBytes == null || tamanoBytes < 0 ? 0L : tamanoBytes);
        historial.setFilasProcesadas(Math.max(filasProcesadas, 0));
        historial.setProductosCreados(Math.max(productosCreados, 0));
        historial.setProductosActualizados(Math.max(productosActualizados, 0));
        historial.setVariantesGuardadas(Math.max(variantesGuardadas, 0));
        historial.setCategoriasCreadas(Math.max(categoriasCreadas, 0));
        historial.setColoresCreados(Math.max(coloresCreados, 0));
        historial.setTallasCreadas(Math.max(tallasCreadas, 0));
        historial.setEstado(normalizarEstado(estado));
        historial.setMensajeError(normalizarMensajeError(mensajeError));
        historial.setDuracionMs(duracionMs == null ? null : Math.max(duracionMs, 0));
        historial.setActivo("ACTIVO");

        historialRepository.save(historial);
    }

    private Sucursal obtenerSucursalReferencia(Integer idSucursal) {
        if (idSucursal == null) {
            return null;
        }
        return sucursalRepository.getReferenceById(idSucursal);
    }

    private ProductoImportHistorialListItemResponse toListItemResponse(ImportacionProductoHistorial historial) {
        Usuario usuario = historial.getUsuario();
        Sucursal sucursal = historial.getSucursal();

        Integer idUsuario = usuario != null ? usuario.getIdUsuario() : null;
        String nombreUsuario = construirNombreUsuario(usuario);
        Integer idSucursal = sucursal != null ? sucursal.getIdSucursal() : null;
        String nombreSucursal = sucursal != null ? sucursal.getNombre() : null;

        return new ProductoImportHistorialListItemResponse(
                historial.getIdImportacion(),
                idUsuario,
                nombreUsuario,
                idSucursal,
                nombreSucursal,
                historial.getNombreArchivo(),
                historial.getTamanoBytes(),
                historial.getFilasProcesadas(),
                historial.getProductosCreados(),
                historial.getProductosActualizados(),
                historial.getVariantesGuardadas(),
                historial.getCategoriasCreadas(),
                historial.getColoresCreados(),
                historial.getTallasCreadas(),
                historial.getEstado(),
                historial.getMensajeError(),
                historial.getDuracionMs(),
                historial.getCreatedAt());
    }

    private String construirNombreUsuario(Usuario usuario) {
        if (usuario == null) {
            return null;
        }
        String nombre = usuario.getNombre() == null ? "" : usuario.getNombre().trim();
        String apellido = usuario.getApellido() == null ? "" : usuario.getApellido().trim();
        String completo = (nombre + " " + apellido).trim();
        return completo.isEmpty() ? usuario.getCorreo() : completo;
    }

    private void validarPagina(int page) {
        if (page < 0) {
            throw new RuntimeException("El parametro 'page' no puede ser negativo");
        }
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
            throw new RuntimeException("El usuario autenticado no tiene permisos para listar historial de importaciones");
        }
    }

    private Integer obtenerIdSucursalUsuario(Usuario usuario) {
        if (usuario.getSucursal() == null || usuario.getSucursal().getIdSucursal() == null) {
            throw new RuntimeException("El usuario autenticado no tiene sucursal asignada");
        }
        return usuario.getSucursal().getIdSucursal();
    }

    private boolean esAdministrador(Usuario usuario) {
        return usuario.getRol() == Rol.ADMINISTRADOR;
    }

    private Integer resolverIdSucursalFiltroListado(Usuario usuarioAutenticado, Integer idSucursalRequest) {
        if (esAdministrador(usuarioAutenticado)) {
            if (idSucursalRequest == null) {
                return null;
            }
            return sucursalRepository.findByIdSucursalAndDeletedAtIsNull(idSucursalRequest)
                    .orElseThrow(() -> new RuntimeException("Sucursal no encontrada"))
                    .getIdSucursal();
        }

        Integer idSucursalUsuario = obtenerIdSucursalUsuario(usuarioAutenticado);
        if (idSucursalRequest != null && !idSucursalUsuario.equals(idSucursalRequest)) {
            throw new RuntimeException("No tiene permisos para consultar otra sucursal");
        }
        return idSucursalUsuario;
    }

    private String normalizarNombreArchivo(String nombreArchivo) {
        if (nombreArchivo == null || nombreArchivo.isBlank()) {
            return "archivo_sin_nombre.xlsx";
        }
        String trimmed = nombreArchivo.trim();
        return trimmed.length() > 255 ? trimmed.substring(0, 255) : trimmed;
    }

    private String normalizarEstado(String estado) {
        if (estado == null || estado.isBlank()) {
            return ESTADO_EXITOSA;
        }
        String normalized = estado.trim().toUpperCase();
        if (!ESTADO_EXITOSA.equals(normalized)
                && !ESTADO_PARCIAL.equals(normalized)
                && !ESTADO_FALLIDA.equals(normalized)) {
            return ESTADO_EXITOSA;
        }
        return normalized;
    }

    private String normalizarMensajeError(String mensajeError) {
        if (mensajeError == null || mensajeError.isBlank()) {
            return null;
        }
        String trimmed = mensajeError.trim();
        return trimmed.length() > 1000 ? trimmed.substring(0, 1000) : trimmed;
    }
}
