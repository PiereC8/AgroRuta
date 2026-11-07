package pe.agroruta.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

class AnalisisFinancieroTest {

    private static Indicadores periodo(int entregas, String ingreso, String flete, String perdida) {
        Dinero ingresoReal = Dinero.de(ingreso);
        Dinero fleteTotal = Dinero.de(flete);
        Dinero perdidaMerma = Dinero.de(perdida);
        return new Indicadores(entregas, new BigDecimal("1000"), new BigDecimal("950"), new BigDecimal("30"),
                new BigDecimal("20"), ingresoReal, fleteTotal, ingresoReal.restar(fleteTotal),
                ingresoReal.restar(fleteTotal).sumar(perdidaMerma), perdidaMerma, ingresoReal, Dinero.CERO);
    }

    private static FilaEntrega fila(String ingreso, String flete, boolean cobrada) {
        Dinero ingresoReal = Dinero.de(ingreso);
        Dinero fleteTotal = Dinero.de(flete);
        return new FilaEntrega(1L, 1L, Instant.parse("2026-11-05T15:00:00Z"), "Mercado Central", "Palta Hass",
                "Mercado Lima", new BigDecimal("100"), new BigDecimal("90"), new BigDecimal("6"),
                new BigDecimal("4"), ingresoReal, fleteTotal, ingresoReal.restar(fleteTotal), ingresoReal, Dinero.CERO,
                cobrada);
    }

    private static final Indicadores SIN_ENTREGAS = Indicadores.de(List.of());

    @Test
    void los_indicadores_suman_las_entregas_y_separan_lo_cobrado_de_lo_pendiente() {
        Indicadores total = Indicadores.de(List.of(fila("1000.00", "200.00", true), fila("500.00", "100.00", false)));

        assertThat(total.entregas()).isEqualTo(2);
        assertThat(total.ingresoReal()).isEqualTo(Dinero.de("1500.00"));
        assertThat(total.fleteTotal()).isEqualTo(Dinero.de("300.00"));
        assertThat(total.gananciaReal()).isEqualTo(Dinero.de("1200.00"));
        assertThat(total.cobrado()).isEqualTo(Dinero.de("1000.00"));
        assertThat(total.porCobrar()).isEqualTo(Dinero.de("500.00"));
        assertThat(total.margenPorcentaje()).isEqualByComparingTo("80.0");
    }

    @Test
    void sin_ingreso_no_hay_margen_y_sin_entregas_todo_es_cero() {
        assertThat(SIN_ENTREGAS.entregas()).isZero();
        assertThat(SIN_ENTREGAS.gananciaReal()).isEqualTo(Dinero.CERO);
        assertThat(SIN_ENTREGAS.margenPorcentaje()).isNull();
    }

    @Test
    void sin_entregas_el_veredicto_es_sin_datos() {
        assertThat(AnalisisFinanciero.veredicto(SIN_ENTREGAS, periodo(3, "5000.00", "1000.00", "100.00")))
                .isEqualTo(Veredicto.SIN_DATOS);
    }

    @Test
    void ganancia_positiva_y_mayor_que_la_anterior_es_ganando() {
        assertThat(AnalisisFinanciero.veredicto(periodo(5, "6000.00", "1000.00", "100.00"),
                periodo(4, "5000.00", "1000.00", "100.00"))).isEqualTo(Veredicto.GANANDO);
    }

    @Test
    void ganancia_positiva_pero_menor_que_la_anterior_es_ganando_menos() {
        assertThat(AnalisisFinanciero.veredicto(periodo(5, "5000.00", "2400.00", "650.00"),
                periodo(4, "5800.00", "2000.00", "300.00"))).isEqualTo(Veredicto.GANANDO_MENOS);
    }

    @Test
    void ganancia_cero_o_negativa_es_perdiendo() {
        assertThat(AnalisisFinanciero.veredicto(periodo(2, "1000.00", "1000.00", "0.00"),
                periodo(4, "5000.00", "1000.00", "0.00"))).isEqualTo(Veredicto.PERDIENDO);
        assertThat(AnalisisFinanciero.veredicto(periodo(2, "800.00", "1000.00", "50.00"), SIN_ENTREGAS))
                .isEqualTo(Veredicto.PERDIENDO);
    }

    @Test
    void sin_periodo_anterior_y_con_ganancia_es_ganando() {
        assertThat(AnalisisFinanciero.veredicto(periodo(2, "3000.00", "500.00", "0.00"), SIN_ENTREGAS))
                .isEqualTo(Veredicto.GANANDO);
    }

    @Test
    void la_comparacion_no_divide_entre_cero_si_lo_anterior_fue_cero() {
        List<Comparacion> comparaciones = AnalisisFinanciero.comparar(periodo(2, "3000.00", "500.00", "0.00"),
                SIN_ENTREGAS);

        Comparacion ganancia = comparaciones.stream().filter(c -> c.concepto().equals("Ganancia real")).findFirst()
                .orElseThrow();
        assertThat(ganancia.porcentaje()).isNull();
        assertThat(ganancia.diferencia()).isEqualByComparingTo("2500.00");
        assertThat(ganancia.tendencia()).isEqualTo(Tendencia.MEJORA);
    }

    @Test
    void subir_el_flete_o_la_merma_se_marca_como_empeora_y_bajarlos_como_mejora() {
        List<Comparacion> comparaciones = AnalisisFinanciero.comparar(periodo(5, "5000.00", "2400.00", "650.00"),
                periodo(4, "5800.00", "2000.00", "300.00"));

        assertThat(buscar(comparaciones, "Flete").tendencia()).isEqualTo(Tendencia.EMPEORA);
        assertThat(buscar(comparaciones, "Merma y rechazo").tendencia()).isEqualTo(Tendencia.EMPEORA);
        assertThat(buscar(comparaciones, "Ganancia real").tendencia()).isEqualTo(Tendencia.EMPEORA);
        assertThat(buscar(comparaciones, "Entregas").tendencia()).isEqualTo(Tendencia.MEJORA);
        assertThat(buscar(comparaciones, "Flete").porcentaje()).isEqualByComparingTo("20.0");

        List<Comparacion> mejor = AnalisisFinanciero.comparar(periodo(4, "5800.00", "2000.00", "300.00"),
                periodo(4, "5800.00", "2400.00", "300.00"));
        assertThat(buscar(mejor, "Flete").tendencia()).isEqualTo(Tendencia.MEJORA);
        assertThat(buscar(mejor, "Entregas").tendencia()).isEqualTo(Tendencia.IGUAL);
    }

    private static Comparacion buscar(List<Comparacion> lista, String concepto) {
        return lista.stream().filter(c -> c.concepto().equals(concepto)).findFirst().orElseThrow();
    }

    @Test
    void las_causas_suman_exactamente_el_cambio_de_ganancia_y_van_de_mayor_a_menor() {
        Indicadores actual = periodo(5, "5000.00", "2400.00", "650.00");
        Indicadores anterior = periodo(4, "5800.00", "2000.00", "300.00");

        List<Causa> causas = AnalisisFinanciero.causas(actual, anterior);

        assertThat(causas).extracting(Causa::concepto)
                .containsExactly(Causa.Concepto.VENTAS, Causa.Concepto.FLETE, Causa.Concepto.MERMA);
        assertThat(causas.get(0).efecto()).isEqualTo(Dinero.de("-450.00"));
        assertThat(causas.get(1).efecto()).isEqualTo(Dinero.de("-400.00"));
        assertThat(causas.get(2).efecto()).isEqualTo(Dinero.de("-350.00"));
        Dinero suma = causas.stream().map(Causa::efecto).reduce(Dinero.CERO, Dinero::sumar);
        assertThat(suma).isEqualTo(actual.gananciaReal().restar(anterior.gananciaReal()));
    }

    @Test
    void la_explicacion_nombra_las_dos_causas_mas_grandes_con_sus_montos() {
        Indicadores actual = periodo(5, "5000.00", "2400.00", "650.00");
        Indicadores anterior = periodo(4, "5800.00", "2000.00", "300.00");

        String texto = AnalisisFinanciero.explicacion(Veredicto.GANANDO_MENOS, actual, anterior);

        assertThat(texto).contains("S/ 2,600.00").contains("S/ 1,200.00").contains("menos")
                .contains("S/ 450.00").contains("el flete costó S/ 400.00 más");
        assertThat(texto).doesNotContain("merma");
    }

    @Test
    void la_explicacion_de_una_perdida_dice_cuanto_se_perdio() {
        String texto = AnalisisFinanciero.explicacion(Veredicto.PERDIENDO, periodo(2, "800.00", "1000.00", "50.00"),
                SIN_ENTREGAS);

        assertThat(texto).contains("Perdiste S/ 200.00");
    }

    @Test
    void la_explicacion_sin_datos_o_ganando_sin_comparacion_es_clara() {
        assertThat(AnalisisFinanciero.explicacion(Veredicto.SIN_DATOS, SIN_ENTREGAS, SIN_ENTREGAS))
                .isEqualTo("No hay entregas en este período.");
        assertThat(AnalisisFinanciero.explicacion(Veredicto.GANANDO, periodo(2, "3000.00", "500.00", "0.00"),
                SIN_ENTREGAS)).isEqualTo("Ganaste S/ 2,500.00. No hay entregas en el período anterior para comparar.");
    }
}
