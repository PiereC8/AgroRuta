package pe.agroruta.domain.model;

import java.text.Normalizer;
import java.util.Locale;

public final class MensajesPago {

    private MensajesPago() {
    }

    public static String paraUsuario(String codigo, String mensajePasarela) {
        String texto = normalizar((codigo == null ? "" : codigo) + " " + (mensajePasarela == null ? "" : mensajePasarela));
        if (texto.contains("servicio_en_pausa")) {
            return "El servicio de pagos no está disponible por un momento. No se cobró nada: intenta de nuevo en unos minutos.";
        }
        if (texto.contains("fondo") || texto.contains("insufficient")) {
            return "La tarjeta no tiene fondos suficientes. Prueba con otra tarjeta.";
        }
        if (texto.contains("vencid") || texto.contains("expir")) {
            return "La tarjeta está vencida. Prueba con otra tarjeta.";
        }
        if (texto.contains("max_attempts") || texto.contains("maximo de intentos")) {
            return "Superaste el máximo de intentos con esa tarjeta. Espera unos minutos o usa otra tarjeta.";
        }
        if (texto.contains("duplicated")) {
            return "Ya se registró un pago igual hace poco. Revisa si ya se cobró antes de intentar de nuevo.";
        }
        if (texto.contains("robad") || texto.contains("perdid") || texto.contains("fraud")
                || texto.contains("stolen") || texto.contains("lost") || texto.contains("restring")
                || texto.contains("call_for_authorize") || texto.contains("card_disabled")
                || texto.contains("blacklist") || texto.contains("high_risk")) {
            return "Tu banco no autorizó la operación. Comunícate con tu banco o usa otra tarjeta.";
        }
        if (texto.contains("cvv") || texto.contains("security_code") || texto.contains("bad_filled")
                || texto.contains("invalid") || texto.contains("incorrect") || texto.contains("numero")
                || texto.contains("datos")) {
            return "Revisa los datos de la tarjeta e inténtalo de nuevo.";
        }
        return "No pudimos procesar el pago. Intenta con otra tarjeta.";
    }

    private static String normalizar(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}
