package pe.agroruta.domain.model;

import java.text.Normalizer;
import java.util.Locale;

/** Traduce lo que responde la pasarela a una explicación simple y a un siguiente paso (RNF-29). */
public final class MensajesPago {

    private MensajesPago() {
    }

    public static String paraUsuario(String codigo, String mensajePasarela) {
        String texto = normalizar((codigo == null ? "" : codigo) + " " + (mensajePasarela == null ? "" : mensajePasarela));
        if (texto.contains("fondo")) {
            return "La tarjeta no tiene fondos suficientes. Prueba con otra tarjeta.";
        }
        if (texto.contains("vencid") || texto.contains("expir")) {
            return "La tarjeta está vencida. Prueba con otra tarjeta.";
        }
        if (texto.contains("robad") || texto.contains("perdid") || texto.contains("fraud")
                || texto.contains("stolen") || texto.contains("lost") || texto.contains("restring")) {
            return "Tu banco no autorizó la operación. Comunícate con tu banco o usa otra tarjeta.";
        }
        if (texto.contains("cvv") || texto.contains("invalid") || texto.contains("incorrect")
                || texto.contains("numero") || texto.contains("datos")) {
            return "Revisa los datos de la tarjeta e inténtalo de nuevo.";
        }
        return "No pudimos procesar el pago. Intenta con otra tarjeta.";
    }

    private static String normalizar(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}
