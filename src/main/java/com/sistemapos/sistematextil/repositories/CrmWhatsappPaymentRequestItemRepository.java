package com.sistemapos.sistematextil.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sistemapos.sistematextil.model.CrmWhatsappPaymentRequestItem;

public interface CrmWhatsappPaymentRequestItemRepository extends JpaRepository<CrmWhatsappPaymentRequestItem, Long> {
    List<CrmWhatsappPaymentRequestItem> findByPaymentRequest_IdPaymentRequestOrderByIdPaymentRequestItemAsc(Long requestId);
}
