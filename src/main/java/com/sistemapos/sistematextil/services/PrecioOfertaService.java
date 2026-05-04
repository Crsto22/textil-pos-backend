package com.sistemapos.sistematextil.services;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.sistemapos.sistematextil.model.ProductoVariante;
import com.sistemapos.sistematextil.model.ProductoVarianteOfertaSucursal;
import com.sistemapos.sistematextil.repositories.ProductoVarianteOfertaSucursalRepository;
import com.sistemapos.sistematextil.util.producto.TipoOfertaAplicada;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PrecioOfertaService {

    private final ProductoVarianteOfertaSucursalRepository productoVarianteOfertaSucursalRepository;

    public Double normalizarPrecioOferta(Double precioOferta) {
        if (precioOferta == null) {
            return null;
        }
        if (precioOferta <= 0) {
            throw new RuntimeException("El precio de oferta debe ser mayor a 0");
        }
        return precioOferta;
    }

    public void validarPrecioOferta(Double precio, Double precioOferta, LocalDateTime ofertaInicio, LocalDateTime ofertaFin) {
        if (precioOferta == null) {
            if (ofertaInicio != null || ofertaFin != null) {
                throw new RuntimeException("No puede registrar ofertaInicio/ofertaFin sin precioOferta");
            }
            return;
        }
        if (precio == null) {
            throw new RuntimeException("El precio es obligatorio para validar el precio de oferta");
        }
        if (precioOferta >= precio) {
            throw new RuntimeException("El precio de oferta debe ser menor al precio regular");
        }
        if ((ofertaInicio == null) != (ofertaFin == null)) {
            throw new RuntimeException("Debe enviar ofertaInicio y ofertaFin juntas");
        }
        if (ofertaInicio != null && ofertaFin != null && !ofertaFin.isAfter(ofertaInicio)) {
            throw new RuntimeException("ofertaFin debe ser mayor a ofertaInicio");
        }
    }

    public Map<Integer, ProductoVarianteOfertaSucursal> obtenerOfertasSucursalPorVariantes(
            Collection<Integer> idsProductoVariante,
            Integer idSucursal) {
        if (idSucursal == null || idsProductoVariante == null || idsProductoVariante.isEmpty()) {
            return Map.of();
        }
        List<Integer> ids = idsProductoVariante.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return productoVarianteOfertaSucursalRepository
                .findByProductoVarianteIdProductoVarianteInAndSucursalIdSucursalAndDeletedAtIsNull(ids, idSucursal)
                .stream()
                .filter(oferta -> oferta.getProductoVariante() != null
                        && oferta.getProductoVariante().getIdProductoVariante() != null)
                .collect(Collectors.toMap(
                        oferta -> oferta.getProductoVariante().getIdProductoVariante(),
                        Function.identity(),
                        (actual, ignorada) -> actual));
    }

    public ProductoVarianteOfertaSucursal obtenerOfertaSucursal(Integer idProductoVariante, Integer idSucursal) {
        if (idProductoVariante == null || idSucursal == null) {
            return null;
        }
        return productoVarianteOfertaSucursalRepository
                .findByProductoVarianteIdProductoVarianteAndSucursalIdSucursalAndDeletedAtIsNull(idProductoVariante, idSucursal)
                .orElse(null);
    }

    public ResultadoPrecioOferta resolver(ProductoVariante variante, Integer idSucursal) {
        ProductoVarianteOfertaSucursal ofertaSucursal = variante == null || idSucursal == null
                ? null
                : obtenerOfertaSucursal(variante.getIdProductoVariante(), idSucursal);
        return resolver(variante, ofertaSucursal);
    }

    public ResultadoPrecioOferta resolver(ProductoVariante variante, ProductoVarianteOfertaSucursal ofertaSucursal) {
        if (variante == null) {
            return ResultadoPrecioOferta.sinOferta();
        }

        Double precioRegular = variante.getPrecio();
        OfertaEvaluada ofertaEvaluadaSucursal = evaluar(
                precioRegular,
                ofertaSucursal != null ? ofertaSucursal.getPrecioOferta() : null,
                ofertaSucursal != null ? ofertaSucursal.getOfertaInicio() : null,
                ofertaSucursal != null ? ofertaSucursal.getOfertaFin() : null);
        if (ofertaEvaluadaSucursal.aplica()) {
            return new ResultadoPrecioOferta(
                    precioRegular,
                    ofertaEvaluadaSucursal.precioVigente(),
                    TipoOfertaAplicada.SUCURSAL,
                    ofertaSucursal != null ? ofertaSucursal.getIdProductoVarianteOfertaSucursal() : null,
                    ofertaSucursal != null ? ofertaSucursal.getPrecioOferta() : null,
                    ofertaSucursal != null ? ofertaSucursal.getOfertaInicio() : null,
                    ofertaSucursal != null ? ofertaSucursal.getOfertaFin() : null);
        }

        OfertaEvaluada ofertaEvaluadaGlobal = evaluar(
                precioRegular,
                variante.getPrecioOferta(),
                variante.getOfertaInicio(),
                variante.getOfertaFin());
        if (ofertaEvaluadaGlobal.aplica()) {
            return new ResultadoPrecioOferta(
                    precioRegular,
                    ofertaEvaluadaGlobal.precioVigente(),
                    TipoOfertaAplicada.GLOBAL,
                    null,
                    variante.getPrecioOferta(),
                    variante.getOfertaInicio(),
                    variante.getOfertaFin());
        }

        return new ResultadoPrecioOferta(
                precioRegular,
                precioRegular,
                TipoOfertaAplicada.NINGUNA,
                null,
                null,
                null,
                null);
    }

    public Double resolverPrecioVigente(ProductoVariante variante, Integer idSucursal) {
        return resolver(variante, idSucursal).precioVigente();
    }

    public Double resolverPrecioVigente(
            Double precio,
            Double precioOferta,
            LocalDateTime ofertaInicio,
            LocalDateTime ofertaFin) {
        return evaluar(precio, precioOferta, ofertaInicio, ofertaFin).precioVigente();
    }

    private OfertaEvaluada evaluar(
            Double precio,
            Double precioOferta,
            LocalDateTime ofertaInicio,
            LocalDateTime ofertaFin) {
        if (precioOferta == null || precio == null || precioOferta <= 0 || precioOferta >= precio) {
            return new OfertaEvaluada(false, precio);
        }
        if (ofertaInicio == null && ofertaFin == null) {
            return new OfertaEvaluada(true, precioOferta);
        }
        if (ofertaInicio == null || ofertaFin == null) {
            return new OfertaEvaluada(false, precio);
        }
        LocalDateTime ahora = LocalDateTime.now();
        if (ahora.isBefore(ofertaInicio) || ahora.isAfter(ofertaFin)) {
            return new OfertaEvaluada(false, precio);
        }
        return new OfertaEvaluada(true, precioOferta);
    }

    private record OfertaEvaluada(boolean aplica, Double precioVigente) {
    }

    public record ResultadoPrecioOferta(
            Double precioRegular,
            Double precioVigente,
            TipoOfertaAplicada tipoOfertaAplicada,
            Integer sucursalOfertaId,
            Double precioOfertaAplicada,
            LocalDateTime ofertaInicioAplicada,
            LocalDateTime ofertaFinAplicada) {

        public static ResultadoPrecioOferta sinOferta() {
            return new ResultadoPrecioOferta(null, null, TipoOfertaAplicada.NINGUNA, null, null, null, null);
        }
    }
}
