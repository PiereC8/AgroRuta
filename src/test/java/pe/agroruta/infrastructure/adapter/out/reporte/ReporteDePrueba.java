package pe.agroruta.infrastructure.adapter.out.reporte;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import pe.agroruta.domain.model.AnalisisFinanciero;
import pe.agroruta.domain.model.Dimension;
import pe.agroruta.domain.model.Dinero;
import pe.agroruta.domain.model.FilaEntrega;
import pe.agroruta.domain.model.FilaRanking;
import pe.agroruta.domain.model.FiltroReporte;
import pe.agroruta.domain.model.Indicadores;
import pe.agroruta.domain.model.PuntoSerie;
import pe.agroruta.domain.model.Reporte;
import pe.agroruta.domain.model.Resumen;
import pe.agroruta.domain.model.Veredicto;

/** Arma reportes de ejemplo para probar los exportadores. */
final class ReporteDePrueba {

    private ReporteDePrueba() {
    }

    static FilaEntrega entrega(int numero, String comprador, String ingreso, String flete) {
        Dinero ingresoReal = Dinero.de(ingreso);
        Dinero fleteTotal = Dinero.de(flete);
        Dinero ganancia = ingresoReal.restar(fleteTotal);
        return new FilaEntrega((long) numero, (long) numero, Instant.parse("2026-11-05T15:30:00Z"), comprador,
                "Palta Hass", "Mercado Lima", new BigDecimal("100.000"), new BigDecimal("94.500"),
                new BigDecimal("3.500"), new BigDecimal("2.000"), ingresoReal, fleteTotal, ganancia,
                ganancia.sumar(Dinero.de("30.00")), Dinero.de("30.00"), numero % 2 == 0);
    }

    static Reporte reporte(List<FilaEntrega> entregas) {
        FiltroReporte filtro = new FiltroReporte(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 7), null, null);
        Indicadores actual = Indicadores.de(entregas);
        Indicadores anterior = Indicadores.de(entregas.stream().limit(Math.max(0, entregas.size() / 2)).toList());
        Veredicto veredicto = AnalisisFinanciero.veredicto(actual, anterior);
        Resumen resumen = new Resumen(filtro, actual, anterior, veredicto,
                AnalisisFinanciero.explicacion(veredicto, actual, anterior),
                AnalisisFinanciero.comparar(actual, anterior));
        List<PuntoSerie> serie = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            serie.add(new PuntoSerie(filtro.desde().plusDays(i), String.format("%02d nov", i + 1),
                    i == 3 ? Dinero.de("-120.00") : Dinero.de(String.valueOf(100 * (i + 1) + ".00")),
                    Dinero.de("900.00")));
        }
        Map<Dimension, List<FilaRanking>> rankings = new EnumMap<>(Dimension.class);
        for (Dimension d : Dimension.values()) {
            rankings.put(d, List.of(new FilaRanking("Mercado Lima", entregas.size(), actual.gananciaReal(),
                    new BigDecimal("55.5"), actual.mermaKg())));
        }
        return new Reporte("Empresa Demo SAC", "dueno.empresa", Instant.parse("2026-11-07T13:15:00Z"), null,
                resumen, serie, rankings, entregas);
    }

    static List<FilaEntrega> variasEntregas(int cantidad) {
        List<FilaEntrega> lista = new ArrayList<>();
        for (int i = 1; i <= cantidad; i++) {
            lista.add(entrega(i, "Comprador " + i, "1000.00", "200.00"));
        }
        return lista;
    }
}
