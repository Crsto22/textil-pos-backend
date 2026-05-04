package com.sistemapos.sistematextil.services;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.sistemapos.sistematextil.config.StorageProperties;
import com.sistemapos.sistematextil.util.dashboard.DashboardSistemaResponse;
import com.sistemapos.sistematextil.util.dashboard.DashboardSistemaResponse.AlertaItem;
import com.sistemapos.sistematextil.util.dashboard.DashboardSistemaResponse.DatabaseResumen;
import com.sistemapos.sistematextil.util.dashboard.DashboardSistemaResponse.DatabaseTableItem;
import com.sistemapos.sistematextil.util.dashboard.DashboardSistemaResponse.DiskResumen;
import com.sistemapos.sistematextil.util.dashboard.DashboardSistemaResponse.RuntimeResumen;
import com.sistemapos.sistematextil.util.dashboard.DashboardSistemaResponse.StorageCarpetaItem;
import com.sistemapos.sistematextil.util.dashboard.DashboardSistemaResponse.StorageResumen;
import com.sistemapos.sistematextil.util.dashboard.DashboardSistemaResponse.SunatEstadoItem;
import com.sistemapos.sistematextil.util.dashboard.DashboardSistemaResponse.SunatResumen;
import com.sistemapos.sistematextil.util.dashboard.DashboardSistemaResponse.SunatUltimoJob;
import com.sistemapos.sistematextil.util.dashboard.DashboardSistemaResponse.UsuarioRolItem;
import com.sistemapos.sistematextil.util.dashboard.DashboardSistemaResponse.UsuariosResumen;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SistemaDashboardService {

    private static final List<String> STORAGE_CARPETAS_BASE = List.of("empresa", "productos", "sunat", "usuarios");
    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    private final StorageProperties storageProperties;
    private final JdbcTemplate jdbcTemplate;

    @Value("${spring.application.name:sistematextil}")
    private String applicationName;

    public DashboardSistemaResponse obtenerDashboard() {
        StorageResumen storage = construirStorageResumen();
        DatabaseResumen database = construirDatabaseResumen();
        RuntimeResumen runtime = construirRuntimeResumen();
        DiskResumen disk = construirDiskResumen(resolverStoragePath());
        SunatResumen sunat = construirSunatResumen();
        UsuariosResumen usuarios = construirUsuariosResumen();

        return new DashboardSistemaResponse(
                "SISTEMA",
                LocalDateTime.now(),
                storage,
                database,
                runtime,
                disk,
                sunat,
                usuarios,
                construirAlertas(storage, database, runtime, disk, sunat));
    }

    private StorageResumen construirStorageResumen() {
        Path basePath = resolverStoragePath();
        boolean existe = Files.exists(basePath);
        List<StorageCarpetaItem> carpetas = new ArrayList<>();

        for (String carpeta : STORAGE_CARPETAS_BASE) {
            carpetas.add(construirStorageCarpeta(basePath.resolve(carpeta), carpeta));
        }

        if (existe) {
            try (Stream<Path> children = Files.list(basePath)) {
                children.filter(Files::isDirectory)
                        .map(path -> path.getFileName().toString())
                        .filter(nombre -> !STORAGE_CARPETAS_BASE.contains(nombre))
                        .sorted()
                        .map(nombre -> construirStorageCarpeta(basePath.resolve(nombre), nombre))
                        .forEach(carpetas::add);
            } catch (IOException ignored) {
                // El resumen total queda disponible aunque el desglose no pueda listar carpetas extra.
            }
        }

        ConteoArchivos total = contarArchivos(basePath);
        return new StorageResumen(
                basePath.toString(),
                existe,
                total.bytes(),
                formatoBytes(total.bytes()),
                total.archivos(),
                carpetas);
    }

    private StorageCarpetaItem construirStorageCarpeta(Path path, String nombre) {
        ConteoArchivos conteo = contarArchivos(path);
        return new StorageCarpetaItem(
                nombre,
                Files.exists(path),
                conteo.bytes(),
                formatoBytes(conteo.bytes()),
                conteo.archivos());
    }

    private ConteoArchivos contarArchivos(Path path) {
        if (!Files.exists(path)) {
            return new ConteoArchivos(0, 0);
        }
        try (Stream<Path> walk = Files.walk(path)) {
            return walk.filter(Files::isRegularFile)
                    .map(file -> {
                        try {
                            return new ConteoArchivos(Files.size(file), 1);
                        } catch (IOException e) {
                            return new ConteoArchivos(0, 0);
                        }
                    })
                    .reduce(new ConteoArchivos(0, 0),
                            (a, b) -> new ConteoArchivos(a.bytes() + b.bytes(), a.archivos() + b.archivos()));
        } catch (IOException e) {
            return new ConteoArchivos(0, 0);
        }
    }

    private DatabaseResumen construirDatabaseResumen() {
        try {
            Map<String, Object> resumen = jdbcTemplate.queryForMap("""
                    SELECT
                      COALESCE(ROUND(SUM(data_length + index_length) / 1024 / 1024, 2), 0) AS size_mb,
                      COUNT(*) AS tables_count
                    FROM information_schema.tables
                    WHERE table_schema = DATABASE()
                    """);
            List<DatabaseTableItem> tablas = jdbcTemplate.query("""
                    SELECT
                      table_name,
                      ROUND((data_length + index_length) / 1024 / 1024, 2) AS size_mb,
                      COALESCE(table_rows, 0) AS table_rows
                    FROM information_schema.tables
                    WHERE table_schema = DATABASE()
                    ORDER BY (data_length + index_length) DESC, table_name ASC
                    LIMIT 10
                    """,
                    (rs, rowNum) -> new DatabaseTableItem(
                            rs.getString("table_name"),
                            escalarMoneda(rs.getBigDecimal("size_mb")),
                            rs.getLong("table_rows")));

            return new DatabaseResumen(
                    escalarMoneda(toBigDecimal(resumen.get("size_mb"))),
                    toLong(resumen.get("tables_count")),
                    tablas);
        } catch (DataAccessException e) {
            return new DatabaseResumen(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), 0, List.of());
        }
    }

    private RuntimeResumen construirRuntimeResumen() {
        Runtime runtime = Runtime.getRuntime();
        long max = runtime.maxMemory();
        long total = runtime.totalMemory();
        long free = runtime.freeMemory();
        long used = total - free;
        long uptime = ManagementFactory.getRuntimeMXBean().getUptime();

        return new RuntimeResumen(
                applicationName,
                System.getProperty("java.version"),
                uptime,
                formatoDuracion(uptime),
                runtime.availableProcessors(),
                used,
                formatoBytes(used),
                free,
                formatoBytes(free),
                max,
                formatoBytes(max),
                porcentaje(used, max));
    }

    private DiskResumen construirDiskResumen(Path storagePath) {
        Path path = resolverPathExistente(storagePath);
        try {
            FileStore fileStore = Files.getFileStore(path);
            long total = fileStore.getTotalSpace();
            long free = fileStore.getUsableSpace();
            long used = Math.max(0, total - free);
            return new DiskResumen(
                    path.toString(),
                    total,
                    formatoBytes(total),
                    used,
                    formatoBytes(used),
                    free,
                    formatoBytes(free),
                    porcentaje(free, total));
        } catch (IOException e) {
            return new DiskResumen(path.toString(), 0, formatoBytes(0), 0, formatoBytes(0), 0, formatoBytes(0),
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        }
    }

    private SunatResumen construirSunatResumen() {
        try {
            List<SunatEstadoItem> estados = jdbcTemplate.query("""
                    SELECT estado, COUNT(*) AS total
                    FROM sunat_job
                    GROUP BY estado
                    ORDER BY total DESC, estado ASC
                    """,
                    (rs, rowNum) -> new SunatEstadoItem(rs.getString("estado"), rs.getLong("total")));

            Map<String, Object> resumen = jdbcTemplate.queryForMap("""
                    SELECT
                      COUNT(*) AS total_jobs,
                      COALESCE(SUM(CASE WHEN estado IN ('PENDIENTE','EN_PROCESO','ERROR') THEN 1 ELSE 0 END), 0) AS jobs_no_finalizados
                    FROM sunat_job
                    """);

            List<SunatUltimoJob> ultimos = jdbcTemplate.query("""
                    SELECT id_sunat_job, estado, tipo_documento, created_at, updated_at
                    FROM sunat_job
                    ORDER BY created_at DESC, id_sunat_job DESC
                    LIMIT 1
                    """,
                    (rs, rowNum) -> new SunatUltimoJob(
                            rs.getInt("id_sunat_job"),
                            rs.getString("estado"),
                            rs.getString("tipo_documento"),
                            toLocalDateTime(rs.getTimestamp("created_at")),
                            toLocalDateTime(rs.getTimestamp("updated_at"))));

            return new SunatResumen(
                    toLong(resumen.get("total_jobs")),
                    toLong(resumen.get("jobs_no_finalizados")),
                    estados,
                    ultimos.isEmpty() ? null : ultimos.get(0));
        } catch (DataAccessException e) {
            return new SunatResumen(0, 0, List.of(), null);
        }
    }

    private UsuariosResumen construirUsuariosResumen() {
        try {
            List<UsuarioRolItem> activosPorRol = jdbcTemplate.query("""
                    SELECT rol, COUNT(*) AS total
                    FROM usuario
                    WHERE deleted_at IS NULL
                    GROUP BY rol
                    ORDER BY total DESC, rol ASC
                    """,
                    (rs, rowNum) -> new UsuarioRolItem(rs.getString("rol"), rs.getLong("total")));

            Map<String, Object> resumen = jdbcTemplate.queryForMap("""
                    SELECT
                      COALESCE(SUM(CASE WHEN deleted_at IS NULL THEN 1 ELSE 0 END), 0) AS activos,
                      COALESCE(SUM(CASE WHEN deleted_at IS NOT NULL THEN 1 ELSE 0 END), 0) AS eliminados
                    FROM usuario
                    """);

            return new UsuariosResumen(
                    toLong(resumen.get("activos")),
                    toLong(resumen.get("eliminados")),
                    activosPorRol);
        } catch (DataAccessException e) {
            return new UsuariosResumen(0, 0, List.of());
        }
    }

    private List<AlertaItem> construirAlertas(
            StorageResumen storage,
            DatabaseResumen database,
            RuntimeResumen runtime,
            DiskResumen disk,
            SunatResumen sunat) {
        List<AlertaItem> alertas = new ArrayList<>();
        alertas.add(alertaStorage(storage));
        alertas.add(alertaDatabase(database));
        alertas.add(alertaMemoria(runtime));
        alertas.add(alertaDisco(disk));
        alertas.add(alertaSunat(sunat));
        return alertas;
    }

    private AlertaItem alertaStorage(StorageResumen storage) {
        if (!storage.existe()) {
            return new AlertaItem("STORAGE", "WARNING", "La carpeta storage no existe o no esta disponible");
        }
        return new AlertaItem("STORAGE", "OK", "Storage local disponible: " + storage.totalLegible());
    }

    private AlertaItem alertaDatabase(DatabaseResumen database) {
        if (database.tablesCount() == 0) {
            return new AlertaItem("DATABASE", "WARNING", "No se pudieron leer metricas de base de datos");
        }
        return new AlertaItem("DATABASE", "OK", "Base de datos disponible con " + database.tablesCount() + " tablas");
    }

    private AlertaItem alertaMemoria(RuntimeResumen runtime) {
        if (runtime.memoryUsedPercent().compareTo(BigDecimal.valueOf(90)) >= 0) {
            return new AlertaItem("MEMORY", "CRITICAL", "Uso de memoria JVM mayor o igual a 90%");
        }
        if (runtime.memoryUsedPercent().compareTo(BigDecimal.valueOf(75)) >= 0) {
            return new AlertaItem("MEMORY", "WARNING", "Uso de memoria JVM mayor o igual a 75%");
        }
        return new AlertaItem("MEMORY", "OK", "Memoria JVM dentro de rango normal");
    }

    private AlertaItem alertaDisco(DiskResumen disk) {
        if (disk.totalBytes() == 0) {
            return new AlertaItem("DISK", "WARNING", "No se pudo leer el espacio del disco");
        }
        if (disk.freePercent().compareTo(BigDecimal.valueOf(5)) < 0) {
            return new AlertaItem("DISK", "CRITICAL", "Espacio libre del disco menor a 5%");
        }
        if (disk.freePercent().compareTo(BigDecimal.valueOf(15)) < 0) {
            return new AlertaItem("DISK", "WARNING", "Espacio libre del disco menor a 15%");
        }
        return new AlertaItem("DISK", "OK", "Disco con espacio libre suficiente");
    }

    private AlertaItem alertaSunat(SunatResumen sunat) {
        long errores = sunat.jobsPorEstado().stream()
                .filter(item -> "ERROR".equalsIgnoreCase(item.estado()))
                .mapToLong(SunatEstadoItem::total)
                .sum();
        if (errores > 0) {
            return new AlertaItem("SUNAT", "CRITICAL", "Existen jobs SUNAT en ERROR");
        }
        if (sunat.jobsNoFinalizados() > 0) {
            return new AlertaItem("SUNAT", "WARNING", "Existen jobs SUNAT pendientes o en proceso");
        }
        return new AlertaItem("SUNAT", "OK", "Jobs SUNAT sin errores pendientes");
    }

    private Path resolverStoragePath() {
        String configured = storageProperties.getBasePath();
        if (configured == null || configured.isBlank()) {
            return Paths.get("storage").toAbsolutePath().normalize();
        }
        return Paths.get(configured.trim()).toAbsolutePath().normalize();
    }

    private Path resolverPathExistente(Path path) {
        Path cursor = path;
        while (cursor != null && !Files.exists(cursor)) {
            cursor = cursor.getParent();
        }
        return cursor != null ? cursor : Paths.get(".").toAbsolutePath().normalize();
    }

    private BigDecimal porcentaje(long valor, long total) {
        if (total <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(valor)
                .multiply(CIEN)
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal escalarMoneda(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return new BigDecimal(value.toString());
    }

    private long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private String formatoBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double value = bytes;
        String[] units = { "KB", "MB", "GB", "TB" };
        int index = -1;
        while (value >= 1024 && index < units.length - 1) {
            value /= 1024;
            index++;
        }
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP) + " " + units[index];
    }

    private String formatoDuracion(long millis) {
        Duration duration = Duration.ofMillis(millis);
        long days = duration.toDays();
        long hours = duration.toHoursPart();
        long minutes = duration.toMinutesPart();
        long seconds = duration.toSecondsPart();
        if (days > 0) {
            return days + "d " + hours + "h " + minutes + "m";
        }
        if (hours > 0) {
            return hours + "h " + minutes + "m " + seconds + "s";
        }
        return minutes + "m " + seconds + "s";
    }

    private record ConteoArchivos(long bytes, long archivos) {
    }
}
