package pe.agroruta.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class CotizadorDestinosTest {

    private static final Lote LOTE = Lote.nuevo("Palta Hass", new BigDecimal("1000"), LocalDate.of(2026, 11, 5),
            "Chacra Sur", "dueno.demo", Instant.parse("2026-11-06T15:00:00Z"), LocalDate.of(2026, 11, 6))
            .conId(1L);

    private static DestinoVenta destino(long id, String nombre, boolean local) {
        return new DestinoVenta(id, nombre, "Ciudad", local, true);
    }

    private static Dinero soles(String monto) {
        return Dinero.de(monto);
    }

    @Test
    void ordena_por_ganancia_neta_y_marca_primero_el_mejor_destino() {
        var tacna = destino(1, "Mercado Tacna", true);
        var lima = destino(2, "Mercado Lima", false);
        var arequipa = destino(3, "Mercado Arequipa", false);

        Cotizacion cotizacion = CotizadorDestinos.cotizar(LOTE, LOTE.cantidadKg(), List.of(tacna, lima, arequipa),
                Map.of(1L, soles("3.00"), 2L, soles("5.50"), 3L, soles("4.20")),
                Map.of(2L, soles("1.20"), 3L, soles("0.40")));

        assertThat(cotizacion.opciones()).extracting(o -> o.destino().nombreDestino())
                .containsExactly("Mercado Lima", "Mercado Arequipa", "Mercado Tacna");
        // Lima: 5.50 x 1000 - 1.20 x 1000 = 4300; Arequipa: 3800; Tacna (local, sin flete): 3000
        assertThat(cotizacion.opciones()).extracting(o -> o.gananciaNeta().monto().toPlainString())
                .containsExactly("4300.00", "3800.00", "3000.00");
        assertThat(cotizacion.mejorOpcion().orElseThrow().destino().nombreDestino()).isEqualTo("Mercado Lima");
    }

    @Test
    void un_destino_local_no_paga_flete_aunque_exista_una_tarifa() {
        var tacna = destino(1, "Mercado Tacna", true);

        Cotizacion cotizacion = CotizadorDestinos.cotizar(LOTE, LOTE.cantidadKg(), List.of(tacna),
                Map.of(1L, soles("3.00")), Map.of(1L, soles("9.99")));

        var opcion = cotizacion.opciones().get(0);
        assertThat(opcion.costoFletePorKg()).isEqualTo(Dinero.CERO);
        assertThat(opcion.gananciaNeta()).isEqualTo(soles("3000.00"));
    }

    @Test
    void omite_los_destinos_sin_precio_o_sin_tarifa_y_dice_por_que() {
        var sinPrecio = destino(1, "Sin precio", true);
        var sinTarifa = destino(2, "Sin tarifa", false);
        var completo = destino(3, "Completo", false);

        Cotizacion cotizacion = CotizadorDestinos.cotizar(LOTE, LOTE.cantidadKg(), List.of(sinPrecio, sinTarifa, completo),
                Map.of(2L, soles("5.00"), 3L, soles("5.00")), Map.of(3L, soles("1.00")));

        assertThat(cotizacion.opciones()).hasSize(1);
        assertThat(cotizacion.omitidos()).extracting(o -> o.destino().nombreDestino() + ":" + o.motivo())
                .containsExactly("Sin precio:SIN_PRECIO", "Sin tarifa:SIN_TARIFA");
    }

    @Test
    void si_el_flete_supera_el_precio_la_ganancia_es_negativa_y_sigue_comparando() {
        var lejano = destino(1, "Lejano", false);
        var cercano = destino(2, "Cercano", false);

        Cotizacion cotizacion = CotizadorDestinos.cotizar(LOTE, LOTE.cantidadKg(), List.of(lejano, cercano),
                Map.of(1L, soles("2.00"), 2L, soles("2.00")), Map.of(1L, soles("2.50"), 2L, soles("2.10")));

        assertThat(cotizacion.opciones().get(0).destino().nombreDestino()).isEqualTo("Cercano");
        assertThat(cotizacion.opciones().get(0).gananciaNeta().esNegativo()).isTrue();
        assertThat(cotizacion.opciones().get(1).gananciaNeta()).isEqualTo(soles("-500.00"));
    }

    @Test
    void los_montos_se_redondean_a_centimos_sin_errores_de_decimales() {
        Lote lote = Lote.nuevo("Palta", new BigDecimal("12.250"), LocalDate.of(2026, 11, 5), "Sur", "x1x",
                Instant.parse("2026-11-06T15:00:00Z"), LocalDate.of(2026, 11, 6)).conId(2L);
        var destino = destino(1, "Mercado", false);

        Cotizacion cotizacion = CotizadorDestinos.cotizar(lote, lote.cantidadKg(), List.of(destino),
                Map.of(1L, soles("3.50")), Map.of(1L, soles("0.33")));

        var opcion = cotizacion.opciones().get(0);
        assertThat(opcion.ingresoEsperado().monto().toPlainString()).isEqualTo("42.88");
        assertThat(opcion.costoFleteTotal().monto().toPlainString()).isEqualTo("4.04");
        assertThat(opcion.gananciaNeta().monto().toPlainString()).isEqualTo("38.84");
    }

    @Test
    void sin_destinos_no_hay_mejor_opcion() {
        Cotizacion cotizacion = CotizadorDestinos.cotizar(LOTE, LOTE.cantidadKg(), List.of(), Map.of(), Map.of());

        assertThat(cotizacion.mejorOpcion()).isEmpty();
    }
}
