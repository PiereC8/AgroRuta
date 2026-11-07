package pe.agroruta.domain.port.out;

import java.time.Instant;
import java.util.List;

import pe.agroruta.domain.model.HechoDelNegocio.AlertaDeEnvio;
import pe.agroruta.domain.model.HechoDelNegocio.CobroVencido;
import pe.agroruta.domain.model.HechoDelNegocio.ComprobanteAtascado;
import pe.agroruta.domain.model.HechoDelNegocio.ComprobanteRechazado;
import pe.agroruta.domain.model.HechoDelNegocio.EntregaRegistrada;
import pe.agroruta.domain.model.HechoDelNegocio.PagoRecibido;

/** Lo que se mira cada cierto tiempo para avisar: lo que acaba de pasar y lo que se quedó sin atender. */
public interface RevisionRepositoryPort {

    List<EntregaRegistrada> entregasDesde(Instant desde);

    List<PagoRecibido> pagosAprobadosDesde(Instant desde);

    List<AlertaDeEnvio> alertasDesde(Instant desde);

    List<ComprobanteRechazado> comprobantesRechazadosDesde(Instant desde);

    List<ComprobanteAtascado> comprobantesAtascados(Instant creadosAntesDe, Instant ahora);

    List<CobroVencido> cobrosVencidos(Instant entregadosAntesDe, Instant ahora);
}
