package pe.agroruta.domain.model;

import java.math.BigDecimal;
import java.time.Instant;

/** Un envío programado o en ruta, tal como se muestra en el panel de inicio. */
public record EnvioEnCurso(Long idEnvio, EstadoEnvio estadoEnvio, String destino, String comprador,
                           BigDecimal cantidadKg, Instant fechaProgramada) {
}
