package pe.agroruta.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import pe.agroruta.domain.model.DominioException;
import pe.agroruta.domain.model.EventoLote;
import pe.agroruta.domain.model.Lote;
import pe.agroruta.domain.model.LoteNoEncontradoException;
import pe.agroruta.domain.model.TipoEventoLote;
import pe.agroruta.domain.port.in.RegistrarLoteUseCase.Comando;
import pe.agroruta.domain.port.out.LoteRepositoryPort;

class LotesServiceTest {

    private static final Clock RELOJ =
            Clock.fixed(Instant.parse("2026-11-07T03:00:00Z"), ZoneId.of("America/Lima"));

    private static class LotesEnMemoria implements LoteRepositoryPort {
        final List<Lote> lotes = new ArrayList<>();
        final List<EventoLote> eventos = new ArrayList<>();

        @Override
        public Lote guardarNuevo(Lote lote, EventoLote primerEvento) {
            Lote guardado = lote.conId((long) lotes.size() + 1);
            lotes.add(guardado);
            eventos.add(primerEvento);
            return guardado;
        }

        @Override
        public Optional<Lote> buscarPorId(Long idLote) {
            return lotes.stream().filter(l -> l.idLote().equals(idLote)).findFirst();
        }

        @Override
        public List<Lote> listar(int pagina, int tamano) {
            return lotes.stream().skip((long) pagina * tamano).limit(tamano).toList();
        }

        @Override
        public List<EventoLote> historial(Long idLote) {
            return eventos;
        }
    }

    private final LotesEnMemoria repositorio = new LotesEnMemoria();
    private final RegistrarLoteService registrar = new RegistrarLoteService(repositorio, RELOJ);
    private final ConsultarLotesService consultar = new ConsultarLotesService(repositorio);

    private Comando comando(LocalDate cosecha) {
        return new Comando("Palta Hass", new BigDecimal("1250.5"), cosecha, "Chacra Sur", "operador.demo");
    }

    @Test
    void registra_el_lote_con_su_evento_de_registro() {
        Lote lote = registrar.registrar(comando(LocalDate.of(2026, 11, 5)));

        assertThat(lote.idLote()).isEqualTo(1L);
        EventoLote evento = repositorio.eventos.get(0);
        assertThat(evento.tipoEvento()).isEqualTo(TipoEventoLote.REGISTRO);
        assertThat(evento.usuarioEvento()).isEqualTo("operador.demo");
        assertThat(evento.descripcionEvento()).contains("1250.500 kg", "Palta Hass");
    }

    @Test
    void usa_la_fecha_de_lima_para_decidir_si_la_cosecha_es_futura() {
        // A las 22:00 del 6 de noviembre en Lima ya son las 03:00 UTC del 7: "hoy" sigue siendo el 6.
        registrar.registrar(comando(LocalDate.of(2026, 11, 6)));

        assertThatThrownBy(() -> registrar.registrar(comando(LocalDate.of(2026, 11, 7))))
                .isInstanceOf(DominioException.class);
    }

    @Test
    void si_el_lote_es_invalido_no_guarda_nada() {
        assertThatThrownBy(() -> registrar.registrar(
                new Comando("Palta", BigDecimal.ZERO, LocalDate.of(2026, 11, 5), "Chacra", "operador.demo")))
                .isInstanceOf(DominioException.class);
        assertThat(repositorio.lotes).isEmpty();
    }

    @Test
    void obtener_un_lote_inexistente_lanza_no_encontrado() {
        assertThatThrownBy(() -> consultar.obtener(99L)).isInstanceOf(LoteNoEncontradoException.class);
        assertThatThrownBy(() -> consultar.historial(99L)).isInstanceOf(LoteNoEncontradoException.class);
    }

    @Test
    void el_historial_de_un_lote_existente_trae_sus_eventos() {
        Lote lote = registrar.registrar(comando(LocalDate.of(2026, 11, 5)));

        assertThat(consultar.historial(lote.idLote())).hasSize(1);
    }

    @Test
    void la_paginacion_valida_sus_limites() {
        assertThatThrownBy(() -> consultar.listar(-1, 10)).isInstanceOf(DominioException.class);
        assertThatThrownBy(() -> consultar.listar(0, 0)).isInstanceOf(DominioException.class);
        assertThatThrownBy(() -> consultar.listar(0, 201)).isInstanceOf(DominioException.class);
        assertThat(consultar.listar(0, 200)).isEmpty();
    }
}
