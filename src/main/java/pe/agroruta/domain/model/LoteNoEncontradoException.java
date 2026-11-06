package pe.agroruta.domain.model;

public class LoteNoEncontradoException extends DominioException {

    public LoteNoEncontradoException(Long idLote) {
        super("No existe el lote " + idLote);
    }
}
