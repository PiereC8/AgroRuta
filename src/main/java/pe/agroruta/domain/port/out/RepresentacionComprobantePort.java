package pe.agroruta.domain.port.out;

import pe.agroruta.domain.model.ComprobanteImpreso;

/** Arma la representación impresa (PDF) de un comprobante electrónico. */
public interface RepresentacionComprobantePort {

    byte[] pdf(ComprobanteImpreso comprobante);
}
