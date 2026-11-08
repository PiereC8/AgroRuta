package pe.agroruta.infrastructure.adapter.out.comprobante;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** El importe en letras que exige la representación impresa: "CUATROCIENTOS SETENTA Y CINCO CON 00/100 SOLES". */
final class MontoEnLetras {

    private static final String[] UNIDADES = {"", "UNO", "DOS", "TRES", "CUATRO", "CINCO", "SEIS", "SIETE", "OCHO",
            "NUEVE", "DIEZ", "ONCE", "DOCE", "TRECE", "CATORCE", "QUINCE", "DIECISEIS", "DIECISIETE", "DIECIOCHO",
            "DIECINUEVE", "VEINTE", "VEINTIUNO", "VEINTIDOS", "VEINTITRES", "VEINTICUATRO", "VEINTICINCO",
            "VEINTISEIS", "VEINTISIETE", "VEINTIOCHO", "VEINTINUEVE"};
    private static final String[] DECENAS = {"", "", "", "TREINTA", "CUARENTA", "CINCUENTA", "SESENTA", "SETENTA",
            "OCHENTA", "NOVENTA"};
    private static final String[] CENTENAS = {"", "CIENTO", "DOSCIENTOS", "TRESCIENTOS", "CUATROCIENTOS",
            "QUINIENTOS", "SEISCIENTOS", "SETECIENTOS", "OCHOCIENTOS", "NOVECIENTOS"};

    private MontoEnLetras() {
    }

    static String soles(BigDecimal monto) {
        BigDecimal redondeado = monto.setScale(2, RoundingMode.HALF_UP);
        long enteros = redondeado.longValue();
        int centimos = redondeado.remainder(BigDecimal.ONE).movePointRight(2).intValue();
        if (enteros < 0 || enteros > 999_999_999L) {
            throw new IllegalArgumentException("Monto fuera de rango para escribirlo en letras: " + monto);
        }
        return (enteros == 0 ? "CERO" : numero(enteros)) + " CON " + String.format("%02d", centimos) + "/100 SOLES";
    }

    private static String numero(long n) {
        long millones = n / 1_000_000;
        long miles = (n / 1_000) % 1_000;
        int resto = (int) (n % 1_000);
        StringBuilder texto = new StringBuilder();
        if (millones > 0) {
            texto.append(millones == 1 ? "UN MILLON" : apocope(hasta999((int) millones)) + " MILLONES");
        }
        if (miles > 0) {
            texto.append(texto.isEmpty() ? "" : " ").append(miles == 1 ? "MIL" : apocope(hasta999((int) miles)) + " MIL");
        }
        if (resto > 0) {
            texto.append(texto.isEmpty() ? "" : " ").append(hasta999(resto));
        }
        return texto.toString();
    }

    private static String hasta999(int n) {
        if (n == 100) {
            return "CIEN";
        }
        int centena = n / 100;
        int resto = n % 100;
        String parteCentena = CENTENAS[centena];
        String parteResto;
        if (resto < 30) {
            parteResto = UNIDADES[resto];
        } else {
            parteResto = DECENAS[resto / 10] + (resto % 10 == 0 ? "" : " Y " + UNIDADES[resto % 10]);
        }
        if (parteCentena.isEmpty()) {
            return parteResto;
        }
        return parteResto.isEmpty() ? parteCentena : parteCentena + " " + parteResto;
    }

    /** Delante de MIL o MILLONES: "VEINTIUN MIL", "TREINTA Y UN MIL", no "VEINTIUNO MIL". */
    private static String apocope(String texto) {
        return texto.endsWith("UNO") ? texto.substring(0, texto.length() - 1) : texto;
    }
}
