package com.sistemapos.sistematextil.repositories;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sistemapos.sistematextil.model.MarcacionAsistencia;

public interface MarcacionAsistenciaRepository
        extends JpaRepository<MarcacionAsistencia, Long>, JpaSpecificationExecutor<MarcacionAsistencia> {

    List<MarcacionAsistencia> findByTrabajador_IdTrabajadorInAndFechaHoraBetweenOrderByFechaHoraAsc(
            List<Integer> idsTrabajadores, LocalDateTime desde, LocalDateTime hasta);

    List<MarcacionAsistencia> findByTrabajador_IdTrabajadorAndFechaHoraBetweenOrderByFechaHoraAsc(
            Integer idTrabajador, LocalDateTime desde, LocalDateTime hasta);

    @Modifying
    @Query(value = """
            UPDATE marcacion_asistencia
            SET id_trabajador = :idTrabajador
            WHERE id_trabajador IS NULL AND codigo_zkteco = :codigoZkteco
            """, nativeQuery = true)
    int vincularMarcaciones(@Param("idTrabajador") Integer idTrabajador,
            @Param("codigoZkteco") String codigoZkteco);

    @Modifying
    @Query(value = """
            INSERT IGNORE INTO marcacion_asistencia
              (id_dispositivo, id_sucursal, id_trabajador, codigo_zkteco, fecha_hora,
               tipo_marcacion, tipo_verificacion, recibido_at, origen)
            VALUES
              (:idDispositivo, :idSucursal, :idTrabajador, :codigoZkteco, :fechaHora,
               :tipoMarcacion, :tipoVerificacion, :recibidoAt, 'BIOMETRICO')
            """, nativeQuery = true)
    int insertarSiNoExiste(
            @Param("idDispositivo") Integer idDispositivo,
            @Param("idSucursal") Integer idSucursal,
            @Param("idTrabajador") Integer idTrabajador,
            @Param("codigoZkteco") String codigoZkteco,
            @Param("fechaHora") LocalDateTime fechaHora,
            @Param("tipoMarcacion") String tipoMarcacion,
            @Param("tipoVerificacion") String tipoVerificacion,
            @Param("recibidoAt") LocalDateTime recibidoAt);
}
