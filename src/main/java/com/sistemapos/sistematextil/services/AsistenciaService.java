package com.sistemapos.sistematextil.services;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sistemapos.sistematextil.model.DispositivoAsistencia;
import com.sistemapos.sistematextil.model.CargoTrabajador;
import com.sistemapos.sistematextil.model.MarcacionAsistencia;
import com.sistemapos.sistematextil.model.Sucursal;
import com.sistemapos.sistematextil.model.Trabajador;
import com.sistemapos.sistematextil.model.Turno;
import com.sistemapos.sistematextil.model.TurnoDia;
import com.sistemapos.sistematextil.repositories.DispositivoAsistenciaRepository;
import com.sistemapos.sistematextil.repositories.CargoTrabajadorRepository;
import com.sistemapos.sistematextil.repositories.MarcacionAsistenciaRepository;
import com.sistemapos.sistematextil.repositories.SucursalRepository;
import com.sistemapos.sistematextil.repositories.TrabajadorRepository;
import com.sistemapos.sistematextil.repositories.TurnoRepository;
import com.sistemapos.sistematextil.repositories.UsuarioRepository;
import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.util.asistencia.AsistenciaDtos.DispositivoRequest;
import com.sistemapos.sistematextil.util.asistencia.AsistenciaDtos.DispositivoResponse;
import com.sistemapos.sistematextil.util.asistencia.AsistenciaDtos.CargoEstadoRequest;
import com.sistemapos.sistematextil.util.asistencia.AsistenciaDtos.CargoRequest;
import com.sistemapos.sistematextil.util.asistencia.AsistenciaDtos.CargoResponse;
import com.sistemapos.sistematextil.util.asistencia.AsistenciaDtos.AnalisisEstado;
import com.sistemapos.sistematextil.util.asistencia.AsistenciaDtos.AnalisisEvolucion;
import com.sistemapos.sistematextil.util.asistencia.AsistenciaDtos.AnalisisIndicadores;
import com.sistemapos.sistematextil.util.asistencia.AsistenciaDtos.AnalisisResponse;
import com.sistemapos.sistematextil.util.asistencia.AsistenciaDtos.AnalisisSucursal;
import com.sistemapos.sistematextil.util.asistencia.AsistenciaDtos.AnalisisTrabajador;
import com.sistemapos.sistematextil.util.asistencia.AsistenciaDtos.MarcacionResponse;
import com.sistemapos.sistematextil.util.asistencia.AsistenciaDtos.MarcacionManualRequest;
import com.sistemapos.sistematextil.util.asistencia.AsistenciaDtos.AnularMarcacionRequest;
import com.sistemapos.sistematextil.util.asistencia.AsistenciaDtos.ResumenResponse;
import com.sistemapos.sistematextil.util.asistencia.AsistenciaDtos.ResumenSemanalResponse;
import com.sistemapos.sistematextil.util.asistencia.AsistenciaDtos.SesionAsistenciaResponse;
import com.sistemapos.sistematextil.util.asistencia.AsistenciaDtos.SucursalMarcacionResponse;
import com.sistemapos.sistematextil.util.asistencia.AsistenciaDtos.TrabajadorRequest;
import com.sistemapos.sistematextil.util.asistencia.AsistenciaDtos.TrabajadorResponse;
import com.sistemapos.sistematextil.util.asistencia.AsistenciaExcelExporter;
import com.sistemapos.sistematextil.util.paginacion.PagedResponse;
import com.sistemapos.sistematextil.util.turno.DiaSemana;

import jakarta.persistence.criteria.JoinType;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AsistenciaService {

    private static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");
    private static final DateTimeFormatter ADMS_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter ADMS_DATE_TIME_SLASH = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
    private static final Set<String> ESTADOS_RESUMEN = Set.of(
            "PRESENTE", "TARDANZA", "FALTA", "INCOMPLETA", "EN_CURSO", "PENDIENTE", "DESCANSO",
            "TRABAJO_EN_DESCANSO", "SIN_REGISTRO", "REGISTRO_UNICO", "REGISTRO_INCOMPLETO",
            "REQUIERE_REVISION", "SALIDA_ANTICIPADA");
    private static final int TOLERANCIA_SALIDA_MINUTOS = 10;

    private final TrabajadorRepository trabajadorRepository;
    private final CargoTrabajadorRepository cargoRepository;
    private final DispositivoAsistenciaRepository dispositivoRepository;
    private final MarcacionAsistenciaRepository marcacionRepository;
    private final SucursalRepository sucursalRepository;
    private final TurnoRepository turnoRepository;
    private final UsuarioRepository usuarioRepository;

    @Value("${application.pagination.default-size:10}")
    private int pageSize;

    @Value("${asistencia.adms.max-events:1000}")
    private int admsMaxEvents;

    @Value("${asistencia.adms.max-past-days:7}")
    private int admsMaxPastDays;

    @Value("${asistencia.adms.max-future-minutes:10}")
    private int admsMaxFutureMinutes;

    @Value("${asistencia.adms.max-body-bytes:262144}")
    private int admsMaxBodyBytes = 256 * 1024;

    @Value("${asistencia.duplicado-segundos:120}")
    private int duplicadoSegundos = 120;

    @Transactional(readOnly = true)
    public PagedResponse<TrabajadorResponse> listarTrabajadores(
            String q, Integer idSucursal, String estado, String modalidad, Boolean rotativo, int page) {
        validarPagina(page);
        Specification<Trabajador> spec = (root, query, cb) -> cb.isNull(root.get("deletedAt"));
        spec = spec.and(busquedaTrabajadorSpec(q));
        if (idSucursal != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("sucursal").get("idSucursal"), idSucursal));
        }
        String estadoNormalizado = normalizarEstadoOpcional(estado);
        if (estadoNormalizado != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("estado"), estadoNormalizado));
        }
        String modalidadNormalizada = normalizarModalidad(modalidad);
        if (modalidadNormalizada != null) {
            spec = spec.and((root, query, cb) -> "CON_TURNO".equals(modalidadNormalizada)
                    ? cb.isNotNull(root.get("turno")) : cb.isNull(root.get("turno")));
        }
        if (rotativo != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("rotativo"), rotativo));
        }
        Page<TrabajadorResponse> result = trabajadorRepository.findAll(spec,
                PageRequest.of(page, pageSize, Sort.by("apellidos", "nombres"))).map(this::toTrabajadorResponse);
        return PagedResponse.fromPage(result);
    }

    @Transactional(readOnly = true)
    public PagedResponse<CargoResponse> listarCargos(String q, String estado, int page) {
        validarPagina(page);
        Specification<CargoTrabajador> spec = (root, query, cb) -> cb.conjunction();
        String term = normalizar(q);
        if (term != null) {
            spec = spec.and((root, query, cb) -> cb.like(
                    cb.lower(root.get("nombre")), "%" + term.toLowerCase(Locale.ROOT) + "%"));
        }
        String estadoNormalizado = normalizarEstadoOpcional(estado);
        if (estadoNormalizado != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("estado"), estadoNormalizado));
        }
        Page<CargoResponse> result = cargoRepository.findAll(spec,
                PageRequest.of(page, pageSize, Sort.by("nombre"))).map(this::toCargoResponse);
        return PagedResponse.fromPage(result);
    }

    @Transactional
    public CargoResponse crearCargo(CargoRequest request) {
        String nombre = nombreCargo(request.nombre());
        validarNombreCargoUnico(nombre, 0);
        CargoTrabajador cargo = new CargoTrabajador();
        cargo.setNombre(nombre);
        cargo.setEstado("ACTIVO");
        return toCargoResponse(cargoRepository.save(cargo));
    }

    @Transactional
    public CargoResponse actualizarCargo(Integer id, CargoRequest request) {
        CargoTrabajador cargo = cargoRepository.findByIdCargo(id)
                .orElseThrow(() -> new IllegalArgumentException("Cargo no encontrado"));
        String nombre = nombreCargo(request.nombre());
        validarNombreCargoUnico(nombre, id);
        cargo.setNombre(nombre);
        return toCargoResponse(cargoRepository.save(cargo));
    }

    @Transactional
    public CargoResponse actualizarEstadoCargo(Integer id, CargoEstadoRequest request) {
        CargoTrabajador cargo = cargoRepository.findByIdCargo(id)
                .orElseThrow(() -> new IllegalArgumentException("Cargo no encontrado"));
        String estado = estadoRequerido(request.estado());
        cargo.setEstado(estado);
        cargo.setDeletedAt("INACTIVO".equals(estado) ? ahoraLima() : null);
        return toCargoResponse(cargoRepository.save(cargo));
    }

    @Transactional
    public TrabajadorResponse crearTrabajador(TrabajadorRequest request) {
        Trabajador trabajador = new Trabajador();
        aplicarTrabajador(trabajador, request, true);
        Trabajador guardado = trabajadorRepository.saveAndFlush(trabajador);
        marcacionRepository.vincularMarcaciones(guardado.getIdTrabajador(), guardado.getCodigoZkteco());
        return toTrabajadorResponse(guardado);
    }

    @Transactional
    public TrabajadorResponse actualizarTrabajador(Integer id, TrabajadorRequest request) {
        Trabajador trabajador = trabajadorRepository.findByIdTrabajadorAndDeletedAtIsNull(id)
                .orElseThrow(() -> new IllegalArgumentException("Trabajador no encontrado"));
        aplicarTrabajador(trabajador, request, false);
        Trabajador guardado = trabajadorRepository.saveAndFlush(trabajador);
        marcacionRepository.vincularMarcaciones(guardado.getIdTrabajador(), guardado.getCodigoZkteco());
        return toTrabajadorResponse(guardado);
    }

    @Transactional
    public void eliminarTrabajador(Integer id) {
        Trabajador trabajador = trabajadorRepository.findByIdTrabajadorAndDeletedAtIsNull(id)
                .orElseThrow(() -> new IllegalArgumentException("Trabajador no encontrado"));
        trabajador.setEstado("INACTIVO");
        trabajador.setDeletedAt(ahoraLima());
        trabajadorRepository.save(trabajador);
    }

    @Transactional(readOnly = true)
    public PagedResponse<DispositivoResponse> listarDispositivos(
            String q, Integer idSucursal, String estado, int page) {
        validarPagina(page);
        Specification<DispositivoAsistencia> spec = (root, query, cb) -> cb.conjunction();
        String term = normalizar(q);
        if (term != null) {
            String like = "%" + term.toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("nombre")), like),
                    cb.like(cb.lower(root.get("numeroSerie")), like)));
        }
        if (idSucursal != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("sucursal").get("idSucursal"), idSucursal));
        }
        String estadoNormalizado = normalizarEstadoOpcional(estado);
        if (estadoNormalizado != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("estado"), estadoNormalizado));
        }
        Page<DispositivoResponse> result = dispositivoRepository.findAll(spec,
                PageRequest.of(page, pageSize, Sort.by("nombre"))).map(this::toDispositivoResponse);
        return PagedResponse.fromPage(result);
    }

    @Transactional
    public DispositivoResponse crearDispositivo(DispositivoRequest request) {
        DispositivoAsistencia dispositivo = new DispositivoAsistencia();
        aplicarDispositivo(dispositivo, request, true);
        return toDispositivoResponse(dispositivoRepository.save(dispositivo));
    }

    @Transactional
    public DispositivoResponse actualizarDispositivo(Integer id, DispositivoRequest request) {
        DispositivoAsistencia dispositivo = dispositivoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Dispositivo no encontrado"));
        aplicarDispositivo(dispositivo, request, false);
        return toDispositivoResponse(dispositivoRepository.save(dispositivo));
    }

    @Transactional(readOnly = true)
    public PagedResponse<MarcacionResponse> listarMarcaciones(
            LocalDateTime desde, LocalDateTime hasta, Integer idTrabajador,
            Integer idSucursal, Integer idDispositivo, String vinculacion, int page) {
        validarPagina(page);
        validarRangoMarcaciones(desde, hasta);
        Specification<MarcacionAsistencia> spec = (root, query, cb) -> cb.conjunction();
        if (desde != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("fechaHora"), desde));
        }
        if (hasta != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("fechaHora"), hasta));
        }
        if (idTrabajador != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("trabajador").get("idTrabajador"), idTrabajador));
        }
        if (idSucursal != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("sucursal").get("idSucursal"), idSucursal));
        }
        if (idDispositivo != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("dispositivo").get("idDispositivo"), idDispositivo));
        }
        String vinculacionNormalizada = normalizar(vinculacion);
        if (vinculacionNormalizada != null) {
            boolean vinculada = switch (vinculacionNormalizada.toUpperCase(Locale.ROOT)) {
                case "VINCULADA" -> true;
                case "SIN_VINCULAR" -> false;
                default -> throw new IllegalArgumentException("Vinculacion permitida: VINCULADA o SIN_VINCULAR");
            };
            spec = spec.and((root, query, cb) -> vinculada
                    ? cb.isNotNull(root.get("trabajador"))
                    : cb.isNull(root.get("trabajador")));
        }
        Page<MarcacionResponse> result = marcacionRepository.findAll(spec,
                PageRequest.of(page, pageSize, Sort.by("fechaHora").descending())).map(this::toMarcacionResponse);
        return PagedResponse.fromPage(result);
    }

    @Transactional
    public MarcacionResponse registrarMarcacionManual(MarcacionManualRequest request, String correoUsuario) {
        LocalDateTime ahora = ahoraLima();
        if (request.fechaHora().isAfter(ahora)) {
            throw new IllegalArgumentException("La marcacion no puede estar en el futuro");
        }
        if (request.fechaHora().isBefore(ahora.minusDays(31))) {
            throw new IllegalArgumentException("Solo se permiten correcciones de los ultimos 31 dias");
        }
        Trabajador trabajador = trabajadorRepository.findByIdTrabajadorAndDeletedAtIsNull(request.idTrabajador())
                .filter(item -> "ACTIVO".equals(item.getEstado()))
                .orElseThrow(() -> new IllegalArgumentException("Trabajador no encontrado o inactivo"));
        Sucursal sucursal = sucursalActiva(request.idSucursal());
        Usuario usuario = usuarioRepository.findByCorreoAndDeletedAtIsNull(correoUsuario)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
        String tipo = request.tipoEvento().toUpperCase(Locale.ROOT);
        LocalDate fecha = request.fechaHora().toLocalDate();
        List<MarcacionAsistencia> existentes = marcacionesDelDia(trabajador.getIdTrabajador(), fecha);
        MarcacionAsistencia nueva = new MarcacionAsistencia();
        nueva.setTrabajador(trabajador);
        nueva.setSucursal(sucursal);
        nueva.setCodigoZkteco(trabajador.getCodigoZkteco());
        nueva.setFechaHora(request.fechaHora());
        nueva.setRecibidoAt(ahora);
        nueva.setOrigen("MANUAL");
        nueva.setTipoEvento(tipo);
        nueva.setMotivoRegistro(request.motivo().trim());
        nueva.setUsuarioRegistro(usuario);

        List<MarcacionAsistencia> candidatas = new ArrayList<>(existentes);
        candidatas.add(nueva);
        candidatas.sort(Comparator.comparing(MarcacionAsistencia::getFechaHora));
        ClasificacionMarcaciones clasificacion = clasificarMarcaciones(candidatas);
        if (clasificacion.efectivas().size() > 2) {
            throw new IllegalArgumentException("Anule primero las marcaciones sobrantes del dia");
        }
        int posicion = clasificacion.efectivas().indexOf(nueva);
        if (posicion < 0) {
            throw new IllegalArgumentException("Ya existe una marcacion equivalente dentro de 2 minutos");
        }
        String esperado = posicion == 0 ? "ENTRADA" : "SALIDA";
        if (!esperado.equals(tipo)) {
            throw new IllegalArgumentException("Por el orden horario, esta marcacion debe ser " + esperado);
        }
        return toMarcacionResponse(marcacionRepository.save(nueva));
    }

    @Transactional
    public MarcacionResponse anularMarcacion(Long id, AnularMarcacionRequest request, String correoUsuario) {
        MarcacionAsistencia marcacion = marcacionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Marcacion no encontrada"));
        if (marcacion.getAnuladaAt() != null) {
            throw new IllegalArgumentException("La marcacion ya fue anulada");
        }
        Usuario usuario = usuarioRepository.findByCorreoAndDeletedAtIsNull(correoUsuario)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
        marcacion.setAnuladaAt(ahoraLima());
        marcacion.setUsuarioAnula(usuario);
        marcacion.setMotivoAnulacion(request.motivo().trim());
        return toMarcacionResponse(marcacionRepository.save(marcacion));
    }

    @Transactional(readOnly = true)
    public PagedResponse<ResumenResponse> obtenerResumen(
            LocalDate desde, LocalDate hasta, Integer idTrabajador,
            Integer idSucursal, String q, String estado, String modalidad, Boolean rotativo, int page) {
        validarPagina(page);
        validarRangoResumen(desde, hasta);

        List<ResumenResponse> resumen = calcularResumenRango(
                desde, hasta, idTrabajador, q, normalizarModalidad(modalidad), rotativo);
        if (idSucursal != null) {
            resumen.removeIf(item -> !coincideSucursal(item, idSucursal));
            resumen.replaceAll(item -> filtrarPorSucursal(item, idSucursal));
        }
        String estadoFiltro = validarEstadoResumen(estado);
        if (estadoFiltro != null) {
            resumen.removeIf(item -> !coincideEstado(item, estadoFiltro));
        }
        resumen.sort(Comparator.comparing(ResumenResponse::fecha).reversed()
                .thenComparing(ResumenResponse::trabajador));
        int from = Math.min(page * pageSize, resumen.size());
        int to = Math.min(from + pageSize, resumen.size());
        Page<ResumenResponse> result = new PageImpl<>(resumen.subList(from, to),
                PageRequest.of(page, pageSize), resumen.size());
        return PagedResponse.fromPage(result);
    }

    @Transactional(readOnly = true)
    public PagedResponse<ResumenSemanalResponse> obtenerResumenSemanal(
            LocalDate desde, LocalDate hasta, Integer idTrabajador,
            Integer idSucursal, String q, String estado, String modalidad, Boolean rotativo, int page) {
        validarPagina(page);
        validarRangoResumen(desde, hasta);
        if (!hasta.equals(desde.plusDays(6))) {
            throw new IllegalArgumentException("La vista semanal requiere exactamente 7 dias");
        }

        String estadoFiltro = validarEstadoResumen(estado);
        Map<Integer, List<ResumenResponse>> porTrabajador = new java.util.LinkedHashMap<>();
        calcularResumenRango(desde, hasta, idTrabajador, q, normalizarModalidad(modalidad), rotativo)
                .forEach(item -> porTrabajador
                .computeIfAbsent(item.idTrabajador(), ignored -> new ArrayList<>()).add(item));
        List<ResumenSemanalResponse> resumen = porTrabajador.values().stream()
                .filter(dias -> idSucursal == null || dias.stream().anyMatch(dia -> coincideSucursal(dia, idSucursal)))
                .map(dias -> {
                    List<ResumenResponse> diasFiltrados = idSucursal == null ? dias
                            : dias.stream().map(dia -> filtrarPorSucursal(dia, idSucursal)).toList();
                    ResumenResponse primero = diasFiltrados.getFirst();
                    return new ResumenSemanalResponse(primero.idTrabajador(), primero.codigoZkteco(),
                            primero.trabajador(), primero.idSucursal(), primero.sucursal(),
                            primero.idTurno(), primero.turno(), primero.rotativo(), diasFiltrados);
                })
                .filter(item -> estadoFiltro == null
                        || item.dias().stream().anyMatch(dia -> coincideEstado(dia, estadoFiltro)))
                .toList();
        int from = Math.min(page * pageSize, resumen.size());
        int to = Math.min(from + pageSize, resumen.size());
        Page<ResumenSemanalResponse> result = new PageImpl<>(resumen.subList(from, to),
                PageRequest.of(page, pageSize), resumen.size());
        return PagedResponse.fromPage(result);
    }

    @Transactional(readOnly = true)
    public byte[] exportarResumenExcel(
            LocalDate desde, LocalDate hasta, Integer idTrabajador,
            Integer idSucursal, String q, String estado, String modalidad, Boolean rotativo, String correoUsuario) {
        validarRangoResumen(desde, hasta);
        String modalidadNormalizada = normalizarModalidad(modalidad);
        String estadoNormalizado = validarEstadoResumen(estado);
        List<ResumenResponse> calculado = calcularResumenRango(
                desde, hasta, idTrabajador, q, modalidadNormalizada, rotativo);

        Map<Integer, List<ResumenResponse>> porTrabajador = new java.util.LinkedHashMap<>();
        calculado.forEach(item -> porTrabajador
                .computeIfAbsent(item.idTrabajador(), ignored -> new ArrayList<>()).add(item));
        List<ResumenResponse> filtrado = porTrabajador.values().stream()
                .filter(dias -> idSucursal == null
                        || dias.stream().anyMatch(dia -> coincideSucursal(dia, idSucursal)))
                .map(dias -> idSucursal == null ? dias
                        : dias.stream().map(dia -> filtrarPorSucursal(dia, idSucursal)).toList())
                .filter(dias -> estadoNormalizado == null
                        || dias.stream().anyMatch(dia -> coincideEstado(dia, estadoNormalizado)))
                .flatMap(List::stream)
                .toList();

        Sucursal sucursalFiltro = idSucursal == null ? null
                : sucursalRepository.findByIdSucursalAndDeletedAtIsNull(idSucursal)
                        .orElseThrow(() -> new IllegalArgumentException("La sucursal no existe"));
        Usuario usuario = usuarioRepository.findByCorreoAndDeletedAtIsNull(correoUsuario).orElse(null);
        String filtros = "Trabajador: " + (idTrabajador == null ? "TODOS" : nombreTrabajador(filtrado, idTrabajador))
                + " | Busqueda: " + (normalizar(q) == null ? "TODAS" : q.trim())
                + " | Sucursal: " + (sucursalFiltro == null ? "TODAS" : sucursalFiltro.getNombre())
                + " | Estado: " + (estadoNormalizado == null ? "TODOS" : estadoNormalizado)
                + " | Horario: " + (modalidadNormalizada == null ? "TODOS" : modalidadNormalizada)
                + " | Rotacion: " + (rotativo == null ? "TODOS" : rotativo ? "ROTATIVOS" : "FIJOS");
        return AsistenciaExcelExporter.exportar(
                filtrado, desde, hasta, filtros, usuario, sucursalFiltro);
    }

    @Transactional(readOnly = true)
    public AnalisisResponse obtenerAnalisis(
            LocalDate desde, LocalDate hasta, Integer idTrabajador, Integer idSucursal, int page) {
        validarPagina(page);
        validarRangoResumen(desde, hasta);
        List<ResumenResponse> resumen = calcularResumenRango(desde, hasta, idTrabajador, null, null, null);
        if (idSucursal != null) {
            resumen.removeIf(item -> !coincideSucursal(item, idSucursal));
            resumen.replaceAll(item -> filtrarPorSucursal(item, idSucursal));
        }
        return construirAnalisis(resumen, desde, hasta, idSucursal, page);
    }

    AnalisisResponse construirAnalisis(
            List<ResumenResponse> resumen, LocalDate desde, LocalDate hasta, Integer idSucursal, int page) {
        long trabajadores = resumen.stream().map(ResumenResponse::idTrabajador).distinct().count();
        long asistencias = resumen.stream().filter(item -> item.cantidadMarcaciones() > 0).count();
        long faltas = contarEstado(resumen, "FALTA");
        long tardanzas = contarEstado(resumen, "TARDANZA");
        long salidas = resumen.stream().filter(ResumenResponse::salidaAnticipada).count();
        long incompletos = resumen.stream().filter(this::esIncompleto).count();
        long segundos = resumen.stream().mapToLong(item -> segundosParaSucursal(item, idSucursal)).sum();
        long minutos = segundos / 60;
        long diasEvaluables = resumen.stream().filter(this::esDiaProgramadoFinalizado).count();
        long diasCumplidos = resumen.stream().filter(this::esDiaCumplido).count();
        double porcentaje = diasEvaluables == 0 ? 0 : Math.round(diasCumplidos * 10000d / diasEvaluables) / 100d;

        AnalisisIndicadores indicadores = new AnalisisIndicadores(
                trabajadores, asistencias, faltas, tardanzas, salidas, incompletos, minutos, segundos, porcentaje);
        List<AnalisisEstado> distribucion = List.of(
                new AnalisisEstado("PRESENTE", contarEstado(resumen, "PRESENTE")),
                new AnalisisEstado("TARDANZA", tardanzas),
                new AnalisisEstado("FALTA", faltas),
                new AnalisisEstado("REGISTRO_INCOMPLETO", incompletos),
                new AnalisisEstado("EN_CURSO", contarEstado(resumen, "EN_CURSO")),
                new AnalisisEstado("TRABAJO_EN_DESCANSO", contarEstado(resumen, "TRABAJO_EN_DESCANSO")));

        List<AnalisisEvolucion> evolucion = new ArrayList<>();
        for (LocalDate fecha = desde; !fecha.isAfter(hasta); fecha = fecha.plusDays(1)) {
            final LocalDate dia = fecha;
            List<ResumenResponse> items = resumen.stream().filter(item -> item.fecha().equals(dia)).toList();
            evolucion.add(new AnalisisEvolucion(
                    fecha,
                    items.stream().filter(item -> item.cantidadMarcaciones() > 0).count(),
                    contarEstado(items, "TARDANZA"),
                    contarEstado(items, "FALTA"),
                    items.stream().filter(this::esIncompleto).count()));
        }

        Map<Integer, AnalisisSucursal> sucursales = new HashMap<>();
        for (ResumenResponse item : resumen) {
            if (item.rotativo()) {
                item.sesiones().stream().filter(SesionAsistenciaResponse::completa)
                        .filter(sesion -> idSucursal == null || idSucursal.equals(sesion.idSucursal()))
                        .forEach(sesion -> acumularSucursal(
                                sucursales, sesion.idSucursal(), sesion.sucursal(), sesion.segundosTrabajados()));
            } else if (item.idSucursal() != null && (idSucursal == null || idSucursal.equals(item.idSucursal()))) {
                acumularSucursal(sucursales, item.idSucursal(), item.sucursal(), item.segundosTrabajados());
            }
        }
        List<AnalisisSucursal> horasSucursal = sucursales.values().stream()
                .filter(item -> item.segundosTrabajados() > 0)
                .sorted(Comparator.comparingLong(AnalisisSucursal::segundosTrabajados).reversed())
                .toList();

        List<AnalisisTrabajador> ranking = resumen.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        ResumenResponse::idTrabajador, java.util.LinkedHashMap::new, java.util.stream.Collectors.toList()))
                .values().stream().map(dias -> rankingTrabajador(dias, idSucursal))
                .filter(item -> item.totalIncidencias() > 0)
                .sorted(Comparator.comparingLong(AnalisisTrabajador::totalIncidencias).reversed()
                        .thenComparing(AnalisisTrabajador::trabajador))
                .toList();
        int rankingSize = 10;
        int from = Math.min(page * rankingSize, ranking.size());
        int to = Math.min(from + rankingSize, ranking.size());
        Page<AnalisisTrabajador> rankingPage = new PageImpl<>(ranking.subList(from, to),
                PageRequest.of(page, rankingSize), ranking.size());

        return new AnalisisResponse(desde, hasta, indicadores, distribucion, evolucion, horasSucursal,
                PagedResponse.fromPage(rankingPage));
    }

    private long contarEstado(List<ResumenResponse> resumen, String estado) {
        return resumen.stream().filter(item -> estado.equals(item.estado())).count();
    }

    private boolean esIncompleto(ResumenResponse item) {
        return "INCOMPLETA".equals(item.estado()) || "REGISTRO_INCOMPLETO".equals(item.estado())
                || "REGISTRO_UNICO".equals(item.estado()) || "REQUIERE_REVISION".equals(item.estado());
    }

    private boolean esDiaProgramadoFinalizado(ResumenResponse item) {
        return item.idTurno() != null && ("PRESENTE".equals(item.estado()) || "TARDANZA".equals(item.estado())
                || "FALTA".equals(item.estado()) || "INCOMPLETA".equals(item.estado()));
    }

    private boolean esDiaCumplido(ResumenResponse item) {
        return item.idTurno() != null && ("PRESENTE".equals(item.estado()) || "TARDANZA".equals(item.estado()));
    }

    private long segundosParaSucursal(ResumenResponse item, Integer idSucursal) {
        if (!item.rotativo()) {
            return idSucursal == null || idSucursal.equals(item.idSucursal()) ? item.segundosTrabajados() : 0;
        }
        return item.sesiones().stream().filter(SesionAsistenciaResponse::completa)
                .filter(sesion -> idSucursal == null || idSucursal.equals(sesion.idSucursal()))
                .mapToLong(SesionAsistenciaResponse::segundosTrabajados).sum();
    }

    private void acumularSucursal(
            Map<Integer, AnalisisSucursal> acumulado, Integer idSucursal, String sucursal, long segundos) {
        AnalisisSucursal actual = acumulado.get(idSucursal);
        long total = segundos + (actual == null ? 0 : actual.segundosTrabajados());
        acumulado.put(idSucursal, new AnalisisSucursal(idSucursal, sucursal, total / 60, total));
    }

    private AnalisisTrabajador rankingTrabajador(List<ResumenResponse> dias, Integer idSucursal) {
        ResumenResponse trabajador = dias.getFirst();
        long faltas = contarEstado(dias, "FALTA");
        long tardanzas = contarEstado(dias, "TARDANZA");
        long salidas = dias.stream().filter(ResumenResponse::salidaAnticipada).count();
        long incompletos = dias.stream().filter(this::esIncompleto).count();
        return new AnalisisTrabajador(
                trabajador.idTrabajador(), trabajador.codigoZkteco(), trabajador.trabajador(),
                trabajador.sucursal(), faltas, tardanzas, salidas, incompletos,
                dias.stream().mapToLong(item -> segundosParaSucursal(item, idSucursal)).sum() / 60,
                dias.stream().mapToLong(item -> segundosParaSucursal(item, idSucursal)).sum(),
                faltas + tardanzas + salidas + incompletos);
    }

    private String nombreTrabajador(List<ResumenResponse> resumen, Integer idTrabajador) {
        return resumen.stream()
                .filter(item -> idTrabajador.equals(item.idTrabajador()))
                .map(ResumenResponse::trabajador)
                .findFirst()
                .orElse("ID " + idTrabajador);
    }

    private List<ResumenResponse> calcularResumenRango(
            LocalDate desde, LocalDate hasta, Integer idTrabajador, String q, String modalidad, Boolean rotativo) {

        Specification<Trabajador> spec = (root, query, cb) -> cb.conjunction();
        spec = spec.and(busquedaTrabajadorSpec(q));
        if (idTrabajador != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("idTrabajador"), idTrabajador));
        }
        if (modalidad != null) {
            spec = spec.and((root, query, cb) -> "CON_TURNO".equals(modalidad)
                    ? cb.isNotNull(root.get("turno")) : cb.isNull(root.get("turno")));
        }
        if (rotativo != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("rotativo"), rotativo));
        }
        List<Trabajador> trabajadores = trabajadorRepository.findAll(spec, Sort.by("apellidos", "nombres"));
        List<Integer> ids = trabajadores.stream().map(Trabajador::getIdTrabajador).toList();
        Map<Integer, List<MarcacionAsistencia>> marcaciones = new HashMap<>();
        if (!ids.isEmpty()) {
            LocalDateTime inicioConsulta = desde.atStartOfDay().minusHours(4);
            LocalDateTime finConsulta = hasta.plusDays(2).atStartOfDay().plusHours(6);
            marcacionRepository.findByTrabajador_IdTrabajadorInAndFechaHoraBetweenOrderByFechaHoraAsc(
                    ids, inicioConsulta, finConsulta).forEach(m -> marcaciones
                            .computeIfAbsent(m.getTrabajador().getIdTrabajador(), ignored -> new ArrayList<>()).add(m));
        }

        LocalDateTime ahora = ahoraLima();
        List<ResumenResponse> resumen = new ArrayList<>();
        for (Trabajador trabajador : trabajadores) {
            for (LocalDate fecha = desde; !fecha.isAfter(hasta); fecha = fecha.plusDays(1)) {
                if (!trabajadorVigenteEn(trabajador, fecha)) {
                    continue;
                }
                ResumenResponse item = calcularResumen(
                        trabajador, fecha, marcaciones.getOrDefault(trabajador.getIdTrabajador(), List.of()), ahora);
                resumen.add(item);
            }
        }
        return resumen;
    }

    private Specification<Trabajador> busquedaTrabajadorSpec(String q) {
        String term = normalizar(q);
        if (term == null) {
            return (root, query, cb) -> cb.conjunction();
        }
        String like = "%" + term.toLowerCase(Locale.ROOT) + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("nombres")), like),
                cb.like(cb.lower(root.get("apellidos")), like),
                cb.like(cb.lower(root.join("cargo", JoinType.LEFT).get("nombre")), like),
                cb.like(root.get("dni"), "%" + term + "%"),
                cb.like(root.get("codigoZkteco"), "%" + term + "%"));
    }

    private String validarEstadoResumen(String estado) {
        String estadoFiltro = normalizar(estado);
        if (estadoFiltro == null) {
            return null;
        }
        estadoFiltro = estadoFiltro.toUpperCase(Locale.ROOT);
        if (!ESTADOS_RESUMEN.contains(estadoFiltro)) {
            throw new IllegalArgumentException("Estado de asistencia invalido");
        }
        return estadoFiltro;
    }

    private boolean coincideEstado(ResumenResponse item, String estado) {
        return "SALIDA_ANTICIPADA".equals(estado) ? item.salidaAnticipada() : estado.equals(item.estado());
    }

    private boolean coincideSucursal(ResumenResponse item, Integer idSucursal) {
        if (!item.sesiones().isEmpty()) {
            return item.sesiones().stream().anyMatch(sesion -> idSucursal.equals(sesion.idSucursal()));
        }
        if (!item.sucursalesMarcacion().isEmpty()) {
            return item.sucursalesMarcacion().stream().anyMatch(sucursal -> idSucursal.equals(sucursal.idSucursal()));
        }
        return item.idSucursal() != null && idSucursal.equals(item.idSucursal());
    }

    private ResumenResponse filtrarPorSucursal(ResumenResponse item, Integer idSucursal) {
        List<SesionAsistenciaResponse> sesiones = item.sesiones().stream()
                .filter(sesion -> idSucursal.equals(sesion.idSucursal()))
                .toList();
        if (item.sesiones().isEmpty() && idSucursal.equals(item.idSucursal())) {
            return item;
        }
        if (item.sesiones().isEmpty() && item.sucursalesMarcacion().stream()
                .anyMatch(sucursal -> idSucursal.equals(sucursal.idSucursal()))) {
            return item;
        }

        long segundos = sesiones.stream().filter(SesionAsistenciaResponse::completa)
                .mapToLong(SesionAsistenciaResponse::segundosTrabajados).sum();
        int marcaciones = sesiones.stream().mapToInt(sesion -> sesion.completa() ? 2 : 1).sum();
        LocalDateTime primera = sesiones.isEmpty() ? null : sesiones.getFirst().entrada();
        LocalDateTime ultima = sesiones.stream().map(SesionAsistenciaResponse::salida)
                .filter(java.util.Objects::nonNull).max(LocalDateTime::compareTo).orElse(null);
        String estado = item.estado();
        if (sesiones.isEmpty()) {
            estado = "SIN_REGISTRO";
        } else if (item.idTurno() == null) {
            boolean incompleta = sesiones.stream().anyMatch(sesion -> !sesion.completa());
            estado = incompleta ? (marcaciones == 1 ? "REGISTRO_UNICO" : "REGISTRO_INCOMPLETO") : "PRESENTE";
        }
        boolean conservaSalidaAnticipada = item.salidaAnticipada() && ultima != null
                && ultima.equals(item.ultimaMarcacion());
        long tardanza = primera != null && primera.equals(item.primeraMarcacion()) ? item.minutosTardanza() : 0;

        return new ResumenResponse(item.idTrabajador(), item.codigoZkteco(), item.trabajador(),
                item.idSucursal(), item.sucursal(), item.idTurno(), item.turno(), item.fecha(),
                item.horaProgramadaEntrada(), item.horaProgramadaSalida(), primera, ultima, estado,
                tardanza, segundos / 60, segundos, marcaciones, conservaSalidaAnticipada,
                conservaSalidaAnticipada ? item.minutosSalidaAnticipada() : 0, item.rotativo(),
                sesiones.isEmpty() ? List.of()
                        : List.of(new SucursalMarcacionResponse(idSucursal, sesiones.getFirst().sucursal())),
                sesiones);
    }

    @Transactional
    public String opcionesAdms(String serial) {
        DispositivoAsistencia dispositivo = dispositivoAdms(serial);
        tocar(dispositivo);
        return "GET OPTION FROM: " + dispositivo.getNumeroSerie() + "\n"
                + "Stamp=9999\nOpStamp=9999\nErrorDelay=60\nDelay=30\n"
                + "TransTimes=00:00;14:05\nTransInterval=1\n"
                + "TransFlag=TransData AttLog\nRealtime=1\nEncrypt=0";
    }

    @Transactional
    public int recibirAdms(String serial, String table, String body) {
        DispositivoAsistencia dispositivo = dispositivoAdms(serial);
        if (!"ATTLOG".equalsIgnoreCase(normalizar(table))) {
            return 0;
        }
        if (body == null || body.isBlank()) {
            return 0;
        }
        validarTamanoAdms(body);

        List<AdmsMarcacion> eventos = parsearAdms(body);
        LocalDateTime recibidoAt = ahoraLima();
        validarEventosAdms(eventos, recibidoAt);
        Map<String, Integer> trabajadores = new HashMap<>();
        for (AdmsMarcacion evento : eventos) {
            trabajadores.computeIfAbsent(evento.codigoZkteco(), this::idTrabajadorActivoAdms);
        }
        for (AdmsMarcacion evento : eventos) {
            marcacionRepository.insertarSiNoExiste(
                    dispositivo.getIdDispositivo(), dispositivo.getSucursal().getIdSucursal(),
                    trabajadores.get(evento.codigoZkteco()),
                    evento.codigoZkteco(), evento.fechaHora(),
                    evento.tipoMarcacion(), evento.tipoVerificacion(), recibidoAt);
        }
        tocar(dispositivo);
        return eventos.size();
    }

    public void validarTamanoAdms(String body) {
        if (admsMaxBodyBytes < 1) {
            throw new IllegalStateException("Configuracion de tamano ADMS invalida");
        }
        if (body != null && body.getBytes(StandardCharsets.UTF_8).length > admsMaxBodyBytes) {
            throw new IllegalArgumentException("Carga ADMS demasiado grande");
        }
    }

    @Transactional
    public void registrarConsultaAdms(String serial) {
        DispositivoAsistencia dispositivo = dispositivoAdms(serial);
        tocar(dispositivo);
    }

    List<AdmsMarcacion> parsearAdms(String body) {
        List<AdmsMarcacion> eventos = new ArrayList<>();
        int numeroLinea = 0;
        for (String raw : body.split("\\R")) {
            numeroLinea++;
            String line = raw.trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] fields = line.contains("\t") ? line.split("\t") : line.split(",");
            if (fields.length < 2) {
                throw new IllegalArgumentException("Marcacion ADMS invalida en linea " + numeroLinea);
            }
            String codigo = fields[0].trim();
            if (!codigo.matches("\\d{1,24}")) {
                throw new IllegalArgumentException("Codigo ZKTeco invalido en linea " + numeroLinea);
            }
            LocalDateTime fechaHora = parsearFechaAdms(fields[1].trim(), numeroLinea);
            eventos.add(new AdmsMarcacion(
                    codigo,
                    fechaHora,
                    campoOpcional(fields, 2, numeroLinea),
                    campoOpcional(fields, 3, numeroLinea)));
        }
        if (eventos.isEmpty()) {
            throw new IllegalArgumentException("La carga ADMS no contiene marcaciones");
        }
        return eventos;
    }

    private void validarEventosAdms(List<AdmsMarcacion> eventos, LocalDateTime recibidoAt) {
        if (admsMaxEvents < 1 || admsMaxPastDays < 0 || admsMaxFutureMinutes < 0) {
            throw new IllegalStateException("Configuracion ADMS invalida");
        }
        if (eventos.size() > admsMaxEvents) {
            throw new IllegalArgumentException("Lote ADMS excede el maximo permitido");
        }
        LocalDateTime minimo = recibidoAt.minusDays(admsMaxPastDays);
        LocalDateTime maximo = recibidoAt.plusMinutes(admsMaxFutureMinutes);
        for (AdmsMarcacion evento : eventos) {
            if (evento.fechaHora().isBefore(minimo) || evento.fechaHora().isAfter(maximo)) {
                throw new IllegalArgumentException("Fecha de marcacion ADMS fuera del rango permitido");
            }
        }
    }

    private Integer idTrabajadorActivoAdms(String codigoZkteco) {
        Trabajador trabajador = trabajadorRepository.findByCodigoZktecoAndDeletedAtIsNull(codigoZkteco)
                .orElseThrow(() -> new IllegalArgumentException("Codigo ZKTeco no registrado o inactivo"));
        if (!"ACTIVO".equals(trabajador.getEstado())) {
            throw new IllegalArgumentException("Codigo ZKTeco no registrado o inactivo");
        }
        return trabajador.getIdTrabajador();
    }

    private void aplicarTrabajador(Trabajador trabajador, TrabajadorRequest request, boolean creando) {
        String codigo = request.codigoZkteco().trim();
        String dni = request.dni().trim();
        int idActual = creando ? 0 : trabajador.getIdTrabajador();
        if (trabajadorRepository.existsByCodigoZktecoAndIdTrabajadorNot(codigo, idActual)) {
            throw new IllegalArgumentException("El codigo ZKTeco ya esta registrado");
        }
        if (trabajadorRepository.existsByDniAndIdTrabajadorNot(dni, idActual)) {
            throw new IllegalArgumentException("El DNI ya esta registrado");
        }
        trabajador.setCodigoZkteco(codigo);
        trabajador.setDni(dni);
        trabajador.setNombres(request.nombres().trim());
        trabajador.setApellidos(request.apellidos().trim());
        boolean rotativo = Boolean.TRUE.equals(request.rotativo());
        if (!rotativo && request.idSucursal() == null) {
            throw new IllegalArgumentException("Ingrese sucursal base para el trabajador fijo");
        }
        trabajador.setSucursal(request.idSucursal() != null ? sucursalActiva(request.idSucursal()) : null);
        trabajador.setTurno(request.idTurno() != null ? turnoActivo(request.idTurno()) : null);
        trabajador.setCargo(cargoAsignable(request.idCargo(), trabajador.getCargo()));
        trabajador.setRotativo(rotativo);
        Usuario usuario = null;
        if (request.idUsuario() != null) {
            usuario = usuarioRepository.findByIdUsuarioAndDeletedAtIsNull(request.idUsuario())
                    .orElseThrow(() -> new IllegalArgumentException("La cuenta de usuario no existe"));
            if (!dni.equals(usuario.getDni())) {
                throw new IllegalArgumentException("El DNI del trabajador no coincide con la cuenta seleccionada");
            }
            if (trabajadorRepository.existsByUsuario_IdUsuarioAndIdTrabajadorNot(
                    usuario.getIdUsuario(), idActual)) {
                throw new IllegalArgumentException("La cuenta ya esta vinculada a otro trabajador");
            }
        }
        trabajador.setUsuario(usuario);
        trabajador.setEstado(creando ? "ACTIVO" : estadoRequerido(request.estado()));
    }

    private void aplicarDispositivo(DispositivoAsistencia dispositivo, DispositivoRequest request, boolean creando) {
        String serial = request.numeroSerie().trim();
        int idActual = creando ? 0 : dispositivo.getIdDispositivo();
        if (dispositivoRepository.existsByNumeroSerieIgnoreCaseAndIdDispositivoNot(serial, idActual)) {
            throw new IllegalArgumentException("El numero de serie ya esta registrado");
        }
        dispositivo.setNumeroSerie(serial);
        dispositivo.setNombre(request.nombre().trim());
        dispositivo.setSucursal(sucursalActiva(request.idSucursal()));
        dispositivo.setEstado(creando ? "ACTIVO" : estadoRequerido(request.estado()));
    }

    ResumenResponse calcularResumen(
            Trabajador trabajador, LocalDate fecha, List<MarcacionAsistencia> todas, LocalDateTime ahora) {
        Turno turno = trabajador.getTurno();
        if (turno == null) {
            return calcularResumenFlexible(trabajador, fecha, todas);
        }
        TurnoDia horario = turno.getDiasSemana().stream()
                .filter(dia -> dia.getDiaSemana() == DiaSemana.fromJavaDayOfWeek(fecha.getDayOfWeek()))
                .findFirst().orElse(null);
        List<MarcacionAsistencia> delDia;
        LocalDateTime inicio = null;
        LocalDateTime fin = null;
        if (horario == null) {
            delDia = todas.stream().filter(m -> m.getFechaHora().toLocalDate().equals(fecha)).toList();
        } else {
            LocalTime horaInicio = horario.getHoraInicio() != null ? horario.getHoraInicio() : turno.getHoraInicio();
            LocalTime horaFin = horario.getHoraFin() != null ? horario.getHoraFin() : turno.getHoraFin();
            inicio = fecha.atTime(horaInicio);
            fin = fecha.atTime(horaFin);
            if (!horaFin.isAfter(horaInicio)) {
                fin = fin.plusDays(1);
            }
            LocalDateTime ventanaInicio = inicio.minusHours(4);
            LocalDateTime ventanaFin = fin.plusHours(6);
            delDia = todas.stream().filter(m -> !m.getFechaHora().isBefore(ventanaInicio)
                    && !m.getFechaHora().isAfter(ventanaFin)).toList();
        }

        ClasificacionMarcaciones clasificacion = clasificarMarcaciones(delDia);
        List<MarcacionAsistencia> efectivas = clasificacion.efectivas();
        LocalDateTime primera = efectivas.isEmpty() ? null : efectivas.getFirst().getFechaHora();
        LocalDateTime ultima = efectivas.isEmpty() ? null : efectivas.getLast().getFechaHora();
        CalculoSesiones calculoSesiones = calcularSesionesPorPares(efectivas);
        long segundosTrabajados = calculoSesiones.segundosTrabajados();
        long minutosTrabajados = segundosTrabajados / 60;
        long minutosTardanza = inicio != null && primera != null
                ? Math.max(0, Duration.between(inicio.plusMinutes(turno.getToleranciaMinutos()), primera).toMinutes())
                : 0;
        boolean salidaAnticipada = fin != null && efectivas.size() == 2 && ahora.isAfter(fin)
                && ultima.isBefore(fin.minusMinutes(TOLERANCIA_SALIDA_MINUTOS));
        long minutosSalidaAnticipada = salidaAnticipada ? Duration.between(ultima, fin).toMinutes() : 0;
        String estado;
        if (calculoSesiones.requiereRevision()) {
            estado = "REQUIERE_REVISION";
        } else if (horario == null) {
            estado = efectivas.isEmpty() ? "DESCANSO"
                    : calculoSesiones.incompleta() ? "REGISTRO_INCOMPLETO" : "TRABAJO_EN_DESCANSO";
        } else if (efectivas.isEmpty()) {
            estado = ahora.isAfter(fin) ? "FALTA" : "PENDIENTE";
        } else if (!ahora.isAfter(fin)) {
            estado = "EN_CURSO";
        } else if (calculoSesiones.incompleta()) {
            estado = "INCOMPLETA";
        } else {
            estado = primera.isAfter(inicio.plusMinutes(turno.getToleranciaMinutos())) ? "TARDANZA" : "PRESENTE";
        }
        return new ResumenResponse(
                trabajador.getIdTrabajador(), trabajador.getCodigoZkteco(), nombreCompleto(trabajador),
                trabajador.getSucursal() != null ? trabajador.getSucursal().getIdSucursal() : null,
                trabajador.getSucursal() != null ? trabajador.getSucursal().getNombre() : null,
                turno.getIdTurno(), turno.getNombre(), fecha,
                horario != null ? horario.getHoraInicio() : null,
                horario != null ? horario.getHoraFin() : null,
                primera, ultima, estado, minutosTardanza, minutosTrabajados, segundosTrabajados, efectivas.size(),
                salidaAnticipada, minutosSalidaAnticipada, trabajador.isRotativo(),
                sucursalesMarcacion(efectivas), calculoSesiones.sesiones());
    }

    private ResumenResponse calcularResumenFlexible(
            Trabajador trabajador, LocalDate fecha, List<MarcacionAsistencia> todas) {
        List<MarcacionAsistencia> delDia = todas.stream()
                .filter(marcacion -> marcacion.getFechaHora().toLocalDate().equals(fecha))
                .toList();
        ClasificacionMarcaciones clasificacion = clasificarMarcaciones(delDia);
        List<MarcacionAsistencia> efectivas = clasificacion.efectivas();
        LocalDateTime primera = efectivas.isEmpty() ? null : efectivas.getFirst().getFechaHora();
        LocalDateTime ultima = efectivas.isEmpty() ? null : efectivas.getLast().getFechaHora();
        CalculoSesiones calculoSesiones = calcularSesionesPorPares(efectivas);
        long segundosTrabajados = calculoSesiones.segundosTrabajados();
        long minutosTrabajados = segundosTrabajados / 60;
        String estado = calculoSesiones.requiereRevision() ? "REQUIERE_REVISION"
                : efectivas.isEmpty() ? "SIN_REGISTRO"
                : efectivas.size() == 1 ? "REGISTRO_UNICO"
                : calculoSesiones.incompleta() ? "REGISTRO_INCOMPLETO" : "PRESENTE";
        return new ResumenResponse(
                trabajador.getIdTrabajador(), trabajador.getCodigoZkteco(), nombreCompleto(trabajador),
                trabajador.getSucursal() != null ? trabajador.getSucursal().getIdSucursal() : null,
                trabajador.getSucursal() != null ? trabajador.getSucursal().getNombre() : null,
                null, null, fecha,
                null, null, primera, ultima, estado, 0, minutosTrabajados, segundosTrabajados,
                efectivas.size(), false, 0,
                trabajador.isRotativo(), sucursalesMarcacion(efectivas), calculoSesiones.sesiones());
    }

    private CalculoSesiones calcularSesionesPorPares(List<MarcacionAsistencia> marcaciones) {
        if (marcaciones.size() > 2) {
            return new CalculoSesiones(List.of(), 0, false, true);
        }
        if (marcaciones.isEmpty()) {
            return new CalculoSesiones(List.of(), 0, false, false);
        }
        MarcacionAsistencia entrada = marcaciones.getFirst();
        MarcacionAsistencia salida = marcaciones.size() == 2 ? marcaciones.getLast() : null;
        Sucursal sucursal = entrada.getSucursal();
        long segundos = salida == null ? 0
                : Math.max(0, Duration.between(entrada.getFechaHora(), salida.getFechaHora()).toSeconds());
        SesionAsistenciaResponse sesion = new SesionAsistenciaResponse(
                sucursal.getIdSucursal(), sucursal.getNombre(), entrada.getFechaHora(),
                salida != null ? salida.getFechaHora() : null,
                nombreDispositivo(entrada), salida != null ? nombreDispositivo(salida) : null,
                salida != null ? salida.getSucursal().getIdSucursal() : null,
                salida != null ? salida.getSucursal().getNombre() : null,
                segundos / 60, segundos, salida != null);
        return new CalculoSesiones(List.of(sesion), segundos, salida == null, false);
    }

    private ClasificacionMarcaciones clasificarMarcaciones(List<MarcacionAsistencia> marcaciones) {
        if (duplicadoSegundos < 1) {
            throw new IllegalStateException("Configuracion de duplicados invalida");
        }
        List<MarcacionAsistencia> efectivas = new ArrayList<>();
        List<MarcacionAsistencia> duplicadas = new ArrayList<>();
        marcaciones.stream()
                .filter(marcacion -> marcacion.getAnuladaAt() == null)
                .sorted(Comparator.comparing(MarcacionAsistencia::getFechaHora))
                .forEach(marcacion -> {
                    if (!efectivas.isEmpty() && Duration.between(
                            efectivas.getLast().getFechaHora(), marcacion.getFechaHora()).toSeconds() < duplicadoSegundos) {
                        duplicadas.add(marcacion);
                    } else {
                        efectivas.add(marcacion);
                    }
                });
        return new ClasificacionMarcaciones(efectivas, duplicadas);
    }

    private List<SucursalMarcacionResponse> sucursalesMarcacion(List<MarcacionAsistencia> marcaciones) {
        Map<Integer, String> sucursales = new java.util.LinkedHashMap<>();
        marcaciones.forEach(marcacion -> {
            Sucursal sucursal = marcacion.getSucursal();
            sucursales.putIfAbsent(sucursal.getIdSucursal(), sucursal.getNombre());
        });
        return sucursales.entrySet().stream()
                .map(entry -> new SucursalMarcacionResponse(entry.getKey(), entry.getValue())).toList();
    }

    private String nombreDispositivo(MarcacionAsistencia marcacion) {
        return marcacion.getDispositivo() != null ? marcacion.getDispositivo().getNombre() : "Registro manual";
    }

    private List<MarcacionAsistencia> marcacionesDelDia(Integer idTrabajador, LocalDate fecha) {
        return marcacionRepository.findByTrabajador_IdTrabajadorAndFechaHoraBetweenOrderByFechaHoraAsc(
                idTrabajador, fecha.atStartOfDay(), fecha.plusDays(1).atStartOfDay().minusNanos(1));
    }

    private boolean trabajadorVigenteEn(Trabajador trabajador, LocalDate fecha) {
        return !trabajador.getFechaCreacion().toLocalDate().isAfter(fecha)
                && (trabajador.getDeletedAt() == null || !trabajador.getDeletedAt().toLocalDate().isBefore(fecha));
    }

    private DispositivoAsistencia dispositivoAdms(String serial) {
        String normalizado = normalizar(serial);
        if (normalizado == null) {
            throw new IllegalArgumentException("Numero de serie requerido");
        }
        if (!normalizado.matches("[A-Za-z0-9._:-]{1,80}")) {
            throw new IllegalArgumentException("Numero de serie invalido");
        }
        DispositivoAsistencia dispositivo = dispositivoRepository.findByNumeroSerieIgnoreCase(normalizado)
                .orElseThrow(() -> new IllegalArgumentException("Dispositivo no registrado"));
        if (!"ACTIVO".equals(dispositivo.getEstado())) {
            throw new IllegalArgumentException("Dispositivo inactivo");
        }
        return dispositivo;
    }

    private void tocar(DispositivoAsistencia dispositivo) {
        dispositivo.setUltimaConexion(ahoraLima());
        dispositivoRepository.save(dispositivo);
    }

    private LocalDateTime parsearFechaAdms(String value, int line) {
        try {
            return LocalDateTime.parse(value, ADMS_DATE_TIME);
        } catch (DateTimeParseException first) {
            try {
                return LocalDateTime.parse(value, ADMS_DATE_TIME_SLASH);
            } catch (DateTimeParseException second) {
                throw new IllegalArgumentException("Fecha ADMS invalida en linea " + line);
            }
        }
    }

    private String campoOpcional(String[] fields, int index, int linea) {
        if (fields.length <= index || fields[index].isBlank()) {
            return null;
        }
        String valor = fields[index].trim();
        if (valor.length() > 20) {
            throw new IllegalArgumentException("Campo ADMS demasiado largo en linea " + linea);
        }
        return valor;
    }

    private Sucursal sucursalActiva(Integer id) {
        Sucursal sucursal = sucursalRepository.findByIdSucursalAndDeletedAtIsNull(id)
                .orElseThrow(() -> new IllegalArgumentException("Sucursal no encontrada"));
        if (!"ACTIVO".equals(sucursal.getEstado())) {
            throw new IllegalArgumentException("La sucursal esta inactiva");
        }
        return sucursal;
    }

    private Turno turnoActivo(Integer id) {
        Turno turno = turnoRepository.findByIdTurnoAndDeletedAtIsNull(id)
                .orElseThrow(() -> new IllegalArgumentException("Turno no encontrado"));
        if (!"ACTIVO".equals(turno.getEstado())) {
            throw new IllegalArgumentException("El turno esta inactivo");
        }
        return turno;
    }

    private CargoTrabajador cargoAsignable(Integer id, CargoTrabajador actual) {
        if (id == null) {
            throw new IllegalArgumentException("Seleccione cargo");
        }
        CargoTrabajador cargo = cargoRepository.findByIdCargo(id)
                .orElseThrow(() -> new IllegalArgumentException("Cargo no encontrado"));
        boolean conservaActual = actual != null && id.equals(actual.getIdCargo());
        if (!conservaActual && !"ACTIVO".equals(cargo.getEstado())) {
            throw new IllegalArgumentException("El cargo seleccionado esta inactivo");
        }
        return cargo;
    }

    private String nombreCargo(String nombre) {
        String normalizado = normalizar(nombre);
        if (normalizado == null) {
            throw new IllegalArgumentException("Ingrese nombre del cargo");
        }
        return normalizado;
    }

    private void validarNombreCargoUnico(String nombre, Integer idActual) {
        if (cargoRepository.existsByNombreIgnoreCaseAndIdCargoNot(nombre, idActual)) {
            throw new IllegalArgumentException("El cargo ya esta registrado");
        }
    }

    private void validarRangoResumen(LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null) {
            throw new IllegalArgumentException("Ingrese desde y hasta");
        }
        if (hasta.isBefore(desde)) {
            throw new IllegalArgumentException("hasta no puede ser anterior a desde");
        }
        if (desde.plusDays(30).isBefore(hasta)) {
            throw new IllegalArgumentException("El rango maximo del resumen es 31 dias");
        }
    }

    private void validarRangoMarcaciones(LocalDateTime desde, LocalDateTime hasta) {
        if (desde == null || hasta == null) {
            return;
        }
        if (hasta.isBefore(desde)) {
            throw new IllegalArgumentException("hasta no puede ser anterior a desde");
        }
        if (desde.toLocalDate().plusDays(30).isBefore(hasta.toLocalDate())) {
            throw new IllegalArgumentException("El rango maximo de marcaciones es 31 dias");
        }
    }

    private void validarPagina(int page) {
        if (page < 0) {
            throw new IllegalArgumentException("page debe ser mayor o igual a 0");
        }
    }

    private String estadoRequerido(String estado) {
        String value = normalizarEstadoOpcional(estado);
        if (value == null) {
            throw new IllegalArgumentException("Ingrese estado");
        }
        return value;
    }

    private String normalizarEstadoOpcional(String estado) {
        String value = normalizar(estado);
        if (value == null) {
            return null;
        }
        value = value.toUpperCase(Locale.ROOT);
        if (!value.equals("ACTIVO") && !value.equals("INACTIVO")) {
            throw new IllegalArgumentException("Estado permitido: ACTIVO o INACTIVO");
        }
        return value;
    }

    private String normalizarModalidad(String modalidad) {
        String value = normalizar(modalidad);
        if (value == null) {
            return null;
        }
        value = value.toUpperCase(Locale.ROOT);
        if (!value.equals("CON_TURNO") && !value.equals("SIN_TURNO")) {
            throw new IllegalArgumentException("Modalidad permitida: CON_TURNO o SIN_TURNO");
        }
        return value;
    }

    private String normalizar(String value) {
        if (value == null) {
            return null;
        }
        String result = value.trim();
        return result.isEmpty() ? null : result;
    }

    private LocalDateTime ahoraLima() {
        return LocalDateTime.now(ZONA_LIMA);
    }

    private String nombreCompleto(Trabajador trabajador) {
        return trabajador.getNombres() + " " + trabajador.getApellidos();
    }

    private TrabajadorResponse toTrabajadorResponse(Trabajador trabajador) {
        Usuario usuario = trabajador.getUsuario();
        Turno turno = trabajador.getTurno();
        Sucursal sucursal = trabajador.getSucursal();
        CargoTrabajador cargo = trabajador.getCargo();
        return new TrabajadorResponse(
                trabajador.getIdTrabajador(), trabajador.getCodigoZkteco(), trabajador.getDni(),
                trabajador.getNombres(), trabajador.getApellidos(), trabajador.getEstado(),
                sucursal != null ? sucursal.getIdSucursal() : null, sucursal != null ? sucursal.getNombre() : null,
                turno != null ? turno.getIdTurno() : null, turno != null ? turno.getNombre() : null,
                cargo != null ? cargo.getIdCargo() : null,
                cargo != null ? cargo.getNombre() : null,
                cargo != null ? cargo.getEstado() : null,
                trabajador.isRotativo(),
                usuario != null ? usuario.getIdUsuario() : null,
                usuario != null ? usuario.getNombre() + " " + usuario.getApellido() : null,
                usuario != null ? usuario.getCorreo() : null,
                usuario != null ? usuario.getRol().name() : null,
                usuario != null ? usuario.getEstado() : null,
                trabajador.getFechaCreacion());
    }

    private CargoResponse toCargoResponse(CargoTrabajador cargo) {
        return new CargoResponse(cargo.getIdCargo(), cargo.getNombre(), cargo.getEstado(), cargo.getFechaCreacion());
    }

    private DispositivoResponse toDispositivoResponse(DispositivoAsistencia dispositivo) {
        return new DispositivoResponse(
                dispositivo.getIdDispositivo(), dispositivo.getNumeroSerie(), dispositivo.getNombre(),
                dispositivo.getEstado(), dispositivo.getSucursal().getIdSucursal(), dispositivo.getSucursal().getNombre(),
                dispositivo.getUltimaConexion(), dispositivo.getFechaCreacion());
    }

    private MarcacionResponse toMarcacionResponse(MarcacionAsistencia marcacion) {
        Trabajador trabajador = marcacion.getTrabajador();
        DispositivoAsistencia dispositivo = marcacion.getDispositivo();
        Sucursal sucursal = marcacion.getSucursal();
        String estadoCalculo = marcacion.getAnuladaAt() != null ? "ANULADA" : "VALIDA";
        String tipoEvento = marcacion.getTipoEvento();
        if (trabajador != null && marcacion.getAnuladaAt() == null) {
            ClasificacionMarcaciones clasificacion = clasificarMarcaciones(
                    marcacionesDelDia(trabajador.getIdTrabajador(), marcacion.getFechaHora().toLocalDate()));
            if (contieneMarcacion(clasificacion.duplicadas(), marcacion.getIdMarcacion())) {
                estadoCalculo = "DUPLICADA";
            } else {
                int posicion = indiceMarcacion(clasificacion.efectivas(), marcacion.getIdMarcacion());
                if (clasificacion.efectivas().size() > 2) {
                    estadoCalculo = "REQUIERE_REVISION";
                }
                if (tipoEvento == null && posicion >= 0 && posicion < 2) {
                    tipoEvento = posicion == 0 ? "ENTRADA" : "SALIDA";
                }
            }
        }
        return new MarcacionResponse(
                marcacion.getIdMarcacion(), dispositivo != null ? dispositivo.getIdDispositivo() : null,
                dispositivo != null ? dispositivo.getNombre() : "Registro manual",
                sucursal.getIdSucursal(), sucursal.getNombre(),
                trabajador != null ? trabajador.getIdTrabajador() : null,
                trabajador != null ? nombreCompleto(trabajador) : null,
                marcacion.getCodigoZkteco(), marcacion.getFechaHora(), marcacion.getTipoMarcacion(),
                marcacion.getTipoVerificacion(), marcacion.getRecibidoAt(), marcacion.getOrigen(), tipoEvento,
                estadoCalculo, marcacion.getMotivoRegistro(), nombreUsuario(marcacion.getUsuarioRegistro()),
                marcacion.getAnuladaAt(), marcacion.getMotivoAnulacion(), nombreUsuario(marcacion.getUsuarioAnula()));
    }

    private boolean contieneMarcacion(List<MarcacionAsistencia> marcaciones, Long id) {
        return indiceMarcacion(marcaciones, id) >= 0;
    }

    private int indiceMarcacion(List<MarcacionAsistencia> marcaciones, Long id) {
        for (int i = 0; i < marcaciones.size(); i++) {
            if (java.util.Objects.equals(marcaciones.get(i).getIdMarcacion(), id)) {
                return i;
            }
        }
        return -1;
    }

    private String nombreUsuario(Usuario usuario) {
        return usuario == null ? null : usuario.getNombre() + " " + usuario.getApellido();
    }

    record AdmsMarcacion(
            String codigoZkteco, LocalDateTime fechaHora, String tipoMarcacion, String tipoVerificacion) {
    }

    private record ClasificacionMarcaciones(
            List<MarcacionAsistencia> efectivas, List<MarcacionAsistencia> duplicadas) {
    }

    private record CalculoSesiones(
            List<SesionAsistenciaResponse> sesiones, long segundosTrabajados,
            boolean incompleta, boolean requiereRevision) {
    }
}
