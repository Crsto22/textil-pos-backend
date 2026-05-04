package com.sistemapos.sistematextil.services;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

@Service
public class SunatRetryPolicyService {

    public LocalDateTime nextRetryAt(int intentos, LocalDateTime now) {
        return now.plusMinutes(minutesForAttempt(intentos));
    }

    private long minutesForAttempt(int intentos) {
        if (intentos <= 1) {
            return 5;
        }
        if (intentos == 2) {
            return 15;
        }
        if (intentos == 3) {
            return 30;
        }
        return 60;
    }
}
