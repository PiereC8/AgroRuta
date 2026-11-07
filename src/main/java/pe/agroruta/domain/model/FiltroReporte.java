package pe.agroruta.domain.model;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

/** Qué entregas entran en un reporte: un rango de días (ambos incluidos) y, si se quiere, un producto o un destino. */
public record FiltroReporte(LocalDate desde, LocalDate hasta, String producto, Long idDestino) {

    public static final int MAXIMO_DIAS = 366;

    public FiltroReporte {
        if (desde == null || hasta == null) {
            throw new DominioException("Indica desde qué día y hasta qué día quieres el reporte");
        }
        if (desde.isAfter(hasta)) {
            throw new DominioException("La fecha de inicio debe ir antes de la fecha final");
        }
        if (ChronoUnit.DAYS.between(desde, hasta) + 1 > MAXIMO_DIAS) {
            throw new DominioException("El reporte puede cubrir como máximo " + MAXIMO_DIAS + " días");
        }
        producto = producto == null || producto.isBlank() ? null : producto.strip();
        if (producto != null && producto.length() > 80) {
            throw new DominioException("El nombre del producto no puede pasar de 80 caracteres");
        }
    }

    public static FiltroReporte ultimosDias(int dias, LocalDate hoy) {
        return new FiltroReporte(hoy.minusDays(dias - 1L), hoy, null, null);
    }

    public static FiltroReporte esteMes(LocalDate hoy) {
        return new FiltroReporte(hoy.withDayOfMonth(1), hoy, null, null);
    }

    public static FiltroReporte mesPasado(LocalDate hoy) {
        YearMonth mes = YearMonth.from(hoy).minusMonths(1);
        return new FiltroReporte(mes.atDay(1), mes.atEndOfMonth(), null, null);
    }

    public static FiltroReporte esteAnio(LocalDate hoy) {
        return new FiltroReporte(hoy.withDayOfYear(1), hoy, null, null);
    }

    public long dias() {
        return ChronoUnit.DAYS.between(desde, hasta) + 1;
    }

    /**
     * Con qué se compara este período: un mes (o lo que va del mes) con el mismo tramo del mes anterior, lo que va
     * del año con el mismo tramo del año anterior, y cualquier otro rango con los días inmediatamente anteriores.
     */
    public FiltroReporte periodoAnterior() {
        YearMonth mes = YearMonth.from(desde);
        if (desde.getDayOfMonth() == 1 && YearMonth.from(hasta).equals(mes)) {
            YearMonth previo = mes.minusMonths(1);
            LocalDate fin = hasta.equals(mes.atEndOfMonth())
                    ? previo.atEndOfMonth()
                    : previo.atDay(Math.min(hasta.getDayOfMonth(), previo.lengthOfMonth()));
            return new FiltroReporte(previo.atDay(1), fin, producto, idDestino);
        }
        if (desde.getDayOfYear() == 1 && hasta.getYear() == desde.getYear()) {
            return new FiltroReporte(desde.minusYears(1), hasta.minusYears(1), producto, idDestino);
        }
        LocalDate finAnterior = desde.minusDays(1);
        return new FiltroReporte(finAnterior.minusDays(dias() - 1), finAnterior, producto, idDestino);
    }
}
