package com.sistemapos.sistematextil.repositories;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.EntityGraph;

import com.sistemapos.sistematextil.model.CrmWhatsappAiJob;
import com.sistemapos.sistematextil.model.CrmWhatsappAiJobStatus;

public interface CrmWhatsappAiJobRepository extends JpaRepository<CrmWhatsappAiJob, Long> {

    Optional<CrmWhatsappAiJob> findByMessage_IdMessage(Long idMessage);

    @EntityGraph(attributePaths = {
            "message", "conversation", "conversation.connection", "conversation.connection.empresa",
            "conversation.connection.sucursal", "conversation.connection.sucursal.empresa",
            "conversation.cliente", "conversation.assignedUser"
    })
    @Query("SELECT j FROM CrmWhatsappAiJob j WHERE j.idAiJob = :id")
    Optional<CrmWhatsappAiJob> findDetailedById(@Param("id") Long id);

    @Query("""
            SELECT j.idAiJob
            FROM CrmWhatsappAiJob j
            WHERE j.availableAt <= :now
              AND j.attempts < j.maxAttempts
              AND (j.status = com.sistemapos.sistematextil.model.CrmWhatsappAiJobStatus.PENDING
                   OR (j.status = com.sistemapos.sistematextil.model.CrmWhatsappAiJobStatus.PROCESSING
                       AND j.lockedAt < :staleBefore))
            ORDER BY j.availableAt ASC, j.idAiJob ASC
            """)
    List<Long> findReadyJobIds(
            @Param("now") LocalDateTime now,
            @Param("staleBefore") LocalDateTime staleBefore,
            Pageable pageable);

    @Modifying
    @Query("""
            UPDATE CrmWhatsappAiJob j
            SET j.status = com.sistemapos.sistematextil.model.CrmWhatsappAiJobStatus.PROCESSING,
                j.lockedAt = :now,
                j.attempts = j.attempts + 1
            WHERE j.idAiJob = :id
              AND j.attempts < j.maxAttempts
              AND (j.status = com.sistemapos.sistematextil.model.CrmWhatsappAiJobStatus.PENDING
                   OR (j.status = com.sistemapos.sistematextil.model.CrmWhatsappAiJobStatus.PROCESSING
                       AND j.lockedAt < :staleBefore))
            """)
    int claim(@Param("id") Long id, @Param("now") LocalDateTime now, @Param("staleBefore") LocalDateTime staleBefore);

    @Modifying
    @Query("""
            UPDATE CrmWhatsappAiJob j
            SET j.status = :superseded, j.processedAt = :now, j.lockedAt = NULL
            WHERE j.conversation.idConversation = :conversationId
              AND j.status = :pending
            """)
    int supersedePending(
            @Param("conversationId") Long conversationId,
            @Param("pending") CrmWhatsappAiJobStatus pending,
            @Param("superseded") CrmWhatsappAiJobStatus superseded,
            @Param("now") LocalDateTime now);

    boolean existsByConversation_IdConversationAndMessage_IdMessageGreaterThan(
            Long conversationId,
            Long messageId);

    @Modifying
    @Query("""
            UPDATE CrmWhatsappAiJob j SET j.status = :cancelled, j.processedAt = :now,
                j.lockedAt = NULL, j.lastError = :reason
            WHERE j.conversation.connection.idConnection = :connectionId
              AND j.status IN :statuses
            """)
    int cancelForConnection(@Param("connectionId") Long connectionId,
            @Param("statuses") List<CrmWhatsappAiJobStatus> statuses,
            @Param("cancelled") CrmWhatsappAiJobStatus cancelled,
            @Param("reason") String reason, @Param("now") LocalDateTime now);
}
