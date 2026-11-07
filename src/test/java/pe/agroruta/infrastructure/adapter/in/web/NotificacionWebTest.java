package pe.agroruta.infrastructure.adapter.in.web;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import pe.agroruta.domain.model.Aviso;
import pe.agroruta.domain.model.Notificacion;
import pe.agroruta.domain.model.Rol;
import pe.agroruta.domain.model.TipoAviso;
import pe.agroruta.domain.port.in.ConsultarNotificacionesUseCase;
import pe.agroruta.domain.port.in.DespacharNotificacionesUseCase;
import pe.agroruta.infrastructure.adapter.out.seguridad.ServicioJwt;
import pe.agroruta.infrastructure.config.ConfiguracionSeguridad;

@WebMvcTest(controllers = NotificacionController.class)
@Import({ConfiguracionSeguridad.class, ManejadorErrores.class, NotificacionWebTest.Reloj.class})
@TestPropertySource(properties = {
        "agroruta.jwt.secreto=secreto-de-prueba-con-mas-de-32-caracteres!!",
        "agroruta.jwt.horas-validez=8"})
class NotificacionWebTest {

    @org.springframework.boot.test.context.TestConfiguration
    static class Reloj {
        @org.springframework.context.annotation.Bean
        java.time.Clock reloj() {
            return java.time.Clock.systemUTC();
        }
    }

    @Autowired
    MockMvc mvc;
    @Autowired
    ServicioJwt jwt;
    @MockitoBean
    ConsultarNotificacionesUseCase consultas;
    @MockitoBean
    DespacharNotificacionesUseCase envios;

    private String dueno() {
        return "Bearer " + jwt.emitir("empresa_demo", "dueno.empresa", Rol.DUENO);
    }

    @Test
    void el_operador_no_ve_el_historial_ni_manda_pruebas() throws Exception {
        String operador = "Bearer " + jwt.emitir("empresa_demo", "operador.uno", Rol.OPERADOR);

        mvc.perform(get("/api/notificaciones").header("Authorization", operador)).andExpect(status().isForbidden());
        mvc.perform(post("/api/notificaciones/prueba").header("Authorization", operador)
                .contentType(MediaType.APPLICATION_JSON).content("{\"correo\":\"a@b.pe\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void el_dueno_ve_el_historial_sin_el_cuerpo_de_los_correos() throws Exception {
        Notificacion n = Notificacion.nueva(new Aviso("pago:1", TipoAviso.PAGO_RECIBIDO, "Pago recibido",
                "Texto largo"), "dueno@empresa.pe", Instant.parse("2026-11-07T15:00:00Z")).conId(1L);
        when(consultas.recientes(50)).thenReturn(List.of(n));

        mvc.perform(get("/api/notificaciones").header("Authorization", dueno()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].asunto").value("Pago recibido"))
                .andExpect(jsonPath("$[0].estado").value("PENDIENTE"))
                .andExpect(jsonPath("$[0].cuerpo").doesNotExist());
    }

    @Test
    void la_prueba_pasa_el_correo_al_caso_de_uso() throws Exception {
        mvc.perform(post("/api/notificaciones/prueba").header("Authorization", dueno())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"correo\":\"dueno@empresa.pe\"}"))
                .andExpect(status().isNoContent());

        verify(envios).probar("dueno@empresa.pe");
    }
}
