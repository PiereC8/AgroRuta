package pe.agroruta.infrastructure.adapter.out.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import pe.agroruta.domain.model.Dinero;
import pe.agroruta.domain.port.out.PreciosDestinoPort;
import pe.agroruta.domain.port.out.TarifaPort;
import pe.agroruta.infrastructure.config.TenantContext;

class CacheDeTarifasTest {

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

    /** Un solo almacén que hace de tarifas y de precios, y cuenta cuántas veces se le pregunta. */
    private static final class Almacen implements TarifaPort, PreciosDestinoPort {
        final Map<Long, Dinero> fletes = new HashMap<>();
        final Map<Long, Dinero> precios = new HashMap<>();
        final List<Collection<Long>> consultasDeFlete = new ArrayList<>();
        int consultasDePrecio;

        @Override
        public Map<Long, Dinero> costosFletePorKg(Collection<Long> ids) {
            consultasDeFlete.add(List.copyOf(ids));
            Map<Long, Dinero> resultado = new HashMap<>();
            for (Long id : ids) {
                if (fletes.containsKey(id)) {
                    resultado.put(id, fletes.get(id));
                }
            }
            return resultado;
        }

        @Override
        public void guardarPrecioEsperado(Long idDestinoVenta, String productoLote, Dinero precioPorKg) {
            precios.put(idDestinoVenta, precioPorKg);
        }

        @Override
        public Map<Long, Dinero> preciosEsperadosPorKg(String productoLote) {
            consultasDePrecio++;
            return new HashMap<>(precios);
        }

        @Override
        public void guardarTarifaFlete(Long idDestinoVenta, Dinero costoFletePorKg) {
            fletes.put(idDestinoVenta, costoFletePorKg);
        }
    }

    private final RelojMutable reloj = new RelojMutable();
    private final Almacen almacen = new Almacen();
    private final MemoriaConExpiracion memoria = new MemoriaConExpiracion(Duration.ofMinutes(5), reloj);
    private final TarifaPort tarifas = new TarifaPortConCache(almacen, memoria);
    private final PreciosDestinoPort precios = new PreciosDestinoPortConCache(almacen, memoria);

    @AfterEach
    void limpiar() {
        TenantContext.limpiar();
    }

    private <T> T en(String negocio, java.util.function.Supplier<T> accion) {
        return TenantContext.ejecutarEn(negocio, accion);
    }

    @Test
    void la_segunda_consulta_del_mismo_flete_sale_de_la_memoria() {
        almacen.fletes.put(1L, Dinero.de("1.20"));

        var primera = en("empresa_demo", () -> tarifas.costosFletePorKg(List.of(1L)));
        var segunda = en("empresa_demo", () -> tarifas.costosFletePorKg(List.of(1L)));

        assertThat(primera).containsEntry(1L, Dinero.de("1.20"));
        assertThat(segunda).isEqualTo(primera);
        assertThat(almacen.consultasDeFlete).hasSize(1);
    }

    @Test
    void solo_se_piden_al_almacen_los_destinos_que_faltan() {
        almacen.fletes.put(1L, Dinero.de("1.20"));
        almacen.fletes.put(2L, Dinero.de("0.90"));
        en("empresa_demo", () -> tarifas.costosFletePorKg(List.of(1L)));

        var resultado = en("empresa_demo", () -> tarifas.costosFletePorKg(List.of(1L, 2L)));

        assertThat(resultado).containsEntry(1L, Dinero.de("1.20")).containsEntry(2L, Dinero.de("0.90"));
        assertThat(almacen.consultasDeFlete.get(1)).containsExactly(2L);
    }

    @Test
    void un_destino_sin_tarifa_no_se_guarda_como_ausente_y_se_vuelve_a_preguntar() {
        en("empresa_demo", () -> tarifas.costosFletePorKg(List.of(5L)));
        almacen.fletes.put(5L, Dinero.de("2.00"));

        assertThat(en("empresa_demo", () -> tarifas.costosFletePorKg(List.of(5L)))).containsKey(5L);
    }

    @Test
    void cada_negocio_tiene_su_propia_memoria() {
        almacen.fletes.put(1L, Dinero.de("1.20"));
        en("empresa_demo", () -> tarifas.costosFletePorKg(List.of(1L)));

        en("chacra_demo", () -> tarifas.costosFletePorKg(List.of(1L)));

        assertThat(almacen.consultasDeFlete).hasSize(2);
    }

    @Test
    void pasado_el_tiempo_vuelve_a_preguntar_al_almacen() {
        almacen.fletes.put(1L, Dinero.de("1.20"));
        en("empresa_demo", () -> tarifas.costosFletePorKg(List.of(1L)));

        reloj.avanzar(Duration.ofMinutes(6));
        almacen.fletes.put(1L, Dinero.de("1.50"));

        assertThat(en("empresa_demo", () -> tarifas.costosFletePorKg(List.of(1L))))
                .containsEntry(1L, Dinero.de("1.50"));
    }

    @Test
    void guardar_una_tarifa_borra_la_memoria_del_negocio_y_se_ve_el_valor_nuevo() {
        almacen.fletes.put(1L, Dinero.de("1.20"));
        en("empresa_demo", () -> tarifas.costosFletePorKg(List.of(1L)));

        en("empresa_demo", () -> {
            precios.guardarTarifaFlete(1L, Dinero.de("2.50"));
            return null;
        });

        assertThat(en("empresa_demo", () -> tarifas.costosFletePorKg(List.of(1L))))
                .containsEntry(1L, Dinero.de("2.50"));
    }

    @Test
    void los_precios_tambien_se_guardan_en_memoria_y_cambian_al_guardar_uno_nuevo() {
        almacen.precios.put(1L, Dinero.de("5.00"));
        en("empresa_demo", () -> precios.preciosEsperadosPorKg("Palta Hass"));
        en("empresa_demo", () -> precios.preciosEsperadosPorKg("Palta Hass"));
        assertThat(almacen.consultasDePrecio).isEqualTo(1);

        en("empresa_demo", () -> {
            precios.guardarPrecioEsperado(1L, "Palta Hass", Dinero.de("6.00"));
            return null;
        });

        assertThat(en("empresa_demo", () -> precios.preciosEsperadosPorKg("Palta Hass")))
                .containsEntry(1L, Dinero.de("6.00"));
    }

    @Test
    void sin_negocio_activo_no_se_guarda_nada_en_memoria() {
        almacen.fletes.put(1L, Dinero.de("1.20"));

        tarifas.costosFletePorKg(List.of(1L));
        tarifas.costosFletePorKg(List.of(1L));

        assertThat(almacen.consultasDeFlete).hasSize(2);
    }
}
