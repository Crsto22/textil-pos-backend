package com.sistemapos.sistematextil.model;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.LocalDate;
import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "crm_whatsapp_ai_config")
@Getter
@Setter
@NoArgsConstructor
public class CrmWhatsappAiConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_ai_config")
    private Long idAiConfig;

    @OneToOne(optional = false)
    @JoinColumn(name = "id_connection", nullable = false, unique = true)
    private CrmWhatsappConnection connection;

    @Enumerated(EnumType.STRING)
    @Column(name = "modo", nullable = false, length = 20)
    private CrmWhatsappAiMode modo;

    @Column(name = "zona_horaria", nullable = false, length = 60)
    private String zonaHoraria;

    @Column(name = "dias_atencion", nullable = false, length = 100)
    private String diasAtencion;

    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;

    @Column(name = "hora_fin", nullable = false)
    private LocalTime horaFin;

    @Column(name = "espera_respuesta_segundos", nullable = false)
    private Integer esperaRespuestaSegundos;

    @Enumerated(EnumType.STRING)
    @Column(name = "tono", nullable = false, length = 20)
    private CrmWhatsappAiTone tono;

    @Column(name = "instrucciones_personalizadas", length = 1000)
    private String instruccionesPersonalizadas;

    @Column(name = "intenciones_permitidas", nullable = false, length = 500)
    private String intencionesPermitidas;

    @Column(name = "max_respuestas_automaticas", nullable = false)
    private Integer maxRespuestasAutomaticas;

    @Column(name = "confianza_minima", nullable = false)
    private Integer confianzaMinima;

    @Column(name = "transferir_baja_confianza", nullable = false)
    private Boolean transferirBajaConfianza;

    @Column(name = "transferir_solicitud_humana", nullable = false)
    private Boolean transferirSolicitudHumana;

    @Column(name = "transferir_asunto_sensible", nullable = false)
    private Boolean transferirAsuntoSensible;

    @Column(name = "transferir_imagenes_asesora", nullable = false)
    private Boolean transferirImagenesAsesora;

    @Column(name = "mostrar_productos_nuevos", nullable = false)
    private Boolean mostrarProductosNuevos;

    @Column(name = "mandar_catalogo_imagenes", nullable = false)
    private Boolean mandarCatalogoImagenes;

    @Column(name = "sugerir_promociones_carrito", nullable = false)
    private Boolean sugerirPromocionesCarrito;

    @Column(name = "gemini_api_key_ciphertext", columnDefinition = "TEXT")
    private String geminiApiKeyCiphertext;

    @Column(name = "gemini_api_key_nonce", length = 64)
    private String geminiApiKeyNonce;

    @Column(name = "gemini_model", length = 100)
    private String geminiModel;

    @Column(name = "api_key_updated_at")
    private LocalDateTime apiKeyUpdatedAt;

    @jakarta.persistence.ManyToOne(fetch = jakarta.persistence.FetchType.LAZY)
    @JoinColumn(name = "api_key_updated_by")
    private Usuario apiKeyUpdatedBy;

    @Column(name = "daily_token_limit")
    private Long dailyTokenLimit;

    @Column(name = "monthly_token_limit")
    private Long monthlyTokenLimit;

    @Column(name = "monthly_budget_usd", precision = 12, scale = 4)
    private BigDecimal monthlyBudgetUsd;

    @Column(name = "input_cost_per_million_usd", precision = 12, scale = 6)
    private BigDecimal inputCostPerMillionUsd;

    @Column(name = "output_cost_per_million_usd", precision = 12, scale = 6)
    private BigDecimal outputCostPerMillionUsd;

    @Column(name = "automatic_rollout_percent", nullable = false)
    private Integer automaticRolloutPercent;

    @Column(name = "natural_response_enabled", nullable = false)
    private Boolean naturalResponseEnabled;

    @Column(name = "natural_response_rollout_percent", nullable = false)
    private Integer naturalResponseRolloutPercent;

    @Enumerated(EnumType.STRING)
    @Column(name = "shipping_date_mode", nullable = false, length = 24)
    private CrmWhatsappDeliveryDateMode shippingDateMode;

    @Column(name = "shipping_specific_date")
    private LocalDate shippingSpecificDate;

    @Column(name = "same_day_shipping_cutoff", nullable = false)
    private LocalTime sameDayShippingCutoff;

    @Enumerated(EnumType.STRING)
    @Column(name = "pickup_date_mode", nullable = false, length = 24)
    private CrmWhatsappDeliveryDateMode pickupDateMode;

    @Column(name = "pickup_specific_date")
    private LocalDate pickupSpecificDate;

    @Column(name = "pickup_opens_at", nullable = false)
    private LocalTime pickupOpensAt;

    @Column(name = "pickup_closes_at", nullable = false)
    private LocalTime pickupClosesAt;

    @Column(name = "operational_status", nullable = false, length = 30)
    private String operationalStatus;

    @Column(name = "operational_reason", length = 300)
    private String operationalReason;

    @Column(name = "operational_changed_at")
    private LocalDateTime operationalChangedAt;

    @jakarta.persistence.ManyToOne(fetch = jakarta.persistence.FetchType.LAZY)
    @JoinColumn(name = "operational_changed_by")
    private Usuario operationalChangedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (transferirImagenesAsesora == null) transferirImagenesAsesora = false;
        if (mostrarProductosNuevos == null) mostrarProductosNuevos = false;
        if (mandarCatalogoImagenes == null) mandarCatalogoImagenes = false;
        if (sugerirPromocionesCarrito == null) sugerirPromocionesCarrito = false;
        if (automaticRolloutPercent == null) automaticRolloutPercent = 0;
        if (naturalResponseEnabled == null) naturalResponseEnabled = false;
        if (naturalResponseRolloutPercent == null) naturalResponseRolloutPercent = 0;
        if (shippingDateMode == null) shippingDateMode = CrmWhatsappDeliveryDateMode.AUTOMATICA;
        if (sameDayShippingCutoff == null) sameDayShippingCutoff = LocalTime.of(15, 0);
        if (pickupDateMode == null) pickupDateMode = CrmWhatsappDeliveryDateMode.AUTOMATICA;
        if (pickupOpensAt == null) pickupOpensAt = LocalTime.of(10, 0);
        if (pickupClosesAt == null) pickupClosesAt = LocalTime.of(18, 0);
        if (operationalStatus == null || operationalStatus.isBlank()) operationalStatus = "ACTIVE";
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
