package pe.agroruta.application.service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.time.Duration;

import pe.agroruta.domain.model.FiltroReporte;
import pe.agroruta.domain.model.HechoDelNegocio.ResumenSemanal;
import pe.agroruta.domain.port.in.ConsultarReportesUseCase;
import pe.agroruta.domain.port.in.PublicarAvisosUseCase;
import pe.agroruta.domain.port.in.RevisarNegocioUseCase;
import pe.agroruta.domain.port.out.RevisionRepositoryPort;

public class RevisionNegocioService implements RevisarNegocioUseCase {

    private static final Duration VENTANA_RECHAZADOS = Duration.ofHours(48);

    private final PublicarAvisosUseCase avisos;
    private final RevisionRepositoryPort revision;
    private final ConsultarReportesUseCase reportes;
    private final Clock reloj;
    private final int horasComprobanteAtascado;
    private final int diasCobroVencido;

    public RevisionNegocioService(PublicarAvisosUseCase avisos, RevisionRepositoryPort revision,
                                  ConsultarReportesUseCase reportes, Clock reloj, int horasComprobanteAtascado,
                                  int diasCobroVencido) {
        this.avisos = avisos;
        this.revision = revision;
        this.reportes = reportes;
        this.reloj = reloj;
        this.horasComprobanteAtascado = horasComprobanteAtascado;
        this.diasCobroVencido = diasCobroVencido;
    }

    @Override
    public void revisarPendientes() {
        Instant ahora = reloj.instant();
        revision.comprobantesRechazadosDesde(ahora.minus(VENTANA_RECHAZADOS)).forEach(avisos::publicar);
        revision.comprobantesAtascados(ahora.minus(Duration.ofHours(horasComprobanteAtascado)), ahora)
                .forEach(avisos::publicar);
        revision.cobrosVencidos(ahora.minus(Duration.ofDays(diasCobroVencido)), ahora).forEach(avisos::publicar);
    }

    @Override
    public void enviarResumenSemanal() {
        LocalDate hoy = LocalDate.now(reloj);
        LocalDate lunes = hoy.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1);
        FiltroReporte semana = new FiltroReporte(lunes, lunes.plusDays(6), null, null);
        avisos.publicar(new ResumenSemanal(semana.desde(), semana.hasta(), reportes.resumen(semana)));
    }
}
