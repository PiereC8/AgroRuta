package pe.agroruta.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.function.Function;

/** Los números de un período. La pérdida por merma y rechazo es lo que se dejó de ganar frente a lo esperado. */
public record Indicadores(int entregas, BigDecimal kgDespachados, BigDecimal kgRecibidos, BigDecimal mermaKg,
                          BigDecimal rechazoKg, Dinero ingresoReal, Dinero fleteTotal, Dinero gananciaReal,
                          Dinero gananciaEsperada, Dinero perdidaMermaRechazo, Dinero cobrado, Dinero porCobrar) {

    public static Indicadores de(List<FilaEntrega> filas) {
        return new Indicadores(filas.size(), kilos(filas, FilaEntrega::kgDespachados),
                kilos(filas, FilaEntrega::kgRecibidos), kilos(filas, FilaEntrega::mermaKg),
                kilos(filas, FilaEntrega::rechazoKg), dinero(filas, FilaEntrega::ingresoReal),
                dinero(filas, FilaEntrega::fleteTotal), dinero(filas, FilaEntrega::gananciaReal),
                dinero(filas, FilaEntrega::gananciaEsperada), dinero(filas, FilaEntrega::perdidaMermaRechazo),
                dinero(filas.stream().filter(FilaEntrega::cobrada).toList(), FilaEntrega::ingresoReal),
                dinero(filas.stream().filter(f -> !f.cobrada()).toList(), FilaEntrega::ingresoReal));
    }

    /** Ganancia sobre ingreso, con un decimal. Nulo si no hubo ingreso. */
    public BigDecimal margenPorcentaje() {
        if (ingresoReal.monto().signum() == 0) {
            return null;
        }
        return gananciaReal.monto().multiply(BigDecimal.valueOf(100))
                .divide(ingresoReal.monto(), 1, RoundingMode.HALF_UP);
    }

    private static BigDecimal kilos(List<FilaEntrega> filas, Function<FilaEntrega, BigDecimal> campo) {
        return filas.stream().map(campo).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static Dinero dinero(List<FilaEntrega> filas, Function<FilaEntrega, Dinero> campo) {
        return filas.stream().map(campo).reduce(Dinero.CERO, Dinero::sumar);
    }
}
