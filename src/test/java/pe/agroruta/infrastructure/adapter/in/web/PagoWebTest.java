package pe.agroruta.infrastructure.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import pe.agroruta.domain.model.Comprobante;
import pe.agroruta.domain.model.ConflictoDominioException;
import pe.agroruta.domain.model.Dinero;
import pe.agroruta.domain.model.EstadoComprobante;
import pe.agroruta.domain.model.EstadoEnvio;
import pe.agroruta.domain.model.EstadoPago;
import pe.agroruta.domain.model.Pago;
import pe.agroruta.domain.model.RecursoNoEncontradoException;
import pe.agroruta.domain.model.Rol;
import pe.agroruta.domain.model.ServicioExternoNoDisponibleException;
import pe.agroruta.domain.model.SesionEnlace;
import pe.agroruta.domain.model.TipoComprobante;
import pe.agroruta.domain.model.TipoEnlace;
import pe.agroruta.domain.port.in.AutenticarEnlaceUseCase;
import pe.agroruta.domain.port.in.ConfirmarPagoUseCase;
import pe.agroruta.domain.port.in.ConsultarPagosUseCase;
import pe.agroruta.domain.port.in.EmitirComprobanteUseCase;
import pe.agroruta.domain.port.in.PagarPedidoUseCase;
import pe.agroruta.infrastructure.adapter.out.seguridad.ServicioJwt;
import pe.agroruta.infrastructure.config.ConfiguracionPasarela;
import pe.agroruta.infrastructure.config.ConfiguracionSeguridad;
import pe.agroruta.infrastructure.config.TenantContext;

@WebMvcTest(controllers = {PublicoPagoController.class, WebhookPagoController.class, PagoController.class})
@Import({ConfiguracionSeguridad.class, ManejadorErrores.class, PagoWebTest.Configuracion.class})
@TestPropertySource(properties = {
        "agroruta.jwt.secreto=secreto-de-prueba-con-mas-de-32-caracteres!!",
        "agroruta.jwt.horas-validez=8"})
class PagoWebTest {

    @TestConfiguration
    static class Configuracion {
        @Bean
        Clock reloj() {
            return Clock.fixed(Instant.parse("2026-11-06T15:00:00Z"), ZoneOffset.UTC);
        }

        @Bean
        ConfiguracionPasarela configuracionPasarela() {
            return new ConfiguracionPasarela(ConfiguracionPasarela.MERCADOPAGO, "TEST-publica");
        }
    }

    private static final Instant AHORA = Instant.parse("2026-11-06T15:00:00Z");
    private static final String TOKEN = PublicoController.ENCABEZADO_TOKEN;
    private static final SesionEnlace COMPRADOR = new SesionEnlace("empresa_demo", 7L, TipoEnlace.COMPRADOR);

    @Autowired
    MockMvc mvc;
    @Autowired
    ServicioJwt jwt;
    @MockitoBean
    AutenticarEnlaceUseCase enlaces;
    @MockitoBean
    PagarPedidoUseCase pagar;
    @MockitoBean
    ConfirmarPagoUseCase confirmar;
    @MockitoBean
    ConsultarPagosUseCase consultarPagos;
    @MockitoBean
    EmitirComprobanteUseCase comprobantes;

    private static Pago pago(EstadoPago estado) {
        return new Pago(5L, 1L, 1L, Dinero.de("6372.00"), estado, "clave-de-operacion-1", "chr_test_1", null,
                estado == EstadoPago.RECHAZADO ? "La tarjeta no tiene fondos suficientes. Prueba con otra tarjeta."
                        : "Pago aprobado", "Visa", "4242", "comprador@ejemplo.com", AHORA, AHORA);
    }

    private static Comprobante comprobante() {
        return new Comprobante(2L, 5L, TipoComprobante.FACTURA, "F001", 1, EstadoComprobante.ACEPTADO,
                "https://proveedor.test/pdf", "https://proveedor.test/xml", "aceptada", 1, null,
                LocalDate.of(2026, 11, 6), AHORA);
    }

    private String bearer(String negocio, Rol rol) {
        return "Bearer " + jwt.emitir(negocio, "operador.demo", rol);
    }

    private void tokenValido() {
        when(enlaces.resolver("tok-comp")).thenReturn(Optional.of(COMPRADOR));
    }

    // ---------- estado del pago ----------

    @Test
    void el_comprador_ve_cuanto_debe_y_solo_la_llave_publica_de_la_pasarela() throws Exception {
        tokenValido();
        when(pagar.estado(COMPRADOR)).thenAnswer(invocacion -> {
            assertThat(TenantContext.actual()).contains("empresa_demo");
            return new PagarPedidoUseCase.EstadoPagoPublico(EstadoEnvio.ENTREGADO, true, Dinero.de("6372.00"),
                    "Pedido 7", null, null);
        });

        String json = mvc.perform(get("/api/publico/pago").header(TOKEN, "tok-comp"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagable").value(true))
                .andExpect(jsonPath("$.monto").value(6372.00))
                .andExpect(jsonPath("$.moneda").value("PEN"))
                .andExpect(jsonPath("$.modo").value("MERCADOPAGO"))
                .andExpect(jsonPath("$.llavePublica").value("TEST-publica"))
                .andReturn().getResponse().getContentAsString();

        assertThat(json).doesNotContain("sk_", "secret", "llaveSecreta", "idLiquidacion", "idPago");
        assertThat(TenantContext.actual()).isEmpty();
    }

    @Test
    void despues_de_pagar_el_estado_muestra_el_comprobante_con_sus_enlaces() throws Exception {
        tokenValido();
        when(pagar.estado(COMPRADOR)).thenReturn(new PagarPedidoUseCase.EstadoPagoPublico(EstadoEnvio.ENTREGADO,
                false, Dinero.de("6372.00"), "Pedido 7", pago(EstadoPago.APROBADO), comprobante()));

        mvc.perform(get("/api/publico/pago").header(TOKEN, "tok-comp"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ultimoPago.estadoPago").value("APROBADO"))
                .andExpect(jsonPath("$.ultimoPago.ultimosCuatro").value("4242"))
                .andExpect(jsonPath("$.comprobante.numero").value("F001-1"))
                .andExpect(jsonPath("$.comprobante.enlacePdf").value("https://proveedor.test/pdf"));
    }

    @Test
    void un_token_desconocido_o_de_conductor_se_ve_como_enlace_invalido() throws Exception {
        when(enlaces.resolver("tok-cond"))
                .thenReturn(Optional.of(new SesionEnlace("empresa_demo", 7L, TipoEnlace.CONDUCTOR)));
        when(enlaces.resolver("desconocido")).thenReturn(Optional.empty());

        mvc.perform(get("/api/publico/pago").header(TOKEN, "desconocido")).andExpect(status().isNotFound());
        mvc.perform(get("/api/publico/pago").header(TOKEN, "tok-cond")).andExpect(status().isNotFound());
        mvc.perform(get("/api/publico/pago")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/publico/pago").header(TOKEN, "tok-cond").header("Idempotency-Key", "clave-de-operacion-1")
                .contentType(MediaType.APPLICATION_JSON).content("{\"tokenTarjeta\":\"t\",\"email\":\"a@b.co\"}"))
                .andExpect(status().isNotFound());
        verify(pagar, never()).pagar(any());
    }

    // ---------- pagar ----------

    @Test
    void pagar_pasa_el_token_de_la_tarjeta_el_correo_y_la_clave_de_operacion_al_caso_de_uso() throws Exception {
        tokenValido();
        when(pagar.pagar(any())).thenReturn(new PagarPedidoUseCase.ResultadoPago(5L, EstadoPago.APROBADO,
                "¡Pago recibido! Gracias.", Dinero.de("6372.00"), comprobante()));

        mvc.perform(post("/api/publico/pago").header(TOKEN, "tok-comp").header("Idempotency-Key", "clave-de-operacion-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tokenTarjeta\":\"tkn_test_abc\",\"email\":\"comprador@ejemplo.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoPago").value("APROBADO"))
                .andExpect(jsonPath("$.monto").value(6372.00))
                .andExpect(jsonPath("$.comprobante.estado").value("ACEPTADO"));

        ArgumentCaptor<PagarPedidoUseCase.Comando> captura = ArgumentCaptor.forClass(PagarPedidoUseCase.Comando.class);
        verify(pagar).pagar(captura.capture());
        assertThat(captura.getValue().sesion()).isEqualTo(COMPRADOR);
        assertThat(captura.getValue().tokenTarjeta()).isEqualTo("tkn_test_abc");
        assertThat(captura.getValue().email()).isEqualTo("comprador@ejemplo.com");
        assertThat(captura.getValue().claveIdempotencia()).isEqualTo("clave-de-operacion-1");
    }

    @Test
    void una_tarjeta_rechazada_responde_200_con_el_motivo_y_no_como_error_del_servidor() throws Exception {
        tokenValido();
        when(pagar.pagar(any())).thenReturn(new PagarPedidoUseCase.ResultadoPago(5L, EstadoPago.RECHAZADO,
                "La tarjeta no tiene fondos suficientes. Prueba con otra tarjeta.", Dinero.de("6372.00"), null));

        mvc.perform(post("/api/publico/pago").header(TOKEN, "tok-comp").header("Idempotency-Key", "clave-de-operacion-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tokenTarjeta\":\"tkn\",\"email\":\"a@b.co\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoPago").value("RECHAZADO"))
                .andExpect(jsonPath("$.mensaje").value("La tarjeta no tiene fondos suficientes. Prueba con otra tarjeta."));
    }

    @Test
    void sin_clave_de_operacion_o_sin_token_de_tarjeta_no_se_procesa() throws Exception {
        tokenValido();

        mvc.perform(post("/api/publico/pago").header(TOKEN, "tok-comp").contentType(MediaType.APPLICATION_JSON)
                .content("{\"tokenTarjeta\":\"tkn\",\"email\":\"a@b.co\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/publico/pago").header(TOKEN, "tok-comp").header("Idempotency-Key", "clave-de-operacion-1")
                .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"a@b.co\"}"))
                .andExpect(status().isBadRequest());
        verify(pagar, never()).pagar(any());
    }

    @Test
    void los_conflictos_y_las_caidas_de_servicios_externos_responden_con_su_codigo() throws Exception {
        tokenValido();
        when(pagar.pagar(any())).thenThrow(new ConflictoDominioException("Este pedido ya está pagado"))
                .thenThrow(new ServicioExternoNoDisponibleException("pasarela caída"));
        String cuerpo = "{\"tokenTarjeta\":\"tkn\",\"email\":\"a@b.co\"}";

        mvc.perform(post("/api/publico/pago").header(TOKEN, "tok-comp").header("Idempotency-Key", "clave-de-operacion-1")
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("Este pedido ya está pagado"));
        mvc.perform(post("/api/publico/pago").header(TOKEN, "tok-comp").header("Idempotency-Key", "clave-de-operacion-2")
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isServiceUnavailable());
    }

    // ---------- webhook ----------

    @Test
    void el_webhook_es_publico_y_solo_toma_el_identificador_del_cargo() throws Exception {
        when(confirmar.confirmar("1234567890")).thenReturn(true);

        mvc.perform(post("/api/webhooks/pago").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"payment\",\"action\":\"payment.created\","
                                + "\"data\":{\"id\":\"1234567890\"}}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.procesado").value(true));
        mvc.perform(post("/api/webhooks/pago").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"data\":{\"id\":\"1234567890\"}}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.procesado").value(true));

        verify(confirmar, org.mockito.Mockito.times(2)).confirmar("1234567890");
    }

    @Test
    void el_webhook_ignora_lo_que_no_parece_un_cargo_y_rechaza_cuerpos_que_no_son_json() throws Exception {
        for (String cuerpo : List.of("{\"type\":\"merchant_order\",\"data\":{\"id\":\"1234567890\"}}", "{\"data\":{\"id\":\"../../etc\"}}",
                "{\"data\":\"no json\"}", "{}")) {
            mvc.perform(post("/api/webhooks/pago").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.procesado").value(false));
        }
        mvc.perform(post("/api/webhooks/pago").contentType(MediaType.APPLICATION_JSON).content("no es json"))
                .andExpect(status().isBadRequest());
        verify(confirmar, never()).confirmar(any());
    }

    @Test
    void si_no_se_puede_verificar_el_cargo_con_la_pasarela_se_pide_que_la_notificacion_se_reintente() throws Exception {
        when(confirmar.confirmar("1234567890")).thenThrow(new ServicioExternoNoDisponibleException("caída"));

        mvc.perform(post("/api/webhooks/pago").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"data\":{\"id\":\"1234567890\"}}"))
                .andExpect(status().isServiceUnavailable());
    }

    // ---------- Dueño y Operador ----------

    @Test
    void el_operador_consulta_pagos_y_comprobantes_y_puede_reintentar_un_comprobante() throws Exception {
        when(consultarPagos.listar(0, 50)).thenReturn(List.of(pago(EstadoPago.APROBADO)));
        when(consultarPagos.pagoDeLote(1L)).thenReturn(pago(EstadoPago.APROBADO));
        when(consultarPagos.comprobanteDePago(5L)).thenReturn(comprobante());
        when(comprobantes.reintentar(2L)).thenReturn(comprobante());
        when(comprobantes.consultar(2L)).thenReturn(comprobante());
        String operador = bearer("empresa_demo", Rol.OPERADOR);

        mvc.perform(get("/api/pagos").header("Authorization", operador))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].ultimosCuatro").value("4242"));
        mvc.perform(get("/api/lotes/1/pago").header("Authorization", operador)).andExpect(status().isOk());
        mvc.perform(get("/api/pagos/5/comprobante").header("Authorization", operador))
                .andExpect(status().isOk()).andExpect(jsonPath("$.numero").value("F001-1"));
        mvc.perform(post("/api/comprobantes/2/reintentar").header("Authorization", operador))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estadoComprobante").value("ACEPTADO"));
        mvc.perform(post("/api/comprobantes/2/consultar").header("Authorization", operador)).andExpect(status().isOk());
    }

    @Test
    void pagos_y_comprobantes_exigen_sesion_de_un_negocio() throws Exception {
        mvc.perform(get("/api/pagos")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/comprobantes/2/reintentar")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/pagos").header("Authorization", bearer(null, Rol.ADMINISTRADOR_PLATAFORMA)))
                .andExpect(status().isForbidden());
    }

    @Test
    void un_pago_inexistente_responde_404() throws Exception {
        when(consultarPagos.pagoDeLote(9L)).thenThrow(new RecursoNoEncontradoException("sin pagos"));

        mvc.perform(get("/api/lotes/9/pago").header("Authorization", bearer("empresa_demo", Rol.DUENO)))
                .andExpect(status().isNotFound());
    }

    // ---------- página ----------

    @Test
    void la_pagina_de_pago_es_publica_sin_codigo_en_el_html_y_su_politica_permite_a_mercado_pago() throws Exception {
        String html = mvc.perform(get("/pago.html")).andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy",
                        org.hamcrest.Matchers.containsString("https://sdk.mercadopago.com")))
                .andExpect(header().string("Content-Security-Policy",
                        org.hamcrest.Matchers.containsString("frame-src https://*.mercadopago.com")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Pago de tu pedido")))
                .andReturn().getResponse().getContentAsString();

        assertThat(html).doesNotContainPattern("<script(?![^>]*\\bsrc=)");
        assertThat(html).doesNotContain(" style=").doesNotContain("onclick=").doesNotContain("<style");
        mvc.perform(get("/js/pago.js")).andExpect(status().isOk());
    }

    @Test
    void la_pagina_nunca_envia_el_numero_de_la_tarjeta_al_servidor() throws Exception {
        String html = mvc.perform(get("/pago.html")).andReturn().getResponse().getContentAsString();
        String js = mvc.perform(get("/js/pago.js")).andReturn().getResponse().getContentAsString();
        String simulado = mvc.perform(get("/js/pago-simulado.js")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Con Mercado Pago los campos de tarjeta los pone su formulario seguro: la página no tiene ninguno propio.
        assertThat(html).contains("brick-tarjeta").contains("Modo demostraci");
        // El único dato que viaja al servidor es el token; el número solo se lee dentro del formulario simulado.
        assertThat(js).contains("tokenTarjeta").doesNotContain("sim-numero").doesNotContain("sim-codigo");
        assertThat(simulado).contains("sim_").contains("slice(-4)");
        assertThat(simulado).doesNotContain("fetch(").doesNotContain("XMLHttpRequest");
    }
}
