package pe.agroruta.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Todo lo que lleva un reporte exportado. {@code nombreDestino} es nulo si no se filtró por destino. */
public record Reporte(String negocio, String generadoPor, Instant generadoEn, String nombreDestino, Resumen resumen,
                      List<PuntoSerie> serie, Map<Dimension, List<FilaRanking>> rankings,
                      List<FilaEntrega> entregas) {
}
