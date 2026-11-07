package pe.agroruta.infrastructure.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import pe.agroruta.domain.model.DominioException;
import pe.agroruta.domain.model.MiembroEquipo;
import pe.agroruta.domain.model.Rol;
import pe.agroruta.domain.port.in.AdministrarEquipoUseCase;
import pe.agroruta.infrastructure.adapter.out.seguridad.ServicioJwt;
import pe.agroruta.infrastructure.config.ConfiguracionSeguridad;

@WebMvcTest(controllers = EquipoController.class)
@Import({ConfiguracionSeguridad.class, ManejadorErrores.class, EquipoWebTest.Reloj.class})
@TestPropertySource(properties = {
        "agroruta.jwt.secreto=secreto-de-prueba-con-mas-de-32-caracteres!!",
        "agroruta.jwt.horas-validez=8"})
class EquipoWebTest {

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
    AdministrarEquipoUseCase equipo;

    private String dueno() {
        return "Bearer " + jwt.emitir("empresa_demo", "dueno.empresa", Rol.DUENO);
    }

    private String operador() {
        return "Bearer " + jwt.emitir("empresa_demo", "operador.uno", Rol.OPERADOR);
    }

    @Test
    void el_operador_no_ve_ni_cambia_el_equipo() throws Exception {
        mvc.perform(get("/api/equipo").header("Authorization", operador())).andExpect(status().isForbidden());
        mvc.perform(post("/api/equipo").header("Authorization", operador()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombreUsuario\":\"x.yz\",\"clave\":\"clave-segura-1\"}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/equipo")).andExpect(status().isUnauthorized());
    }

    @Test
    void el_dueno_lista_el_equipo_sin_ver_claves() throws Exception {
        when(equipo.listar()).thenReturn(List.of(new MiembroEquipo(1L, "dueno.empresa", Rol.DUENO, "d@e.pe", true,
                true)));

        mvc.perform(get("/api/equipo").header("Authorization", dueno()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombreUsuario").value("dueno.empresa"))
                .andExpect(jsonPath("$[0].correo").value("d@e.pe"))
                .andExpect(jsonPath("$[0].hashClave").doesNotExist())
                .andExpect(jsonPath("$[0].clave").doesNotExist());
    }

    @Test
    void crea_un_operador_y_responde_201() throws Exception {
        when(equipo.crearOperador("operador.dos", "clave-segura-1", "op@e.pe"))
                .thenReturn(new MiembroEquipo(5L, "operador.dos", Rol.OPERADOR, "op@e.pe", true, true));

        mvc.perform(post("/api/equipo").header("Authorization", dueno()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombreUsuario\":\"operador.dos\",\"clave\":\"clave-segura-1\",\"correo\":\"op@e.pe\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idUsuario").value(5))
                .andExpect(jsonPath("$.rolUsuario").value("OPERADOR"));
    }

    @Test
    void un_correo_malo_responde_400_con_el_mensaje() throws Exception {
        org.mockito.Mockito.doThrow(new DominioException("El correo no es válido")).when(equipo)
                .cambiarCorreo(any(), any(), org.mockito.ArgumentMatchers.anyBoolean());

        mvc.perform(put("/api/equipo/2/correo").header("Authorization", dueno())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"correo\":\"malo\",\"avisosActivos\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("El correo no es válido"));
    }

    @Test
    void desactivar_pasa_el_nombre_de_quien_lo_pide() throws Exception {
        mvc.perform(put("/api/equipo/2/activo").header("Authorization", dueno())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}"))
                .andExpect(status().isNoContent());

        verify(equipo).cambiarActivo(2L, false, "dueno.empresa");
    }
}
