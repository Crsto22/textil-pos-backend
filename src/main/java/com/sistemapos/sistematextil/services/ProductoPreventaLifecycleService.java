package com.sistemapos.sistematextil.services;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sistemapos.sistematextil.repositories.ProductoRepository;
import com.sistemapos.sistematextil.util.producto.ProductoPreventaPolicy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductoPreventaLifecycleService implements ApplicationRunner {
    private final ProductoRepository productoRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        avanzarFechasVencidas();
    }

    @Scheduled(cron = "0 1 0 * * *", zone = "America/Lima")
    @Transactional
    public void avanzarFechasVencidas() {
        LocalDate hoy = LocalDate.now(ProductoPreventaPolicy.BUSINESS_ZONE);
        int actualizados = productoRepository.avanzarFechasPreventaVencidas(
                hoy, hoy.plusDays(1), LocalDateTime.now(ProductoPreventaPolicy.BUSINESS_ZONE));
        if (actualizados > 0) {
            log.info("Fechas de envio de preventa avanzadas al {}: {} productos", hoy.plusDays(1), actualizados);
        }
    }
}
