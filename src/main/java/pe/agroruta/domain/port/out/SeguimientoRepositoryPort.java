package pe.agroruta.domain.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import pe.agroruta.domain.model.AlertaEnvio;
import pe.agroruta.domain.model.Posicion;
import pe.agroruta.domain.model.TipoAlerta;

public interface SeguimientoRepositoryPort {

    /** Guarda la posición. Devuelve vacío si ya existía (mismo envío y misma fecha): así reenviar no duplica. */
    Optional<Posicion> guardarPosicion(Posicion posicion);

    /** Posiciones desde una fecha, de la más antigua a la más reciente. */
    List<Posicion> posicionesDesde(Long idEnvio, Instant desde);

    /** Las últimas N posiciones, de la más antigua a la más reciente. */
    List<Posicion> ultimasPosiciones(Long idEnvio, int cantidad);

    AlertaEnvio guardarAlerta(AlertaEnvio alerta);

    boolean existeAlertaDesde(Long idEnvio, TipoAlerta tipoAlerta, Instant desde);

    List<AlertaEnvio> alertas(Long idEnvio);

    void registrarConsentimiento(Long idEnvio, Instant fecha);

    boolean tieneConsentimiento(Long idEnvio);
}
