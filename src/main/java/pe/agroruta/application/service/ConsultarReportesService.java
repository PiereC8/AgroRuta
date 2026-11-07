package pe.agroruta.application.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import pe.agroruta.domain.model.AnalisisFinanciero;
import pe.agroruta.domain.model.DestinoVenta;
import pe.agroruta.domain.model.Dimension;
import pe.agroruta.domain.model.Dinero;
import pe.agroruta.domain.model.DominioException;
import pe.agroruta.domain.model.FilaEntrega;
import pe.agroruta.domain.model.FilaRanking;
import pe.agroruta.domain.model.FiltroReporte;
import pe.agroruta.domain.model.Indicadores;
import pe.agroruta.domain.model.OpcionesFiltro;
import pe.agroruta.domain.model.PuntoSerie;
import pe.agroruta.domain.model.Resumen;
import pe.agroruta.domain.model.Veredicto;
import pe.agroruta.domain.port.in.ConsultarReportesUseCase;
import pe.agroruta.domain.port.out.DestinoRepositoryPort;
import pe.agroruta.domain.port.out.ReporteRepositoryPort;

public class ConsultarReportesService implements ConsultarReportesUseCase {

    static final int LIMITE_DE_ENTREGAS = 20_000;
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final Locale ES = Locale.forLanguageTag("es-PE");
    private static final int MAXIMO_DIAS_POR_DIA = 31;
    private static final int MAXIMO_DIAS_POR_SEMANA = 120;

    private final ReporteRepositoryPort reportes;
    private final DestinoRepositoryPort destinos;

    public ConsultarReportesService(ReporteRepositoryPort reportes, DestinoRepositoryPort destinos) {
        this.reportes = reportes;
        this.destinos = destinos;
    }

    @Override
    public Resumen resumen(FiltroReporte filtro) {
        Indicadores actual = Indicadores.de(entregas(filtro));
        Indicadores anterior = Indicadores.de(entregas(filtro.periodoAnterior()));
        Veredicto veredicto = AnalisisFinanciero.veredicto(actual, anterior);
        return new Resumen(filtro, actual, anterior, veredicto,
                AnalisisFinanciero.explicacion(veredicto, actual, anterior),
                AnalisisFinanciero.comparar(actual, anterior));
    }

    @Override
    public List<FilaEntrega> entregas(FiltroReporte filtro) {
        List<FilaEntrega> filas = reportes.entregas(filtro, LIMITE_DE_ENTREGAS);
        if (filas.size() >= LIMITE_DE_ENTREGAS) {
            throw new DominioException("Hay demasiadas entregas en este período. Acota el período o usa un filtro");
        }
        return filas;
    }

    @Override
    public List<PuntoSerie> serie(FiltroReporte filtro) {
        long dias = filtro.dias();
        Function<LocalDate, LocalDate> inicioDelTramo;
        Function<LocalDate, LocalDate> siguiente;
        DateTimeFormatter formato;
        if (dias <= MAXIMO_DIAS_POR_DIA) {
            inicioDelTramo = dia -> dia;
            siguiente = dia -> dia.plusDays(1);
            formato = DateTimeFormatter.ofPattern("dd MMM", ES);
        } else if (dias <= MAXIMO_DIAS_POR_SEMANA) {
            inicioDelTramo = dia -> dia.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            siguiente = dia -> dia.plusWeeks(1);
            formato = DateTimeFormatter.ofPattern("dd MMM", ES);
        } else {
            inicioDelTramo = dia -> dia.withDayOfMonth(1);
            siguiente = dia -> dia.plusMonths(1);
            formato = DateTimeFormatter.ofPattern("MMM yyyy", ES);
        }
        Map<LocalDate, List<FilaEntrega>> porTramo = new LinkedHashMap<>();
        for (LocalDate tramo = inicioDelTramo.apply(filtro.desde()); !tramo.isAfter(filtro.hasta());
             tramo = siguiente.apply(tramo)) {
            porTramo.put(tramo, new ArrayList<>());
        }
        for (FilaEntrega fila : entregas(filtro)) {
            LocalDate diaLocal = fila.fechaEntrega().atZone(LIMA).toLocalDate();
            List<FilaEntrega> destino = porTramo.get(inicioDelTramo.apply(diaLocal));
            if (destino != null) {
                destino.add(fila);
            }
        }
        List<PuntoSerie> serie = new ArrayList<>();
        porTramo.forEach((inicio, filas) -> {
            Indicadores totales = Indicadores.de(filas);
            serie.add(new PuntoSerie(inicio, formato.format(inicio).replace(".", ""), totales.gananciaReal(),
                    totales.ingresoReal()));
        });
        return serie;
    }

    @Override
    public List<FilaRanking> ranking(FiltroReporte filtro, Dimension dimension) {
        Function<FilaEntrega, String> clave = switch (dimension) {
            case DESTINO -> FilaEntrega::destino;
            case PRODUCTO -> FilaEntrega::producto;
            case COMPRADOR -> FilaEntrega::comprador;
        };
        Map<String, List<FilaEntrega>> grupos = new LinkedHashMap<>();
        for (FilaEntrega fila : entregas(filtro)) {
            grupos.computeIfAbsent(clave.apply(fila), k -> new ArrayList<>()).add(fila);
        }
        List<FilaRanking> ranking = new ArrayList<>();
        grupos.forEach((nombre, filas) -> {
            Indicadores totales = Indicadores.de(filas);
            ranking.add(new FilaRanking(nombre, filas.size(), totales.gananciaReal(), margen(totales),
                    totales.mermaKg()));
        });
        ranking.sort(Comparator.comparing((FilaRanking f) -> f.ganancia().monto()).reversed()
                .thenComparing(FilaRanking::nombre));
        return ranking;
    }

    private static BigDecimal margen(Indicadores totales) {
        Dinero ingreso = totales.ingresoReal();
        return ingreso.monto().signum() == 0 ? null
                : totales.gananciaReal().monto().multiply(BigDecimal.valueOf(100))
                        .divide(ingreso.monto(), 1, RoundingMode.HALF_UP);
    }

    @Override
    public OpcionesFiltro opciones() {
        List<OpcionesFiltro.OpcionDestino> opcionesDeDestino = destinos.listarActivos().stream()
                .map((DestinoVenta d) -> new OpcionesFiltro.OpcionDestino(d.idDestinoVenta(), d.nombreDestino()))
                .toList();
        return new OpcionesFiltro(reportes.productosConEntregas(), opcionesDeDestino);
    }
}
