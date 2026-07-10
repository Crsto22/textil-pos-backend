package com.sistemapos.sistematextil.repositories;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sistemapos.sistematextil.model.EcommercePromocionCombo;

public interface EcommercePromocionComboRepository extends JpaRepository<EcommercePromocionCombo, Integer> {

    @EntityGraph(attributePaths = "usuarioCreacion")
    Page<EcommercePromocionCombo> findByDeletedAtIsNullOrderByCreatedAtDescIdEcommercePromocionComboDesc(Pageable pageable);

    @EntityGraph(attributePaths = "usuarioCreacion")
    @Query(
            value = """
                    SELECT DISTINCT p
                    FROM EcommercePromocionCombo p
                    WHERE p.deletedAt IS NULL
                      AND p.estado = 'ACTIVO'
                      AND (p.fechaInicio IS NULL OR p.fechaInicio <= :ahora)
                      AND (p.fechaFin IS NULL OR p.fechaFin >= :ahora)
                    ORDER BY p.createdAt DESC, p.idEcommercePromocionCombo DESC
                    """,
            countQuery = """
                    SELECT COUNT(p)
                    FROM EcommercePromocionCombo p
                    WHERE p.deletedAt IS NULL
                      AND p.estado = 'ACTIVO'
                      AND (p.fechaInicio IS NULL OR p.fechaInicio <= :ahora)
                      AND (p.fechaFin IS NULL OR p.fechaFin >= :ahora)
                    """)
    Page<EcommercePromocionCombo> listarAdminActivas(@Param("ahora") LocalDateTime ahora, Pageable pageable);

    @EntityGraph(attributePaths = "usuarioCreacion")
    @Query(
            value = """
                    SELECT DISTINCT p
                    FROM EcommercePromocionCombo p
                    WHERE p.deletedAt IS NULL
                      AND p.fechaFin IS NOT NULL
                      AND p.fechaFin < :ahora
                    ORDER BY p.fechaFin DESC, p.idEcommercePromocionCombo DESC
                    """,
            countQuery = """
                    SELECT COUNT(p)
                    FROM EcommercePromocionCombo p
                    WHERE p.deletedAt IS NULL
                      AND p.fechaFin IS NOT NULL
                      AND p.fechaFin < :ahora
                    """)
    Page<EcommercePromocionCombo> listarAdminVencidas(@Param("ahora") LocalDateTime ahora, Pageable pageable);

    @EntityGraph(attributePaths = {"items", "items.producto", "usuarioCreacion"})
    @Query("""
            SELECT DISTINCT p
            FROM EcommercePromocionCombo p
            LEFT JOIN p.items i
            LEFT JOIN i.producto prod
            WHERE p.deletedAt IS NULL
              AND p.estado = 'ACTIVO'
              AND (p.fechaInicio IS NULL OR p.fechaInicio <= :ahora)
              AND (p.fechaFin IS NULL OR p.fechaFin >= :ahora)
            """)
    List<EcommercePromocionCombo> listarActivas(@Param("ahora") LocalDateTime ahora);

    @EntityGraph(attributePaths = "usuarioCreacion")
    @Query("""
            SELECT DISTINCT p
            FROM EcommercePromocionCombo p
            LEFT JOIN p.items i
            LEFT JOIN i.producto prod
            WHERE p.deletedAt IS NULL
              AND p.estado = 'ACTIVO'
              AND (p.fechaInicio IS NULL OR p.fechaInicio <= :ahora)
              AND (p.fechaFin IS NULL OR p.fechaFin >= :ahora)
              AND p.items IS NOT EMPTY
              AND NOT EXISTS (
                    SELECT i2
                    FROM EcommercePromocionComboItem i2
                    JOIN i2.producto prod2
                    WHERE i2.promocion = p
                      AND (
                            prod2.publicarEcommerce <> true
                            OR prod2.estado <> 'ACTIVO'
                            OR prod2.activo <> 'ACTIVO'
                            OR prod2.deletedAt IS NOT NULL
                      )
              )
            ORDER BY FUNCTION('RAND')
            """)
    List<EcommercePromocionCombo> listarActivasAleatorias(@Param("ahora") LocalDateTime ahora, Pageable pageable);

    @EntityGraph(attributePaths = "usuarioCreacion")
    @Query(
            value = """
                    SELECT DISTINCT p
                    FROM EcommercePromocionCombo p
                    WHERE p.deletedAt IS NULL
                      AND p.estado = 'ACTIVO'
                      AND (p.fechaInicio IS NULL OR p.fechaInicio <= :ahora)
                      AND (p.fechaFin IS NULL OR p.fechaFin >= :ahora)
                      AND p.items IS NOT EMPTY
                      AND NOT EXISTS (
                            SELECT i2
                            FROM EcommercePromocionComboItem i2
                            JOIN i2.producto prod2
                            WHERE i2.promocion = p
                              AND (
                                    prod2.publicarEcommerce <> true
                                    OR prod2.estado <> 'ACTIVO'
                                    OR prod2.activo <> 'ACTIVO'
                                    OR prod2.deletedAt IS NOT NULL
                              )
                      )
                    ORDER BY p.createdAt DESC, p.idEcommercePromocionCombo DESC
                    """,
            countQuery = """
                    SELECT COUNT(p)
                    FROM EcommercePromocionCombo p
                    WHERE p.deletedAt IS NULL
                      AND p.estado = 'ACTIVO'
                      AND (p.fechaInicio IS NULL OR p.fechaInicio <= :ahora)
                      AND (p.fechaFin IS NULL OR p.fechaFin >= :ahora)
                      AND p.items IS NOT EMPTY
                      AND NOT EXISTS (
                            SELECT i2
                            FROM EcommercePromocionComboItem i2
                            JOIN i2.producto prod2
                            WHERE i2.promocion = p
                              AND (
                                    prod2.publicarEcommerce <> true
                                    OR prod2.estado <> 'ACTIVO'
                                    OR prod2.activo <> 'ACTIVO'
                                    OR prod2.deletedAt IS NOT NULL
                              )
                      )
                    """)
    Page<EcommercePromocionCombo> listarPublicasVigentes(@Param("ahora") LocalDateTime ahora, Pageable pageable);

    @EntityGraph(attributePaths = {"items", "items.producto", "usuarioCreacion"})
    List<EcommercePromocionCombo> findByIdEcommercePromocionComboIn(List<Integer> ids);

    @EntityGraph(attributePaths = {"items", "items.producto", "usuarioCreacion"})
    @Query("""
            SELECT DISTINCT p
            FROM EcommercePromocionCombo p
            LEFT JOIN p.items i
            LEFT JOIN i.producto prod
            WHERE p.deletedAt IS NULL
              AND p.estado = 'ACTIVO'
              AND (p.fechaInicio IS NULL OR p.fechaInicio <= :ahora)
              AND (p.fechaFin IS NULL OR p.fechaFin >= :ahora)
              AND prod.idProducto IN :productoIds
            """)
    List<EcommercePromocionCombo> listarActivasPorProductos(
            @Param("ahora") LocalDateTime ahora,
            @Param("productoIds") List<Integer> productoIds);
}
