package pe.agroruta.infrastructure.adapter.in.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;

import pe.agroruta.domain.model.Rol;
import pe.agroruta.domain.model.SesionEnlace;
import pe.agroruta.domain.model.TipoEnlace;
import pe.agroruta.infrastructure.adapter.out.seguridad.ServicioJwt;

class InterceptorAutenticacionStompTest {

    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-11-06T15:00:00Z"), ZoneOffset.UTC);

    private final ServicioJwt jwt = new ServicioJwt("secreto-de-prueba-con-mas-de-32-caracteres!!",
            Duration.ofHours(8), RELOJ);
    private final SesionEnlace sesionConductor = new SesionEnlace("empresa_demo", 7L, TipoEnlace.CONDUCTOR);
    private final SesionEnlace sesionComprador = new SesionEnlace("empresa_demo", 7L, TipoEnlace.COMPRADOR);
    private final InterceptorAutenticacionStomp interceptor = new InterceptorAutenticacionStomp(jwt, token -> {
        if ("tok-conductor".equals(token)) {
            return Optional.of(sesionConductor);
        }
        if ("tok-comprador".equals(token)) {
            return Optional.of(sesionComprador);
        }
        return Optional.empty();
    });

    private Message<?> mensaje(StompCommand comando, String destino, UsuarioWs usuario, String... cabeceras) {
        StompHeaderAccessor accesor = StompHeaderAccessor.create(comando);
        accesor.setLeaveMutable(true);
        if (destino != null) {
            accesor.setDestination(destino);
        }
        if (usuario != null) {
            accesor.setUser(usuario);
        }
        for (int i = 0; i < cabeceras.length; i += 2) {
            accesor.addNativeHeader(cabeceras[i], cabeceras[i + 1]);
        }
        return MessageBuilder.createMessage(new byte[0], accesor.getMessageHeaders());
    }

    private UsuarioWs conectar(String... cabeceras) {
        Message<?> mensaje = mensaje(StompCommand.CONNECT, null, null, cabeceras);
        interceptor.preSend(mensaje, null);
        return (UsuarioWs) MessageHeaderAccessor.getAccessor(mensaje, StompHeaderAccessor.class).getUser();
    }

    private void suscribir(UsuarioWs usuario, String destino) {
        interceptor.preSend(mensaje(StompCommand.SUBSCRIBE, destino, usuario), null);
    }

    private void enviar(UsuarioWs usuario, String destino) {
        interceptor.preSend(mensaje(StompCommand.SEND, destino, usuario), null);
    }

    private final UsuarioWs conductor = UsuarioWs.deEnlace(sesionConductor);
    private final UsuarioWs comprador = UsuarioWs.deEnlace(sesionComprador);
    private final UsuarioWs dueno = UsuarioWs.interno("dueno.demo", "empresa_demo", Rol.DUENO);

    // ---------- conexión ----------

    @Test
    void un_usuario_del_sistema_se_conecta_con_su_jwt() {
        String token = jwt.emitir("empresa_demo", "operador.demo", Rol.OPERADOR);

        UsuarioWs usuario = conectar("Authorization", "Bearer " + token);

        assertThat(usuario.esInterno()).isTrue();
        assertThat(usuario.identificadorNegocio()).isEqualTo("empresa_demo");
        assertThat(usuario.rolUsuario()).isEqualTo(Rol.OPERADOR);
    }

    @Test
    void rechaza_jwt_falsos_y_el_del_administrador_de_plataforma_que_no_tiene_negocio() {
        assertThatThrownBy(() -> conectar("Authorization", "Bearer falso")).isInstanceOf(MessageDeliveryException.class);
        String admin = jwt.emitir(null, "admin", Rol.ADMINISTRADOR_PLATAFORMA);
        assertThatThrownBy(() -> conectar("Authorization", "Bearer " + admin))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void un_enlace_de_conductor_o_de_comprador_se_conecta_con_su_token() {
        assertThat(conectar("token", "tok-conductor").esConductor()).isTrue();
        UsuarioWs usuarioComprador = conectar("token", "tok-comprador");
        assertThat(usuarioComprador.esConductor()).isFalse();
        assertThat(usuarioComprador.esInterno()).isFalse();
    }

    @Test
    void sin_credenciales_o_con_un_token_desconocido_no_se_conecta() {
        assertThatThrownBy(() -> conectar()).isInstanceOf(MessageDeliveryException.class);
        assertThatThrownBy(() -> conectar("token", "inventado")).isInstanceOf(MessageDeliveryException.class);
        assertThatThrownBy(() -> conectar("token", "")).isInstanceOf(MessageDeliveryException.class);
    }

    // ---------- suscripción ----------

    @Test
    void un_usuario_del_sistema_solo_ve_los_canales_de_su_negocio() {
        suscribir(dueno, "/topic/empresa_demo/envios/7");
        suscribir(dueno, "/topic/empresa_demo/envios/99");

        assertThatThrownBy(() -> suscribir(dueno, "/topic/otro_negocio/envios/7"))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void el_comprador_solo_ve_su_propio_envio() {
        suscribir(comprador, "/topic/empresa_demo/envios/7");

        assertThatThrownBy(() -> suscribir(comprador, "/topic/empresa_demo/envios/8"))
                .isInstanceOf(MessageDeliveryException.class);
        assertThatThrownBy(() -> suscribir(comprador, "/topic/otro_negocio/envios/7"))
                .isInstanceOf(MessageDeliveryException.class);
        assertThatThrownBy(() -> suscribir(comprador, "/user/queue/acuses"))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void el_conductor_solo_recibe_sus_acuses_y_no_mira_el_mapa() {
        suscribir(conductor, "/user/queue/acuses");
        suscribir(conductor, "/user/queue/errores");

        assertThatThrownBy(() -> suscribir(conductor, "/topic/empresa_demo/envios/7"))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void rechaza_canales_mal_formados_comodines_y_suscripciones_sin_conexion_previa() {
        assertThatThrownBy(() -> suscribir(dueno, "/topic/empresa_demo/envios/*"))
                .isInstanceOf(MessageDeliveryException.class);
        assertThatThrownBy(() -> suscribir(dueno, "/topic/empresa_demo/envios/7/../8"))
                .isInstanceOf(MessageDeliveryException.class);
        assertThatThrownBy(() -> suscribir(dueno, "/topic/empresa_demo"))
                .isInstanceOf(MessageDeliveryException.class);
        assertThatThrownBy(() -> suscribir(dueno, "/queue/otro")).isInstanceOf(MessageDeliveryException.class);
        assertThatThrownBy(() -> suscribir(dueno, null)).isInstanceOf(MessageDeliveryException.class);
        assertThatThrownBy(() -> suscribir(null, "/topic/empresa_demo/envios/7"))
                .isInstanceOf(MessageDeliveryException.class);
    }

    // ---------- envío ----------

    @Test
    void solo_el_conductor_envia_mensajes_a_la_aplicacion() {
        enviar(conductor, "/app/posicion");

        assertThatThrownBy(() -> enviar(comprador, "/app/posicion")).isInstanceOf(MessageDeliveryException.class);
        assertThatThrownBy(() -> enviar(dueno, "/app/posicion")).isInstanceOf(MessageDeliveryException.class);
        assertThatThrownBy(() -> enviar(null, "/app/posicion")).isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void el_conductor_no_puede_publicar_directamente_en_los_canales_de_los_demas() {
        assertThatThrownBy(() -> enviar(conductor, "/topic/empresa_demo/envios/7"))
                .isInstanceOf(MessageDeliveryException.class);
        assertThatThrownBy(() -> enviar(conductor, "/user/queue/acuses")).isInstanceOf(MessageDeliveryException.class);
    }
}
