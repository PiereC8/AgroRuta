package pe.agroruta.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import pe.agroruta.domain.model.DominioException;
import pe.agroruta.domain.model.Negocio;
import pe.agroruta.domain.model.Plan;
import pe.agroruta.domain.port.out.EnvioCorreoPort;
import pe.agroruta.domain.port.out.RecuperacionClavePort;

class RecuperarClaveServiceTest {

    private static final Instant AHORA = Instant.parse("2026-11-11T15:00:00Z");
    private static final String URL = "https://agroruta.vercel.app";

    static class RecuperacionesEnMemoria implements RecuperacionClavePort {
        final Map<String, Cuenta> cuentas = new HashMap<>();
        final Map<Long, String> claves = new HashMap<>();
        final List<Object[]> solicitudes = new ArrayList<>();

        @Override
        public Optional<Cuenta> buscarCuenta(String negocio, String nombreUsuario) {
            return Optional.ofNullable(cuentas.get(negocio + "/" + nombreUsuario));
        }

        @Override
        public int solicitudesDesde(String negocio, Long idUsuario, Instant desde) {
            return (int) solicitudes.stream()
                    .filter(s -> s[0].equals(idUsuario) && !((Instant) s[2]).isBefore(desde)).count();
        }

        @Override
        public void guardarSolicitud(String negocio, Long idUsuario, String hashToken, Instant creada, Instant vence) {
            solicitudes.add(new Object[] {idUsuario, hashToken, creada, vence, null});
        }

        @Override
        public Optional<Long> consumirToken(String negocio, String hashToken, Instant ahora) {
            for (Object[] s : solicitudes) {
                if (s[1].equals(hashToken) && s[4] == null && ahora.isBefore((Instant) s[3])) {
                    Long id = (Long) s[0];
                    solicitudes.stream().filter(o -> o[0].equals(id) && o[4] == null).forEach(o -> o[4] = ahora);
                    return Optional.of(id);
                }
            }
            return Optional.empty();
        }

        @Override
        public void cambiarClave(String negocio, Long idUsuario, String hashClave) {
            claves.put(idUsuario, hashClave);
        }
    }

    static class CorreoGrabador implements EnvioCorreoPort {
        final List<String[]> enviados = new ArrayList<>();
        boolean falla;

        @Override
        public void enviar(String correoDestino, String asunto, String cuerpo) {
            if (falla) {
                throw new IllegalStateException("sin conexión");
            }
            enviados.add(new String[] {correoDestino, asunto, cuerpo});
        }
    }

    private final RecuperacionesEnMemoria recuperaciones = new RecuperacionesEnMemoria();
    private final CorreoGrabador correo = new CorreoGrabador();
    private Instant ahora = AHORA;
    private RecuperarClaveService servicio;

    @BeforeEach
    void preparar() {
        var negocios = new Falsos.NegociosEnMemoria(new ArrayList<>());
        negocios.registrados.put("chacra_demo", new Negocio(1L, "chacra_demo", "Chacra", Plan.CHACRA, true));
        negocios.registrados.put("cerrado", new Negocio(2L, "cerrado", "Cerrado", Plan.CHACRA, false));
        recuperaciones.cuentas.put("chacra_demo/rosa", new RecuperacionClavePort.Cuenta(5L, "rosa", "rosa@correo.pe",
                true));
        recuperaciones.cuentas.put("chacra_demo/sin.correo", new RecuperacionClavePort.Cuenta(6L, "sin.correo", null,
                true));
        recuperaciones.cuentas.put("chacra_demo/inactivo", new RecuperacionClavePort.Cuenta(7L, "inactivo",
                "x@correo.pe", false));
        servicio = new RecuperarClaveService(negocios, recuperaciones, new Falsos.GeneradorTokenFalso(),
                new Falsos.HashFalso(), correo, new Clock() {
                    @Override
                    public ZoneOffset getZone() {
                        return ZoneOffset.UTC;
                    }

                    @Override
                    public Clock withZone(java.time.ZoneId zona) {
                        return this;
                    }

                    @Override
                    public Instant instant() {
                        return ahora;
                    }
                });
    }

    @Test
    void manda_un_enlace_con_el_token_despues_del_numeral_y_solo_guarda_su_hash() {
        servicio.solicitar("chacra_demo", "rosa", URL);

        assertThat(correo.enviados).hasSize(1);
        assertThat(correo.enviados.get(0)[0]).isEqualTo("rosa@correo.pe");
        assertThat(correo.enviados.get(0)[2])
                .contains(URL + "/recuperar.html?negocio=chacra_demo#token=token-1")
                .contains("30 minutos");
        assertThat(recuperaciones.solicitudes.get(0)[1]).isEqualTo("hash:token-1");
        assertThat(recuperaciones.solicitudes.get(0)[3]).isEqualTo(AHORA.plus(Duration.ofMinutes(30)));
    }

    @Test
    void no_hace_nada_ni_avisa_si_la_cuenta_no_existe_no_tiene_correo_esta_inactiva_o_el_negocio_no_vale() {
        servicio.solicitar("chacra_demo", "no.existe", URL);
        servicio.solicitar("chacra_demo", "sin.correo", URL);
        servicio.solicitar("chacra_demo", "inactivo", URL);
        servicio.solicitar("cerrado", "rosa", URL);
        servicio.solicitar("no_existe", "rosa", URL);
        servicio.solicitar(" ", "rosa", URL);

        assertThat(correo.enviados).isEmpty();
        assertThat(recuperaciones.solicitudes).isEmpty();
    }

    @Test
    void como_maximo_tres_enlaces_por_hora() {
        for (int i = 0; i < 5; i++) {
            servicio.solicitar("chacra_demo", "rosa", URL);
        }
        assertThat(correo.enviados).hasSize(RecuperarClaveService.MAXIMO_SOLICITUDES_POR_HORA);

        ahora = AHORA.plus(Duration.ofMinutes(61));
        servicio.solicitar("chacra_demo", "rosa", URL);
        assertThat(correo.enviados).hasSize(4);
    }

    @Test
    void si_el_correo_falla_no_se_lo_dice_a_quien_lo_pidio() {
        correo.falla = true;

        servicio.solicitar("chacra_demo", "rosa", URL);

        assertThat(recuperaciones.solicitudes).hasSize(1);
    }

    @Test
    void el_enlace_cambia_la_clave_una_sola_vez() {
        servicio.solicitar("chacra_demo", "rosa", URL);

        servicio.restablecer("chacra_demo", "token-1", "clave-nueva-1");
        assertThat(recuperaciones.claves).containsEntry(5L, "hash:clave-nueva-1");

        assertThatThrownBy(() -> servicio.restablecer("chacra_demo", "token-1", "otra-clave-2"))
                .isInstanceOf(DominioException.class).hasMessage(RecuperarClaveService.ENLACE_INVALIDO);
        assertThat(recuperaciones.claves).containsEntry(5L, "hash:clave-nueva-1");
    }

    @Test
    void usar_un_enlace_anula_los_demas_pedidos_antes() {
        servicio.solicitar("chacra_demo", "rosa", URL);
        servicio.solicitar("chacra_demo", "rosa", URL);

        servicio.restablecer("chacra_demo", "token-2", "clave-nueva-1");

        assertThatThrownBy(() -> servicio.restablecer("chacra_demo", "token-1", "otra-clave-2"))
                .isInstanceOf(DominioException.class);
    }

    @Test
    void un_enlace_vencido_falso_o_de_otro_negocio_no_sirve() {
        servicio.solicitar("chacra_demo", "rosa", URL);

        assertThatThrownBy(() -> servicio.restablecer("chacra_demo", "inventado", "clave-nueva-1"))
                .isInstanceOf(DominioException.class);
        assertThatThrownBy(() -> servicio.restablecer("cerrado", "token-1", "clave-nueva-1"))
                .isInstanceOf(DominioException.class);
        ahora = AHORA.plus(Duration.ofMinutes(31));
        assertThatThrownBy(() -> servicio.restablecer("chacra_demo", "token-1", "clave-nueva-1"))
                .isInstanceOf(DominioException.class);
        assertThat(recuperaciones.claves).isEmpty();
    }

    @Test
    void una_clave_corta_no_gasta_el_enlace() {
        servicio.solicitar("chacra_demo", "rosa", URL);

        assertThatThrownBy(() -> servicio.restablecer("chacra_demo", "token-1", "corta"))
                .isInstanceOf(DominioException.class).hasMessageContaining("entre 8 y 72");
        servicio.restablecer("chacra_demo", "token-1", "clave-nueva-1");
        assertThat(recuperaciones.claves).containsEntry(5L, "hash:clave-nueva-1");
    }
}
