package pe.agroruta.domain.model;

/** La pasarela o el proveedor de comprobantes no respondió: no se sabe qué pasó, hay que reintentar o confirmar luego. */
public class ServicioExternoNoDisponibleException extends RuntimeException {

    public ServicioExternoNoDisponibleException(String mensaje) {
        super(mensaje);
    }

    public ServicioExternoNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
