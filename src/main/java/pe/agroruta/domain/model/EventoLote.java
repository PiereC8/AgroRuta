package pe.agroruta.domain.model;

import java.time.Instant;

public record EventoLote(TipoEventoLote tipoEvento, String descripcionEvento, String usuarioEvento,
                         Instant fechaEvento) {
}
