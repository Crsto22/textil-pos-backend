package com.sistemapos.sistematextil.services;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sistemapos.sistematextil.model.Categoria;
import com.sistemapos.sistematextil.model.Producto;
import com.sistemapos.sistematextil.model.Sucursal;
import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.repositories.CategoriaRepository;
import com.sistemapos.sistematextil.repositories.ProductoRepository;
import com.sistemapos.sistematextil.repositories.SucursalRepository;
import com.sistemapos.sistematextil.repositories.UsuarioRepository;
import com.sistemapos.sistematextil.util.PagedResponse;
import com.sistemapos.sistematextil.util.ProductoCreateRequest;
import com.sistemapos.sistematextil.util.ProductoListItemResponse;
import com.sistemapos.sistematextil.util.ProductoUpdateRequest;
import com.sistemapos.sistematextil.util.Rol;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductoService {

    private final ProductoRepository productoRepository;
    private final CategoriaRepository categoriaRepository;
    private final SucursalRepository sucursalRepository;
    private final UsuarioRepository usuarioRepository;

    @Value("${application.pagination.default-size:10}")
    private int defaultPageSize;

    public PagedResponse<ProductoListItemResponse> listarPaginado(int page, String correoUsuarioAutenticado) {
        validarPagina(page);
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolPermitido(usuarioAutenticado);

        PageRequest pageable = PageRequest.of(page, defaultPageSize, Sort.by("idProducto").ascending());
        Page<Producto> productos = esAdministrador(usuarioAutenticado)
                ? productoRepository.findAllByOrderByIdProductoAsc(pageable)
                : productoRepository.findBySucursal_IdSucursalOrderByIdProductoAsc(
                        obtenerIdSucursalUsuario(usuarioAutenticado),
                        pageable);

        return PagedResponse.fromPage(productos.map(this::toListItemResponse));
    }

    public PagedResponse<ProductoListItemResponse> buscarPaginado(String q, int page, String correoUsuarioAutenticado) {
        validarPagina(page);
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolPermitido(usuarioAutenticado);

        String term = normalizar(q);
        if (term == null) {
            return listarPaginado(page, correoUsuarioAutenticado);
        }

        Integer idSucursalFiltro = esAdministrador(usuarioAutenticado) ? null : obtenerIdSucursalUsuario(usuarioAutenticado);
        PageRequest pageable = PageRequest.of(page, defaultPageSize, Sort.by("idProducto").ascending());
        Page<Producto> productos = productoRepository.buscarConFiltros(term, idSucursalFiltro, pageable);

        return PagedResponse.fromPage(productos.map(this::toListItemResponse));
    }

    @Transactional
    public ProductoListItemResponse insertar(ProductoCreateRequest request, String correoUsuarioAutenticado) {
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolPermitido(usuarioAutenticado);

        Sucursal sucursal = resolverSucursalParaEscritura(request.idSucursal(), usuarioAutenticado);
        Categoria categoria = obtenerCategoriaActivaEnSucursal(request.idCategoria(), sucursal.getIdSucursal());

        String sku = normalizarRequerido(request.sku(), "El SKU es obligatorio");
        String nombre = normalizarRequerido(request.nombre(), "El nombre del producto es obligatorio");
        String descripcion = normalizar(request.descripcion());
        String codigoExterno = normalizar(request.codigoExterno());

        validarSkuUnicoPorSucursal(sku, sucursal.getIdSucursal(), null);
        validarCodigoExternoUnico(codigoExterno, null);

        Producto producto = new Producto();
        producto.setSucursal(sucursal);
        producto.setCategoria(categoria);
        producto.setSku(sku);
        producto.setNombre(nombre);
        producto.setDescripcion(descripcion);
        producto.setCodigoExterno(codigoExterno);
        producto.setFechaCreacion(LocalDateTime.now());
        producto.setEstado("ACTIVO");

        try {
            Producto creado = productoRepository.save(producto);
            return toListItemResponse(creado);
        } catch (DataIntegrityViolationException e) {
            throw new RuntimeException("No se pudo guardar el producto por restriccion de datos unicos");
        }
    }

    @Transactional
    public ProductoListItemResponse actualizar(
            Integer idProducto,
            ProductoUpdateRequest request,
            String correoUsuarioAutenticado) {
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolPermitido(usuarioAutenticado);

        Producto producto = obtenerProductoConAlcance(idProducto, usuarioAutenticado);
        Sucursal sucursalDestino = resolverSucursalParaActualizacion(producto, request.idSucursal(), usuarioAutenticado);
        Categoria categoriaDestino = obtenerCategoriaActivaEnSucursal(request.idCategoria(), sucursalDestino.getIdSucursal());

        String sku = normalizarRequerido(request.sku(), "El SKU es obligatorio");
        String nombre = normalizarRequerido(request.nombre(), "El nombre del producto es obligatorio");
        String descripcion = normalizar(request.descripcion());
        String codigoExterno = normalizar(request.codigoExterno());

        validarSkuUnicoPorSucursal(sku, sucursalDestino.getIdSucursal(), idProducto);
        validarCodigoExternoUnico(codigoExterno, idProducto);

        producto.setSucursal(sucursalDestino);
        producto.setCategoria(categoriaDestino);
        producto.setSku(sku);
        producto.setNombre(nombre);
        producto.setDescripcion(descripcion);
        producto.setCodigoExterno(codigoExterno);

        try {
            Producto actualizado = productoRepository.save(producto);
            return toListItemResponse(actualizado);
        } catch (DataIntegrityViolationException e) {
            throw new RuntimeException("No se pudo actualizar el producto por restriccion de datos unicos");
        }
    }

    @Transactional
    public void eliminar(Integer idProducto, String correoUsuarioAutenticado) {
        Usuario usuarioAutenticado = obtenerUsuarioAutenticado(correoUsuarioAutenticado);
        validarRolPermitido(usuarioAutenticado);

        Producto producto = obtenerProductoConAlcance(idProducto, usuarioAutenticado);
        if (productoRepository.estaEnUso(producto.getIdProducto())) {
            throw new RuntimeException("No se puede eliminar el producto '" + producto.getNombre()
                    + "' porque esta asociado a variantes. Te sugiero archivarlo.");
        }
        productoRepository.deleteById(producto.getIdProducto());
    }

    public Producto obtenerPorId(Integer id) {
        return productoRepository.findByIdProducto(id)
                .orElseThrow(() -> new RuntimeException("Producto con ID " + id + " no encontrado"));
    }

    private Producto obtenerProductoConAlcance(Integer idProducto, Usuario usuarioAutenticado) {
        if (esAdministrador(usuarioAutenticado)) {
            return productoRepository.findByIdProducto(idProducto)
                    .orElseThrow(() -> new RuntimeException("Producto con ID " + idProducto + " no encontrado"));
        }
        Integer idSucursalUsuario = obtenerIdSucursalUsuario(usuarioAutenticado);
        return productoRepository.findByIdProductoAndSucursal_IdSucursal(idProducto, idSucursalUsuario)
                .orElseThrow(() -> new RuntimeException("Producto con ID " + idProducto + " no encontrado"));
    }

    private Sucursal resolverSucursalParaEscritura(Integer idSucursalRequest, Usuario usuarioAutenticado) {
        Integer idSucursalDestino = esAdministrador(usuarioAutenticado)
                ? idSucursalRequeridaParaAdmin(idSucursalRequest)
                : obtenerIdSucursalUsuario(usuarioAutenticado);
        return obtenerSucursalActiva(idSucursalDestino);
    }

    private Sucursal resolverSucursalParaActualizacion(
            Producto productoOriginal,
            Integer idSucursalRequest,
            Usuario usuarioAutenticado) {
        Integer idSucursalDestino;
        if (esAdministrador(usuarioAutenticado)) {
            idSucursalDestino = idSucursalRequest != null
                    ? idSucursalRequest
                    : productoOriginal.getSucursal().getIdSucursal();
        } else {
            idSucursalDestino = obtenerIdSucursalUsuario(usuarioAutenticado);
        }
        return obtenerSucursalActiva(idSucursalDestino);
    }

    private Categoria obtenerCategoriaActivaEnSucursal(Integer idCategoria, Integer idSucursal) {
        Categoria categoria = categoriaRepository.findByIdCategoriaAndSucursal_IdSucursal(idCategoria, idSucursal)
                .orElseThrow(() -> new RuntimeException("Categoria no encontrada en la sucursal seleccionada"));
        if (!"ACTIVO".equalsIgnoreCase(categoria.getEstado())) {
            throw new RuntimeException("No se puede usar una categoria INACTIVA");
        }
        return categoria;
    }

    private Sucursal obtenerSucursalActiva(Integer idSucursal) {
        Sucursal sucursal = sucursalRepository.findByIdSucursalAndDeletedAtIsNull(idSucursal)
                .orElseThrow(() -> new RuntimeException("Sucursal no encontrada"));
        if (!"ACTIVO".equalsIgnoreCase(sucursal.getEstado())) {
            throw new RuntimeException("No se pueden gestionar productos en una sucursal INACTIVA");
        }
        return sucursal;
    }

    private void validarSkuUnicoPorSucursal(String sku, Integer idSucursal, Integer idProducto) {
        boolean existe = idProducto == null
                ? productoRepository.existsBySkuAndSucursalIdSucursal(sku, idSucursal)
                : productoRepository.existsBySkuAndSucursalIdSucursalAndIdProductoNot(sku, idSucursal, idProducto);
        if (existe) {
            throw new RuntimeException("El SKU '" + sku + "' ya existe en esta sucursal");
        }
    }

    private void validarCodigoExternoUnico(String codigoExterno, Integer idProducto) {
        if (codigoExterno == null) {
            return;
        }
        boolean existe = idProducto == null
                ? productoRepository.existsByCodigoExterno(codigoExterno)
                : productoRepository.existsByCodigoExternoAndIdProductoNot(codigoExterno, idProducto);
        if (existe) {
            throw new RuntimeException("El codigo externo '" + codigoExterno + "' ya pertenece a otro producto");
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

    private Integer idSucursalRequeridaParaAdmin(Integer idSucursalRequest) {
        if (idSucursalRequest == null) {
            throw new RuntimeException("Ingrese idSucursal");
        }
        return idSucursalRequest;
    }

    private void validarRolPermitido(Usuario usuario) {
        if (usuario.getRol() != Rol.ADMINISTRADOR
                && usuario.getRol() != Rol.VENTAS
                && usuario.getRol() != Rol.ALMACEN) {
            throw new RuntimeException("El usuario autenticado no tiene permisos para gestionar productos");
        }
    }

    private boolean esAdministrador(Usuario usuario) {
        return usuario.getRol() == Rol.ADMINISTRADOR;
    }

    private void validarPagina(int page) {
        if (page < 0) {
            throw new RuntimeException("El parametro page debe ser mayor o igual a 0");
        }
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
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private ProductoListItemResponse toListItemResponse(Producto producto) {
        Integer idCategoria = producto.getCategoria() != null ? producto.getCategoria().getIdCategoria() : null;
        String nombreCategoria = producto.getCategoria() != null ? producto.getCategoria().getNombreCategoria() : null;
        Integer idSucursal = producto.getSucursal() != null ? producto.getSucursal().getIdSucursal() : null;
        String nombreSucursal = producto.getSucursal() != null ? producto.getSucursal().getNombre() : null;

        return new ProductoListItemResponse(
                producto.getIdProducto(),
                producto.getSku(),
                producto.getNombre(),
                producto.getDescripcion(),
                producto.getEstado(),
                producto.getFechaCreacion(),
                producto.getCodigoExterno(),
                idCategoria,
                nombreCategoria,
                idSucursal,
                nombreSucursal);
    }
}
