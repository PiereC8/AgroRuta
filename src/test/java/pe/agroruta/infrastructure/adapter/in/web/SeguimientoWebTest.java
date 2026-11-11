package pe.agroruta.infrastructure.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import pe.agroruta.domain.model.AlertaEnvio;
import pe.agroruta.domain.model.Coordenada;
import pe.agroruta.domain.model.Dinero;
import pe.agroruta.domain.model.Envio;
import pe.agroruta.domain.model.EstadoEnvio;
import pe.agroruta.domain.model.Lote;
import pe.agroruta.domain.model.Pedido;
import pe.agroruta.domain.model.Posicion;
import pe.agroruta.domain.model.Rol;
import pe.agroruta.domain.model.Seguimiento;
import pe.agroruta.domain.model.SesionEnlace;
import pe.agroruta.domain.model.TipoAlerta;
import pe.agroruta.domain.model.TipoEnlace;
import pe.agroruta.domain.port.in.AutenticarEnlaceUseCase;
import pe.agroruta.domain.port.in.ConsultarSeguimientoUseCase;
import pe.agroruta.domain.port.in.EnviarEnlacePagoUseCase;
import pe.agroruta.domain.port.in.GenerarEnlacesEnvioUseCase;
import pe.agroruta.infrastructure.adapter.out.seguridad.ServicioJwt;
import pe.agroruta.infrastructure.config.ConfiguracionSeguridad;
import pe.agroruta.infrastructure.config.TenantContext;

@WebMvcTest(controllers = {SeguimientoController.class, PublicoController.class})
@Import({ConfiguracionSeguridad.class, ManejadorErrores.class, SeguimientoWebTest.Reloj.class})
@TestPropertySource(properties = {
        "agroruta.jwt.secreto=secreto-de-prueba-con-mas-de-32-caracteres!!",
        "agroruta.jwt.horas-validez=8"})
class SeguimientoWebTest {

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
    GenerarEnlacesEnvioUseCase generarEnlaces;
    @MockitoBean
    AutenticarEnlaceUseCase autenticarEnlace;
    @MockitoBean
    ConsultarSeguimientoUseCase consultar;
    @MockitoBean
    EnviarEnlacePagoUseCase enviarEnlacePago;

    private String bearer(String negocio, Rol rol) {
        return "Bearer " + jwt.emitir(negocio, "operador.demo", rol);
    }

    private Seguimiento seguimiento() {
        Envio envio = new Envio(7L, 1L, 1L, 1L, 1L, EstadoEnvio.EN_RUTA, AHORA.minusSeconds(7200),
                AHORA.minusSeconds(3600), null, "operador.demo");
        Posicion ultima = new Posicion(1L, 7L, new Coordenada(-17.5, -71.0), 9.0, AHORA.minusSeconds(10), AHORA);
        return new Seguimiento(envio, "Mercado Tacna", "Tacna", new Coordenada(-18.0146, -70.2536), ultima, 42L,
                AHORA, List.of(ultima));
    }

    @Test
    void el_operador_genera_los_enlaces_y_las_rutas_llevan_el_token_en_el_fragmento() throws Exception {
        when(generarEnlaces.generar(eq("empresa_demo"), eq(7L), isNull())).thenReturn(
                new GenerarEnlacesEnvioUseCase.EnlacesGenerados("tok-cond", "tok-comp", AHORA.plusSeconds(86400)));

        mvc.perform(post("/api/envios/7/enlaces").header("Authorization", bearer("empresa_demo", Rol.OPERADOR)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rutaConductor").value("/conductor.html#t=tok-cond"))
                .andExpect(jsonPath("$.rutaComprador").value("/seguimiento.html#t=tok-comp"));
    }

    @Test
    void con_tipo_solo_se_genera_ese_enlace_y_el_otro_no_viene() throws Exception {
        when(generarEnlaces.generar(eq("empresa_demo"), eq(7L), eq(TipoEnlace.COMPRADOR))).thenReturn(
                new GenerarEnlacesEnvioUseCase.EnlacesGenerados(null, "tok-comp", AHORA.plusSeconds(86400)));

        mvc.perform(post("/api/envios/7/enlaces?tipo=COMPRADOR")
                        .header("Authorization", bearer("empresa_demo", Rol.OPERADOR)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rutaConductor").doesNotExist())
                .andExpect(jsonPath("$.rutaComprador").value("/seguimiento.html#t=tok-comp"));
    }

    @Test
    void el_enlace_de_pago_se_manda_al_correo_con_la_direccion_del_sitio() throws Exception {
        when(enviarEnlacePago.enviar(new EnviarEnlacePagoUseCase.Comando("empresa_demo", 7L, "compras@ejemplo.pe",
                "http://localhost"))).thenReturn(new EnviarEnlacePagoUseCase.Resultado("compras@ejemplo.pe",
                "http://localhost/pago.html#t=tok", AHORA.plusSeconds(86400)));

        mvc.perform(post("/api/envios/7/enlace-pago/correo").header("Authorization", bearer("empresa_demo", Rol.DUENO))
                        .contentType("application/json").content("{\"correo\":\"compras@ejemplo.pe\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correo").value("compras@ejemplo.pe"));
        mvc.perform(post("/api/envios/7/enlace-pago/correo").contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void generar_enlaces_exige_estar_autenticado_y_ser_de_un_negocio() throws Exception {
        mvc.perform(post("/api/envios/7/enlaces")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/envios/7/enlaces").header("Authorization", bearer(null, Rol.ADMINISTRADOR_PLATAFORMA)))
                .andExpect(status().isForbidden());
    }

    @Test
    void el_seguimiento_interno_incluye_el_canal_del_envio_con_el_negocio_del_token() throws Exception {
        when(consultar.obtener(7L)).thenReturn(seguimiento());

        mvc.perform(get("/api/envios/7/seguimiento").header("Authorization", bearer("empresa_demo", Rol.DUENO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canal").value("/topic/empresa_demo/envios/7"))
                .andExpect(jsonPath("$.etaMinutos").value(42))
                .andExpect(jsonPath("$.ultimaPosicion.latitud").value(-17.5));
    }

    @Test
    void el_recorrido_y_las_alertas_se_consultan_con_sesion() throws Exception {
        when(consultar.recorrido(7L)).thenReturn(seguimiento().trazoReciente());
        when(consultar.alertas(7L)).thenReturn(List.of(
                new AlertaEnvio(1L, 7L, TipoAlerta.GPS_DESACTIVADO, "El GPS se desactivó", AHORA)));
        String operador = bearer("empresa_demo", Rol.OPERADOR);

        mvc.perform(get("/api/envios/7/recorrido").header("Authorization", operador))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].longitud").value(-71.0));
        mvc.perform(get("/api/envios/7/alertas").header("Authorization", operador))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].tipoAlerta").value("GPS_DESACTIVADO"));
        mvc.perform(get("/api/envios/7/recorrido")).andExpect(status().isUnauthorized());
    }

    // ---------- enlace público del comprador ----------

    @Test
    void el_comprador_ve_su_envio_sin_iniciar_sesion_y_sin_datos_internos() throws Exception {
        when(autenticarEnlace.resolver("tok-comp"))
                .thenReturn(Optional.of(new SesionEnlace("empresa_demo", 7L, TipoEnlace.COMPRADOR)));
        when(consultar.obtener(7L)).thenReturn(seguimiento());
        when(consultar.alertas(7L)).thenReturn(List.of());

        String json = mvc.perform(get("/api/publico/seguimiento").header(PublicoController.ENCABEZADO_TOKEN, "tok-comp"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoEnvio").value("EN_RUTA"))
                .andExpect(jsonPath("$.ciudadDestino").value("Tacna"))
                .andExpect(jsonPath("$.etaMinutos").value(42))
                .andExpect(jsonPath("$.llegoAlDestino").value(false))
                .andExpect(jsonPath("$.canal").value("/topic/empresa_demo/envios/7"))
                .andReturn().getResponse().getContentAsString();

        // Nada de precios, placas, ids de pedido o lote, ni usuarios.
        assertThat(json).doesNotContain("precio", "placa", "idPedido", "idLote", "usuario", "idVehiculo");
    }

    @Test
    void el_enlace_del_comprador_avisa_cuando_el_vehiculo_ya_llego() throws Exception {
        when(autenticarEnlace.resolver("tok-comp"))
                .thenReturn(Optional.of(new SesionEnlace("empresa_demo", 7L, TipoEnlace.COMPRADOR)));
        when(consultar.obtener(7L)).thenReturn(seguimiento());
        when(consultar.alertas(7L)).thenReturn(List.of(
                new AlertaEnvio(1L, 7L, TipoAlerta.LLEGADA, "El vehículo llegó al destino", AHORA)));

        mvc.perform(get("/api/publico/seguimiento").header(PublicoController.ENCABEZADO_TOKEN, "tok-comp"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.llegoAlDestino").value(true));
    }

    @Test
    void un_token_desconocido_vencido_o_de_conductor_se_ve_igual_que_un_enlace_invalido() throws Exception {
        when(autenticarEnlace.resolver("tok-cond"))
                .thenReturn(Optional.of(new SesionEnlace("empresa_demo", 7L, TipoEnlace.CONDUCTOR)));
        when(autenticarEnlace.resolver("desconocido")).thenReturn(Optional.empty());

        mvc.perform(get("/api/publico/seguimiento").header(PublicoController.ENCABEZADO_TOKEN, "desconocido"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("El enlace no es válido o ya venció"));
        mvc.perform(get("/api/publico/seguimiento").header(PublicoController.ENCABEZADO_TOKEN, "tok-cond"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/publico/seguimiento")).andExpect(status().isBadRequest());
    }

    @Test
    void la_consulta_publica_se_hace_en_el_negocio_del_token_y_no_deja_el_contexto_sucio() throws Exception {
        when(autenticarEnlace.resolver("tok-comp"))
                .thenReturn(Optional.of(new SesionEnlace("empresa_demo", 7L, TipoEnlace.COMPRADOR)));
        when(consultar.obtener(7L)).thenAnswer(invocacion -> {
            assertThat(TenantContext.actual()).contains("empresa_demo");
            return seguimiento();
        });
        when(consultar.alertas(7L)).thenReturn(List.of());

        mvc.perform(get("/api/publico/seguimiento").header(PublicoController.ENCABEZADO_TOKEN, "tok-comp"))
                .andExpect(status().isOk());

        assertThat(TenantContext.actual()).isEmpty();
    }

    // ---------- páginas y cabeceras de seguridad ----------

    @Test
    void las_paginas_del_conductor_y_del_comprador_son_publicas_y_llevan_politica_de_contenido() throws Exception {
        mvc.perform(get("/conductor.html")).andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy", org.hamcrest.Matchers.containsString("script-src 'self'")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Compartir mi ubicación")));
        mvc.perform(get("/seguimiento.html")).andExpect(status().isOk());
        mvc.perform(get("/js/stomp.js")).andExpect(status().isOk());
        mvc.perform(get("/css/publico.css")).andExpect(status().isOk());
    }

    @Test
    void las_paginas_no_llevan_scripts_ni_estilos_dentro_del_html() throws Exception {
        for (String pagina : List.of("/conductor.html", "/seguimiento.html")) {
            String html = mvc.perform(get(pagina)).andReturn().getResponse().getContentAsString();
            assertThat(html).doesNotContainPattern("<script(?![^>]*\\bsrc=)");
            assertThat(html).doesNotContain(" style=").doesNotContain("onclick=").doesNotContain("<style");
        }
    }
}
