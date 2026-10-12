package com.sistemapos.sistematextil.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "shalom_agency_sync")
@Getter
@Setter
@NoArgsConstructor
public class ShalomAgencySync {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_shalom_agency_sync") private Long id;
    @Column(nullable = false, length = 20) private String status;
    @Column(name = "source_total") private Integer sourceTotal;
    @Column(name = "received_count", nullable = false) private int receivedCount;
    @Column(name = "created_count", nullable = false) private int createdCount;
    @Column(name = "updated_count", nullable = false) private int updatedCount;
    @Column(name = "deactivated_count", nullable = false) private int deactivatedCount;
    @Column(name = "error_message", length = 1000) private String errorMessage;
    @Column(name = "started_at", nullable = false) private LocalDateTime startedAt;
    @Column(name = "completed_at") private LocalDateTime completedAt;
}
