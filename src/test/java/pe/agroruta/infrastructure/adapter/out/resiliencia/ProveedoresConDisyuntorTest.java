package pe.agroruta.infrastructure.adapter.out.resiliencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import pe.agroruta.domain.model.DatosComprobante;
import pe.agroruta.domain.model.Dinero;
import pe.agroruta.domain.model.EstadoComprobante;
import pe.agroruta.domain.model.ResultadoCobro;
import pe.agroruta.domain.model.ResultadoEmision;
import pe.agroruta.domain.model.ServicioEnPausaException;
import pe.agroruta.domain.model.ServicioExternoNoDisponibleException;
import pe.agroruta.domain.model.SolicitudCobro;
import pe.agroruta.domain.model.TipoComprobante;
import pe.agroruta.domain.port.out.ComprobantePort;
import pe.agroruta.domain.port.out.PagoPort;

class ProveedoresConDisyuntorTest {

    private static final Clock RELOJ = Clock.systemUTC();

    private static SolicitudCobro solicitud() {
        return new SolicitudCobro(Dinero.de("10.00"), "a@b.co", "tkn", "clave-de-operacion-1", "pedido",
                Map.of("negocio", "empresa_demo", "idPago", "1"));
    }

    private static final class PagoCaido implements PagoPort {
        int llamadas;
        boolean caido = true;

        @Override
        public ResultadoCobro cobrar(SolicitudCobro solicitud) {
            llamadas++;
            if (caido) {
                throw new ServicioExternoNoDisponibleException("caída");
            }
            return ResultadoCobro.rechazado("1", "fondos_insuficientes", "sin fondos", solicitud.metadatos());
        }

        @Override
        public Optional<ResultadoCobro> consultarCargo(String idCargo) {
            llamadas++;
            throw new ServicioExternoNoDisponibleException("caída");
        }
    }

    @Test
    void la_pasarela_en_pausa_no_recibe_mas_llamadas_ni_para_cobrar_ni_para_consultar() {
        var real = new PagoCaido();
        var pasarela = new PagoPortConDisyuntor(real, new Disyuntor("pasarela", 2, Duration.ofSeconds(30), RELOJ));

        assertThatThrownBy(() -> pasarela.cobrar(solicitud())).isInstanceOf(ServicioExternoNoDisponibleException.class);
        assertThatThrownBy(() -> pasarela.consultarCargo("1")).isInstanceOf(ServicioExternoNoDisponibleException.class);

        assertThatThrownBy(() -> pasarela.cobrar(solicitud())).isInstanceOf(ServicioEnPausaException.class);
        assertThatThrownBy(() -> pasarela.consultarCargo("1")).isInstanceOf(ServicioEnPausaException.class);
        assertThat(real.llamadas).isEqualTo(2);
    }

    @Test
    void un_pago_rechazado_es_una_respuesta_normal_y_no_cuenta_como_caida() {
        var real = new PagoCaido();
        real.caido = false;
        var pasarela = new PagoPortConDisyuntor(real, new Disyuntor("pasarela", 2, Duration.ofSeconds(30), RELOJ));

        for (int i = 0; i < 5; i++) {
            assertThat(pasarela.cobrar(solicitud()).estado()).isEqualTo(ResultadoCobro.Estado.RECHAZADO);
        }

        assertThat(real.llamadas).isEqualTo(5);
    }

    @Test
    void el_proveedor_de_comprobantes_en_pausa_deja_de_recibir_llamadas() {
        int[] llamadas = {0};
        ComprobantePort real = new ComprobantePort() {
            @Override
            public ResultadoEmision emitir(DatosComprobante datos) {
                llamadas[0]++;
                throw new ServicioExternoNoDisponibleException("caído");
            }

            @Override
            public ResultadoEmision consultar(TipoComprobante tipo, String serie, int numero) {
                llamadas[0]++;
                return new ResultadoEmision(EstadoComprobante.ACEPTADO, null, null, "ok");
            }
        };
        var proveedor = new ComprobantePortConDisyuntor(real, new Disyuntor("comprobantes", 2, Duration.ofSeconds(30), RELOJ));
        var datos = new DatosComprobante(TipoComprobante.BOLETA, "B001", 1, LocalDate.of(2026, 11, 6),
                new DatosComprobante.Cliente("-", "-", "CLIENTE VARIOS", "a@b.co"),
                List.of(new DatosComprobante.Item("KGM", "P", "Palta", BigDecimal.ONE, Dinero.de("5"), Dinero.de("5"))),
                Dinero.de("5"));

        assertThatThrownBy(() -> proveedor.emitir(datos)).isInstanceOf(ServicioExternoNoDisponibleException.class);
        assertThatThrownBy(() -> proveedor.emitir(datos)).isInstanceOf(ServicioExternoNoDisponibleException.class);
        assertThatThrownBy(() -> proveedor.consultar(TipoComprobante.BOLETA, "B001", 1))
                .isInstanceOf(ServicioEnPausaException.class);

        assertThat(llamadas[0]).isEqualTo(2);
    }
}
