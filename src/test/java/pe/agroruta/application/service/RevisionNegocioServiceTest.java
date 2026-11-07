package pe.agroruta.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import pe.agroruta.application.service.AvisosServiceTest.BandejaEnMemoria;
import pe.agroruta.application.service.EquipoServiceTest.EquipoEnMemoria;
import pe.agroruta.domain.model.Dimension;
import pe.agroruta.domain.model.Dinero;
import pe.agroruta.domain.model.FilaEntrega;
import pe.agroruta.domain.model.FilaRanking;
import pe.agroruta.domain.model.FiltroReporte;
import pe.agroruta.domain.model.Indicadores;
import pe.agroruta.domain.model.OpcionesFiltro;
import pe.agroruta.domain.model.PuntoSerie;
import pe.agroruta.domain.model.Reporte;
import pe.agroruta.domain.model.Resumen;
import pe.agroruta.domain.model.Veredicto;
import pe.agroruta.domain.port.in.ConsultarReportesUseCase;
import pe.agroruta.domain.model.HechoDelNegocio.CobroVencido;
import pe.agroruta.domain.model.HechoDelNegocio.ComprobanteAtascado;
import pe.agroruta.domain.model.HechoDelNegocio.ComprobanteRechazado;
import pe.agroruta.domain.model.MiembroEquipo;
import pe.agroruta.domain.model.Notificacion;
import pe.agroruta.domain.model.PoliticaAvisos;
import pe.agroruta.domain.model.Rol;
import pe.agroruta.domain.model.TipoAviso;
import pe.agroruta.domain.port.out.RevisionRepositoryPort;

class RevisionNegocioServiceTest {

    static class RevisionFalsa implements RevisionRepositoryPort {
        final List<Instant> consultas = new ArrayList<>();

        @Override
        public List<ComprobanteRechazado> comprobantesRechazadosDesde(Instant desde) {
            consultas.add(desde);
            return List.of(new ComprobanteRechazado(3L, "B001-00000012", "RUC inválido"));
        }

        @Override
        public List<ComprobanteAtascado> comprobantesAtascados(Instant creadosAntesDe, Instant ahora) {
            consultas.add(creadosAntesDe);
            return List.of(new ComprobanteAtascado(5L, "B001-00000013", 30));
        }

        @Override
        public List<CobroVencido> cobrosVencidos(Instant entregadosAntesDe, Instant ahora) {
            consultas.add(entregadosAntesDe);
            return List.of(new CobroVencido(7L, "Ana Torres", Dinero.de("2400.00"), 9));
        }
    }

    static class ConsultasFalsas implements ConsultarReportesUseCase {
        FiltroReporte ultimoFiltro;

        @Override
        public Resumen resumen(FiltroReporte filtro) {
            ultimoFiltro = filtro;
            Indicadores vacios = Indicadores.de(List.of());
            return new Resumen(filtro, vacios, vacios, Veredicto.SIN_DATOS, "No hay entregas en este período.",
                    List.of());
        }

        @Override
        public List<PuntoSerie> serie(FiltroReporte filtro) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<FilaRanking> ranking(FiltroReporte filtro, Dimension dimension) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<FilaEntrega> entregas(FiltroReporte filtro) {
            throw new UnsupportedOperationException();
        }

        @Override
        public OpcionesFiltro opciones() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Reporte completo(FiltroReporte filtro, String negocio, String generadoPor, Instant ahora) {
            throw new UnsupportedOperationException();
        }
    }

    private static final Instant AHORA = Instant.parse("2026-11-09T12:00:00Z");

    private BandejaEnMemoria bandeja;
    private RevisionFalsa revision;
    private RevisionNegocioService servicio;
    private ConsultasFalsas consultas;

    @BeforeEach
    void preparar() {
        EquipoEnMemoria equipo = new EquipoEnMemoria();
        equipo.miembros.add(new MiembroEquipo(1L, "dueno.demo", Rol.DUENO, "dueno@empresa.pe", true, true));
        bandeja = new BandejaEnMemoria();
        revision = new RevisionFalsa();
        Clock reloj = Clock.fixed(AHORA, ZoneId.of("America/Lima"));
        consultas = new ConsultasFalsas();
        AvisosService avisos = new AvisosService(new PoliticaAvisos(new BigDecimal("10")), equipo, bandeja, reloj);
        servicio = new RevisionNegocioService(avisos, revision, consultas, reloj, 24, 7);
    }

    @Test
    void la_revision_encola_un_aviso_por_cada_problema_y_no_repite_en_la_siguiente_vuelta() {
        servicio.revisarPendientes();
        servicio.revisarPendientes();

        assertThat(bandeja.todas).extracting(Notificacion::tipo).containsExactlyInAnyOrder(
                TipoAviso.COMPROBANTE_RECHAZADO, TipoAviso.COMPROBANTE_ATASCADO, TipoAviso.COBRO_VENCIDO);
    }

    @Test
    void usa_los_umbrales_configurados_para_buscar() {
        servicio.revisarPendientes();

        assertThat(revision.consultas).contains(AHORA.minusSeconds(24 * 3600L), AHORA.minusSeconds(7 * 86400L));
    }

    @Test
    void el_resumen_semanal_cubre_los_siete_dias_anteriores_al_lunes_y_se_envia_una_sola_vez() {
        servicio.enviarResumenSemanal();
        servicio.enviarResumenSemanal();

        assertThat(bandeja.todas).hasSize(1);
        Notificacion resumen = bandeja.todas.get(0);
        assertThat(resumen.tipo()).isEqualTo(TipoAviso.RESUMEN_SEMANAL);
        assertThat(resumen.claveUnica()).startsWith("resumen-semanal:2026-11-02");
        assertThat(consultas.ultimoFiltro.desde()).isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(consultas.ultimoFiltro.hasta()).isEqualTo(LocalDate.of(2026, 11, 8));
    }
}
