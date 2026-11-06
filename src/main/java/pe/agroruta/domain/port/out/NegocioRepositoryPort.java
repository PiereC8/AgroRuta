package pe.agroruta.domain.port.out;

import pe.agroruta.domain.model.Negocio;

public interface NegocioRepositoryPort {

    boolean existePorIdentificador(String identificadorNegocio);

    Negocio guardar(Negocio negocio);
}
