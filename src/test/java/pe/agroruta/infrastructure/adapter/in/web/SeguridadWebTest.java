package pe.agroruta.infrastructure.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import pe.agroruta.domain.model.CredencialesInvalidasException;
import pe.agroruta.domain.model.Negocio;
import pe.agroruta.domain.model.Plan;
import pe.agroruta.domain.model.Rol;
import pe.agroruta.domain.port.in.AltaNegocioUseCase;
import pe.agroruta.domain.port.in.IniciarSesionUseCase;
import pe.agroruta.infrastructure.adapter.out.seguridad.ServicioJwt;
import pe.agroruta.infrastructure.config.ConfiguracionSeguridad;
import pe.agroruta.infrastructure.config.TenantContext;

@WebMvcTest(controllers = {NegocioController.class, AuthController.class})
@Import({ConfiguracionSeguridad.class, ManejadorErrores.class, SeguridadWebTest.Reloj.class})
@TestPropertySource(properties = {
        "agroruta.jwt.secreto=secreto-de-prueba-con-mas-de-32-caracteres!!",
        "agroruta.jwt.horas-validez=8"})
class SeguridadWebTest {

    @TestConfiguration
    static class Reloj {
        @Bean
        Clock reloj() {
            return Clock.fixed(Instant.now(), ZoneOffset.UTC);
        }
    }

    private static final String ALTA = """
            {"identificadorNegocio":"chacra_demo","nombreNegocio":"Chacra Demo","planSuscripcion":"CHACRA",
             "nombreUsuarioDueno":"dueno.demo","claveInicialDueno":"clave-segura-1"}""";

    @Autowired
    MockMvc mvc;
    @Autowired
    ServicioJwt jwt;
    @MockitoBean
    AltaNegocioUseCase altaNegocio;
    @MockitoBean
    IniciarSesionUseCase iniciarSesion;

    private String bearer(String negocio, String usuario, Rol rol) {
        return "Bearer " + jwt.emitir(negocio, usuario, rol);
    }

    @Test
    void sin_token_el_alta_de_negocios_responde_401() throws Exception {
        mvc.perform(post("/api/plataforma/negocios").contentType(MediaType.APPLICATION_JSON).content(ALTA))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void con_un_token_falso_responde_401() throws Exception {
        mvc.perform(post("/api/plataforma/negocios").header("Authorization", "Bearer falso")
                        .contentType(MediaType.APPLICATION_JSON).content(ALTA))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void un_dueno_no_puede_dar_de_alta_negocios() throws Exception {
        mvc.perform(post("/api/plataforma/negocios").header("Authorization", bearer("chacra_demo", "dueno.demo", Rol.DUENO))
                        .contentType(MediaType.APPLICATION_JSON).content(ALTA))
                .andExpect(status().isForbidden());
    }

    @Test
    void el_administrador_de_plataforma_si_puede() throws Exception {
        when(altaNegocio.darDeAlta(any())).thenReturn(
                new Negocio(7L, "chacra_demo", "Chacra Demo", Plan.CHACRA, true));

        mvc.perform(post("/api/plataforma/negocios")
                        .header("Authorization", bearer(null, "admin", Rol.ADMINISTRADOR_PLATAFORMA))
                        .contentType(MediaType.APPLICATION_JSON).content(ALTA))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idNegocio").value(7))
                .andExpect(jsonPath("$.identificadorNegocio").value("chacra_demo"));
    }

    @Test
    void el_login_es_publico_y_devuelve_el_token() throws Exception {
        when(iniciarSesion.iniciar(any())).thenReturn(
                new IniciarSesionUseCase.Resultado("el-token", Rol.DUENO, "chacra_demo"));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identificadorNegocio\":\"chacra_demo\",\"nombreUsuario\":\"dueno.demo\",\"clave\":\"x\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("el-token"))
                .andExpect(jsonPath("$.rolUsuario").value("DUENO"));
    }

    @Test
    void un_login_fallido_responde_401_con_mensaje_generico() throws Exception {
        when(iniciarSesion.iniciar(any())).thenThrow(new CredencialesInvalidasException());

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identificadorNegocio\":\"chacra_demo\",\"nombreUsuario\":\"x\",\"clave\":\"y\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Credenciales inválidas"));
    }

    @Test
    void cualquier_otra_ruta_exige_token() throws Exception {
        mvc.perform(get("/api/lotes")).andExpect(status().isUnauthorized());
    }

    @Test
    void el_negocio_de_la_peticion_se_limpia_al_terminar() throws Exception {
        mvc.perform(get("/api/lotes").header("Authorization", bearer("chacra_demo", "dueno.demo", Rol.DUENO)));

        assertThat(TenantContext.actual()).isEmpty();
    }
}
