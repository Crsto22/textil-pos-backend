package com.sistemapos.sistematextil.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sistemapos.sistematextil.model.CrmWhatsappConversationTag;

public interface CrmWhatsappConversationTagRepository extends JpaRepository<CrmWhatsappConversationTag, Long> {

    @Modifying
    @Query(value = "DELETE FROM crm_whatsapp_conversation_tag", nativeQuery = true)
    int deleteAllConversationTags();

    @Query("""
            SELECT ct
            FROM CrmWhatsappConversationTag ct
            JOIN FETCH ct.tag t
            WHERE ct.deletedAt IS NULL
              AND t.deletedAt IS NULL
              AND ct.conversation.idConversation = :conversationId
            ORDER BY t.nombre ASC
            """)
    List<CrmWhatsappConversationTag> findActiveByConversationId(@Param("conversationId") Long conversationId);

    @Query("""
            SELECT ct
            FROM CrmWhatsappConversationTag ct
            JOIN FETCH ct.tag t
            WHERE ct.deletedAt IS NULL
              AND t.deletedAt IS NULL
              AND ct.conversation.idConversation IN :conversationIds
            ORDER BY ct.conversation.idConversation ASC, t.nombre ASC
            """)
    List<CrmWhatsappConversationTag> findActiveByConversationIds(@Param("conversationIds") List<Long> conversationIds);

    Optional<CrmWhatsappConversationTag> findFirstByConversation_IdConversationAndTag_IdTagAndDeletedAtIsNull(
            Long conversationId,
            Long tagId);

    Optional<CrmWhatsappConversationTag> findFirstByConversation_IdConversationAndTag_IdTagOrderByIdConversationTagAsc(
            Long conversationId,
            Long tagId);

    List<CrmWhatsappConversationTag> findByTag_IdTagAndDeletedAtIsNull(Long tagId);

    @Query("""
            SELECT ct.tag.idTag, COUNT(ct)
            FROM CrmWhatsappConversationTag ct
            WHERE ct.deletedAt IS NULL
              AND ct.tag.deletedAt IS NULL
              AND ct.tag.idTag IN :tagIds
            GROUP BY ct.tag.idTag
            """)
    List<Object[]> countActiveByTagIds(@Param("tagIds") List<Long> tagIds);
}
