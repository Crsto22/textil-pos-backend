package com.sistemapos.sistematextil.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sistemapos.sistematextil.model.GuiaRemisionCatalogoConductor;

public interface GuiaRemisionCatalogoConductorRepository
        extends JpaRepository<GuiaRemisionCatalogoConductor, Integer> {

    @Query("""
            SELECT c
            FROM GuiaRemisionCatalogoConductor c
            WHERE c.deletedAt IS NULL
              AND c.empresa.idEmpresa = :idEmpresa
              AND (
                    :term IS NULL
                    OR LOWER(c.nroDocumento) LIKE LOWER(CONCAT('%', :term, '%'))
                    OR LOWER(c.nombres) LIKE LOWER(CONCAT('%', :term, '%'))
                    OR LOWER(c.apellidos) LIKE LOWER(CONCAT('%', :term, '%'))
                    OR LOWER(c.licencia) LIKE LOWER(CONCAT('%', :term, '%'))
                    OR LOWER(CONCAT(CONCAT(c.nombres, ' '), c.apellidos)) LIKE LOWER(CONCAT('%', :term, '%'))
              )
            ORDER BY c.nombres ASC, c.apellidos ASC, c.idCatalogoConductor ASC
            """)
    List<GuiaRemisionCatalogoConductor> buscarActivos(
            @Param("idEmpresa") Integer idEmpresa,
            @Param("term") String term);

    Optional<GuiaRemisionCatalogoConductor> findByIdCatalogoConductorAndEmpresa_IdEmpresaAndDeletedAtIsNull(
            Integer idCatalogoConductor,
            Integer idEmpresa);

    List<GuiaRemisionCatalogoConductor> findByIdCatalogoConductorInAndEmpresa_IdEmpresaAndDeletedAtIsNull(
            List<Integer> idsCatalogoConductores,
            Integer idEmpresa);

    boolean existsByEmpresa_IdEmpresaAndNroDocumentoAndDeletedAtIsNull(Integer idEmpresa, String nroDocumento);

    boolean existsByEmpresa_IdEmpresaAndNroDocumentoAndDeletedAtIsNullAndIdCatalogoConductorNot(
            Integer idEmpresa,
            String nroDocumento,
            Integer idCatalogoConductor);
}
