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

    @Modifying
    @Query(value = """
            DELETE ct FROM crm_whatsapp_conversation_tag ct
            JOIN crm_whatsapp_conversation c ON c.id_conversation = ct.id_conversation
            LEFT JOIN crm_whatsapp_connection cn ON cn.id_connection = c.id_connection
            WHERE COALESCE(cn.area, 'SALES') = :area
            """, nativeQuery = true)
    int deleteConversationTagsByArea(@Param("area") String area);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "DELETE FROM crm_whatsapp_conversation_tag WHERE id_conversation = :conversationId", nativeQuery = true)
    int deleteByConversationId(@Param("conversationId") Long conversationId);

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
