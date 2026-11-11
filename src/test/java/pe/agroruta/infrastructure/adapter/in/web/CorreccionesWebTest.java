package pe.agroruta.infrastructure.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import pe.agroruta.domain.model.ConflictoDominioException;
import pe.agroruta.domain.model.Envio;
import pe.agroruta.domain.model.EstadoEnvio;
import pe.agroruta.domain.model.Rol;
import pe.agroruta.domain.port.in.CancelarOperacionUseCase;
import pe.agroruta.domain.port.in.CorregirEntregaUseCase;
import pe.agroruta.infrastructure.adapter.out.seguridad.ServicioJwt;
import pe.agroruta.infrastructure.config.ConfiguracionSeguridad;

@WebMvcTest(controllers = CorreccionesController.class)
@Import({ConfiguracionSeguridad.class, ManejadorErrores.class, CorreccionesWebTest.Reloj.class})
@TestPropertySource(properties = {
        "agroruta.jwt.secreto=secreto-de-prueba-con-mas-de-32-caracteres!!",
        "agroruta.jwt.horas-validez=8"})
class CorreccionesWebTest {

    @TestConfiguration
    static class Reloj {
        @Bean
        Clock reloj() {
            return Clock.fixed(Instant.parse("2026-11-06T15:00:00Z"), ZoneOffset.UTC);
        }
    }

    private static final Instant AHORA = Instant.parse("2026-11-06T15:00:00Z");

    @Autowired
    MockMvc mvc;
    @Autowired
    ServicioJwt jwt;
    @MockitoBean
    CancelarOperacionUseCase cancelar;
    @MockitoBean
    CorregirEntregaUseCase corregir;

    private String bearer(Rol rol) {
        return "Bearer " + jwt.emitir("sol_de_tacna", "operador.demo", rol);
    }

    @Test
    void el_operador_cancela_un_envio_del_negocio_de_su_token() throws Exception {
        when(cancelar.cancelarEnvio(eq("sol_de_tacna"), eq(7L), eq("operador.demo"))).thenReturn(
                new Envio(7L, 3L, 2L, 1L, 1L, EstadoEnvio.CANCELADO, AHORA, AHORA, null, "operador.demo"));

        mvc.perform(post("/api/envios/7/cancelar").header("Authorization", bearer(Rol.OPERADOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoEnvio").value("CANCELADO"));
    }

    @Test
    void sin_sesion_no_se_cancela_ni_se_corrige_nada() throws Exception {
        mvc.perform(post("/api/envios/7/cancelar")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/pedidos/3/cancelar")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/envios/7/entrega").contentType("application/json")
                        .content("{\"cantidadRecibidaKg\":1,\"mermaKg\":0,\"rechazoKg\":0}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void corregir_una_entrega_ya_pagada_responde_conflicto_con_el_motivo() throws Exception {
        when(corregir.corregir(any())).thenThrow(
                new ConflictoDominioException("Esta entrega ya tiene un pago aprobado o en curso: ya no se puede corregir"));

        mvc.perform(put("/api/envios/7/entrega").header("Authorization", bearer(Rol.DUENO))
                        .contentType("application/json")
                        .content("{\"cantidadRecibidaKg\":900,\"mermaKg\":100,\"rechazoKg\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Esta entrega ya tiene un pago aprobado o en curso: ya no se puede corregir"));
    }
}
