package pe.agroruta.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import pe.agroruta.domain.model.CobrosPendientes;
import pe.agroruta.domain.model.Dinero;
import pe.agroruta.domain.model.EnvioEnCurso;
import pe.agroruta.domain.model.EstadoEnvio;
import pe.agroruta.domain.model.GananciaDelMes;
import pe.agroruta.domain.model.Panel;
import pe.agroruta.domain.model.Rol;
import pe.agroruta.domain.port.out.PanelRepositoryPort;

class PanelServiceTest {

    private static final Instant AHORA = Instant.parse("2026-12-01T03:00:00Z");
    private static final Instant INICIO_NOVIEMBRE = Instant.parse("2026-11-01T05:00:00Z");
    private static final Instant INICIO_DICIEMBRE = Instant.parse("2026-12-01T05:00:00Z");

    private static final class PanelFalso implements PanelRepositoryPort {
        final Map<Instant, Dinero> gananciaPorInicio = new HashMap<>();
        final List<Instant[]> intervalosDeGanancia = new ArrayList<>();
        final List<Instant[]> intervalosDeMerma = new ArrayList<>();
        BigDecimal mermaMes = BigDecimal.ZERO;
        BigDecimal mermaTotal = BigDecimal.ZERO;
        Dinero perdida = Dinero.CERO;
        List<EnvioEnCurso> envios = List.of();
        CobrosPendientes cobros = new CobrosPendientes(Dinero.CERO, 0);

        @Override
        public Dinero gananciaEntre(Instant desde, Instant hasta) {
            intervalosDeGanancia.add(new Instant[]{desde, hasta});
            return gananciaPorInicio.getOrDefault(desde, Dinero.CERO);
        }

        @Override
        public BigDecimal mermaKgEntre(Instant desde, Instant hasta) {
            intervalosDeMerma.add(new Instant[]{desde, hasta});
            return desde.equals(Instant.EPOCH) ? mermaTotal : mermaMes;
        }

        @Override
        public Dinero perdidaEntre(Instant desde, Instant hasta) {
            return perdida;
        }

        @Override
        public List<EnvioEnCurso> enviosEnCurso(int limite) {
            return envios;
        }

        @Override
        public CobrosPendientes cobrosPendientes() {
            return cobros;
        }
    }

    private final PanelFalso repositorio = new PanelFalso();
    private final ConsultarPanelService servicio = new ConsultarPanelService(repositorio,
            Clock.fixed(AHORA, ZoneOffset.UTC));

    @Test
    void un_mes_sin_entregas_devuelve_ceros_y_listas_vacias() {
        Panel panel = servicio.consultar(Rol.DUENO);

        assertThat(panel.gananciaMes()).isEqualTo(Dinero.CERO);
        assertThat(panel.gananciaMesAnterior()).isEqualTo(Dinero.CERO);
        assertThat(panel.mermaMesKg()).isEqualByComparingTo("0");
        assertThat(panel.mermaTotalKg()).isEqualByComparingTo("0");
        assertThat(panel.cobrosPendientes().cantidad()).isZero();
        assertThat(panel.enviosEnCurso()).isEmpty();
    }

    @Test
    void la_ganancia_del_mes_usa_el_mes_calendario_de_lima() {
        repositorio.gananciaPorInicio.put(INICIO_NOVIEMBRE, Dinero.de("4871.40"));

        Panel panel = servicio.consultar(Rol.DUENO);

        assertThat(panel.mes()).isEqualTo(YearMonth.of(2026, 11));
        assertThat(panel.gananciaMes()).isEqualTo(Dinero.de("4871.40"));
        assertThat(repositorio.intervalosDeGanancia)
                .anySatisfy(i -> {
                    assertThat(i[0]).isEqualTo(INICIO_NOVIEMBRE);
                    assertThat(i[1]).isEqualTo(INICIO_DICIEMBRE);
                });
    }

    @Test
    void la_serie_trae_seis_meses_del_mas_antiguo_al_actual() {
        repositorio.gananciaPorInicio.put(INICIO_NOVIEMBRE, Dinero.de("100.00"));
        repositorio.gananciaPorInicio.put(Instant.parse("2026-06-01T05:00:00Z"), Dinero.de("7.00"));

        List<GananciaDelMes> serie = servicio.consultar(Rol.DUENO).gananciaUltimosMeses();

        assertThat(serie).extracting(GananciaDelMes::mes).containsExactly(YearMonth.of(2026, 6),
                YearMonth.of(2026, 7), YearMonth.of(2026, 8), YearMonth.of(2026, 9), YearMonth.of(2026, 10),
                YearMonth.of(2026, 11));
        assertThat(serie.get(0).ganancia()).isEqualTo(Dinero.de("7.00"));
        assertThat(serie.get(5).ganancia()).isEqualTo(Dinero.de("100.00"));
    }

    @Test
    void el_mes_anterior_se_toma_del_calendario_de_lima() {
        repositorio.gananciaPorInicio.put(Instant.parse("2026-10-01T05:00:00Z"), Dinero.de("250.00"));

        assertThat(servicio.consultar(Rol.DUENO).gananciaMesAnterior()).isEqualTo(Dinero.de("250.00"));
    }

    @Test
    void el_operador_no_recibe_ganancia_ni_serie_pero_si_lo_operativo() {
        repositorio.gananciaPorInicio.put(INICIO_NOVIEMBRE, Dinero.de("999.00"));
        repositorio.mermaMes = new BigDecimal("40.500");
        repositorio.envios = List.of(new EnvioEnCurso(1L, EstadoEnvio.EN_RUTA, "Mercado Mayorista", "Ana",
                new BigDecimal("500.000"), AHORA));
        repositorio.cobros = new CobrosPendientes(Dinero.de("6372.00"), 1);

        Panel panel = servicio.consultar(Rol.OPERADOR);

        assertThat(panel.gananciaMes()).isNull();
        assertThat(panel.gananciaMesAnterior()).isNull();
        assertThat(panel.gananciaUltimosMeses()).isNull();
        assertThat(panel.mermaMesKg()).isEqualByComparingTo("40.5");
        assertThat(panel.enviosEnCurso()).hasSize(1);
        assertThat(panel.cobrosPendientes().monto()).isEqualTo(Dinero.de("6372.00"));
    }

    @Test
    void la_merma_total_suma_todo_el_historial_hasta_el_fin_del_mes() {
        repositorio.mermaTotal = new BigDecimal("123.250");

        Panel panel = servicio.consultar(Rol.DUENO);

        assertThat(panel.mermaTotalKg()).isEqualByComparingTo("123.25");
        assertThat(repositorio.intervalosDeMerma)
                .anySatisfy(i -> {
                    assertThat(i[0]).isEqualTo(Instant.EPOCH);
                    assertThat(i[1]).isEqualTo(INICIO_DICIEMBRE);
                });
    }

    @Test
    void el_mes_anterior_se_compara_hasta_el_mismo_dia_y_hora_y_no_completo() {
        servicio.consultar(Rol.DUENO);

        // Ahora es el 30 de noviembre a las 22:00 en Lima: el mes anterior va del 1 al 30 de octubre a las 22:00.
        assertThat(repositorio.intervalosDeGanancia).anySatisfy(intervalo -> {
            assertThat(intervalo[0]).isEqualTo(Instant.parse("2026-10-01T05:00:00Z"));
            assertThat(intervalo[1]).isEqualTo(Instant.parse("2026-10-31T03:00:00Z"));
        });
    }
}
