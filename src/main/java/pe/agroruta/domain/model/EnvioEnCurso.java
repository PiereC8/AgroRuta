package pe.agroruta.domain.model;

import java.math.BigDecimal;
import java.time.Instant;

public record EnvioEnCurso(Long idEnvio, EstadoEnvio estadoEnvio, String destino, String comprador,
                           BigDecimal cantidadKg, Instant fechaProgramada) {
}
