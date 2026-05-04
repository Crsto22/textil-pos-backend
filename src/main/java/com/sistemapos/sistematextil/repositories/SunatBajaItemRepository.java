package com.sistemapos.sistematextil.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sistemapos.sistematextil.model.SunatBajaItem;

public interface SunatBajaItemRepository extends JpaRepository<SunatBajaItem, Integer> {

    List<SunatBajaItem> findByLote_IdSunatBajaLoteAndDeletedAtIsNullOrderByIdSunatBajaItemAsc(Integer idSunatBajaLote);

    boolean existsByNotaCredito_IdNotaCreditoAndDeletedAtIsNull(Integer idNotaCredito);
}
