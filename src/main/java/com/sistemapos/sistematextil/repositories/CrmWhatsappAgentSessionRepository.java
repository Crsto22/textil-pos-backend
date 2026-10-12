package com.sistemapos.sistematextil.repositories;

import java.util.Optional;
import java.util.List;
import java.time.LocalDateTime;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sistemapos.sistematextil.model.CrmWhatsappAgentSession;
import com.sistemapos.sistematextil.model.CrmWhatsappAgentState;
import com.sistemapos.sistematextil.model.CrmWhatsappFulfillmentStatus;

import jakarta.persistence.LockModeType;

public interface CrmWhatsappAgentSessionRepository extends JpaRepository<CrmWhatsappAgentSession, Long> {
    Optional<CrmWhatsappAgentSession> findBySessionKey(String sessionKey);

    Optional<CrmWhatsappAgentSession> findByConversation_IdConversationAndActiveSlotTrue(Long conversationId);

    Optional<CrmWhatsappAgentSession> findFirstByConversation_IdConversationOrderByIdAgentSessionDesc(Long conversationId);

    List<CrmWhatsappAgentSession> findByConversation_IdConversationInAndActiveSlotTrue(List<Long> conversationIds);

    boolean existsByConversation_IdConversation(Long conversationId);

    long countByConnection_IdConnectionAndCreatedAtGreaterThanEqual(Long connectionId, LocalDateTime createdAfter);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT s FROM CrmWhatsappAgentSession s
            WHERE s.idAgentSession = :sessionId
            """)
    Optional<CrmWhatsappAgentSession> findForUpdate(@Param("sessionId") Long sessionId);

    @Query("""
            select s.idAgentSession
            from CrmWhatsappAgentSession s
            where s.activeSlot = true
              and s.state = :state
              and exists (
                    select f.idFulfillment
                    from CrmWhatsappFulfillment f
                    where f.agentSession = s
                      and f.status = :fulfillmentStatus
                      and f.venta is null
              )
            order by s.stateChangedAt, s.idAgentSession
            """)
    List<Long> findReadyForSaleSessionIds(
            @Param("state") CrmWhatsappAgentState state,
            @Param("fulfillmentStatus") CrmWhatsappFulfillmentStatus fulfillmentStatus,
            Pageable pageable);
}
