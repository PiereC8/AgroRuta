package pe.agroruta.infrastructure.adapter.out.comprobante;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class MontoEnLetrasTest {

    private static String letras(String monto) {
        return MontoEnLetras.soles(new BigDecimal(monto));
    }

    @Test
    void escribe_los_montos_como_en_un_comprobante() {
        assertThat(letras("475.00")).isEqualTo("CUATROCIENTOS SETENTA Y CINCO CON 00/100 SOLES");
        assertThat(letras("0.50")).isEqualTo("CERO CON 50/100 SOLES");
        assertThat(letras("1")).isEqualTo("UNO CON 00/100 SOLES");
        assertThat(letras("100")).isEqualTo("CIEN CON 00/100 SOLES");
        assertThat(letras("101.99")).isEqualTo("CIENTO UNO CON 99/100 SOLES");
        assertThat(letras("21")).isEqualTo("VEINTIUNO CON 00/100 SOLES");
        assertThat(letras("6372.00")).isEqualTo("SEIS MIL TRESCIENTOS SETENTA Y DOS CON 00/100 SOLES");
    }

    @Test
    void usa_mil_veintiun_mil_y_millon_bien() {
        assertThat(letras("1000")).isEqualTo("MIL CON 00/100 SOLES");
        assertThat(letras("21000")).isEqualTo("VEINTIUN MIL CON 00/100 SOLES");
        assertThat(letras("31500")).isEqualTo("TREINTA Y UN MIL QUINIENTOS CON 00/100 SOLES");
        assertThat(letras("1000000")).isEqualTo("UN MILLON CON 00/100 SOLES");
        assertThat(letras("2100100.05")).isEqualTo("DOS MILLONES CIEN MIL CIEN CON 05/100 SOLES");
    }

    @Test
    void redondea_a_centimos() {
        assertThat(letras("10.005")).isEqualTo("DIEZ CON 01/100 SOLES");
    }
}
