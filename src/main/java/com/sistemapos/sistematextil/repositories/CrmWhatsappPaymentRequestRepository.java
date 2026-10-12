package com.sistemapos.sistematextil.repositories;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

import com.sistemapos.sistematextil.model.CrmWhatsappPaymentRequest;
import com.sistemapos.sistematextil.model.CrmWhatsappPaymentRequestStatus;

import jakarta.persistence.LockModeType;

public interface CrmWhatsappPaymentRequestRepository extends JpaRepository<CrmWhatsappPaymentRequest, Long> {
    List<CrmWhatsappPaymentRequest> findByStatusIn(List<CrmWhatsappPaymentRequestStatus> statuses);

    List<CrmWhatsappPaymentRequest> findByConversation_IdConversationAndStatusInOrderByCreatedAtDesc(
            Long conversationId, List<CrmWhatsappPaymentRequestStatus> statuses);

    Optional<CrmWhatsappPaymentRequest> findFirstByConversation_IdConversationAndStatusInOrderByCreatedAtDesc(
            Long conversationId, List<CrmWhatsappPaymentRequestStatus> statuses);

    List<CrmWhatsappPaymentRequest> findByConversation_IdConversationInAndStatusInOrderByCreatedAtDesc(
            List<Long> conversationIds, List<CrmWhatsappPaymentRequestStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from CrmWhatsappPaymentRequest r where r.idPaymentRequest = :id")
    Optional<CrmWhatsappPaymentRequest> findForUpdate(@Param("id") Long id);

    List<CrmWhatsappPaymentRequest> findByStatusInAndExpiresAtBefore(
            List<CrmWhatsappPaymentRequestStatus> statuses, LocalDateTime now);

    List<CrmWhatsappPaymentRequest> findByAgentSessionIsNullAndStatusInAndExpiresAtBefore(
            List<CrmWhatsappPaymentRequestStatus> statuses, LocalDateTime now);

    Optional<CrmWhatsappPaymentRequest> findFirstByAgentSession_IdAgentSessionAndStatusInOrderByCreatedAtDesc(
            Long sessionId, List<CrmWhatsappPaymentRequestStatus> statuses);

    @Query("""
            select r.idPaymentRequest
            from CrmWhatsappPaymentRequest r
            where r.agentSession is not null
              and r.status in :statuses
              and r.expiresAt <= :now
            order by r.expiresAt asc, r.idPaymentRequest asc
            """)
    List<Long> findExpiredAgentRequestIds(
            @Param("statuses") List<CrmWhatsappPaymentRequestStatus> statuses,
            @Param("now") LocalDateTime now,
            Pageable pageable);
}
