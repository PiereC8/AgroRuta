package pe.agroruta.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class FiltroReporteTest {

    private static final LocalDate HOY = LocalDate.of(2026, 11, 7);

    @Test
    void rechaza_rangos_al_reves_sin_fechas_o_de_mas_de_366_dias() {
        assertThatThrownBy(() -> new FiltroReporte(LocalDate.of(2026, 11, 7), LocalDate.of(2026, 11, 1), null, null))
                .isInstanceOf(DominioException.class).hasMessageContaining("antes");
        assertThatThrownBy(() -> new FiltroReporte(null, HOY, null, null)).isInstanceOf(DominioException.class);
        assertThatThrownBy(() -> new FiltroReporte(LocalDate.of(2025, 1, 1), LocalDate.of(2026, 11, 7), null, null))
                .isInstanceOf(DominioException.class).hasMessageContaining("366");
        assertThat(new FiltroReporte(LocalDate.of(2025, 11, 7), LocalDate.of(2026, 11, 7), null, null).dias())
                .isEqualTo(366);
    }

    @Test
    void el_producto_en_blanco_cuenta_como_sin_filtro_y_se_limita_el_largo() {
        assertThat(new FiltroReporte(HOY, HOY, "   ", null).producto()).isNull();
        assertThat(new FiltroReporte(HOY, HOY, "  Palta Hass ", null).producto()).isEqualTo("Palta Hass");
        assertThatThrownBy(() -> new FiltroReporte(HOY, HOY, "x".repeat(81), null))
                .isInstanceOf(DominioException.class);
    }

    @Test
    void los_ultimos_30_dias_incluyen_hoy() {
        FiltroReporte filtro = FiltroReporte.ultimosDias(30, HOY);

        assertThat(filtro.hasta()).isEqualTo(HOY);
        assertThat(filtro.desde()).isEqualTo(LocalDate.of(2026, 10, 9));
        assertThat(filtro.dias()).isEqualTo(30);
    }

    @Test
    void el_periodo_anterior_de_un_rango_libre_tiene_la_misma_duracion_y_termina_el_dia_antes() {
        FiltroReporte filtro = new FiltroReporte(LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 9), "Palta", 4L);

        FiltroReporte anterior = filtro.periodoAnterior();

        assertThat(anterior.desde()).isEqualTo(LocalDate.of(2026, 10, 27));
        assertThat(anterior.hasta()).isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(anterior.producto()).isEqualTo("Palta");
        assertThat(anterior.idDestino()).isEqualTo(4L);
    }

    @Test
    void este_mes_a_la_fecha_se_compara_con_el_mismo_tramo_del_mes_pasado() {
        FiltroReporte anterior = FiltroReporte.esteMes(HOY).periodoAnterior();

        assertThat(anterior.desde()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(anterior.hasta()).isEqualTo(LocalDate.of(2026, 10, 7));
    }

    @Test
    void un_mes_completo_se_compara_con_el_mes_anterior_completo_aunque_dure_distinto() {
        FiltroReporte octubre = FiltroReporte.mesPasado(HOY);
        FiltroReporte marzo = new FiltroReporte(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), null, null);

        assertThat(octubre.desde()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(octubre.hasta()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(octubre.periodoAnterior().desde()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(octubre.periodoAnterior().hasta()).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(marzo.periodoAnterior().desde()).isEqualTo(LocalDate.of(2026, 2, 1));
        assertThat(marzo.periodoAnterior().hasta()).isEqualTo(LocalDate.of(2026, 2, 28));
    }

    @Test
    void el_mes_pasado_de_enero_es_diciembre_del_anio_anterior() {
        FiltroReporte filtro = FiltroReporte.mesPasado(LocalDate.of(2027, 1, 15));

        assertThat(filtro.desde()).isEqualTo(LocalDate.of(2026, 12, 1));
        assertThat(filtro.hasta()).isEqualTo(LocalDate.of(2026, 12, 31));
    }

    @Test
    void este_anio_se_compara_con_el_mismo_tramo_del_anio_anterior() {
        FiltroReporte anterior = FiltroReporte.esteAnio(HOY).periodoAnterior();

        assertThat(anterior.desde()).isEqualTo(LocalDate.of(2025, 1, 1));
        assertThat(anterior.hasta()).isEqualTo(LocalDate.of(2025, 11, 7));
    }
}
