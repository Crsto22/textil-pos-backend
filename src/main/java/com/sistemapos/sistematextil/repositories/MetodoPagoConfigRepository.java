package com.sistemapos.sistematextil.repositories;

import java.util.Optional;
import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.sistemapos.sistematextil.model.MetodoPagoConfig;

@Repository
public interface MetodoPagoConfigRepository extends JpaRepository<MetodoPagoConfig, Integer> {

    @EntityGraph(attributePaths = "cuentas")
    List<MetodoPagoConfig> findByDeletedAtIsNull(Sort sort);

    @EntityGraph(attributePaths = "cuentas")
    List<MetodoPagoConfig> findByEstadoAndDeletedAtIsNullOrderByNombreAsc(String estado);

    @EntityGraph(attributePaths = "cuentas")
    Optional<MetodoPagoConfig> findByIdMetodoPagoAndDeletedAtIsNull(Integer idMetodoPago);

    @Query("""
            SELECT m
            FROM MetodoPagoConfig m
            WHERE UPPER(TRIM(m.nombre)) = UPPER(TRIM(:nombre))
            ORDER BY m.idMetodoPago ASC
            """)
    List<MetodoPagoConfig> findAllByNombreNormalizado(@Param("nombre") String nombre);
}
