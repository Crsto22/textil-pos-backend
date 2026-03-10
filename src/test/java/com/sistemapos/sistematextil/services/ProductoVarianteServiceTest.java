package com.sistemapos.sistematextil.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sistemapos.sistematextil.model.Color;
import com.sistemapos.sistematextil.model.Producto;
import com.sistemapos.sistematextil.model.ProductoVariante;
import com.sistemapos.sistematextil.model.Sucursal;
import com.sistemapos.sistematextil.model.Talla;
import com.sistemapos.sistematextil.repositories.ProductoColorImagenRepository;
import com.sistemapos.sistematextil.repositories.ProductoVarianteRepository;
import com.sistemapos.sistematextil.util.paginacion.PagedResponse;
import com.sistemapos.sistematextil.util.producto.ProductoImagenColorRow;
import com.sistemapos.sistematextil.util.producto.ProductoVarianteOfertaListItemResponse;
import com.sistemapos.sistematextil.util.producto.ProductoVarianteOfertaLoteItemRequest;
import com.sistemapos.sistematextil.util.producto.ProductoVarianteOfertaLoteUpdateRequest;

import jakarta.persistence.EntityManager;

@ExtendWith(MockitoExtension.class)
class ProductoVarianteServiceTest {

    private static final int DEFAULT_PAGE_SIZE = 10;

    @Mock
    private ProductoVarianteRepository repository;

    @Mock
    private ProductoService productoService;

    @Mock
    private ProductoColorImagenRepository productoColorImagenRepository;

    @Mock
    private TallaService tallaService;

    @Mock
    private ColorService colorService;

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
                entityManager);
    }

    @Test
    void listarConOfertaPaginadoDebeUsarPagedResponse() {
        ProductoVariante variante = crearVariante(10, "SKU-10", 120.0);
        variante.setPrecioOferta(95.0);

        PageRequest pageable = PageRequest.of(
                0,
                DEFAULT_PAGE_SIZE,
                org.springframework.data.domain.Sort.by("idProductoVariante").ascending());
        when(repository.findByPrecioOfertaIsNotNullAndDeletedAtIsNull(pageable))
                .thenReturn(new PageImpl<>(List.of(variante), pageable, 1));
        when(productoColorImagenRepository.obtenerResumenPorProductos(List.of(5)))
                .thenReturn(List.of(
                        new ProductoImagenColorRow(5, 3, "Azul", "#0011FF", "https://cdn/full-secundaria.webp",
                                "https://cdn/thumb-secundaria.webp", 2, false),
                        new ProductoImagenColorRow(5, 3, "Azul", "#0011FF", "https://cdn/full-principal.webp",
                                "https://cdn/thumb-principal.webp", 1, true),
                        new ProductoImagenColorRow(5, 4, "Rojo", "#FF0000", "https://cdn/full-rojo.webp",
                                "https://cdn/thumb-rojo.webp", 1, true)));

        PagedResponse<ProductoVarianteOfertaListItemResponse> response = service.listarConOfertaPaginado(0);

        assertThat(response.content()).hasSize(1);
        assertThat(response.page()).isZero();
        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.content().get(0).idProductoVariante()).isEqualTo(10);
        assertThat(response.content().get(0).precioOferta()).isEqualTo(95.0);
        assertThat(response.content().get(0).imagenUrl()).isEqualTo("https://cdn/full-principal.webp");
    }

    @Test
    void actualizarOfertasLoteDebeActualizarYQuitarOfertasEnUnaSolaSolicitud() {
        ProductoVariante varianteConOfertaNueva = crearVariante(10, "SKU-10", 120.0);
        ProductoVariante varianteSinOferta = crearVariante(20, "SKU-20", 90.0);
        varianteSinOferta.setPrecioOferta(75.0);
        varianteSinOferta.setOfertaInicio(LocalDateTime.of(2026, 3, 1, 8, 0));
        varianteSinOferta.setOfertaFin(LocalDateTime.of(2026, 3, 8, 22, 0));

        when(repository.findByIdProductoVarianteInAndDeletedAtIsNull(List.of(10, 20)))
                .thenReturn(List.of(varianteConOfertaNueva, varianteSinOferta));
        when(repository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        ProductoVarianteOfertaLoteUpdateRequest request = new ProductoVarianteOfertaLoteUpdateRequest(List.of(
                new ProductoVarianteOfertaLoteItemRequest(
                        10,
                        95.0,
                        LocalDateTime.of(2026, 3, 10, 9, 0),
                        LocalDateTime.of(2026, 3, 20, 21, 0)),
                new ProductoVarianteOfertaLoteItemRequest(20, null, null, null)));

        List<ProductoVarianteOfertaListItemResponse> response = service.actualizarOfertasLote(request);

        assertThat(response).hasSize(2);
        assertThat(response.get(0).idProductoVariante()).isEqualTo(10);
        assertThat(response.get(0).precioOferta()).isEqualTo(95.0);
        assertThat(response.get(1).idProductoVariante()).isEqualTo(20);
        assertThat(response.get(1).precioOferta()).isNull();

        assertThat(varianteConOfertaNueva.getPrecioOferta()).isEqualTo(95.0);
        assertThat(varianteConOfertaNueva.getOfertaInicio()).isEqualTo(LocalDateTime.of(2026, 3, 10, 9, 0));
        assertThat(varianteConOfertaNueva.getOfertaFin()).isEqualTo(LocalDateTime.of(2026, 3, 20, 21, 0));
        assertThat(varianteSinOferta.getPrecioOferta()).isNull();
        assertThat(varianteSinOferta.getOfertaInicio()).isNull();
        assertThat(varianteSinOferta.getOfertaFin()).isNull();

        verify(repository).saveAll(anyList());
    }

    @Test
    void actualizarOfertasLoteDebeRechazarVariantesDuplicadas() {
        ProductoVarianteOfertaLoteUpdateRequest request = new ProductoVarianteOfertaLoteUpdateRequest(List.of(
                new ProductoVarianteOfertaLoteItemRequest(10, 95.0, null, null),
                new ProductoVarianteOfertaLoteItemRequest(10, 80.0, null, null)));

        assertThatThrownBy(() -> service.actualizarOfertasLote(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("No puede repetir la variante con ID 10");
    }

    private ProductoVariante crearVariante(Integer id, String sku, Double precio) {
        Producto producto = new Producto();
        producto.setIdProducto(5);
        producto.setNombre("Camisa premium");

        Sucursal sucursal = new Sucursal();
        sucursal.setIdSucursal(2);
        sucursal.setNombre("Sucursal Central");

        Color color = new Color();
        color.setIdColor(3);
        color.setNombre("Azul");
        color.setCodigo("#0011FF");

        Talla talla = new Talla();
        talla.setIdTalla(4);
        talla.setNombre("M");

        ProductoVariante variante = new ProductoVariante();
        variante.setIdProductoVariante(id);
        variante.setProducto(producto);
        variante.setSucursal(sucursal);
        variante.setColor(color);
        variante.setTalla(talla);
        variante.setSku(sku);
        variante.setPrecio(precio);
        variante.setStock(10);
        variante.setEstado("ACTIVO");
        return variante;
    }
}
