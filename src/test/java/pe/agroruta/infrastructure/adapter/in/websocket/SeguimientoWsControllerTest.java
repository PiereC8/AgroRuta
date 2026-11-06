package pe.agroruta.infrastructure.adapter.in.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import pe.agroruta.domain.model.ConflictoDominioException;
import pe.agroruta.domain.model.DominioException;
import pe.agroruta.domain.model.Rol;
import pe.agroruta.domain.model.SesionEnlace;
import pe.agroruta.domain.model.TipoEnlace;
import pe.agroruta.domain.port.in.SeguimientoConductorUseCase;
import pe.agroruta.infrastructure.adapter.in.websocket.SeguimientoWsController.EstadoGpsMensaje;
import pe.agroruta.infrastructure.adapter.in.websocket.SeguimientoWsController.IniciarMensaje;
import pe.agroruta.infrastructure.adapter.in.websocket.SeguimientoWsController.PosicionMensaje;
import pe.agroruta.infrastructure.config.TenantContext;

class SeguimientoWsControllerTest {

    private final SesionEnlace sesion = new SesionEnlace("empresa_demo", 7L, TipoEnlace.CONDUCTOR);
    private final UsuarioWs conductor = UsuarioWs.deEnlace(sesion);
    private final List<String> negociosVistos = new ArrayList<>();
    private RuntimeException falla;
    private ResultadoPosicionFalso resultado = new ResultadoPosicionFalso(ResultadoPosicion.ACEPTADA);

    private enum ResultadoPosicion { ACEPTADA, DUPLICADA }

    private record ResultadoPosicionFalso(ResultadoPosicion valor) {
    }

    private final SeguimientoConductorUseCase casoDeUso = new SeguimientoConductorUseCase() {
        @Override
        public void iniciar(SesionEnlace s, boolean gpsActivo, boolean permisoUbicacion, boolean consentimiento) {
            negociosVistos.add(TenantContext.actual().orElse("sin negocio"));
            if (falla != null) {
                throw falla;
            }
        }

        @Override
        public SeguimientoConductorUseCase.ResultadoPosicion reportarPosicion(SesionEnlace s, double latitud,
                                                                              double longitud, Double precision,
                                                                              Instant fecha) {
            negociosVistos.add(TenantContext.actual().orElse("sin negocio"));
            if (falla != null) {
                throw falla;
            }
            return SeguimientoConductorUseCase.ResultadoPosicion.valueOf(resultado.valor().name());
        }

        @Override
        public void reportarEstadoGps(SesionEnlace s, boolean gpsActivo) {
            negociosVistos.add(TenantContext.actual().orElse("sin negocio"));
            if (falla != null) {
                throw falla;
            }
        }
    };

    private final SeguimientoWsController controlador = new SeguimientoWsController(casoDeUso);

    @AfterEach
    void limpiar() {
        TenantContext.limpiar();
    }

    private PosicionMensaje posicion() {
        return new PosicionMensaje("abc-1", -17.0, -71.0, 8.0, 1_793_000_000_000L);
    }

    @Test
    void la_posicion_se_procesa_dentro_del_negocio_del_enlace_y_se_confirma_con_su_id_local() {
        var acuse = controlador.posicion(posicion(), conductor);

        assertThat(acuse.estado()).isEqualTo("ACEPTADA");
        assertThat(acuse.idLocal()).isEqualTo("abc-1");
        assertThat(negociosVistos).containsExactly("empresa_demo");
        assertThat(TenantContext.actual()).isEmpty();
    }

    @Test
    void una_posicion_repetida_tambien_se_confirma_para_que_el_celular_la_borre() {
        resultado = new ResultadoPosicionFalso(ResultadoPosicion.DUPLICADA);

        assertThat(controlador.posicion(posicion(), conductor).estado()).isEqualTo("DUPLICADA");
    }

    @Test
    void un_rechazo_de_regla_se_responde_como_rechazada_con_el_motivo() {
        falla = new ConflictoDominioException("El conductor todavía no aceptó compartir su ubicación");

        var acuse = controlador.posicion(posicion(), conductor);

        assertThat(acuse.estado()).isEqualTo("RECHAZADA");
        assertThat(acuse.idLocal()).isEqualTo("abc-1");
        assertThat(acuse.detalle()).contains("no aceptó compartir");
    }

    @Test
    void un_error_inesperado_no_se_confirma_para_que_el_celular_reintente() {
        falla = new IllegalStateException("la base no responde");

        assertThatThrownBy(() -> controlador.posicion(posicion(), conductor))
                .isInstanceOf(IllegalStateException.class);
        assertThat(controlador.error(falla).detalle()).contains("se reintentará");
    }

    @Test
    void iniciar_y_avisar_del_gps_responden_con_su_propio_tipo_de_acuse() {
        assertThat(controlador.iniciar(new IniciarMensaje(true, true, true), conductor).tipo()).isEqualTo("INICIO");
        assertThat(controlador.estadoGps(new EstadoGpsMensaje(false), conductor).tipo()).isEqualTo("GPS");

        falla = new DominioException("Debes aceptar compartir tu ubicación para iniciar");
        var rechazo = controlador.iniciar(new IniciarMensaje(true, true, false), conductor);
        assertThat(rechazo.estado()).isEqualTo("RECHAZADA");
        assertThat(rechazo.detalle()).contains("aceptar compartir");
    }

    @Test
    void quien_no_es_conductor_no_puede_enviar_nada() {
        UsuarioWs comprador = UsuarioWs.deEnlace(new SesionEnlace("empresa_demo", 7L, TipoEnlace.COMPRADOR));
        UsuarioWs dueno = UsuarioWs.interno("dueno.demo", "empresa_demo", Rol.DUENO);

        assertThatThrownBy(() -> controlador.posicion(posicion(), comprador)).isInstanceOf(DominioException.class);
        assertThatThrownBy(() -> controlador.posicion(posicion(), dueno)).isInstanceOf(DominioException.class);
        assertThatThrownBy(() -> controlador.posicion(posicion(), null)).isInstanceOf(DominioException.class);
        assertThat(negociosVistos).isEmpty();
    }
}
