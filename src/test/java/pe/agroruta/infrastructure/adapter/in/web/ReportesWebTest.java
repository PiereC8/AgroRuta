package pe.agroruta.infrastructure.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import pe.agroruta.domain.model.Archivo;
import pe.agroruta.domain.model.Dimension;
import pe.agroruta.domain.model.Dinero;
import pe.agroruta.domain.model.FilaRanking;
import pe.agroruta.domain.model.Formato;
import pe.agroruta.domain.model.FiltroReporte;
import pe.agroruta.domain.model.Indicadores;
import pe.agroruta.domain.model.OpcionesFiltro;
import pe.agroruta.domain.model.PuntoSerie;
import pe.agroruta.domain.model.Resumen;
import pe.agroruta.domain.model.Rol;
import pe.agroruta.domain.model.Veredicto;
import pe.agroruta.domain.port.in.ConsultarReportesUseCase;
import pe.agroruta.domain.port.in.ExportarReporteUseCase;
import pe.agroruta.infrastructure.adapter.out.seguridad.ServicioJwt;
import pe.agroruta.infrastructure.config.ConfiguracionSeguridad;

@WebMvcTest(controllers = ReportesController.class)
@Import({ConfiguracionSeguridad.class, ManejadorErrores.class, ReportesWebTest.Reloj.class})
@TestPropertySource(properties = {
        "agroruta.jwt.secreto=secreto-de-prueba-con-mas-de-32-caracteres!!",
        "agroruta.jwt.horas-validez=8"})
class ReportesWebTest {

    @TestConfiguration
    static class Reloj {
        @Bean
        Clock reloj() {
            return Clock.fixed(Instant.parse("2026-11-07T15:00:00Z"), ZoneId.of("America/Lima"));
        }
    }

    @Autowired
    MockMvc mvc;
    @Autowired
    ServicioJwt jwt;
    @MockitoBean
    ConsultarReportesUseCase consultas;
    @MockitoBean
    ExportarReporteUseCase exportar;

    private String dueno() {
        return "Bearer " + jwt.emitir("empresa_demo", "dueno.empresa", Rol.DUENO);
    }

    private String operador() {
        return "Bearer " + jwt.emitir("empresa_demo", "operador.uno", Rol.OPERADOR);
    }

    @Test
    void sin_sesion_responde_401() throws Exception {
        mvc.perform(get("/api/reportes/resumen")).andExpect(status().isUnauthorized());
    }

    @Test
    void el_operador_no_puede_ver_reportes_ni_exportarlos() throws Exception {
        for (String ruta : List.of("/api/reportes/resumen", "/api/reportes/serie", "/api/reportes/opciones",
                "/api/reportes/ranking?dimension=destino", "/api/reportes/exportar?formato=csv")) {
            mvc.perform(get(ruta).header("Authorization", operador())).andExpect(status().isForbidden());
        }
    }

    @Test
    void sin_fechas_el_resumen_usa_los_ultimos_30_dias_hasta_hoy_en_lima() throws Exception {
        FiltroReporte esperado = FiltroReporte.ultimosDias(30, LocalDate.of(2026, 11, 7));
        Indicadores vacios = Indicadores.de(List.of());
        when(consultas.resumen(any())).thenReturn(new Resumen(esperado, vacios, vacios, Veredicto.SIN_DATOS,
                "No hay entregas en este período.", List.of()));

        mvc.perform(get("/api/reportes/resumen").header("Authorization", dueno()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.veredicto").value("SIN_DATOS"))
                .andExpect(jsonPath("$.desde").value("2026-10-09"))
                .andExpect(jsonPath("$.hasta").value("2026-11-07"));

        ArgumentCaptor<FiltroReporte> filtro = ArgumentCaptor.forClass(FiltroReporte.class);
        verify(consultas).resumen(filtro.capture());
        org.assertj.core.api.Assertions.assertThat(filtro.getValue()).isEqualTo(esperado);
    }

    @Test
    void el_resumen_entrega_los_importes_como_numeros_y_las_causas() throws Exception {
        FiltroReporte filtro = new FiltroReporte(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 7), "Palta", 3L);
        Indicadores vacios = Indicadores.de(List.of());
        when(consultas.resumen(filtro)).thenReturn(new Resumen(filtro, vacios, vacios, Veredicto.GANANDO,
                "Ganaste S/ 0.00", List.of()));

        mvc.perform(get("/api/reportes/resumen?desde=2026-11-01&hasta=2026-11-07&producto=Palta&destino=3")
                        .header("Authorization", dueno()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.veredicto").value("GANANDO"))
                .andExpect(jsonPath("$.actual.gananciaReal").value(0.0))
                .andExpect(jsonPath("$.anteriorDesde").value("2026-10-01"))
                .andExpect(jsonPath("$.causas.length()").value(3));
    }

    @Test
    void fechas_invalidas_responden_400() throws Exception {
        mvc.perform(get("/api/reportes/resumen?desde=2026-11-07&hasta=2026-11-01").header("Authorization", dueno()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
        mvc.perform(get("/api/reportes/resumen?desde=hola&hasta=2026-11-01").header("Authorization", dueno()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void la_serie_y_el_ranking_salen_como_listas() throws Exception {
        when(consultas.serie(any())).thenReturn(List.of(new PuntoSerie(LocalDate.of(2026, 11, 1), "01 nov",
                Dinero.de("120.50"), Dinero.de("900.00"))));
        when(consultas.ranking(any(), eq(Dimension.DESTINO))).thenReturn(List.of(new FilaRanking("Mercado Lima", 2,
                Dinero.de("800.00"), new BigDecimal("55.5"), new BigDecimal("3.500"))));

        mvc.perform(get("/api/reportes/serie").header("Authorization", dueno()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].etiqueta").value("01 nov"))
                .andExpect(jsonPath("$[0].ganancia").value(120.50));
        mvc.perform(get("/api/reportes/ranking?dimension=destino").header("Authorization", dueno()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Mercado Lima"))
                .andExpect(jsonPath("$[0].ganancia").value(800.00));
        mvc.perform(get("/api/reportes/ranking?dimension=nada").header("Authorization", dueno()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void las_opciones_traen_productos_y_destinos() throws Exception {
        when(consultas.opciones()).thenReturn(new OpcionesFiltro(List.of("Palta Hass"),
                List.of(new OpcionesFiltro.OpcionDestino(3L, "Mercado Lima"))));

        mvc.perform(get("/api/reportes/opciones").header("Authorization", dueno()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productos[0]").value("Palta Hass"))
                .andExpect(jsonPath("$.destinos[0].nombre").value("Mercado Lima"));
    }

    @Test
    void exportar_descarga_el_archivo_con_su_nombre_y_tipo() throws Exception {
        when(exportar.exportar(any(), eq(Formato.CSV), eq("dueno.empresa"), eq("empresa_demo")))
                .thenReturn(new Archivo("reporte-agroruta-2026-11-01_2026-11-07.csv", "text/csv; charset=UTF-8",
                        "a,b".getBytes()));

        mvc.perform(get("/api/reportes/exportar?formato=csv&desde=2026-11-01&hasta=2026-11-07")
                        .header("Authorization", dueno()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("reporte-agroruta-2026-11-01_2026-11-07.csv")))
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.startsWith("text/csv")))
                .andExpect(content().bytes("a,b".getBytes()));
    }

    @Test
    void un_formato_desconocido_responde_400() throws Exception {
        mvc.perform(get("/api/reportes/exportar?formato=xls").header("Authorization", dueno()))
                .andExpect(status().isBadRequest());
    }
}
