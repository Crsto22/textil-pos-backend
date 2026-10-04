package com.sistemapos.sistematextil.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

import com.sistemapos.sistematextil.model.CrmWhatsappMessage;

import jakarta.persistence.LockModeType;

public interface CrmWhatsappMessageRepository extends JpaRepository<CrmWhatsappMessage, Long> {
    Optional<CrmWhatsappMessage> findFirstByConversation_IdConversationAndRelatedSaleIdAndReceiptFormatAndDeletedAtIsNull(
            Long conversationId, Integer relatedSaleId, String receiptFormat);
    List<CrmWhatsappMessage> findByConversation_IdConversationOrderByCreatedAtAsc(Long idConversation);
    Optional<CrmWhatsappMessage> findFirstByWhatsappMessageId(String whatsappMessageId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM CrmWhatsappMessage m WHERE m.idMessage = :id")
    Optional<CrmWhatsappMessage> findForUpdate(@Param("id") Long id);

    @Query("""
            SELECT m
            FROM CrmWhatsappMessage m
            WHERE m.conversation.idConversation = :conversationId
              AND m.deletedAt IS NULL
            ORDER BY m.idMessage DESC
            """)
    List<CrmWhatsappMessage> findRecentActiveMessages(
            @Param("conversationId") Long conversationId,
            Pageable pageable);

    @Query("""
            SELECT m
            FROM CrmWhatsappMessage m
            WHERE m.conversation.idConversation = :conversationId
              AND m.direction = 'INCOMING'
              AND m.deletedAt IS NULL
            ORDER BY m.idMessage DESC
            """)
    List<CrmWhatsappMessage> findRecentIncomingMessages(
            @Param("conversationId") Long conversationId,
            Pageable pageable);

    @Query(value = """
            SELECT DISTINCT m.id_message
            FROM crm_whatsapp_message m
            INNER JOIN crm_whatsapp_payment_request r
              ON r.id_conversation = m.id_conversation
             AND r.status IN ('PENDING_EVIDENCE', 'UNDER_REVIEW')
             AND m.created_at >= r.created_at
            LEFT JOIN crm_whatsapp_payment_evidence e ON e.id_message = m.id_message
            WHERE m.direction = 'INCOMING'
              AND m.deleted_at IS NULL
              AND m.media_storage_path IS NOT NULL
              AND m.media_storage_path <> ''
              AND e.id_payment_evidence IS NULL
            ORDER BY m.id_message
            """, nativeQuery = true)
    List<Long> findUnregisteredPaymentMedia(Pageable pageable);

    @Query("""
            SELECT m
            FROM CrmWhatsappMessage m
            WHERE m.conversation.idConversation = :conversationId
              AND (:beforeId IS NULL OR m.idMessage < :beforeId)
            ORDER BY m.idMessage DESC
            """)
    List<CrmWhatsappMessage> findMessageHistory(
            @Param("conversationId") Long conversationId,
            @Param("beforeId") Long beforeId,
            Pageable pageable);

    @Query("""
            SELECT m
            FROM CrmWhatsappMessage m
            WHERE m.conversation.idConversation = :conversationId
              AND m.idMessage > :afterId
            ORDER BY m.idMessage ASC
            """)
    List<CrmWhatsappMessage> findMessagesAfter(
            @Param("conversationId") Long conversationId,
            @Param("afterId") Long afterId,
            Pageable pageable);

    @Query(value = """
            SELECT *
            FROM crm_whatsapp_message
            WHERE id_conversation = :idConversation
            ORDER BY created_at DESC, id_message DESC
            LIMIT 1
            """, nativeQuery = true)
    Optional<CrmWhatsappMessage> findLatestByConversationId(@Param("idConversation") Long idConversation);

    @Query("""
            SELECT CASE WHEN COUNT(m) > 0 THEN true ELSE false END
            FROM CrmWhatsappMessage m
            WHERE m.conversation.idConversation = :conversationId
              AND m.idMessage > :messageId
              AND m.deletedAt IS NULL
              AND m.direction IN ('INCOMING', 'OUTGOING')
            """)
    boolean existsActiveChatMessageAfter(
            @Param("conversationId") Long conversationId,
            @Param("messageId") Long messageId);

    @Query("""
            SELECT m.mediaStoragePath
            FROM CrmWhatsappMessage m
            WHERE m.mediaStoragePath IS NOT NULL AND m.mediaStoragePath <> ''
            """)
    List<String> findAllMediaStoragePaths();

    @Modifying
    @Query(value = "DELETE FROM crm_whatsapp_message", nativeQuery = true)
    int deleteAllWhatsappMessages();

    @Query(value = """
            SELECT COUNT(*)
            FROM crm_whatsapp_message m
            INNER JOIN crm_whatsapp_conversation c ON c.id_conversation = m.id_conversation
            WHERE (:isAdmin = TRUE OR c.assigned_user_id = :idUsuario OR (c.status = 'ESPERA' AND c.assigned_user_id IS NULL))
              AND m.direction = :direction
              AND m.deleted_at IS NULL
              AND m.created_at >= :desde
              AND m.created_at < :hasta
            """, nativeQuery = true)
    long countAccessibleByDirectionInRange(
            @Param("idUsuario") Integer idUsuario,
            @Param("isAdmin") boolean isAdmin,
            @Param("direction") String direction,
            @Param("desde") java.time.LocalDateTime desde,
            @Param("hasta") java.time.LocalDateTime hasta);

    @Query(value = """
            SELECT HOUR(m.created_at), COUNT(*)
            FROM crm_whatsapp_message m
            INNER JOIN crm_whatsapp_conversation c ON c.id_conversation = m.id_conversation
            WHERE (:isAdmin = TRUE OR c.assigned_user_id = :idUsuario OR (c.status = 'ESPERA' AND c.assigned_user_id IS NULL))
              AND m.deleted_at IS NULL
              AND m.created_at >= :desde
              AND m.created_at < :hasta
            GROUP BY HOUR(m.created_at)
            ORDER BY HOUR(m.created_at)
            """, nativeQuery = true)
    List<Object[]> countAccessibleMessagesByHour(
            @Param("idUsuario") Integer idUsuario,
            @Param("isAdmin") boolean isAdmin,
            @Param("desde") java.time.LocalDateTime desde,
            @Param("hasta") java.time.LocalDateTime hasta);

    @Query(value = """
            SELECT DATE(m.created_at), COUNT(*)
            FROM crm_whatsapp_message m
            INNER JOIN crm_whatsapp_conversation c ON c.id_conversation = m.id_conversation
            WHERE (:isAdmin = TRUE OR c.assigned_user_id = :idUsuario OR (c.status = 'ESPERA' AND c.assigned_user_id IS NULL))
              AND m.deleted_at IS NULL
              AND m.created_at >= :desde
              AND m.created_at < :hasta
            GROUP BY DATE(m.created_at)
            ORDER BY DATE(m.created_at)
            """, nativeQuery = true)
    List<Object[]> countAccessibleMessagesByDate(
            @Param("idUsuario") Integer idUsuario,
            @Param("isAdmin") boolean isAdmin,
            @Param("desde") java.time.LocalDateTime desde,
            @Param("hasta") java.time.LocalDateTime hasta);

    @Query(value = """
            SELECT m.message_type, COUNT(*)
            FROM crm_whatsapp_message m
            INNER JOIN crm_whatsapp_conversation c ON c.id_conversation = m.id_conversation
            WHERE (:isAdmin = TRUE OR c.assigned_user_id = :idUsuario OR (c.status = 'ESPERA' AND c.assigned_user_id IS NULL))
              AND m.deleted_at IS NULL
              AND m.created_at >= :desde
              AND m.created_at < :hasta
            GROUP BY m.message_type
            ORDER BY COUNT(*) DESC
            """, nativeQuery = true)
    List<Object[]> countAccessibleMessagesByType(
            @Param("idUsuario") Integer idUsuario,
            @Param("isAdmin") boolean isAdmin,
            @Param("desde") java.time.LocalDateTime desde,
            @Param("hasta") java.time.LocalDateTime hasta);

    @Query(value = """
            SELECT m.direction, COUNT(*)
            FROM crm_whatsapp_message m
            INNER JOIN crm_whatsapp_conversation c ON c.id_conversation = m.id_conversation
            WHERE (:isAdmin = TRUE OR c.assigned_user_id = :idUsuario OR (c.status = 'ESPERA' AND c.assigned_user_id IS NULL))
              AND m.deleted_at IS NULL
              AND m.created_at >= :desde
              AND m.created_at < :hasta
            GROUP BY m.direction
            ORDER BY COUNT(*) DESC
            """, nativeQuery = true)
    List<Object[]> countAccessibleMessagesByDirection(
            @Param("idUsuario") Integer idUsuario,
            @Param("isAdmin") boolean isAdmin,
            @Param("desde") java.time.LocalDateTime desde,
            @Param("hasta") java.time.LocalDateTime hasta);

    @Query(value = """
            SELECT HOUR(cl.created_at), COUNT(DISTINCT cl.id_cliente)
            FROM crm_whatsapp_conversation c
            INNER JOIN cliente cl ON cl.id_cliente = c.id_cliente
            WHERE c.id_cliente IS NOT NULL
              AND cl.deleted_at IS NULL
              AND (:isAdmin = TRUE OR c.assigned_user_id = :idUsuario OR (c.status = 'ESPERA' AND c.assigned_user_id IS NULL))
              AND cl.created_at >= :desde
              AND cl.created_at < :hasta
            GROUP BY HOUR(cl.created_at)
            ORDER BY HOUR(cl.created_at)
            """, nativeQuery = true)
    List<Object[]> countAccessibleNewLinkedClientsByHour(
            @Param("idUsuario") Integer idUsuario,
            @Param("isAdmin") boolean isAdmin,
            @Param("desde") java.time.LocalDateTime desde,
            @Param("hasta") java.time.LocalDateTime hasta);

    @Query(value = """
            SELECT DATE(cl.created_at), COUNT(DISTINCT cl.id_cliente)
            FROM crm_whatsapp_conversation c
            INNER JOIN cliente cl ON cl.id_cliente = c.id_cliente
            WHERE c.id_cliente IS NOT NULL
              AND cl.deleted_at IS NULL
              AND (:isAdmin = TRUE OR c.assigned_user_id = :idUsuario OR (c.status = 'ESPERA' AND c.assigned_user_id IS NULL))
              AND cl.created_at >= :desde
              AND cl.created_at < :hasta
            GROUP BY DATE(cl.created_at)
            ORDER BY DATE(cl.created_at)
            """, nativeQuery = true)
    List<Object[]> countAccessibleNewLinkedClientsByDate(
            @Param("idUsuario") Integer idUsuario,
            @Param("isAdmin") boolean isAdmin,
            @Param("desde") java.time.LocalDateTime desde,
            @Param("hasta") java.time.LocalDateTime hasta);
}
