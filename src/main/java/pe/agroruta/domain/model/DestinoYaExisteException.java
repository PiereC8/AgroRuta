package pe.agroruta.domain.model;

public class DestinoYaExisteException extends ConflictoDominioException {

    public DestinoYaExisteException(String nombreDestino) {
        super("Ya existe un destino llamado " + nombreDestino);
    }
}
