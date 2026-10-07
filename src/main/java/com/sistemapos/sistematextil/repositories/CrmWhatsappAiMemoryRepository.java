package com.sistemapos.sistematextil.repositories;

import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import com.sistemapos.sistematextil.model.CrmWhatsappAiMemory;

public interface CrmWhatsappAiMemoryRepository extends JpaRepository<CrmWhatsappAiMemory, Long> {
    @EntityGraph(attributePaths = {"items", "conversation", "conversation.connection", "conversation.cliente"})
    Optional<CrmWhatsappAiMemory> findByConversation_IdConversation(Long conversationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM CrmWhatsappAiMemory m WHERE m.conversation.idConversation = :conversationId")
    Optional<CrmWhatsappAiMemory> findForUpdate(@Param("conversationId") Long conversationId);

    @Modifying
    @Query(value = "DELETE FROM crm_whatsapp_ai_memory", nativeQuery = true)
    int deleteAllAiMemories();
}
