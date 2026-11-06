package pe.agroruta.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Random;

import org.junit.jupiter.api.Test;

class LiquidacionTest {

    private static final Instant AHORA = Instant.parse("2026-11-06T15:00:00Z");

    private static Pedido pedido(String kg, String precio) {
        Lote lote = Lote.nuevo("Palta Hass", new BigDecimal(kg), LocalDate.of(2026, 11, 5), "Sur", "dueno.demo", AHORA,
                LocalDate.of(2026, 11, 6)).conId(1L);
        return Pedido.nuevo(1L, lote, 3L, Dinero.de(precio), "operador.demo", AHORA).conId(1L);
    }

    private static Envio envio(Pedido pedido) {
        return new Envio(1L, pedido.idPedido(), pedido.idLote(), 1L, 1L, EstadoEnvio.EN_RUTA, AHORA, AHORA, null, "x1x");
    }

    private static Liquidacion calcular(String kg, String precio, String recibido, String merma, String rechazo,
                                        String fletePorKg) {
        Pedido p = pedido(kg, precio);
        return Liquidacion.calcular(p, envio(p), new BigDecimal(recibido), new BigDecimal(merma),
                new BigDecimal(rechazo), Dinero.de(fletePorKg), "operador.demo", AHORA);
    }

    @Test
    void el_ejemplo_del_lote_de_palta_da_las_cifras_esperadas() {
        Liquidacion l = calcular("1250.5", "5.40", "1180", "40.5", "30", "1.20");

        assertThat(l.ingresoReal().monto().toPlainString()).isEqualTo("6372.00");
        assertThat(l.costoFleteTotal().monto().toPlainString()).isEqualTo("1500.60");
        assertThat(l.gananciaReal().monto().toPlainString()).isEqualTo("4871.40");
        assertThat(l.gananciaEsperada().monto().toPlainString()).isEqualTo("5252.10");
        assertThat(l.perdidaPorMermaYRechazo().monto().toPlainString()).isEqualTo("380.70");
        assertThat(l.cantidadDespachadaKg().toPlainString()).isEqualTo("1250.500");
    }

    @Test
    void sin_merma_ni_rechazo_la_ganancia_real_es_la_esperada() {
        Liquidacion l = calcular("1000", "5.00", "1000", "0", "0", "1.00");

        assertThat(l.gananciaReal()).isEqualTo(l.gananciaEsperada());
        assertThat(l.perdidaPorMermaYRechazo()).isEqualTo(Dinero.CERO);
    }

    @Test
    void si_todo_se_rechaza_solo_queda_el_flete_como_perdida() {
        Liquidacion l = calcular("1000", "5.00", "0", "0", "1000", "1.20");

        assertThat(l.ingresoReal()).isEqualTo(Dinero.CERO);
        assertThat(l.gananciaReal().monto().toPlainString()).isEqualTo("-1200.00");
        assertThat(l.gananciaReal().esNegativo()).isTrue();
    }

    @Test
    void un_destino_local_sin_flete_no_descuenta_nada() {
        Liquidacion l = calcular("100", "3.00", "90", "10", "0", "0");

        assertThat(l.costoFleteTotal()).isEqualTo(Dinero.CERO);
        assertThat(l.gananciaReal().monto().toPlainString()).isEqualTo("270.00");
    }

    @Test
    void rechaza_si_lo_recibido_la_merma_y_el_rechazo_no_suman_lo_despachado() {
        assertThatThrownBy(() -> calcular("1000", "5", "900", "50", "49.999", "1"))
                .isInstanceOf(DominioException.class).hasMessageContaining("suman 999.999 kg");
        assertThatThrownBy(() -> calcular("1000", "5", "900", "50", "50.001", "1"))
                .isInstanceOf(DominioException.class);
        assertThatThrownBy(() -> calcular("1000", "5", "1000", "1", "0", "1"))
                .isInstanceOf(DominioException.class);
    }

    @Test
    void rechaza_cantidades_negativas_con_demasiados_decimales_o_vacias() {
        assertThatThrownBy(() -> calcular("1000", "5", "1100", "-100", "0", "1"))
                .isInstanceOf(DominioException.class).hasMessageContaining("negativa");
        assertThatThrownBy(() -> calcular("1000", "5", "999.9995", "0.0005", "0", "1"))
                .isInstanceOf(DominioException.class).hasMessageContaining("3 decimales");
        Pedido p = pedido("1000", "5");
        assertThatThrownBy(() -> Liquidacion.calcular(p, envio(p), null, BigDecimal.ZERO, BigDecimal.ZERO,
                Dinero.CERO, "x1x", AHORA)).isInstanceOf(DominioException.class);
    }

    @Test
    void rechaza_un_flete_negativo_y_un_usuario_vacio() {
        Pedido p = pedido("1000", "5");
        assertThatThrownBy(() -> Liquidacion.calcular(p, envio(p), new BigDecimal("1000"), BigDecimal.ZERO,
                BigDecimal.ZERO, Dinero.de("-1"), "x1x", AHORA)).isInstanceOf(DominioException.class);
        assertThatThrownBy(() -> Liquidacion.calcular(p, envio(p), new BigDecimal("1000"), BigDecimal.ZERO,
                BigDecimal.ZERO, Dinero.CERO, " ", AHORA)).isInstanceOf(DominioException.class);
    }

    @Test
    void coincide_con_el_calculo_manual_en_mil_casos_aleatorios() {
        Random azar = new Random(20261106L);
        for (int i = 0; i < 1000; i++) {
            BigDecimal despachado = gramos(azar, 1, 5_000_000);
            BigDecimal merma = gramos(azar, 0, despachado.movePointRight(3).intValue());
            BigDecimal rechazo = gramos(azar, 0, despachado.subtract(merma).movePointRight(3).intValue());
            BigDecimal recibido = despachado.subtract(merma).subtract(rechazo);
            String precio = centimos(azar, 1, 50_000);
            String flete = centimos(azar, 0, 10_000);

            Liquidacion l = calcular(despachado.toPlainString(), precio, recibido.toPlainString(),
                    merma.toPlainString(), rechazo.toPlainString(), flete);

            // Cálculo manual e independiente, solo con BigDecimal.
            BigDecimal ingreso = new BigDecimal(precio).multiply(recibido).setScale(2, RoundingMode.HALF_UP);
            BigDecimal costoFlete = new BigDecimal(flete).multiply(despachado).setScale(2, RoundingMode.HALF_UP);
            BigDecimal esperado = new BigDecimal(precio).multiply(despachado).setScale(2, RoundingMode.HALF_UP)
                    .subtract(costoFlete);

            assertThat(l.ingresoReal().monto()).isEqualByComparingTo(ingreso);
            assertThat(l.costoFleteTotal().monto()).isEqualByComparingTo(costoFlete);
            assertThat(l.gananciaReal().monto()).isEqualByComparingTo(ingreso.subtract(costoFlete));
            assertThat(l.gananciaEsperada().monto()).isEqualByComparingTo(esperado);
            // La pérdida cuadra exactamente con la diferencia entre lo esperado y lo real.
            assertThat(l.gananciaReal().sumar(l.perdidaPorMermaYRechazo())).isEqualTo(l.gananciaEsperada());
            assertThat(l.cantidadRecibidaKg().add(l.mermaKg()).add(l.rechazoKg()))
                    .isEqualByComparingTo(l.cantidadDespachadaKg());
        }
    }

    private static BigDecimal gramos(Random azar, int minimo, int maximo) {
        int valor = maximo <= minimo ? minimo : minimo + azar.nextInt(maximo - minimo + 1);
        return BigDecimal.valueOf(valor, 3);
    }

    private static String centimos(Random azar, int minimo, int maximo) {
        return BigDecimal.valueOf(minimo + azar.nextInt(maximo - minimo + 1), 2).toPlainString();
    }
}
