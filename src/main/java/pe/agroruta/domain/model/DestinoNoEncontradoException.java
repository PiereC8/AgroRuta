package pe.agroruta.domain.model;

public class DestinoNoEncontradoException extends DominioException {

    public DestinoNoEncontradoException(Long idDestinoVenta) {
        super("No existe el destino " + idDestinoVenta);
    }
}
