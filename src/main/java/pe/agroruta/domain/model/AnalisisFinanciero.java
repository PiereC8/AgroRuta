package pe.agroruta.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Reglas del reporte del Dueño. Ganancia real = ingreso real - flete, y el ingreso real ya descuenta lo perdido por
 * merma y rechazo; por eso el cambio de la ganancia se explica exactamente con tres causas:
 * lo vendido (ingreso + pérdida), la merma y el flete.
 */
public final class AnalisisFinanciero {

    private AnalisisFinanciero() {
    }

    public static Veredicto veredicto(Indicadores actual, Indicadores anterior) {
        if (actual.entregas() == 0) {
            return Veredicto.SIN_DATOS;
        }
        if (actual.gananciaReal().monto().signum() <= 0) {
            return Veredicto.PERDIENDO;
        }
        boolean hayAnterior = anterior.entregas() > 0 && anterior.gananciaReal().esPositivo();
        if (hayAnterior && actual.gananciaReal().compareTo(anterior.gananciaReal()) < 0) {
            return Veredicto.GANANDO_MENOS;
        }
        return Veredicto.GANANDO;
    }

    public static List<Comparacion> comparar(Indicadores actual, Indicadores anterior) {
        List<Comparacion> lista = new ArrayList<>();
        lista.add(comparacion("Entregas", Unidad.CANTIDAD, BigDecimal.valueOf(actual.entregas()),
                BigDecimal.valueOf(anterior.entregas()), true));
        lista.add(comparacion("Kilos recibidos", Unidad.KILOS, actual.kgRecibidos(), anterior.kgRecibidos(), true));
        lista.add(comparacion("Ingreso real", Unidad.SOLES, actual.ingresoReal().monto(),
                anterior.ingresoReal().monto(), true));
        lista.add(comparacion("Flete", Unidad.SOLES, actual.fleteTotal().monto(), anterior.fleteTotal().monto(),
                false));
        lista.add(comparacion("Merma y rechazo", Unidad.SOLES, actual.perdidaMermaRechazo().monto(),
                anterior.perdidaMermaRechazo().monto(), false));
        lista.add(comparacion("Ganancia real", Unidad.SOLES, actual.gananciaReal().monto(),
                anterior.gananciaReal().monto(), true));
        return lista;
    }

    private static Comparacion comparacion(String concepto, Unidad unidad, BigDecimal actual, BigDecimal anterior,
                                           boolean masEsMejor) {
        BigDecimal diferencia = actual.subtract(anterior);
        BigDecimal porcentaje = anterior.signum() == 0 ? null
                : diferencia.multiply(BigDecimal.valueOf(100)).divide(anterior.abs(), 1, RoundingMode.HALF_UP);
        Tendencia tendencia = diferencia.signum() == 0 ? Tendencia.IGUAL
                : (diferencia.signum() > 0) == masEsMejor ? Tendencia.MEJORA : Tendencia.EMPEORA;
        return new Comparacion(concepto, unidad, actual, anterior, diferencia, porcentaje, tendencia);
    }

    /** Qué sumó o restó cada causa al cambio de la ganancia, de la que más pesó a la que menos. */
    public static List<Causa> causas(Indicadores actual, Indicadores anterior) {
        Dinero ventasActual = actual.ingresoReal().sumar(actual.perdidaMermaRechazo());
        Dinero ventasAnterior = anterior.ingresoReal().sumar(anterior.perdidaMermaRechazo());
        List<Causa> causas = new ArrayList<>(List.of(
                new Causa(Causa.Concepto.VENTAS, ventasActual.restar(ventasAnterior)),
                new Causa(Causa.Concepto.MERMA, anterior.perdidaMermaRechazo().restar(actual.perdidaMermaRechazo())),
                new Causa(Causa.Concepto.FLETE, anterior.fleteTotal().restar(actual.fleteTotal()))));
        causas.sort(Comparator.comparing((Causa c) -> c.efecto().monto().abs()).reversed());
        return causas;
    }

    public static String explicacion(Veredicto veredicto, Indicadores actual, Indicadores anterior) {
        if (veredicto == Veredicto.SIN_DATOS) {
            return "No hay entregas en este período.";
        }
        Dinero ganancia = actual.gananciaReal();
        String base = resultado(ganancia, "No ganaste nada", "Ganaste ", "Perdiste ") + " en este período.";
        if (anterior.entregas() == 0) {
            return base + " No hay entregas en el período anterior para comparar.";
        }
        // Cada monto dice qué es: la ganancia de ahora, la diferencia y la ganancia de antes.
        String antes = ", cuando " + resultado(anterior.gananciaReal(), "no ganaste nada", "ganaste ", "perdiste ")
                + ".";
        Dinero cambio = ganancia.restar(anterior.gananciaReal());
        if (cambio.monto().signum() == 0) {
            return base + " Es lo mismo que en el período anterior" + antes;
        }
        if (cambio.monto().signum() > 0) {
            return base + " Es " + soles(cambio) + " más de ganancia que en el período anterior" + antes;
        }
        String motivos = motivos(causas(actual, anterior));
        return base + " Es " + soles(new Dinero(cambio.monto().negate()))
                + " menos de ganancia que en el período anterior" + antes
                + (motivos.isEmpty() ? "" : " Por qué: " + motivos + ".");
    }

    private static String resultado(Dinero monto, String cero, String gano, String perdio) {
        if (monto.monto().signum() == 0) {
            return cero;
        }
        return monto.esNegativo() ? perdio + soles(new Dinero(monto.monto().negate())) : gano + soles(monto);
    }

    /** Las dos causas que más restaron, por ejemplo "se vendió S/ 450.00 menos y el flete costó S/ 400.00 más". */
    private static String motivos(List<Causa> causas) {
        List<String> frases = causas.stream().filter(c -> c.efecto().esNegativo()).limit(2).map(c -> {
            Dinero monto = new Dinero(c.efecto().monto().negate());
            return switch (c.concepto()) {
                case VENTAS -> "se vendió " + soles(monto) + " menos";
                case MERMA -> "la merma costó " + soles(monto) + " más";
                case FLETE -> "el flete costó " + soles(monto) + " más";
            };
        }).toList();
        return String.join(" y ", frases);
    }

    private static String soles(Dinero dinero) {
        return String.format(Locale.US, "S/ %,.2f", dinero.monto());
    }
}
