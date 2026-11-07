package pe.agroruta.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import pe.agroruta.application.service.EquipoServiceTest.EquipoEnMemoria;
import pe.agroruta.domain.model.Dinero;
import pe.agroruta.domain.model.EstadoNotificacion;
import pe.agroruta.domain.model.HechoDelNegocio.EntregaRegistrada;
import pe.agroruta.domain.model.HechoDelNegocio.PagoRecibido;
import pe.agroruta.domain.model.MiembroEquipo;
import pe.agroruta.domain.model.Notificacion;
import pe.agroruta.domain.model.PoliticaAvisos;
import pe.agroruta.domain.model.Rol;
import pe.agroruta.domain.port.out.EnvioCorreoPort;
import pe.agroruta.domain.port.out.NotificacionRepositoryPort;

class AvisosServiceTest {

    static class BandejaEnMemoria implements NotificacionRepositoryPort {
        final List<Notificacion> todas = new ArrayList<>();
        final Set<String> claves = new HashSet<>();
        boolean fallar;

        @Override
        public boolean encolar(Notificacion notificacion) {
            if (fallar) {
                throw new IllegalStateException("base caída");
            }
            if (!claves.add(notificacion.claveUnica())) {
                return false;
            }
            todas.add(notificacion.conId((long) todas.size() + 1));
            return true;
        }

        @Override
        public List<Notificacion> pendientes(Instant ahora, int limite) {
            return todas.stream().filter(n -> n.estado() == EstadoNotificacion.PENDIENTE
                    && !n.proximoIntento().isAfter(ahora)).limit(limite).toList();
        }

        @Override
        public void guardar(Notificacion notificacion) {
            for (int i = 0; i < todas.size(); i++) {
                if (todas.get(i).idNotificacion().equals(notificacion.idNotificacion())) {
                    todas.set(i, notificacion);
                }
            }
        }

        @Override
        public List<Notificacion> recientes(int limite) {
            return todas.stream().limit(limite).toList();
        }
    }

    static class CorreoFalso implements EnvioCorreoPort {
        final List<String> enviados = new ArrayList<>();
        String error;

        @Override
        public void enviar(String correoDestino, String asunto, String cuerpo) {
            if (error != null) {
                throw new IllegalStateException(error);
            }
            enviados.add(correoDestino + "|" + asunto);
        }
    }

    static final Instant AHORA = Instant.parse("2026-11-07T15:00:00Z");

    private EquipoEnMemoria equipo;
    private BandejaEnMemoria bandeja;
    private AvisosService servicio;

    @BeforeEach
    void preparar() {
        equipo = new EquipoEnMemoria();
        equipo.miembros.add(new MiembroEquipo(1L, "dueno.demo", Rol.DUENO, "dueno@empresa.pe", true, true));
        equipo.miembros.add(new MiembroEquipo(2L, "operador.uno", Rol.OPERADOR, "op1@empresa.pe", true, true));
        equipo.miembros.add(new MiembroEquipo(3L, "operador.dos", Rol.OPERADOR, null, true, true));
        bandeja = new BandejaEnMemoria();
        servicio = new AvisosService(new PoliticaAvisos(new BigDecimal("10")), equipo, bandeja,
                Clock.fixed(AHORA, ZoneId.of("America/Lima")));
    }

    private static EntregaRegistrada entregaConPerdida() {
        return new EntregaRegistrada(7L, "Ana Torres", "Palta Hass", "Mercado Lima", new BigDecimal("100.000"),
                new BigDecimal("3.000"), new BigDecimal("2.000"), Dinero.de("-50.00"), Dinero.de("120.00"));
    }

    @Test
    void un_aviso_del_dueno_solo_encola_un_correo_para_el_dueno() {
        servicio.publicar(entregaConPerdida());

        assertThat(bandeja.todas).hasSize(1);
        assertThat(bandeja.todas.get(0).correoDestino()).isEqualTo("dueno@empresa.pe");
        assertThat(bandeja.todas.get(0).estado()).isEqualTo(EstadoNotificacion.PENDIENTE);
    }

    @Test
    void las_alertas_del_envio_llegan_a_todos_los_roles_que_toca_y_solo_si_tienen_correo() {
        servicio.publicar(new pe.agroruta.domain.model.HechoDelNegocio.AlertaDeEnvio(11L, 6L,
                pe.agroruta.domain.model.TipoAlerta.DETENIDO, "Mercado Lima", "Detenido"));

        assertThat(bandeja.todas).extracting(Notificacion::correoDestino)
                .containsExactlyInAnyOrder("dueno@empresa.pe", "op1@empresa.pe");
    }

    @Test
    void el_mismo_hecho_publicado_dos_veces_no_duplica_el_correo() {
        servicio.publicar(entregaConPerdida());
        servicio.publicar(entregaConPerdida());

        assertThat(bandeja.todas).hasSize(1);
    }

    @Test
    void quien_apago_los_avisos_o_esta_desactivado_no_recibe_nada() {
        equipo.actualizarCorreo(1L, "dueno@empresa.pe", false);

        servicio.publicar(new PagoRecibido(4L, "Ana Torres", Dinero.de("100.00"), 7L));
        assertThat(bandeja.todas).isEmpty();

        equipo.actualizarCorreo(1L, "dueno@empresa.pe", true);
        equipo.actualizarActivo(1L, false);
        servicio.publicar(new PagoRecibido(5L, "Ana Torres", Dinero.de("100.00"), 7L));
        assertThat(bandeja.todas).isEmpty();
    }

    @Test
    void un_hecho_que_no_merece_aviso_no_encola_nada() {
        servicio.publicar(new EntregaRegistrada(8L, "Ana Torres", "Palta", "Mercado", new BigDecimal("100.000"),
                BigDecimal.ONE, BigDecimal.ONE, Dinero.de("500.00"), Dinero.de("10.00")));

        assertThat(bandeja.todas).isEmpty();
    }

    @Test
    void si_la_bandeja_falla_publicar_no_lanza_nada() {
        bandeja.fallar = true;

        assertThatCode(() -> servicio.publicar(entregaConPerdida())).doesNotThrowAnyException();
    }

    @Test
    void el_resumen_de_dos_hechos_distintos_genera_dos_correos() {
        servicio.publicar(entregaConPerdida());
        servicio.publicar(new PagoRecibido(4L, "Ana Torres", Dinero.de("100.00"), 7L));

        assertThat(bandeja.todas).hasSize(2);
        assertThat(LocalDate.ofInstant(bandeja.todas.get(0).fechaCreacion(), ZoneId.of("America/Lima")))
                .isEqualTo(LocalDate.of(2026, 11, 7));
    }
}
