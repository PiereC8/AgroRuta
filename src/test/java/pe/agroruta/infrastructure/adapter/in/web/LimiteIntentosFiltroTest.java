package pe.agroruta.infrastructure.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class LimiteIntentosFiltroTest {

    private static final Instant AHORA = Instant.parse("2026-11-11T15:00:00Z");

    private Instant ahora = AHORA;
    private final LimiteIntentosFiltro filtro = new LimiteIntentosFiltro(3, () -> ahora.toEpochMilli());

    private int intentar(String metodo, String ruta, String ip) throws Exception {
        MockHttpServletRequest peticion = new MockHttpServletRequest(metodo, ruta);
        peticion.setRemoteAddr("10.0.0.1");
        if (ip != null) {
            peticion.addHeader("X-Forwarded-For", ip + ", 10.0.0.1");
        }
        MockHttpServletResponse respuesta = new MockHttpServletResponse();
        filtro.doFilter(peticion, respuesta, new MockFilterChain());
        return respuesta.getStatus();
    }

    @Test
    void despues_del_limite_por_minuto_responde_429_y_al_minuto_siguiente_vuelve_a_dejar() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(intentar("POST", "/api/auth/login", "200.1.1.1")).isEqualTo(200);
        }
        assertThat(intentar("POST", "/api/auth/login", "200.1.1.1")).isEqualTo(429);
        assertThat(intentar("POST", "/api/auth/recuperar", "200.1.1.1")).isEqualTo(429);

        ahora = AHORA.plus(Duration.ofSeconds(61));
        assertThat(intentar("POST", "/api/auth/login", "200.1.1.1")).isEqualTo(200);
    }

    @Test
    void cada_ip_tiene_su_propia_cuenta_y_lo_que_no_es_auth_no_se_limita() throws Exception {
        for (int i = 0; i < 4; i++) {
            intentar("POST", "/api/auth/login", "200.1.1.1");
        }
        assertThat(intentar("POST", "/api/auth/login", "200.2.2.2")).isEqualTo(200);
        for (int i = 0; i < 10; i++) {
            assertThat(intentar("POST", "/api/lotes", "200.1.1.1")).isEqualTo(200);
            assertThat(intentar("GET", "/api/auth/login", "200.1.1.1")).isEqualTo(200);
        }
    }

    @Test
    void la_respuesta_429_dice_cuanto_esperar() throws Exception {
        for (int i = 0; i < 3; i++) {
            intentar("POST", "/api/auth/login", null);
        }
        MockHttpServletRequest peticion = new MockHttpServletRequest("POST", "/api/auth/login");
        peticion.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse respuesta = new MockHttpServletResponse();
        filtro.doFilter(peticion, respuesta, new MockFilterChain());

        assertThat(respuesta.getStatus()).isEqualTo(429);
        assertThat(respuesta.getHeader("Retry-After")).isEqualTo("60");
        assertThat(respuesta.getContentAsString()).contains("Espera un minuto");
    }
}
