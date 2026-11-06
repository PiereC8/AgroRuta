package pe.agroruta.domain.model;

import java.time.Instant;
import java.util.List;

public record Seguimiento(Envio envio, String nombreDestino, String ciudadDestino, Coordenada coordenadaDestino,
                          Posicion ultimaPosicion, Long etaMinutos, Instant ahora, List<Posicion> trazoReciente) {
}
