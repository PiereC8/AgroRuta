package pe.agroruta.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import pe.agroruta.application.service.DestinosServiceTest.DestinosEnMemoria;
import pe.agroruta.application.service.Falsos.CompradoresEnMemoria;
import pe.agroruta.application.service.Falsos.EnviosEnMemoria;
import pe.agroruta.application.service.Falsos.PedidosEnMemoria;
import pe.agroruta.application.service.Falsos.TransaccionDirecta;
import pe.agroruta.application.service.Falsos.TransporteEnMemoria;
import pe.agroruta.application.service.LotesServiceTest.LotesEnMemoria;
import pe.agroruta.domain.model.ConflictoDominioException;
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

class DespachoServiceTest {

    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-11-06T15:00:00Z"), ZoneId.of("America/Lima"));

    private final LotesEnMemoria lotes = new LotesEnMemoria();
    private final DestinosEnMemoria destinos = new DestinosEnMemoria();
    private final CompradoresEnMemoria compradores = new CompradoresEnMemoria();
    private final PedidosEnMemoria pedidos = new PedidosEnMemoria();
    private final TransporteEnMemoria transporte = new TransporteEnMemoria();
    private final EnviosEnMemoria envios = new EnviosEnMemoria();
    private final TransaccionDirecta transaccion = new TransaccionDirecta();

    private GestionarPedidosService gestionarPedidos;
    private DespacharEnvioService despacho;

    @BeforeEach
    void preparar() {
        gestionarPedidos = new GestionarPedidosService(pedidos, compradores, lotes, destinos, envios, transaccion, RELOJ);
        despacho = new DespacharEnvioService(pedidos, lotes, transporte, envios, transaccion, RELOJ);

        new AdministrarDestinosService(destinos, destinos, destinos).registrarDestino("Mercado Lima", "Lima", false);
        new AdministrarCompradoresService(compradores).registrar("Mercado Central SAC", TipoComprador.MERCADO,
                "20123456789", "987654321", "Lima");
        var admin = new AdministrarTransporteService(transporte);
        admin.registrarTransportista("Transportes Andinos", "054-123456");
        admin.registrarVehiculo(1L, "abc-123", new BigDecimal("2000"));
        admin.registrarVehiculo(1L, "XYZ-987", new BigDecimal("500"));
        registrarLote("1000");
    }

    private void registrarLote(String kg) {
        lotes.guardarNuevo(Lote.nuevo("Palta Hass", new BigDecimal(kg), LocalDate.of(2026, 11, 5), "Chacra Sur",
                        "dueno.demo", Instant.parse("2026-11-05T15:00:00Z"), LocalDate.of(2026, 11, 6)),
                new EventoLote(TipoEventoLote.REGISTRO, "registro", "dueno.demo", Instant.now()));
    }

    private Comando pedido(long idLote) {
        return new Comando(1L, idLote, 1L, null, new BigDecimal("5.20"), "operador.demo");
    }

    @Test
    void el_pedido_cubre_todo_el_lote_y_calcula_el_ingreso_acordado() {
        var pedido = gestionarPedidos.registrar(pedido(1L));

        assertThat(pedido.cantidadKg().toPlainString()).isEqualTo("1000.000");
        assertThat(pedido.estadoPedido()).isEqualTo(EstadoPedido.PENDIENTE);
        assertThat(pedido.ingresoAcordado().monto().toPlainString()).isEqualTo("5200.00");
    }

    private Comando pedidoDe(long idLote, String kg) {
        return new Comando(1L, idLote, 1L, new BigDecimal(kg), new BigDecimal("5.20"), "operador.demo");
    }

    @Test
    void un_lote_se_reparte_entre_varios_pedidos_mientras_queden_kg() {
        var primero = gestionarPedidos.registrar(pedidoDe(1L, "400"));
        var segundo = gestionarPedidos.registrar(pedidoDe(1L, "350.500"));

        assertThat(primero.cantidadKg()).isEqualByComparingTo("400");
        assertThat(segundo.cantidadKg()).isEqualByComparingTo("350.5");
        assertThat(lotes.buscarPorId(1L).orElseThrow().estadoLote()).isEqualTo(EstadoLote.RESERVADO);
        var consulta = new ConsultarLotesService(lotes, pedidos).obtener(1L);
        assertThat(consulta.kgComprometidos()).isEqualByComparingTo("750.5");
        assertThat(consulta.kgDisponibles()).isEqualByComparingTo("249.5");
        assertThat(lotes.eventos.get(lotes.eventos.size() - 1).descripcionEvento())
                .contains("350.500 kg").contains("quedan 249.500 kg disponibles");
    }

    @Test
    void un_pedido_sin_cantidad_toma_todo_lo_que_queda_disponible() {
        gestionarPedidos.registrar(pedidoDe(1L, "300"));

        var resto = gestionarPedidos.registrar(pedido(1L));

        assertThat(resto.cantidadKg()).isEqualByComparingTo("700");
    }

    @Test
    void no_se_pide_mas_de_lo_que_queda_ni_de_un_lote_agotado() {
        gestionarPedidos.registrar(pedidoDe(1L, "900"));

        assertThatThrownBy(() -> gestionarPedidos.registrar(pedidoDe(1L, "100.001")))
                .isInstanceOf(ConflictoDominioException.class).hasMessageContaining("solo tiene 100.000 kg disponibles");
        gestionarPedidos.registrar(pedidoDe(1L, "100"));
        assertThatThrownBy(() -> gestionarPedidos.registrar(pedido(1L)))
                .isInstanceOf(ConflictoDominioException.class).hasMessageContaining("no tiene kg disponibles");
        assertThat(pedidos.pedidos).hasSize(2);
    }

    @Test
    void la_cantidad_del_pedido_debe_ser_positiva_y_con_hasta_tres_decimales() {
        assertThatThrownBy(() -> gestionarPedidos.registrar(pedidoDe(1L, "0")))
                .isInstanceOf(DominioException.class);
        assertThatThrownBy(() -> gestionarPedidos.registrar(pedidoDe(1L, "-5")))
                .isInstanceOf(DominioException.class);
        assertThatThrownBy(() -> gestionarPedidos.registrar(pedidoDe(1L, "10.0001")))
                .isInstanceOf(DominioException.class).hasMessageContaining("3 decimales");
    }

    @Test
    void cancelar_un_pedido_devuelve_sus_kg_al_stock_y_el_lote_vuelve_a_estar_disponible() {
        gestionarPedidos.registrar(pedidoDe(1L, "400"));
        gestionarPedidos.registrar(pedidoDe(1L, "600"));
        var cancelar = new CancelarOperacionService(envios, pedidos, lotes, new Falsos.EnlacesEnMemoria(), transaccion,
                RELOJ);

        cancelar.cancelarPedido(1L, "operador.demo");

        var consulta = new ConsultarLotesService(lotes, pedidos).obtener(1L);
        assertThat(consulta.kgDisponibles()).isEqualByComparingTo("400");
        assertThat(lotes.buscarPorId(1L).orElseThrow().estadoLote()).isEqualTo(EstadoLote.RESERVADO);
        cancelar.cancelarPedido(2L, "operador.demo");
        assertThat(new ConsultarLotesService(lotes, pedidos).obtener(1L).kgDisponibles()).isEqualByComparingTo("1000");
        assertThat(lotes.buscarPorId(1L).orElseThrow().estadoLote()).isEqualTo(EstadoLote.REGISTRADO);
    }

    @Test
    void el_lote_va_en_camino_si_alguno_de_sus_envios_sale_y_sigue_reservado_con_los_demas() {
        gestionarPedidos.registrar(pedidoDe(1L, "400"));
        gestionarPedidos.registrar(pedidoDe(1L, "300"));
        despacho.despachar(1L, 1L, "operador.demo");
        assertThat(lotes.buscarPorId(1L).orElseThrow().estadoLote()).isEqualTo(EstadoLote.RESERVADO);

        despacho.iniciarRuta(1L, "operador.demo");

        assertThat(lotes.buscarPorId(1L).orElseThrow().estadoLote()).isEqualTo(EstadoLote.DESPACHADO);
        assertThat(pedidos.buscarPorId(2L).orElseThrow().estadoPedido()).isEqualTo(EstadoPedido.PENDIENTE);
    }

    @Test
    void la_capacidad_del_vehiculo_se_compara_con_el_pedido_y_no_con_todo_el_lote() {
        // El vehículo 2 carga hasta 500 kg y el lote pesa 1000: con un pedido de 450 kg sí se puede despachar.
        gestionarPedidos.registrar(pedidoDe(1L, "450"));

        var envio = despacho.despachar(1L, 2L, "operador.demo");

        assertThat(envio.estadoEnvio()).isEqualTo(EstadoEnvio.PROGRAMADO);
        gestionarPedidos.registrar(pedidoDe(1L, "550"));
        assertThat(despacho.despachar(2L, 1L, "operador.demo").estadoEnvio()).isEqualTo(EstadoEnvio.PROGRAMADO);
    }

    @Test
    void el_pedido_valida_comprador_lote_destino_y_precio() {
        assertThatThrownBy(() -> gestionarPedidos.registrar(new Comando(9L, 1L, 1L, null, BigDecimal.ONE, "x1x")))
                .isInstanceOf(RecursoNoEncontradoException.class).hasMessageContaining("comprador");
        assertThatThrownBy(() -> gestionarPedidos.registrar(new Comando(1L, 9L, 1L, null, BigDecimal.ONE, "x1x")))
                .isInstanceOf(RecursoNoEncontradoException.class).hasMessageContaining("lote");
        assertThatThrownBy(() -> gestionarPedidos.registrar(new Comando(1L, 1L, 9L, null, BigDecimal.ONE, "x1x")))
                .isInstanceOf(RecursoNoEncontradoException.class).hasMessageContaining("destino");
        assertThatThrownBy(() -> gestionarPedidos.registrar(new Comando(1L, 1L, 1L, null, BigDecimal.ZERO, "x1x")))
                .isInstanceOf(DominioException.class);
        assertThat(pedidos.pedidos).isEmpty();
    }

    @Test
    void despachar_reserva_el_lote_asigna_vehiculo_y_deja_constancia_en_el_historial() {
        gestionarPedidos.registrar(pedido(1L));

        var envio = despacho.despachar(1L, 1L, "operador.demo");

        assertThat(envio.estadoEnvio()).isEqualTo(EstadoEnvio.PROGRAMADO);
        assertThat(envio.idTransportista()).isEqualTo(1L);
        assertThat(pedidos.buscarPorId(1L).orElseThrow().estadoPedido()).isEqualTo(EstadoPedido.DESPACHADO);
        assertThat(lotes.buscarPorId(1L).orElseThrow().estadoLote()).isEqualTo(EstadoLote.RESERVADO);
        assertThat(lotes.eventos.get(lotes.eventos.size() - 1).tipoEvento()).isEqualTo(TipoEventoLote.RESERVA);
        assertThat(lotes.eventos.get(lotes.eventos.size() - 1).descripcionEvento()).contains("1000.000 kg");
        // Una transacción reservó el pedido y otra despachó el envío.
        assertThat(transaccion.ejecuciones).isEqualTo(2);
    }

    @Test
    void no_despacha_si_el_vehiculo_no_tiene_capacidad_y_no_cambia_nada() {
        gestionarPedidos.registrar(pedido(1L));

        assertThatThrownBy(() -> despacho.despachar(1L, 2L, "operador.demo"))
                .isInstanceOf(DominioException.class).hasMessageContaining("carga hasta 500.000 kg");

        assertThat(envios.envios).isEmpty();
        assertThat(pedidos.buscarPorId(1L).orElseThrow().estadoPedido()).isEqualTo(EstadoPedido.PENDIENTE);
        // El pedido conserva sus kg reservados aunque el despacho no se haya podido hacer.
        assertThat(lotes.buscarPorId(1L).orElseThrow().estadoLote()).isEqualTo(EstadoLote.RESERVADO);
    }

    @Test
    void un_vehiculo_con_envio_en_curso_no_puede_tomar_otro() {
        registrarLote("800");
        gestionarPedidos.registrar(pedido(1L));
        gestionarPedidos.registrar(new Comando(1L, 2L, 1L, null, new BigDecimal("5.00"), "operador.demo"));
        despacho.despachar(1L, 1L, "operador.demo");

        assertThatThrownBy(() -> despacho.despachar(2L, 1L, "operador.demo"))
                .isInstanceOf(ConflictoDominioException.class).hasMessageContaining("envío en curso");
        assertThat(lotes.buscarPorId(2L).orElseThrow().estadoLote()).isEqualTo(EstadoLote.RESERVADO);
    }

    @Test
    void un_pedido_no_se_despacha_dos_veces() {
        gestionarPedidos.registrar(pedido(1L));
        despacho.despachar(1L, 1L, "operador.demo");

        assertThatThrownBy(() -> despacho.despachar(1L, 1L, "operador.demo"))
                .isInstanceOf(ConflictoDominioException.class);
        assertThat(envios.envios).hasSize(1);
    }

    @Test
    void despachar_con_pedido_o_vehiculo_inexistentes_da_no_encontrado() {
        gestionarPedidos.registrar(pedido(1L));

        assertThatThrownBy(() -> despacho.despachar(9L, 1L, "x1x")).isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> despacho.despachar(1L, 9L, "x1x")).isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void al_salir_en_ruta_el_envio_pasa_a_en_ruta_y_el_lote_a_despachado() {
        gestionarPedidos.registrar(pedido(1L));
        despacho.despachar(1L, 1L, "operador.demo");

        var envio = despacho.iniciarRuta(1L, "operador.demo");

        assertThat(envio.estadoEnvio()).isEqualTo(EstadoEnvio.EN_RUTA);
        assertThat(envio.fechaSalida()).isEqualTo(RELOJ.instant());
        assertThat(envios.buscarPorId(1L).orElseThrow().estadoEnvio()).isEqualTo(EstadoEnvio.EN_RUTA);
        assertThat(lotes.buscarPorId(1L).orElseThrow().estadoLote()).isEqualTo(EstadoLote.DESPACHADO);
        assertThat(lotes.eventos.get(lotes.eventos.size() - 1).tipoEvento()).isEqualTo(TipoEventoLote.DESPACHO);
    }

    @Test
    void no_se_puede_salir_dos_veces_ni_con_un_envio_inexistente() {
        gestionarPedidos.registrar(pedido(1L));
        despacho.despachar(1L, 1L, "operador.demo");
        despacho.iniciarRuta(1L, "operador.demo");

        assertThatThrownBy(() -> despacho.iniciarRuta(1L, "operador.demo"))
                .isInstanceOf(ConflictoDominioException.class);
        assertThatThrownBy(() -> despacho.iniciarRuta(9L, "operador.demo"))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void listar_valida_la_paginacion() {
        assertThatThrownBy(() -> despacho.listar(0, 500)).isInstanceOf(DominioException.class);
        assertThatThrownBy(() -> gestionarPedidos.listar(-1, 10)).isInstanceOf(DominioException.class);
    }
}
