package pe.agroruta.infrastructure.adapter.in.websocket;

public final class Canales {

    private Canales() {
    }

    public static String deEnvio(String identificadorNegocio, Long idEnvio) {
        return "/topic/" + identificadorNegocio + "/envios/" + idEnvio;
    }
}
