package pe.agroruta.infrastructure.adapter.in.web;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import pe.agroruta.domain.model.AnalisisFinanciero;
import pe.agroruta.domain.model.Archivo;
import pe.agroruta.domain.model.Causa;
import pe.agroruta.domain.model.Comparacion;
import pe.agroruta.domain.model.Dimension;
import pe.agroruta.domain.model.DominioException;
import pe.agroruta.domain.model.FilaRanking;
import pe.agroruta.domain.model.Formato;
import pe.agroruta.domain.model.FiltroReporte;
import pe.agroruta.domain.model.Indicadores;
import pe.agroruta.domain.model.OpcionesFiltro;
import pe.agroruta.domain.model.PuntoSerie;
import pe.agroruta.domain.model.Resumen;
import pe.agroruta.domain.model.Veredicto;
import pe.agroruta.domain.port.in.ConsultarReportesUseCase;
import pe.agroruta.domain.port.in.ExportarReporteUseCase;
import pe.agroruta.infrastructure.adapter.out.seguridad.ServicioJwt.DatosToken;

/** Reportes financieros. Solo el Dueño llega aquí (lo exige la configuración de seguridad). */
@RestController
@RequestMapping("/api/reportes")
public class ReportesController {

    private static final int DIAS_POR_DEFECTO = 30;

    private final ConsultarReportesUseCase consultas;
    private final ExportarReporteUseCase exportar;
    private final Clock reloj;

    public ReportesController(ConsultarReportesUseCase consultas, ExportarReporteUseCase exportar, Clock reloj) {
        this.consultas = consultas;
        this.exportar = exportar;
        this.reloj = reloj;
    }

    @GetMapping("/resumen")
    public ResumenRespuesta resumen(@RequestParam(required = false) String desde,
                                    @RequestParam(required = false) String hasta,
                                    @RequestParam(required = false) String producto,
                                    @RequestParam(required = false) Long destino) {
        return ResumenRespuesta.de(consultas.resumen(filtro(desde, hasta, producto, destino)));
    }

    @GetMapping("/serie")
    public List<PuntoRespuesta> serie(@RequestParam(required = false) String desde,
                                      @RequestParam(required = false) String hasta,
                                      @RequestParam(required = false) String producto,
                                      @RequestParam(required = false) Long destino) {
        return consultas.serie(filtro(desde, hasta, producto, destino)).stream().map(PuntoRespuesta::de).toList();
    }

    @GetMapping("/ranking")
    public List<RankingRespuesta> ranking(@RequestParam String dimension,
                                          @RequestParam(required = false) String desde,
                                          @RequestParam(required = false) String hasta,
                                          @RequestParam(required = false) String producto,
                                          @RequestParam(required = false) Long destino) {
        Dimension elegida;
        try {
            elegida = Dimension.valueOf(dimension.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new DominioException("El ranking puede ser por destino, producto o comprador");
        }
        return consultas.ranking(filtro(desde, hasta, producto, destino), elegida).stream()
                .map(RankingRespuesta::de).toList();
    }

    @GetMapping("/opciones")
    public OpcionesFiltro opciones() {
        return consultas.opciones();
    }

    @GetMapping("/exportar")
    public ResponseEntity<byte[]> exportar(@AuthenticationPrincipal DatosToken usuario,
                                           @RequestParam String formato,
                                           @RequestParam(required = false) String desde,
                                           @RequestParam(required = false) String hasta,
                                           @RequestParam(required = false) String producto,
                                           @RequestParam(required = false) Long destino) {
        Formato elegido;
        try {
            elegido = Formato.valueOf(formato.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new DominioException("El reporte se puede exportar en CSV o PDF");
        }
        Archivo archivo = exportar.exportar(filtro(desde, hasta, producto, destino), elegido,
                usuario.nombreUsuario(), usuario.identificadorNegocio());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(archivo.nombre()).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.parseMediaType(archivo.tipoContenido()))
                .body(archivo.contenido());
    }

    private FiltroReporte filtro(String desde, String hasta, String producto, Long destino) {
        if (desde == null && hasta == null) {
            FiltroReporte base = FiltroReporte.ultimosDias(DIAS_POR_DEFECTO, LocalDate.now(reloj));
            return new FiltroReporte(base.desde(), base.hasta(), producto, destino);
        }
        return new FiltroReporte(fecha(desde, "desde"), fecha(hasta, "hasta"), producto, destino);
    }

    private static LocalDate fecha(String texto, String campo) {
        if (texto == null || texto.isBlank()) {
            throw new DominioException("Indica desde qué día y hasta qué día quieres el reporte");
        }
        try {
            return LocalDate.parse(texto);
        } catch (DateTimeParseException e) {
            throw new DominioException("La fecha '" + campo + "' debe tener el formato AAAA-MM-DD");
        }
    }

    public record IndicadoresRespuesta(int entregas, BigDecimal kgDespachados, BigDecimal kgRecibidos,
                                       BigDecimal mermaKg, BigDecimal rechazoKg, BigDecimal ingresoReal,
                                       BigDecimal fleteTotal, BigDecimal gananciaReal, BigDecimal gananciaEsperada,
                                       BigDecimal perdidaMermaRechazo, BigDecimal cobrado, BigDecimal porCobrar,
                                       BigDecimal margenPorcentaje) {
        static IndicadoresRespuesta de(Indicadores i) {
            return new IndicadoresRespuesta(i.entregas(), i.kgDespachados(), i.kgRecibidos(), i.mermaKg(),
                    i.rechazoKg(), i.ingresoReal().monto(), i.fleteTotal().monto(), i.gananciaReal().monto(),
                    i.gananciaEsperada().monto(), i.perdidaMermaRechazo().monto(), i.cobrado().monto(),
                    i.porCobrar().monto(), i.margenPorcentaje());
        }
    }

    public record CausaRespuesta(Causa.Concepto concepto, BigDecimal efecto) {
    }

    public record ResumenRespuesta(LocalDate desde, LocalDate hasta, LocalDate anteriorDesde, LocalDate anteriorHasta,
                                   Veredicto veredicto, String explicacion, IndicadoresRespuesta actual,
                                   IndicadoresRespuesta anterior, List<Comparacion> comparaciones,
                                   List<CausaRespuesta> causas) {
        static ResumenRespuesta de(Resumen r) {
            FiltroReporte anterior = r.filtro().periodoAnterior();
            List<CausaRespuesta> causas = AnalisisFinanciero.causas(r.actual(), r.anterior()).stream()
                    .map(c -> new CausaRespuesta(c.concepto(), c.efecto().monto())).toList();
            return new ResumenRespuesta(r.filtro().desde(), r.filtro().hasta(), anterior.desde(), anterior.hasta(),
                    r.veredicto(), r.explicacion(), IndicadoresRespuesta.de(r.actual()),
                    IndicadoresRespuesta.de(r.anterior()), r.comparaciones(), causas);
        }
    }

    public record PuntoRespuesta(LocalDate inicio, String etiqueta, BigDecimal ganancia, BigDecimal ingreso) {
        static PuntoRespuesta de(PuntoSerie p) {
            return new PuntoRespuesta(p.inicio(), p.etiqueta(), p.ganancia().monto(), p.ingreso().monto());
        }
    }

    public record RankingRespuesta(String nombre, int entregas, BigDecimal ganancia, BigDecimal margenPorcentaje,
                                   BigDecimal mermaKg) {
        static RankingRespuesta de(FilaRanking f) {
            return new RankingRespuesta(f.nombre(), f.entregas(), f.ganancia().monto(), f.margenPorcentaje(),
                    f.mermaKg());
        }
    }
}
