package com.sistemapos.sistematextil.repositories;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.sistemapos.sistematextil.model.CrmWhatsappBusinessHours;

public interface CrmWhatsappBusinessHoursRepository extends JpaRepository<CrmWhatsappBusinessHours, Long> {
    List<CrmWhatsappBusinessHours> findByConnection_IdConnectionOrderByIdBusinessHoursAsc(Long connectionId);
    Optional<CrmWhatsappBusinessHours> findByConnection_IdConnectionAndDayOfWeek(Long connectionId, String dayOfWeek);
}
