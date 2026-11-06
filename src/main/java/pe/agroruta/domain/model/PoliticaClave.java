package pe.agroruta.domain.model;

public final class PoliticaClave {

    public static final int LONGITUD_MINIMA = 8;
    public static final int LONGITUD_MAXIMA = 72;

    private PoliticaClave() {
    }

    public static void validar(String clave) {
        if (clave == null || clave.length() < LONGITUD_MINIMA || clave.length() > LONGITUD_MAXIMA) {
            throw new DominioException(
                    "La clave debe tener entre " + LONGITUD_MINIMA + " y " + LONGITUD_MAXIMA + " caracteres");
        }
    }
}
