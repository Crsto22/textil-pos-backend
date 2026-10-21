package com.sistemapos.sistematextil.repositories;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

import com.sistemapos.sistematextil.model.CrmWarehouseOrder;
import com.sistemapos.sistematextil.model.CrmWarehouseStatus;

public interface CrmWarehouseOrderRepository extends JpaRepository<CrmWarehouseOrder, Long> {
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE crm_warehouse_order SET id_sales_conversation = NULL WHERE id_sales_conversation IS NOT NULL", nativeQuery = true)
    int clearSalesConversations();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE crm_warehouse_order SET id_shipping_conversation = NULL WHERE id_shipping_conversation IS NOT NULL", nativeQuery = true)
    int clearShippingConversations();

    Optional<CrmWarehouseOrder> findByVenta_IdVenta(Integer saleId);

    @Query("""
            SELECT w
            FROM CrmWarehouseOrder w
            JOIN w.venta v
            JOIN v.sucursal s
            WHERE s.empresa.idEmpresa = :companyId
              AND (:branchId IS NULL OR s.idSucursal = :branchId)
              AND v.deletedAt IS NULL
              AND UPPER(COALESCE(v.estado, '')) <> 'ANULADA'
              AND (:status IS NULL OR w.status = :status)
            ORDER BY
              CASE WHEN :ascending = true THEN v.fecha END ASC,
              CASE WHEN :ascending = true THEN v.idVenta END ASC,
              CASE WHEN :ascending = false THEN v.fecha END DESC,
              CASE WHEN :ascending = false THEN v.idVenta END DESC
            """)
    Page<CrmWarehouseOrder> searchWithoutTerm(
            @Param("companyId") Integer companyId,
            @Param("branchId") Integer branchId,
            @Param("status") CrmWarehouseStatus status,
            @Param("ascending") boolean ascending,
            Pageable pageable);

    @Query(value = """
            SELECT w.*
            FROM crm_warehouse_order w
            JOIN venta v ON v.id_venta = w.id_venta
            JOIN sucursal s ON s.id_sucursal = v.id_sucursal
            LEFT JOIN cliente c ON c.id_cliente = v.id_cliente
            WHERE s.id_empresa = :companyId
              AND (:branchId IS NULL OR s.id_sucursal = :branchId)
              AND v.deleted_at IS NULL
              AND UPPER(COALESCE(v.estado, '')) <> 'ANULADA'
              AND (:ascending = :ascending)
              AND (:status IS NULL OR w.status = :status)
              AND (
                    LOWER(CONVERT(COALESCE(c.nombres, '') USING utf8mb4)) COLLATE utf8mb4_0900_ai_ci
                        LIKE LOWER(CONCAT('%', :term, '%')) COLLATE utf8mb4_0900_ai_ci
                 OR CONVERT(COALESCE(c.telefono, '') USING utf8mb4) COLLATE utf8mb4_0900_ai_ci
                        LIKE CONCAT('%', :term, '%') COLLATE utf8mb4_0900_ai_ci
                 OR LOWER(CONVERT(COALESCE(v.tipo_comprobante, '') USING utf8mb4)) COLLATE utf8mb4_0900_ai_ci
                        LIKE LOWER(CONCAT('%', :term, '%')) COLLATE utf8mb4_0900_ai_ci
                 OR LOWER(CONVERT(CONCAT(COALESCE(v.serie, ''), '-', COALESCE(CAST(v.correlativo AS CHAR), '')) USING utf8mb4)) COLLATE utf8mb4_0900_ai_ci
                        LIKE LOWER(CONCAT('%', :term, '%')) COLLATE utf8mb4_0900_ai_ci
              )
            ORDER BY
              CASE WHEN :ascending = TRUE THEN v.fecha END ASC,
              CASE WHEN :ascending = TRUE THEN v.id_venta END ASC,
              CASE WHEN :ascending = FALSE THEN v.fecha END DESC,
              CASE WHEN :ascending = FALSE THEN v.id_venta END DESC
            """,
            countQuery = """
            SELECT COUNT(*)
            FROM crm_warehouse_order w
            JOIN venta v ON v.id_venta = w.id_venta
            JOIN sucursal s ON s.id_sucursal = v.id_sucursal
            LEFT JOIN cliente c ON c.id_cliente = v.id_cliente
            WHERE s.id_empresa = :companyId
              AND (:branchId IS NULL OR s.id_sucursal = :branchId)
              AND v.deleted_at IS NULL
              AND UPPER(COALESCE(v.estado, '')) <> 'ANULADA'
              AND (:ascending = :ascending)
              AND (:status IS NULL OR w.status = :status)
              AND (
                    LOWER(CONVERT(COALESCE(c.nombres, '') USING utf8mb4)) COLLATE utf8mb4_0900_ai_ci
                        LIKE LOWER(CONCAT('%', :term, '%')) COLLATE utf8mb4_0900_ai_ci
                 OR CONVERT(COALESCE(c.telefono, '') USING utf8mb4) COLLATE utf8mb4_0900_ai_ci
                        LIKE CONCAT('%', :term, '%') COLLATE utf8mb4_0900_ai_ci
                 OR LOWER(CONVERT(COALESCE(v.tipo_comprobante, '') USING utf8mb4)) COLLATE utf8mb4_0900_ai_ci
                        LIKE LOWER(CONCAT('%', :term, '%')) COLLATE utf8mb4_0900_ai_ci
                 OR LOWER(CONVERT(CONCAT(COALESCE(v.serie, ''), '-', COALESCE(CAST(v.correlativo AS CHAR), '')) USING utf8mb4)) COLLATE utf8mb4_0900_ai_ci
                        LIKE LOWER(CONCAT('%', :term, '%')) COLLATE utf8mb4_0900_ai_ci
              )
            """, nativeQuery = true)
    Page<CrmWarehouseOrder> searchWithTerm(
            @Param("companyId") Integer companyId,
            @Param("branchId") Integer branchId,
            @Param("status") String status,
            @Param("term") String term,
            @Param("ascending") boolean ascending,
            Pageable pageable);

    @Query("""
            SELECT CASE WHEN COUNT(w) > 0 THEN true ELSE false END
            FROM CrmWarehouseOrder w
            LEFT JOIN w.salesConversation salesChat
            LEFT JOIN w.shippingConversation shippingChat
            WHERE w.venta.sucursal.empresa.idEmpresa = :companyId
              AND (:branchId IS NULL OR w.venta.sucursal.idSucursal = :branchId)
              AND w.venta.deletedAt IS NULL
              AND UPPER(COALESCE(w.venta.estado, '')) <> 'ANULADA'
              AND (salesChat.idConversation = :conversationId
                   OR shippingChat.idConversation = :conversationId)
            """)
    boolean canAccessConversation(@Param("companyId") Integer companyId,
            @Param("branchId") Integer branchId,
            @Param("conversationId") Long conversationId);
}
