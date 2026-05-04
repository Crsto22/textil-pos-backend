package com.sistemapos.sistematextil.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sistemapos.sistematextil.model.GuiaRemisionCatalogoTransportista;

public interface GuiaRemisionCatalogoTransportistaRepository
        extends JpaRepository<GuiaRemisionCatalogoTransportista, Integer> {

    @Query("""
            SELECT t
            FROM GuiaRemisionCatalogoTransportista t
            WHERE t.deletedAt IS NULL
              AND t.empresa.idEmpresa = :idEmpresa
              AND (
                    :term IS NULL
                    OR LOWER(t.transportistaNroDoc) LIKE LOWER(CONCAT('%', :term, '%'))
                    OR LOWER(t.transportistaRazonSocial) LIKE LOWER(CONCAT('%', :term, '%'))
                    OR LOWER(t.transportistaRegistroMtc) LIKE LOWER(CONCAT('%', :term, '%'))
              )
            ORDER BY t.transportistaRazonSocial ASC, t.idCatalogoTransportista ASC
            """)
    List<GuiaRemisionCatalogoTransportista> buscarActivos(
            @Param("idEmpresa") Integer idEmpresa,
            @Param("term") String term);

    Optional<GuiaRemisionCatalogoTransportista>
            findByIdCatalogoTransportistaAndEmpresa_IdEmpresaAndDeletedAtIsNull(
                    Integer idCatalogoTransportista,
                    Integer idEmpresa);

    List<GuiaRemisionCatalogoTransportista> findByIdCatalogoTransportistaInAndEmpresa_IdEmpresaAndDeletedAtIsNull(
            List<Integer> idsCatalogoTransportistas,
            Integer idEmpresa);

    boolean existsByEmpresa_IdEmpresaAndTransportistaNroDocAndDeletedAtIsNull(
            Integer idEmpresa,
            String transportistaNroDoc);

    boolean existsByEmpresa_IdEmpresaAndTransportistaNroDocAndDeletedAtIsNullAndIdCatalogoTransportistaNot(
            Integer idEmpresa,
            String transportistaNroDoc,
            Integer idCatalogoTransportista);
}
