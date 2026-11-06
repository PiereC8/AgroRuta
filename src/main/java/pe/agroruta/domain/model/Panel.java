package pe.agroruta.domain.model;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

public record Panel(YearMonth mes, Dinero gananciaMes, Dinero gananciaMesAnterior, BigDecimal mermaMesKg,
                    BigDecimal mermaTotalKg, Dinero perdidaMermaMes, List<EnvioEnCurso> enviosEnCurso,
                    CobrosPendientes cobrosPendientes, List<GananciaDelMes> gananciaUltimosMeses) {
}
