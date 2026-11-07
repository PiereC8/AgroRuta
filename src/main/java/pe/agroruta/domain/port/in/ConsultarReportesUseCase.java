package pe.agroruta.domain.port.in;

import java.util.List;

import pe.agroruta.domain.model.Dimension;
import pe.agroruta.domain.model.FilaEntrega;
import pe.agroruta.domain.model.FilaRanking;
import pe.agroruta.domain.model.FiltroReporte;
import pe.agroruta.domain.model.OpcionesFiltro;
import pe.agroruta.domain.model.PuntoSerie;
import pe.agroruta.domain.model.Resumen;

public interface ConsultarReportesUseCase {

    Resumen resumen(FiltroReporte filtro);

    /** Ganancia por día (hasta 31 días), por semana (hasta 120 días) o por mes, sin saltarse los tramos vacíos. */
    List<PuntoSerie> serie(FiltroReporte filtro);

    List<FilaRanking> ranking(FiltroReporte filtro, Dimension dimension);

    List<FilaEntrega> entregas(FiltroReporte filtro);

    OpcionesFiltro opciones();
}
