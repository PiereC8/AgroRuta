package pe.agroruta.infrastructure.adapter.in.websocket;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;

import pe.agroruta.domain.model.Rol;
import pe.agroruta.domain.model.SesionEnlace;
import pe.agroruta.domain.model.TipoEnlace;
import pe.agroruta.domain.port.in.AutenticarEnlaceUseCase;
import pe.agroruta.infrastructure.adapter.out.seguridad.ServicioJwt;

/**
 * Decide quién puede conectarse, a qué canales puede suscribirse y quién puede enviar posiciones.
 * Un negocio nunca ve los canales de otro, y un enlace solo ve su propio envío (CASO 1 y RNF-22).
 */
public class InterceptorAutenticacionStomp implements ChannelInterceptor {

    private static final Pattern CANAL_ENVIO = Pattern.compile("^/topic/([a-z][a-z0-9_]{2,30})/envios/(\\d{1,18})$");
    private static final Pattern CANAL_PRIVADO = Pattern.compile("^/user/queue/(acuses|errores)$");

    private final ServicioJwt jwt;
    private final AutenticarEnlaceUseCase enlaces;

    public InterceptorAutenticacionStomp(ServicioJwt jwt, AutenticarEnlaceUseCase enlaces) {
        this.jwt = jwt;
        this.enlaces = enlaces;
    }

    @Override
    public Message<?> preSend(Message<?> mensaje, MessageChannel canal) {
        StompHeaderAccessor cabecera = MessageHeaderAccessor.getAccessor(mensaje, StompHeaderAccessor.class);
        if (cabecera == null || cabecera.getCommand() == null) {
            return mensaje;
        }
        StompCommand comando = cabecera.getCommand();
        if (StompCommand.CONNECT.equals(comando) || StompCommand.STOMP.equals(comando)) {
            cabecera.setUser(autenticar(cabecera));
        } else if (StompCommand.SUBSCRIBE.equals(comando)) {
            exigirSuscripcionPermitida(cabecera);
        } else if (StompCommand.SEND.equals(comando)) {
            exigirEnvioPermitido(cabecera);
        }
        return mensaje;
    }

    private UsuarioWs autenticar(StompHeaderAccessor cabecera) {
        String autorizacion = cabecera.getFirstNativeHeader("Authorization");
        if (autorizacion != null && autorizacion.startsWith("Bearer ")) {
            var datos = jwt.leer(autorizacion.substring(7).strip());
            if (datos.isPresent() && datos.get().identificadorNegocio() != null
                    && (datos.get().rolUsuario() == Rol.DUENO || datos.get().rolUsuario() == Rol.OPERADOR)) {
                return UsuarioWs.interno(datos.get().nombreUsuario(), datos.get().identificadorNegocio(),
                        datos.get().rolUsuario());
            }
            throw rechazo();
        }
        String token = cabecera.getFirstNativeHeader("token");
        Optional<SesionEnlace> sesion = enlaces.resolver(token);
        if (sesion.isPresent()) {
            return UsuarioWs.deEnlace(sesion.get());
        }
        throw rechazo();
    }

    private void exigirSuscripcionPermitida(StompHeaderAccessor cabecera) {
        UsuarioWs usuario = usuarioConectado(cabecera);
        String destino = cabecera.getDestination();
        if (destino == null) {
            throw rechazo();
        }
        Matcher privado = CANAL_PRIVADO.matcher(destino);
        if (privado.matches()) {
            if (!usuario.esConductor()) {
                throw rechazo();
            }
            return;
        }
        Matcher envio = CANAL_ENVIO.matcher(destino);
        if (!envio.matches() || !envio.group(1).equals(usuario.identificadorNegocio())) {
            throw rechazo();
        }
        if (usuario.esInterno()) {
            return;
        }
        SesionEnlace sesion = usuario.sesionEnlace();
        boolean esSuEnvio = sesion.tipoEnlace() == TipoEnlace.COMPRADOR
                && String.valueOf(sesion.idEnvio()).equals(envio.group(2));
        if (!esSuEnvio) {
            throw rechazo();
        }
    }

    private void exigirEnvioPermitido(StompHeaderAccessor cabecera) {
        UsuarioWs usuario = usuarioConectado(cabecera);
        String destino = cabecera.getDestination();
        if (destino == null || !destino.startsWith("/app/") || !usuario.esConductor()) {
            throw rechazo();
        }
    }

    private UsuarioWs usuarioConectado(StompHeaderAccessor cabecera) {
        if (cabecera.getUser() instanceof UsuarioWs usuario) {
            return usuario;
        }
        throw rechazo();
    }

    private static MessageDeliveryException rechazo() {
        return new MessageDeliveryException("No autorizado");
    }
}
