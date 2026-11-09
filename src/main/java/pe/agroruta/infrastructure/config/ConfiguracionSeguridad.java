package pe.agroruta.infrastructure.config;

import java.time.Clock;
import jakarta.servlet.DispatcherType;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import pe.agroruta.domain.model.Rol;
import pe.agroruta.infrastructure.adapter.in.web.FiltroJwt;
import pe.agroruta.infrastructure.adapter.out.seguridad.ServicioJwt;

@Configuration
public class ConfiguracionSeguridad {

    @Bean
    ServicioJwt servicioJwt(@Value("${agroruta.jwt.secreto}") String secreto,
                            @Value("${agroruta.jwt.horas-validez}") long horasValidez,
                            Clock reloj) {
        return new ServicioJwt(secreto, Duration.ofHours(horasValidez), reloj);
    }

    @Bean
    SecurityFilterChain cadenaDeSeguridad(HttpSecurity http, ServicioJwt servicioJwt) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .headers(cabeceras -> cabeceras.contentSecurityPolicy(politica -> politica.policyDirectives(
                        "default-src 'self'; "
                                + "script-src 'self' https://cdnjs.cloudflare.com https://sdk.mercadopago.com "
                                + "https://*.mlstatic.com; "
                                + "style-src 'self' https://cdnjs.cloudflare.com https://*.mlstatic.com; "
                                + "img-src 'self' data: https://tile.openstreetmap.org "
                                + "https://*.tile.openstreetmap.org https://*.mlstatic.com https://*.mercadopago.com; "
                                + "connect-src 'self' ws: wss: https://*.mercadopago.com https://*.mercadolibre.com "
                                + "https://nominatim.openstreetmap.org; "
                                + "frame-src https://*.mercadopago.com https://*.mercadolibre.com; "
                                + "frame-ancestors 'none'; base-uri 'self'; form-action 'self'")))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/api/auth/login", "/actuator/health").permitAll()
                        // Las páginas son públicas (los datos se piden a la API con el token), igual que sus recursos
                        // y el WebSocket (se autentica por token). Una página que no existe responde con la 404 propia.
                        .requestMatchers(HttpMethod.GET, "/", "/*.html").permitAll()
                        .requestMatchers("/js/**", "/css/**", "/img/**", "/fonts/**", "/favicon.ico", "/ws/**")
                        .permitAll()
                        // El enlace con token del comprador: el propio caso de uso valida el token.
                        .requestMatchers("/api/publico/**").permitAll()
                        // Notificaciones de la pasarela: no se confía en ellas, se verifican contra la pasarela.
                        .requestMatchers("/api/webhooks/**").permitAll()
                        .requestMatchers("/api/plataforma/**").hasRole(Rol.ADMINISTRADOR_PLATAFORMA.name())
                        .requestMatchers("/api/lotes/**").hasAnyRole(Rol.DUENO.name(), Rol.OPERADOR.name())
                        .requestMatchers(HttpMethod.GET, "/api/destinos/**")
                        .hasAnyRole(Rol.DUENO.name(), Rol.OPERADOR.name())
                        .requestMatchers("/api/destinos/**").hasRole(Rol.DUENO.name())
                        .requestMatchers("/api/reportes/**", "/api/equipo/**", "/api/notificaciones/**").hasRole(Rol.DUENO.name())
                        .requestMatchers("/api/compradores/**", "/api/pedidos/**", "/api/envios/**",
                                "/api/liquidaciones/**", "/api/pagos/**", "/api/comprobantes/**", "/api/panel/**")
                        .hasAnyRole(Rol.DUENO.name(), Rol.OPERADOR.name())
                        .requestMatchers(HttpMethod.GET, "/api/transportistas/**", "/api/vehiculos/**")
                        .hasAnyRole(Rol.DUENO.name(), Rol.OPERADOR.name())
                        .requestMatchers("/api/transportistas/**", "/api/vehiculos/**").hasRole(Rol.DUENO.name())
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(new FiltroJwt(servicioJwt), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
