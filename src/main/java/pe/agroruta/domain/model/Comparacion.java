package pe.agroruta.domain.model;

import java.math.BigDecimal;

/** Un indicador del período contra el anterior. El porcentaje es nulo si el anterior fue cero. */
public record Comparacion(String concepto, Unidad unidad, BigDecimal actual, BigDecimal anterior,
                          BigDecimal diferencia, BigDecimal porcentaje, Tendencia tendencia) {
}
