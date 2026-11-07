package pe.agroruta.application.service;

import java.time.Clock;
import java.util.Map;

import pe.agroruta.domain.model.Archivo;
import pe.agroruta.domain.model.DominioException;
import pe.agroruta.domain.model.Formato;
import pe.agroruta.domain.model.FiltroReporte;
import pe.agroruta.domain.model.Negocio;
import pe.agroruta.domain.model.Reporte;
import pe.agroruta.domain.port.in.ConsultarReportesUseCase;
import pe.agroruta.domain.port.in.ExportarReporteUseCase;
import pe.agroruta.domain.port.out.ExportadorReportePort;
import pe.agroruta.domain.port.out.NegocioRepositoryPort;

public class ExportarReporteService implements ExportarReporteUseCase {

    private final ConsultarReportesUseCase consultas;
    private final NegocioRepositoryPort negocios;
    private final Map<Formato, ExportadorReportePort> exportadores;
    private final Clock reloj;

    public ExportarReporteService(ConsultarReportesUseCase consultas, NegocioRepositoryPort negocios,
                                  Map<Formato, ExportadorReportePort> exportadores, Clock reloj) {
        this.consultas = consultas;
        this.negocios = negocios;
        this.exportadores = exportadores;
        this.reloj = reloj;
    }

    @Override
    public Archivo exportar(FiltroReporte filtro, Formato formato, String usuario, String identificadorNegocio) {
        ExportadorReportePort exportador = exportadores.get(formato);
        if (exportador == null) {
            throw new DominioException("No se puede exportar en el formato " + formato);
        }
        String nombreNegocio = negocios.buscarActivoPorIdentificador(identificadorNegocio)
                .map(Negocio::nombreNegocio).orElse(identificadorNegocio);
        Reporte reporte = consultas.completo(filtro, nombreNegocio, usuario, reloj.instant());
        String nombre = "reporte-agroruta-" + filtro.desde() + "_" + filtro.hasta() + "." + exportador.extension();
        return new Archivo(nombre, exportador.tipoContenido(), exportador.exportar(reporte));
    }
}
