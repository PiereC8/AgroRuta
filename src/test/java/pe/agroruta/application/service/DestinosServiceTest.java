package pe.agroruta.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import pe.agroruta.domain.model.DestinoNoEncontradoException;
import pe.agroruta.domain.model.DestinoVenta;
import pe.agroruta.domain.model.DestinoYaExisteException;
import pe.agroruta.domain.model.Dinero;
import pe.agroruta.domain.model.DominioException;
import pe.agroruta.domain.model.EstadoLote;
import pe.agroruta.domain.model.EventoLote;
import pe.agroruta.domain.model.Lote;
import pe.agroruta.domain.model.LoteNoEncontradoException;
import pe.agroruta.domain.port.out.DestinoRepositoryPort;
import pe.agroruta.domain.port.out.PreciosDestinoPort;
import pe.agroruta.domain.port.out.UbicacionDestinoPort;
import pe.agroruta.domain.port.out.LoteRepositoryPort;
import pe.agroruta.domain.port.out.TarifaPort;

class DestinosServiceTest {

    static class DestinosEnMemoria implements DestinoRepositoryPort, PreciosDestinoPort, UbicacionDestinoPort {
        final List<DestinoVenta> destinos = new ArrayList<>();
        final Map<String, Dinero> precios = new HashMap<>();
        final Map<Long, Dinero> tarifas = new HashMap<>();
        final Map<Long, pe.agroruta.domain.model.Coordenada> coordenadas = new HashMap<>();

        @Override
        public void guardarCoordenada(Long id, pe.agroruta.domain.model.Coordenada coordenada) {
            coordenadas.put(id, coordenada);
        }

        @Override
        public Optional<pe.agroruta.domain.model.Coordenada> buscarCoordenada(Long id) {
            return Optional.ofNullable(coordenadas.get(id));
        }

        @Override
        public boolean existePorNombre(String nombreDestino) {
            return destinos.stream().anyMatch(d -> d.nombreDestino().equalsIgnoreCase(nombreDestino));
        }

        @Override
        public DestinoVenta guardar(DestinoVenta destino) {
            DestinoVenta guardado = destino.conId((long) destinos.size() + 1);
            destinos.add(guardado);
            return guardado;
        }

        @Override
        public Optional<DestinoVenta> buscarPorId(Long id) {
            return destinos.stream().filter(d -> d.idDestinoVenta().equals(id)).findFirst();
        }

        @Override
        public List<DestinoVenta> listarActivos() {
            return destinos.stream().filter(DestinoVenta::destinoActivo).toList();
        }

        @Override
        public void guardarPrecioEsperado(Long id, String producto, Dinero precio) {
            precios.put(id + "/" + producto.toLowerCase(), precio);
        }

        @Override
        public Map<Long, Dinero> preciosEsperadosPorKg(String producto) {
            Map<Long, Dinero> resultado = new HashMap<>();
            precios.forEach((llave, precio) -> {
                String[] partes = llave.split("/");
                if (partes[1].equalsIgnoreCase(producto)) {
                    resultado.put(Long.parseLong(partes[0]), precio);
                }
            });
            return resultado;
        }

        @Override
        public void guardarTarifaFlete(Long id, Dinero costo) {
            tarifas.put(id, costo);
        }
    }

    private static class LotesFijos implements LoteRepositoryPort {
        final Lote lote;

        LotesFijos(Lote lote) {
            this.lote = lote;
        }

        @Override
        public Optional<Lote> buscarParaActualizar(Long idLote) {
            return buscarPorId(idLote);
        }

        @Override
        public Lote guardarNuevo(Lote lote, EventoLote primerEvento) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void actualizarEstado(Long idLote, EstadoLote nuevoEstado, EventoLote evento) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Lote> buscarPorId(Long idLote) {
            return lote != null && lote.idLote().equals(idLote) ? Optional.of(lote) : Optional.empty();
        }

        @Override
        public List<Lote> listar(int pagina, int tamano) {
            return List.of();
        }

        @Override
        public List<EventoLote> historial(Long idLote) {
            return List.of();
        }
    }

    private static Lote lote(EstadoLote estado) {
        return new Lote(1L, "Palta Hass", new BigDecimal("1000"), LocalDate.of(2026, 11, 5), "Sur", estado,
                "dueno.demo", Instant.parse("2026-11-06T15:00:00Z"));
    }

    private final DestinosEnMemoria destinos = new DestinosEnMemoria();
    private final AdministrarDestinosService administrar = new AdministrarDestinosService(destinos, destinos, destinos);

    @Test
    void registra_un_destino_y_no_permite_repetir_el_nombre() {
        DestinoVenta destino = administrar.registrarDestino("Mercado Lima", "Lima", false);

        assertThat(destino.idDestinoVenta()).isEqualTo(1L);
        assertThatThrownBy(() -> administrar.registrarDestino("mercado lima", "Lima", false))
                .isInstanceOf(DestinoYaExisteException.class);
    }

    @Test
    void valida_precio_producto_y_destino_al_fijar_el_precio() {
        administrar.registrarDestino("Mercado Lima", "Lima", false);

        assertThatThrownBy(() -> administrar.fijarPrecioEsperado(1L, "Palta", Dinero.CERO))
                .isInstanceOf(DominioException.class);
        assertThatThrownBy(() -> administrar.fijarPrecioEsperado(1L, " ", Dinero.de("5")))
                .isInstanceOf(DominioException.class);
        assertThatThrownBy(() -> administrar.fijarPrecioEsperado(99L, "Palta", Dinero.de("5")))
                .isInstanceOf(DestinoNoEncontradoException.class);

        administrar.fijarPrecioEsperado(1L, "Palta Hass", Dinero.de("5.50"));
        assertThat(destinos.preciosEsperadosPorKg("palta hass")).containsEntry(1L, Dinero.de("5.50"));
    }

    @Test
    void la_ubicacion_del_destino_debe_estar_dentro_del_peru() {
        administrar.registrarDestino("Gran Mercado Mayorista de Tacna", "Tacna", true);

        // Madrid, Santiago de Chile y el océano frente a Lima: fuera del Perú.
        assertThatThrownBy(() -> administrar.fijarCoordenadas(1L, 40.4168, -3.7038))
                .isInstanceOf(DominioException.class).hasMessageContaining("Perú");
        assertThatThrownBy(() -> administrar.fijarCoordenadas(1L, -33.4489, -70.6693))
                .isInstanceOf(DominioException.class);
        assertThatThrownBy(() -> administrar.fijarCoordenadas(1L, -12.05, -82.0))
                .isInstanceOf(DominioException.class);
        assertThat(destinos.coordenadas).isEmpty();

        administrar.fijarCoordenadas(1L, -18.0105, -70.2486);
        assertThat(destinos.coordenadas).containsKey(1L);
    }

    @Test
    void consulta_la_ubicacion_guardada_del_destino_y_esta_vacia_si_aun_no_se_marco() {
        administrar.registrarDestino("Mercado Tacna", "Tacna", true);

        assertThat(administrar.coordenadasDe(1L)).isEmpty();
        administrar.fijarCoordenadas(1L, -18.0105, -70.2486);

        assertThat(administrar.coordenadasDe(1L)).hasValueSatisfying(c -> {
            assertThat(c.latitud()).isEqualTo(-18.0105);
            assertThat(c.longitud()).isEqualTo(-70.2486);
        });
        assertThatThrownBy(() -> administrar.coordenadasDe(99L)).isInstanceOf(DestinoNoEncontradoException.class);
    }

    @Test
    void un_destino_local_no_acepta_tarifa_y_el_flete_no_puede_ser_negativo() {
        administrar.registrarDestino("Mercado Tacna", "Tacna", true);
        administrar.registrarDestino("Mercado Lima", "Lima", false);

        assertThatThrownBy(() -> administrar.fijarTarifaFlete(1L, Dinero.de("1")))
                .isInstanceOf(DominioException.class).hasMessageContaining("local");
        assertThatThrownBy(() -> administrar.fijarTarifaFlete(2L, Dinero.de("-1")))
                .isInstanceOf(DominioException.class);

        administrar.fijarTarifaFlete(2L, Dinero.de("1.20"));
        assertThat(destinos.tarifas).containsEntry(2L, Dinero.de("1.20"));
    }

    @Test
    void cotiza_pidiendo_tarifas_solo_de_los_destinos_externos_con_precio() {
        administrar.registrarDestino("Mercado Tacna", "Tacna", true);
        administrar.registrarDestino("Mercado Lima", "Lima", false);
        administrar.registrarDestino("Mercado Cusco", "Cusco", false);
        administrar.fijarPrecioEsperado(1L, "Palta Hass", Dinero.de("3.00"));
        administrar.fijarPrecioEsperado(2L, "Palta Hass", Dinero.de("5.50"));
        administrar.fijarTarifaFlete(2L, Dinero.de("1.20"));
        List<Collection<Long>> pedidos = new ArrayList<>();
        TarifaPort tarifas = ids -> {
            pedidos.add(ids);
            Map<Long, Dinero> resultado = new HashMap<>();
            ids.forEach(id -> resultado.put(id, destinos.tarifas.get(id)));
            return resultado;
        };

        var cotizacion = new CotizarFleteService(new LotesFijos(lote(EstadoLote.REGISTRADO)), destinos, destinos, tarifas,
                new Falsos.PedidosEnMemoria())
                .cotizar(1L);

        assertThat(pedidos).hasSize(1);
        assertThat(pedidos.get(0)).containsExactly(2L);
        assertThat(cotizacion.mejorOpcion().orElseThrow().destino().nombreDestino()).isEqualTo("Mercado Lima");
        assertThat(cotizacion.omitidos()).extracting(o -> o.destino().nombreDestino()).containsExactly("Mercado Cusco");
    }

    @Test
    void no_cotiza_lotes_inexistentes_ni_lotes_con_todo_comprometido() {
        TarifaPort sinTarifas = ids -> Map.of();
        var pedidos = new Falsos.PedidosEnMemoria();
        pedidos.guardarNuevo(new pe.agroruta.domain.model.Pedido(null, 1L, 1L, 1L, new BigDecimal("1000.000"),
                Dinero.de("5"), pe.agroruta.domain.model.EstadoPedido.PENDIENTE, "x1x",
                Instant.parse("2026-11-06T15:00:00Z")));

        assertThatThrownBy(() -> new CotizarFleteService(new LotesFijos(null), destinos, destinos, sinTarifas,
                new Falsos.PedidosEnMemoria()).cotizar(1L)).isInstanceOf(LoteNoEncontradoException.class);
        assertThatThrownBy(() -> new CotizarFleteService(new LotesFijos(lote(EstadoLote.RESERVADO)), destinos, destinos,
                sinTarifas, pedidos).cotizar(1L)).isInstanceOf(DominioException.class)
                .hasMessageContaining("no tiene kg disponibles");
    }

    @Test
    void cotiza_solo_los_kg_que_quedan_disponibles_del_lote() {
        administrar.registrarDestino("Mercado Lima", "Lima", false);
        administrar.fijarPrecioEsperado(1L, "Palta Hass", Dinero.de("6"));
        TarifaPort tarifas = ids -> Map.of(1L, Dinero.de("1"));
        var pedidos = new Falsos.PedidosEnMemoria();
        pedidos.guardarNuevo(new pe.agroruta.domain.model.Pedido(null, 1L, 1L, 1L, new BigDecimal("400.000"),
                Dinero.de("5"), pe.agroruta.domain.model.EstadoPedido.DESPACHADO, "x1x",
                Instant.parse("2026-11-06T15:00:00Z")));

        var cotizacion = new CotizarFleteService(new LotesFijos(lote(EstadoLote.RESERVADO)), destinos, destinos, tarifas,
                pedidos).cotizar(1L);

        assertThat(cotizacion.cantidadKg()).isEqualByComparingTo("600");
        // (6 - 1) x 600 kg = 3000, no (6 - 1) x 1000.
        assertThat(cotizacion.mejorOpcion().orElseThrow().gananciaNeta().monto()).isEqualByComparingTo("3000");
    }
}
