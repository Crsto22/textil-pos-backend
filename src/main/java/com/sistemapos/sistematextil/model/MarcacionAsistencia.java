package com.sistemapos.sistematextil.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "marcacion_asistencia", uniqueConstraints = @UniqueConstraint(
        name = "uk_marcacion_dispositivo_codigo_fecha",
        columnNames = { "id_dispositivo", "codigo_zkteco", "fecha_hora" }))
@Getter
@Setter
@NoArgsConstructor
public class MarcacionAsistencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_marcacion")
    private Long idMarcacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_dispositivo")
    private DispositivoAsistencia dispositivo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_sucursal", nullable = false)
    private Sucursal sucursal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_trabajador")
    private Trabajador trabajador;

    @Column(name = "codigo_zkteco", nullable = false, length = 24)
    private String codigoZkteco;

    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora;

    @Column(name = "tipo_marcacion", length = 20)
    private String tipoMarcacion;

    @Column(name = "tipo_verificacion", length = 20)
    private String tipoVerificacion;

    @Column(name = "recibido_at", nullable = false)
    private LocalDateTime recibidoAt;

    @Column(name = "origen", nullable = false, length = 12)
    private String origen = "BIOMETRICO";

    @Column(name = "tipo_evento", length = 10)
    private String tipoEvento;

    @Column(name = "motivo_registro", length = 255)
    private String motivoRegistro;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario_registro")
    private Usuario usuarioRegistro;

    @Column(name = "anulada_at")
    private LocalDateTime anuladaAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario_anula")
    private Usuario usuarioAnula;

    @Column(name = "motivo_anulacion", length = 255)
    private String motivoAnulacion;
}
