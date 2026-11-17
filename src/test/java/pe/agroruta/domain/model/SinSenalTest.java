package pe.agroruta.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class SinSenalTest {

    private static final Instant SALIDA = Instant.parse("2026-11-17T10:00:00Z");

    private static Envio envio(EstadoEnvio estado) {
        return new Envio(1L, 1L, 1L, 1L, 1L, estado, SALIDA, SALIDA, null, "operador.demo");
    }

    @Test
    void sin_ninguna_posicion_desde_iniciar_ruta_hay_alerta_pasado_el_umbral() {
        assertThat(ReglasSeguimiento.sinSenal(envio(EstadoEnvio.EN_RUTA), null, SALIDA.plusSeconds(9 * 60)))
                .isEmpty();
        var alerta = ReglasSeguimiento.sinSenal(envio(EstadoEnvio.EN_RUTA), null, SALIDA.plusSeconds(25 * 60));

        assertThat(alerta).isPresent();
        assertThat(alerta.get().tipoAlerta()).isEqualTo(TipoAlerta.SIN_SENAL);
        assertThat(alerta.get().descripcionAlerta()).startsWith("Lleva 25 min sin señal del conductor")
                .contains("ninguna posición");
    }

    @Test
    void cuenta_desde_la_ultima_posicion_y_solo_en_ruta() {
        Instant ultima = SALIDA.plusSeconds(3600);
        Instant ahora = ultima.plusSeconds(2 * 3600 + 5 * 60);

        assertThat(ReglasSeguimiento.sinSenal(envio(EstadoEnvio.EN_RUTA), ultima, ahora).orElseThrow()
                .descripcionAlerta()).isEqualTo("Lleva 2 h 5 min sin señal del conductor");
        assertThat(ReglasSeguimiento.sinSenal(envio(EstadoEnvio.EN_RUTA), ultima, ultima.plusSeconds(60))).isEmpty();
        assertThat(ReglasSeguimiento.sinSenal(envio(EstadoEnvio.ENTREGADO), ultima, ahora)).isEmpty();
    }
}
