package com.sistemapos.sistematextil.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sistemapos.sistematextil.model.EcommercePromocionCombo;
import com.sistemapos.sistematextil.model.EcommercePromocionComboItem;
import com.sistemapos.sistematextil.model.Producto;
import com.sistemapos.sistematextil.model.ProductoVariante;
import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.repositories.EcommercePromocionComboRepository;
import com.sistemapos.sistematextil.repositories.ProductoRepository;
import com.sistemapos.sistematextil.repositories.ProductoVarianteRepository;
import com.sistemapos.sistematextil.repositories.UsuarioRepository;
import com.sistemapos.sistematextil.util.ecommerce.EcommerceCarritoResumenResponse;
import com.sistemapos.sistematextil.util.ecommerce.EcommerceCarritoValidarResponse;
import com.sistemapos.sistematextil.util.ecommerce.EcommerceInicioComboResponse;
import com.sistemapos.sistematextil.util.ecommerce.EcommercePromocionComboEstadoRequest;
import com.sistemapos.sistematextil.util.ecommerce.EcommercePromocionComboRequest;
import com.sistemapos.sistematextil.util.ecommerce.EcommercePromocionComboResponse;
import com.sistemapos.sistematextil.util.paginacion.PagedResponse;
import com.sistemapos.sistematextil.util.usuario.Rol;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EcommercePromocionComboService {

    private static final String ACTIVO = "ACTIVO";
    private static final String INACTIVO = "INACTIVO";
    private static final BigDecimal CERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final int MAX_PUBLIC_SIZE = 24;

    private final EcommercePromocionComboRepository repository;
    private final ProductoRepository productoRepository;
    private final ProductoVarianteRepository productoVarianteRepository;
    private final UsuarioRepository usuarioRepository;
    private final PrecioOfertaService precioOfertaService;
    private final EcommerceCacheInvalidationService ecommerceCacheInvalidationService;

    @Transactional(readOnly = true)
    public PagedResponse<EcommercePromocionComboResponse> listar(int page, String vigencia, String correo) {
        validarAdmin(correo);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), 20);
        Page<EcommercePromocionCombo> promociones = switch (normalizarVigencia(vigencia)) {
            case "ACTIVAS" -> repository.listarAdminActivas(LocalDateTime.now(), pageable);
            case "VENCIDAS" -> repository.listarAdminVencidas(LocalDateTime.now(), pageable);
            default -> repository.findByDeletedAtIsNullOrderByCreatedAtDescIdEcommercePromocionComboDesc(pageable);
        };
        promociones = cargarCombosConItems(promociones);
        return PagedResponse.fromPage(promociones.map(this::toResponse));
    }

    @Transactional
    public EcommercePromocionComboResponse crear(EcommercePromocionComboRequest request, String correo) {
        Usuario usuario = validarAdmin(correo);
        EcommercePromocionCombo combo = new EcommercePromocionCombo();
        aplicar(combo, request, usuario);
        EcommercePromocionCombo guardado = repository.save(combo);
        ecommerceCacheInvalidationService.invalidate();
        return toResponse(guardado);
    }

    @Transactional
    public EcommercePromocionComboResponse actualizar(Integer id, EcommercePromocionComboRequest request, String correo) {
        Usuario usuario = validarAdmin(correo);
        EcommercePromocionCombo combo = obtener(id);
        aplicar(combo, request, usuario);
        EcommercePromocionCombo guardado = repository.save(combo);
        ecommerceCacheInvalidationService.invalidate();
        return toResponse(guardado);
    }

    @Transactional
    public EcommercePromocionComboResponse actualizarEstado(Integer id, EcommercePromocionComboEstadoRequest request, String correo) {
        validarAdmin(correo);
        EcommercePromocionCombo combo = obtener(id);
        combo.setEstado(normalizarEstado(request.estado()));
        EcommercePromocionCombo guardado = repository.save(combo);
        ecommerceCacheInvalidationService.invalidate();
        return toResponse(guardado);
    }

    @Transactional
    public void eliminar(Integer id, String correo) {
        validarAdmin(correo);
        EcommercePromocionCombo combo = obtener(id);
        combo.setDeletedAt(LocalDateTime.now());
        combo.setEstado(INACTIVO);
        repository.save(combo);
        ecommerceCacheInvalidationService.invalidate();
    }

    @Transactional(readOnly = true)
    public List<EcommercePromocionComboResponse> listarActivasPorProductos(Set<Integer> productoIds) {
        if (productoIds == null || productoIds.isEmpty()) {
            return List.of();
        }
        return repository.listarActivasPorProductos(LocalDateTime.now(), new ArrayList<>(productoIds))
                .stream()
                .filter(this::visibleEnEcommerce)
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EcommerceInicioComboResponse> listarInicioAleatorias(int limit) {
        if (limit <= 0) {
            return List.of();
        }
        return cargarCombosConItems(repository.listarActivasAleatorias(LocalDateTime.now(), PageRequest.of(0, limit)))
                .stream()
                .filter(this::visibleEnEcommerce)
                .map(this::toInicioResponse)
                .filter(Objects::nonNull)
                .limit(limit)
                .toList();
    }

    @Transactional(readOnly = true)
    public PagedResponse<EcommerceInicioComboResponse> listarPublicas(int page, int size) {
        Page<EcommercePromocionCombo> promociones = repository.listarPublicasVigentes(
                LocalDateTime.now(),
                PageRequest.of(Math.max(page, 0), normalizarPublicSize(size)));
        promociones = cargarCombosConItems(promociones);
        List<EcommerceInicioComboResponse> content = promociones.getContent().stream()
                .map(this::toInicioResponse)
                .filter(Objects::nonNull)
                .toList();
        return new PagedResponse<>(
                content,
                promociones.getNumber(),
                promociones.getSize(),
                promociones.getTotalPages(),
                promociones.getTotalElements(),
                content.size(),
                promociones.isFirst(),
                promociones.isLast(),
                content.isEmpty());
    }

    public EcommerceCarritoResumenResponse calcular(List<ItemPrecio> items) {
        if (items == null || items.isEmpty()) {
            return new EcommerceCarritoResumenResponse(CERO, CERO, CERO, List.of(), List.of());
        }
        BigDecimal subtotal = items.stream()
                .map(i -> i.precio().multiply(BigDecimal.valueOf(i.cantidad())))
                .reduce(CERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        List<Unidad> unidades = expandir(items);
        List<EcommercePromocionCombo> combos = repository.listarActivas(LocalDateTime.now())
                .stream()
                .filter(this::visibleEnEcommerce)
                .toList();
        List<Candidato> candidatos = candidatos(combos, unidades);
        Set<Integer> usadas = new HashSet<>();
        List<EcommerceCarritoResumenResponse.ComboAplicado> aplicados = new ArrayList<>();
        BigDecimal descuento = CERO;

        for (Candidato candidato : mejores(candidatos, new HashSet<>(), 0)) {
            usadas.add(candidato.a().index());
            usadas.add(candidato.b().index());
            descuento = descuento.add(candidato.descuento()).setScale(2, RoundingMode.HALF_UP);
            aplicados.add(new EcommerceCarritoResumenResponse.ComboAplicado(
                    candidato.combo().getIdEcommercePromocionCombo(),
                    candidato.combo().getNombre(),
                    regla(candidato.combo()),
                    candidato.normal(),
                    candidato.combo().getPrecioCombo(),
                    candidato.descuento()));
        }

        return new EcommerceCarritoResumenResponse(
                subtotal,
                descuento,
                subtotal.subtract(descuento).setScale(2, RoundingMode.HALF_UP),
                aplicados,
                pendientes(combos, unidades, usadas));
    }

    public List<EcommerceCarritoValidarResponse.PromocionNoDisponible> promocionesNoDisponibles(
            List<Integer> esperadas,
            EcommerceCarritoResumenResponse resumen) {
        if (esperadas == null || esperadas.isEmpty()) {
            return List.of();
        }
        List<Integer> idsEsperados = esperadas.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Set<Integer> disponibles = new HashSet<>();
        if (resumen != null) {
            resumen.combosAplicados().forEach(combo -> disponibles.add(combo.idPromocionCombo()));
            resumen.combosPendientes().forEach(combo -> disponibles.add(combo.idPromocionCombo()));
        }
        Map<Integer, String> nombres = repository.findAllById(idsEsperados).stream()
                .collect(Collectors.toMap(
                        EcommercePromocionCombo::getIdEcommercePromocionCombo,
                        EcommercePromocionCombo::getNombre,
                        (a, b) -> a));
        return idsEsperados.stream()
                .filter(id -> !disponibles.contains(id))
                .map(id -> new EcommerceCarritoValidarResponse.PromocionNoDisponible(
                        id,
                        nombres.getOrDefault(id, "Promocion no disponible")))
                .toList();
    }

    public List<ItemPrecio> itemsDesdeVariantes(List<ProductoVariante> variantes, Map<Integer, Integer> cantidades, Integer idSucursal) {
        return variantes.stream()
                .map(variante -> new ItemPrecio(
                        variante.getIdProductoVariante(),
                        variante.getProducto().getIdProducto(),
                        variante.getProducto().getNombre(),
                        BigDecimal.valueOf(precioOfertaService.resolverPrecioVigente(variante, idSucursal)).setScale(2, RoundingMode.HALF_UP),
                        cantidades.getOrDefault(variante.getIdProductoVariante(), 0)))
                .filter(item -> item.cantidad() > 0)
                .toList();
    }

    private void aplicar(EcommercePromocionCombo combo, EcommercePromocionComboRequest request, Usuario usuario) {
        if (request == null || request.items() == null || request.items().isEmpty()) {
            throw new RuntimeException("Ingrese productos para el combo");
        }
        combo.setNombre(normalizar(request.nombre()));
        if (combo.getNombre().isBlank()) {
            throw new RuntimeException("El nombre es obligatorio");
        }
        if (request.precioCombo() == null || request.precioCombo().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("El precio combo debe ser mayor a 0");
        }
        combo.setPrecioCombo(request.precioCombo().setScale(2, RoundingMode.HALF_UP));
        combo.setEstado(normalizarEstado(request.estado()));
        combo.setFechaInicio(request.fechaInicio());
        combo.setFechaFin(request.fechaFin());
        if (combo.getUsuarioCreacion() == null) {
            combo.setUsuarioCreacion(usuario);
        }
        if (combo.getFechaInicio() != null && combo.getFechaFin() != null && !combo.getFechaFin().isAfter(combo.getFechaInicio())) {
            throw new RuntimeException("fechaFin debe ser mayor a fechaInicio");
        }
        Map<Integer, Integer> cantidades = new LinkedHashMap<>();
        for (EcommercePromocionComboRequest.Item item : request.items()) {
            if (item.idProducto() == null) {
                throw new RuntimeException("El producto es obligatorio");
            }
            if (item.cantidadRequerida() == null || item.cantidadRequerida() <= 0) {
                throw new RuntimeException("La cantidad requerida debe ser mayor a 0");
            }
            cantidades.merge(item.idProducto(), item.cantidadRequerida(), Integer::sum);
        }
        if (cantidades.values().stream().mapToInt(Integer::intValue).sum() != 2) {
            throw new RuntimeException("El combo debe sumar exactamente 2 unidades");
        }
        combo.getItems().clear();
        for (Map.Entry<Integer, Integer> entry : cantidades.entrySet()) {
            Producto producto = productoRepository.findByIdProductoAndDeletedAtIsNull(entry.getKey())
                    .filter(p -> ACTIVO.equalsIgnoreCase(p.getEstado()))
                    .orElseThrow(() -> new RuntimeException("Producto no disponible para combo"));
            EcommercePromocionComboItem item = new EcommercePromocionComboItem();
            item.setProducto(producto);
            item.setCantidadRequerida(entry.getValue());
            combo.addItem(item);
        }
        validarAhorroMinimo(combo);
    }

    private void validarAhorroMinimo(EcommercePromocionCombo combo) {
        BigDecimal minimoNormal = CERO;
        for (EcommercePromocionComboItem item : combo.getItems()) {
            Double precioMinimo = productoVarianteRepository
                    .findByProductoIdProductoAndDeletedAtIsNull(item.getProducto().getIdProducto())
                    .stream()
                    .filter(v -> ACTIVO.equalsIgnoreCase(v.getEstado()))
                    .map(ProductoVariante::getPrecio)
                    .filter(Objects::nonNull)
                    .min(Double::compareTo)
                    .orElseThrow(() -> new RuntimeException("Producto sin variantes activas para combo"));
            minimoNormal = minimoNormal.add(BigDecimal.valueOf(precioMinimo)
                    .multiply(BigDecimal.valueOf(item.getCantidadRequerida())));
        }
        if (combo.getPrecioCombo().compareTo(minimoNormal.setScale(2, RoundingMode.HALF_UP)) >= 0) {
            throw new RuntimeException("El precio combo debe ser menor al precio normal minimo de los productos");
        }
    }

    private List<Candidato> candidatos(List<EcommercePromocionCombo> combos, List<Unidad> unidades) {
        List<Candidato> candidatos = new ArrayList<>();
        for (EcommercePromocionCombo combo : combos) {
            List<EcommercePromocionComboItem> items = combo.getItems();
            if (items == null || items.isEmpty()) {
                continue;
            }
            for (int i = 0; i < unidades.size(); i++) {
                for (int j = i + 1; j < unidades.size(); j++) {
                    Unidad a = unidades.get(i);
                    Unidad b = unidades.get(j);
                    if (!cumple(combo, a, b)) {
                        continue;
                    }
                    BigDecimal normal = a.precio().add(b.precio()).setScale(2, RoundingMode.HALF_UP);
                    BigDecimal descuento = normal.subtract(combo.getPrecioCombo()).setScale(2, RoundingMode.HALF_UP);
                    if (descuento.compareTo(BigDecimal.ZERO) > 0) {
                        candidatos.add(new Candidato(combo, a, b, normal, descuento));
                    }
                }
            }
        }
        candidatos.sort(Comparator.comparing(Candidato::descuento).reversed());
        return candidatos;
    }

    private List<Candidato> mejores(List<Candidato> candidatos, Set<Integer> usadas, int index) {
        // ponytail: busqueda exhaustiva para combos v1 de 2 unidades; cambiar a matching si el carrito/promos crecen mucho.
        if (index >= candidatos.size()) {
            return List.of();
        }
        Candidato actual = candidatos.get(index);
        List<Candidato> sinActual = mejores(candidatos, usadas, index + 1);
        if (usadas.contains(actual.a().index()) || usadas.contains(actual.b().index())) {
            return sinActual;
        }
        Set<Integer> usadasConActual = new HashSet<>(usadas);
        usadasConActual.add(actual.a().index());
        usadasConActual.add(actual.b().index());
        List<Candidato> conActual = new ArrayList<>();
        conActual.add(actual);
        conActual.addAll(mejores(candidatos, usadasConActual, index + 1));
        return totalDescuento(conActual).compareTo(totalDescuento(sinActual)) > 0 ? conActual : sinActual;
    }

    private BigDecimal totalDescuento(List<Candidato> candidatos) {
        return candidatos.stream()
                .map(Candidato::descuento)
                .reduce(CERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private boolean cumple(EcommercePromocionCombo combo, Unidad a, Unidad b) {
        Map<Integer, Long> productos = List.of(a, b).stream()
                .collect(Collectors.groupingBy(Unidad::productoId, Collectors.counting()));
        for (EcommercePromocionComboItem item : combo.getItems()) {
            Integer productoId = item.getProducto() != null ? item.getProducto().getIdProducto() : null;
            if (productos.getOrDefault(productoId, 0L).intValue() != item.getCantidadRequerida()) {
                return false;
            }
        }
        return productos.size() == combo.getItems().size();
    }

    private boolean visibleEnEcommerce(EcommercePromocionCombo combo) {
        if (combo.getItems() == null || combo.getItems().isEmpty()) {
            return false;
        }
        return combo.getItems().stream().allMatch(item -> {
            Producto producto = item.getProducto();
            return producto != null
                    && Boolean.TRUE.equals(producto.getPublicarEcommerce())
                    && ACTIVO.equalsIgnoreCase(normalizar(producto.getEstado()))
                    && ACTIVO.equalsIgnoreCase(normalizar(producto.getActivo()))
                    && producto.getDeletedAt() == null;
        });
    }

    private List<EcommerceCarritoResumenResponse.ComboPendiente> pendientes(
            List<EcommercePromocionCombo> combos,
            List<Unidad> unidades,
            Set<Integer> usadas) {
        Map<Integer, Long> disponibles = unidades.stream()
                .filter(u -> !usadas.contains(u.index()))
                .collect(Collectors.groupingBy(Unidad::productoId, Collectors.counting()));
        List<EcommerceCarritoResumenResponse.ComboPendiente> pendientes = new ArrayList<>();
        for (EcommercePromocionCombo combo : combos) {
            int faltante = 0;
            String nombreFaltante = null;
            boolean tieneAlgo = false;
            for (EcommercePromocionComboItem item : combo.getItems()) {
                Integer productoId = item.getProducto().getIdProducto();
                int tiene = disponibles.getOrDefault(productoId, 0L).intValue();
                if (tiene > 0) {
                    tieneAlgo = true;
                }
                int falta = item.getCantidadRequerida() - tiene;
                if (falta > 0) {
                    faltante += falta;
                    nombreFaltante = item.getProducto().getNombre();
                }
            }
            if (tieneAlgo && faltante > 0) {
                pendientes.add(new EcommerceCarritoResumenResponse.ComboPendiente(
                        combo.getIdEcommercePromocionCombo(),
                        combo.getNombre(),
                        regla(combo),
                        "Agrega " + faltante + " " + nombreFaltante + " mas para " + combo.getNombre()));
            }
        }
        return pendientes;
    }

    private List<Unidad> expandir(List<ItemPrecio> items) {
        List<Unidad> unidades = new ArrayList<>();
        for (ItemPrecio item : items) {
            for (int i = 0; i < item.cantidad(); i++) {
                unidades.add(new Unidad(unidades.size(), item.productoId(), item.productoNombre(), item.precio()));
            }
        }
        return unidades;
    }

    private EcommercePromocionCombo obtener(Integer id) {
        return repository.findById(id)
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> new RuntimeException("Promocion combo no encontrada"));
    }

    private Page<EcommercePromocionCombo> cargarCombosConItems(Page<EcommercePromocionCombo> page) {
        return new PageImpl<>(
                cargarCombosConItems(page.getContent()),
                page.getPageable(),
                page.getTotalElements());
    }

    private List<EcommercePromocionCombo> cargarCombosConItems(List<EcommercePromocionCombo> combos) {
        List<Integer> ids = combos.stream()
                .map(EcommercePromocionCombo::getIdEcommercePromocionCombo)
                .toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Integer, EcommercePromocionCombo> base = new LinkedHashMap<>();
        combos.forEach(combo -> base.put(combo.getIdEcommercePromocionCombo(), combo));
        repository.findByIdEcommercePromocionComboIn(ids)
                .forEach(combo -> base.put(combo.getIdEcommercePromocionCombo(), combo));
        return ids.stream()
                .map(base::get)
                .toList();
    }

    private Usuario validarAdmin(String correo) {
        Usuario usuario = usuarioRepository.findByCorreoAndDeletedAtIsNull(correo)
                .orElseThrow(() -> new RuntimeException("Usuario autenticado no encontrado"));
        Rol rol = usuario.getRol();
        if (rol == null || !rol.esAdministrador()) {
            throw new AccessDeniedException("Solo administradores pueden gestionar promociones ecommerce");
        }
        return usuario;
    }

    private String normalizarEstado(String estado) {
        String value = normalizar(estado).toUpperCase();
        if (value.isBlank()) {
            return ACTIVO;
        }
        if (!ACTIVO.equals(value) && !INACTIVO.equals(value)) {
            throw new RuntimeException("Estado permitido: ACTIVO o INACTIVO");
        }
        return value;
    }

    private String normalizarVigencia(String vigencia) {
        return normalizar(vigencia).toUpperCase();
    }

    private EcommercePromocionComboResponse toResponse(EcommercePromocionCombo combo) {
        return new EcommercePromocionComboResponse(
                combo.getIdEcommercePromocionCombo(),
                combo.getNombre(),
                regla(combo),
                combo.getPrecioCombo(),
                combo.getEstado(),
                combo.getFechaInicio(),
                combo.getFechaFin(),
                combo.getUsuarioCreacion() != null ? combo.getUsuarioCreacion().getIdUsuario() : null,
                nombreUsuario(combo.getUsuarioCreacion()),
                combo.getItems().stream()
                        .map(item -> new EcommercePromocionComboResponse.Item(
                                item.getProducto().getIdProducto(),
                                item.getProducto().getNombre(),
                                item.getCantidadRequerida()))
                        .toList());
    }

    private EcommerceInicioComboResponse toInicioResponse(EcommercePromocionCombo combo) {
        BigDecimal regular = precioRegularMinimo(combo);
        if (regular == null) {
            return null;
        }
        return new EcommerceInicioComboResponse(
                combo.getIdEcommercePromocionCombo(),
                combo.getNombre(),
                regla(combo),
                combo.getPrecioCombo(),
                regular,
                regular.subtract(combo.getPrecioCombo()).max(CERO).setScale(2, RoundingMode.HALF_UP),
                combo.getItems().stream()
                        .map(item -> {
                            Producto producto = item.getProducto();
                            return new EcommerceInicioComboResponse.Item(
                                    producto.getIdProducto(),
                                    producto.getNombre(),
                                    producto.getSlug(),
                                    item.getCantidadRequerida(),
                                    producto.getImagenGlobalUrl(),
                                    producto.getImagenGlobalThumbUrl());
                        })
                        .toList());
    }

    private BigDecimal precioRegularMinimo(EcommercePromocionCombo combo) {
        BigDecimal total = CERO;
        for (EcommercePromocionComboItem item : combo.getItems()) {
            Double precioMinimo = productoVarianteRepository
                    .findByProductoIdProductoAndDeletedAtIsNull(item.getProducto().getIdProducto())
                    .stream()
                    .filter(v -> ACTIVO.equalsIgnoreCase(v.getEstado()))
                    .map(ProductoVariante::getPrecio)
                    .filter(Objects::nonNull)
                    .min(Double::compareTo)
                    .orElse(null);
            if (precioMinimo == null) {
                return null;
            }
            total = total.add(BigDecimal.valueOf(precioMinimo)
                    .multiply(BigDecimal.valueOf(item.getCantidadRequerida())));
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private String regla(EcommercePromocionCombo combo) {
        if (combo.getItems() == null) {
            return combo.getNombre();
        }
        return combo.getItems().stream()
                .map(item -> item.getProducto().getNombre() + " x" + item.getCantidadRequerida())
                .collect(Collectors.joining(" + "));
    }

    private String nombreUsuario(Usuario usuario) {
        if (usuario == null) {
            return null;
        }
        return (normalizar(usuario.getNombre()) + " " + normalizar(usuario.getApellido())).trim();
    }

    private String normalizar(String value) {
        return value == null ? "" : value.trim();
    }

    private int normalizarPublicSize(int size) {
        if (size <= 0) {
            return 9;
        }
        return Math.min(size, MAX_PUBLIC_SIZE);
    }

    public record ItemPrecio(
            Integer idProductoVariante,
            Integer productoId,
            String productoNombre,
            BigDecimal precio,
            Integer cantidad
    ) {
    }

    private record Unidad(Integer index, Integer productoId, String productoNombre, BigDecimal precio) {
    }

    private record Candidato(
            EcommercePromocionCombo combo,
            Unidad a,
            Unidad b,
            BigDecimal normal,
            BigDecimal descuento
    ) {
    }
}
