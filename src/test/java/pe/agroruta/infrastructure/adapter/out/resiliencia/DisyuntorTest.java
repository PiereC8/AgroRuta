package pe.agroruta.infrastructure.adapter.out.resiliencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import pe.agroruta.domain.model.ServicioEnPausaException;
import pe.agroruta.domain.model.ServicioExternoNoDisponibleException;

class DisyuntorTest {

    private static final class RelojMutable extends Clock {
        private Instant ahora = Instant.parse("2026-11-06T15:00:00Z");

        void avanzar(Duration duracion) {
            ahora = ahora.plus(duracion);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zona) {
            return this;
        }

        @Override
        public Instant instant() {
            return ahora;
        }
    }

    private final RelojMutable reloj = new RelojMutable();
    private final Disyuntor disyuntor = new Disyuntor("pasarela", 3, Duration.ofSeconds(30), reloj);
    private final AtomicInteger llamadas = new AtomicInteger();

    private String caida() {
        llamadas.incrementAndGet();
        throw new ServicioExternoNoDisponibleException("sin respuesta");
    }

    private String bien() {
        llamadas.incrementAndGet();
        return "ok";
    }

    private void fallar(int veces) {
        for (int i = 0; i < veces; i++) {
            assertThatThrownBy(() -> disyuntor.ejecutar(this::caida))
                    .isInstanceOf(ServicioExternoNoDisponibleException.class)
                    .isNotInstanceOf(ServicioEnPausaException.class);
        }
    }

    @Test
    void mientras_el_servicio_responde_todo_pasa_igual() {
        assertThat(disyuntor.ejecutar(this::bien)).isEqualTo("ok");
        assertThat(disyuntor.ejecutar(this::bien)).isEqualTo("ok");
        assertThat(llamadas.get()).isEqualTo(2);
    }

    @Test
    void despues_de_varias_caidas_seguidas_deja_de_llamar_al_servicio() {
        fallar(3);

        assertThatThrownBy(() -> disyuntor.ejecutar(this::bien)).isInstanceOf(ServicioEnPausaException.class);
        assertThat(llamadas.get()).isEqualTo(3);
    }

    @Test
    void un_exito_en_medio_reinicia_la_cuenta_de_caidas() {
        fallar(2);
        disyuntor.ejecutar(this::bien);
        fallar(2);

        assertThat(disyuntor.ejecutar(this::bien)).isEqualTo("ok");
    }

    @Test
    void los_errores_que_no_son_caidas_del_servicio_no_cuentan() {
        for (int i = 0; i < 10; i++) {
            assertThatThrownBy(() -> disyuntor.ejecutar(() -> {
                throw new IllegalArgumentException("dato mal formado");
            })).isInstanceOf(IllegalArgumentException.class);
        }

        assertThat(disyuntor.ejecutar(this::bien)).isEqualTo("ok");
    }

    @Test
    void pasada_la_pausa_deja_pasar_una_prueba_y_si_sale_bien_vuelve_a_la_normalidad() {
        fallar(3);
        reloj.avanzar(Duration.ofSeconds(31));

        assertThat(disyuntor.ejecutar(this::bien)).isEqualTo("ok");
        assertThat(disyuntor.ejecutar(this::bien)).isEqualTo("ok");
    }

    @Test
    void si_la_prueba_falla_la_pausa_empieza_de_nuevo_completa() {
        fallar(3);
        reloj.avanzar(Duration.ofSeconds(31));
        fallar(1);

        reloj.avanzar(Duration.ofSeconds(29));
        assertThatThrownBy(() -> disyuntor.ejecutar(this::bien)).isInstanceOf(ServicioEnPausaException.class);

        reloj.avanzar(Duration.ofSeconds(2));
        assertThat(disyuntor.ejecutar(this::bien)).isEqualTo("ok");
    }

    @Test
    void durante_la_prueba_otra_llamada_a_la_vez_se_rechaza() {
        fallar(3);
        reloj.avanzar(Duration.ofSeconds(31));

        String resultado = disyuntor.ejecutar(() -> {
            assertThatThrownBy(() -> disyuntor.ejecutar(this::bien)).isInstanceOf(ServicioEnPausaException.class);
            return "prueba";
        });

        assertThat(resultado).isEqualTo("prueba");
    }
}
