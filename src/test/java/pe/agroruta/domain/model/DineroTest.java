package pe.agroruta.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class DineroTest {

    @Test
    void suma_sin_los_errores_de_los_decimales_binarios() {
        assertThat(Dinero.de("0.10").sumar(Dinero.de("0.20"))).isEqualTo(Dinero.de("0.30"));
    }

    @Test
    void siempre_guarda_dos_decimales() {
        assertThat(Dinero.de("5").monto().toPlainString()).isEqualTo("5.00");
        assertThat(Dinero.de("2.345").monto().toPlainString()).isEqualTo("2.35");
        assertThat(Dinero.de("2.344").monto().toPlainString()).isEqualTo("2.34");
    }

    @Test
    void multiplica_precio_por_kg_y_redondea_una_sola_vez_al_final() {
        Dinero ingreso = Dinero.de("3.50").multiplicar(new BigDecimal("12.250"));

        assertThat(ingreso.monto().toPlainString()).isEqualTo("42.88");
    }

    @Test
    void la_ganancia_puede_ser_negativa() {
        Dinero ganancia = Dinero.de("100.00").restar(Dinero.de("120.50"));

        assertThat(ganancia.esNegativo()).isTrue();
        assertThat(ganancia).isEqualTo(Dinero.de("-20.50"));
    }

    @Test
    void compara_montos() {
        assertThat(Dinero.de("10.00")).isGreaterThan(Dinero.de("9.99"));
        assertThat(Dinero.CERO.esNegativo()).isFalse();
    }

    @Test
    void rechaza_un_monto_nulo() {
        assertThatThrownBy(() -> new Dinero(null)).isInstanceOf(DominioException.class);
    }
}
