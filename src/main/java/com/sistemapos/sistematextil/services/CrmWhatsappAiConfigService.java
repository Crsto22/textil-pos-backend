package com.sistemapos.sistematextil.services;

import java.time.DateTimeException;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.math.BigDecimal;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.sistemapos.sistematextil.model.CrmWhatsappAiConfig;
import com.sistemapos.sistematextil.model.CrmWhatsappAiAttentionMode;
import com.sistemapos.sistematextil.model.CrmWhatsappAiDeliveryStatus;
import com.sistemapos.sistematextil.model.CrmWhatsappAiDeliveryType;
import com.sistemapos.sistematextil.model.CrmWhatsappAiJobStatus;
import com.sistemapos.sistematextil.model.CrmWhatsappAiMode;
import com.sistemapos.sistematextil.model.CrmWhatsappAiTone;
import com.sistemapos.sistematextil.model.CrmWhatsappAttentionQueue;
import com.sistemapos.sistematextil.model.CrmWhatsappBusinessHours;
import com.sistemapos.sistematextil.model.CrmWhatsappConnection;
import com.sistemapos.sistematextil.model.CrmWhatsappConversation;
import com.sistemapos.sistematextil.model.CrmWhatsappWaitingReason;
import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAiConfigRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAiDeliveryRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappAiJobRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappBusinessHoursRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappConversationRepository;
import com.sistemapos.sistematextil.services.CrmWhatsappConnectionService.BranchResponse;
import com.sistemapos.sistematextil.util.usuario.Rol;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CrmWhatsappAiConfigService {

    private static final int MAX_AUTOMATIC_RESPONSES = 50;

    private static final List<String> DIAS = List.of(
            "LUNES", "MARTES", "MIERCOLES", "JUEVES", "VIERNES", "SABADO", "DOMINGO");
    private static final List<String> INTENCIONES_PREDETERMINADAS = List.of(
            "SALUDO", "PRODUCTOS", "ENLACE_ECOMMERCE", "PRECIO", "STOCK", "COLORES_TALLAS", "GUIA_TALLAS", "OFERTAS", "PROMOCIONES",
            "HORARIOS", "UBICACION", "ENVIOS", "TIENDAS", "POLITICAS", "CUIDADOS", "FAQ", "INSTITUCIONAL", "INFORMACION_NEGOCIO",
            "METODOS_PAGO", "INTENCION_COMPRA", "MODIFICAR_CARRITO",
            "CONFIRMAR_PEDIDO", "CANCELAR_PEDIDO");
    private static final List<String> INTENCIONES = List.of(
            "SALUDO", "PRODUCTOS", "ENLACE_ECOMMERCE", "PRECIO", "STOCK", "COLORES_TALLAS", "GUIA_TALLAS", "OFERTAS", "PROMOCIONES",
            "UBICACION_HORARIOS", "HORARIOS", "UBICACION", "ENVIOS", "TIENDAS", "POLITICAS", "CUIDADOS", "FAQ",
            "INSTITUCIONAL", "INFORMACION_NEGOCIO", "METODOS_PAGO", "MI_CUENTA", "HISTORIAL_VENTAS", "ESTADO_VENTA",
            "INTENCION_COMPRA", "MODIFICAR_CARRITO", "CONFIRMAR_PEDIDO", "CANCELAR_PEDIDO");
    private static final List<String> REGLAS_SEGURIDAD = List.of(
            "Precios y ofertas siempre se consultan en tiempo real.",
            "El stock se limita a la sucursal vinculada y no implica reserva.",
            "La IA no emite ventas ni crea descuentos; solo aplica ofertas y combos validados por el backend.",
            "Las capturas bancarias requieren validacion humana.",
            "Los datos del cliente y sus ventas se limitan a la conversacion y sucursal vinculadas.",
            "Envios, tiendas, horarios, ubicacion y politicas se responden solo desde la base de conocimiento.",
            "El costo de envio siempre debe confirmarlo el personal encargado.",
            "Las condiciones mayoristas se confirman con un asesor.",
            "La IA se pausa cuando un asesor acepta el chat.",
            "Fuera del horario no se generan respuestas automaticas.");

    private final CrmWhatsappAiConfigRepository configRepository;
    private final CrmWhatsappBusinessHoursRepository businessHoursRepository;
    private final CrmWhatsappConnectionService connectionService;
    private final CrmWhatsappAiCredentialService credentialService;
    private final CrmWhatsappAiAuditService auditService;
    private final CrmWhatsappAiOperationsService operationsService;
    private final CrmWhatsappConversationRepository conversationRepository;
    private final CrmWhatsappAiJobRepository jobRepository;
    private final CrmWhatsappAiDeliveryRepository deliveryRepository;
    private final org.springframework.beans.factory.ObjectProvider<CrmWhatsappChatService> chatServiceProvider;

    @Transactional(readOnly = true)
    public AiConfigResponse obtener(Usuario actor) {
        CrmWhatsappConnection connection = connectionService.buscarConexionConfigurada(actor);
        if (connection == null) {
            return defaultResponse(false, null);
        }
        return configRepository.findByConnection_IdConnection(connection.getIdConnection())
                .map(this::toResponse)
                .orElseGet(() -> defaultResponse(true, branch(connection)));
    }

    @Transactional
    public AiConfigResponse guardar(AiConfigRequest request, Usuario actor) {
        if (actor == null || actor.getRol() != Rol.ADMINISTRADOR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo un administrador puede configurar la IA");
        }
        if (request == null) {
            throw badRequest("Ingrese la configuracion de IA");
        }
        CrmWhatsappConnection connection = connectionService.requireConexionConfigurada(actor);
        CrmWhatsappAiMode modo = parseEnum(CrmWhatsappAiMode.class, request.modo(), "Modo de IA invalido");
        if (modo != CrmWhatsappAiMode.DESACTIVADA
                && !credentialService.hasUsableCredential(connection.getIdConnection())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Configura y valida una API key de Gemini antes de activar la IA");
        }
        CrmWhatsappAiTone tono = parseEnum(CrmWhatsappAiTone.class, request.tono(), "Tono de IA invalido");
        String zonaHoraria = clean(request.zonaHoraria());
        try {
            ZoneId.of(zonaHoraria);
        } catch (DateTimeException ex) {
            throw badRequest("Zona horaria invalida");
        }

        List<String> dias = normalizeValues(request.diasAtencion(), DIAS, "Dia de atencion invalido");
        List<String> intenciones = normalizeValues(
                request.intencionesPermitidas(), INTENCIONES, "Intencion de IA invalida");
        if (dias.isEmpty()) {
            throw badRequest("Seleccione al menos un dia de atencion");
        }
        if (modo != CrmWhatsappAiMode.DESACTIVADA && intenciones.isEmpty()) {
            throw badRequest("Seleccione al menos una intencion permitida");
        }

        LocalTime horaInicio = parseTime(request.horaInicio(), "Hora de inicio invalida");
        LocalTime horaFin = parseTime(request.horaFin(), "Hora de fin invalida");
        if (!horaInicio.isBefore(horaFin)) {
            throw badRequest("La hora de inicio debe ser anterior a la hora de fin");
        }
        int espera = requireRange(request.esperaRespuestaSegundos(), 3, 60, "La espera debe estar entre 3 y 60 segundos");
        int maxRespuestas = requireRange(request.maxRespuestasAutomaticas(), 1, MAX_AUTOMATIC_RESPONSES,
                "El limite debe estar entre 1 y " + MAX_AUTOMATIC_RESPONSES + " respuestas");
        int confianza = requireRange(request.confianzaMinima(), 50, 95, "La confianza debe estar entre 50% y 95%");
        int rollout = request.automaticRolloutPercent() == null ? 0
                : requireRange(request.automaticRolloutPercent(), 0, 100, "El despliegue debe estar entre 0% y 100%");
        boolean naturalResponseEnabled = Boolean.TRUE.equals(request.naturalResponseEnabled());
        int naturalRollout = request.naturalResponseRolloutPercent() == null ? 0
                : requireRange(request.naturalResponseRolloutPercent(), 0, 100,
                        "El despliegue de redaccion natural debe estar entre 0% y 100%");
        // Un interruptor activo con 0% se mostraba como habilitado, pero nunca
        // seleccionaba ninguna conversacion. Al activarlo, el comportamiento debe
        // tener efecto inmediatamente; el porcentaje sigue permitiendo un rollout
        // parcial entre 1% y 100%.
        if (naturalResponseEnabled && naturalRollout == 0) naturalRollout = 100;
        String instrucciones = clean(request.instruccionesPersonalizadas());
        if (instrucciones.length() > 1000) {
            throw badRequest("Las instrucciones no deben superar 1000 caracteres");
        }
        if (tono == CrmWhatsappAiTone.PERSONALIZADO && instrucciones.length() < 10) {
            throw badRequest("Describe el tono personalizado con al menos 10 caracteres");
        }

        CrmWhatsappAiConfig config = configRepository
                .findByConnection_IdConnection(connection.getIdConnection())
                .orElseGet(CrmWhatsappAiConfig::new);
        CrmWhatsappAiMode previousMode = config.getModo() == null
                ? CrmWhatsappAiMode.DESACTIVADA
                : config.getModo();
        config.setConnection(connection);
        config.setModo(modo);
        config.setZonaHoraria(zonaHoraria);
        config.setDiasAtencion(String.join(",", dias));
        config.setHoraInicio(horaInicio);
        config.setHoraFin(horaFin);
        config.setEsperaRespuestaSegundos(espera);
        config.setTono(tono);
        config.setInstruccionesPersonalizadas(instrucciones.isBlank() ? null : instrucciones);
        config.setIntencionesPermitidas(String.join(",", intenciones));
        config.setMaxRespuestasAutomaticas(maxRespuestas);
        config.setConfianzaMinima(confianza);
        config.setTransferirBajaConfianza(request.transferirBajaConfianza() == null || request.transferirBajaConfianza());
        config.setTransferirSolicitudHumana(true);
        config.setTransferirAsuntoSensible(true);
        config.setTransferirImagenesAsesora(Boolean.TRUE.equals(request.transferirImagenesAsesora()));
        config.setMostrarProductosNuevos(Boolean.TRUE.equals(request.mostrarProductosNuevos()));
        config.setMandarCatalogoImagenes(Boolean.TRUE.equals(request.mandarCatalogoImagenes()));
        config.setSugerirPromocionesCarrito(Boolean.TRUE.equals(request.sugerirPromocionesCarrito()));
        config.setDailyTokenLimit(positiveLong(request.dailyTokenLimit(), "El limite diario debe ser positivo"));
        config.setMonthlyTokenLimit(positiveLong(request.monthlyTokenLimit(), "El limite mensual debe ser positivo"));
        config.setMonthlyBudgetUsd(positiveDecimal(request.monthlyBudgetUsd(), "El presupuesto debe ser positivo"));
        config.setInputCostPerMillionUsd(nonNegativeDecimal(request.inputCostPerMillionUsd(), "La tarifa de entrada no puede ser negativa"));
        config.setOutputCostPerMillionUsd(nonNegativeDecimal(request.outputCostPerMillionUsd(), "La tarifa de salida no puede ser negativa"));
        config.setAutomaticRolloutPercent(rollout);
        config.setNaturalResponseEnabled(naturalResponseEnabled);
        config.setNaturalResponseRolloutPercent(naturalRollout);
        if (config.getOperationalStatus() == null) config.setOperationalStatus(CrmWhatsappAiOperationsService.ACTIVE);
        saveBusinessHours(connection, request.horariosComerciales());
        CrmWhatsappAiConfig saved = configRepository.save(config);
        auditService.record(connection, null, null, actor, "CONFIG_UPDATED", "INFO",
                "Configuracion de IA actualizada", java.util.Map.of(
                        "mode", modo.name(),
                        "rollout", rollout,
                        "naturalResponse", Boolean.TRUE.equals(config.getNaturalResponseEnabled()),
                        "naturalResponseRollout", naturalRollout));
        return toResponse(saved);
    }

    private AiConfigResponse defaultResponse(boolean connectionConfigured, BranchResponse branch) {
        return new AiConfigResponse(
                connectionConfigured,
                branch,
                CrmWhatsappAiMode.DESACTIVADA.name(),
                "America/Lima",
                DIAS.subList(0, 6),
                "09:00",
                "19:00",
                5,
                CrmWhatsappAiTone.CERCANO.name(),
                "",
                INTENCIONES_PREDETERMINADAS,
                5,
                75,
                true,
                true,
                true,
                false,
                false,
                false,
                false,
                null, null, null, null, null, 0, false, 0,
                new CrmWhatsappAiOperationsService.OperationalSnapshot("ACTIVE", false, 0, 0,
                        BigDecimal.ZERO, null, null, null, 0, null),
                defaultBusinessHours(),
                REGLAS_SEGURIDAD);
    }

    private AiConfigResponse toResponse(CrmWhatsappAiConfig config) {
        return new AiConfigResponse(
                true,
                branch(config.getConnection()),
                config.getModo().name(),
                config.getZonaHoraria(),
                split(config.getDiasAtencion()),
                config.getHoraInicio().toString(),
                config.getHoraFin().toString(),
                config.getEsperaRespuestaSegundos(),
                config.getTono().name(),
                config.getInstruccionesPersonalizadas() == null ? "" : config.getInstruccionesPersonalizadas(),
                responseIntents(config.getIntencionesPermitidas()),
                config.getMaxRespuestasAutomaticas(),
                config.getConfianzaMinima(),
                Boolean.TRUE.equals(config.getTransferirBajaConfianza()),
                true,
                true,
                Boolean.TRUE.equals(config.getTransferirImagenesAsesora()),
                Boolean.TRUE.equals(config.getMostrarProductosNuevos()),
                Boolean.TRUE.equals(config.getMandarCatalogoImagenes()),
                Boolean.TRUE.equals(config.getSugerirPromocionesCarrito()),
                config.getDailyTokenLimit(),
                config.getMonthlyTokenLimit(),
                config.getMonthlyBudgetUsd(),
                config.getInputCostPerMillionUsd(),
                config.getOutputCostPerMillionUsd(),
                config.getAutomaticRolloutPercent(),
                Boolean.TRUE.equals(config.getNaturalResponseEnabled()),
                config.getNaturalResponseRolloutPercent() == null ? 0 : config.getNaturalResponseRolloutPercent(),
                operationsService.snapshot(config),
                businessHours(config.getConnection()),
                REGLAS_SEGURIDAD);
    }

    private void saveBusinessHours(CrmWhatsappConnection connection, List<BusinessHoursRequest> requested) {
        // Compatibilidad con clientes anteriores. Los horarios comerciales nuevos se administran en el RAG.
        if (requested == null) return;
        if (requested == null || requested.size() != DIAS.size()) {
            throw badRequest("Configure los siete dias del horario comercial");
        }
        Set<String> seen = new LinkedHashSet<>();
        for (BusinessHoursRequest item : requested) {
            String day = clean(item == null ? null : item.dia()).toUpperCase(Locale.ROOT);
            if (!DIAS.contains(day) || !seen.add(day)) throw badRequest("Dia comercial invalido o duplicado");
            boolean closed = item.cerrado() != null && item.cerrado();
            LocalTime opens = closed ? null : parseTime(item.horaApertura(), "Hora de apertura invalida");
            LocalTime closes = closed ? null : parseTime(item.horaCierre(), "Hora de cierre invalida");
            if (!closed && !opens.isBefore(closes)) throw badRequest("La apertura debe ser anterior al cierre");
            CrmWhatsappBusinessHours hours = businessHoursRepository
                    .findByConnection_IdConnectionAndDayOfWeek(connection.getIdConnection(), day)
                    .orElseGet(CrmWhatsappBusinessHours::new);
            hours.setConnection(connection);
            hours.setDayOfWeek(day);
            hours.setClosed(closed);
            hours.setOpensAt(opens);
            hours.setClosesAt(closes);
            businessHoursRepository.save(hours);
        }
    }

    private List<BusinessHoursResponse> businessHours(CrmWhatsappConnection connection) {
        var values = businessHoursRepository.findByConnection_IdConnectionOrderByIdBusinessHoursAsc(connection.getIdConnection());
        if (values.isEmpty()) return defaultBusinessHours();
        return DIAS.stream().map(day -> values.stream().filter(item -> day.equals(item.getDayOfWeek())).findFirst()
                .map(item -> new BusinessHoursResponse(day, Boolean.TRUE.equals(item.getClosed()),
                        item.getOpensAt() == null ? "" : item.getOpensAt().toString(),
                        item.getClosesAt() == null ? "" : item.getClosesAt().toString()))
                .orElse(new BusinessHoursResponse(day, true, "", ""))).toList();
    }

    private List<BusinessHoursResponse> defaultBusinessHours() {
        return DIAS.stream().map(day -> new BusinessHoursResponse(day, "DOMINGO".equals(day),
                "DOMINGO".equals(day) ? "" : "09:00", "DOMINGO".equals(day) ? "" : "19:00")).toList();
    }

    private BranchResponse branch(CrmWhatsappConnection connection) {
        return new BranchResponse(
                connection.getSucursal().getIdSucursal(),
                connection.getSucursal().getNombre(),
                connection.getSucursal().getTipo().name());
    }

    private List<String> normalizeValues(List<String> values, List<String> allowed, String message) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (values != null) {
            for (String value : values) {
                String item = clean(value).toUpperCase(Locale.ROOT);
                if (!allowed.contains(item)) {
                    throw badRequest(message);
                }
                normalized.add(item);
            }
        }
        return allowed.stream().filter(normalized::contains).toList();
    }

    private List<String> split(String value) {
        if (value == null || value.isBlank()) return List.of();
        return List.of(value.split(","));
    }

    private List<String> responseIntents(String value) {
        LinkedHashSet<String> values = new LinkedHashSet<>(split(value));
        if (values.remove("UBICACION_HORARIOS")) {
            values.add("UBICACION");
            values.add("HORARIOS");
        }
        return List.copyOf(values);
    }

    private LocalTime parseTime(String value, String message) {
        try {
            return LocalTime.parse(clean(value));
        } catch (DateTimeParseException ex) {
            throw badRequest(message);
        }
    }

    private int requireRange(Integer value, int min, int max, String message) {
        if (value == null || value < min || value > max) throw badRequest(message);
        return value;
    }

    private Long positiveLong(Long value, String message) {
        if (value == null) return null;
        if (value <= 0) throw badRequest(message);
        return value;
    }

    private BigDecimal positiveDecimal(BigDecimal value, String message) {
        if (value == null) return null;
        if (value.signum() <= 0) throw badRequest(message);
        return value;
    }

    private BigDecimal nonNegativeDecimal(BigDecimal value, String message) {
        if (value == null) return null;
        if (value.signum() < 0) throw badRequest(message);
        return value;
    }

    private <T extends Enum<T>> T parseEnum(Class<T> type, String value, String message) {
        try {
            return Enum.valueOf(type, clean(value).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw badRequest(message);
        }
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    public record AiConfigRequest(
            String modo,
            String zonaHoraria,
            List<String> diasAtencion,
            String horaInicio,
            String horaFin,
            Integer esperaRespuestaSegundos,
            String tono,
            String instruccionesPersonalizadas,
            List<String> intencionesPermitidas,
            Integer maxRespuestasAutomaticas,
            Integer confianzaMinima,
            Boolean transferirBajaConfianza,
            Boolean transferirImagenesAsesora,
            Boolean mostrarProductosNuevos,
            Boolean mandarCatalogoImagenes,
            Boolean sugerirPromocionesCarrito,
            Long dailyTokenLimit,
            Long monthlyTokenLimit,
            BigDecimal monthlyBudgetUsd,
            BigDecimal inputCostPerMillionUsd,
            BigDecimal outputCostPerMillionUsd,
            Integer automaticRolloutPercent,
            Boolean naturalResponseEnabled,
            Integer naturalResponseRolloutPercent,
            List<BusinessHoursRequest> horariosComerciales) {}

    public record AiConfigResponse(
            boolean connectionConfigured,
            BranchResponse branch,
            String modo,
            String zonaHoraria,
            List<String> diasAtencion,
            String horaInicio,
            String horaFin,
            Integer esperaRespuestaSegundos,
            String tono,
            String instruccionesPersonalizadas,
            List<String> intencionesPermitidas,
            Integer maxRespuestasAutomaticas,
            Integer confianzaMinima,
            boolean transferirBajaConfianza,
            boolean transferirSolicitudHumana,
            boolean transferirAsuntoSensible,
            boolean transferirImagenesAsesora,
            boolean mostrarProductosNuevos,
            boolean mandarCatalogoImagenes,
            boolean sugerirPromocionesCarrito,
            Long dailyTokenLimit,
            Long monthlyTokenLimit,
            BigDecimal monthlyBudgetUsd,
            BigDecimal inputCostPerMillionUsd,
            BigDecimal outputCostPerMillionUsd,
            Integer automaticRolloutPercent,
            boolean naturalResponseEnabled,
            Integer naturalResponseRolloutPercent,
            CrmWhatsappAiOperationsService.OperationalSnapshot operational,
            List<BusinessHoursResponse> horariosComerciales,
            List<String> reglasSeguridad) {}

    public record BusinessHoursRequest(String dia, Boolean cerrado, String horaApertura, String horaCierre) {}
    public record BusinessHoursResponse(String dia, boolean cerrado, String horaApertura, String horaCierre) {}
}
