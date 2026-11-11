package pe.agroruta.infrastructure.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import pe.agroruta.domain.model.CobrosPendientes;
import pe.agroruta.domain.model.Dinero;
import pe.agroruta.domain.model.EnvioEnCurso;
import pe.agroruta.domain.model.EstadoEnvio;
import pe.agroruta.domain.model.GananciaDelMes;
import pe.agroruta.domain.model.Panel;
import pe.agroruta.domain.model.Rol;
import pe.agroruta.domain.port.in.ConsultarPanelUseCase;
import pe.agroruta.infrastructure.adapter.out.seguridad.ServicioJwt;
import pe.agroruta.infrastructure.config.ConfiguracionSeguridad;

@WebMvcTest(controllers = PanelController.class)
@Import({ConfiguracionSeguridad.class, ManejadorErrores.class, PanelWebTest.Reloj.class})
@TestPropertySource(properties = {
        "agroruta.jwt.secreto=secreto-de-prueba-con-mas-de-32-caracteres!!",
        "agroruta.jwt.horas-validez=8"})
class PanelWebTest {

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
    ConsultarPanelUseCase consultarPanel;

    private String bearer(String negocio, Rol rol) {
        return "Bearer " + jwt.emitir(negocio, "usuario.demo", rol);
    }

    private static Panel panelDelDueno(String anterior) {
        return new Panel(YearMonth.of(2026, 11), Dinero.de("4871.40"), Dinero.de(anterior),
                new BigDecimal("40.500"), new BigDecimal("123.250"), Dinero.de("380.70"),
                List.of(new EnvioEnCurso(7L, EstadoEnvio.EN_RUTA, "Mercado Mayorista", "Ana Torres",
                        new BigDecimal("500.000"), Instant.parse("2026-11-06T08:00:00Z"))),
                new CobrosPendientes(Dinero.de("6372.00"), 1),
                List.of(new GananciaDelMes(YearMonth.of(2026, 10), Dinero.de("1000.00")),
                        new GananciaDelMes(YearMonth.of(2026, 11), Dinero.de("4871.40"))));
    }

    @Test
    void sin_sesion_responde_401() throws Exception {
        mvc.perform(get("/api/panel/resumen")).andExpect(status().isUnauthorized());
    }

    @Test
    void el_administrador_de_plataforma_no_tiene_panel() throws Exception {
        mvc.perform(get("/api/panel/resumen").header("Authorization", bearer(null, Rol.ADMINISTRADOR_PLATAFORMA)))
                .andExpect(status().isForbidden());
    }

    @Test
    void el_dueno_recibe_ganancia_variacion_y_serie() throws Exception {
        when(consultarPanel.consultar(Rol.DUENO)).thenReturn(panelDelDueno("4000.00"));

        mvc.perform(get("/api/panel/resumen").header("Authorization", bearer("empresa_demo", Rol.DUENO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mes").value("2026-11"))
                .andExpect(jsonPath("$.gananciaMes").value(4871.40))
                .andExpect(jsonPath("$.gananciaMesAnterior").value(4000.00))
                .andExpect(jsonPath("$.variacionPorcentual").value(21.8))
                .andExpect(jsonPath("$.mermaMesKg").value(40.5))
                .andExpect(jsonPath("$.mermaTotalKg").value(123.25))
                .andExpect(jsonPath("$.perdidaMermaMes").value(380.70))
                .andExpect(jsonPath("$.enviosEnCurso[0].destino").value("Mercado Mayorista"))
                .andExpect(jsonPath("$.enviosEnCurso[0].estadoEnvio").value("EN_RUTA"))
                .andExpect(jsonPath("$.cobrosPendientes.monto").value(6372.00))
                .andExpect(jsonPath("$.cobrosPendientes.cantidad").value(1))
                .andExpect(jsonPath("$.gananciaUltimosMeses[1].mes").value("2026-11"))
                .andExpect(jsonPath("$.gananciaUltimosMeses[1].ganancia").value(4871.40));
    }

    @Test
    void el_operador_no_recibe_ningun_campo_de_ganancia() throws Exception {
        when(consultarPanel.consultar(Rol.OPERADOR)).thenReturn(new Panel(YearMonth.of(2026, 11), null, null,
                new BigDecimal("40.500"), new BigDecimal("123.250"), null, List.of(),
                new CobrosPendientes(Dinero.de("6372.00"), 1), null));

        mvc.perform(get("/api/panel/resumen").header("Authorization", bearer("empresa_demo", Rol.OPERADOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gananciaMes").doesNotExist())
                .andExpect(jsonPath("$.gananciaMesAnterior").doesNotExist())
                .andExpect(jsonPath("$.variacionPorcentual").doesNotExist())
                .andExpect(jsonPath("$.gananciaUltimosMeses").doesNotExist())
                .andExpect(jsonPath("$.perdidaMermaMes").doesNotExist())
                .andExpect(jsonPath("$.mermaMesKg").value(40.5))
                .andExpect(jsonPath("$.cobrosPendientes.cantidad").value(1));
    }

    @Test
    void la_variacion_no_existe_si_el_mes_anterior_fue_cero() throws Exception {
        when(consultarPanel.consultar(any())).thenReturn(panelDelDueno("0.00"));

        mvc.perform(get("/api/panel/resumen").header("Authorization", bearer("empresa_demo", Rol.DUENO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gananciaMesAnterior").value(0.0))
                .andExpect(jsonPath("$.variacionPorcentual").doesNotExist());
    }

    @Test
    void con_un_mes_anterior_en_perdida_la_mejora_sale_positiva() throws Exception {
        when(consultarPanel.consultar(any())).thenReturn(panelDelDueno("-1000.00"));

        // De -1000 a 4871.40 es una mejora: +587.1 %, no un número negativo.
        mvc.perform(get("/api/panel/resumen").header("Authorization", bearer("empresa_demo", Rol.DUENO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.variacionPorcentual").value(587.1));
    }
}
