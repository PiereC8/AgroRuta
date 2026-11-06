package pe.agroruta.infrastructure.adapter.in.websocket;

/** Nombres de los canales de tiempo real. El negocio va en el nombre porque cada negocio numera sus envíos. */
public final class Canales {

    private Canales() {
    }

    public static String deEnvio(String identificadorNegocio, Long idEnvio) {
        return "/topic/" + identificadorNegocio + "/envios/" + idEnvio;
    }
}
