package pe.agroruta.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import pe.agroruta.application.service.Falsos.CompradoresEnMemoria;
import pe.agroruta.application.service.Falsos.ComprobantesEnMemoria;
import pe.agroruta.application.service.Falsos.CorrelativoEnMemoria;
import pe.agroruta.application.service.Falsos.EmisorGrabador;
import pe.agroruta.application.service.Falsos.EnviosEnMemoria;
import pe.agroruta.application.service.Falsos.LiquidacionesEnMemoria;
import pe.agroruta.application.service.Falsos.PagosEnMemoria;
import pe.agroruta.application.service.Falsos.PedidosEnMemoria;
import pe.agroruta.application.service.LotesServiceTest.LotesEnMemoria;
import pe.agroruta.domain.model.Archivo;
import pe.agroruta.domain.model.Comprador;
import pe.agroruta.domain.model.ComprobanteImpreso;
import pe.agroruta.domain.model.ConflictoDominioException;
import pe.agroruta.domain.model.Dinero;
import pe.agroruta.domain.model.DominioException;
import pe.agroruta.domain.model.Envio;
import pe.agroruta.domain.model.EstadoComprobante;
import pe.agroruta.domain.model.EstadoEnvio;
import pe.agroruta.domain.model.EventoLote;
import pe.agroruta.domain.model.Liquidacion;
import pe.agroruta.domain.model.Lote;
import pe.agroruta.domain.model.Pedido;
import pe.agroruta.domain.model.RecursoNoEncontradoException;
import pe.agroruta.domain.model.SeriesComprobante;
import pe.agroruta.domain.model.SesionEnlace;
import pe.agroruta.domain.model.TipoComprador;
import pe.agroruta.domain.model.TipoEnlace;
import pe.agroruta.domain.model.TipoEventoLote;
import pe.agroruta.domain.port.in.PagarPedidoUseCase.Comando;
import pe.agroruta.domain.port.out.RepresentacionComprobantePort;
import pe.agroruta.infrastructure.adapter.out.pago.PasarelaSimulada;

/** El PDF del comprobante: solo de lo que SUNAT aceptó, y con los mismos datos que se le enviaron. */
class DescargarComprobanteServiceTest {

    private static final Instant T0 = Instant.parse("2026-11-06T15:00:00Z");
    private static final SesionEnlace COMPRADOR = new SesionEnlace("empresa_demo", 1L, TipoEnlace.COMPRADOR);
    private static final SesionEnlace CONDUCTOR = new SesionEnlace("empresa_demo", 1L, TipoEnlace.CONDUCTOR);

    private final RelojMutable reloj = new RelojMutable(T0);
    private final LotesEnMemoria lotes = new LotesEnMemoria();
    private final CompradoresEnMemoria compradores = new CompradoresEnMemoria();
    private final PedidosEnMemoria pedidos = new PedidosEnMemoria();
    private final EnviosEnMemoria envios = new EnviosEnMemoria();
    private final LiquidacionesEnMemoria liquidaciones = new LiquidacionesEnMemoria();
    private final PagosEnMemoria pagos = new PagosEnMemoria();
    private final ComprobantesEnMemoria comprobantes = new ComprobantesEnMemoria();
    private final EmisorGrabador emisor = new EmisorGrabador();
    private final PdfGrabador pdf = new PdfGrabador();

    private PagarPedidoService pagar;
    private DescargarComprobanteService descargar;

    static class PdfGrabador implements RepresentacionComprobantePort {
        final List<ComprobanteImpreso> impresos = new ArrayList<>();

        @Override
        public byte[] pdf(ComprobanteImpreso comprobante) {
            impresos.add(comprobante);
            return new byte[]{'%', 'P', 'D', 'F'};
        }
    }

    @BeforeEach
    void preparar() {
        EmitirComprobanteService emitir = new EmitirComprobanteService(pagos, comprobantes, liquidaciones, pedidos,
                compradores, lotes, new CorrelativoEnMemoria(), emisor, new SeriesComprobante("B001", "F001"), reloj);
        pagar = new PagarPedidoService(envios, pedidos, liquidaciones, compradores, pagos, comprobantes,
                new PasarelaSimulada(reloj), emitir, reloj);
        descargar = new DescargarComprobanteService(pagos, comprobantes, envios, liquidaciones, pedidos, compradores,
                lotes, pdf);
        compradores.guardar(Comprador.nuevo("Mercado Central SAC", TipoComprador.MERCADO, "20123456789", null, "Lima"));
        Lote lote = lotes.guardarNuevo(Lote.nuevo("Palta Hass", new BigDecimal("1250.5"), LocalDate.of(2026, 11, 5),
                        "Sur", "dueno.demo", T0, LocalDate.of(2026, 11, 6)),
                new EventoLote(TipoEventoLote.REGISTRO, "registro", "dueno.demo", T0));
        Pedido pedido = pedidos.guardarNuevo(Pedido.nuevo(1L, lote, 3L, Dinero.de("5.40"), "operador.demo", T0));
        Envio envio = envios.guardarNuevo(new Envio(null, pedido.idPedido(), lote.idLote(), 1L, 1L,
                EstadoEnvio.ENTREGADO, T0, T0, T0, "operador.demo"));
        liquidaciones.guardarNueva(Liquidacion.calcular(pedido, envio, new BigDecimal("1180"),
                new BigDecimal("40.5"), new BigDecimal("30"), Dinero.de("1.20"), "operador.demo", T0));
    }

    private Long pagarConTarjeta() {
        return pagar.pagar(new Comando(COMPRADOR, "sim_aprobada_visa_4242", "comprador@ejemplo.com",
                "clave-de-operacion-1", "visa", null)).idPago();
    }

    @Test
    void el_pdf_lleva_los_mismos_datos_que_se_enviaron_a_sunat() {
        Long idPago = pagarConTarjeta();

        Archivo archivo = descargar.pdfDePago(idPago);

        assertThat(archivo.nombre()).isEqualTo("factura-F001-1.pdf");
        assertThat(archivo.tipoContenido()).isEqualTo("application/pdf");
        assertThat(archivo.contenido()).startsWith('%', 'P', 'D', 'F');
        ComprobanteImpreso impreso = pdf.impresos.get(0);
        assertThat(impreso.datos()).isEqualTo(emisor.emitidos.get(0));
        assertThat(impreso.comprobante().estadoComprobante()).isEqualTo(EstadoComprobante.ACEPTADO);
    }

    @Test
    void el_comprador_descarga_el_pdf_de_su_pedido_con_su_enlace() {
        pagarConTarjeta();

        assertThat(descargar.pdfDelComprador(COMPRADOR).nombre()).isEqualTo("factura-F001-1.pdf");
    }

    @Test
    void no_se_imprime_un_comprobante_que_sunat_no_acepto() {
        emisor.respuesta = EstadoComprobante.RECHAZADO;
        Long idPago = pagarConTarjeta();

        assertThatThrownBy(() -> descargar.pdfDePago(idPago)).isInstanceOf(ConflictoDominioException.class)
                .hasMessageContaining("todavía no fue aceptado");
        assertThat(pdf.impresos).isEmpty();
    }

    @Test
    void el_enlace_del_conductor_no_sirve_para_ver_el_comprobante() {
        pagarConTarjeta();

        assertThatThrownBy(() -> descargar.pdfDelComprador(CONDUCTOR)).isInstanceOf(DominioException.class);
    }

    @Test
    void sin_pago_aprobado_no_hay_comprobante_que_descargar() {
        assertThatThrownBy(() -> descargar.pdfDelComprador(COMPRADOR))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }
}
