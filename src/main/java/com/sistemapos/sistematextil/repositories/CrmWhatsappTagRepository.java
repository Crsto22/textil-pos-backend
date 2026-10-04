package com.sistemapos.sistematextil.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sistemapos.sistematextil.model.CrmWhatsappTag;

public interface CrmWhatsappTagRepository extends JpaRepository<CrmWhatsappTag, Long> {

    List<CrmWhatsappTag> findByEmpresa_IdEmpresaAndDeletedAtIsNullOrderByNombreAsc(Integer idEmpresa);

    Optional<CrmWhatsappTag> findByIdTagAndEmpresa_IdEmpresaAndDeletedAtIsNull(Long idTag, Integer idEmpresa);

    Optional<CrmWhatsappTag> findFirstByEmpresa_IdEmpresaAndNombreIgnoreCaseAndDeletedAtIsNull(
            Integer idEmpresa,
            String nombre);
}
