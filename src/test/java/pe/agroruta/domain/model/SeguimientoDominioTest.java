package pe.agroruta.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class SeguimientoDominioTest {

    private static final Instant T0 = Instant.parse("2026-11-06T15:00:00Z");

    private static Posicion en(double latitud, double longitud, Instant fecha) {
        return new Posicion(1L, 1L, new Coordenada(latitud, longitud), 10.0, fecha, fecha);
    }

    @Test
    void la_distancia_entre_coordenadas_es_correcta() {
        assertThat(new Coordenada(0, 0).distanciaMetrosA(new Coordenada(1, 0))).isBetween(111_100.0, 111_300.0);
        // Lima a Tacna, en línea recta, son unos 986 km.
        double lima_tacna = new Coordenada(-12.0464, -77.0428).distanciaMetrosA(new Coordenada(-18.0146, -70.2536));
        assertThat(lima_tacna / 1000).isBetween(980.0, 995.0);
        assertThat(new Coordenada(-18.0, -70.0).distanciaMetrosA(new Coordenada(-18.0, -70.0))).isZero();
    }

    @Test
    void rechaza_coordenadas_fuera_de_rango() {
        assertThatThrownBy(() -> new Coordenada(91, 0)).isInstanceOf(DominioException.class);
        assertThatThrownBy(() -> new Coordenada(0, -181)).isInstanceOf(DominioException.class);
        assertThatThrownBy(() -> new Coordenada(Double.NaN, 0)).isInstanceOf(DominioException.class);
    }

    @Test
    void la_posicion_no_puede_ser_futura_ni_de_hace_mas_de_un_dia() {
        Posicion.nueva(1L, -18, -70, 8.0, T0.minusSeconds(5), T0);
        Posicion.nueva(1L, -18, -70, null, T0.minus(Duration.ofHours(23)), T0);
        Posicion.nueva(1L, -18, -70, 8.0, T0.plusSeconds(30), T0);

        assertThatThrownBy(() -> Posicion.nueva(1L, -18, -70, 8.0, T0.plus(Duration.ofMinutes(5)), T0))
                .isInstanceOf(DominioException.class).hasMessageContaining("futura");
        assertThatThrownBy(() -> Posicion.nueva(1L, -18, -70, 8.0, T0.minus(Duration.ofHours(25)), T0))
                .isInstanceOf(DominioException.class).hasMessageContaining("antigua");
        assertThatThrownBy(() -> Posicion.nueva(1L, -18, -70, -1.0, T0, T0)).isInstanceOf(DominioException.class);
        assertThatThrownBy(() -> Posicion.nueva(1L, 95, -70, 5.0, T0, T0)).isInstanceOf(DominioException.class);
    }

    @Test
    void el_envio_solo_admite_posiciones_tomadas_mientras_estuvo_en_ruta() {
        Envio programado = new Envio(1L, 1L, 1L, 1L, 1L, EstadoEnvio.PROGRAMADO, T0, null, null, "x1x");
        Envio enRuta = new Envio(1L, 1L, 1L, 1L, 1L, EstadoEnvio.EN_RUTA, T0, T0.plusSeconds(60), null, "x1x");
        Envio entregado = new Envio(1L, 1L, 1L, 1L, 1L, EstadoEnvio.ENTREGADO, T0, T0.plusSeconds(60),
                T0.plusSeconds(3600), "x1x");

        assertThat(programado.admitePosicionEn(T0.plusSeconds(100))).isFalse();
        assertThat(enRuta.admitePosicionEn(T0.plusSeconds(59))).isFalse();
        assertThat(enRuta.admitePosicionEn(T0.plusSeconds(60))).isTrue();
        assertThat(enRuta.admitePosicionEn(T0.plus(Duration.ofDays(1)))).isTrue();
        assertThat(entregado.admitePosicionEn(T0.plusSeconds(3600))).isTrue();
        assertThat(entregado.admitePosicionEn(T0.plusSeconds(3601))).isFalse();
    }

    @Test
    void llega_cuando_esta_a_menos_de_300_metros_del_destino() {
        Coordenada destino = new Coordenada(-18.0146, -70.2536);

        assertThat(ReglasSeguimiento.llego(new Coordenada(-18.0146 + 0.0018, -70.2536), destino)).isTrue();
        assertThat(ReglasSeguimiento.llego(new Coordenada(-18.0146 + 0.0045, -70.2536), destino)).isFalse();
    }

    @Test
    void esta_detenido_si_pasa_la_ventana_completa_en_el_mismo_lugar() {
        List<Posicion> quieto = new ArrayList<>();
        for (int segundos = 0; segundos <= 16 * 60; segundos += 30) {
            quieto.add(en(-18.0, -70.0, T0.plusSeconds(segundos)));
        }
        Instant ahora = T0.plusSeconds(16 * 60);

        assertThat(ReglasSeguimiento.detenido(quieto, ahora)).isTrue();
    }

    @Test
    void no_esta_detenido_si_se_mueve_si_falta_cobertura_o_si_la_ultima_posicion_es_vieja() {
        List<Posicion> enMovimiento = new ArrayList<>();
        for (int segundos = 0; segundos <= 16 * 60; segundos += 30) {
            enMovimiento.add(en(-18.0 + segundos * 0.000004, -70.0, T0.plusSeconds(segundos)));
        }
        assertThat(ReglasSeguimiento.detenido(enMovimiento, T0.plusSeconds(16 * 60))).isFalse();

        List<Posicion> pocoTiempo = new ArrayList<>();
        for (int segundos = 0; segundos <= 8 * 60; segundos += 30) {
            pocoTiempo.add(en(-18.0, -70.0, T0.plusSeconds(segundos)));
        }
        assertThat(ReglasSeguimiento.detenido(pocoTiempo, T0.plusSeconds(8 * 60))).isFalse();

        List<Posicion> quieto = new ArrayList<>();
        for (int segundos = 0; segundos <= 16 * 60; segundos += 30) {
            quieto.add(en(-18.0, -70.0, T0.plusSeconds(segundos)));
        }
        assertThat(ReglasSeguimiento.detenido(quieto, T0.plusSeconds(25 * 60))).isFalse();
        assertThat(ReglasSeguimiento.detenido(List.of(), T0)).isFalse();
    }

    @Test
    void la_hora_estimada_usa_la_velocidad_reciente() {
        // Se mueve ~1 km en 2 minutos (30 km/h) y le faltan ~15 km: unos 30 minutos.
        List<Posicion> posiciones = List.of(en(0.0, 0.0, T0), en(0.009, 0.0, T0.plusSeconds(120)));
        Coordenada destino = new Coordenada(0.1449, 0.0);

        assertThat(ReglasSeguimiento.etaMinutos(posiciones, destino).getAsLong()).isBetween(30L, 31L);
    }

    @Test
    void si_esta_quieto_o_hay_una_sola_posicion_usa_la_velocidad_de_referencia() {
        Coordenada destino = new Coordenada(0.1798, 0.0); // ~20 km
        List<Posicion> quieto = List.of(en(0.0, 0.0, T0), en(0.0, 0.0, T0.plusSeconds(120)));

        assertThat(ReglasSeguimiento.etaMinutos(quieto, destino).getAsLong()).isBetween(29L, 31L);
        assertThat(ReglasSeguimiento.etaMinutos(List.of(en(0.0, 0.0, T0)), destino).getAsLong())
                .isBetween(29L, 31L);
        assertThat(ReglasSeguimiento.etaMinutos(List.of(), destino)).isEmpty();
    }

    // ---------- ruta (trayecto) ----------

    private static Trayecto enL() {
        // Un kilometro al norte y luego un kilometro al este (aprox.), a 36 km/h: 200 s.
        Coordenada a = new Coordenada(0.0, 0.0);
        Coordenada b = new Coordenada(0.009, 0.0);
        Coordenada c = new Coordenada(0.009, 0.009);
        return new Trayecto(List.of(a, b, c), 2000, 200, true, T0);
    }

    @Test
    void el_trayecto_mide_cuanto_se_avanzo_y_cuanto_falta() {
        Trayecto t = enL();
        double total = t.longitudMetros();

        Trayecto.Medida alInicio = t.medirDesde(new Coordenada(0.0, 0.0));
        assertThat(alInicio.metrosRecorridos()).isLessThan(1);
        assertThat(alInicio.desvioMetros()).isLessThan(1);

        Trayecto.Medida enElCodo = t.medirDesde(new Coordenada(0.009, 0.0));
        assertThat(enElCodo.metrosRecorridos()).isBetween(995.0, 1005.0);
        assertThat(enElCodo.metrosRestantes()).isBetween(total - 1005, total - 995);

        Trayecto.Medida alFinal = t.medirDesde(new Coordenada(0.009, 0.009));
        assertThat(alFinal.metrosRestantes()).isLessThan(1);
    }

    @Test
    void un_punto_al_lado_de_la_ruta_se_proyecta_sobre_ella_y_cuenta_como_desvio() {
        Trayecto t = enL();

        Trayecto.Medida medida = t.medirDesde(new Coordenada(0.0045, 0.0009));

        assertThat(medida.metrosRecorridos()).isBetween(495.0, 505.0);
        assertThat(medida.desvioMetros()).isBetween(95.0, 105.0);
        assertThat(ReglasSeguimiento.desviado(t, new Coordenada(0.0045, 0.0009))).isFalse();
        assertThat(ReglasSeguimiento.desviado(t, new Coordenada(0.0045, 0.03))).isTrue();
    }

    @Test
    void la_linea_recta_de_respaldo_usa_la_velocidad_de_referencia() {
        Trayecto t = Trayecto.lineaRecta(new Coordenada(0, 0), new Coordenada(0.36, 0), T0);

        assertThat(t.porCarretera()).isFalse();
        assertThat(t.puntos()).hasSize(2);
        // 40 km en linea recta a 40 km/h: una hora.
        assertThat(t.duracionSegundos()).isBetween(3500L, 3700L);
        assertThat(t.velocidadMediaKmh()).isBetween(39.0, 41.0);
    }

    @Test
    void el_trayecto_exige_dos_puntos_y_datos_validos() {
        assertThatThrownBy(() -> new Trayecto(List.of(new Coordenada(0, 0)), 10, 10, true, T0))
                .isInstanceOf(DominioException.class);
        assertThatThrownBy(() -> new Trayecto(List.of(new Coordenada(0, 0), new Coordenada(1, 1)), 0, 10, true, T0))
                .isInstanceOf(DominioException.class);
    }

    @Test
    void la_hora_estimada_se_mide_sobre_la_ruta_y_no_en_linea_recta() {
        Trayecto t = enL();
        List<Posicion> posiciones = List.of(en(0.0, 0.0, T0));

        long sobreLaRuta = ReglasSeguimiento.etaMinutos(posiciones, new Coordenada(0.009, 0.009), t).getAsLong();
        long enLinea = ReglasSeguimiento.etaMinutos(posiciones, new Coordenada(0.009, 0.009)).getAsLong();

        // La ruta (2 km) a 36 km/h son 4 min; en linea recta (1.4 km) a 40 km/h son 3 min.
        assertThat(sobreLaRuta).isGreaterThan(enLinea);
        assertThat(ReglasSeguimiento.etaMinutos(posiciones, new Coordenada(0.009, 0.009), null).getAsLong())
                .isEqualTo(enLinea);
    }

    @Test
    void hay_retraso_solo_si_se_pasa_de_la_tolerancia() {
        Duration viaje = Duration.ofHours(4);
        Instant prevista = T0.plus(viaje);

        assertThat(ReglasSeguimiento.hayRetraso(prevista, prevista.plus(Duration.ofMinutes(40)), viaje)).isFalse();
        assertThat(ReglasSeguimiento.hayRetraso(prevista, prevista.plus(Duration.ofMinutes(50)), viaje)).isTrue();
        // En un viaje corto vale el minimo de 15 minutos.
        Duration corto = Duration.ofMinutes(30);
        assertThat(ReglasSeguimiento.hayRetraso(T0.plus(corto), T0.plus(corto).plus(Duration.ofMinutes(14)), corto))
                .isFalse();
        assertThat(ReglasSeguimiento.hayRetraso(T0.plus(corto), T0.plus(corto).plus(Duration.ofMinutes(16)), corto))
                .isTrue();
    }
}
