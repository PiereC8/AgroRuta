package pe.agroruta.domain.port.out;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import pe.agroruta.domain.model.CobrosPendientes;
import pe.agroruta.domain.model.Dinero;
import pe.agroruta.domain.model.EnvioEnCurso;

/** Consultas de solo lectura para el panel. Los intervalos incluyen el inicio y excluyen el final. */
public interface PanelRepositoryPort {

    Dinero gananciaEntre(Instant desde, Instant hasta);

    BigDecimal mermaKgEntre(Instant desde, Instant hasta);

    Dinero perdidaEntre(Instant desde, Instant hasta);

    List<EnvioEnCurso> enviosEnCurso(int limite);

    CobrosPendientes cobrosPendientes();
}
