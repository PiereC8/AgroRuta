package pe.agroruta.domain.port.in;

import pe.agroruta.domain.model.Cotizacion;

// RFF-06: puerto de entrada del cotizador.
public interface CotizarFleteUseCase {

    Cotizacion cotizar(Long idLote);
}
