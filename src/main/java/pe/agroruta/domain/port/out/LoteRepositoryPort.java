package pe.agroruta.domain.port.out;

import java.util.List;
import java.util.Optional;

import pe.agroruta.domain.model.EstadoLote;
import pe.agroruta.domain.model.EventoLote;
import pe.agroruta.domain.model.Lote;

public interface LoteRepositoryPort {

    /** Guarda el lote y su primer evento en una sola operación: o se guardan los dos o ninguno. */
    Lote guardarNuevo(Lote lote, EventoLote primerEvento);

    /** Cambia el estado del lote y deja constancia en su historial. */
    void actualizarEstado(Long idLote, EstadoLote nuevoEstado, EventoLote evento);

    Optional<Lote> buscarPorId(Long idLote);

    List<Lote> listar(int pagina, int tamano);

    List<EventoLote> historial(Long idLote);
}
