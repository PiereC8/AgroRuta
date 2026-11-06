package pe.agroruta.domain.port.in;

import pe.agroruta.domain.model.Cotizacion;

public interface CotizarFleteUseCase {

    Cotizacion cotizar(Long idLote);
}
