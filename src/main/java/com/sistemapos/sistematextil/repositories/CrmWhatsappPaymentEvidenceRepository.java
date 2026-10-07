package com.sistemapos.sistematextil.repositories;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sistemapos.sistematextil.model.CrmWhatsappPaymentEvidence;
import com.sistemapos.sistematextil.model.CrmWhatsappPaymentEvidenceStatus;
import com.sistemapos.sistematextil.model.CrmWhatsappPaymentProcessingStatus;
import com.sistemapos.sistematextil.model.CrmWhatsappPaymentRequestStatus;
import com.sistemapos.sistematextil.model.CrmWhatsappPaymentReservationStatus;

import jakarta.persistence.LockModeType;

public interface CrmWhatsappPaymentEvidenceRepository extends JpaRepository<CrmWhatsappPaymentEvidence, Long> {
    Optional<CrmWhatsappPaymentEvidence> findByMessage_IdMessage(Long messageId);
    Optional<CrmWhatsappPaymentEvidence> findFirstByPaymentRequest_IdPaymentRequestAndValidationStatusOrderByCreatedAtDesc(
            Long paymentRequestId, CrmWhatsappPaymentEvidenceStatus status);
    List<CrmWhatsappPaymentEvidence> findByConversation_IdConversationOrderByCreatedAtDesc(Long conversationId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "DELETE FROM crm_whatsapp_payment_evidence WHERE id_conversation = :conversationId", nativeQuery = true)
    int deleteByConversationId(@Param("conversationId") Long conversationId);

    @Query("select e from CrmWhatsappPaymentEvidence e where e.sha256 = :sha and e.conversation.connection.empresa.idEmpresa = :companyId order by e.createdAt")
    List<CrmWhatsappPaymentEvidence> findDuplicatesByHash(
            @Param("sha") String sha, @Param("companyId") Integer companyId, Pageable pageable);

    @Query("select e from CrmWhatsappPaymentEvidence e where lower(e.operationCode) = lower(:code) and e.conversation.connection.empresa.idEmpresa = :companyId order by e.createdAt")
    List<CrmWhatsappPaymentEvidence> findDuplicatesByOperation(
            @Param("code") String code, @Param("companyId") Integer companyId, Pageable pageable);

    @Query("select e from CrmWhatsappPaymentEvidence e where e.perceptualHash is not null and e.conversation.connection.empresa.idEmpresa = :companyId order by e.createdAt desc")
    List<CrmWhatsappPaymentEvidence> findRecentVisualHashes(
            @Param("companyId") Integer companyId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from CrmWhatsappPaymentEvidence e where e.idPaymentEvidence = :id")
    Optional<CrmWhatsappPaymentEvidence> findForUpdate(@Param("id") Long id);

    @Query("select e from CrmWhatsappPaymentEvidence e where e.processingStatus = :status and e.availableAt <= :now order by e.createdAt")
    List<CrmWhatsappPaymentEvidence> findReady(
            @Param("status") CrmWhatsappPaymentProcessingStatus status,
            @Param("now") LocalDateTime now,
            Pageable pageable);

    @Query("""
            select e.idPaymentEvidence
            from CrmWhatsappPaymentEvidence e
            where e.validationStatus = :evidenceStatus
              and e.processingStatus = :processingStatus
              and e.duplicateOfId is null
              and e.detectedAmount is not null
              and e.detectedAmount = e.paymentRequest.expectedAmount
              and e.paymentRequest.status in :requestStatuses
              and e.paymentRequest.reservationStatus = :reservationStatus
              and e.paymentRequest.expiresAt >= :now
            order by e.createdAt
            """)
    List<Long> findObservedWithExactAmount(
            @Param("evidenceStatus") CrmWhatsappPaymentEvidenceStatus evidenceStatus,
            @Param("processingStatus") CrmWhatsappPaymentProcessingStatus processingStatus,
            @Param("requestStatuses") List<CrmWhatsappPaymentRequestStatus> requestStatuses,
            @Param("reservationStatus") CrmWhatsappPaymentReservationStatus reservationStatus,
            @Param("now") LocalDateTime now,
            Pageable pageable);
}
