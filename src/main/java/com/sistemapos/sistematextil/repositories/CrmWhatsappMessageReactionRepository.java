package com.sistemapos.sistematextil.repositories;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sistemapos.sistematextil.model.CrmWhatsappMessageReaction;

public interface CrmWhatsappMessageReactionRepository extends JpaRepository<CrmWhatsappMessageReaction, Long> {
    Optional<CrmWhatsappMessageReaction> findFirstByMessage_IdMessageAndDirection(Long messageId, String direction);

    @Query("""
            SELECT r FROM CrmWhatsappMessageReaction r
            WHERE r.message.idMessage IN :messageIds AND r.deletedAt IS NULL
            ORDER BY r.idReaction ASC
            """)
    List<CrmWhatsappMessageReaction> findActiveByMessageIds(@Param("messageIds") Collection<Long> messageIds);
}
