package pe.agroruta.domain.model;

import java.util.Locale;
import java.util.regex.Pattern;

/** Reglas de una dirección de correo. Se guarda en minúsculas y sin espacios alrededor. */
public final class Correo {

    public static final int LONGITUD_MAXIMA = 120;
    private static final Pattern FORMATO = Pattern.compile("^[^@\\s,;<>]+@[^@\\s,;<>]+\\.[^@\\s,;<>]+$");

    private Correo() {
    }

    /** Devuelve el correo limpio, o null si no se escribió nada. Lanza error si lo escrito no es un correo. */
    public static String normalizar(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        String limpio = texto.strip().toLowerCase(Locale.ROOT);
        if (limpio.length() > LONGITUD_MAXIMA || !FORMATO.matcher(limpio).matches()) {
            throw new DominioException("El correo no es válido");
        }
        return limpio;
    }
}
