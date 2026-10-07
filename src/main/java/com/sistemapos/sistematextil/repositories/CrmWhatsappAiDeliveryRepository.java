package com.sistemapos.sistematextil.repositories;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.sistemapos.sistematextil.model.CrmWhatsappAiDelivery;
import com.sistemapos.sistematextil.model.CrmWhatsappAiDeliveryStatus;
import com.sistemapos.sistematextil.model.CrmWhatsappAiDeliveryType;

public interface CrmWhatsappAiDeliveryRepository extends JpaRepository<CrmWhatsappAiDelivery, Long> {
    Optional<CrmWhatsappAiDelivery> findByRun_IdAiRun(Long runId);
    Optional<CrmWhatsappAiDelivery> findByIdempotencyKey(String idempotencyKey);
    boolean existsByOutgoingMessage_IdMessage(Long messageId);
    Optional<CrmWhatsappAiDelivery> findFirstByConversation_IdConversationAndStatusOrderByCreatedAtAsc(
            Long conversationId, CrmWhatsappAiDeliveryStatus status);

    @EntityGraph(attributePaths = {
            "run", "run.job", "run.message", "conversation", "conversation.connection",
            "conversation.connection.empresa", "conversation.connection.sucursal",
            "conversation.connection.sucursal.empresa", "conversation.cliente",
            "conversation.cliente.empresa", "conversation.assignedUser"})
    @Query("SELECT d FROM CrmWhatsappAiDelivery d WHERE d.idAiDelivery = :id")
    Optional<CrmWhatsappAiDelivery> findDetailedById(@Param("id") Long id);

    @Query("""
            SELECT d.idAiDelivery FROM CrmWhatsappAiDelivery d
            WHERE d.status = com.sistemapos.sistematextil.model.CrmWhatsappAiDeliveryStatus.PENDING
              AND d.availableAt <= :now
            ORDER BY d.availableAt, d.idAiDelivery
            """)
    List<Long> findReadyIds(@Param("now") LocalDateTime now, Pageable pageable);

    @Modifying
    @Query("""
            UPDATE CrmWhatsappAiDelivery d SET d.status = :sending, d.lockedAt = :now, d.attempts = d.attempts + 1
            WHERE d.idAiDelivery = :id AND d.status = :pending
            """)
    int claim(@Param("id") Long id, @Param("pending") CrmWhatsappAiDeliveryStatus pending,
            @Param("sending") CrmWhatsappAiDeliveryStatus sending, @Param("now") LocalDateTime now);

    @Modifying
    @Query("""
            UPDATE CrmWhatsappAiDelivery d SET d.status = :cancelled, d.failureReason = :reason
            WHERE d.conversation.idConversation = :conversationId
              AND d.status IN :statuses
              AND d.deliveryType IN :types
            """)
    int cancelForConversation(@Param("conversationId") Long conversationId,
            @Param("statuses") List<CrmWhatsappAiDeliveryStatus> statuses,
            @Param("types") List<CrmWhatsappAiDeliveryType> types,
            @Param("cancelled") CrmWhatsappAiDeliveryStatus cancelled,
            @Param("reason") String reason);

    long countByConversation_IdConversationAndStatus(Long conversationId, CrmWhatsappAiDeliveryStatus status);

    @Query(value = """
            SELECT ignored.id_message
            FROM (
                SELECT d.prelude_outgoing_message_id AS id_message
                FROM crm_whatsapp_ai_delivery d
                WHERE d.id_conversation = :conversationId
                  AND d.prelude_outgoing_message_id IS NOT NULL
                UNION
                SELECT d.guide_outgoing_message_id AS id_message
                FROM crm_whatsapp_ai_delivery d
                WHERE d.id_conversation = :conversationId
                  AND d.guide_outgoing_message_id IS NOT NULL
                UNION
                SELECT d.id_outgoing_message AS id_message
                FROM crm_whatsapp_ai_delivery d
                WHERE d.id_conversation = :conversationId
                  AND d.id_outgoing_message IS NOT NULL
                  AND d.delivery_type IN (
                      'NEW_PRODUCT_ANNOUNCEMENT', 'CATALOG_PRODUCT_CARD',
                      'PRODUCT_PROMOTION_SUGGESTION', 'CART_PROMOTION_SUGGESTION',
                      'HANDOFF_NOTICE', 'PAYMENT_REGISTERED', 'PAYMENT_RETRY',
                      'PAYMENT_REJECTED', 'SALE_COMPLETED'
                  )
            ) ignored
            """, nativeQuery = true)
    List<Long> findMessageIdsExcludedFromAiContext(@Param("conversationId") Long conversationId);

    @Modifying
    @Query("""
            UPDATE CrmWhatsappAiDelivery d SET d.status = :cancelled, d.failureReason = :reason
            WHERE d.conversation.connection.idConnection = :connectionId AND d.status IN :statuses
              AND d.deliveryType IN :types
            """)
    int cancelForConnection(@Param("connectionId") Long connectionId,
            @Param("statuses") List<CrmWhatsappAiDeliveryStatus> statuses,
            @Param("types") List<CrmWhatsappAiDeliveryType> types,
            @Param("cancelled") CrmWhatsappAiDeliveryStatus cancelled,
            @Param("reason") String reason);
}
