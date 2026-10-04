package com.sistemapos.sistematextil.repositories;

import java.util.Collection;
import java.util.Optional;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sistemapos.sistematextil.model.CrmWhatsappAiSaleDraft;
import com.sistemapos.sistematextil.model.CrmWhatsappAiSaleDraftStatus;

import jakarta.persistence.LockModeType;

public interface CrmWhatsappAiSaleDraftRepository extends JpaRepository<CrmWhatsappAiSaleDraft, Long> {
    List<CrmWhatsappAiSaleDraft> findByStatusInAndExpiresAtBefore(
            Collection<CrmWhatsappAiSaleDraftStatus> statuses, LocalDateTime expiresAt);
    Optional<CrmWhatsappAiSaleDraft> findFirstByConversation_IdConversationAndStatusInOrderByCreatedAtDesc(
            Long conversationId, Collection<CrmWhatsappAiSaleDraftStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select distinct d from CrmWhatsappAiSaleDraft d left join fetch d.items where d.idAiSaleDraft = :id")
    Optional<CrmWhatsappAiSaleDraft> findForUpdate(@Param("id") Long id);

    @Query("select distinct d from CrmWhatsappAiSaleDraft d left join fetch d.items where d.idAiSaleDraft = :id")
    Optional<CrmWhatsappAiSaleDraft> findDetailedById(@Param("id") Long id);
}
