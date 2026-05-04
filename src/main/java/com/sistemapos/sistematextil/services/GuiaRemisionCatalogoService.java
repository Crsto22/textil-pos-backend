package com.sistemapos.sistematextil.services;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sistemapos.sistematextil.model.Empresa;
import com.sistemapos.sistematextil.model.GuiaRemisionCatalogoConductor;
import com.sistemapos.sistematextil.model.GuiaRemisionCatalogoTransportista;
import com.sistemapos.sistematextil.model.GuiaRemisionCatalogoVehiculo;
import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.repositories.EmpresaRepository;
import com.sistemapos.sistematextil.repositories.GuiaRemisionCatalogoConductorRepository;
import com.sistemapos.sistematextil.repositories.GuiaRemisionCatalogoTransportistaRepository;
import com.sistemapos.sistematextil.repositories.GuiaRemisionCatalogoVehiculoRepository;
import com.sistemapos.sistematextil.repositories.UsuarioRepository;
import com.sistemapos.sistematextil.util.guiaremision.GuiaRemisionCatalogoConductorResponse;
import com.sistemapos.sistematextil.util.guiaremision.GuiaRemisionCatalogoTransportistaResponse;
import com.sistemapos.sistematextil.util.guiaremision.GuiaRemisionCatalogoVehiculoResponse;
import com.sistemapos.sistematextil.util.guiaremision.GuiaRemisionConductorRequest;
import com.sistemapos.sistematextil.util.guiaremision.GuiaRemisionTransportistaRequest;
import com.sistemapos.sistematextil.util.guiaremision.GuiaRemisionVehiculoRequest;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GuiaRemisionCatalogoService {

    private static final String ACTIVO = "ACTIVO";
    private static final String INACTIVO = "INACTIVO";
    private static final ZoneId LIMA_ZONE = ZoneId.of("America/Lima");

    private final GuiaRemisionCatalogoConductorRepository conductorRepository;
    private final GuiaRemisionCatalogoTransportistaRepository transportistaRepository;
    private final GuiaRemisionCatalogoVehiculoRepository vehiculoRepository;
    private final UsuarioRepository usuarioRepository;
    private final EmpresaRepository empresaRepository;

    @Transactional(readOnly = true)
    public List<GuiaRemisionCatalogoConductorResponse> listarConductores(String q, String correoAutenticado) {
        Empresa empresa = resolverEmpresaContexto(obtenerUsuarioAutenticado(correoAutenticado));
        return conductorRepository.buscarActivos(empresa.getIdEmpresa(), normalizarBusqueda(q)).stream()
                .map(this::toConductorResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public GuiaRemisionCatalogoConductorResponse obtenerConductor(Integer id, String correoAutenticado) {
        Empresa empresa = resolverEmpresaContexto(obtenerUsuarioAutenticado(correoAutenticado));
        return toConductorResponse(obtenerConductor(id, empresa.getIdEmpresa()));
    }

    @Transactional
    public GuiaRemisionCatalogoConductorResponse insertarConductor(
            GuiaRemisionConductorRequest request, String correoAutenticado) {
        Empresa empresa = resolverEmpresaContexto(obtenerUsuarioAutenticado(correoAutenticado));
        String nroDocumento = requireText(request.nroDocumento(), "El nroDocumento del conductor es obligatorio");
        validarDuplicadoConductor(empresa.getIdEmpresa(), nroDocumento, null);

        GuiaRemisionCatalogoConductor conductor = new GuiaRemisionCatalogoConductor();
        conductor.setEmpresa(empresa);
        conductor.setActivo(ACTIVO);
        conductor.setDeletedAt(null);
        aplicarConductor(conductor, request);
        return toConductorResponse(conductorRepository.save(conductor));
    }

    @Transactional
    public GuiaRemisionCatalogoConductorResponse actualizarConductor(
            Integer id, GuiaRemisionConductorRequest request, String correoAutenticado) {
        Empresa empresa = resolverEmpresaContexto(obtenerUsuarioAutenticado(correoAutenticado));
        GuiaRemisionCatalogoConductor conductor = obtenerConductor(id, empresa.getIdEmpresa());
        String nroDocumento = requireText(request.nroDocumento(), "El nroDocumento del conductor es obligatorio");
        validarDuplicadoConductor(empresa.getIdEmpresa(), nroDocumento, id);
        aplicarConductor(conductor, request);
        return toConductorResponse(conductorRepository.save(conductor));
    }

    @Transactional
    public void eliminarConductor(Integer id, String correoAutenticado) {
        Empresa empresa = resolverEmpresaContexto(obtenerUsuarioAutenticado(correoAutenticado));
        GuiaRemisionCatalogoConductor conductor = obtenerConductor(id, empresa.getIdEmpresa());
        conductor.setActivo(INACTIVO);
        conductor.setDeletedAt(nowLima());
        conductorRepository.save(conductor);
    }

    @Transactional(readOnly = true)
    public List<GuiaRemisionCatalogoTransportistaResponse> listarTransportistas(
            String q, String correoAutenticado) {
        Empresa empresa = resolverEmpresaContexto(obtenerUsuarioAutenticado(correoAutenticado));
        return transportistaRepository.buscarActivos(empresa.getIdEmpresa(), normalizarBusqueda(q)).stream()
                .map(this::toTransportistaResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public GuiaRemisionCatalogoTransportistaResponse obtenerTransportista(Integer id, String correoAutenticado) {
        Empresa empresa = resolverEmpresaContexto(obtenerUsuarioAutenticado(correoAutenticado));
        return toTransportistaResponse(obtenerTransportista(id, empresa.getIdEmpresa()));
    }

    @Transactional
    public GuiaRemisionCatalogoTransportistaResponse insertarTransportista(
            GuiaRemisionTransportistaRequest request, String correoAutenticado) {
        Empresa empresa = resolverEmpresaContexto(obtenerUsuarioAutenticado(correoAutenticado));
        String nroDoc = requireText(request.transportistaNroDoc(), "El nroDoc del transportista es obligatorio");
        validarDuplicadoTransportista(empresa.getIdEmpresa(), nroDoc, null);

        GuiaRemisionCatalogoTransportista transportista = new GuiaRemisionCatalogoTransportista();
        transportista.setEmpresa(empresa);
        transportista.setActivo(ACTIVO);
        transportista.setDeletedAt(null);
        aplicarTransportista(transportista, request);
        return toTransportistaResponse(transportistaRepository.save(transportista));
    }

    @Transactional
    public GuiaRemisionCatalogoTransportistaResponse actualizarTransportista(
            Integer id, GuiaRemisionTransportistaRequest request, String correoAutenticado) {
        Empresa empresa = resolverEmpresaContexto(obtenerUsuarioAutenticado(correoAutenticado));
        GuiaRemisionCatalogoTransportista transportista = obtenerTransportista(id, empresa.getIdEmpresa());
        String nroDoc = requireText(request.transportistaNroDoc(), "El nroDoc del transportista es obligatorio");
        validarDuplicadoTransportista(empresa.getIdEmpresa(), nroDoc, id);
        aplicarTransportista(transportista, request);
        return toTransportistaResponse(transportistaRepository.save(transportista));
    }

    @Transactional
    public void eliminarTransportista(Integer id, String correoAutenticado) {
        Empresa empresa = resolverEmpresaContexto(obtenerUsuarioAutenticado(correoAutenticado));
        GuiaRemisionCatalogoTransportista transportista = obtenerTransportista(id, empresa.getIdEmpresa());
        transportista.setActivo(INACTIVO);
        transportista.setDeletedAt(nowLima());
        transportistaRepository.save(transportista);
    }

    @Transactional(readOnly = true)
    public List<GuiaRemisionCatalogoVehiculoResponse> listarVehiculos(String q, String correoAutenticado) {
        Empresa empresa = resolverEmpresaContexto(obtenerUsuarioAutenticado(correoAutenticado));
        return vehiculoRepository.buscarActivos(empresa.getIdEmpresa(), normalizarBusqueda(q)).stream()
                .map(this::toVehiculoResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public GuiaRemisionCatalogoVehiculoResponse obtenerVehiculo(Integer id, String correoAutenticado) {
        Empresa empresa = resolverEmpresaContexto(obtenerUsuarioAutenticado(correoAutenticado));
        return toVehiculoResponse(obtenerVehiculo(id, empresa.getIdEmpresa()));
    }

    @Transactional
    public GuiaRemisionCatalogoVehiculoResponse insertarVehiculo(
            GuiaRemisionVehiculoRequest request, String correoAutenticado) {
        Empresa empresa = resolverEmpresaContexto(obtenerUsuarioAutenticado(correoAutenticado));
        String placa = normalizarPlaca(request.placa());
        validarDuplicadoVehiculo(empresa.getIdEmpresa(), placa, null);

        GuiaRemisionCatalogoVehiculo vehiculo = new GuiaRemisionCatalogoVehiculo();
        vehiculo.setEmpresa(empresa);
        vehiculo.setActivo(ACTIVO);
        vehiculo.setDeletedAt(null);
        aplicarVehiculo(vehiculo, request);
        return toVehiculoResponse(vehiculoRepository.save(vehiculo));
    }

    @Transactional
    public GuiaRemisionCatalogoVehiculoResponse actualizarVehiculo(
            Integer id, GuiaRemisionVehiculoRequest request, String correoAutenticado) {
        Empresa empresa = resolverEmpresaContexto(obtenerUsuarioAutenticado(correoAutenticado));
        GuiaRemisionCatalogoVehiculo vehiculo = obtenerVehiculo(id, empresa.getIdEmpresa());
        String placa = normalizarPlaca(request.placa());
        validarDuplicadoVehiculo(empresa.getIdEmpresa(), placa, id);
        aplicarVehiculo(vehiculo, request);
        return toVehiculoResponse(vehiculoRepository.save(vehiculo));
    }

    @Transactional
    public void eliminarVehiculo(Integer id, String correoAutenticado) {
        Empresa empresa = resolverEmpresaContexto(obtenerUsuarioAutenticado(correoAutenticado));
        GuiaRemisionCatalogoVehiculo vehiculo = obtenerVehiculo(id, empresa.getIdEmpresa());
        vehiculo.setActivo(INACTIVO);
        vehiculo.setDeletedAt(nowLima());
        vehiculoRepository.save(vehiculo);
    }

    @Transactional(readOnly = true)
    public List<GuiaRemisionCatalogoConductor> obtenerConductoresPorIds(List<Integer> ids, Integer idEmpresa) {
        List<Integer> normalizados = normalizarIds(ids);
        if (normalizados.isEmpty()) return List.of();
        List<GuiaRemisionCatalogoConductor> encontrados = conductorRepository
                .findByIdCatalogoConductorInAndEmpresa_IdEmpresaAndDeletedAtIsNull(normalizados, idEmpresa);
        validarIdsEncontrados(normalizados, encontrados.stream().map(GuiaRemisionCatalogoConductor::getIdCatalogoConductor).toList(),
                "conductores");
        return ordenarConductores(encontrados, normalizados);
    }

    @Transactional(readOnly = true)
    public List<GuiaRemisionCatalogoTransportista> obtenerTransportistasPorIds(List<Integer> ids, Integer idEmpresa) {
        List<Integer> normalizados = normalizarIds(ids);
        if (normalizados.isEmpty()) return List.of();
        List<GuiaRemisionCatalogoTransportista> encontrados = transportistaRepository
                .findByIdCatalogoTransportistaInAndEmpresa_IdEmpresaAndDeletedAtIsNull(normalizados, idEmpresa);
        validarIdsEncontrados(normalizados,
                encontrados.stream().map(GuiaRemisionCatalogoTransportista::getIdCatalogoTransportista).toList(),
                "transportistas");
        return ordenarTransportistas(encontrados, normalizados);
    }

    @Transactional(readOnly = true)
    public List<GuiaRemisionCatalogoVehiculo> obtenerVehiculosPorIds(List<Integer> ids, Integer idEmpresa) {
        List<Integer> normalizados = normalizarIds(ids);
        if (normalizados.isEmpty()) return List.of();
        List<GuiaRemisionCatalogoVehiculo> encontrados = vehiculoRepository
                .findByIdCatalogoVehiculoInAndEmpresa_IdEmpresaAndDeletedAtIsNull(normalizados, idEmpresa);
        validarIdsEncontrados(normalizados,
                encontrados.stream().map(GuiaRemisionCatalogoVehiculo::getIdCatalogoVehiculo).toList(),
                "vehiculos");
        return ordenarVehiculos(encontrados, normalizados);
    }

    private GuiaRemisionCatalogoConductor obtenerConductor(Integer id, Integer idEmpresa) {
        return conductorRepository.findByIdCatalogoConductorAndEmpresa_IdEmpresaAndDeletedAtIsNull(id, idEmpresa)
                .orElseThrow(() -> new RuntimeException("Conductor no encontrado"));
    }

    private GuiaRemisionCatalogoTransportista obtenerTransportista(Integer id, Integer idEmpresa) {
        return transportistaRepository
                .findByIdCatalogoTransportistaAndEmpresa_IdEmpresaAndDeletedAtIsNull(id, idEmpresa)
                .orElseThrow(() -> new RuntimeException("Transportista no encontrado"));
    }

    private GuiaRemisionCatalogoVehiculo obtenerVehiculo(Integer id, Integer idEmpresa) {
        return vehiculoRepository.findByIdCatalogoVehiculoAndEmpresa_IdEmpresaAndDeletedAtIsNull(id, idEmpresa)
                .orElseThrow(() -> new RuntimeException("Vehiculo no encontrado"));
    }

    private void aplicarConductor(GuiaRemisionCatalogoConductor conductor, GuiaRemisionConductorRequest request) {
        conductor.setTipoDocumento(request.tipoDocumento() != null ? request.tipoDocumento().trim() : "1");
        conductor.setNroDocumento(requireText(request.nroDocumento(), "El nroDocumento del conductor es obligatorio"));
        conductor.setNombres(requireText(request.nombres(), "Los nombres del conductor son obligatorios"));
        conductor.setApellidos(requireText(request.apellidos(), "Los apellidos del conductor son obligatorios"));
        conductor.setLicencia(requireText(request.licencia(), "La licencia del conductor es obligatoria"));
        conductor.setEsPrincipal(request.esPrincipal() != null ? request.esPrincipal() : true);
    }

    private void aplicarTransportista(
            GuiaRemisionCatalogoTransportista transportista,
            GuiaRemisionTransportistaRequest request) {
        transportista.setTransportistaTipoDoc(
                request.transportistaTipoDoc() != null ? request.transportistaTipoDoc().trim() : "6");
        transportista.setTransportistaNroDoc(requireText(request.transportistaNroDoc(),
                "El nroDoc del transportista es obligatorio"));
        transportista.setTransportistaRazonSocial(requireText(request.transportistaRazonSocial(),
                "La razon social del transportista es obligatoria"));
        transportista.setTransportistaRegistroMtc(normalizarTexto(request.transportistaRegistroMtc(), 20));
    }

    private void aplicarVehiculo(GuiaRemisionCatalogoVehiculo vehiculo, GuiaRemisionVehiculoRequest request) {
        vehiculo.setPlaca(normalizarPlaca(request.placa()));
        vehiculo.setEsPrincipal(request.esPrincipal() != null ? request.esPrincipal() : true);
    }

    private void validarDuplicadoConductor(Integer idEmpresa, String nroDocumento, Integer idActual) {
        boolean existe = idActual == null
                ? conductorRepository.existsByEmpresa_IdEmpresaAndNroDocumentoAndDeletedAtIsNull(idEmpresa, nroDocumento)
                : conductorRepository.existsByEmpresa_IdEmpresaAndNroDocumentoAndDeletedAtIsNullAndIdCatalogoConductorNot(
                        idEmpresa, nroDocumento, idActual);
        if (existe) {
            throw new RuntimeException("Ya existe un conductor con ese nroDocumento");
        }
    }

    private void validarDuplicadoTransportista(Integer idEmpresa, String nroDocumento, Integer idActual) {
        boolean existe = idActual == null
                ? transportistaRepository.existsByEmpresa_IdEmpresaAndTransportistaNroDocAndDeletedAtIsNull(
                        idEmpresa, nroDocumento)
                : transportistaRepository
                        .existsByEmpresa_IdEmpresaAndTransportistaNroDocAndDeletedAtIsNullAndIdCatalogoTransportistaNot(
                                idEmpresa, nroDocumento, idActual);
        if (existe) {
            throw new RuntimeException("Ya existe un transportista con ese nroDoc");
        }
    }

    private void validarDuplicadoVehiculo(Integer idEmpresa, String placa, Integer idActual) {
        boolean existe = idActual == null
                ? vehiculoRepository.existsByEmpresa_IdEmpresaAndPlacaAndDeletedAtIsNull(idEmpresa, placa)
                : vehiculoRepository.existsByEmpresa_IdEmpresaAndPlacaAndDeletedAtIsNullAndIdCatalogoVehiculoNot(
                        idEmpresa, placa, idActual);
        if (existe) {
            throw new RuntimeException("Ya existe un vehiculo con esa placa");
        }
    }

    private GuiaRemisionCatalogoConductorResponse toConductorResponse(GuiaRemisionCatalogoConductor conductor) {
        return new GuiaRemisionCatalogoConductorResponse(
                conductor.getIdCatalogoConductor(),
                conductor.getEmpresa() != null ? conductor.getEmpresa().getIdEmpresa() : null,
                conductor.getEmpresa() != null ? conductor.getEmpresa().getNombre() : null,
                conductor.getTipoDocumento(),
                conductor.getNroDocumento(),
                conductor.getNombres(),
                conductor.getApellidos(),
                conductor.getLicencia(),
                conductor.getEsPrincipal());
    }

    private GuiaRemisionCatalogoTransportistaResponse toTransportistaResponse(
            GuiaRemisionCatalogoTransportista transportista) {
        return new GuiaRemisionCatalogoTransportistaResponse(
                transportista.getIdCatalogoTransportista(),
                transportista.getEmpresa() != null ? transportista.getEmpresa().getIdEmpresa() : null,
                transportista.getEmpresa() != null ? transportista.getEmpresa().getNombre() : null,
                transportista.getTransportistaTipoDoc(),
                transportista.getTransportistaNroDoc(),
                transportista.getTransportistaRazonSocial(),
                transportista.getTransportistaRegistroMtc());
    }

    private GuiaRemisionCatalogoVehiculoResponse toVehiculoResponse(GuiaRemisionCatalogoVehiculo vehiculo) {
        return new GuiaRemisionCatalogoVehiculoResponse(
                vehiculo.getIdCatalogoVehiculo(),
                vehiculo.getEmpresa() != null ? vehiculo.getEmpresa().getIdEmpresa() : null,
                vehiculo.getEmpresa() != null ? vehiculo.getEmpresa().getNombre() : null,
                vehiculo.getPlaca(),
                vehiculo.getEsPrincipal());
    }

    private Usuario obtenerUsuarioAutenticado(String correo) {
        if (correo == null || correo.isBlank()) {
            throw new RuntimeException("No autenticado");
        }
        return usuarioRepository.findByCorreoAndDeletedAtIsNull(correo)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
    }

    private Empresa resolverEmpresaContexto(Usuario usuario) {
        if (usuario.getSucursal() != null && usuario.getSucursal().getEmpresa() != null) {
            return usuario.getSucursal().getEmpresa();
        }
        return empresaRepository.findTopByOrderByIdEmpresaAsc()
                .orElseThrow(() -> new RuntimeException("No se pudo determinar la empresa del usuario"));
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) throw new RuntimeException(message);
        return value.trim();
    }

    private String normalizarBusqueda(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String normalizarTexto(String value, int maxLen) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        return trimmed.length() > maxLen ? trimmed.substring(0, maxLen) : trimmed;
    }

    private String normalizarPlaca(String placa) {
        String normalizada = requireText(placa, "La placa del vehiculo es obligatoria")
                .toUpperCase(Locale.ROOT)
                .replace(" ", "")
                .replace("-", "");
        if (!normalizada.matches("[A-Z0-9]{6,10}")) {
            throw new RuntimeException(
                    "La placa del vehiculo debe tener entre 6 y 10 caracteres alfanumericos, sin simbolos");
        }
        return normalizada;
    }

    private List<Integer> normalizarIds(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return new LinkedHashSet<>(ids).stream().toList();
    }

    private void validarIdsEncontrados(List<Integer> solicitados, List<Integer> encontrados, String label) {
        if (solicitados.size() != encontrados.size()) {
            List<Integer> faltantes = solicitados.stream()
                    .filter(id -> !encontrados.contains(id))
                    .toList();
            throw new RuntimeException("No se encontraron todos los IDs de " + label + ": " + faltantes);
        }
    }

    private List<GuiaRemisionCatalogoConductor> ordenarConductores(
            List<GuiaRemisionCatalogoConductor> encontrados,
            List<Integer> idsOrdenados) {
        return idsOrdenados.stream()
                .map(id -> encontrados.stream()
                        .filter(item -> item.getIdCatalogoConductor().equals(id))
                        .findFirst()
                        .orElseThrow())
                .toList();
    }

    private List<GuiaRemisionCatalogoTransportista> ordenarTransportistas(
            List<GuiaRemisionCatalogoTransportista> encontrados,
            List<Integer> idsOrdenados) {
        return idsOrdenados.stream()
                .map(id -> encontrados.stream()
                        .filter(item -> item.getIdCatalogoTransportista().equals(id))
                        .findFirst()
                        .orElseThrow())
                .toList();
    }

    private List<GuiaRemisionCatalogoVehiculo> ordenarVehiculos(
            List<GuiaRemisionCatalogoVehiculo> encontrados,
            List<Integer> idsOrdenados) {
        return idsOrdenados.stream()
                .map(id -> encontrados.stream()
                        .filter(item -> item.getIdCatalogoVehiculo().equals(id))
                        .findFirst()
                        .orElseThrow())
                .toList();
    }

    private LocalDateTime nowLima() {
        return LocalDateTime.now(LIMA_ZONE);
    }
}
