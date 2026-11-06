package pe.agroruta.domain.model;

public class DestinoNoEncontradoException extends RecursoNoEncontradoException {

    public DestinoNoEncontradoException(Long idDestinoVenta) {
        super("No existe el destino " + idDestinoVenta);
    }
}
