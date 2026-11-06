package pe.agroruta.domain.model;

/** Se dejó de llamar al servicio por un rato porque falló varias veces seguidas: no se le envió nada. */
public class ServicioEnPausaException extends ServicioExternoNoDisponibleException {

    public ServicioEnPausaException(String mensaje) {
        super(mensaje);
    }
}
