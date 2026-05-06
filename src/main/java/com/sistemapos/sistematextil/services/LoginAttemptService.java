package com.sistemapos.sistematextil.services;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

@Service
public class LoginAttemptService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final Map<String, AttemptState> attempts = new ConcurrentHashMap<>();

    public void validarDisponible(String email, String clientIp) {
        AttemptState state = attempts.get(key(email, clientIp));
        if (state == null || state.lockedUntil == null || Instant.now().isAfter(state.lockedUntil)) {
            return;
        }
        throw new RuntimeException("Demasiados intentos fallidos. Intente nuevamente en unos minutos.");
    }

    public void registrarExito(String email, String clientIp) {
        attempts.remove(key(email, clientIp));
    }

    public void registrarFallo(String email, String clientIp) {
        attempts.compute(key(email, clientIp), (ignored, current) -> {
            AttemptState state = current == null ? new AttemptState() : current;
            Instant now = Instant.now();
            if ((state.lockedUntil != null && now.isAfter(state.lockedUntil))
                    || (state.lastFailedAt != null && now.isAfter(state.lastFailedAt.plus(LOCK_DURATION)))) {
                state.failedAttempts = 0;
                state.lockedUntil = null;
            }
            state.failedAttempts++;
            state.lastFailedAt = now;
            if (state.failedAttempts >= MAX_FAILED_ATTEMPTS) {
                state.lockedUntil = now.plus(LOCK_DURATION);
            }
            return state;
        });
    }

    private String key(String email, String clientIp) {
        String normalizedEmail = email == null ? "sin-email" : email.trim().toLowerCase(Locale.ROOT);
        String normalizedIp = clientIp == null || clientIp.isBlank() ? "sin-ip" : clientIp.trim();
        return normalizedEmail + "|" + normalizedIp;
    }

    private static final class AttemptState {
        private int failedAttempts;
        private Instant lastFailedAt;
        private Instant lockedUntil;
    }
}
