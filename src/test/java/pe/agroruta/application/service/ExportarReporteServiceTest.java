package pe.agroruta.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import pe.agroruta.domain.model.Archivo;
import pe.agroruta.domain.model.Dimension;
import pe.agroruta.domain.model.FilaEntrega;
import pe.agroruta.domain.model.FilaRanking;
import pe.agroruta.domain.model.Formato;
import pe.agroruta.domain.model.FiltroReporte;
import pe.agroruta.domain.model.Negocio;
import pe.agroruta.domain.model.OpcionesFiltro;
import pe.agroruta.domain.model.Plan;
import pe.agroruta.domain.model.PuntoSerie;
import pe.agroruta.domain.model.Reporte;
import pe.agroruta.domain.model.Resumen;
import pe.agroruta.domain.port.in.ConsultarReportesUseCase;
import pe.agroruta.domain.port.out.ExportadorReportePort;
import pe.agroruta.domain.port.out.NegocioRepositoryPort;

class ExportarReporteServiceTest {

    private static final Instant AHORA = Instant.parse("2026-11-07T13:15:00Z");

    private static final class ConsultasFalsas implements ConsultarReportesUseCase {
        FiltroReporte filtroRecibido;
        String negocioRecibido;
        String usuarioRecibido;
        Instant ahoraRecibido;

        @Override
        public Resumen resumen(FiltroReporte filtro) {
            throw new UnsupportedOperationException();
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
            filtroRecibido = filtro;
            negocioRecibido = negocio;
            usuarioRecibido = generadoPor;
            ahoraRecibido = ahora;
            return new Reporte(negocio, generadoPor, ahora, null, null, List.of(), new EnumMap<>(Dimension.class),
                    List.of());
        }
    }

    private static final class ExportadorFalso implements ExportadorReportePort {
        private final String extension;
        private final String tipo;
        Reporte recibido;

        ExportadorFalso(String extension, String tipo) {
            this.extension = extension;
            this.tipo = tipo;
        }

        @Override
        public byte[] exportar(Reporte reporte) {
            recibido = reporte;
            return extension.getBytes();
        }

        @Override
        public String tipoContenido() {
            return tipo;
        }

        @Override
        public String extension() {
            return extension;
        }
    }

    private final ConsultasFalsas consultas = new ConsultasFalsas();
    private final ExportadorFalso csv = new ExportadorFalso("csv", "text/csv");
    private final ExportadorFalso pdf = new ExportadorFalso("pdf", "application/pdf");
    private final NegocioRepositoryPort negocios = new NegocioRepositoryPort() {
        @Override
        public boolean existePorIdentificador(String identificadorNegocio) {
            return true;
        }

        @Override
        public Optional<Negocio> buscarActivoPorIdentificador(String identificadorNegocio) {
            return Optional.of(new Negocio(1L, identificadorNegocio, "Empresa Demo SAC", Plan.EMPRESA, true));
        }

        @Override
        public List<Negocio> listarActivos() {
            return List.of();
        }

        @Override
        public Negocio guardar(Negocio negocio) {
            return negocio;
        }
    };
    private final ExportarReporteService servicio = new ExportarReporteService(consultas, negocios,
            Map.of(Formato.CSV, csv, Formato.PDF, pdf), Clock.fixed(AHORA, ZoneOffset.UTC));
    private final FiltroReporte filtro = new FiltroReporte(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 7),
            "Palta", 3L);

    @Test
    void elige_el_exportador_segun_el_formato_y_arma_el_nombre_del_archivo() {
        Archivo archivoCsv = servicio.exportar(filtro, Formato.CSV, "dueno.empresa", "empresa_demo");
        Archivo archivoPdf = servicio.exportar(filtro, Formato.PDF, "dueno.empresa", "empresa_demo");

        assertThat(archivoCsv.nombre()).isEqualTo("reporte-agroruta-2026-11-01_2026-11-07.csv");
        assertThat(archivoCsv.tipoContenido()).isEqualTo("text/csv");
        assertThat(archivoCsv.contenido()).isEqualTo("csv".getBytes());
        assertThat(archivoPdf.nombre()).isEqualTo("reporte-agroruta-2026-11-01_2026-11-07.pdf");
        assertThat(archivoPdf.tipoContenido()).isEqualTo("application/pdf");
        assertThat(csv.recibido).isNotNull();
        assertThat(pdf.recibido).isNotNull();
    }

    @Test
    void pasa_los_mismos_filtros_el_nombre_del_negocio_el_usuario_y_la_hora_a_la_consulta() {
        servicio.exportar(filtro, Formato.PDF, "dueno.empresa", "empresa_demo");

        assertThat(consultas.filtroRecibido).isEqualTo(filtro);
        assertThat(consultas.negocioRecibido).isEqualTo("Empresa Demo SAC");
        assertThat(consultas.usuarioRecibido).isEqualTo("dueno.empresa");
        assertThat(consultas.ahoraRecibido).isEqualTo(AHORA);
    }
}
