package pe.agroruta.domain.port.out;

import java.util.Optional;

import pe.agroruta.domain.model.Negocio;

public interface NegocioRepositoryPort {

    boolean existePorIdentificador(String identificadorNegocio);

    Optional<Negocio> buscarActivoPorIdentificador(String identificadorNegocio);

    Negocio guardar(Negocio negocio);
}
