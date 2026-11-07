package pe.agroruta.domain.port.out;

import java.time.Instant;
import java.util.List;

import pe.agroruta.domain.model.HechoDelNegocio.CobroVencido;
import pe.agroruta.domain.model.HechoDelNegocio.ComprobanteAtascado;
import pe.agroruta.domain.model.HechoDelNegocio.ComprobanteRechazado;

/** Lo que hay que mirar cada cierto tiempo porque nadie lo dispara: cosas que se quedan sin atender. */
public interface RevisionRepositoryPort {

    List<ComprobanteRechazado> comprobantesRechazadosDesde(Instant desde);

    List<ComprobanteAtascado> comprobantesAtascados(Instant creadosAntesDe, Instant ahora);

    List<CobroVencido> cobrosVencidos(Instant entregadosAntesDe, Instant ahora);
}
