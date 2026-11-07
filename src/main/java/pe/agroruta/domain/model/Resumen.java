package pe.agroruta.domain.model;

import java.util.List;

/** El período elegido, el anterior con el que se compara, y lo que se concluye de los dos. */
public record Resumen(FiltroReporte filtro, Indicadores actual, Indicadores anterior, Veredicto veredicto,
                      String explicacion, List<Comparacion> comparaciones) {
}
