package com.sistemapos.sistematextil.repositories;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.sistemapos.sistematextil.model.CrmWhatsappShalomProfile;

public interface CrmWhatsappShalomProfileRepository extends JpaRepository<CrmWhatsappShalomProfile, Long> {
    Optional<CrmWhatsappShalomProfile> findByCliente_IdCliente(Integer customerId);
}
