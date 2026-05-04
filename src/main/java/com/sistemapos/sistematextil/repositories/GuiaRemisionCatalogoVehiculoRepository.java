package com.sistemapos.sistematextil.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sistemapos.sistematextil.model.GuiaRemisionCatalogoVehiculo;

public interface GuiaRemisionCatalogoVehiculoRepository
        extends JpaRepository<GuiaRemisionCatalogoVehiculo, Integer> {

    @Query("""
            SELECT v
            FROM GuiaRemisionCatalogoVehiculo v
            WHERE v.deletedAt IS NULL
              AND v.empresa.idEmpresa = :idEmpresa
              AND (
                    :term IS NULL
                    OR LOWER(v.placa) LIKE LOWER(CONCAT('%', :term, '%'))
              )
            ORDER BY v.placa ASC, v.idCatalogoVehiculo ASC
            """)
    List<GuiaRemisionCatalogoVehiculo> buscarActivos(
            @Param("idEmpresa") Integer idEmpresa,
            @Param("term") String term);

    Optional<GuiaRemisionCatalogoVehiculo> findByIdCatalogoVehiculoAndEmpresa_IdEmpresaAndDeletedAtIsNull(
            Integer idCatalogoVehiculo,
            Integer idEmpresa);

    List<GuiaRemisionCatalogoVehiculo> findByIdCatalogoVehiculoInAndEmpresa_IdEmpresaAndDeletedAtIsNull(
            List<Integer> idsCatalogoVehiculos,
            Integer idEmpresa);

    boolean existsByEmpresa_IdEmpresaAndPlacaAndDeletedAtIsNull(Integer idEmpresa, String placa);

    boolean existsByEmpresa_IdEmpresaAndPlacaAndDeletedAtIsNullAndIdCatalogoVehiculoNot(
            Integer idEmpresa,
            String placa,
            Integer idCatalogoVehiculo);
}
