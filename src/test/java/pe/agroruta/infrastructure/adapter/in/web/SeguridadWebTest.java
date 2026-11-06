package pe.agroruta.infrastructure.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

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

import pe.agroruta.domain.model.Cotizacion;
import pe.agroruta.domain.model.CredencialesInvalidasException;
import pe.agroruta.domain.model.DestinoVenta;
import pe.agroruta.domain.model.Dinero;
import pe.agroruta.domain.model.DominioException;
import pe.agroruta.domain.model.EstadoLote;
import pe.agroruta.domain.model.Lote;
import pe.agroruta.domain.model.LoteNoEncontradoException;
import pe.agroruta.domain.model.Negocio;
import pe.agroruta.domain.model.Plan;
import pe.agroruta.domain.model.Rol;
import pe.agroruta.domain.port.in.AdministrarDestinosUseCase;
import pe.agroruta.domain.port.in.AltaNegocioUseCase;
import pe.agroruta.domain.port.in.ConsultarLotesUseCase;
import pe.agroruta.domain.port.in.CotizarFleteUseCase;
import pe.agroruta.domain.port.in.IniciarSesionUseCase;
import pe.agroruta.domain.port.in.RegistrarLoteUseCase;
import pe.agroruta.infrastructure.adapter.out.seguridad.ServicioJwt;
import pe.agroruta.infrastructure.config.ConfiguracionSeguridad;
import pe.agroruta.infrastructure.config.TenantContext;

@WebMvcTest(controllers = {NegocioController.class, AuthController.class, LoteController.class,
        DestinoController.class, CotizacionController.class})
@Import({ConfiguracionSeguridad.class, ManejadorErrores.class, SeguridadWebTest.Reloj.class})
@TestPropertySource(properties = {
        "agroruta.jwt.secreto=secreto-de-prueba-con-mas-de-32-caracteres!!",
        "agroruta.jwt.horas-validez=8"})
class SeguridadWebTest {

    @TestConfiguration
    static class Reloj {
        @Bean
        Clock reloj() {
            return Clock.fixed(Instant.now(), ZoneOffset.UTC);
        }
    }

    private static final String ALTA = """
            {"identificadorNegocio":"chacra_demo","nombreNegocio":"Chacra Demo","planSuscripcion":"CHACRA",
             "nombreUsuarioDueno":"dueno.demo","claveInicialDueno":"clave-segura-1"}""";

    @Autowired
    MockMvc mvc;
    @Autowired
    ServicioJwt jwt;
    @MockitoBean
    AltaNegocioUseCase altaNegocio;
    @MockitoBean
    IniciarSesionUseCase iniciarSesion;
    @MockitoBean
    RegistrarLoteUseCase registrarLote;
    @MockitoBean
    ConsultarLotesUseCase consultarLotes;
    @MockitoBean
    AdministrarDestinosUseCase administrarDestinos;
    @MockitoBean
    CotizarFleteUseCase cotizarFlete;

    private static final String LOTE = """
            {"productoLote":"Palta Hass","cantidadKg":1250.5,"fechaCosecha":"2026-11-05","chacraOrigen":"Chacra Sur"}""";

    private String bearer(String negocio, String usuario, Rol rol) {
        return "Bearer " + jwt.emitir(negocio, usuario, rol);
    }

    @Test
    void sin_token_el_alta_de_negocios_responde_401() throws Exception {
        mvc.perform(post("/api/plataforma/negocios").contentType(MediaType.APPLICATION_JSON).content(ALTA))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void con_un_token_falso_responde_401() throws Exception {
        mvc.perform(post("/api/plataforma/negocios").header("Authorization", "Bearer falso")
                        .contentType(MediaType.APPLICATION_JSON).content(ALTA))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void un_dueno_no_puede_dar_de_alta_negocios() throws Exception {
        mvc.perform(post("/api/plataforma/negocios").header("Authorization", bearer("chacra_demo", "dueno.demo", Rol.DUENO))
                        .contentType(MediaType.APPLICATION_JSON).content(ALTA))
                .andExpect(status().isForbidden());
    }

    @Test
    void el_administrador_de_plataforma_si_puede() throws Exception {
        when(altaNegocio.darDeAlta(any())).thenReturn(
                new Negocio(7L, "chacra_demo", "Chacra Demo", Plan.CHACRA, true));

        mvc.perform(post("/api/plataforma/negocios")
                        .header("Authorization", bearer(null, "admin", Rol.ADMINISTRADOR_PLATAFORMA))
                        .contentType(MediaType.APPLICATION_JSON).content(ALTA))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idNegocio").value(7))
                .andExpect(jsonPath("$.identificadorNegocio").value("chacra_demo"));
    }

    @Test
    void el_login_es_publico_y_devuelve_el_token() throws Exception {
        when(iniciarSesion.iniciar(any())).thenReturn(
                new IniciarSesionUseCase.Resultado("el-token", Rol.DUENO, "chacra_demo"));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identificadorNegocio\":\"chacra_demo\",\"nombreUsuario\":\"dueno.demo\",\"clave\":\"x\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("el-token"))
                .andExpect(jsonPath("$.rolUsuario").value("DUENO"));
    }

    @Test
    void un_login_fallido_responde_401_con_mensaje_generico() throws Exception {
        when(iniciarSesion.iniciar(any())).thenThrow(new CredencialesInvalidasException());

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identificadorNegocio\":\"chacra_demo\",\"nombreUsuario\":\"x\",\"clave\":\"y\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Credenciales inválidas"));
    }

    @Test
    void el_administrador_de_plataforma_no_ve_los_lotes_de_un_negocio() throws Exception {
        mvc.perform(get("/api/lotes").header("Authorization", bearer(null, "admin", Rol.ADMINISTRADOR_PLATAFORMA)))
                .andExpect(status().isForbidden());
    }

    @Test
    void un_operador_registra_un_lote_con_su_propio_usuario() throws Exception {
        when(registrarLote.registrar(any())).thenReturn(new Lote(5L, "Palta Hass", new BigDecimal("1250.500"),
                LocalDate.of(2026, 11, 5), "Chacra Sur", EstadoLote.REGISTRADO, "operador.demo", Instant.now()));

        mvc.perform(post("/api/lotes").header("Authorization", bearer("chacra_demo", "operador.demo", Rol.OPERADOR))
                        .contentType(MediaType.APPLICATION_JSON).content(LOTE))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idLote").value(5))
                .andExpect(jsonPath("$.estadoLote").value("REGISTRADO"));

        ArgumentCaptor<RegistrarLoteUseCase.Comando> captura =
                ArgumentCaptor.forClass(RegistrarLoteUseCase.Comando.class);
        verify(registrarLote).registrar(captura.capture());
        assertThat(captura.getValue().usuarioRegistro()).isEqualTo("operador.demo");
        assertThat(captura.getValue().cantidadKg()).isEqualByComparingTo("1250.5");
    }

    @Test
    void un_lote_con_regla_rota_responde_400_y_uno_inexistente_404() throws Exception {
        when(registrarLote.registrar(any())).thenThrow(new DominioException("La cantidad en kg debe ser mayor que cero"));
        when(consultarLotes.obtener(99L)).thenThrow(new LoteNoEncontradoException(99L));
        String dueno = bearer("chacra_demo", "dueno.demo", Rol.DUENO);

        mvc.perform(post("/api/lotes").header("Authorization", dueno)
                        .contentType(MediaType.APPLICATION_JSON).content(LOTE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("La cantidad en kg debe ser mayor que cero"));
        mvc.perform(get("/api/lotes/99").header("Authorization", dueno)).andExpect(status().isNotFound());
    }

    @Test
    void un_cuerpo_que_no_es_json_valido_responde_400() throws Exception {
        mvc.perform(post("/api/lotes").header("Authorization", bearer("chacra_demo", "dueno.demo", Rol.DUENO))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productoLote\":\"Palta\",\"cantidadKg\":\"mucho\",\"fechaCosecha\":\"2026-11-05\",\"chacraOrigen\":\"Sur\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void solo_el_dueno_configura_destinos_pero_el_operador_los_consulta() throws Exception {
        when(administrarDestinos.registrarDestino(any(), any(), anyBoolean()))
                .thenReturn(new DestinoVenta(1L, "Mercado Lima", "Lima", false, true));
        String destino = "{\"nombreDestino\":\"Mercado Lima\",\"ciudadDestino\":\"Lima\",\"esLocal\":false}";
        String operador = bearer("chacra_demo", "operador.demo", Rol.OPERADOR);
        String dueno = bearer("chacra_demo", "dueno.demo", Rol.DUENO);

        mvc.perform(post("/api/destinos").header("Authorization", operador)
                .contentType(MediaType.APPLICATION_JSON).content(destino)).andExpect(status().isForbidden());
        mvc.perform(put("/api/destinos/1/tarifa").header("Authorization", operador)
                .contentType(MediaType.APPLICATION_JSON).content("{\"costoFletePorKg\":1.2}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/destinos").header("Authorization", operador)).andExpect(status().isOk());
        mvc.perform(post("/api/destinos").header("Authorization", dueno)
                .contentType(MediaType.APPLICATION_JSON).content(destino))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.nombreDestino").value("Mercado Lima"));
        mvc.perform(put("/api/destinos/1/tarifa").header("Authorization", dueno)
                .contentType(MediaType.APPLICATION_JSON).content("{\"costoFletePorKg\":1.2}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void el_operador_cotiza_un_lote_y_ve_la_mejor_opcion() throws Exception {
        Lote lote = new Lote(1L, "Palta Hass", new BigDecimal("1000"), LocalDate.of(2026, 11, 5), "Sur",
                EstadoLote.REGISTRADO, "dueno.demo", Instant.now());
        var lima = new DestinoVenta(2L, "Mercado Lima", "Lima", false, true);
        var tacna = new DestinoVenta(1L, "Mercado Tacna", "Tacna", true, true);
        var cusco = new DestinoVenta(3L, "Mercado Cusco", "Cusco", false, true);
        when(cotizarFlete.cotizar(1L)).thenReturn(new Cotizacion(lote, List.of(
                new Cotizacion.Opcion(lima, Dinero.de("5.50"), Dinero.de("1.20"), Dinero.de("5500"),
                        Dinero.de("1200"), Dinero.de("4300")),
                new Cotizacion.Opcion(tacna, Dinero.de("3.00"), Dinero.CERO, Dinero.de("3000"), Dinero.CERO,
                        Dinero.de("3000"))),
                List.of(new Cotizacion.Omitido(cusco, Cotizacion.Motivo.SIN_TARIFA))));

        mvc.perform(get("/api/lotes/1/cotizacion")
                        .header("Authorization", bearer("chacra_demo", "operador.demo", Rol.OPERADOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mejorDestino").value("Mercado Lima"))
                .andExpect(jsonPath("$.opciones[0].conviene").value(true))
                .andExpect(jsonPath("$.opciones[0].gananciaNeta").value(4300.00))
                .andExpect(jsonPath("$.opciones[1].conviene").value(false))
                .andExpect(jsonPath("$.destinosSinDatos[0].motivo").value("SIN_TARIFA"));
    }

    @Test
    void cualquier_otra_ruta_exige_token() throws Exception {
        mvc.perform(get("/api/lotes")).andExpect(status().isUnauthorized());
    }

    @Test
    void el_negocio_de_la_peticion_se_limpia_al_terminar() throws Exception {
        mvc.perform(get("/api/lotes").header("Authorization", bearer("chacra_demo", "dueno.demo", Rol.DUENO)));

        assertThat(TenantContext.actual()).isEmpty();
    }
}
