package pe.agroruta.domain.model;

/** Una regla de negocio choca con el estado actual de los datos (algo ya existe, ya fue usado, está ocupado). */
public class ConflictoDominioException extends DominioException {

    public ConflictoDominioException(String mensaje) {
        super(mensaje);
    }
}
