package pe.agroruta.domain.port.in;

public interface DespacharNotificacionesUseCase {

    /** Envía los correos pendientes. Devuelve cuántos salieron. Un fallo reprograma el correo, no lanza error. */
    int despachar();

    /** Manda un correo de prueba a esa dirección y lo deja en el historial. Explica el error si no sale. */
    void probar(String correoDestino);
}
