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
        Negocio negocio = Negocio.nuevo(comando.identificadorNegocio(), comando.nombreNegocio(),
                comando.planSuscripcion());
        if (negocios.existePorIdentificador(negocio.identificadorNegocio())) {
            throw new NegocioYaExisteException(negocio.identificadorNegocio());
        }
        bases.crearYMigrar(negocio.identificadorNegocio());
        return negocios.guardar(negocio);
    }
}
