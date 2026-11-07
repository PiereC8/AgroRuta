package pe.agroruta.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import pe.agroruta.domain.model.DestinoVenta;
import pe.agroruta.domain.model.Dimension;
import pe.agroruta.domain.model.Dinero;
import pe.agroruta.domain.model.DominioException;
import pe.agroruta.domain.model.FilaEntrega;
import pe.agroruta.domain.model.FilaRanking;
import pe.agroruta.domain.model.FiltroReporte;
import pe.agroruta.domain.model.OpcionesFiltro;
import pe.agroruta.domain.model.PuntoSerie;
import pe.agroruta.domain.model.Resumen;
import pe.agroruta.domain.model.Veredicto;
import pe.agroruta.domain.port.out.DestinoRepositoryPort;
import pe.agroruta.domain.port.out.ReporteRepositoryPort;

class ReportesServiceTest {

    /** Devuelve las filas que se le dieron para cada fecha de inicio y recuerda con qué filtros le preguntaron. */
    private static final class ReporteFalso implements ReporteRepositoryPort {
        final Map<LocalDate, List<FilaEntrega>> filasPorInicio = new HashMap<>();
        final List<FiltroReporte> consultas = new ArrayList<>();
        List<String> productos = List.of();
        int limiteRecibido;

        @Override
        public List<FilaEntrega> entregas(FiltroReporte filtro, int limite) {
            consultas.add(filtro);
            limiteRecibido = limite;
            return filasPorInicio.getOrDefault(filtro.desde(), List.of());
        }

        @Override
        public List<String> productosConEntregas() {
            return productos;
        }
    }

    private static final class DestinosFalsos implements DestinoRepositoryPort {
        @Override
        public boolean existePorNombre(String nombreDestino) {
            return false;
        }

        @Override
        public DestinoVenta guardar(DestinoVenta destino) {
            return destino;
        }

        @Override
        public Optional<DestinoVenta> buscarPorId(Long idDestinoVenta) {
            return Optional.empty();
        }

        @Override
        public List<DestinoVenta> listarActivos() {
            return List.of(DestinoVenta.nuevo("Mercado Lima", "Lima", false).conId(3L),
                    DestinoVenta.nuevo("Mercado Tacna", "Tacna", true).conId(1L));
        }
    }

    private final ReporteFalso reporte = new ReporteFalso();
    private final ConsultarReportesService servicio = new ConsultarReportesService(reporte, new DestinosFalsos());

    private static FilaEntrega fila(String fechaUtc, String comprador, String producto, String destino,
                                    String ingreso, String flete, String perdida, String merma) {
        Dinero ingresoReal = Dinero.de(ingreso);
        Dinero fleteTotal = Dinero.de(flete);
        return new FilaEntrega(1L, 1L, Instant.parse(fechaUtc), comprador, producto, destino, new BigDecimal("100"),
                new BigDecimal("90"), new BigDecimal(merma), new BigDecimal("4"), ingresoReal, fleteTotal,
                ingresoReal.restar(fleteTotal), ingresoReal.restar(fleteTotal).sumar(Dinero.de(perdida)),
                Dinero.de(perdida), false);
    }

    @Test
    void el_resumen_suma_las_entregas_del_periodo_y_las_del_anterior_por_separado() {
        reporte.filasPorInicio.put(LocalDate.of(2026, 11, 1), List.of(
                fila("2026-11-03T15:00:00Z", "Ana", "Palta", "Lima", "5000.00", "1000.00", "200.00", "10"),
                fila("2026-11-05T15:00:00Z", "Luis", "Palta", "Lima", "3000.00", "500.00", "100.00", "6")));
        reporte.filasPorInicio.put(LocalDate.of(2026, 10, 1), List.of(
                fila("2026-10-03T15:00:00Z", "Ana", "Palta", "Lima", "9000.00", "1500.00", "100.00", "5")));

        Resumen resumen = servicio.resumen(FiltroReporte.esteMes(LocalDate.of(2026, 11, 7)));

        assertThat(resumen.actual().entregas()).isEqualTo(2);
        assertThat(resumen.actual().gananciaReal()).isEqualTo(Dinero.de("6500.00"));
        assertThat(resumen.anterior().entregas()).isEqualTo(1);
        assertThat(resumen.anterior().gananciaReal()).isEqualTo(Dinero.de("7500.00"));
        assertThat(resumen.veredicto()).isEqualTo(Veredicto.GANANDO_MENOS);
        assertThat(resumen.explicacion()).contains("S/ 6,500.00").contains("S/ 1,000.00");
        assertThat(resumen.comparaciones()).hasSize(6);
        assertThat(reporte.consultas).extracting(FiltroReporte::desde)
                .containsExactly(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 10, 1));
    }

    @Test
    void un_periodo_vacio_devuelve_todo_en_cero_y_sin_datos() {
        Resumen resumen = servicio.resumen(FiltroReporte.ultimosDias(30, LocalDate.of(2026, 11, 7)));

        assertThat(resumen.veredicto()).isEqualTo(Veredicto.SIN_DATOS);
        assertThat(resumen.actual().entregas()).isZero();
        assertThat(resumen.actual().gananciaReal()).isEqualTo(Dinero.CERO);
        assertThat(resumen.explicacion()).isEqualTo("No hay entregas en este período.");
        assertThat(servicio.serie(FiltroReporte.ultimosDias(7, LocalDate.of(2026, 11, 7)))).hasSize(7)
                .allSatisfy(p -> assertThat(p.ganancia()).isEqualTo(Dinero.CERO));
        assertThat(servicio.ranking(FiltroReporte.ultimosDias(7, LocalDate.of(2026, 11, 7)), Dimension.DESTINO))
                .isEmpty();
    }

    @Test
    void la_serie_agrupa_por_dia_semana_o_mes_segun_el_rango() {
        LocalDate hoy = LocalDate.of(2026, 11, 6);

        List<PuntoSerie> dias = servicio.serie(FiltroReporte.ultimosDias(7, hoy));
        List<PuntoSerie> semanas = servicio.serie(FiltroReporte.ultimosDias(60, hoy));
        List<PuntoSerie> meses = servicio.serie(FiltroReporte.ultimosDias(200, hoy));

        assertThat(dias).hasSize(7);
        assertThat(dias.get(0).inicio()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(dias.get(6).inicio()).isEqualTo(hoy);
        assertThat(semanas).hasSize(9);
        assertThat(semanas.get(0).inicio()).isEqualTo(LocalDate.of(2026, 9, 7));
        assertThat(semanas.get(0).inicio().getDayOfWeek()).isEqualTo(java.time.DayOfWeek.MONDAY);
        assertThat(meses).hasSize(8);
        assertThat(meses.get(0).inicio()).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(meses.get(7).inicio()).isEqualTo(LocalDate.of(2026, 11, 1));
    }

    @Test
    void la_entrega_de_las_00_30_de_lima_cuenta_en_su_dia_local() {
        LocalDate hoy = LocalDate.of(2026, 11, 6);
        reporte.filasPorInicio.put(hoy.minusDays(6), List.of(
                fila("2026-11-06T05:30:00Z", "Ana", "Palta", "Lima", "1000.00", "100.00", "0.00", "0"),
                fila("2026-11-06T04:30:00Z", "Ana", "Palta", "Lima", "500.00", "50.00", "0.00", "0")));

        List<PuntoSerie> dias = servicio.serie(FiltroReporte.ultimosDias(7, hoy));

        assertThat(punto(dias, LocalDate.of(2026, 11, 6)).ganancia()).isEqualTo(Dinero.de("900.00"));
        assertThat(punto(dias, LocalDate.of(2026, 11, 5)).ganancia()).isEqualTo(Dinero.de("450.00"));
    }

    private static PuntoSerie punto(List<PuntoSerie> serie, LocalDate dia) {
        return serie.stream().filter(p -> p.inicio().equals(dia)).findFirst().orElseThrow();
    }

    @Test
    void el_ranking_ordena_por_ganancia_y_calcula_margen_y_merma() {
        reporte.filasPorInicio.put(LocalDate.of(2026, 11, 1), List.of(
                fila("2026-11-03T15:00:00Z", "Ana", "Palta", "Lima", "5000.00", "1000.00", "200.00", "10"),
                fila("2026-11-04T15:00:00Z", "Luis", "Palta", "Tacna", "3000.00", "0.00", "100.00", "6"),
                fila("2026-11-05T15:00:00Z", "Ana", "Mango", "Lima", "1000.00", "500.00", "50.00", "2")));

        List<FilaRanking> destinos = servicio.ranking(FiltroReporte.esteMes(LocalDate.of(2026, 11, 7)),
                Dimension.DESTINO);
        List<FilaRanking> compradores = servicio.ranking(FiltroReporte.esteMes(LocalDate.of(2026, 11, 7)),
                Dimension.COMPRADOR);
        List<FilaRanking> productos = servicio.ranking(FiltroReporte.esteMes(LocalDate.of(2026, 11, 7)),
                Dimension.PRODUCTO);

        assertThat(destinos).extracting(FilaRanking::nombre).containsExactly("Lima", "Tacna");
        assertThat(destinos.get(0).entregas()).isEqualTo(2);
        assertThat(destinos.get(0).ganancia()).isEqualTo(Dinero.de("4500.00"));
        assertThat(destinos.get(0).mermaKg()).isEqualByComparingTo("12");
        assertThat(destinos.get(0).margenPorcentaje()).isEqualByComparingTo("75.0");
        assertThat(compradores).extracting(FilaRanking::nombre).containsExactly("Ana", "Luis");
        assertThat(productos).extracting(FilaRanking::nombre).containsExactly("Palta", "Mango");
    }

    @Test
    void el_filtro_por_producto_y_destino_se_pasa_al_puerto_tanto_al_periodo_como_al_anterior() {
        FiltroReporte filtro = new FiltroReporte(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 7), "Palta", 3L);

        servicio.resumen(filtro);

        assertThat(reporte.consultas).hasSize(2).allSatisfy(c -> {
            assertThat(c.producto()).isEqualTo("Palta");
            assertThat(c.idDestino()).isEqualTo(3L);
        });
    }

    @Test
    void si_hay_demasiadas_entregas_pide_acotar_el_periodo() {
        List<FilaEntrega> muchas = new ArrayList<>();
        for (int i = 0; i < ConsultarReportesService.LIMITE_DE_ENTREGAS; i++) {
            muchas.add(fila("2026-11-03T15:00:00Z", "Ana", "Palta", "Lima", "10.00", "1.00", "0.00", "0"));
        }
        reporte.filasPorInicio.put(LocalDate.of(2026, 11, 1), muchas);

        assertThatThrownBy(() -> servicio.resumen(FiltroReporte.esteMes(LocalDate.of(2026, 11, 7))))
                .isInstanceOf(DominioException.class).hasMessageContaining("Acota");
        assertThat(reporte.limiteRecibido).isEqualTo(ConsultarReportesService.LIMITE_DE_ENTREGAS);
    }

    @Test
    void el_reporte_completo_trae_todo_con_solo_dos_consultas_a_la_base() {
        reporte.filasPorInicio.put(LocalDate.of(2026, 11, 1), List.of(
                fila("2026-11-03T15:00:00Z", "Ana", "Palta", "Lima", "5000.00", "1000.00", "200.00", "10"),
                fila("2026-11-05T15:00:00Z", "Luis", "Mango", "Tacna", "3000.00", "500.00", "100.00", "6")));
        FiltroReporte filtro = new FiltroReporte(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 7), null, 3L);

        pe.agroruta.domain.model.Reporte completo = servicio.completo(filtro, "Chacra Demo", "dueno.empresa",
                Instant.parse("2026-11-07T13:00:00Z"));

        assertThat(reporte.consultas).hasSize(2);
        assertThat(completo.negocio()).isEqualTo("Chacra Demo");
        assertThat(completo.generadoPor()).isEqualTo("dueno.empresa");
        assertThat(completo.nombreDestino()).isEqualTo("Mercado Lima");
        assertThat(completo.entregas()).hasSize(2);
        assertThat(completo.serie()).hasSize(7);
        assertThat(completo.rankings()).containsKeys(Dimension.DESTINO, Dimension.PRODUCTO, Dimension.COMPRADOR);
        assertThat(completo.rankings().get(Dimension.COMPRADOR)).extracting(FilaRanking::nombre)
                .containsExactly("Ana", "Luis");
        assertThat(completo.resumen().actual().gananciaReal()).isEqualTo(Dinero.de("6500.00"));
    }

    @Test
    void las_opciones_traen_los_productos_con_entregas_y_los_destinos_activos() {
        reporte.productos = List.of("Mango", "Palta Hass");

        OpcionesFiltro opciones = servicio.opciones();

        assertThat(opciones.productos()).containsExactly("Mango", "Palta Hass");
        assertThat(opciones.destinos()).extracting(OpcionesFiltro.OpcionDestino::nombre)
                .containsExactly("Mercado Lima", "Mercado Tacna");
        assertThat(opciones.destinos().get(0).idDestinoVenta()).isEqualTo(3L);
    }
}
