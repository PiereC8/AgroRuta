package pe.agroruta.domain.model;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

/**
 * Resumen del panel de inicio. La ganancia (del mes, del mes anterior y la serie) es nula cuando quien consulta
 * no es el Dueño.
 */
public record Panel(YearMonth mes, Dinero gananciaMes, Dinero gananciaMesAnterior, BigDecimal mermaMesKg,
                    BigDecimal mermaTotalKg, Dinero perdidaMermaMes, List<EnvioEnCurso> enviosEnCurso,
                    CobrosPendientes cobrosPendientes, List<GananciaDelMes> gananciaUltimosMeses) {
}
