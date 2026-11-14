package pe.agroruta.domain.port.out;

import java.util.List;
import java.util.Optional;

import pe.agroruta.domain.model.EstadoLote;
import pe.agroruta.domain.model.EventoLote;
import pe.agroruta.domain.model.Lote;

public interface LoteRepositoryPort {

    /** Guarda el lote y su primer evento en una sola operación: o se guardan los dos o ninguno. */
    Lote guardarNuevo(Lote lote, EventoLote primerEvento);

    void actualizarEstado(Long idLote, EstadoLote nuevoEstado, EventoLote evento);

    Optional<Lote> buscarPorId(Long idLote);

    /**
     * Igual que buscarPorId, pero bloquea la fila del lote hasta que termine la transacción actual. Sirve para que dos pedidos a la vez no reserven los mismos kg.
     */
    Optional<Lote> buscarParaActualizar(Long idLote);

    List<Lote> listar(int pagina, int tamano);

    List<EventoLote> historial(Long idLote);
}
