package com.sistemapos.sistematextil.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sistemapos.sistematextil.model.GuiaRemisionTransportista;

public interface GuiaRemisionTransportistaRepository extends JpaRepository<GuiaRemisionTransportista, Integer> {

    List<GuiaRemisionTransportista> findByGuiaRemision_IdGuiaRemisionAndDeletedAtIsNull(Integer idGuiaRemision);

    Optional<GuiaRemisionTransportista> findFirstByGuiaRemision_IdGuiaRemisionAndDeletedAtIsNull(Integer idGuiaRemision);

    Optional<GuiaRemisionTransportista> findByIdGuiaTransportistaAndGuiaRemision_IdGuiaRemisionAndDeletedAtIsNull(
            Integer idGuiaTransportista,
            Integer idGuiaRemision);
}
