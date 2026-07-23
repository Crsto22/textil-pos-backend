package com.sistemapos.sistematextil.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import com.sistemapos.sistematextil.model.MarcacionAsistencia;
import com.sistemapos.sistematextil.model.CargoTrabajador;
import com.sistemapos.sistematextil.model.DispositivoAsistencia;
import com.sistemapos.sistematextil.model.Sucursal;
import com.sistemapos.sistematextil.model.Trabajador;
import com.sistemapos.sistematextil.model.Turno;
import com.sistemapos.sistematextil.model.TurnoDia;
import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.repositories.DispositivoAsistenciaRepository;
import com.sistemapos.sistematextil.repositories.CargoTrabajadorRepository;
import com.sistemapos.sistematextil.repositories.MarcacionAsistenciaRepository;
import com.sistemapos.sistematextil.repositories.SucursalRepository;
import com.sistemapos.sistematextil.repositories.TrabajadorRepository;
import com.sistemapos.sistematextil.repositories.TurnoRepository;
import com.sistemapos.sistematextil.repositories.UsuarioRepository;
import com.sistemapos.sistematextil.util.asistencia.AsistenciaDtos.ResumenResponse;
import com.sistemapos.sistematextil.util.asistencia.AsistenciaDtos.ResumenSemanalResponse;
import com.sistemapos.sistematextil.util.asistencia.AsistenciaDtos.AnalisisResponse;
import com.sistemapos.sistematextil.util.asistencia.AsistenciaDtos.CargoEstadoRequest;
import com.sistemapos.sistematextil.util.asistencia.AsistenciaDtos.CargoRequest;
import com.sistemapos.sistematextil.util.asistencia.AsistenciaDtos.SesionAsistenciaResponse;
import com.sistemapos.sistematextil.util.asistencia.AsistenciaDtos.TrabajadorRequest;
import com.sistemapos.sistematextil.util.asistencia.AsistenciaDtos.MarcacionManualRequest;
import com.sistemapos.sistematextil.util.asistencia.AsistenciaDtos.AnularMarcacionRequest;
import com.sistemapos.sistematextil.util.paginacion.PagedResponse;
import com.sistemapos.sistematextil.util.usuario.Rol;
import com.sistemapos.sistematextil.util.turno.DiaSemana;

class AsistenciaServiceTest {

    private final AsistenciaService service = new AsistenciaService(
            mock(TrabajadorRepository.class),
            mock(CargoTrabajadorRepository.class),
            mock(DispositivoAsistenciaRepository.class),
            mock(MarcacionAsistenciaRepository.class),
            mock(SucursalRepository.class),
            mock(TurnoRepository.class),
            mock(UsuarioRepository.class));

    @Test
    void parseaAttlogRepresentativo() throws IOException {
        byte[] bytes = getClass().getResourceAsStream("/zkteco/attlog-sample.txt").readAllBytes();
        List<AsistenciaService.AdmsMarcacion> eventos = service.parsearAdms(
                new String(bytes, StandardCharsets.UTF_8));

        assertEquals(3, eventos.size());
        assertEquals("1001", eventos.getFirst().codigoZkteco());
        assertEquals(LocalDateTime.of(2026, 7, 14, 8, 1, 3), eventos.getFirst().fechaHora());
        assertEquals("15", eventos.getFirst().tipoVerificacion());
    }

    @Test
    void rechazaLineaAdmsInvalida() {
        assertThrows(IllegalArgumentException.class, () -> service.parsearAdms("abc\tsin-fecha"));
        assertThrows(IllegalArgumentException.class, () -> service.parsearAdms(
                "1001\t2026-07-20 08:00:00\t123456789012345678901"));
    }

    @Test
    void rechazaSerialAdmsInvalidoYDispositivoInactivo() {
        DispositivoAsistenciaRepository dispositivos = mock(DispositivoAsistenciaRepository.class);
        AsistenciaService asistencia = servicioAdms(
                mock(TrabajadorRepository.class), dispositivos, mock(MarcacionAsistenciaRepository.class));

        assertThrows(IllegalArgumentException.class,
                () -> asistencia.opcionesAdms("serial con espacios y caracteres invalidos !"));
        verifyNoInteractions(dispositivos);

        DispositivoAsistencia inactivo = dispositivoActivo();
        inactivo.setEstado("INACTIVO");
        when(dispositivos.findByNumeroSerieIgnoreCase("SN-1")).thenReturn(java.util.Optional.of(inactivo));
        assertThrows(IllegalArgumentException.class, () -> asistencia.opcionesAdms("SN-1"));
    }

    @Test
    void rechazaMarcacionesAdmsFueraDeRangoAntesDeInsertar() {
        DispositivoAsistenciaRepository dispositivos = mock(DispositivoAsistenciaRepository.class);
        TrabajadorRepository trabajadores = mock(TrabajadorRepository.class);
        MarcacionAsistenciaRepository marcaciones = mock(MarcacionAsistenciaRepository.class);
        AsistenciaService asistencia = servicioAdms(trabajadores, dispositivos, marcaciones);
        when(dispositivos.findByNumeroSerieIgnoreCase("SN-1")).thenReturn(java.util.Optional.of(dispositivoActivo()));
        String fechaAntigua = LocalDateTime.now(ZoneId.of("America/Lima")).minusDays(8)
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        assertThrows(IllegalArgumentException.class,
                () -> asistencia.recibirAdms("SN-1", "ATTLOG", "1001\t" + fechaAntigua));

        verifyNoInteractions(trabajadores, marcaciones);
        verify(dispositivos, org.mockito.Mockito.never()).save(any(DispositivoAsistencia.class));
    }

    @Test
    void rechazaCodigoInactivoYAceptaTrabajadorRotativoActivo() {
        DispositivoAsistenciaRepository dispositivos = mock(DispositivoAsistenciaRepository.class);
        TrabajadorRepository trabajadores = mock(TrabajadorRepository.class);
        MarcacionAsistenciaRepository marcaciones = mock(MarcacionAsistenciaRepository.class);
        AsistenciaService asistencia = servicioAdms(trabajadores, dispositivos, marcaciones);
        when(dispositivos.findByNumeroSerieIgnoreCase("SN-1")).thenReturn(java.util.Optional.of(dispositivoActivo()));
        Trabajador inactivo = new Trabajador();
        inactivo.setEstado("INACTIVO");
        when(trabajadores.findByCodigoZktecoAndDeletedAtIsNull("1001")).thenReturn(java.util.Optional.of(inactivo));
        String ahora = LocalDateTime.now(ZoneId.of("America/Lima"))
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        assertThrows(IllegalArgumentException.class, () -> asistencia.recibirAdms("SN-1", "ATTLOG", "1001\t" + ahora));
        verifyNoInteractions(marcaciones);

        Trabajador rotativo = new Trabajador();
        rotativo.setIdTrabajador(7);
        rotativo.setEstado("ACTIVO");
        rotativo.setRotativo(true);
        when(trabajadores.findByCodigoZktecoAndDeletedAtIsNull("1001")).thenReturn(java.util.Optional.of(rotativo));

        assertEquals(1, asistencia.recibirAdms("SN-1", "ATTLOG", "1001\t" + ahora));
        verify(marcaciones).insertarSiNoExiste(anyInt(), anyInt(), org.mockito.ArgumentMatchers.eq(7),
                org.mockito.ArgumentMatchers.eq("1001"), any(), any(), any(), any());
    }

    @Test
    void rechazaLoteAdmsExcesivoAntesDeInsertar() {
        DispositivoAsistenciaRepository dispositivos = mock(DispositivoAsistenciaRepository.class);
        MarcacionAsistenciaRepository marcaciones = mock(MarcacionAsistenciaRepository.class);
        AsistenciaService asistencia = servicioAdms(mock(TrabajadorRepository.class), dispositivos, marcaciones);
        ReflectionTestUtils.setField(asistencia, "admsMaxEvents", 1);
        when(dispositivos.findByNumeroSerieIgnoreCase("SN-1")).thenReturn(java.util.Optional.of(dispositivoActivo()));
        String ahora = LocalDateTime.now(ZoneId.of("America/Lima"))
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        assertThrows(IllegalArgumentException.class,
                () -> asistencia.recibirAdms("SN-1", "ATTLOG", "1001\t" + ahora + "\n1002\t" + ahora));

        verifyNoInteractions(marcaciones);
    }

    @Test
    void rechazaFechaAdmsFuturaYCodigoDesconocido() {
        DispositivoAsistenciaRepository dispositivos = mock(DispositivoAsistenciaRepository.class);
        TrabajadorRepository trabajadores = mock(TrabajadorRepository.class);
        MarcacionAsistenciaRepository marcaciones = mock(MarcacionAsistenciaRepository.class);
        AsistenciaService asistencia = servicioAdms(trabajadores, dispositivos, marcaciones);
        when(dispositivos.findByNumeroSerieIgnoreCase("SN-1")).thenReturn(java.util.Optional.of(dispositivoActivo()));
        String futura = LocalDateTime.now(ZoneId.of("America/Lima")).plusMinutes(11)
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String ahora = LocalDateTime.now(ZoneId.of("America/Lima"))
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        assertThrows(IllegalArgumentException.class,
                () -> asistencia.recibirAdms("SN-1", "ATTLOG", "1001\t" + futura));
        assertThrows(IllegalArgumentException.class,
                () -> asistencia.recibirAdms("SN-1", "ATTLOG", "1001\t" + ahora));

        verifyNoInteractions(marcaciones);
    }

    @Test
    void rechazaRangoDeMarcacionesMayorA31Dias() {
        LocalDateTime desde = LocalDateTime.of(2026, 7, 1, 0, 0);

        assertThrows(IllegalArgumentException.class,
                () -> service.listarMarcaciones(desde, desde.plusDays(31), null, null, null, null, 0));
    }

    @Test
    void registraSalidaManualConUsuarioYSucursal() {
        TrabajadorRepository trabajadores = mock(TrabajadorRepository.class);
        MarcacionAsistenciaRepository marcaciones = mock(MarcacionAsistenciaRepository.class);
        SucursalRepository sucursales = mock(SucursalRepository.class);
        UsuarioRepository usuarios = mock(UsuarioRepository.class);
        AsistenciaService asistencia = new AsistenciaService(trabajadores, mock(CargoTrabajadorRepository.class),
                mock(DispositivoAsistenciaRepository.class), marcaciones, sucursales,
                mock(TurnoRepository.class), usuarios);
        LocalDateTime salida = LocalDateTime.now(ZoneId.of("America/Lima")).minusMinutes(5);
        Trabajador trabajador = trabajador(salida.toLocalDate(), LocalTime.of(8, 0), LocalTime.of(17, 0));
        trabajador.setEstado("ACTIVO");
        Sucursal sucursal = trabajador.getSucursal();
        sucursal.setEstado("ACTIVO");
        Usuario usuario = new Usuario();
        usuario.setNombre("Admin");
        usuario.setApellido("Prueba");
        List<MarcacionAsistencia> dia = new ArrayList<>(List.of(
                marcacion(trabajador, salida.minusHours(8), 1, "Principal")));
        dia.getFirst().setIdMarcacion(1L);
        when(trabajadores.findByIdTrabajadorAndDeletedAtIsNull(1)).thenReturn(java.util.Optional.of(trabajador));
        when(sucursales.findByIdSucursalAndDeletedAtIsNull(1)).thenReturn(java.util.Optional.of(sucursal));
        when(usuarios.findByCorreoAndDeletedAtIsNull("admin@kiments.pe")).thenReturn(java.util.Optional.of(usuario));
        when(marcaciones.findByTrabajador_IdTrabajadorAndFechaHoraBetweenOrderByFechaHoraAsc(
                anyInt(), any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(dia);
        when(marcaciones.save(any(MarcacionAsistencia.class))).thenAnswer(invocation -> {
            MarcacionAsistencia guardada = invocation.getArgument(0);
            guardada.setIdMarcacion(2L);
            dia.add(guardada);
            return guardada;
        });

        var resultado = asistencia.registrarMarcacionManual(
                new MarcacionManualRequest(1, 1, salida, "SALIDA", "Olvido registrar la salida"),
                "admin@kiments.pe");

        assertEquals("MANUAL", resultado.origen());
        assertEquals("SALIDA", resultado.tipoEvento());
        assertEquals("Admin Prueba", resultado.usuarioRegistro());
    }

    @Test
    void anulaMarcacionSinEliminarElRegistro() {
        MarcacionAsistenciaRepository marcaciones = mock(MarcacionAsistenciaRepository.class);
        UsuarioRepository usuarios = mock(UsuarioRepository.class);
        AsistenciaService asistencia = new AsistenciaService(mock(TrabajadorRepository.class),
                mock(CargoTrabajadorRepository.class), mock(DispositivoAsistenciaRepository.class), marcaciones,
                mock(SucursalRepository.class), mock(TurnoRepository.class), usuarios);
        LocalDate fecha = LocalDate.now(ZoneId.of("America/Lima"));
        Trabajador trabajador = trabajador(fecha, LocalTime.of(8, 0), LocalTime.of(17, 0));
        MarcacionAsistencia marcacion = marcacion(trabajador, fecha.atTime(8, 0), 1, "Principal");
        marcacion.setIdMarcacion(1L);
        Usuario usuario = new Usuario();
        usuario.setNombre("Admin");
        usuario.setApellido("Prueba");
        when(marcaciones.findById(1L)).thenReturn(java.util.Optional.of(marcacion));
        when(usuarios.findByCorreoAndDeletedAtIsNull("admin@kiments.pe")).thenReturn(java.util.Optional.of(usuario));
        when(marcaciones.save(marcacion)).thenReturn(marcacion);

        var resultado = asistencia.anularMarcacion(
                1L, new AnularMarcacionRequest("Marcacion ingresada por error"), "admin@kiments.pe");

        assertEquals("ANULADA", resultado.estadoCalculo());
        assertEquals("Admin Prueba", resultado.usuarioAnula());
        assertEquals("Marcacion ingresada por error", resultado.motivoAnulacion());
    }

    @Test
    void vinculaCuentaSeleccionadaConDniCoincidente() {
        TrabajadorRepository trabajadores = mock(TrabajadorRepository.class);
        MarcacionAsistenciaRepository marcaciones = mock(MarcacionAsistenciaRepository.class);
        SucursalRepository sucursales = mock(SucursalRepository.class);
        TurnoRepository turnos = mock(TurnoRepository.class);
        UsuarioRepository usuarios = mock(UsuarioRepository.class);
        CargoTrabajadorRepository cargos = cargoRepositoryActivo();
        AsistenciaService asistencia = new AsistenciaService(trabajadores,
                cargos, mock(DispositivoAsistenciaRepository.class), marcaciones, sucursales, turnos, usuarios);
        Sucursal sucursal = new Sucursal();
        sucursal.setIdSucursal(1);
        sucursal.setNombre("Central");
        sucursal.setEstado("ACTIVO");
        Turno turno = new Turno();
        turno.setIdTurno(1);
        turno.setNombre("Dia");
        turno.setEstado("ACTIVO");
        Usuario usuario = Usuario.builder().idUsuario(7).nombre("Ana").apellido("Perez")
                .dni("12345678").correo("ana@kiments.pe").rol(Rol.VENTAS).estado("ACTIVO").build();
        AtomicReference<Trabajador> guardadoRef = new AtomicReference<>();
        when(sucursales.findByIdSucursalAndDeletedAtIsNull(1)).thenReturn(java.util.Optional.of(sucursal));
        when(turnos.findByIdTurnoAndDeletedAtIsNull(1)).thenReturn(java.util.Optional.of(turno));
        when(usuarios.findByIdUsuarioAndDeletedAtIsNull(7)).thenReturn(java.util.Optional.of(usuario));
        when(trabajadores.saveAndFlush(any(Trabajador.class))).thenAnswer(invocation -> {
            Trabajador guardado = invocation.getArgument(0);
            guardado.setIdTrabajador(1);
            guardadoRef.set(guardado);
            return guardado;
        });

        asistencia.crearTrabajador(new TrabajadorRequest(
                "1001", "12345678", "Ana", "Perez", 1, 1, 1, false, null, 7));

        assertSame(usuario, guardadoRef.get().getUsuario());
    }

    @Test
    void permiteRotativoSinSucursalYRechazaFijoSinSucursal() {
        TrabajadorRepository trabajadores = mock(TrabajadorRepository.class);
        MarcacionAsistenciaRepository marcaciones = mock(MarcacionAsistenciaRepository.class);
        CargoTrabajadorRepository cargos = cargoRepositoryActivo();
        AsistenciaService asistencia = new AsistenciaService(trabajadores,
                cargos, mock(DispositivoAsistenciaRepository.class), marcaciones,
                mock(SucursalRepository.class), mock(TurnoRepository.class), mock(UsuarioRepository.class));
        AtomicReference<Trabajador> guardadoRef = new AtomicReference<>();
        when(trabajadores.saveAndFlush(any(Trabajador.class))).thenAnswer(invocation -> {
            Trabajador guardado = invocation.getArgument(0);
            guardado.setIdTrabajador(1);
            guardadoRef.set(guardado);
            return guardado;
        });

        asistencia.crearTrabajador(new TrabajadorRequest(
                "2001", "87654321", "Rosa", "Modelo", null, null, 1, true, null, null));

        assertTrue(guardadoRef.get().isRotativo());
        assertNull(guardadoRef.get().getSucursal());
        assertThrows(IllegalArgumentException.class, () -> asistencia.crearTrabajador(new TrabajadorRequest(
                "2002", "87654322", "Luis", "Fijo", null, null, 1, false, null, null)));
    }

    @Test
    void rechazaCargoDuplicadoEInactivoEnNuevaAsignacion() {
        CargoTrabajadorRepository duplicados = mock(CargoTrabajadorRepository.class);
        when(duplicados.existsByNombreIgnoreCaseAndIdCargoNot("Modelo", 0)).thenReturn(true);
        AsistenciaService catalogo = new AsistenciaService(mock(TrabajadorRepository.class), duplicados,
                mock(DispositivoAsistenciaRepository.class), mock(MarcacionAsistenciaRepository.class),
                mock(SucursalRepository.class), mock(TurnoRepository.class), mock(UsuarioRepository.class));
        assertThrows(IllegalArgumentException.class, () -> catalogo.crearCargo(new CargoRequest(" Modelo ")));

        CargoTrabajadorRepository inactivos = mock(CargoTrabajadorRepository.class);
        CargoTrabajador cargo = new CargoTrabajador();
        cargo.setIdCargo(9);
        cargo.setNombre("Modelo");
        cargo.setEstado("INACTIVO");
        when(inactivos.findByIdCargo(9)).thenReturn(java.util.Optional.of(cargo));
        AsistenciaService asignacion = new AsistenciaService(mock(TrabajadorRepository.class), inactivos,
                mock(DispositivoAsistenciaRepository.class), mock(MarcacionAsistenciaRepository.class),
                mock(SucursalRepository.class), mock(TurnoRepository.class), mock(UsuarioRepository.class));
        assertThrows(IllegalArgumentException.class, () -> asignacion.crearTrabajador(new TrabajadorRequest(
                "3001", "87654323", "Maria", "Modelo", null, null, 9, true, null, null)));
    }

    @Test
    void desactivaYReactivaCargoSinEliminarlo() {
        CargoTrabajadorRepository cargos = mock(CargoTrabajadorRepository.class);
        CargoTrabajador cargo = new CargoTrabajador();
        cargo.setIdCargo(1);
        cargo.setNombre("Ventas");
        cargo.setEstado("ACTIVO");
        when(cargos.findByIdCargo(1)).thenReturn(java.util.Optional.of(cargo));
        when(cargos.save(any(CargoTrabajador.class))).thenAnswer(invocation -> invocation.getArgument(0));
        AsistenciaService catalogo = new AsistenciaService(mock(TrabajadorRepository.class), cargos,
                mock(DispositivoAsistenciaRepository.class), mock(MarcacionAsistenciaRepository.class),
                mock(SucursalRepository.class), mock(TurnoRepository.class), mock(UsuarioRepository.class));

        assertEquals("INACTIVO", catalogo.actualizarEstadoCargo(1, new CargoEstadoRequest("INACTIVO")).estado());
        assertTrue(cargo.getDeletedAt() != null);
        assertEquals("ACTIVO", catalogo.actualizarEstadoCargo(1, new CargoEstadoRequest("ACTIVO")).estado());
        assertNull(cargo.getDeletedAt());
    }

    @Test
    void calculaPuntualTardanzaFaltaEIncompleta() {
        LocalDate fecha = LocalDate.of(2026, 7, 13);
        Trabajador trabajador = trabajador(fecha, LocalTime.of(8, 0), LocalTime.of(17, 0));
        LocalDateTime despuesDelTurno = fecha.atTime(18, 0);

        assertEquals("PRESENTE", resumen(trabajador, fecha, despuesDelTurno,
                fecha.atTime(8, 10), fecha.atTime(17, 0)).estado());
        assertEquals("TARDANZA", resumen(trabajador, fecha, despuesDelTurno,
                fecha.atTime(8, 11), fecha.atTime(17, 0)).estado());
        assertEquals("FALTA", resumen(trabajador, fecha, despuesDelTurno).estado());
        assertEquals("INCOMPLETA", resumen(trabajador, fecha, despuesDelTurno,
                fecha.atTime(8, 0)).estado());
    }

    @Test
    void atribuyeTurnoNocturnoAlDiaDeInicio() {
        LocalDate fecha = LocalDate.of(2026, 7, 13);
        Trabajador trabajador = trabajador(fecha, LocalTime.of(22, 0), LocalTime.of(6, 0));

        ResumenResponse resumen = resumen(trabajador, fecha, fecha.plusDays(1).atTime(7, 0),
                fecha.atTime(21, 58), fecha.plusDays(1).atTime(6, 2));

        assertEquals("PRESENTE", resumen.estado());
        assertEquals(484, resumen.minutosTrabajados());
    }

    @Test
    void agrupaLosSieteDiasPorTrabajadorEnVistaSemanal() {
        TrabajadorRepository trabajadores = mock(TrabajadorRepository.class);
        MarcacionAsistenciaRepository marcaciones = mock(MarcacionAsistenciaRepository.class);
        AsistenciaService asistencia = new AsistenciaService(trabajadores,
                mock(CargoTrabajadorRepository.class), mock(DispositivoAsistenciaRepository.class), marcaciones,
                mock(SucursalRepository.class), mock(TurnoRepository.class), mock(UsuarioRepository.class));
        LocalDate lunes = LocalDate.of(2026, 7, 6);
        Trabajador trabajador = trabajador(lunes, LocalTime.of(8, 0), LocalTime.of(17, 0));
        when(trabajadores.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(trabajador));
        when(marcaciones.findByTrabajador_IdTrabajadorInAndFechaHoraBetweenOrderByFechaHoraAsc(
                anyList(), any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(List.of());
        ReflectionTestUtils.setField(asistencia, "pageSize", 10);

        PagedResponse<ResumenSemanalResponse> resultado = asistencia.obtenerResumenSemanal(
                lunes, lunes.plusDays(6), null, null, null, null, null, null, 0);

        assertEquals(1, resultado.totalElements());
        assertEquals(7, resultado.content().getFirst().dias().size());
    }

    @Test
    void calculaAsistenciaFlexibleSinTurno() {
        LocalDate fecha = LocalDate.of(2026, 7, 13);
        Trabajador trabajador = trabajador(fecha, LocalTime.of(8, 0), LocalTime.of(17, 0));
        trabajador.setTurno(null);

        assertEquals("SIN_REGISTRO", resumen(trabajador, fecha, fecha.atTime(23, 0)).estado());
        assertEquals("REGISTRO_UNICO", resumen(trabajador, fecha, fecha.atTime(23, 0),
                fecha.atTime(9, 0)).estado());
        ResumenResponse dosMarcas = resumen(trabajador, fecha, fecha.atTime(23, 0),
                fecha.atTime(9, 0), fecha.atTime(14, 30));
        assertEquals("PRESENTE", dosMarcas.estado());
        assertEquals(330, dosMarcas.minutosTrabajados());
        ResumenResponse cuatroMarcas = resumen(trabajador, fecha, fecha.atTime(23, 0),
                fecha.atTime(8, 0), fecha.atTime(12, 0), fecha.atTime(13, 0), fecha.atTime(17, 0));
        assertEquals("REQUIERE_REVISION", cuatroMarcas.estado());
        assertEquals(0, cuatroMarcas.minutosTrabajados());
        assertEquals(4, cuatroMarcas.cantidadMarcaciones());
    }

    @Test
    void bloqueaMasDeDosMarcacionesSinInventarHoras() {
        LocalDate fecha = LocalDate.of(2026, 7, 16);
        Trabajador trabajador = trabajador(fecha, LocalTime.of(8, 0), LocalTime.of(17, 0));
        trabajador.setTurno(null);

        ResumenResponse resultado = resumen(trabajador, fecha, fecha.atTime(23, 0),
                fecha.atTime(11, 18, 41), fecha.atTime(11, 23, 11),
                fecha.atTime(12, 20, 22), fecha.atTime(12, 25, 47));

        assertEquals("REQUIERE_REVISION", resultado.estado());
        assertEquals(0, resultado.segundosTrabajados());
        assertTrue(resultado.sesiones().isEmpty());
    }

    @Test
    void ignoraDobleHuellaMenorADosMinutosPeroAceptaElLimite() {
        LocalDate fecha = LocalDate.of(2026, 7, 16);
        Trabajador trabajador = trabajador(fecha, LocalTime.of(8, 0), LocalTime.of(17, 0));
        trabajador.setTurno(null);

        ResumenResponse duplicada = resumen(trabajador, fecha, fecha.atTime(23, 0),
                fecha.atTime(8, 0), fecha.atTime(8, 1, 59), fecha.atTime(17, 0));
        assertEquals("PRESENTE", duplicada.estado());
        assertEquals(2, duplicada.cantidadMarcaciones());
        assertEquals(540, duplicada.minutosTrabajados());

        ResumenResponse limite = resumen(trabajador, fecha, fecha.atTime(23, 0),
                fecha.atTime(8, 0), fecha.atTime(8, 2));
        assertEquals("PRESENTE", limite.estado());
        assertEquals(2, limite.minutosTrabajados());
    }

    @Test
    void detectaSalidaAnticipadaDespuesDeFinalizarElTurno() {
        LocalDate fecha = LocalDate.of(2026, 7, 13);
        Trabajador trabajador = trabajador(fecha, LocalTime.of(8, 0), LocalTime.of(17, 0));

        ResumenResponse duranteTurno = resumen(trabajador, fecha, fecha.atTime(12, 0),
                fecha.atTime(8, 0), fecha.atTime(11, 30));
        assertEquals("EN_CURSO", duranteTurno.estado());
        ResumenResponse finalizado = resumen(trabajador, fecha, fecha.atTime(18, 0),
                fecha.atTime(8, 0), fecha.atTime(11, 30));
        assertEquals("PRESENTE", finalizado.estado());
        assertTrue(finalizado.salidaAnticipada());
        assertEquals(330, finalizado.minutosSalidaAnticipada());
        assertEquals(210, finalizado.minutosTrabajados());
    }

    @Test
    void calculaSesionesDeTrabajadorRotativoPorSucursal() {
        LocalDate fecha = LocalDate.of(2026, 7, 13);
        Trabajador trabajador = trabajador(fecha, LocalTime.of(8, 0), LocalTime.of(17, 0));
        trabajador.setTurno(null);
        trabajador.setRotativo(true);
        List<MarcacionAsistencia> completas = List.of(
                marcacion(trabajador, fecha.atTime(6, 0), 1, "Central"),
                marcacion(trabajador, fecha.atTime(12, 0), 1, "Central"),
                marcacion(trabajador, fecha.atTime(13, 0), 2, "Gamarra"),
                marcacion(trabajador, fecha.atTime(18, 0), 2, "Gamarra"));

        ResumenResponse resumen = service.calcularResumen(trabajador, fecha, completas, fecha.atTime(23, 0));

        assertEquals("REQUIERE_REVISION", resumen.estado());
        assertEquals(0, resumen.minutosTrabajados());
        assertTrue(resumen.sesiones().isEmpty());
        List<MarcacionAsistencia> incompletas = new ArrayList<>(completas.subList(0, 3));
        ResumenResponse conPendiente = service.calcularResumen(
                trabajador, fecha, incompletas, fecha.atTime(23, 0));
        assertEquals("REQUIERE_REVISION", conPendiente.estado());
        assertEquals(0, conPendiente.minutosTrabajados());
    }

    @Test
    void cuentaJornadaRotativaAunqueLaSalidaSeaEnOtraSucursal() {
        LocalDate fecha = LocalDate.of(2026, 7, 13);
        Trabajador trabajador = trabajador(fecha, LocalTime.of(8, 0), LocalTime.of(17, 0));
        trabajador.setTurno(null);
        trabajador.setRotativo(true);

        ResumenResponse resumen = service.calcularResumen(trabajador, fecha, List.of(
                marcacion(trabajador, fecha.atTime(8, 0), 1, "Central"),
                marcacion(trabajador, fecha.atTime(17, 0), 2, "Gamarra")), fecha.atTime(23, 0));

        assertEquals("PRESENTE", resumen.estado());
        assertEquals(540, resumen.minutosTrabajados());
        assertEquals(1, resumen.sesiones().size());
        assertEquals("Central", resumen.sesiones().getFirst().sucursal());
    }

    @Test
    void registraTrabajoEnDiaDeDescanso() {
        LocalDate fecha = LocalDate.of(2026, 7, 13);
        Trabajador trabajador = trabajador(fecha, LocalTime.of(8, 0), LocalTime.of(17, 0));
        LocalDate descanso = fecha.plusDays(1);

        ResumenResponse resumen = service.calcularResumen(trabajador, descanso, List.of(
                marcacion(trabajador, descanso.atTime(8, 0), 1, "Central"),
                marcacion(trabajador, descanso.atTime(17, 0), 1, "Central")), descanso.atTime(23, 0));

        assertEquals("TRABAJO_EN_DESCANSO", resumen.estado());
        assertEquals(540, resumen.minutosTrabajados());
    }

    @Test
    void filtraResumenSemanalYSumaSoloLaSucursalSeleccionada() {
        TrabajadorRepository trabajadores = mock(TrabajadorRepository.class);
        MarcacionAsistenciaRepository marcaciones = mock(MarcacionAsistenciaRepository.class);
        AsistenciaService asistencia = new AsistenciaService(trabajadores,
                mock(CargoTrabajadorRepository.class), mock(DispositivoAsistenciaRepository.class), marcaciones,
                mock(SucursalRepository.class), mock(TurnoRepository.class), mock(UsuarioRepository.class));
        LocalDate lunes = LocalDate.of(2026, 7, 13);
        Trabajador trabajador = trabajador(lunes, LocalTime.of(8, 0), LocalTime.of(17, 0));
        trabajador.setTurno(null);
        trabajador.setRotativo(true);
        List<MarcacionAsistencia> marcas = List.of(
                marcacion(trabajador, lunes.atTime(6, 0), 1, "Central"),
                marcacion(trabajador, lunes.atTime(18, 0), 2, "Gamarra"));
        when(trabajadores.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(trabajador));
        when(marcaciones.findByTrabajador_IdTrabajadorInAndFechaHoraBetweenOrderByFechaHoraAsc(
                anyList(), any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(marcas);
        ReflectionTestUtils.setField(asistencia, "pageSize", 10);

        ResumenResponse lunesCentral = asistencia.obtenerResumenSemanal(
                lunes, lunes.plusDays(6), null, 1, null, null, null, null, 0)
                .content().getFirst().dias().getFirst();

        assertEquals(43200, lunesCentral.segundosTrabajados());
        assertEquals(2, lunesCentral.cantidadMarcaciones());
        assertEquals(1, lunesCentral.sesiones().size());
        assertEquals("Central", lunesCentral.sucursalesMarcacion().getFirst().sucursal());
        assertEquals("Gamarra", lunesCentral.sesiones().getFirst().sucursalSalida());
    }

    @Test
    void resumenSemanalAceptaBusquedaDeTrabajador() {
        TrabajadorRepository trabajadores = mock(TrabajadorRepository.class);
        MarcacionAsistenciaRepository marcaciones = mock(MarcacionAsistenciaRepository.class);
        AsistenciaService asistencia = new AsistenciaService(trabajadores,
                mock(CargoTrabajadorRepository.class), mock(DispositivoAsistenciaRepository.class), marcaciones,
                mock(SucursalRepository.class), mock(TurnoRepository.class), mock(UsuarioRepository.class));
        LocalDate lunes = LocalDate.of(2026, 7, 13);
        Trabajador trabajador = trabajador(lunes, LocalTime.of(8, 0), LocalTime.of(17, 0));
        when(trabajadores.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(trabajador));
        when(marcaciones.findByTrabajador_IdTrabajadorInAndFechaHoraBetweenOrderByFechaHoraAsc(
                anyList(), any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(List.of());
        ReflectionTestUtils.setField(asistencia, "pageSize", 10);

        PagedResponse<ResumenSemanalResponse> resultado = asistencia.obtenerResumenSemanal(
                lunes, lunes.plusDays(6), null, 1, "Ana", null, null, null, 0);

        assertEquals(1, resultado.totalElements());
        verify(trabajadores).findAll(any(Specification.class), any(Sort.class));
    }

    @Test
    void exportaTodasLasSucursalesOLaSucursalSeleccionada() throws Exception {
        TrabajadorRepository trabajadores = mock(TrabajadorRepository.class);
        MarcacionAsistenciaRepository marcaciones = mock(MarcacionAsistenciaRepository.class);
        SucursalRepository sucursales = mock(SucursalRepository.class);
        AsistenciaService asistencia = new AsistenciaService(trabajadores,
                mock(CargoTrabajadorRepository.class), mock(DispositivoAsistenciaRepository.class), marcaciones,
                sucursales, mock(TurnoRepository.class), mock(UsuarioRepository.class));
        LocalDate lunes = LocalDate.of(2026, 7, 13);
        Trabajador trabajador = trabajador(lunes, LocalTime.of(8, 0), LocalTime.of(17, 0));
        trabajador.setTurno(null);
        trabajador.setRotativo(true);
        List<MarcacionAsistencia> marcas = List.of(
                marcacion(trabajador, lunes.atTime(6, 0), 1, "Central"),
                marcacion(trabajador, lunes.atTime(18, 0), 2, "Gamarra"));
        Sucursal central = marcas.getFirst().getDispositivo().getSucursal();
        when(trabajadores.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(trabajador));
        when(marcaciones.findByTrabajador_IdTrabajadorInAndFechaHoraBetweenOrderByFechaHoraAsc(
                anyList(), any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(marcas);
        when(sucursales.findByIdSucursalAndDeletedAtIsNull(1)).thenReturn(java.util.Optional.of(central));

        byte[] todas = asistencia.exportarResumenExcel(
                lunes, lunes.plusDays(6), null, null, null, null, null, null, "admin@kiments.pe");
        byte[] soloCentral = asistencia.exportarResumenExcel(
                lunes, lunes.plusDays(6), null, 1, null, null, null, null, "admin@kiments.pe");

        try (Workbook workbookTodas = WorkbookFactory.create(new ByteArrayInputStream(todas));
                Workbook workbookCentral = WorkbookFactory.create(new ByteArrayInputStream(soloCentral))) {
            List<String> sedesTodas = valoresColumna(workbookTodas, "Sesiones por sucursal", 3);
            List<String> salidasTodas = valoresColumna(workbookTodas, "Sesiones por sucursal", 4);
            List<String> sedesCentral = valoresColumna(workbookCentral, "Sesiones por sucursal", 3);
            assertTrue(sedesTodas.contains("Central"));
            assertTrue(salidasTodas.contains("Gamarra"));
            assertTrue(sedesCentral.contains("Central"));
            assertFalse(sedesCentral.contains("Gamarra"));
            assertEquals(43200 / 86400d,
                    workbookCentral.getSheet("Horas trabajadas").getRow(10).getCell(2).getNumericCellValue(),
                    0.0000001);
        }
    }

    @Test
    void analizaTurnosYRotacionSinPenalizarFlexibles() {
        LocalDate fecha = LocalDate.of(2026, 7, 13);
        ResumenResponse puntual = new ResumenResponse(
                1, "1001", "Ana Fija", 1, "Central", 1, "Dia", fecha, null, null,
                fecha.atTime(8, 0), fecha.atTime(17, 0), "PRESENTE", 0, 540, 32400, 2,
                false, 0, false, List.of(), List.of());
        ResumenResponse flexibleSinRegistro = new ResumenResponse(
                2, "1002", "Rosa Flexible", null, null, null, null, fecha, null, null,
                null, null, "SIN_REGISTRO", 0, 0, 0, 0, false, 0, true, List.of(), List.of());
        ResumenResponse rotativa = new ResumenResponse(
                2, "1002", "Rosa Flexible", null, null, null, null, fecha.plusDays(1), null, null,
                fecha.plusDays(1).atTime(8, 0), fecha.plusDays(1).atTime(18, 0), "REGISTRO_INCOMPLETO",
                0, 360, 21600, 3, false, 0, true, List.of(), List.of(
                        new SesionAsistenciaResponse(1, "Central", fecha.plusDays(1).atTime(8, 0),
                                fecha.plusDays(1).atTime(14, 0), "Reloj 1", "Reloj 1",
                                1, "Central", 360, 21600, true),
                        new SesionAsistenciaResponse(2, "Gamarra", fecha.plusDays(1).atTime(15, 0),
                                null, "Reloj 2", null, null, null, 0, 0, false)));

        AnalisisResponse resultado = service.construirAnalisis(
                List.of(puntual, flexibleSinRegistro, rotativa), fecha, fecha.plusDays(1), 1, 0);

        assertEquals(100.0, resultado.indicadores().porcentajeAsistencia());
        assertEquals(900, resultado.indicadores().minutosTrabajados());
        assertEquals(1, resultado.indicadores().registrosIncompletos());
        assertEquals(900, resultado.horasPorSucursal().getFirst().minutosTrabajados());
    }

    private ResumenResponse resumen(
            Trabajador trabajador, LocalDate fecha, LocalDateTime ahora, LocalDateTime... horas) {
        List<MarcacionAsistencia> marcaciones = new ArrayList<>();
        for (LocalDateTime hora : horas) {
            marcaciones.add(marcacion(trabajador, hora,
                    trabajador.getSucursal().getIdSucursal(), trabajador.getSucursal().getNombre()));
        }
        return service.calcularResumen(trabajador, fecha, marcaciones, ahora);
    }

    private MarcacionAsistencia marcacion(
            Trabajador trabajador, LocalDateTime hora, int idSucursal, String nombreSucursal) {
        Sucursal sucursal = new Sucursal();
        sucursal.setIdSucursal(idSucursal);
        sucursal.setNombre(nombreSucursal);
        DispositivoAsistencia dispositivo = new DispositivoAsistencia();
        dispositivo.setIdDispositivo(idSucursal);
        dispositivo.setNombre("Reloj " + nombreSucursal);
        dispositivo.setSucursal(sucursal);
        MarcacionAsistencia marcacion = new MarcacionAsistencia();
        marcacion.setTrabajador(trabajador);
        marcacion.setDispositivo(dispositivo);
        marcacion.setSucursal(sucursal);
        marcacion.setFechaHora(hora);
        return marcacion;
    }

    private List<String> valoresColumna(Workbook workbook, String hoja, int columna) {
        List<String> valores = new ArrayList<>();
        workbook.getSheet(hoja).forEach(row -> {
            if (row.getCell(columna) != null) {
                valores.add(row.getCell(columna).toString());
            }
        });
        return valores;
    }

    private Trabajador trabajador(LocalDate fecha, LocalTime inicio, LocalTime fin) {
        Sucursal sucursal = new Sucursal();
        sucursal.setIdSucursal(1);
        sucursal.setNombre("Principal");

        Turno turno = new Turno();
        turno.setIdTurno(1);
        turno.setNombre("Prueba");
        turno.setHoraInicio(inicio);
        turno.setHoraFin(fin);
        turno.setToleranciaMinutos(10);
        TurnoDia dia = new TurnoDia();
        dia.setTurno(turno);
        dia.setDiaSemana(DiaSemana.fromJavaDayOfWeek(fecha.getDayOfWeek()));
        dia.setHoraInicio(inicio);
        dia.setHoraFin(fin);
        turno.setDiasSemana(new ArrayList<>(List.of(dia)));

        Trabajador trabajador = new Trabajador();
        trabajador.setIdTrabajador(1);
        trabajador.setCodigoZkteco("1001");
        trabajador.setNombres("Ana");
        trabajador.setApellidos("Prueba");
        trabajador.setSucursal(sucursal);
        trabajador.setTurno(turno);
        trabajador.setFechaCreacion(fecha.atStartOfDay());
        return trabajador;
    }

    private CargoTrabajadorRepository cargoRepositoryActivo() {
        CargoTrabajadorRepository cargos = mock(CargoTrabajadorRepository.class);
        CargoTrabajador cargo = new CargoTrabajador();
        cargo.setIdCargo(1);
        cargo.setNombre("Operaciones");
        cargo.setEstado("ACTIVO");
        when(cargos.findByIdCargo(1)).thenReturn(java.util.Optional.of(cargo));
        return cargos;
    }

    private AsistenciaService servicioAdms(
            TrabajadorRepository trabajadores,
            DispositivoAsistenciaRepository dispositivos,
            MarcacionAsistenciaRepository marcaciones) {
        AsistenciaService asistencia = new AsistenciaService(trabajadores, mock(CargoTrabajadorRepository.class),
                dispositivos, marcaciones, mock(SucursalRepository.class), mock(TurnoRepository.class),
                mock(UsuarioRepository.class));
        ReflectionTestUtils.setField(asistencia, "admsMaxEvents", 1000);
        ReflectionTestUtils.setField(asistencia, "admsMaxPastDays", 7);
        ReflectionTestUtils.setField(asistencia, "admsMaxFutureMinutes", 10);
        ReflectionTestUtils.setField(asistencia, "admsMaxBodyBytes", 256 * 1024);
        return asistencia;
    }

    private DispositivoAsistencia dispositivoActivo() {
        Sucursal sucursal = new Sucursal();
        sucursal.setIdSucursal(1);
        sucursal.setNombre("Principal");
        DispositivoAsistencia dispositivo = new DispositivoAsistencia();
        dispositivo.setIdDispositivo(3);
        dispositivo.setNumeroSerie("SN-1");
        dispositivo.setEstado("ACTIVO");
        dispositivo.setSucursal(sucursal);
        return dispositivo;
    }
}
