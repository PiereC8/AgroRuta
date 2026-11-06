package pe.agroruta.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import pe.agroruta.application.service.DestinosServiceTest.DestinosEnMemoria;
import pe.agroruta.application.service.Falsos.CompradoresEnMemoria;
import pe.agroruta.application.service.Falsos.EnviosEnMemoria;
import pe.agroruta.application.service.Falsos.LiquidacionesEnMemoria;
import pe.agroruta.application.service.Falsos.PedidosEnMemoria;
import pe.agroruta.application.service.Falsos.TransaccionDirecta;
import pe.agroruta.application.service.Falsos.TransporteEnMemoria;
import pe.agroruta.application.service.LotesServiceTest.LotesEnMemoria;
import pe.agroruta.domain.model.ConflictoDominioException;
import pe.agroruta.domain.model.Dinero;
import pe.agroruta.domain.model.DominioException;
import pe.agroruta.domain.model.EstadoEnvio;
import pe.agroruta.domain.model.EstadoLote;
import pe.agroruta.domain.model.EstadoPedido;
import pe.agroruta.domain.model.EventoLote;
import pe.agroruta.domain.model.Lote;
import pe.agroruta.domain.model.RecursoNoEncontradoException;
import pe.agroruta.domain.model.TipoComprador;
import pe.agroruta.domain.model.TipoEventoLote;
import pe.agroruta.domain.port.in.GestionarPedidosUseCase.Comando;
import pe.agroruta.domain.port.in.RegistrarEntregaUseCase;
import pe.agroruta.domain.port.out.TarifaPort;

class EntregaServiceTest {

    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-11-06T15:00:00Z"), ZoneId.of("America/Lima"));

    private final LotesEnMemoria lotes = new LotesEnMemoria();
    private final DestinosEnMemoria destinos = new DestinosEnMemoria();
    private final PedidosEnMemoria pedidos = new PedidosEnMemoria();
    private final EnviosEnMemoria envios = new EnviosEnMemoria();
    private final LiquidacionesEnMemoria liquidaciones = new LiquidacionesEnMemoria();
    private final TransaccionDirecta transaccion = new TransaccionDirecta();
    private final TransporteEnMemoria transporte = new TransporteEnMemoria();

    private RegistrarEntregaService entrega;
    private DespacharEnvioService despacho;

    private void montar(TarifaPort tarifas) {
        entrega = new RegistrarEntregaService(envios, pedidos, lotes, destinos, tarifas, liquidaciones, transaccion,
                RELOJ);
    }

    private void enRutaHacia(long idDestino) {
        var compradores = new CompradoresEnMemoria();
        new AdministrarCompradoresService(compradores).registrar("Mercado Central", TipoComprador.MERCADO, null, null,
                "Lima");
        new AdministrarTransporteService(transporte).registrarTransportista("Transportes Andinos", null);
        new AdministrarTransporteService(transporte).registrarVehiculo(1L, "ABC-123", new BigDecimal("2000"));
        lotes.guardarNuevo(Lote.nuevo("Palta Hass", new BigDecimal("1250.5"), LocalDate.of(2026, 11, 5), "Sur",
                        "dueno.demo", Instant.parse("2026-11-05T15:00:00Z"), LocalDate.of(2026, 11, 6)),
                new EventoLote(TipoEventoLote.REGISTRO, "registro", "dueno.demo", Instant.now()));
        new GestionarPedidosService(pedidos, compradores, lotes, destinos, RELOJ)
                .registrar(new Comando(1L, 1L, idDestino, new BigDecimal("5.40"), "operador.demo"));
        despacho = new DespacharEnvioService(pedidos, lotes, transporte, envios, transaccion, RELOJ);
        despacho.despachar(1L, 1L, "operador.demo");
        despacho.iniciarRuta(1L, "operador.demo");
    }

    @BeforeEach
    void destinosBase() {
        var admin = new AdministrarDestinosService(destinos, destinos, destinos);
        admin.registrarDestino("Mercado Lima", "Lima", false);
        admin.registrarDestino("Mercado Tacna", "Tacna", true);
        montar(ids -> Map.of(1L, Dinero.de("1.20")));
    }

    private RegistrarEntregaUseCase.Comando comando(String recibido, String merma, String rechazo) {
        return new RegistrarEntregaUseCase.Comando(1L, new BigDecimal(recibido), new BigDecimal(merma),
                new BigDecimal(rechazo), "operador.demo");
    }

    @Test
    void registra_la_entrega_liquida_y_cierra_el_ciclo_del_lote() {
        enRutaHacia(1L);

        var liquidacion = entrega.registrar(comando("1180", "40.5", "30"));

        assertThat(liquidacion.gananciaReal().monto().toPlainString()).isEqualTo("4871.40");
        assertThat(liquidacion.perdidaPorMermaYRechazo().monto().toPlainString()).isEqualTo("380.70");
        assertThat(envios.buscarPorId(1L).orElseThrow().estadoEnvio()).isEqualTo(EstadoEnvio.ENTREGADO);
        assertThat(pedidos.buscarPorId(1L).orElseThrow().estadoPedido()).isEqualTo(EstadoPedido.ENTREGADO);
        assertThat(lotes.buscarPorId(1L).orElseThrow().estadoLote()).isEqualTo(EstadoLote.LIQUIDADO);
        assertThat(liquidaciones.buscarPorLote(1L)).isPresent();
        var tipos = lotes.eventos.stream().map(EventoLote::tipoEvento).toList();
        assertThat(tipos.subList(tipos.size() - 2, tipos.size()))
                .containsExactly(TipoEventoLote.ENTREGA, TipoEventoLote.LIQUIDACION);
    }

    @Test
    void con_cantidades_que_no_suman_no_cambia_nada() {
        enRutaHacia(1L);

        assertThatThrownBy(() -> entrega.registrar(comando("1180", "40.5", "29")))
                .isInstanceOf(DominioException.class).hasMessageContaining("suman");

        assertThat(envios.buscarPorId(1L).orElseThrow().estadoEnvio()).isEqualTo(EstadoEnvio.EN_RUTA);
        assertThat(lotes.buscarPorId(1L).orElseThrow().estadoLote()).isEqualTo(EstadoLote.DESPACHADO);
        assertThat(liquidaciones.liquidaciones).isEmpty();
    }

    @Test
    void solo_se_entrega_un_envio_que_esta_en_ruta_y_solo_una_vez() {
        enRutaHacia(1L);
        entrega.registrar(comando("1250.5", "0", "0"));

        assertThatThrownBy(() -> entrega.registrar(comando("1250.5", "0", "0")))
                .isInstanceOf(ConflictoDominioException.class);
        assertThat(liquidaciones.liquidaciones).hasSize(1);
    }

    @Test
    void un_envio_solo_programado_todavia_no_se_puede_entregar() {
        enRutaHacia(1L);
        // Un segundo lote y pedido quedan solo programados.
        envios.envios.set(0, envios.envios.get(0).conId(1L));
        var programado = new pe.agroruta.domain.model.Envio(1L, 1L, 1L, 1L, 1L, EstadoEnvio.PROGRAMADO,
                Instant.now(), null, null, "x1x");
        envios.actualizar(programado);

        assertThatThrownBy(() -> entrega.registrar(comando("1250.5", "0", "0")))
                .isInstanceOf(ConflictoDominioException.class);
    }

    @Test
    void un_destino_local_se_liquida_sin_flete() {
        enRutaHacia(2L);

        var liquidacion = entrega.registrar(comando("1250.5", "0", "0"));

        assertThat(liquidacion.costoFleteTotal()).isEqualTo(Dinero.CERO);
        assertThat(liquidacion.gananciaReal().monto().toPlainString()).isEqualTo("6752.70");
    }

    @Test
    void un_destino_externo_sin_tarifa_pide_fijarla_y_no_cambia_nada() {
        enRutaHacia(1L);
        montar(ids -> Map.of());

        assertThatThrownBy(() -> entrega.registrar(comando("1250.5", "0", "0")))
                .isInstanceOf(ConflictoDominioException.class).hasMessageContaining("no tiene tarifa de flete");
        assertThat(envios.buscarPorId(1L).orElseThrow().estadoEnvio()).isEqualTo(EstadoEnvio.EN_RUTA);
        assertThat(liquidaciones.liquidaciones).isEmpty();
    }

    @Test
    void un_envio_inexistente_da_no_encontrado_y_la_consulta_de_liquidacion_tambien() {
        assertThatThrownBy(() -> entrega.registrar(comando("1", "0", "0")))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> new ConsultarLiquidacionesService(liquidaciones).obtenerPorLote(1L))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> new ConsultarLiquidacionesService(liquidaciones).listar(0, 0))
                .isInstanceOf(DominioException.class);
    }

    @Test
    void la_liquidacion_guardada_se_consulta_por_lote() {
        enRutaHacia(1L);
        entrega.registrar(comando("1250.5", "0", "0"));

        var consultada = new ConsultarLiquidacionesService(liquidaciones).obtenerPorLote(1L);

        assertThat(consultada.gananciaReal().monto().toPlainString()).isEqualTo("5252.10");
    }
}
