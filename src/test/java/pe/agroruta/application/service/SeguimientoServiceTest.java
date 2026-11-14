package pe.agroruta.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import pe.agroruta.application.service.DestinosServiceTest.DestinosEnMemoria;
import pe.agroruta.application.service.Falsos.EnlacesEnMemoria;
import pe.agroruta.application.service.Falsos.EnviosEnMemoria;
import pe.agroruta.application.service.Falsos.GeneradorTokenFalso;
import pe.agroruta.application.service.Falsos.PedidosEnMemoria;
import pe.agroruta.application.service.Falsos.PublicadorGrabador;
import pe.agroruta.application.service.Falsos.SeguimientoEnMemoria;
import pe.agroruta.domain.model.ConflictoDominioException;
import pe.agroruta.domain.model.Coordenada;
import pe.agroruta.domain.model.Dinero;
import pe.agroruta.domain.model.DominioException;
import pe.agroruta.domain.model.Envio;
import pe.agroruta.domain.model.EstadoEnvio;
import pe.agroruta.domain.model.Lote;
import pe.agroruta.domain.model.Pedido;
import pe.agroruta.domain.model.RecursoNoEncontradoException;
import pe.agroruta.domain.model.SesionEnlace;
import pe.agroruta.domain.model.ReglasSeguimiento;
import pe.agroruta.domain.model.TipoAlerta;
import pe.agroruta.domain.model.TipoEnlace;
import pe.agroruta.domain.port.in.SeguimientoConductorUseCase.ResultadoPosicion;

class SeguimientoServiceTest {

    private static final Instant AHORA = Instant.parse("2026-11-06T15:00:00Z");
    private static final Clock RELOJ = Clock.fixed(AHORA, ZoneId.of("America/Lima"));
    private static final Coordenada TACNA = new Coordenada(-18.0146, -70.2536);
    private static final SesionEnlace CONDUCTOR = new SesionEnlace("empresa_demo", 1L, TipoEnlace.CONDUCTOR);
    private static final SesionEnlace COMPRADOR = new SesionEnlace("empresa_demo", 1L, TipoEnlace.COMPRADOR);

    private final EnviosEnMemoria envios = new EnviosEnMemoria();
    private final PedidosEnMemoria pedidos = new PedidosEnMemoria();
    private final DestinosEnMemoria destinos = new DestinosEnMemoria();
    private final SeguimientoEnMemoria seguimiento = new SeguimientoEnMemoria();
    private final PublicadorGrabador publicador = new PublicadorGrabador();
    private final Falsos.RutasEnMemoria rutas = new Falsos.RutasEnMemoria();
    private final Falsos.RutaFalsa calculador = new Falsos.RutaFalsa();
    private SeguimientoConductorService conductor;
    private ConsultarSeguimientoService consulta;

    @BeforeEach
    void preparar() {
        conductor = new SeguimientoConductorService(envios, pedidos, destinos, seguimiento, publicador, calculador,
                rutas, RELOJ);
        consulta = new ConsultarSeguimientoService(envios, pedidos, destinos, destinos, seguimiento, rutas, RELOJ);
        new AdministrarDestinosService(destinos, destinos, destinos).registrarDestino("Mercado Tacna", "Tacna", false);
        destinos.guardarCoordenada(1L, TACNA);
        Lote lote = Lote.nuevo("Palta", new BigDecimal("1000"), LocalDate.of(2026, 11, 5), "Sur", "dueno.demo",
                AHORA.minus(Duration.ofDays(1)), LocalDate.of(2026, 11, 6)).conId(1L);
        pedidos.guardarNuevo(Pedido.nuevo(1L, lote, 1L, Dinero.de("5"), "x1x", AHORA.minus(Duration.ofDays(1))));
        enRuta();
    }

    private void enRuta() {
        envios.envios.clear();
        envios.guardarNuevo(new Envio(null, 1L, 1L, 1L, 1L, EstadoEnvio.EN_RUTA, AHORA.minus(Duration.ofHours(4)),
                AHORA.minus(Duration.ofHours(3)), null, "operador.demo"));
    }

    private ResultadoPosicion reportar(double latitud, double longitud, Instant fecha) {
        return conductor.reportarPosicion(CONDUCTOR, latitud, longitud, 12.0, fecha);
    }

    private void aceptarConsentimiento() {
        conductor.iniciar(CONDUCTOR, true, true, true);
    }

    // ---------- iniciar ----------

    @Test
    void iniciar_exige_consentimiento_gps_permiso_y_envio_en_ruta() {
        assertThatThrownBy(() -> conductor.iniciar(CONDUCTOR, true, true, false))
                .isInstanceOf(DominioException.class).hasMessageContaining("aceptar compartir");
        assertThatThrownBy(() -> conductor.iniciar(CONDUCTOR, false, true, true))
                .isInstanceOf(ConflictoDominioException.class).hasMessageContaining("GPS");
        assertThatThrownBy(() -> conductor.iniciar(CONDUCTOR, true, false, true))
                .isInstanceOf(ConflictoDominioException.class);
        assertThat(seguimiento.tieneConsentimiento(1L)).isFalse();

        envios.envios.set(0, new Envio(1L, 1L, 1L, 1L, 1L, EstadoEnvio.PROGRAMADO, AHORA, null, null, "x1x"));
        assertThatThrownBy(() -> conductor.iniciar(CONDUCTOR, true, true, true))
                .isInstanceOf(ConflictoDominioException.class).hasMessageContaining("no está en ruta");

        enRuta();
        aceptarConsentimiento();
        assertThat(seguimiento.tieneConsentimiento(1L)).isTrue();
    }

    @Test
    void un_enlace_de_comprador_no_puede_compartir_ubicacion() {
        assertThatThrownBy(() -> conductor.iniciar(COMPRADOR, true, true, true)).isInstanceOf(DominioException.class);
        assertThatThrownBy(() -> conductor.reportarPosicion(COMPRADOR, -18, -70, 5.0, AHORA))
                .isInstanceOf(DominioException.class);
        assertThatThrownBy(() -> conductor.reportarEstadoGps(COMPRADOR, false)).isInstanceOf(DominioException.class);
    }

    // ---------- reportar posición ----------

    @Test
    void sin_consentimiento_no_se_guarda_ninguna_posicion() {
        assertThatThrownBy(() -> reportar(-17.0, -71.0, AHORA.minusSeconds(5)))
                .isInstanceOf(ConflictoDominioException.class).hasMessageContaining("no aceptó compartir");
        assertThat(seguimiento.posiciones).isEmpty();
    }

    @Test
    void guarda_la_posicion_la_publica_con_la_hora_estimada_y_la_sesion_fija_el_envio() {
        aceptarConsentimiento();

        assertThat(reportar(-17.0, -71.0, AHORA.minusSeconds(5))).isEqualTo(ResultadoPosicion.ACEPTADA);

        assertThat(seguimiento.posiciones).hasSize(1);
        assertThat(seguimiento.posiciones.get(0).idEnvio()).isEqualTo(1L);
        assertThat(publicador.posiciones).hasSize(1);
        assertThat(publicador.negocios).containsExactly("empresa_demo");
        assertThat(publicador.etas.get(0)).isNotNull().isPositive();
    }

    @Test
    void reenviar_la_misma_posicion_no_la_duplica_ni_la_vuelve_a_publicar() {
        aceptarConsentimiento();
        Instant fecha = AHORA.minusSeconds(5);
        reportar(-17.0, -71.0, fecha);

        assertThat(reportar(-17.0, -71.0, fecha)).isEqualTo(ResultadoPosicion.DUPLICADA);

        assertThat(seguimiento.posiciones).hasSize(1);
        assertThat(publicador.posiciones).hasSize(1);
    }

    @Test
    void rechaza_posiciones_de_antes_de_la_salida_futuras_y_mal_formadas() {
        aceptarConsentimiento();

        assertThatThrownBy(() -> reportar(-17.0, -71.0, AHORA.minus(Duration.ofHours(3)).minusSeconds(1)))
                .isInstanceOf(DominioException.class).hasMessageContaining("tiempo");
        assertThatThrownBy(() -> reportar(-17.0, -71.0, AHORA.plus(Duration.ofMinutes(10))))
                .isInstanceOf(DominioException.class);
        assertThatThrownBy(() -> reportar(120.0, -71.0, AHORA)).isInstanceOf(DominioException.class);
        assertThat(seguimiento.posiciones).isEmpty();
    }

    @Test
    void acepta_posiciones_guardadas_sin_senal_sin_generar_avisos_viejos() {
        aceptarConsentimiento();
        // Veinte minutos sin señal: llegan juntas, algunas justo en el destino.
        for (int minutos = 20; minutos >= 6; minutos--) {
            reportar(TACNA.latitud(), TACNA.longitud(), AHORA.minus(Duration.ofMinutes(minutos)));
        }

        assertThat(seguimiento.posiciones).hasSize(15);
        assertThat(publicador.posiciones).hasSize(15);
        assertThat(seguimiento.alertas).isEmpty();
        assertThat(publicador.etas).containsOnlyNulls();
    }

    // ---------- avisos ----------

    // ---------- ruta y retraso ----------

    @Test
    void con_la_primera_posicion_se_calcula_y_publica_la_ruta_y_la_siguiente_la_reutiliza() {
        aceptarConsentimiento();

        reportar(-17.0, -71.0, AHORA.minusSeconds(60));
        reportar(-17.0, -70.99, AHORA.minusSeconds(30));

        assertThat(calculador.llamadas).isEqualTo(1);
        assertThat(publicador.rutasPublicadas).isEqualTo(1);
        var ruta = rutas.guardadas.get(1L);
        assertThat(ruta.porCarretera()).isTrue();
        assertThat(ruta.puntos().get(0)).isEqualTo(new Coordenada(-17.0, -71.0));
        assertThat(ruta.puntos().get(ruta.puntos().size() - 1)).isEqualTo(TACNA);
    }

    @Test
    void si_el_vehiculo_se_sale_de_la_ruta_se_calcula_una_nueva_desde_donde_esta() {
        aceptarConsentimiento();

        reportar(-17.0, -71.0, AHORA.minusSeconds(60));
        reportar(-16.5, -71.0, AHORA.minusSeconds(30));

        assertThat(calculador.llamadas).isEqualTo(2);
        assertThat(publicador.rutasPublicadas).isEqualTo(2);
        assertThat(rutas.guardadas.get(1L).puntos().get(0)).isEqualTo(new Coordenada(-16.5, -71.0));
    }

    @Test
    void sin_servicio_de_rutas_queda_una_linea_recta_y_el_seguimiento_sigue_funcionando() {
        aceptarConsentimiento();
        calculador.disponible = false;

        assertThat(reportar(-17.0, -71.0, AHORA.minusSeconds(30))).isEqualTo(ResultadoPosicion.ACEPTADA);

        var ruta = rutas.guardadas.get(1L);
        assertThat(ruta.porCarretera()).isFalse();
        assertThat(ruta.puntos()).hasSize(2);
        assertThat(publicador.etas.get(0)).isNotNull().isPositive();
    }

    @Test
    void avisa_una_sola_vez_si_el_vehiculo_va_mucho_mas_lento_de_lo_previsto() {
        aceptarConsentimiento();

        reportar(-17.0, -71.0, AHORA.minusSeconds(100));
        reportar(-16.996, -71.0, AHORA.minusSeconds(5));
        reportar(-16.992, -71.0, AHORA.minusSeconds(1));

        assertThat(seguimiento.alertas).extracting(a -> a.tipoAlerta()).containsExactly(TipoAlerta.RETRASO);
    }

    @Test
    void la_consulta_trae_la_ruta_y_calcula_la_hora_estimada_sobre_ella() {
        aceptarConsentimiento();
        reportar(-17.0, -71.0, AHORA.minusSeconds(30));

        var seguimientoEnvio = consulta.obtener(1L);

        assertThat(seguimientoEnvio.ruta()).isNotNull();
        assertThat(seguimientoEnvio.velocidadKmh()).isBetween(45.0, 55.0);
        // Por el codo de la ruta hay mas camino que en linea recta: la hora estimada lo refleja.
        long enLineaRecta = ReglasSeguimiento.etaMinutos(seguimientoEnvio.trazoReciente(), TACNA).getAsLong();
        assertThat(seguimientoEnvio.etaMinutos()).isNotNull();
        assertThat(seguimientoEnvio.etaMinutos()).isNotEqualTo(enLineaRecta);
    }

    @Test
    void avisa_una_sola_vez_cuando_el_vehiculo_llega_al_destino() {
        aceptarConsentimiento();

        reportar(TACNA.latitud() + 0.0100, TACNA.longitud(), AHORA.minusSeconds(60));
        assertThat(seguimiento.alertas).isEmpty();
        reportar(TACNA.latitud() + 0.0009, TACNA.longitud(), AHORA.minusSeconds(30));
        reportar(TACNA.latitud() + 0.0005, TACNA.longitud(), AHORA.minusSeconds(5));

        assertThat(seguimiento.alertas).hasSize(1);
        assertThat(seguimiento.alertas.get(0).tipoAlerta()).isEqualTo(TipoAlerta.LLEGADA);
        assertThat(publicador.alertas).hasSize(1);
    }

    @Test
    void sin_coordenadas_del_destino_no_hay_hora_estimada_ni_aviso_de_llegada() {
        destinos.coordenadas.clear();
        aceptarConsentimiento();

        reportar(TACNA.latitud(), TACNA.longitud(), AHORA.minusSeconds(5));

        assertThat(publicador.etas.get(0)).isNull();
        assertThat(seguimiento.alertas).isEmpty();
    }

    @Test
    void avisa_una_sola_vez_cuando_lleva_mas_de_quince_minutos_detenido() {
        aceptarConsentimiento();
        for (int segundos = 17 * 60; segundos >= 30; segundos -= 30) {
            seguimiento.guardarPosicion(new pe.agroruta.domain.model.Posicion(null, 1L,
                    new Coordenada(-17.0, -71.0), 10.0, AHORA.minusSeconds(segundos), AHORA.minusSeconds(segundos)));
        }

        reportar(-17.0, -71.0, AHORA.minusSeconds(5));
        reportar(-17.0, -71.0, AHORA.minusSeconds(2));

        assertThat(seguimiento.alertas).extracting(a -> a.tipoAlerta()).containsExactly(TipoAlerta.DETENIDO);
    }

    @Test
    void un_vehiculo_en_movimiento_no_genera_aviso_de_detencion() {
        aceptarConsentimiento();
        for (int segundos = 17 * 60; segundos >= 30; segundos -= 30) {
            seguimiento.guardarPosicion(new pe.agroruta.domain.model.Posicion(null, 1L,
                    new Coordenada(-17.0 - segundos * 0.0001, -71.0), 10.0, AHORA.minusSeconds(segundos),
                    AHORA.minusSeconds(segundos)));
        }

        reportar(-17.0, -71.0, AHORA.minusSeconds(5));

        assertThat(seguimiento.alertas).extracting(x -> x.tipoAlerta()).doesNotContain(TipoAlerta.DETENIDO);
    }

    @Test
    void si_el_gps_se_desactiva_en_ruta_avisa_y_no_repite_el_aviso_enseguida() {
        conductor.reportarEstadoGps(CONDUCTOR, true);
        assertThat(seguimiento.alertas).isEmpty();

        conductor.reportarEstadoGps(CONDUCTOR, false);
        conductor.reportarEstadoGps(CONDUCTOR, false);

        assertThat(seguimiento.alertas).extracting(a -> a.tipoAlerta()).containsExactly(TipoAlerta.GPS_DESACTIVADO);
        assertThat(publicador.alertas).hasSize(1);
    }

    @Test
    void el_aviso_de_gps_solo_aplica_mientras_el_envio_esta_en_ruta() {
        envios.envios.set(0, new Envio(1L, 1L, 1L, 1L, 1L, EstadoEnvio.PROGRAMADO, AHORA, null, null, "x1x"));

        conductor.reportarEstadoGps(CONDUCTOR, false);

        assertThat(seguimiento.alertas).isEmpty();
    }

    // ---------- consulta ----------

    @Test
    void la_consulta_trae_la_ultima_posicion_el_destino_y_la_hora_estimada() {
        aceptarConsentimiento();
        reportar(-17.0, -71.0, AHORA.minusSeconds(60));
        reportar(-17.01, -71.0, AHORA.minusSeconds(30));

        var estado = consulta.obtener(1L);

        assertThat(estado.ciudadDestino()).isEqualTo("Tacna");
        assertThat(estado.coordenadaDestino()).isEqualTo(TACNA);
        assertThat(estado.ultimaPosicion().coordenada().latitud()).isEqualTo(-17.01);
        assertThat(estado.etaMinutos()).isNotNull().isPositive();
        assertThat(estado.trazoReciente()).hasSize(2);
        assertThat(estado.ahora()).isEqualTo(AHORA);
        assertThat(consulta.recorrido(1L)).hasSize(2);
    }

    @Test
    void sin_posiciones_o_ya_entregado_no_hay_hora_estimada() {
        assertThat(consulta.obtener(1L).ultimaPosicion()).isNull();
        assertThat(consulta.obtener(1L).etaMinutos()).isNull();

        aceptarConsentimiento();
        reportar(-17.0, -71.0, AHORA.minusSeconds(30));
        envios.envios.set(0, new Envio(1L, 1L, 1L, 1L, 1L, EstadoEnvio.ENTREGADO, AHORA.minus(Duration.ofHours(4)),
                AHORA.minus(Duration.ofHours(3)), AHORA, "x1x"));

        assertThat(consulta.obtener(1L).etaMinutos()).isNull();
    }

    @Test
    void consultar_un_envio_inexistente_da_no_encontrado() {
        assertThatThrownBy(() -> consulta.obtener(99L)).isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> consulta.recorrido(99L)).isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> consulta.alertas(99L)).isInstanceOf(RecursoNoEncontradoException.class);
    }

    // ---------- enlaces con token ----------

    @Test
    void genera_un_enlace_para_el_conductor_y_otro_para_el_comprador_guardando_solo_el_hash() {
        var enlaces = new EnlacesEnMemoria();
        var generar = new GenerarEnlacesEnvioService(envios, enlaces, new GeneradorTokenFalso(), RELOJ);

        var generados = generar.generar("empresa_demo", 1L);

        assertThat(generados.tokenConductor()).isNotEqualTo(generados.tokenComprador());
        assertThat(enlaces.enlaces).hasSize(2);
        assertThat(enlaces.enlaces).extracting(e -> e.hashToken())
                .containsExactlyInAnyOrder("hash:" + generados.tokenConductor(), "hash:" + generados.tokenComprador());
        assertThat(enlaces.enlaces).extracting(e -> e.tipoEnlace())
                .containsExactlyInAnyOrder(TipoEnlace.CONDUCTOR, TipoEnlace.COMPRADOR);
        assertThat(generados.fechaExpiracion()).isEqualTo(AHORA.plus(GenerarEnlacesEnvioService.VIGENCIA));
    }

    @Test
    void al_generar_otros_enlaces_los_anteriores_dejan_de_valer() {
        var enlaces = new EnlacesEnMemoria();
        var generador = new GeneradorTokenFalso();
        var generar = new GenerarEnlacesEnvioService(envios, enlaces, generador, RELOJ);
        var autenticar = new AutenticarEnlaceService(enlaces, generador, RELOJ);

        var primeros = generar.generar("empresa_demo", 1L);
        assertThat(autenticar.resolver(primeros.tokenConductor())).isPresent();

        var segundos = generar.generar("empresa_demo", 1L);

        assertThat(autenticar.resolver(primeros.tokenConductor())).isEmpty();
        assertThat(autenticar.resolver(primeros.tokenComprador())).isEmpty();
        assertThat(autenticar.resolver(segundos.tokenConductor()).orElseThrow())
                .isEqualTo(new SesionEnlace("empresa_demo", 1L, TipoEnlace.CONDUCTOR));
        assertThat(autenticar.resolver(segundos.tokenComprador()).orElseThrow().tipoEnlace())
                .isEqualTo(TipoEnlace.COMPRADOR);
    }

    @Test
    void renovar_el_enlace_del_comprador_no_corta_al_conductor_y_viceversa() {
        var enlaces = new EnlacesEnMemoria();
        var generador = new GeneradorTokenFalso();
        var generar = new GenerarEnlacesEnvioService(envios, enlaces, generador, RELOJ);
        var autenticar = new AutenticarEnlaceService(enlaces, generador, RELOJ);
        var primeros = generar.generar("empresa_demo", 1L);

        var comprador = generar.generar("empresa_demo", 1L, TipoEnlace.COMPRADOR);

        assertThat(comprador.tokenConductor()).isNull();
        assertThat(autenticar.resolver(primeros.tokenConductor())).isPresent();
        assertThat(autenticar.resolver(primeros.tokenComprador())).isEmpty();
        assertThat(autenticar.resolver(comprador.tokenComprador())).isPresent();

        var conductor = generar.generar("empresa_demo", 1L, TipoEnlace.CONDUCTOR);

        assertThat(conductor.tokenComprador()).isNull();
        assertThat(autenticar.resolver(primeros.tokenConductor())).isEmpty();
        assertThat(autenticar.resolver(conductor.tokenConductor())).isPresent();
        assertThat(autenticar.resolver(comprador.tokenComprador())).isPresent();
    }

    @Test
    void no_genera_enlaces_de_envios_entregados_ni_inexistentes_y_no_acepta_tokens_raros() {
        var enlaces = new EnlacesEnMemoria();
        var generador = new GeneradorTokenFalso();
        var generar = new GenerarEnlacesEnvioService(envios, enlaces, generador, RELOJ);

        assertThatThrownBy(() -> generar.generar("empresa_demo", 99L)).isInstanceOf(RecursoNoEncontradoException.class);

        var autenticar = new AutenticarEnlaceService(enlaces, generador, RELOJ);
        assertThat(autenticar.resolver(null)).isEmpty();
        assertThat(autenticar.resolver("  ")).isEmpty();
        assertThat(autenticar.resolver("x".repeat(500))).isEmpty();
        assertThat(autenticar.resolver("token-inexistente")).isEmpty();
    }

    @Test
    void de_un_envio_ya_entregado_solo_se_genera_el_enlace_del_comprador_para_que_pueda_pagar() {
        var enlaces = new EnlacesEnMemoria();
        var generador = new GeneradorTokenFalso();
        envios.envios.set(0, new Envio(1L, 1L, 1L, 1L, 1L, EstadoEnvio.ENTREGADO, AHORA, AHORA, AHORA, "x1x"));

        var generados = new GenerarEnlacesEnvioService(envios, enlaces, generador, RELOJ).generar("empresa_demo", 1L);

        assertThat(generados.tokenConductor()).isNull();
        assertThat(generados.tokenComprador()).isNotBlank();
        assertThat(enlaces.enlaces).hasSize(1);
        assertThat(enlaces.enlaces.get(0).tipoEnlace()).isEqualTo(TipoEnlace.COMPRADOR);
        assertThat(new AutenticarEnlaceService(enlaces, generador, RELOJ).resolver(generados.tokenComprador()))
                .isPresent();
    }

    @Test
    void un_enlace_vencido_ya_no_sirve() {
        var enlaces = new EnlacesEnMemoria();
        var generador = new GeneradorTokenFalso();
        var token = new GenerarEnlacesEnvioService(envios, enlaces, generador, RELOJ)
                .generar("empresa_demo", 1L).tokenConductor();
        Clock despues = Clock.fixed(AHORA.plus(GenerarEnlacesEnvioService.VIGENCIA).plusSeconds(1),
                ZoneId.of("America/Lima"));

        assertThat(new AutenticarEnlaceService(enlaces, generador, despues).resolver(token)).isEmpty();
    }
}
