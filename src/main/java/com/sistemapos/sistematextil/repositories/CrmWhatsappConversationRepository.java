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
    Optional<CrmWhatsappConversation> findByConnection_IdConnectionAndPhone(Long connectionId, String phone);
    List<CrmWhatsappConversation> findByConnection_IdConnection(Long connectionId);

    @Query(value = """
            SELECT c
            FROM CrmWhatsappConversation c
            LEFT JOIN c.cliente cl
            WHERE c.connection.area = com.sistemapos.sistematextil.model.CrmWhatsappArea.SHIPPING
              AND c.connection.empresa.idEmpresa = :companyId
              AND (:branchId IS NULL OR EXISTS (
                    SELECT 1 FROM CrmWarehouseOrder w
                    WHERE w.shippingConversation = c
                      AND w.venta.sucursal.idSucursal = :branchId
                      AND w.venta.deletedAt IS NULL
                      AND UPPER(COALESCE(w.venta.estado, '')) <> 'ANULADA'
              ))
              AND EXISTS (
                    SELECT 1 FROM CrmWhatsappMessage m
                    WHERE m.conversation = c
                      AND m.deletedAt IS NULL
                      AND m.direction IN ('INCOMING', 'OUTGOING')
              )
              AND (:term IS NULL
                   OR LOWER(COALESCE(c.contactName, '')) LIKE LOWER(CONCAT('%', :term, '%'))
                   OR LOWER(COALESCE(c.whatsappUsername, '')) LIKE LOWER(CONCAT('%', :term, '%'))
                   OR COALESCE(c.phoneNumber, '') LIKE CONCAT('%', :term, '%')
                   OR COALESCE(c.phone, '') LIKE CONCAT('%', :term, '%')
                   OR LOWER(COALESCE(cl.nombres, '')) LIKE LOWER(CONCAT('%', :term, '%'))
                   OR COALESCE(cl.telefono, '') LIKE CONCAT('%', :term, '%'))
            ORDER BY COALESCE(c.lastMessageAt, c.updatedAt, c.createdAt) DESC, c.idConversation DESC
            """,
            countQuery = """
            SELECT COUNT(c)
            FROM CrmWhatsappConversation c
            LEFT JOIN c.cliente cl
            WHERE c.connection.area = com.sistemapos.sistematextil.model.CrmWhatsappArea.SHIPPING
              AND c.connection.empresa.idEmpresa = :companyId
              AND (:branchId IS NULL OR EXISTS (
                    SELECT 1 FROM CrmWarehouseOrder w
                    WHERE w.shippingConversation = c
                      AND w.venta.sucursal.idSucursal = :branchId
                      AND w.venta.deletedAt IS NULL
                      AND UPPER(COALESCE(w.venta.estado, '')) <> 'ANULADA'
              ))
              AND EXISTS (
                    SELECT 1 FROM CrmWhatsappMessage m
                    WHERE m.conversation = c
                      AND m.deletedAt IS NULL
                      AND m.direction IN ('INCOMING', 'OUTGOING')
              )
              AND (:term IS NULL
                   OR LOWER(COALESCE(c.contactName, '')) LIKE LOWER(CONCAT('%', :term, '%'))
                   OR LOWER(COALESCE(c.whatsappUsername, '')) LIKE LOWER(CONCAT('%', :term, '%'))
                   OR COALESCE(c.phoneNumber, '') LIKE CONCAT('%', :term, '%')
                   OR COALESCE(c.phone, '') LIKE CONCAT('%', :term, '%')
                   OR LOWER(COALESCE(cl.nombres, '')) LIKE LOWER(CONCAT('%', :term, '%'))
                   OR COALESCE(cl.telefono, '') LIKE CONCAT('%', :term, '%'))
            """)
    Page<CrmWhatsappConversation> findShippingChatConversations(
            @Param("companyId") Integer companyId,
            @Param("branchId") Integer branchId,
            @Param("term") String term,
            Pageable pageable);

    @Query("""
            SELECT c
            FROM CrmWhatsappConversation c
            WHERE c.connection.area = com.sistemapos.sistematextil.model.CrmWhatsappArea.SALES
              AND c.connection.empresa.idEmpresa = :companyId
              AND ((:clientId IS NOT NULL AND c.cliente.idCliente = :clientId)
                   OR c.phone = :phone
                   OR (:phoneNumber <> '' AND (c.phoneNumber = :phoneNumber
                       OR c.whatsappUsername = :phoneNumber
                       OR c.phone = CONCAT(:phoneNumber, '@s.whatsapp.net'))))
            ORDER BY COALESCE(c.lastMessageAt, c.updatedAt, c.createdAt) DESC, c.idConversation DESC
            """)
    List<CrmWhatsappConversation> findSalesConversationForShippingContact(
            @Param("companyId") Integer companyId,
            @Param("clientId") Integer clientId,
            @Param("phone") String phone,
            @Param("phoneNumber") String phoneNumber,
            Pageable pageable);

    @Modifying
    @Query(value = """
            INSERT IGNORE INTO crm_whatsapp_conversation
                (phone, id_connection, status, unread_count, phone_number_version, created_at, updated_at)
            SELECT :phone, :connectionId, 'ESPERA', 0, COALESCE(phone_number_version, 0),
                   CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
            FROM crm_whatsapp_connection
            WHERE id_connection = :connectionId
            """, nativeQuery = true)
    int insertIfAbsent(@Param("phone") String phone, @Param("connectionId") Long connectionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CrmWhatsappConversation c WHERE c.connection.idConnection = :connectionId AND c.phone = :phone")
    Optional<CrmWhatsappConversation> findForUpdateByConnectionAndPhone(
            @Param("connectionId") Long connectionId, @Param("phone") String phone);

    List<CrmWhatsappConversation> findByStatusOrderByLastMessageAtDesc(String status);
    List<CrmWhatsappConversation> findAllByOrderByLastMessageAtDesc();

    @Modifying
    @Query(value = "UPDATE crm_whatsapp_conversation SET id_connection = :idConnection WHERE id_connection IS NULL", nativeQuery = true)
    int assignUnlinkedConversations(@Param("idConnection") Long idConnection);

    @Modifying
    @Query(value = "DELETE FROM crm_whatsapp_conversation", nativeQuery = true)
    int deleteAllWhatsappConversations();

    @Modifying
    @Query(value = """
            DELETE c FROM crm_whatsapp_conversation c
            LEFT JOIN crm_whatsapp_connection cn ON cn.id_connection = c.id_connection
            WHERE COALESCE(cn.area, 'SALES') = :area
            """, nativeQuery = true)
    int deleteWhatsappConversationsByArea(@Param("area") String area);

    @Modifying
    @Query(value = """
            UPDATE crm_warehouse_order w
            LEFT JOIN crm_whatsapp_conversation sales_c ON sales_c.id_conversation = w.id_sales_conversation
            LEFT JOIN crm_whatsapp_connection sales_cn ON sales_cn.id_connection = sales_c.id_connection
            LEFT JOIN crm_whatsapp_conversation shipping_c ON shipping_c.id_conversation = w.id_shipping_conversation
            LEFT JOIN crm_whatsapp_connection shipping_cn ON shipping_cn.id_connection = shipping_c.id_connection
            SET w.id_sales_conversation = CASE WHEN COALESCE(sales_cn.area, 'SALES') = :area THEN NULL ELSE w.id_sales_conversation END,
                w.id_shipping_conversation = CASE WHEN COALESCE(shipping_cn.area, 'SALES') = :area THEN NULL ELSE w.id_shipping_conversation END
            WHERE COALESCE(sales_cn.area, 'SALES') = :area OR COALESCE(shipping_cn.area, 'SALES') = :area
            """, nativeQuery = true)
    int clearWarehouseConversationReferencesByArea(@Param("area") String area);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "DELETE FROM crm_whatsapp_conversation WHERE id_conversation = :conversationId", nativeQuery = true)
    int deleteWhatsappConversationById(@Param("conversationId") Long conversationId);

    @Query(value = """
            SELECT c
            FROM CrmWhatsappConversation c
            LEFT JOIN c.cliente cl
            WHERE (c.connection IS NULL OR c.connection.area = com.sistemapos.sistematextil.model.CrmWhatsappArea.SALES)
              AND (c.phone NOT LIKE '%@lid'
                   OR COALESCE(TRIM(c.phoneNumber), '') <> ''
                   OR COALESCE(TRIM(c.whatsappUsername), '') <> ''
                   OR COALESCE(TRIM(c.contactName), '') <> ''
                   OR c.cliente IS NOT NULL
                   OR EXISTS (
                        SELECT 1 FROM CrmWhatsappMessage ghostMessage
                        WHERE ghostMessage.conversation = c AND ghostMessage.direction = 'INCOMING'
                   ))
              AND (:status = 'ALL' OR c.status = :status)
              AND (:view = 'ALL'
                   OR (:view = 'MINE'
                       AND c.status <> 'RESUELTO'
                       AND c.assignedUser IS NOT NULL
                       AND c.assignedUser.idUsuario = :idUsuario)
                   OR (:view = 'ATTENDED'
                       AND c.status <> 'RESUELTO'
                       AND c.assignedUser IS NOT NULL)
                   OR (:view = 'AGENT'
                       AND c.status <> 'RESUELTO'
                       AND c.assignedUser IS NULL
                       AND c.attentionQueue = com.sistemapos.sistematextil.model.CrmWhatsappAttentionQueue.AGENT_ACTIVE)
                   OR (:view = 'RESOLVED'
                       AND c.status = 'RESUELTO'))
              AND (:waitingOnly = false OR (
                    c.status <> 'RESUELTO'
                    AND c.assignedUser IS NULL
                    AND (c.attentionQueue IS NULL
                         OR c.attentionQueue <> com.sistemapos.sistematextil.model.CrmWhatsappAttentionQueue.AGENT_ACTIVE)))
              AND (:isAdmin = true
                   OR (c.assignedUser IS NOT NULL AND c.assignedUser.idUsuario = :idUsuario)
                   OR (c.status <> 'RESUELTO' AND c.assignedUser IS NULL))
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
            WHERE (c.connection IS NULL OR c.connection.area = com.sistemapos.sistematextil.model.CrmWhatsappArea.SALES)
              AND (c.phone NOT LIKE '%@lid'
                   OR COALESCE(TRIM(c.phoneNumber), '') <> ''
                   OR COALESCE(TRIM(c.whatsappUsername), '') <> ''
                   OR COALESCE(TRIM(c.contactName), '') <> ''
                   OR c.cliente IS NOT NULL
                   OR EXISTS (
                        SELECT 1 FROM CrmWhatsappMessage ghostMessage
                        WHERE ghostMessage.conversation = c AND ghostMessage.direction = 'INCOMING'
                   ))
              AND (:status = 'ALL' OR c.status = :status)
              AND (:view = 'ALL'
                   OR (:view = 'MINE'
                       AND c.status <> 'RESUELTO'
                       AND c.assignedUser IS NOT NULL
                       AND c.assignedUser.idUsuario = :idUsuario)
                   OR (:view = 'ATTENDED'
                       AND c.status <> 'RESUELTO'
                       AND c.assignedUser IS NOT NULL)
                   OR (:view = 'AGENT'
                       AND c.status <> 'RESUELTO'
                       AND c.assignedUser IS NULL
                       AND c.attentionQueue = com.sistemapos.sistematextil.model.CrmWhatsappAttentionQueue.AGENT_ACTIVE)
                   OR (:view = 'RESOLVED'
                       AND c.status = 'RESUELTO'))
              AND (:waitingOnly = false OR (
                    c.status <> 'RESUELTO'
                    AND c.assignedUser IS NULL
                    AND (c.attentionQueue IS NULL
                         OR c.attentionQueue <> com.sistemapos.sistematextil.model.CrmWhatsappAttentionQueue.AGENT_ACTIVE)))
              AND (:isAdmin = true
                   OR (c.assignedUser IS NOT NULL AND c.assignedUser.idUsuario = :idUsuario)
                   OR (c.status <> 'RESUELTO' AND c.assignedUser IS NULL))
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
            WHERE (c.connection IS NULL OR c.connection.area = com.sistemapos.sistematextil.model.CrmWhatsappArea.SALES)
              AND (c.phone NOT LIKE '%@lid'
                   OR COALESCE(TRIM(c.phoneNumber), '') <> ''
                   OR COALESCE(TRIM(c.whatsappUsername), '') <> ''
                   OR COALESCE(TRIM(c.contactName), '') <> ''
                   OR c.cliente IS NOT NULL
                   OR EXISTS (
                        SELECT 1 FROM CrmWhatsappMessage ghostMessage
                        WHERE ghostMessage.conversation = c AND ghostMessage.direction = 'INCOMING'
                   ))
              AND (:isAdmin = true
                   OR (c.assignedUser IS NOT NULL AND c.assignedUser.idUsuario = :idUsuario)
                   OR (c.status <> 'RESUELTO' AND c.assignedUser IS NULL))
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
                                     AND c.assignedUser IS NOT NULL
                                THEN 1 ELSE 0 END), 0),
              COALESCE(SUM(CASE WHEN c.status <> 'RESUELTO'
                                     AND c.assignedUser IS NULL
                                     AND c.attentionQueue = com.sistemapos.sistematextil.model.CrmWhatsappAttentionQueue.AGENT_ACTIVE
                                THEN 1 ELSE 0 END), 0),
              COALESCE(SUM(CASE WHEN c.status <> 'RESUELTO'
                                     AND c.assignedUser IS NULL
                                     AND (c.attentionQueue IS NULL
                                          OR c.attentionQueue <> com.sistemapos.sistematextil.model.CrmWhatsappAttentionQueue.AGENT_ACTIVE)
                                THEN 1 ELSE 0 END), 0),
              COALESCE(SUM(CASE WHEN c.status = 'RESUELTO' THEN 1 ELSE 0 END), 0),
              COALESCE(SUM(CASE WHEN c.status <> 'RESUELTO'
                                     AND c.assignedUser IS NOT NULL
                                     AND c.assignedUser.idUsuario = :idUsuario
                                THEN 1 ELSE 0 END), 0)
            FROM CrmWhatsappConversation c
            LEFT JOIN c.cliente cl
            WHERE (c.connection IS NULL OR c.connection.area = com.sistemapos.sistematextil.model.CrmWhatsappArea.SALES)
              AND (c.phone NOT LIKE '%@lid'
                   OR COALESCE(TRIM(c.phoneNumber), '') <> ''
                   OR COALESCE(TRIM(c.whatsappUsername), '') <> ''
                   OR COALESCE(TRIM(c.contactName), '') <> ''
                   OR c.cliente IS NOT NULL
                   OR EXISTS (
                        SELECT 1 FROM CrmWhatsappMessage ghostMessage
                        WHERE ghostMessage.conversation = c AND ghostMessage.direction = 'INCOMING'
                   ))
              AND (:isAdmin = true
                   OR (c.assignedUser IS NOT NULL AND c.assignedUser.idUsuario = :idUsuario)
                   OR (c.status <> 'RESUELTO' AND c.assignedUser IS NULL))
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
              AND (c.connection IS NULL OR c.connection.area = com.sistemapos.sistematextil.model.CrmWhatsappArea.SALES)
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
              AND (c.connection IS NULL OR c.connection.area = com.sistemapos.sistematextil.model.CrmWhatsappArea.SALES)
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
