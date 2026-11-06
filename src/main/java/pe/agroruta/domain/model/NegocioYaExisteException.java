package pe.agroruta.domain.model;

public class NegocioYaExisteException extends DominioException {

    public NegocioYaExisteException(String identificadorNegocio) {
        super("Ya existe un negocio con el identificador " + identificadorNegocio);
    }
}
