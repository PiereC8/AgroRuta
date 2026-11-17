package pe.agroruta.domain.model;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

// RFF-18 (Panel de inicio): ganancia del mes, merma, envíos en curso y cobros pendientes.
public record Panel(YearMonth mes, Dinero gananciaMes, Dinero gananciaMesAnterior, BigDecimal mermaMesKg,
                    BigDecimal mermaTotalKg, Dinero perdidaMermaMes, List<EnvioEnCurso> enviosEnCurso,
                    CobrosPendientes cobrosPendientes, List<GananciaDelMes> gananciaUltimosMeses) {
}
