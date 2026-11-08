package pe.agroruta.infrastructure.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import pe.agroruta.domain.port.in.ConsultarPanelUseCase;
import pe.agroruta.infrastructure.config.ConfiguracionSeguridad;

@WebMvcTest(controllers = PanelController.class)
@Import({ConfiguracionSeguridad.class, ManejadorErrores.class, PaginasWebTest.Reloj.class})
@TestPropertySource(properties = {
        "agroruta.jwt.secreto=secreto-de-prueba-con-mas-de-32-caracteres!!",
        "agroruta.jwt.horas-validez=8"})
class PaginasWebTest {

    @TestConfiguration
    static class Reloj {
        @Bean
        Clock reloj() {
            return Clock.systemUTC();
        }
    }

    @Autowired
    MockMvc mvc;
    @MockitoBean
    ConsultarPanelUseCase consultarPanel;

    @Test
    void login_es_publico_sin_codigo_en_linea_y_con_etiquetas_visibles() throws Exception {
        String html = mvc.perform(get("/login.html")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).doesNotContainPattern("<script(?![^>]*\\bsrc=)");
        assertThat(html).doesNotContain(" style=").doesNotContain("onclick=").doesNotContain("<style");
        assertThat(html).contains("/css/sistema.css").contains("/img/logo.png");
        for (String campo : new String[]{"negocio", "usuario", "clave"}) {
            assertThat(html).contains("for=\"" + campo + "\"").contains("id=\"" + campo + "\"");
        }
        assertThat(html).contains("Entrar");
        mvc.perform(get("/js/login.js")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("innerHTML"))));
    }

    @Test
    void inicio_es_publico_sin_codigo_en_linea_con_los_cuatro_indicadores() throws Exception {
        String html = mvc.perform(get("/inicio.html")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).doesNotContainPattern("<script(?![^>]*\\bsrc=)");
        assertThat(html).doesNotContain(" style=").doesNotContain("onclick=").doesNotContain("<style");
        assertThat(html).contains("/css/sistema.css").contains("/js/estructura.js").contains("/js/inicio.js");
        for (String id : new String[]{"ganancia-mes", "envios-en-curso", "merma", "cobros-pendientes",
                "grafico-ganancia", "tabla-envios"}) {
            assertThat(html).contains("id=\"" + id + "\"");
        }
    }

    @Test
    void inicio_pinta_los_datos_con_textcontent_y_nunca_con_innerhtml() throws Exception {
        String js = mvc.perform(get("/js/inicio.js")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(js).contains("textContent").doesNotContain("innerHTML").doesNotContain("insertAdjacentHTML")
                .doesNotContain("document.write");
    }

    private static final String[] PANTALLAS = {"compradores", "transporte", "destinos", "lotes", "pedidos", "envios", "pagos", "reportes", "equipo"};

    @Test
    void ui_js_se_sirve_define_el_kit_y_no_usa_innerhtml() throws Exception {
        String js = mvc.perform(get("/js/ui.js")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(js).contains("window.Ui").contains("textContent")
                .doesNotContain("innerHTML").doesNotContain("insertAdjacentHTML").doesNotContain("document.write");
        for (String funcion : new String[]{"tabla", "panel", "aviso", "confirmar", "pestanas", "etiqueta",
                "puedeEscribir"}) {
            assertThat(js).contains(funcion);
        }
        // Cada panel ofrece una sola forma visible de salir: solo lectura = "Cerrar" arriba y sin pie;
        // formulario = "Cancelar" abajo y sin "Cerrar" arriba.
        assertThat(js).contains("soloLectura").contains("if (soloLectura)").contains("if (!soloLectura)");
        mvc.perform(get("/css/sistema.css")).andExpect(content().string(
                org.hamcrest.Matchers.containsString(".panel")));
    }

    @Test
    void todas_las_pantallas_internas_son_publicas_sin_codigo_en_linea() throws Exception {
        for (String nombre : PANTALLAS) {
            String html = mvc.perform(get("/" + nombre + ".html")).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            assertThat(html).doesNotContainPattern("<script(?![^>]*\\bsrc=)");
            assertThat(html).doesNotContain(" style=").doesNotContain("onclick=").doesNotContain("<style");
            assertThat(html).contains("/css/sistema.css").contains("/js/ui.js").contains("/js/" + nombre + ".js")
                    .contains("id=\"menu\"").contains("id=\"encabezado\"");
            String js = mvc.perform(get("/js/" + nombre + ".js")).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            assertThat(js).doesNotContain("innerHTML").doesNotContain("insertAdjacentHTML")
                    .doesNotContain("document.write");
        }
    }

    @Test
    void las_paginas_publicas_usan_el_sistema_de_diseno_con_la_marca_y_sin_la_hoja_vieja() throws Exception {
        for (String pagina : new String[]{"conductor", "seguimiento", "pago"}) {
            String html = mvc.perform(get("/" + pagina + ".html")).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            assertThat(html).contains("/css/sistema.css").contains("/css/publico.css").contains("/img/logo.png")
                    .doesNotContain("base.css");
            assertThat(html).doesNotContainPattern("<script(?![^>]*\\bsrc=)");
            assertThat(html).doesNotContain(" style=").doesNotContain("onclick=").doesNotContain("<style");
        }
        mvc.perform(get("/css/base.css")).andExpect(status().isNotFound());
    }

    @Test
    void la_pagina_de_pago_ofrece_pagoefectivo_y_simula_el_pago_en_agente_solo_como_demostracion() throws Exception {
        String html = mvc.perform(get("/pago.html")).andReturn().getResponse().getContentAsString();
        String simulado = mvc.perform(get("/js/pago-simulado.js")).andReturn().getResponse().getContentAsString();
        String pago = mvc.perform(get("/js/pago.js")).andReturn().getResponse().getContentAsString();

        assertThat(html).contains("value=\"pagoefectivo\"").contains("id=\"sim-zona-pagoefectivo\"")
                .contains("id=\"zona-cip\"").contains("Simular pago en agente (solo")
                .contains("Modo demostración: no hay un agente real");
        assertThat(simulado).contains("'sim_pagoefectivo'");
        assertThat(pago).contains("/api/publico/pago/simular-agente").contains("'PagoEfectivo'")
                .doesNotContain("innerHTML");
    }

    @Test
    void la_pagina_con_mapa_envia_el_origen_para_que_openstreetmap_no_bloquee_los_mosaicos() throws Exception {
        String html = mvc.perform(get("/seguimiento.html")).andReturn().getResponse().getContentAsString();

        assertThat(html).doesNotContain("content=\"no-referrer\"").contains("strict-origin-when-cross-origin");
    }

    @Test
    void la_biblioteca_de_qr_se_sirve_desde_el_propio_sitio_y_no_llama_a_servicios_externos() throws Exception {
        String js = mvc.perform(get("/js/vendor/qrcode.js")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(js).contains("createDataURL").contains("MIT");
        assertThat(js).doesNotContain("XMLHttpRequest").doesNotContain("fetch(");
    }

    @Test
    void envios_carga_el_qr_propio_y_ui_expone_codigoQr_sin_innerhtml() throws Exception {
        String html = mvc.perform(get("/envios.html")).andReturn().getResponse().getContentAsString();
        String ui = mvc.perform(get("/js/ui.js")).andReturn().getResponse().getContentAsString();
        String envios = mvc.perform(get("/js/envios.js")).andReturn().getResponse().getContentAsString();

        assertThat(html).contains("/js/vendor/qrcode.js");
        assertThat(html.indexOf("/js/vendor/qrcode.js")).isLessThan(html.indexOf("/js/envios.js"));
        assertThat(ui).contains("codigoQr").doesNotContain("innerHTML");
        assertThat(envios).contains("codigoQr");
    }

    @Test
    void las_alertas_de_envio_usan_los_tipos_reales_del_sistema() throws Exception {
        String envios = mvc.perform(get("/js/envios.js")).andReturn().getResponse().getContentAsString();

        assertThat(envios).contains("GPS_DESACTIVADO").contains("DETENIDO").contains("LLEGADA")
                .doesNotContain("DESVIO").doesNotContain("SIN_SENAL");
    }

    @Test
    void la_direccion_del_sitio_lleva_al_inicio_de_sesion_y_hay_una_pagina_404_propia() throws Exception {
        mvc.perform(get("/")).andExpect(status().is3xxRedirection())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl("/login.html"));
        mvc.perform(get("/no-existe.html")).andExpect(status().isNotFound());
        // Spring Boot la muestra sola ante un 404 (static/error/404.html), en lugar de la página genérica.
        String pagina;
        try (var in = getClass().getResourceAsStream("/static/error/404.html")) {
            assertThat(in).isNotNull();
            pagina = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        assertThat(pagina).contains("No encontramos esta página").contains("/css/sistema.css")
                .doesNotContain("<script").doesNotContain(" style=");
    }

    @Test
    void el_logo_los_estilos_y_las_utilidades_se_sirven() throws Exception {
        mvc.perform(get("/img/logo.png")).andExpect(status().isOk());
        mvc.perform(get("/css/sistema.css")).andExpect(status().isOk());
        mvc.perform(get("/js/api.js")).andExpect(status().isOk());
        mvc.perform(get("/js/estructura.js")).andExpect(status().isOk());
        mvc.perform(get("/fonts/fuente.woff2")).andExpect(status().isOk());
    }

    @Test
    void los_estilos_definen_el_foco_visible_y_respetan_reducir_movimiento() throws Exception {
        mvc.perform(get("/css/sistema.css")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(":focus-visible")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("prefers-reduced-motion")));
    }

    @Test
    void las_utilidades_de_js_no_pintan_datos_con_innerhtml() throws Exception {
        for (String js : new String[]{"/js/api.js", "/js/estructura.js"}) {
            String codigo = mvc.perform(get(js)).andReturn().getResponse().getContentAsString();
            assertThat(codigo).doesNotContain("innerHTML").doesNotContain("document.write");
        }
    }
}
