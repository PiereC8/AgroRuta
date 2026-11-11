package pe.agroruta.infrastructure.config;

import java.util.Arrays;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

import pe.agroruta.domain.port.in.AutenticarEnlaceUseCase;
import pe.agroruta.infrastructure.adapter.in.websocket.InterceptorAutenticacionStomp;
import pe.agroruta.infrastructure.adapter.out.seguridad.ServicioJwt;

@Configuration
@EnableWebSocketMessageBroker
public class ConfiguracionWebSocket implements WebSocketMessageBrokerConfigurer {

    private final ServicioJwt jwt;
    private final AutenticarEnlaceUseCase enlaces;
    private final String[] origenes;

    public ConfiguracionWebSocket(ServicioJwt jwt, AutenticarEnlaceUseCase enlaces,
                                  @Value("${agroruta.web.origenes-websocket:}") String origenes) {
        this.jwt = jwt;
        this.enlaces = enlaces;
        this.origenes = Arrays.stream(origenes.split(",")).map(String::trim).filter(o -> !o.isEmpty())
                .toArray(String[]::new);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registro) {
        // Por defecto solo se acepta el mismo origen que sirve las páginas; si las sirve otro sitio (Vercel),
        // su dirección va en agroruta.web.origenes-websocket.
        var punto = registro.addEndpoint("/ws");
        if (origenes.length > 0) {
            punto.setAllowedOrigins(origenes);
        }
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registro) {
        registro.enableSimpleBroker("/topic", "/queue");
        registro.setApplicationDestinationPrefixes("/app");
        registro.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registro) {
        registro.interceptors(new InterceptorAutenticacionStomp(jwt, enlaces));
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registro) {
        registro.setMessageSizeLimit(16 * 1024);
        registro.setTimeToFirstMessage(15_000);
    }
}
