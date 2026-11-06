package pe.agroruta.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LoteTest {

    private static final Instant AHORA = Instant.parse("2026-11-06T15:00:00Z");
    private static final LocalDate HOY = LocalDate.of(2026, 11, 6);

    private Lote lote(String producto, String kg, LocalDate cosecha, String chacra) {
        return Lote.nuevo(producto, new BigDecimal(kg), cosecha, chacra, "dueno.demo", AHORA, HOY);
    }

    @Test
    void crea_un_lote_registrado_con_tres_decimales_y_textos_recortados() {
        Lote lote = lote("  Palta Hass ", "1250.5", HOY, " Chacra Sur ");

        assertThat(lote.estadoLote()).isEqualTo(EstadoLote.REGISTRADO);
        assertThat(lote.productoLote()).isEqualTo("Palta Hass");
        assertThat(lote.chacraOrigen()).isEqualTo("Chacra Sur");
        assertThat(lote.cantidadKg().toPlainString()).isEqualTo("1250.500");
        assertThat(lote.idLote()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-5", "0.0001", "10000000", "1.2345"})
    void rechaza_cantidades_invalidas(String kg) {
        assertThatThrownBy(() -> lote("Palta", kg, HOY, "Chacra Sur")).isInstanceOf(DominioException.class);
    }

    @Test
    void acepta_la_cantidad_maxima_y_el_minimo_de_un_gramo() {
        lote("Palta", "9999999.999", HOY, "Chacra Sur");
        lote("Palta", "0.001", HOY, "Chacra Sur");
    }

    @Test
    void rechaza_fecha_de_cosecha_futura_pero_acepta_hoy() {
        lote("Palta", "10", HOY, "Chacra Sur");

        assertThatThrownBy(() -> lote("Palta", "10", HOY.plusDays(1), "Chacra Sur"))
                .isInstanceOf(DominioException.class).hasMessageContaining("futura");
    }

    @Test
    void rechaza_datos_obligatorios_vacios() {
        assertThatThrownBy(() -> lote(" ", "10", HOY, "Chacra Sur")).isInstanceOf(DominioException.class);
        assertThatThrownBy(() -> lote("Palta", "10", HOY, "")).isInstanceOf(DominioException.class);
        assertThatThrownBy(() -> lote("Palta", "10", null, "Chacra Sur")).isInstanceOf(DominioException.class);
        assertThatThrownBy(() -> Lote.nuevo("Palta", null, HOY, "Chacra Sur", "dueno.demo", AHORA, HOY))
                .isInstanceOf(DominioException.class);
    }
}
