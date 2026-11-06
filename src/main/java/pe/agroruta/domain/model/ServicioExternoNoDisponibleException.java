package pe.agroruta.domain.model;

public class ServicioExternoNoDisponibleException extends RuntimeException {

    public ServicioExternoNoDisponibleException(String mensaje) {
        super(mensaje);
    }

    public ServicioExternoNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
