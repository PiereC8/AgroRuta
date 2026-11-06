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
