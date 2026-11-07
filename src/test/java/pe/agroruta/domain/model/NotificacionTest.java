package pe.agroruta.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class NotificacionTest {

    private static final Instant AHORA = Instant.parse("2026-11-07T15:00:00Z");

    private static Notificacion nueva() {
        return Notificacion.nueva(new Aviso("pago-recibido:4", TipoAviso.PAGO_RECIBIDO, "Pago", "Cuerpo"),
                "dueno@empresa.pe", AHORA);
    }

    @Test
    void nace_pendiente_y_lista_para_enviarse_con_una_clave_por_aviso_y_correo() {
        Notificacion n = nueva();

        assertThat(n.estado()).isEqualTo(EstadoNotificacion.PENDIENTE);
        assertThat(n.claveUnica()).isEqualTo("pago-recibido:4|dueno@empresa.pe");
        assertThat(n.proximoIntento()).isEqualTo(AHORA);
        assertThat(n.intentos()).isZero();
    }

    @Test
    void al_enviarse_queda_enviada_con_su_fecha() {
        Notificacion n = nueva().comoEnviada(AHORA.plusSeconds(3));

        assertThat(n.estado()).isEqualTo(EstadoNotificacion.ENVIADA);
        assertThat(n.fechaEnvio()).isEqualTo(AHORA.plusSeconds(3));
        assertThat(n.ultimoError()).isNull();
    }

    @Test
    void un_fallo_reprograma_con_espera_creciente() {
        Notificacion primero = nueva().conFallo("sin red", AHORA);
        Notificacion segundo = primero.conFallo("sin red", AHORA);

        assertThat(primero.estado()).isEqualTo(EstadoNotificacion.PENDIENTE);
        assertThat(primero.intentos()).isEqualTo(1);
        assertThat(primero.proximoIntento()).isEqualTo(AHORA.plus(Duration.ofMinutes(1)));
        assertThat(segundo.proximoIntento()).isEqualTo(AHORA.plus(Duration.ofMinutes(5)));
        assertThat(segundo.ultimoError()).isEqualTo("sin red");
    }

    @Test
    void despues_del_ultimo_intento_queda_fallida_y_no_se_reintenta() {
        Notificacion n = nueva();
        for (int i = 0; i < Notificacion.MAXIMO_INTENTOS; i++) {
            n = n.conFallo("sin red", AHORA);
        }

        assertThat(n.estado()).isEqualTo(EstadoNotificacion.FALLIDA);
        assertThat(n.intentos()).isEqualTo(Notificacion.MAXIMO_INTENTOS);
    }

    @Test
    void el_error_guardado_se_recorta() {
        assertThat(nueva().conFallo("x".repeat(1000), AHORA).ultimoError()).hasSize(300);
    }
}
