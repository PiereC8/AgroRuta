package pe.agroruta.infrastructure.config;

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

    public ConfiguracionWebSocket(ServicioJwt jwt, AutenticarEnlaceUseCase enlaces) {
        this.jwt = jwt;
        this.enlaces = enlaces;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registro) {
        // Sin orígenes permitidos adicionales: solo se acepta el mismo origen que sirve las páginas.
        registro.addEndpoint("/ws");
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
