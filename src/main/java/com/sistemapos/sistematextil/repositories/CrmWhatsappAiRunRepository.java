package com.sistemapos.sistematextil.repositories;

import java.util.List;
import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import com.sistemapos.sistematextil.model.CrmWhatsappAiRun;

public interface CrmWhatsappAiRunRepository extends JpaRepository<CrmWhatsappAiRun, Long> {
    List<CrmWhatsappAiRun> findTop20ByConversation_IdConversationOrderByCreatedAtDesc(Long conversationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM CrmWhatsappAiRun r WHERE r.idAiRun = :runId")
    java.util.Optional<CrmWhatsappAiRun> findForDecision(@Param("runId") Long runId);

    @org.springframework.data.jpa.repository.Query(value = """
            SELECT COUNT(*)
            FROM crm_whatsapp_ai_run r
            JOIN crm_whatsapp_ai_job j ON j.id_ai_job = r.id_ai_job
            WHERE r.id_conversation = :conversationId
              AND j.trigger_type = 'AUTOMATIC'
              AND r.outcome = 'DRAFT_READY'
              AND r.id_message > COALESCE((
                    SELECT MAX(m.id_message)
                    FROM crm_whatsapp_message m
                    WHERE m.id_conversation = :conversationId
                      AND m.direction = 'OUTGOING'
                      AND m.deleted_at IS NULL
              ), 0)
            """, nativeQuery = true)
    long countAutomaticDraftsSinceLastOutgoing(
            @org.springframework.data.repository.query.Param("conversationId") Long conversationId);

    @Query(value = """
            SELECT COALESCE(SUM(r.total_tokens), 0), COALESCE(SUM(r.estimated_cost_usd), 0)
            FROM crm_whatsapp_ai_run r
            JOIN crm_whatsapp_conversation c ON c.id_conversation = r.id_conversation
            WHERE c.id_connection = :connectionId AND r.created_at >= :fromDate
            """, nativeQuery = true)
    Object[] sumUsageSince(@Param("connectionId") Long connectionId, @Param("fromDate") LocalDateTime fromDate);

    @Query(value = """
            SELECT COUNT(*) FROM crm_whatsapp_ai_run r
            JOIN crm_whatsapp_conversation c ON c.id_conversation = r.id_conversation
            WHERE c.id_connection = :connectionId AND r.created_at >= :fromDate
            """, nativeQuery = true)
    long countSince(@Param("connectionId") Long connectionId, @Param("fromDate") LocalDateTime fromDate);

    long countByConversation_IdConversationAndCreatedAtAfter(Long conversationId, LocalDateTime fromDate);
}
