package com.sistemapos.sistematextil.repositories;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sistemapos.sistematextil.model.CrmWhatsappFilmAddon;
import com.sistemapos.sistematextil.model.CrmWhatsappFilmStatus;
import jakarta.persistence.LockModeType;

public interface CrmWhatsappFilmAddonRepository extends JpaRepository<CrmWhatsappFilmAddon, Long> {
    Optional<CrmWhatsappFilmAddon> findByAgentSession_IdAgentSession(Long sessionId);
    Optional<CrmWhatsappFilmAddon> findByVenta_IdVenta(Integer saleId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from CrmWhatsappFilmAddon f where f.idFilmAddon=:id")
    Optional<CrmWhatsappFilmAddon> findForUpdate(@Param("id") Long id);
    @Query("select f.idFilmAddon from CrmWhatsappFilmAddon f where f.status=:status and f.expiresAt<=:now order by f.expiresAt")
    List<Long> findExpiredIds(@Param("status") CrmWhatsappFilmStatus status,
            @Param("now") LocalDateTime now, Pageable pageable);
    @Query("select f from CrmWhatsappFilmAddon f where f.venta.idVenta=:saleId and f.status in :statuses")
    Optional<CrmWhatsappFilmAddon> findPendingForSale(@Param("saleId") Integer saleId,
            @Param("statuses") Collection<CrmWhatsappFilmStatus> statuses);
    @Query("select f.idFilmAddon from CrmWhatsappFilmAddon f where f.status in :statuses and (f.venta is null or f.finalMessageSentAt is null) order by f.updatedAt")
    List<Long> findPendingFinalizationIds(@Param("statuses") Collection<CrmWhatsappFilmStatus> statuses,
            Pageable pageable);
}
