package pe.agroruta.infrastructure.adapter.out.resiliencia;

import java.util.Optional;

import pe.agroruta.domain.model.ResultadoCobro;
import pe.agroruta.domain.model.SolicitudCobro;
import pe.agroruta.domain.port.out.PagoPort;

/** Decorator: la misma pasarela, con un disyuntor delante. Cobrar y consultar comparten el mismo estado. */
public class PagoPortConDisyuntor implements PagoPort {

    private final PagoPort pasarela;
    private final Disyuntor disyuntor;

    public PagoPortConDisyuntor(PagoPort pasarela, Disyuntor disyuntor) {
        this.pasarela = pasarela;
        this.disyuntor = disyuntor;
    }

    @Override
    public ResultadoCobro cobrar(SolicitudCobro solicitud) {
        return disyuntor.ejecutar(() -> pasarela.cobrar(solicitud));
    }

    @Override
    public Optional<ResultadoCobro> consultarCargo(String idCargo) {
        return disyuntor.ejecutar(() -> pasarela.consultarCargo(idCargo));
    }
}
