package pe.agroruta.domain.model;

import java.time.Instant;

// RFF-05: una línea del historial del lote (registro, reserva, despacho, entrega...).
public record EventoLote(TipoEventoLote tipoEvento, String descripcionEvento, String usuarioEvento,
                         Instant fechaEvento) {
}
