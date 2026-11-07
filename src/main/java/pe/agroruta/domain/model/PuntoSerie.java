package pe.agroruta.domain.model;

import java.time.LocalDate;

/** Un tramo del gráfico de ganancia: un día, una semana (desde el lunes) o un mes (desde el día 1). */
public record PuntoSerie(LocalDate inicio, String etiqueta, Dinero ganancia, Dinero ingreso) {
}
