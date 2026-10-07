package com.sistemapos.sistematextil.repositories;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sistemapos.sistematextil.model.CrmWhatsappPaymentRequest;
import com.sistemapos.sistematextil.model.CrmWhatsappPaymentRequestStatus;

import jakarta.persistence.LockModeType;

public interface CrmWhatsappPaymentRequestRepository extends JpaRepository<CrmWhatsappPaymentRequest, Long> {
    List<CrmWhatsappPaymentRequest> findByStatusIn(List<CrmWhatsappPaymentRequestStatus> statuses);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE crm_whatsapp_payment_request SET id_ai_sale_draft = NULL WHERE id_ai_sale_draft IS NOT NULL", nativeQuery = true)
    int detachAllAiSaleDrafts();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE crm_whatsapp_payment_request
            SET id_ai_sale_draft = NULL, ai_sale_draft_version = NULL
            WHERE id_conversation = :conversationId
            """, nativeQuery = true)
    int detachAiSaleDraftsByConversationId(@Param("conversationId") Long conversationId);

    List<CrmWhatsappPaymentRequest> findByConversation_IdConversationAndStatusInOrderByCreatedAtDesc(
            Long conversationId, List<CrmWhatsappPaymentRequestStatus> statuses);

    Optional<CrmWhatsappPaymentRequest> findFirstByConversation_IdConversationAndStatusInOrderByCreatedAtDesc(
            Long conversationId, List<CrmWhatsappPaymentRequestStatus> statuses);

    Optional<CrmWhatsappPaymentRequest> findFirstByAiSaleDraft_IdAiSaleDraftAndStatusInOrderByCreatedAtDesc(
            Long draftId, List<CrmWhatsappPaymentRequestStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from CrmWhatsappPaymentRequest r where r.idPaymentRequest = :id")
    Optional<CrmWhatsappPaymentRequest> findForUpdate(@Param("id") Long id);

    List<CrmWhatsappPaymentRequest> findByStatusInAndExpiresAtBefore(
            List<CrmWhatsappPaymentRequestStatus> statuses, LocalDateTime now);
}
