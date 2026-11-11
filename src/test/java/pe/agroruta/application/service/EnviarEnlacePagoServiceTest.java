package pe.agroruta.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import pe.agroruta.application.service.AvisosServiceTest.BandejaEnMemoria;
import pe.agroruta.domain.model.ConflictoDominioException;
import pe.agroruta.domain.model.Dinero;
import pe.agroruta.domain.model.DominioException;
import pe.agroruta.domain.model.Envio;
import pe.agroruta.domain.model.EstadoEnvio;
import pe.agroruta.domain.model.EstadoNotificacion;
import pe.agroruta.domain.model.Liquidacion;
import pe.agroruta.domain.model.Notificacion;
import pe.agroruta.domain.model.TipoAviso;
import pe.agroruta.domain.model.TipoEnlace;
import pe.agroruta.domain.port.in.EnviarEnlacePagoUseCase.Comando;
import pe.agroruta.domain.port.in.GenerarEnlacesEnvioUseCase;

class EnviarEnlacePagoServiceTest {

    private static final Instant AHORA = Instant.parse("2026-11-09T15:00:00Z");

    private BandejaEnMemoria bandeja;
    private Falsos.EnviosEnMemoria envios;
    private Falsos.LiquidacionesEnMemoria liquidaciones;
    private EnviarEnlacePagoService servicio;
    private final java.util.List<TipoEnlace> tiposPedidos = new java.util.ArrayList<>();

    @BeforeEach
    void preparar() {
        bandeja = new BandejaEnMemoria();
        envios = new Falsos.EnviosEnMemoria();
        liquidaciones = new Falsos.LiquidacionesEnMemoria();
        GenerarEnlacesEnvioUseCase enlaces = (negocio, idEnvio, tipo) -> {
            tiposPedidos.add(tipo);
            return new GenerarEnlacesEnvioUseCase.EnlacesGenerados(null, "tok-comprador",
                    Instant.parse("2026-12-09T15:00:00Z"));
        };
        servicio = new EnviarEnlacePagoService(enlaces, envios, liquidaciones, bandeja,
                Clock.fixed(AHORA, ZoneId.of("America/Lima")));
        envios.guardarNuevo(new Envio(null, 22L, 5L, 1L, 1L, EstadoEnvio.ENTREGADO, AHORA, AHORA, AHORA, "dueno"));
    }

    private void entregar() {
        entregarPor(new Dinero(new BigDecimal("475.00")));
    }

    private void entregarPor(Dinero ingreso) {
        Dinero cero = new Dinero(BigDecimal.ZERO);
        liquidaciones.guardarNueva(new Liquidacion(null, 5L, 22L, 1L, new BigDecimal("100"), new BigDecimal("100"),
                BigDecimal.ZERO, BigDecimal.ZERO, new Dinero(new BigDecimal("4.75")), cero,
                ingreso, cero, ingreso, ingreso, cero, "dueno", AHORA));
    }

    @Test
    void solo_renueva_el_enlace_del_comprador_y_no_corta_al_conductor() {
        entregar();

        servicio.enviar(new Comando("sol_de_tacna", 1L, "compras@ejemplo.pe", "https://x.pe"));

        assertThat(tiposPedidos).containsExactly(TipoEnlace.COMPRADOR);
    }

    @Test
    void no_manda_enlace_de_pago_si_la_entrega_no_tiene_importe() {
        entregarPor(new Dinero(BigDecimal.ZERO));

        assertThatThrownBy(() -> servicio.enviar(new Comando("sol_de_tacna", 1L, "a@b.pe", "https://x.pe")))
                .isInstanceOf(ConflictoDominioException.class).hasMessageContaining("S/ 0.00");
        assertThat(bandeja.todas).isEmpty();
        assertThat(tiposPedidos).isEmpty();
    }

    @Test
    void deja_en_la_bandeja_un_correo_con_el_enlace_el_monto_y_el_vencimiento() {
        entregar();

        var resultado = servicio.enviar(new Comando("sol_de_tacna", 1L, " Compras@Ejemplo.pe ",
                "https://agroruta.duckdns.org"));

        assertThat(resultado.correo()).isEqualTo("compras@ejemplo.pe");
        assertThat(resultado.urlPago()).isEqualTo("https://agroruta.duckdns.org/pago.html#t=tok-comprador");
        Notificacion correo = bandeja.todas.get(0);
        assertThat(correo.tipo()).isEqualTo(TipoAviso.ENLACE_PAGO);
        assertThat(correo.estado()).isEqualTo(EstadoNotificacion.PENDIENTE);
        assertThat(correo.correoDestino()).isEqualTo("compras@ejemplo.pe");
        assertThat(correo.asunto()).isEqualTo("Enlace para pagar tu pedido N.º 22");
        assertThat(correo.cuerpo()).contains("S/ 475.00")
                .contains("https://agroruta.duckdns.org/pago.html#t=tok-comprador").contains("09/12/2026");
    }

    @Test
    void se_puede_mandar_otra_vez_al_mismo_correo() {
        entregar();
        servicio.enviar(new Comando("sol_de_tacna", 1L, "compras@ejemplo.pe", "https://x.pe"));
        servicio = new EnviarEnlacePagoService((n, id, t) -> new GenerarEnlacesEnvioUseCase.EnlacesGenerados(null,
                "otro", AHORA), envios, liquidaciones, bandeja, Clock.fixed(AHORA.plusSeconds(60), ZoneId.of("UTC")));

        servicio.enviar(new Comando("sol_de_tacna", 1L, "compras@ejemplo.pe", "https://x.pe"));

        assertThat(bandeja.todas).hasSize(2);
    }

    @Test
    void exige_un_correo_valido_y_un_pedido_entregado() {
        assertThatThrownBy(() -> servicio.enviar(new Comando("sol_de_tacna", 1L, " ", "https://x.pe")))
                .isInstanceOf(DominioException.class).hasMessage("Escribe el correo del comprador");
        assertThatThrownBy(() -> servicio.enviar(new Comando("sol_de_tacna", 1L, "no-es-correo", "https://x.pe")))
                .isInstanceOf(DominioException.class);
        assertThatThrownBy(() -> servicio.enviar(new Comando("sol_de_tacna", 1L, "a@b.pe", "https://x.pe")))
                .isInstanceOf(ConflictoDominioException.class);
        assertThat(bandeja.todas).isEmpty();
    }
}
