package pe.agroruta.application.service;

import pe.agroruta.domain.model.Negocio;
import pe.agroruta.domain.model.NegocioYaExisteException;
import pe.agroruta.domain.port.in.AltaNegocioUseCase;
import pe.agroruta.domain.port.out.BaseNegocioPort;
import pe.agroruta.domain.port.out.NegocioRepositoryPort;

public class AltaNegocioService implements AltaNegocioUseCase {

    private final NegocioRepositoryPort negocios;
    private final BaseNegocioPort bases;

    public AltaNegocioService(NegocioRepositoryPort negocios, BaseNegocioPort bases) {
        this.negocios = negocios;
        this.bases = bases;
    }

    @Override
    public Negocio darDeAlta(Comando comando) {
        Negocio negocio = Negocio.nuevo(comando.slug(), comando.nombre(), comando.plan());
        if (negocios.existePorSlug(negocio.slug())) {
            throw new NegocioYaExisteException(negocio.slug());
        }
        bases.crearYMigrar(negocio.slug());
        return negocios.guardar(negocio);
    }
}
