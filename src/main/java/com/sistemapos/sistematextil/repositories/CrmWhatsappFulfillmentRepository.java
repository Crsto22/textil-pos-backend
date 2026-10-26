package com.sistemapos.sistematextil.repositories;

import java.util.Optional;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sistemapos.sistematextil.model.CrmWhatsappFulfillment;
import com.sistemapos.sistematextil.model.CrmWhatsappFulfillmentMethod;

import jakarta.persistence.LockModeType;

public interface CrmWhatsappFulfillmentRepository extends JpaRepository<CrmWhatsappFulfillment, Long> {
    interface SaleMethodProjection {
        Integer getIdVenta();
        CrmWhatsappFulfillmentMethod getMethod();
    }

    Optional<CrmWhatsappFulfillment> findByAgentSession_IdAgentSession(Long sessionId);

    Optional<CrmWhatsappFulfillment> findFirstByConversation_IdConversationAndVentaIsNotNullOrderByIdFulfillmentDesc(
            Long conversationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select f from CrmWhatsappFulfillment f
            where f.agentSession.idAgentSession = :sessionId
            """)
    Optional<CrmWhatsappFulfillment> findForUpdateBySessionId(@Param("sessionId") Long sessionId);

    Optional<CrmWhatsappFulfillment> findByVenta_IdVenta(Integer saleId);

    boolean existsByAgentSession_IdAgentSessionAndStatusAndVentaIsNotNull(
            Long sessionId, com.sistemapos.sistematextil.model.CrmWhatsappFulfillmentStatus status);

    List<CrmWhatsappFulfillment> findByVenta_IdVentaIn(Collection<Integer> saleIds);

    @Query("""
            select f.venta.idVenta as idVenta, f.method as method
            from CrmWhatsappFulfillment f
            where f.venta.idVenta in :saleIds
            """)
    List<SaleMethodProjection> findSaleMethodsBySaleIds(@Param("saleIds") Collection<Integer> saleIds);
}
