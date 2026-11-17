package pe.agroruta.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import pe.agroruta.application.service.Falsos.GeneradorTokenFalso;
import pe.agroruta.application.service.Falsos.HashFalso;
import pe.agroruta.application.service.Falsos.NegociosEnMemoria;
import pe.agroruta.application.service.Falsos.UsuariosEnMemoria;
import pe.agroruta.domain.model.DominioException;
import pe.agroruta.domain.model.Negocio;
import pe.agroruta.domain.model.Plan;
import pe.agroruta.domain.model.ReglasVerificacion;
import pe.agroruta.domain.model.Rol;
import pe.agroruta.domain.model.Usuario;
import pe.agroruta.domain.port.in.IniciarSesionUseCase.Comando;
import pe.agroruta.domain.port.in.IniciarSesionUseCase.Resultado;
import pe.agroruta.domain.port.out.EnvioCorreoPort;
import pe.agroruta.domain.port.out.VerificacionSesionPort;

/** RFF-01 (verificación en dos pasos), sin base de datos: puertos en memoria y un correo que guarda lo enviado. */
class VerificarCodigoServiceTest {

    private static final Instant INICIO = Instant.parse("2026-11-17T15:00:00Z");

    private final UsuariosEnMemoria usuarios = new UsuariosEnMemoria();
    private final NegociosEnMemoria negocios = new NegociosEnMemoria(new ArrayList<>());
    private final VerificacionesEnMemoria verificaciones = new VerificacionesEnMemoria(usuarios);
    private final GeneradorTokenFalso generador = new GeneradorTokenFalso();
    private final CorreoGrabado correo = new CorreoGrabado();
    private RelojMovil reloj;
    private IniciarSesionService login;
    private VerificarCodigoService verificar;

    @BeforeEach
    void preparar() {
        reloj = new RelojMovil(INICIO);
        negocios.guardar(Negocio.nuevo("chacra_demo", "Chacra Demo", Plan.CHACRA));
        Usuario dueno = usuarios.guardar("chacra_demo", Usuario.nuevo("dueno.demo", "hash:clave-segura-1", Rol.DUENO));
        usuarios.guardar("chacra_demo", Usuario.nuevo("operador.demo", "hash:clave-segura-2", Rol.OPERADOR));
        verificaciones.correos.put(dueno.idUsuario(), "dueno@chacra.pe");
        login = new IniciarSesionService(usuarios, negocios, new HashFalso(),
                (negocio, nombre, rol) -> "token:" + negocio + ":" + nombre + ":" + rol, verificaciones, generador,
                correo, reloj);
        verificar = new VerificarCodigoService(negocios, verificaciones, generador,
                (negocio, nombre, rol) -> "token:" + negocio + ":" + nombre + ":" + rol, correo, reloj);
    }

    private Resultado entrarComoDueno() {
        return login.iniciar(new Comando("chacra_demo", "dueno.demo", "clave-segura-1"));
    }

    @Test
    void el_dueno_con_correo_no_recibe_token_sino_un_codigo_en_su_correo() {
        Resultado r = entrarComoDueno();

        assertThat(r.token()).isNull();
        assertThat(r.requiereCodigo()).isTrue();
        assertThat(r.correoOculto()).isEqualTo("d***o@chacra.pe");
        assertThat(correo.enviados).hasSize(1);
        assertThat(correo.enviados.get(0).destino()).isEqualTo("dueno@chacra.pe");
        assertThat(correo.ultimoCodigo()).matches("\\d{6}");
    }

    @Test
    void con_el_codigo_correcto_entrega_el_token_y_el_codigo_no_sirve_dos_veces() {
        Resultado r = entrarComoDueno();

        Resultado sesion = verificar.verificar("chacra_demo", r.desafio(), correo.ultimoCodigo());

        assertThat(sesion.token()).isEqualTo("token:chacra_demo:dueno.demo:DUENO");
        assertThatThrownBy(() -> verificar.verificar("chacra_demo", r.desafio(), correo.ultimoCodigo()))
                .isInstanceOf(DominioException.class).hasMessage(ReglasVerificacion.CODIGO_VENCIDO);
    }

    @Test
    void el_codigo_escrito_con_espacios_tambien_vale() {
        Resultado r = entrarComoDueno();
        String c = correo.ultimoCodigo();

        Resultado sesion = verificar.verificar("chacra_demo", r.desafio(), c.substring(0, 3) + " " + c.substring(3));

        assertThat(sesion.token()).isNotNull();
    }

    @Test
    void un_codigo_incorrecto_dice_cuantos_intentos_quedan_y_al_quinto_ya_no_sirve() {
        Resultado r = entrarComoDueno();
        String correcto = correo.ultimoCodigo();
        String malo = correcto.equals("999999") ? "111111" : "999999";

        assertThatThrownBy(() -> verificar.verificar("chacra_demo", r.desafio(), malo))
                .isInstanceOf(DominioException.class).hasMessageContaining("quedan 4 intentos");
        for (int i = 1; i < ReglasVerificacion.MAXIMO_INTENTOS; i++) {
            assertThatThrownBy(() -> verificar.verificar("chacra_demo", r.desafio(), malo))
                    .isInstanceOf(DominioException.class);
        }

        assertThatThrownBy(() -> verificar.verificar("chacra_demo", r.desafio(), correcto))
                .isInstanceOf(DominioException.class).hasMessage(ReglasVerificacion.CODIGO_VENCIDO);
    }

    @Test
    void un_codigo_que_no_tiene_seis_digitos_no_gasta_intentos() {
        Resultado r = entrarComoDueno();

        assertThatThrownBy(() -> verificar.verificar("chacra_demo", r.desafio(), "12ab"))
                .isInstanceOf(DominioException.class).hasMessageContaining("6 dígitos");

        assertThat(verificaciones.desafios.get(0).intentos).isZero();
    }

    @Test
    void el_codigo_vence_a_los_diez_minutos() {
        Resultado r = entrarComoDueno();
        reloj.avanzar(ReglasVerificacion.VIGENCIA.plusSeconds(1));

        assertThatThrownBy(() -> verificar.verificar("chacra_demo", r.desafio(), correo.ultimoCodigo()))
                .isInstanceOf(DominioException.class).hasMessage(ReglasVerificacion.CODIGO_VENCIDO);
    }

    @Test
    void un_desafio_inventado_no_sirve() {
        entrarComoDueno();

        assertThatThrownBy(() -> verificar.verificar("chacra_demo", "inventado", correo.ultimoCodigo()))
                .isInstanceOf(DominioException.class).hasMessage(ReglasVerificacion.CODIGO_VENCIDO);
    }

    @Test
    void reenviar_pide_esperar_un_minuto_y_el_codigo_viejo_deja_de_valer() {
        Resultado r = entrarComoDueno();
        String viejo = correo.ultimoCodigo();

        assertThatThrownBy(() -> verificar.reenviar("chacra_demo", r.desafio()))
                .isInstanceOf(DominioException.class).hasMessageContaining("Espera");

        reloj.avanzar(ReglasVerificacion.ESPERA_REENVIO);
        assertThat(verificar.reenviar("chacra_demo", r.desafio())).isEqualTo("d***o@chacra.pe");
        assertThat(correo.enviados).hasSize(2);

        assertThatThrownBy(() -> verificar.verificar("chacra_demo", r.desafio(), viejo))
                .isInstanceOf(DominioException.class).hasMessageContaining("no es correcto");
        assertThat(verificar.verificar("chacra_demo", r.desafio(), correo.ultimoCodigo()).token()).isNotNull();
    }

    @Test
    void no_se_puede_reenviar_mas_de_tres_veces() {
        Resultado r = entrarComoDueno();
        for (int i = 0; i < ReglasVerificacion.MAXIMO_REENVIOS; i++) {
            reloj.avanzar(ReglasVerificacion.ESPERA_REENVIO);
            verificar.reenviar("chacra_demo", r.desafio());
        }
        reloj.avanzar(ReglasVerificacion.ESPERA_REENVIO);

        assertThatThrownBy(() -> verificar.reenviar("chacra_demo", r.desafio()))
                .isInstanceOf(DominioException.class).hasMessage(ReglasVerificacion.DEMASIADOS_CODIGOS);
    }

    @Test
    void un_nuevo_inicio_de_sesion_anula_el_codigo_anterior() {
        Resultado primero = entrarComoDueno();
        String codigoPrimero = correo.ultimoCodigo();
        entrarComoDueno();

        assertThatThrownBy(() -> verificar.verificar("chacra_demo", primero.desafio(), codigoPrimero))
                .isInstanceOf(DominioException.class).hasMessage(ReglasVerificacion.CODIGO_VENCIDO);
    }

    @Test
    void mas_de_diez_codigos_en_una_hora_se_rechazan() {
        for (int i = 0; i < ReglasVerificacion.MAXIMO_CODIGOS_POR_HORA; i++) {
            entrarComoDueno();
        }

        assertThatThrownBy(this::entrarComoDueno)
                .isInstanceOf(DominioException.class).hasMessage(ReglasVerificacion.DEMASIADOS_CODIGOS);
        reloj.avanzar(Duration.ofHours(1).plusSeconds(1));
        assertThat(entrarComoDueno().requiereCodigo()).isTrue();
    }

    @Test
    void el_operador_entra_sin_codigo() {
        Resultado r = login.iniciar(new Comando("chacra_demo", "operador.demo", "clave-segura-2"));

        assertThat(r.token()).isEqualTo("token:chacra_demo:operador.demo:OPERADOR");
        assertThat(r.requiereCodigo()).isFalse();
        assertThat(correo.enviados).isEmpty();
    }

    @Test
    void el_dueno_sin_correo_entra_con_su_clave_y_se_le_avisa() {
        verificaciones.correos.clear();

        Resultado r = entrarComoDueno();

        assertThat(r.token()).isNotNull();
        assertThat(r.sinCorreo()).isTrue();
        assertThat(correo.enviados).isEmpty();
    }

    @Test
    void si_el_correo_no_sale_no_entrega_la_sesion() {
        correo.falla = true;

        assertThatThrownBy(this::entrarComoDueno)
                .isInstanceOf(DominioException.class).hasMessageContaining("No pudimos enviar el código");
    }

    @Test
    void oculta_el_correo_sin_mostrarlo_entero() {
        assertThat(ReglasVerificacion.ocultarCorreo("mattias@gmail.com")).isEqualTo("m*****s@gmail.com");
        assertThat(ReglasVerificacion.ocultarCorreo("ab@x.pe")).isEqualTo("a*b@x.pe");
        assertThat(ReglasVerificacion.ocultarCorreo("a@x.pe")).isEqualTo("*@x.pe");
    }

    // ---------- Falsos ----------

    static final class RelojMovil extends Clock {
        private Instant ahora;

        RelojMovil(Instant ahora) {
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

    record Enviado(String destino, String asunto, String cuerpo) {
    }

    static final class CorreoGrabado implements EnvioCorreoPort {
        final List<Enviado> enviados = new ArrayList<>();
        boolean falla;

        @Override
        public void enviar(String correoDestino, String asunto, String cuerpo) {
            if (falla) {
                throw new IllegalStateException("sin conexión");
            }
            enviados.add(new Enviado(correoDestino, asunto, cuerpo));
        }

        /** El asunto empieza con el código: «123456 es tu código…». */
        String ultimoCodigo() {
            return enviados.get(enviados.size() - 1).asunto().substring(0, 6);
        }
    }

    static final class VerificacionesEnMemoria implements VerificacionSesionPort {

        static final class Fila {
            final long id;
            final Long idUsuario;
            final String hashDesafio;
            String hashCodigo;
            Instant vence;
            Instant ultimoEnvio;
            int envios = 1;
            int intentos;
            boolean usado;

            Fila(long id, Long idUsuario, String hashDesafio, String hashCodigo, Instant ahora, Instant vence) {
                this.id = id;
                this.idUsuario = idUsuario;
                this.hashDesafio = hashDesafio;
                this.hashCodigo = hashCodigo;
                this.ultimoEnvio = ahora;
                this.vence = vence;
            }
        }

        final Map<Long, String> correos = new HashMap<>();
        final List<Fila> desafios = new ArrayList<>();
        private final UsuariosEnMemoria usuarios;

        VerificacionesEnMemoria(UsuariosEnMemoria usuarios) {
            this.usuarios = usuarios;
        }

        private Fila fila(Long id) {
            return desafios.stream().filter(f -> f.id == id).findFirst().orElseThrow();
        }

        @Override
        public Optional<String> correoDe(String identificadorNegocio, Long idUsuario) {
            return Optional.ofNullable(correos.get(idUsuario));
        }

        @Override
        public int codigosDesde(String identificadorNegocio, Long idUsuario, Instant desde) {
            return desafios.stream().filter(f -> f.idUsuario.equals(idUsuario) && !f.ultimoEnvio.isBefore(desde))
                    .mapToInt(f -> f.envios).sum();
        }

        @Override
        public void crear(String identificadorNegocio, Long idUsuario, String hashDesafio, String hashCodigo,
                          Instant ahora, Instant vence) {
            desafios.stream().filter(f -> f.idUsuario.equals(idUsuario)).forEach(f -> f.usado = true);
            desafios.add(new Fila(desafios.size() + 1L, idUsuario, hashDesafio, hashCodigo, ahora, vence));
        }

        @Override
        public Optional<Desafio> buscar(String identificadorNegocio, String hashDesafio) {
            return desafios.stream().filter(f -> f.hashDesafio.equals(hashDesafio)).findFirst().map(f -> {
                Usuario u = usuarios.usuarios.values().stream().filter(x -> x.idUsuario().equals(f.idUsuario))
                        .findFirst().orElseThrow();
                return new Desafio(f.id, f.idUsuario, u.nombreUsuario(), u.rolUsuario(), u.usuarioActivo(),
                        f.hashCodigo, f.vence, f.ultimoEnvio, f.envios, f.intentos, f.usado);
            });
        }

        @Override
        public void sumarIntentoFallido(String identificadorNegocio, Long idVerificacion) {
            fila(idVerificacion).intentos++;
        }

        @Override
        public void reemplazarCodigo(String identificadorNegocio, Long idVerificacion, String hashCodigo,
                                     Instant ahora, Instant vence) {
            Fila f = fila(idVerificacion);
            f.hashCodigo = hashCodigo;
            f.ultimoEnvio = ahora;
            f.vence = vence;
            f.envios++;
            f.intentos = 0;
        }

        @Override
        public boolean consumir(String identificadorNegocio, Long idVerificacion, Instant ahora) {
            Fila f = fila(idVerificacion);
            if (f.usado || !ahora.isBefore(f.vence) || f.intentos >= ReglasVerificacion.MAXIMO_INTENTOS) {
                return false;
            }
            f.usado = true;
            return true;
        }
    }
}
