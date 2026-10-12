package com.sistemapos.sistematextil.repositories;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sistemapos.sistematextil.model.CrmWhatsappFilmEvidence;
import com.sistemapos.sistematextil.model.CrmWhatsappPaymentProcessingStatus;
import jakarta.persistence.LockModeType;

public interface CrmWhatsappFilmEvidenceRepository extends JpaRepository<CrmWhatsappFilmEvidence, Long> {
    Optional<CrmWhatsappFilmEvidence> findByMessage_IdMessage(Long messageId);
    Optional<CrmWhatsappFilmEvidence> findFirstByFilmAddon_IdFilmAddonOrderByCreatedAtDesc(Long addonId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from CrmWhatsappFilmEvidence e where e.idFilmEvidence=:id")
    Optional<CrmWhatsappFilmEvidence> findForUpdate(@Param("id") Long id);
    @Query("select e.idFilmEvidence from CrmWhatsappFilmEvidence e where e.processingStatus=:status and e.availableAt<=:now order by e.availableAt")
    List<Long> findReadyIds(@Param("status") CrmWhatsappPaymentProcessingStatus status,
            @Param("now") LocalDateTime now, Pageable pageable);
    @Query("select e from CrmWhatsappFilmEvidence e where e.sha256=:sha and e.filmAddon.conversation.connection.empresa.idEmpresa=:companyId order by e.createdAt")
    List<CrmWhatsappFilmEvidence> findByHash(@Param("sha") String sha, @Param("companyId") Integer companyId, Pageable pageable);
    @Query("select e from CrmWhatsappFilmEvidence e where upper(e.operationCode)=upper(:code) and e.filmAddon.conversation.connection.empresa.idEmpresa=:companyId order by e.createdAt")
    List<CrmWhatsappFilmEvidence> findByOperation(@Param("code") String code, @Param("companyId") Integer companyId, Pageable pageable);
    @Query("select e from CrmWhatsappFilmEvidence e where e.idFilmEvidence<>:id and e.sha256=:sha and e.filmAddon.conversation.connection.empresa.idEmpresa=:companyId order by e.createdAt")
    List<CrmWhatsappFilmEvidence> findOtherByHash(@Param("id") Long id, @Param("sha") String sha,
            @Param("companyId") Integer companyId, Pageable pageable);
    @Query("select e from CrmWhatsappFilmEvidence e where e.idFilmEvidence<>:id and upper(e.operationCode)=upper(:code) and e.filmAddon.conversation.connection.empresa.idEmpresa=:companyId order by e.createdAt")
    List<CrmWhatsappFilmEvidence> findOtherByOperation(@Param("id") Long id, @Param("code") String code,
            @Param("companyId") Integer companyId, Pageable pageable);
}
