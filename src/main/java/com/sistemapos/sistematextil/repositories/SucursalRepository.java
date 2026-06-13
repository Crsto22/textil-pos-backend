package com.sistemapos.sistematextil.repositories;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sistemapos.sistematextil.model.Sucursal;
import com.sistemapos.sistematextil.model.SucursalTipo;
import java.util.List;
import java.util.Set;

public interface SucursalRepository extends JpaRepository<Sucursal, Integer> {
    Page<Sucursal> findByDeletedAtIsNull(Pageable pageable);
    Page<Sucursal> findByDeletedAtIsNullAndNombreStartingWithIgnoreCase(String nombre, Pageable pageable);
    Optional<Sucursal> findByIdSucursalAndDeletedAtIsNull(Integer idSucursal);
    Optional<Sucursal> findByNombreIgnoreCaseAndDeletedAtIsNull(String nombre);
    Optional<Sucursal> findByEmpresa_IdEmpresaAndNombreIgnoreCaseAndDeletedAtIsNull(Integer idEmpresa, String nombre);
    List<Sucursal> findByDeletedAtIsNullAndEstadoOrderByIdSucursalAsc(String estado);
    List<Sucursal> findByEmpresa_IdEmpresaAndDeletedAtIsNullOrderByIdSucursalAsc(Integer idEmpresa);
    List<Sucursal> findByIdSucursalInAndDeletedAtIsNullOrderByIdSucursalAsc(Set<Integer> idsSucursales);
    Optional<Sucursal> findFirstByPublicarEcommerceTrueAndDeletedAtIsNullAndEstadoAndTipoOrderByIdSucursalAsc(
            String estado,
            SucursalTipo tipo);

    @Modifying
    @Query("""
            UPDATE Sucursal s
            SET s.publicarEcommerce = false
            WHERE s.publicarEcommerce = true
              AND (:idSucursalActual IS NULL OR s.idSucursal <> :idSucursalActual)
            """)
    int desmarcarOtrasSucursalesEcommerce(@Param("idSucursalActual") Integer idSucursalActual);
}
