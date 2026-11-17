package pe.agroruta.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import pe.agroruta.application.service.AvisosServiceTest.BandejaEnMemoria;
import pe.agroruta.domain.model.Comprador;
import pe.agroruta.domain.model.Dinero;
import pe.agroruta.domain.model.Envio;
import pe.agroruta.domain.model.EstadoEnvio;
import pe.agroruta.domain.model.EstadoPedido;
import pe.agroruta.domain.model.Pedido;
import pe.agroruta.domain.model.TipoAviso;
import pe.agroruta.domain.model.TipoComprador;
import pe.agroruta.domain.model.TipoEnlace;
import pe.agroruta.domain.model.Transportista;
import pe.agroruta.domain.port.in.GenerarEnlacesEnvioUseCase;

/**
 * Prueba RF-14 y RF-21 (RFF-09 y RFF-22) sin base de datos (RNF-11): usa repositorios falsos en memoria y revisa qué queda en la
 * bandeja de correo según el comprador tenga o no correo.
 */
class AvisarDespachoServiceTest {

    private static final Instant AHORA = Instant.parse("2026-11-14T15:00:00Z");

    private BandejaEnMemoria bandeja;
    private Falsos.CompradoresEnMemoria compradores;
    private Falsos.PedidosEnMemoria pedidos;
    private Falsos.TransporteEnMemoria transporte;
    private Falsos.EnviosEnMemoria envios;
    private AvisarDespachoService servicio;
    private final List<TipoEnlace> tiposPedidos = new ArrayList<>();

    @BeforeEach
    void preparar() {
        bandeja = new BandejaEnMemoria();
        compradores = new Falsos.CompradoresEnMemoria();
        pedidos = new Falsos.PedidosEnMemoria();
        transporte = new Falsos.TransporteEnMemoria();
        envios = new Falsos.EnviosEnMemoria();
        GenerarEnlacesEnvioUseCase enlaces = (negocio, idEnvio, tipo) -> {
            tiposPedidos.add(tipo);
            return new GenerarEnlacesEnvioUseCase.EnlacesGenerados("tok-conductor",
                    tipo == TipoEnlace.CONDUCTOR ? null : "tok-comprador", Instant.parse("2026-12-14T15:00:00Z"));
        };
        servicio = new AvisarDespachoService(enlaces, envios, pedidos, compradores, transporte, bandeja,
                Clock.fixed(AHORA, ZoneId.of("America/Lima")));
    }

    private void despachadoA(String correoComprador, String telefonoTransportista) {
        Comprador c = compradores.guardar(Comprador.nuevo("Mercado Central", TipoComprador.MERCADO, null, null,
                "Lima", correoComprador));
        Pedido p = pedidos.guardarNuevo(new Pedido(null, c.idComprador(), 5L, 1L, new BigDecimal("100"),
                Dinero.de("4.00"), EstadoPedido.DESPACHADO, "operador", AHORA));
        Transportista t = transporte.guardarTransportista(Transportista.nuevo("Juan Quispe", telefonoTransportista));
        envios.guardarNuevo(new Envio(null, p.idPedido(), 5L, 1L, t.idTransportista(), EstadoEnvio.PROGRAMADO, AHORA,
                null, null, "operador"));
    }

    @Test
    void con_correo_avisa_al_comprador_y_devuelve_el_enlace_y_telefono_del_conductor() {
        despachadoA("Compras@Mercado.pe", "987 654 321");

        var r = servicio.avisar("sol_de_tacna", 1L, "https://agroruta.vercel.app");

        assertThat(r.urlConductor()).isEqualTo("https://agroruta.vercel.app/conductor.html#t=tok-conductor");
        assertThat(r.nombreTransportista()).isEqualTo("Juan Quispe");
        assertThat(r.telefonoTransportista()).isEqualTo("987 654 321");
        assertThat(r.correoComprador()).isEqualTo("compras@mercado.pe");
        assertThat(tiposPedidos).containsExactly((TipoEnlace) null);
        assertThat(bandeja.todas).singleElement().satisfies(n -> {
            assertThat(n.tipo()).isEqualTo(TipoAviso.SEGUIMIENTO_ENVIO);
            assertThat(n.correoDestino()).isEqualTo("compras@mercado.pe");
            assertThat(n.cuerpo()).contains("https://agroruta.vercel.app/seguimiento.html#t=tok-comprador")
                    .contains("14/12/2026");
        });
    }

    @Test
    void sin_correo_del_comprador_no_genera_su_enlace_ni_manda_correo() {
        despachadoA(null, null);

        var r = servicio.avisar("sol_de_tacna", 1L, "https://x.pe");

        assertThat(r.correoComprador()).isNull();
        assertThat(r.telefonoTransportista()).isNull();
        assertThat(r.urlConductor()).endsWith("#t=tok-conductor");
        assertThat(tiposPedidos).containsExactly(TipoEnlace.CONDUCTOR);
        assertThat(bandeja.todas).isEmpty();
    }
}
