package com.sistemapos.sistematextil.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sistemapos.sistematextil.model.CrmWhatsappConversation;

import jakarta.persistence.LockModeType;

public interface CrmWhatsappConversationRepository extends JpaRepository<CrmWhatsappConversation, Long> {
    Optional<CrmWhatsappConversation> findFirstByPhoneOrderByIdConversationAsc(String phone);

    @Modifying
    @Query(value = """
            INSERT IGNORE INTO crm_whatsapp_conversation
                (phone, status, unread_count, created_at, updated_at)
            VALUES (:phone, 'ESPERA', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """, nativeQuery = true)
    int insertIfAbsent(@Param("phone") String phone);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CrmWhatsappConversation c WHERE c.phone = :phone")
    Optional<CrmWhatsappConversation> findForUpdateByPhone(@Param("phone") String phone);

    List<CrmWhatsappConversation> findByStatusOrderByLastMessageAtDesc(String status);
    List<CrmWhatsappConversation> findAllByOrderByLastMessageAtDesc();

    @Modifying
    @Query(value = "UPDATE crm_whatsapp_conversation SET id_connection = :idConnection WHERE id_connection IS NULL", nativeQuery = true)
    int assignUnlinkedConversations(@Param("idConnection") Long idConnection);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT c FROM CrmWhatsappConversation c
            WHERE c.connection.idConnection = :connectionId
              AND c.assignedUser IS NULL
              AND c.status <> 'RESUELTO'
              AND (c.attentionQueue = com.sistemapos.sistematextil.model.CrmWhatsappAttentionQueue.AI_ACTIVE
                   OR c.waitingReason = com.sistemapos.sistematextil.model.CrmWhatsappWaitingReason.AI_DISABLED)
            """)
    List<CrmWhatsappConversation> findAutomationQueueCandidatesForUpdate(@Param("connectionId") Long connectionId);

    @Modifying
    @Query(value = "DELETE FROM crm_whatsapp_conversation", nativeQuery = true)
    int deleteAllWhatsappConversations();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "DELETE FROM crm_whatsapp_conversation WHERE id_conversation = :conversationId", nativeQuery = true)
    int deleteWhatsappConversationById(@Param("conversationId") Long conversationId);

    @Query(value = """
            SELECT c
            FROM CrmWhatsappConversation c
            LEFT JOIN c.cliente cl
            WHERE (:status = 'ALL' OR c.status = :status)
              AND (:view = 'ALL'
                   OR (:view = 'ATTENDED'
                       AND c.status <> 'RESUELTO'
                       AND (c.assignedUser IS NOT NULL OR c.status = 'ATENDIDO'))
                   OR (:view = 'AI_ACTIVE'
                       AND c.status <> 'RESUELTO' AND c.status <> 'ATENDIDO'
                       AND c.assignedUser IS NULL
                       AND c.aiAttentionMode = com.sistemapos.sistematextil.model.CrmWhatsappAiAttentionMode.AUTOMATICA
                       AND c.waitingReason IS NULL)
                   OR (:view = 'RESOLVED'
                       AND c.status = 'RESUELTO'))
              AND (:waitingOnly = false OR (
                    c.status <> 'RESUELTO' AND c.status <> 'ATENDIDO'
                    AND c.assignedUser IS NULL
                    AND (c.waitingReason IN (
                          com.sistemapos.sistematextil.model.CrmWhatsappWaitingReason.ADVISOR_REQUIRED,
                          com.sistemapos.sistematextil.model.CrmWhatsappWaitingReason.AI_DISABLED,
                          com.sistemapos.sistematextil.model.CrmWhatsappWaitingReason.IMAGE_RECEIVED,
                          com.sistemapos.sistematextil.model.CrmWhatsappWaitingReason.PAYMENT_VERIFICATION)
                         OR c.aiAttentionMode = com.sistemapos.sistematextil.model.CrmWhatsappAiAttentionMode.HUMANA)))
              AND (:isAdmin = true
                   OR (c.assignedUser IS NOT NULL AND c.assignedUser.idUsuario = :idUsuario)
                   OR (c.status = 'ESPERA' AND c.assignedUser IS NULL))
              AND (:term IS NULL
                   OR LOWER(COALESCE(c.contactName, '')) LIKE LOWER(CONCAT('%', :term, '%'))
                   OR LOWER(COALESCE(c.whatsappUsername, '')) LIKE LOWER(CONCAT('%', :term, '%'))
                   OR COALESCE(c.phoneNumber, '') LIKE CONCAT('%', :term, '%')
                   OR COALESCE(c.phone, '') LIKE CONCAT('%', :term, '%')
                   OR LOWER(COALESCE(cl.nombres, '')) LIKE LOWER(CONCAT('%', :term, '%'))
                   OR COALESCE(cl.telefono, '') LIKE CONCAT('%', :term, '%'))
              AND (:tagId IS NULL OR EXISTS (
                    SELECT 1 FROM CrmWhatsappConversationTag ct
                    WHERE ct.conversation = c
                      AND ct.tag.idTag = :tagId
                      AND ct.deletedAt IS NULL
                      AND ct.tag.deletedAt IS NULL
              ))
            ORDER BY COALESCE(c.lastMessageAt, c.updatedAt, c.createdAt) DESC, c.idConversation DESC
            """,
            countQuery = """
            SELECT COUNT(c)
            FROM CrmWhatsappConversation c
            LEFT JOIN c.cliente cl
            WHERE (:status = 'ALL' OR c.status = :status)
              AND (:view = 'ALL'
                   OR (:view = 'ATTENDED'
                       AND c.status <> 'RESUELTO'
                       AND (c.assignedUser IS NOT NULL OR c.status = 'ATENDIDO'))
                   OR (:view = 'AI_ACTIVE'
                       AND c.status <> 'RESUELTO' AND c.status <> 'ATENDIDO'
                       AND c.assignedUser IS NULL
                       AND c.aiAttentionMode = com.sistemapos.sistematextil.model.CrmWhatsappAiAttentionMode.AUTOMATICA
                       AND c.waitingReason IS NULL)
                   OR (:view = 'RESOLVED'
                       AND c.status = 'RESUELTO'))
              AND (:waitingOnly = false OR (
                    c.status <> 'RESUELTO' AND c.status <> 'ATENDIDO'
                    AND c.assignedUser IS NULL
                    AND (c.waitingReason IN (
                          com.sistemapos.sistematextil.model.CrmWhatsappWaitingReason.ADVISOR_REQUIRED,
                          com.sistemapos.sistematextil.model.CrmWhatsappWaitingReason.AI_DISABLED,
                          com.sistemapos.sistematextil.model.CrmWhatsappWaitingReason.IMAGE_RECEIVED,
                          com.sistemapos.sistematextil.model.CrmWhatsappWaitingReason.PAYMENT_VERIFICATION)
                         OR c.aiAttentionMode = com.sistemapos.sistematextil.model.CrmWhatsappAiAttentionMode.HUMANA)))
              AND (:isAdmin = true
                   OR (c.assignedUser IS NOT NULL AND c.assignedUser.idUsuario = :idUsuario)
                   OR (c.status = 'ESPERA' AND c.assignedUser IS NULL))
              AND (:term IS NULL
                   OR LOWER(COALESCE(c.contactName, '')) LIKE LOWER(CONCAT('%', :term, '%'))
                   OR LOWER(COALESCE(c.whatsappUsername, '')) LIKE LOWER(CONCAT('%', :term, '%'))
                   OR COALESCE(c.phoneNumber, '') LIKE CONCAT('%', :term, '%')
                   OR COALESCE(c.phone, '') LIKE CONCAT('%', :term, '%')
                   OR LOWER(COALESCE(cl.nombres, '')) LIKE LOWER(CONCAT('%', :term, '%'))
                   OR COALESCE(cl.telefono, '') LIKE CONCAT('%', :term, '%'))
              AND (:tagId IS NULL OR EXISTS (
                    SELECT 1 FROM CrmWhatsappConversationTag ct
                    WHERE ct.conversation = c
                      AND ct.tag.idTag = :tagId
                      AND ct.deletedAt IS NULL
                      AND ct.tag.deletedAt IS NULL
              ))
            """)
    Page<CrmWhatsappConversation> findAccessibleConversations(
            @Param("idUsuario") Integer idUsuario,
            @Param("isAdmin") boolean isAdmin,
            @Param("status") String status,
            @Param("view") String view,
            @Param("waitingOnly") boolean waitingOnly,
            @Param("term") String term,
            @Param("tagId") Long tagId,
            Pageable pageable);

    @Query("""
            SELECT c.status, COUNT(c)
            FROM CrmWhatsappConversation c
            LEFT JOIN c.cliente cl
            WHERE (:isAdmin = true
                   OR (c.assignedUser IS NOT NULL AND c.assignedUser.idUsuario = :idUsuario)
                   OR (c.status = 'ESPERA' AND c.assignedUser IS NULL))
              AND (:term IS NULL
                   OR LOWER(COALESCE(c.contactName, '')) LIKE LOWER(CONCAT('%', :term, '%'))
                   OR LOWER(COALESCE(c.whatsappUsername, '')) LIKE LOWER(CONCAT('%', :term, '%'))
                   OR COALESCE(c.phoneNumber, '') LIKE CONCAT('%', :term, '%')
                   OR COALESCE(c.phone, '') LIKE CONCAT('%', :term, '%')
                   OR LOWER(COALESCE(cl.nombres, '')) LIKE LOWER(CONCAT('%', :term, '%'))
                   OR COALESCE(cl.telefono, '') LIKE CONCAT('%', :term, '%'))
              AND (:tagId IS NULL OR EXISTS (
                    SELECT 1 FROM CrmWhatsappConversationTag ct
                    WHERE ct.conversation = c
                      AND ct.tag.idTag = :tagId
                      AND ct.deletedAt IS NULL
                      AND ct.tag.deletedAt IS NULL
              ))
            GROUP BY c.status
            """)
    List<Object[]> countAccessibleConversationsByStatus(
            @Param("idUsuario") Integer idUsuario,
            @Param("isAdmin") boolean isAdmin,
            @Param("term") String term,
            @Param("tagId") Long tagId);

    @Query("""
            SELECT
              COALESCE(SUM(CASE WHEN c.status <> 'RESUELTO'
                                     AND (c.assignedUser IS NOT NULL OR c.status = 'ATENDIDO')
                                THEN 1 ELSE 0 END), 0),
              COALESCE(SUM(CASE WHEN c.status <> 'RESUELTO' AND c.status <> 'ATENDIDO'
                                     AND c.assignedUser IS NULL
                                     AND c.aiAttentionMode = com.sistemapos.sistematextil.model.CrmWhatsappAiAttentionMode.AUTOMATICA
                                     AND c.waitingReason IS NULL
                                THEN 1 ELSE 0 END), 0),
              COALESCE(SUM(CASE WHEN c.status <> 'RESUELTO' AND c.status <> 'ATENDIDO'
                                     AND c.assignedUser IS NULL
                                     AND (c.waitingReason IN (
                                          com.sistemapos.sistematextil.model.CrmWhatsappWaitingReason.ADVISOR_REQUIRED,
                                          com.sistemapos.sistematextil.model.CrmWhatsappWaitingReason.AI_DISABLED,
                                          com.sistemapos.sistematextil.model.CrmWhatsappWaitingReason.IMAGE_RECEIVED,
                                          com.sistemapos.sistematextil.model.CrmWhatsappWaitingReason.PAYMENT_VERIFICATION)
                                          OR c.aiAttentionMode = com.sistemapos.sistematextil.model.CrmWhatsappAiAttentionMode.HUMANA)
                                THEN 1 ELSE 0 END), 0),
              COALESCE(SUM(CASE WHEN c.status = 'RESUELTO' THEN 1 ELSE 0 END), 0)
            FROM CrmWhatsappConversation c
            LEFT JOIN c.cliente cl
            WHERE (:isAdmin = true
                   OR (c.assignedUser IS NOT NULL AND c.assignedUser.idUsuario = :idUsuario)
                   OR (c.status = 'ESPERA' AND c.assignedUser IS NULL))
              AND (:term IS NULL
                   OR LOWER(COALESCE(c.contactName, '')) LIKE LOWER(CONCAT('%', :term, '%'))
                   OR LOWER(COALESCE(c.whatsappUsername, '')) LIKE LOWER(CONCAT('%', :term, '%'))
                   OR COALESCE(c.phoneNumber, '') LIKE CONCAT('%', :term, '%')
                   OR COALESCE(c.phone, '') LIKE CONCAT('%', :term, '%')
                   OR LOWER(COALESCE(cl.nombres, '')) LIKE LOWER(CONCAT('%', :term, '%'))
                   OR COALESCE(cl.telefono, '') LIKE CONCAT('%', :term, '%'))
              AND (:tagId IS NULL OR EXISTS (
                    SELECT 1 FROM CrmWhatsappConversationTag ct
                    WHERE ct.conversation = c AND ct.tag.idTag = :tagId
                      AND ct.deletedAt IS NULL AND ct.tag.deletedAt IS NULL
              ))
            """)
    List<Object[]> countAccessibleConversationsByQueue(
            @Param("idUsuario") Integer idUsuario,
            @Param("isAdmin") boolean isAdmin,
            @Param("term") String term,
            @Param("tagId") Long tagId);

    @Query(value = """
            SELECT c
            FROM CrmWhatsappConversation c
            JOIN c.cliente cl
            WHERE cl.deletedAt IS NULL
              AND cl.empresa.idEmpresa = :idEmpresa
              AND (:isAdmin = true
                   OR (c.assignedUser IS NOT NULL AND c.assignedUser.idUsuario = :idUsuario)
                   OR (c.status = 'ESPERA' AND c.assignedUser IS NULL))
              AND (:term IS NULL
                   OR LOWER(COALESCE(cl.nombres, '')) LIKE LOWER(CONCAT('%', :term, '%'))
                   OR COALESCE(cl.telefono, '') LIKE CONCAT('%', :term, '%')
                   OR UPPER(COALESCE(cl.nroDocumento, '')) LIKE UPPER(CONCAT('%', :term, '%'))
                   OR LOWER(COALESCE(cl.correo, '')) LIKE LOWER(CONCAT('%', :term, '%')))
              AND (:filter = 'TODOS'
                   OR (:filter = 'CON_ETIQUETAS' AND EXISTS (
                        SELECT 1 FROM CrmWhatsappConversationTag ct
                        WHERE ct.conversation = c
                          AND ct.deletedAt IS NULL
                          AND ct.tag.deletedAt IS NULL
                   ))
                   OR (:filter = 'CON_COMPRAS' AND EXISTS (
                        SELECT 1 FROM Venta v
                        WHERE v.deletedAt IS NULL
                          AND v.estado = 'EMITIDA'
                          AND v.cliente = cl
                   ))
                   OR (:filter = 'SIN_COMPRAS' AND NOT EXISTS (
                        SELECT 1 FROM Venta v
                        WHERE v.deletedAt IS NULL
                          AND v.estado = 'EMITIDA'
                          AND v.cliente = cl
                   ))
                   OR (:filter = 'DATOS_INCOMPLETOS' AND (
                        cl.telefono IS NULL OR TRIM(cl.telefono) = ''
                        OR cl.nroDocumento IS NULL OR TRIM(cl.nroDocumento) = ''
                        OR cl.correo IS NULL OR TRIM(cl.correo) = ''
                        OR cl.direccion IS NULL OR TRIM(cl.direccion) = ''
                   )))
            ORDER BY COALESCE(c.lastMessageAt, c.updatedAt, c.createdAt) DESC, c.idConversation DESC
            """,
            countQuery = """
            SELECT COUNT(c)
            FROM CrmWhatsappConversation c
            JOIN c.cliente cl
            WHERE cl.deletedAt IS NULL
              AND cl.empresa.idEmpresa = :idEmpresa
              AND (:isAdmin = true
                   OR (c.assignedUser IS NOT NULL AND c.assignedUser.idUsuario = :idUsuario)
                   OR (c.status = 'ESPERA' AND c.assignedUser IS NULL))
              AND (:term IS NULL
                   OR LOWER(COALESCE(cl.nombres, '')) LIKE LOWER(CONCAT('%', :term, '%'))
                   OR COALESCE(cl.telefono, '') LIKE CONCAT('%', :term, '%')
                   OR UPPER(COALESCE(cl.nroDocumento, '')) LIKE UPPER(CONCAT('%', :term, '%'))
                   OR LOWER(COALESCE(cl.correo, '')) LIKE LOWER(CONCAT('%', :term, '%')))
              AND (:filter = 'TODOS'
                   OR (:filter = 'CON_ETIQUETAS' AND EXISTS (
                        SELECT 1 FROM CrmWhatsappConversationTag ct
                        WHERE ct.conversation = c
                          AND ct.deletedAt IS NULL
                          AND ct.tag.deletedAt IS NULL
                   ))
                   OR (:filter = 'CON_COMPRAS' AND EXISTS (
                        SELECT 1 FROM Venta v
                        WHERE v.deletedAt IS NULL
                          AND v.estado = 'EMITIDA'
                          AND v.cliente = cl
                   ))
                   OR (:filter = 'SIN_COMPRAS' AND NOT EXISTS (
                        SELECT 1 FROM Venta v
                        WHERE v.deletedAt IS NULL
                          AND v.estado = 'EMITIDA'
                          AND v.cliente = cl
                   ))
                   OR (:filter = 'DATOS_INCOMPLETOS' AND (
                        cl.telefono IS NULL OR TRIM(cl.telefono) = ''
                        OR cl.nroDocumento IS NULL OR TRIM(cl.nroDocumento) = ''
                        OR cl.correo IS NULL OR TRIM(cl.correo) = ''
                        OR cl.direccion IS NULL OR TRIM(cl.direccion) = ''
                   )))
            """)
    Page<CrmWhatsappConversation> findAccessibleContacts(
            @Param("idEmpresa") Integer idEmpresa,
            @Param("idUsuario") Integer idUsuario,
            @Param("isAdmin") boolean isAdmin,
            @Param("term") String term,
            @Param("filter") String filter,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CrmWhatsappConversation c WHERE c.idConversation = :id")
    Optional<CrmWhatsappConversation> findForUpdateById(@Param("id") Long id);

    @Modifying
    @Query("""
            UPDATE CrmWhatsappConversation c
            SET c.lastMessage = NULL,
                c.lastMessageType = NULL,
                c.lastMessageAt = NULL,
                c.unreadCount = 0
            """)
    int clearMessagePreviewData();

    @Query(value = """
            SELECT COUNT(*)
            FROM crm_whatsapp_conversation c
            WHERE (:isAdmin = TRUE OR c.assigned_user_id = :idUsuario OR (c.status = 'ESPERA' AND c.assigned_user_id IS NULL))
              AND c.created_at >= :desde
              AND c.created_at < :hasta
            """, nativeQuery = true)
    long countAccessibleCreatedInRange(
            @Param("idUsuario") Integer idUsuario,
            @Param("isAdmin") boolean isAdmin,
            @Param("desde") java.time.LocalDateTime desde,
            @Param("hasta") java.time.LocalDateTime hasta);

    @Query(value = """
            SELECT COUNT(*)
            FROM crm_whatsapp_conversation c
            WHERE (:isAdmin = TRUE OR c.assigned_user_id = :idUsuario OR (c.status = 'ESPERA' AND c.assigned_user_id IS NULL))
              AND c.status = :status
              AND c.created_at >= :desde
              AND c.created_at < :hasta
            """, nativeQuery = true)
    long countAccessibleByStatusCreatedInRange(
            @Param("idUsuario") Integer idUsuario,
            @Param("isAdmin") boolean isAdmin,
            @Param("status") String status,
            @Param("desde") java.time.LocalDateTime desde,
            @Param("hasta") java.time.LocalDateTime hasta);

    @Query(value = """
            SELECT COUNT(*)
            FROM crm_whatsapp_conversation c
            WHERE (:isAdmin = TRUE OR c.assigned_user_id = :idUsuario OR (c.status = 'ESPERA' AND c.assigned_user_id IS NULL))
              AND c.status = 'RESUELTO'
              AND c.updated_at >= :desde
              AND c.updated_at < :hasta
            """, nativeQuery = true)
    long countAccessibleResolvedInRange(
            @Param("idUsuario") Integer idUsuario,
            @Param("isAdmin") boolean isAdmin,
            @Param("desde") java.time.LocalDateTime desde,
            @Param("hasta") java.time.LocalDateTime hasta);

    @Query(value = """
            SELECT COUNT(DISTINCT c.id_cliente)
            FROM crm_whatsapp_conversation c
            WHERE c.id_cliente IS NOT NULL
              AND (:isAdmin = TRUE OR c.assigned_user_id = :idUsuario OR (c.status = 'ESPERA' AND c.assigned_user_id IS NULL))
              AND c.created_at >= :desde
              AND c.created_at < :hasta
            """, nativeQuery = true)
    long countAccessibleLinkedClientsInRange(
            @Param("idUsuario") Integer idUsuario,
            @Param("isAdmin") boolean isAdmin,
            @Param("desde") java.time.LocalDateTime desde,
            @Param("hasta") java.time.LocalDateTime hasta);

    @Query(value = """
            SELECT COUNT(DISTINCT c.id_cliente)
            FROM crm_whatsapp_conversation c
            INNER JOIN cliente cl ON cl.id_cliente = c.id_cliente
            WHERE c.id_cliente IS NOT NULL
              AND cl.deleted_at IS NULL
              AND (:isAdmin = TRUE OR c.assigned_user_id = :idUsuario OR (c.status = 'ESPERA' AND c.assigned_user_id IS NULL))
              AND cl.created_at >= :desde
              AND cl.created_at < :hasta
            """, nativeQuery = true)
    long countAccessibleNewLinkedClientsInRange(
            @Param("idUsuario") Integer idUsuario,
            @Param("isAdmin") boolean isAdmin,
            @Param("desde") java.time.LocalDateTime desde,
            @Param("hasta") java.time.LocalDateTime hasta);

    @Query(value = """
            SELECT c.status, COUNT(*)
            FROM crm_whatsapp_conversation c
            WHERE (:isAdmin = TRUE OR c.assigned_user_id = :idUsuario OR (c.status = 'ESPERA' AND c.assigned_user_id IS NULL))
              AND c.created_at >= :desde
              AND c.created_at < :hasta
            GROUP BY c.status
            ORDER BY COUNT(*) DESC
            """, nativeQuery = true)
    List<Object[]> countAccessibleByStatusDistribution(
            @Param("idUsuario") Integer idUsuario,
            @Param("isAdmin") boolean isAdmin,
            @Param("desde") java.time.LocalDateTime desde,
            @Param("hasta") java.time.LocalDateTime hasta);

    @Query(value = """
            SELECT HOUR(c.updated_at), COUNT(*)
            FROM crm_whatsapp_conversation c
            WHERE (:isAdmin = TRUE OR c.assigned_user_id = :idUsuario OR (c.status = 'ESPERA' AND c.assigned_user_id IS NULL))
              AND c.status = 'RESUELTO'
              AND c.updated_at >= :desde
              AND c.updated_at < :hasta
            GROUP BY HOUR(c.updated_at)
            ORDER BY HOUR(c.updated_at)
            """, nativeQuery = true)
    List<Object[]> countAccessibleResolvedByHour(
            @Param("idUsuario") Integer idUsuario,
            @Param("isAdmin") boolean isAdmin,
            @Param("desde") java.time.LocalDateTime desde,
            @Param("hasta") java.time.LocalDateTime hasta);

    @Query(value = """
            SELECT DATE(c.updated_at), COUNT(*)
            FROM crm_whatsapp_conversation c
            WHERE (:isAdmin = TRUE OR c.assigned_user_id = :idUsuario OR (c.status = 'ESPERA' AND c.assigned_user_id IS NULL))
              AND c.status = 'RESUELTO'
              AND c.updated_at >= :desde
              AND c.updated_at < :hasta
            GROUP BY DATE(c.updated_at)
            ORDER BY DATE(c.updated_at)
            """, nativeQuery = true)
    List<Object[]> countAccessibleResolvedByDate(
            @Param("idUsuario") Integer idUsuario,
            @Param("isAdmin") boolean isAdmin,
            @Param("desde") java.time.LocalDateTime desde,
            @Param("hasta") java.time.LocalDateTime hasta);

    @Query(value = """
            SELECT COALESCE(u.id_usuario, 0), COALESCE(CONCAT(u.nombre, ' ', u.apellido), 'Sin asignar'), COUNT(*)
            FROM crm_whatsapp_conversation c
            LEFT JOIN usuario u ON u.id_usuario = c.assigned_user_id
            WHERE c.assigned_user_id IS NOT NULL
              AND (:isAdmin = TRUE OR c.assigned_user_id = :idUsuario)
              AND c.assigned_at >= :desde
              AND c.assigned_at < :hasta
            GROUP BY COALESCE(u.id_usuario, 0), COALESCE(CONCAT(u.nombre, ' ', u.apellido), 'Sin asignar')
            ORDER BY COUNT(*) DESC
            LIMIT 8
            """, nativeQuery = true)
    List<Object[]> countAccessibleAssignedByUser(
            @Param("idUsuario") Integer idUsuario,
            @Param("isAdmin") boolean isAdmin,
            @Param("desde") java.time.LocalDateTime desde,
            @Param("hasta") java.time.LocalDateTime hasta);

    @Query(value = """
            SELECT COALESCE(u.id_usuario, 0), COALESCE(CONCAT(u.nombre, ' ', u.apellido), 'Sin asignar'), COUNT(*)
            FROM crm_whatsapp_conversation c
            LEFT JOIN usuario u ON u.id_usuario = c.assigned_user_id
            WHERE c.assigned_user_id IS NOT NULL
              AND c.status = 'RESUELTO'
              AND (:isAdmin = TRUE OR c.assigned_user_id = :idUsuario)
              AND c.updated_at >= :desde
              AND c.updated_at < :hasta
            GROUP BY COALESCE(u.id_usuario, 0), COALESCE(CONCAT(u.nombre, ' ', u.apellido), 'Sin asignar')
            ORDER BY COUNT(*) DESC
            LIMIT 8
            """, nativeQuery = true)
    List<Object[]> countAccessibleResolvedByUser(
            @Param("idUsuario") Integer idUsuario,
            @Param("isAdmin") boolean isAdmin,
            @Param("desde") java.time.LocalDateTime desde,
            @Param("hasta") java.time.LocalDateTime hasta);
}
