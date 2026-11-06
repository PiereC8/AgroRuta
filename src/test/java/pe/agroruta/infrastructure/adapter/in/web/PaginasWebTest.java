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

/** Las pantallas del Dueño y el Operador se sirven sin sesión (los datos sí la piden) y sin código en línea. */
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

    /** Pantallas internas ya construidas (la lista crece con cada tarea de la entrega B). */
    private static final String[] PANTALLAS = {"compradores", "transporte", "destinos", "lotes"};

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
