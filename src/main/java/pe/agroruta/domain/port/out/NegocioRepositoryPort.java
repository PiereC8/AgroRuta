package pe.agroruta.domain.port.out;

import pe.agroruta.domain.model.Negocio;

public interface NegocioRepositoryPort {

    boolean existePorSlug(String slug);

    Negocio guardar(Negocio negocio);
}
