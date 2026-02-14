package com.sistemapos.sistematextil.repositories;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sistemapos.sistematextil.model.Cliente;
import com.sistemapos.sistematextil.util.TipoDocumento;

public interface ClienteRepository extends JpaRepository<Cliente, Integer> {

    Page<Cliente> findByDeletedAtIsNull(Pageable pageable);
    Page<Cliente> findByDeletedAtIsNullAndSucursal_IdSucursal(Pageable pageable, Integer idSucursal);

    @Query("""
            SELECT c
            FROM Cliente c
            LEFT JOIN c.sucursal s
            WHERE c.deletedAt IS NULL
              AND (:idSucursal IS NULL OR s.idSucursal = :idSucursal)
              AND (
                    :term IS NULL
                    OR LOWER(c.nombres) LIKE LOWER(CONCAT('%', :term, '%'))
                    OR (
                        c.tipoDocumento = :tipoDocumentoDni
                        AND c.nroDocumento LIKE CONCAT(:term, '%')
                    )
              )
            """)
    Page<Cliente> buscarPorNombreODni(
            @Param("term") String term,
            @Param("idSucursal") Integer idSucursal,
            @Param("tipoDocumentoDni") TipoDocumento tipoDocumentoDni,
            Pageable pageable);

    Optional<Cliente> findByIdClienteAndDeletedAtIsNull(Integer idCliente);
    Optional<Cliente> findByIdClienteAndDeletedAtIsNullAndSucursal_IdSucursal(Integer idCliente, Integer idSucursal);
}
