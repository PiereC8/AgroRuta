package pe.agroruta.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import pe.agroruta.application.service.Falsos.HashFalso;
import pe.agroruta.application.service.Falsos.NegociosEnMemoria;
import pe.agroruta.application.service.Falsos.UsuariosEnMemoria;
import pe.agroruta.domain.model.CredencialesInvalidasException;
import pe.agroruta.domain.model.Negocio;
import pe.agroruta.domain.model.Plan;
import pe.agroruta.domain.model.Rol;
import pe.agroruta.domain.model.Usuario;
import pe.agroruta.domain.port.in.IniciarSesionUseCase.Comando;

class IniciarSesionServiceTest {

    private static final Instant INICIO = Instant.parse("2026-11-06T15:00:00Z");

    private final UsuariosEnMemoria usuarios = new UsuariosEnMemoria();
    private final NegociosEnMemoria negocios = new NegociosEnMemoria(new ArrayList<>());
    private MutableClock reloj;
    private IniciarSesionService servicio;

    private static final class MutableClock extends Clock {
        private Instant ahora;

        MutableClock(Instant ahora) {
            this.ahora = ahora;
        }

        void avanzar(Duration duracion) {
            ahora = ahora.plus(duracion);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zona) {
            return this;
        }

        @Override
        public Instant instant() {
            return ahora;
        }
    }

    @BeforeEach
    void preparar() {
        reloj = new MutableClock(INICIO);
        negocios.guardar(Negocio.nuevo("chacra_demo", "Chacra Demo", Plan.CHACRA));
        usuarios.guardar("chacra_demo", Usuario.nuevo("dueno.demo", "hash:clave-segura-1", Rol.DUENO));
        usuarios.guardar(null, Usuario.nuevo("admin", "hash:clave-admin-1", Rol.ADMINISTRADOR_PLATAFORMA));
        servicio = new IniciarSesionService(usuarios, negocios, new HashFalso(),
                (negocio, nombre, rol) -> "token:" + negocio + ":" + nombre + ":" + rol, reloj);
    }

    private Comando login(String negocio, String usuario, String clave) {
        return new Comando(negocio, usuario, clave);
    }

    @Test
    void con_credenciales_correctas_emite_el_token_del_negocio() {
        var resultado = servicio.iniciar(login("chacra_demo", "dueno.demo", "clave-segura-1"));

        assertThat(resultado.token()).isEqualTo("token:chacra_demo:dueno.demo:DUENO");
        assertThat(resultado.rolUsuario()).isEqualTo(Rol.DUENO);
    }

    @Test
    void el_administrador_de_plataforma_entra_sin_negocio() {
        var resultado = servicio.iniciar(login(null, "admin", "clave-admin-1"));

        assertThat(resultado.rolUsuario()).isEqualTo(Rol.ADMINISTRADOR_PLATAFORMA);
        assertThat(resultado.identificadorNegocio()).isNull();
    }

    @Test
    void usuario_inexistente_clave_mala_y_negocio_inexistente_dan_el_mismo_error() {
        assertThatThrownBy(() -> servicio.iniciar(login("chacra_demo", "nadie", "clave-segura-1")))
                .isInstanceOf(CredencialesInvalidasException.class).hasMessage(CredencialesInvalidasException.MENSAJE);
        assertThatThrownBy(() -> servicio.iniciar(login("chacra_demo", "dueno.demo", "mala")))
                .isInstanceOf(CredencialesInvalidasException.class).hasMessage(CredencialesInvalidasException.MENSAJE);
        assertThatThrownBy(() -> servicio.iniciar(login("otro_negocio", "dueno.demo", "clave-segura-1")))
                .isInstanceOf(CredencialesInvalidasException.class).hasMessage(CredencialesInvalidasException.MENSAJE);
    }

    @Test
    void no_deja_entrar_a_un_usuario_de_otro_negocio() {
        assertThatThrownBy(() -> servicio.iniciar(login(null, "dueno.demo", "clave-segura-1")))
                .isInstanceOf(CredencialesInvalidasException.class);
    }

    @Test
    void tras_cinco_intentos_fallidos_bloquea_la_cuenta_aunque_la_clave_sea_correcta() {
        for (int i = 0; i < Usuario.MAXIMO_INTENTOS_FALLIDOS; i++) {
            assertThatThrownBy(() -> servicio.iniciar(login("chacra_demo", "dueno.demo", "mala")))
                    .isInstanceOf(CredencialesInvalidasException.class);
        }

        assertThatThrownBy(() -> servicio.iniciar(login("chacra_demo", "dueno.demo", "clave-segura-1")))
                .isInstanceOf(CredencialesInvalidasException.class);
    }

    @Test
    void pasado_el_bloqueo_la_cuenta_vuelve_a_funcionar() {
        for (int i = 0; i < Usuario.MAXIMO_INTENTOS_FALLIDOS; i++) {
            assertThatThrownBy(() -> servicio.iniciar(login("chacra_demo", "dueno.demo", "mala")))
                    .isInstanceOf(CredencialesInvalidasException.class);
        }
        reloj.avanzar(Usuario.DURACION_BLOQUEO.plusSeconds(1));

        var resultado = servicio.iniciar(login("chacra_demo", "dueno.demo", "clave-segura-1"));

        assertThat(resultado.rolUsuario()).isEqualTo(Rol.DUENO);
    }

    @Test
    void un_acceso_correcto_reinicia_el_contador_de_fallos() {
        for (int i = 0; i < Usuario.MAXIMO_INTENTOS_FALLIDOS - 1; i++) {
            assertThatThrownBy(() -> servicio.iniciar(login("chacra_demo", "dueno.demo", "mala")))
                    .isInstanceOf(CredencialesInvalidasException.class);
        }
        servicio.iniciar(login("chacra_demo", "dueno.demo", "clave-segura-1"));

        assertThatThrownBy(() -> servicio.iniciar(login("chacra_demo", "dueno.demo", "mala")))
                .isInstanceOf(CredencialesInvalidasException.class);
        var resultado = servicio.iniciar(login("chacra_demo", "dueno.demo", "clave-segura-1"));
        assertThat(resultado.rolUsuario()).isEqualTo(Rol.DUENO);
    }

    @Test
    void un_usuario_desactivado_no_entra() {
        var actual = usuarios.buscarPorNombre("chacra_demo", "dueno.demo").orElseThrow();
        usuarios.guardar("chacra_demo", new Usuario(actual.idUsuario(), actual.nombreUsuario(), actual.hashClave(),
                actual.rolUsuario(), 0, null, false));

        assertThatThrownBy(() -> servicio.iniciar(login("chacra_demo", "dueno.demo", "clave-segura-1")))
                .isInstanceOf(CredencialesInvalidasException.class);
    }
}
