package com.sistemapos.sistematextil.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sistemapos.sistematextil.model.Color;
import com.sistemapos.sistematextil.model.Producto;
import com.sistemapos.sistematextil.model.ProductoColorImagen;
import com.sistemapos.sistematextil.model.ProductoVariante;
import com.sistemapos.sistematextil.model.Sucursal;
import com.sistemapos.sistematextil.model.Talla;
import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.repositories.ProductoColorImagenRepository;
import com.sistemapos.sistematextil.repositories.ProductoVarianteRepository;
import com.sistemapos.sistematextil.repositories.SucursalRepository;
import com.sistemapos.sistematextil.repositories.UsuarioRepository;
import com.sistemapos.sistematextil.util.producto.ProductoVariantePosResponse;
import com.sistemapos.sistematextil.util.usuario.Rol;

import jakarta.persistence.EntityManager;

@ExtendWith(MockitoExtension.class)
class ProductoVarianteServiceTest {

    @Mock
    private ProductoVarianteRepository repository;

    @Mock
    private ProductoColorImagenRepository productoColorImagenRepository;

    @Mock
    private ProductoService productoService;

    @Mock
    private TallaService tallaService;

    @Mock
    private ColorService colorService;

    @Mock
    private SucursalRepository sucursalRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private EntityManager entityManager;

    private ProductoVarianteService service;

    @BeforeEach
    void setUp() {
        service = new ProductoVarianteService(
                repository,
                productoColorImagenRepository,
                productoService,
                tallaService,
                colorService,
                sucursalRepository,
                usuarioRepository,
                entityManager);
    }

    @Test
    void escanearPorCodigoBarrasDevuelveVariantePosConImagenYPrecioVigente() {
        Usuario usuario = crearUsuario(Rol.VENTAS, 7);
        ProductoVariante variante = crearVariante("ABC-123", "SKU-1", "ACTIVO", 5);
        variante.setPrecio(120.0);
        variante.setPrecioMayor(95.0);
        variante.setPrecioOferta(99.0);
        variante.setOfertaInicio(LocalDateTime.now().minusDays(1));
        variante.setOfertaFin(LocalDateTime.now().plusDays(1));

        ProductoColorImagen imagen = new ProductoColorImagen();
        imagen.setIdColorImagen(44);
        imagen.setProducto(variante.getProducto());
        imagen.setColor(variante.getColor());
        imagen.setUrl("https://cdn.test/polo.jpg");
        imagen.setUrlThumb("https://cdn.test/polo-thumb.jpg");
        imagen.setOrden(1);
        imagen.setEsPrincipal(true);
        imagen.setEstado("ACTIVO");

        when(usuarioRepository.findByCorreoAndDeletedAtIsNull("ventas@test.com")).thenReturn(Optional.of(usuario));
        when(repository.findEscaneableByCodigoBarras("ABC-123", 7)).thenReturn(Optional.of(variante));
        when(productoColorImagenRepository.findByProductoIdProductoInAndDeletedAtIsNull(anyList()))
                .thenReturn(List.of(imagen));

        ProductoVariantePosResponse response = service.escanearPorCodigoBarras("  ABC-123  ", null, "ventas@test.com");

        assertEquals(variante.getIdProductoVariante(), response.idProductoVariante());
        assertEquals(7, response.idSucursal());
        assertEquals("ABC-123", response.codigoBarras());
        assertEquals("SKU-1", response.sku());
        assertEquals(99.0, response.precioVigente());
        assertNotNull(response.producto());
        assertEquals("Polo Premium", response.producto().nombre());
        assertNotNull(response.color());
        assertEquals("Negro", response.color().nombre());
        assertNotNull(response.talla());
        assertEquals("M", response.talla().nombre());
        assertNotNull(response.imagenPrincipal());
        assertEquals("https://cdn.test/polo.jpg", response.imagenPrincipal().url());
        assertEquals("https://cdn.test/polo-thumb.jpg", response.imagenPrincipal().urlThumb());
    }

    @Test
    void escanearPorCodigoBarrasFallaSiCodigoBarrasEstaVacio() {
        Usuario usuario = crearUsuario(Rol.VENTAS, 7);
        when(usuarioRepository.findByCorreoAndDeletedAtIsNull("ventas@test.com")).thenReturn(Optional.of(usuario));

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> service.escanearPorCodigoBarras("   ", null, "ventas@test.com"));

        assertEquals("Ingrese codigoBarras", error.getMessage());
        verifyNoInteractions(repository, productoColorImagenRepository);
    }

    @Test
    void escanearPorCodigoBarrasFallaSiNoExisteLaVariante() {
        Usuario usuario = crearUsuario(Rol.VENTAS, 7);
        when(usuarioRepository.findByCorreoAndDeletedAtIsNull("ventas@test.com")).thenReturn(Optional.of(usuario));
        when(repository.findEscaneableByCodigoBarras("ABC-404", 7)).thenReturn(Optional.empty());

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> service.escanearPorCodigoBarras("ABC-404", null, "ventas@test.com"));

        assertEquals("No existe una variante con el codigo de barras 'ABC-404' en la sucursal", error.getMessage());
    }

    @Test
    void escanearPorCodigoBarrasFallaSiLaVarianteEstaInactiva() {
        Usuario usuario = crearUsuario(Rol.VENTAS, 7);
        ProductoVariante variante = crearVariante("ABC-INACTIVO", "SKU-2", "INACTIVO", 3);

        when(usuarioRepository.findByCorreoAndDeletedAtIsNull("ventas@test.com")).thenReturn(Optional.of(usuario));
        when(repository.findEscaneableByCodigoBarras("ABC-INACTIVO", 7)).thenReturn(Optional.of(variante));

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> service.escanearPorCodigoBarras("ABC-INACTIVO", null, "ventas@test.com"));

        assertEquals("El producto 'Polo Premium Negro Talla M' no esta disponible", error.getMessage());
        verifyNoInteractions(productoColorImagenRepository);
    }

    @Test
    void escanearPorCodigoBarrasFallaSiLaVarianteNoTieneStock() {
        Usuario usuario = crearUsuario(Rol.VENTAS, 7);
        ProductoVariante variante = crearVariante("ABC-SIN-STOCK", "SKU-3", "ACTIVO", 0);

        when(usuarioRepository.findByCorreoAndDeletedAtIsNull("ventas@test.com")).thenReturn(Optional.of(usuario));
        when(repository.findEscaneableByCodigoBarras("ABC-SIN-STOCK", 7)).thenReturn(Optional.of(variante));

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> service.escanearPorCodigoBarras("ABC-SIN-STOCK", null, "ventas@test.com"));

        assertEquals("El producto 'Polo Premium Negro Talla M' no tiene stock disponible", error.getMessage());
        verifyNoInteractions(productoColorImagenRepository);
    }

    @Test
    void escanearPorCodigoBarrasFallaSiVentasConsultaOtraSucursal() {
        Usuario usuario = crearUsuario(Rol.VENTAS, 7);
        when(usuarioRepository.findByCorreoAndDeletedAtIsNull("ventas@test.com")).thenReturn(Optional.of(usuario));

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> service.escanearPorCodigoBarras("ABC-123", 8, "ventas@test.com"));

        assertEquals("No tiene permisos para consultar otra sucursal", error.getMessage());
        verifyNoInteractions(repository, productoColorImagenRepository);
    }

    @Test
    void escanearPorCodigoBarrasFallaSiAdminNoEnviaSucursal() {
        Usuario usuario = crearUsuario(Rol.ADMINISTRADOR, 1);
        when(usuarioRepository.findByCorreoAndDeletedAtIsNull("admin@test.com")).thenReturn(Optional.of(usuario));

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> service.escanearPorCodigoBarras("ABC-123", null, "admin@test.com"));

        assertEquals("Ingrese idSucursal", error.getMessage());
        verifyNoInteractions(repository, productoColorImagenRepository, sucursalRepository);
    }

    @Test
    void escanearPorCodigoBarrasPuedeResponderSinImagen() {
        Usuario usuario = crearUsuario(Rol.VENTAS, 7);
        ProductoVariante variante = crearVariante("ABC-SIN-IMG", "SKU-4", "ACTIVO", 2);

        when(usuarioRepository.findByCorreoAndDeletedAtIsNull("ventas@test.com")).thenReturn(Optional.of(usuario));
        when(repository.findEscaneableByCodigoBarras("ABC-SIN-IMG", 7)).thenReturn(Optional.of(variante));
        when(productoColorImagenRepository.findByProductoIdProductoInAndDeletedAtIsNull(anyList()))
                .thenReturn(List.of());

        ProductoVariantePosResponse response = service.escanearPorCodigoBarras("ABC-SIN-IMG", null, "ventas@test.com");

        assertNull(response.imagenPrincipal());
    }

    private Usuario crearUsuario(Rol rol, Integer idSucursal) {
        Usuario usuario = new Usuario();
        usuario.setCorreo(correoPorRol(rol));
        usuario.setRol(rol);
        if (idSucursal != null) {
            Sucursal sucursal = new Sucursal();
            sucursal.setIdSucursal(idSucursal);
            sucursal.setNombre("Sucursal Central");
            usuario.setSucursal(sucursal);
        }
        return usuario;
    }

    private String correoPorRol(Rol rol) {
        return switch (rol) {
            case ADMINISTRADOR -> "admin@test.com";
            case ALMACEN -> "almacen@test.com";
            default -> "ventas@test.com";
        };
    }

    private ProductoVariante crearVariante(String codigoBarras, String sku, String estado, Integer stock) {
        Producto producto = new Producto();
        producto.setIdProducto(10);
        producto.setNombre("Polo Premium");
        producto.setDescripcion("Algodon peinado");

        Color color = new Color();
        color.setIdColor(20);
        color.setNombre("Negro");
        color.setCodigo("#111111");

        Talla talla = new Talla();
        talla.setIdTalla(30);
        talla.setNombre("M");

        Sucursal sucursal = new Sucursal();
        sucursal.setIdSucursal(7);
        sucursal.setNombre("Sucursal Central");

        ProductoVariante variante = new ProductoVariante();
        variante.setIdProductoVariante(99);
        variante.setProducto(producto);
        variante.setColor(color);
        variante.setTalla(talla);
        variante.setSucursal(sucursal);
        variante.setCodigoBarras(codigoBarras);
        variante.setSku(sku);
        variante.setEstado(estado);
        variante.setStock(stock);
        variante.setPrecio(120.0);
        return variante;
    }
}
