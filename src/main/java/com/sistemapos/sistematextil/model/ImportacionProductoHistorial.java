package com.sistemapos.sistematextil.model;

import java.time.LocalDateTime;

import com.sistemapos.sistematextil.model.converter.EstadoActivoConverter;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "importacion_producto_historial")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class ImportacionProductoHistorial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_importacion")
    private Integer idImportacion;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_sucursal")
    private Sucursal sucursal;

    @Column(name = "nombre_archivo", nullable = false, length = 255)
    private String nombreArchivo;

    @Column(name = "tamano_bytes", nullable = false)
    private Long tamanoBytes;

    @Column(name = "filas_procesadas", nullable = false)
    private Integer filasProcesadas;

    @Column(name = "productos_creados", nullable = false)
    private Integer productosCreados;

    @Column(name = "productos_actualizados", nullable = false)
    private Integer productosActualizados;

    @Column(name = "variantes_guardadas", nullable = false)
    private Integer variantesGuardadas;

    @Column(name = "categorias_creadas", nullable = false)
    private Integer categoriasCreadas;

    @Column(name = "colores_creados", nullable = false)
    private Integer coloresCreados;

    @Column(name = "tallas_creadas", nullable = false)
    private Integer tallasCreadas;

    @Column(name = "estado", nullable = false, length = 10)
    private String estado;

    @Column(name = "mensaje_error", length = 1000)
    private String mensajeError;

    @Column(name = "duracion_ms")
    private Integer duracionMs;

    @Convert(converter = EstadoActivoConverter.class)
    @Column(name = "activo", nullable = false)
    private String activo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        this.updatedAt = now;
        if (this.activo == null || this.activo.isBlank()) {
            this.activo = "ACTIVO";
        }
        if (this.estado == null || this.estado.isBlank()) {
            this.estado = "EXITOSA";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
