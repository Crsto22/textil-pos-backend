package com.sistemapos.sistematextil.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

import org.springframework.stereotype.Service;

@Service
public class SunatMontoTextoService {

    public String montoEnLetras(BigDecimal monto, String moneda) {
        BigDecimal valor = monto == null ? BigDecimal.ZERO : monto.abs().setScale(2, RoundingMode.HALF_UP);
        long entero = valor.longValue();
        int decimales = valor.remainder(BigDecimal.ONE).movePointRight(2).intValue();
        String nombreMoneda = "PEN".equalsIgnoreCase(moneda) || moneda == null || moneda.isBlank()
                ? "SOLES"
                : moneda.trim().toUpperCase(Locale.ROOT);
        return convertirNumeroALetras(entero) + " CON "
                + String.format(Locale.ROOT, "%02d/100", decimales)
                + " "
                + nombreMoneda;
    }

    private String convertirNumeroALetras(long numero) {
        String[] unidades = { "CERO", "UNO", "DOS", "TRES", "CUATRO", "CINCO", "SEIS", "SIETE", "OCHO", "NUEVE",
                "DIEZ", "ONCE", "DOCE", "TRECE", "CATORCE", "QUINCE", "DIECISEIS", "DIECISIETE", "DIECIOCHO",
                "DIECINUEVE", "VEINTE" };
        String[] decenas = { "", "", "VEINTE", "TREINTA", "CUARENTA", "CINCUENTA", "SESENTA", "SETENTA", "OCHENTA",
                "NOVENTA" };
        String[] centenas = { "", "CIENTO", "DOSCIENTOS", "TRESCIENTOS", "CUATROCIENTOS", "QUINIENTOS",
                "SEISCIENTOS", "SETECIENTOS", "OCHOCIENTOS", "NOVECIENTOS" };

        if (numero < 0) {
            return "MENOS " + convertirNumeroALetras(Math.abs(numero));
        }
        if (numero <= 20) {
            return unidades[(int) numero];
        }
        if (numero < 30) {
            return numero == 21 ? "VEINTIUNO" : "VEINTI" + convertirNumeroALetras(numero - 20);
        }
        if (numero < 100) {
            long decena = numero / 10;
            long unidad = numero % 10;
            if (unidad == 0) {
                return decenas[(int) decena];
            }
            return decenas[(int) decena] + " Y " + convertirNumeroALetras(unidad);
        }
        if (numero == 100) {
            return "CIEN";
        }
        if (numero < 1000) {
            long centena = numero / 100;
            long resto = numero % 100;
            if (resto == 0) {
                return centenas[(int) centena];
            }
            return centenas[(int) centena] + " " + convertirNumeroALetras(resto);
        }
        if (numero < 1_000_000) {
            long miles = numero / 1000;
            long resto = numero % 1000;
            String prefijo = miles == 1 ? "MIL" : convertirNumeroALetras(miles) + " MIL";
            if (resto == 0) {
                return prefijo;
            }
            return prefijo + " " + convertirNumeroALetras(resto);
        }
        if (numero < 1_000_000_000L) {
            long millones = numero / 1_000_000;
            long resto = numero % 1_000_000;
            String prefijo = millones == 1
                    ? "UN MILLON"
                    : convertirNumeroALetras(millones) + " MILLONES";
            if (resto == 0) {
                return prefijo;
            }
            return prefijo + " " + convertirNumeroALetras(resto);
        }
        return String.valueOf(numero);
    }
}
