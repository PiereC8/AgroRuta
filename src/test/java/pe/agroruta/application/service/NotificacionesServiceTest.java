package pe.agroruta.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import pe.agroruta.application.service.AvisosServiceTest.BandejaEnMemoria;
import pe.agroruta.application.service.AvisosServiceTest.CorreoFalso;
import pe.agroruta.domain.model.Aviso;
import pe.agroruta.domain.model.DominioException;
import pe.agroruta.domain.model.EstadoNotificacion;
import pe.agroruta.domain.model.Notificacion;
import pe.agroruta.domain.model.TipoAviso;

class NotificacionesServiceTest {

    private static final Instant AHORA = Instant.parse("2026-11-07T15:00:00Z");

    private BandejaEnMemoria bandeja;
    private CorreoFalso correo;
    private NotificacionesService servicio;

    @BeforeEach
    void preparar() {
        bandeja = new BandejaEnMemoria();
        correo = new CorreoFalso();
        servicio = new NotificacionesService(bandeja, correo, Clock.fixed(AHORA, ZoneId.of("America/Lima")));
        bandeja.encolar(Notificacion.nueva(new Aviso("pago:1", TipoAviso.PAGO_RECIBIDO, "Pago", "Cuerpo"),
                "dueno@empresa.pe", AHORA));
    }

    @Test
    void despachar_envia_los_pendientes_y_los_marca_como_enviados() {
        assertThat(servicio.despachar()).isEqualTo(1);

        assertThat(correo.enviados).containsExactly("dueno@empresa.pe|Pago");
        assertThat(bandeja.todas.get(0).estado()).isEqualTo(EstadoNotificacion.ENVIADA);
        assertThat(servicio.despachar()).isZero();
    }

    @Test
    void si_el_correo_falla_el_aviso_queda_pendiente_para_mas_tarde_y_no_se_lanza_nada() {
        correo.error = "sin conexión con el servidor de correo";

        assertThat(servicio.despachar()).isZero();

        Notificacion n = bandeja.todas.get(0);
        assertThat(n.estado()).isEqualTo(EstadoNotificacion.PENDIENTE);
        assertThat(n.intentos()).isEqualTo(1);
        assertThat(n.ultimoError()).contains("sin conexión");
        assertThat(n.proximoIntento()).isEqualTo(AHORA.plus(Duration.ofMinutes(1)));
        assertThat(servicio.despachar()).isZero();
        assertThat(bandeja.todas.get(0).intentos()).isEqualTo(1);
    }

    @Test
    void al_volver_el_correo_el_aviso_atrasado_sale() {
        correo.error = "caído";
        servicio.despachar();
        correo.error = null;
        NotificacionesService luego = new NotificacionesService(bandeja, correo,
                Clock.fixed(AHORA.plus(Duration.ofMinutes(2)), ZoneId.of("America/Lima")));

        assertThat(luego.despachar()).isEqualTo(1);
        assertThat(bandeja.todas.get(0).estado()).isEqualTo(EstadoNotificacion.ENVIADA);
    }

    @Test
    void probar_envia_un_correo_de_prueba_y_lo_deja_en_el_historial() {
        servicio.probar(" Dueno@Empresa.PE ");

        assertThat(correo.enviados).contains("dueno@empresa.pe|Prueba de correo de AgroRuta");
        assertThat(bandeja.todas).anyMatch(n -> n.tipo() == TipoAviso.PRUEBA
                && n.estado() == EstadoNotificacion.ENVIADA);
    }

    @Test
    void probar_con_un_correo_malo_o_con_el_servidor_caido_explica_el_problema() {
        assertThatThrownBy(() -> servicio.probar("malo")).isInstanceOf(DominioException.class);
        correo.error = "Authentication failed";

        assertThatThrownBy(() -> servicio.probar("dueno@empresa.pe"))
                .isInstanceOf(DominioException.class).hasMessageContaining("No se pudo enviar");
    }

    @Test
    void el_historial_devuelve_lo_reciente() {
        assertThat(servicio.recientes(10)).hasSize(1);
    }
}
