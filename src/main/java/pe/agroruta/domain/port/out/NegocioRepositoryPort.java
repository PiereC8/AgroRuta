package pe.agroruta.domain.port.out;

import java.util.List;
import java.util.Optional;

import pe.agroruta.domain.model.Negocio;

public interface NegocioRepositoryPort {

    boolean existePorIdentificador(String identificadorNegocio);

    Optional<Negocio> buscarActivoPorIdentificador(String identificadorNegocio);

    List<Negocio> listarActivos();

    Negocio guardar(Negocio negocio);
}
