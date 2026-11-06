package pe.agroruta.domain.model;

public class NegocioYaExisteException extends DominioException {

    public NegocioYaExisteException(String slug) {
        super("Ya existe un negocio con el identificador " + slug);
    }
}
