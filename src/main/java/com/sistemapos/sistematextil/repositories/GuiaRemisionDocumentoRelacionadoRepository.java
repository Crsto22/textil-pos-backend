package com.sistemapos.sistematextil.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sistemapos.sistematextil.model.GuiaRemisionDocumentoRelacionado;

public interface GuiaRemisionDocumentoRelacionadoRepository
        extends JpaRepository<GuiaRemisionDocumentoRelacionado, Integer> {

    List<GuiaRemisionDocumentoRelacionado>
            findByGuiaRemision_IdGuiaRemisionAndDeletedAtIsNullOrderByIdGuiaDocumentoRelacionadoAsc(
                    Integer idGuiaRemision);
}
