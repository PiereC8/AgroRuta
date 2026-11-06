package pe.agroruta.domain.model;

public class DestinoYaExisteException extends DominioException {

    public DestinoYaExisteException(String nombreDestino) {
        super("Ya existe un destino llamado " + nombreDestino);
    }
}
