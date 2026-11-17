package pe.agroruta.domain.model;

/** La cuenta ya tiene una sesión abierta en otro dispositivo (responde 409). */
public class SesionAbiertaException extends ConflictoDominioException {

    public SesionAbiertaException() {
        super(ReglasSesion.SESION_ABIERTA);
    }
}
