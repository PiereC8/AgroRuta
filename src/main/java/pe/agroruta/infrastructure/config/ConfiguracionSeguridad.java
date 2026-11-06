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
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/api/auth/login", "/actuator/health").permitAll()
                        .requestMatchers("/api/plataforma/**").hasRole(Rol.ADMINISTRADOR_PLATAFORMA.name())
                        .requestMatchers("/api/lotes/**").hasAnyRole(Rol.DUENO.name(), Rol.OPERADOR.name())
                        .requestMatchers(HttpMethod.GET, "/api/destinos/**")
                        .hasAnyRole(Rol.DUENO.name(), Rol.OPERADOR.name())
                        .requestMatchers("/api/destinos/**").hasRole(Rol.DUENO.name())
                        .requestMatchers("/api/compradores/**", "/api/pedidos/**", "/api/envios/**",
                                "/api/liquidaciones/**")
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
