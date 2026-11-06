package pe.agroruta.domain.model;

import java.time.Instant;
import java.util.List;

/**
 * Estado del seguimiento de un envío. {@code coordenadaDestino}, {@code ultimaPosicion} y {@code etaMinutos}
 * son nulos cuando todavía no se conocen.
 */
public record Seguimiento(Envio envio, String nombreDestino, String ciudadDestino, Coordenada coordenadaDestino,
                          Posicion ultimaPosicion, Long etaMinutos, Instant ahora, List<Posicion> trazoReciente) {
}
