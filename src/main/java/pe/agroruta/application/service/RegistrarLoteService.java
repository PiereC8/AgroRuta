package pe.agroruta.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

import pe.agroruta.domain.model.EventoLote;
import pe.agroruta.domain.model.Lote;
import pe.agroruta.domain.model.TipoEventoLote;
import pe.agroruta.domain.port.in.RegistrarLoteUseCase;
import pe.agroruta.domain.port.out.LoteRepositoryPort;

public class RegistrarLoteService implements RegistrarLoteUseCase {

    private final LoteRepositoryPort lotes;
    private final Clock reloj;

    public RegistrarLoteService(LoteRepositoryPort lotes, Clock reloj) {
        this.lotes = lotes;
        this.reloj = reloj;
    }

    @Override
    public Lote registrar(Comando comando) {
        Instant ahora = reloj.instant();
        Lote lote = Lote.nuevo(comando.productoLote(), comando.cantidadKg(), comando.fechaCosecha(),
                comando.chacraOrigen(), comando.usuarioRegistro(), ahora, LocalDate.now(reloj));
        EventoLote evento = new EventoLote(TipoEventoLote.REGISTRO,
                "Lote registrado: " + lote.cantidadKg().toPlainString() + " kg de " + lote.productoLote()
                        + " de " + lote.chacraOrigen(),
                lote.usuarioRegistro(), ahora);
        return lotes.guardarNuevo(lote, evento);
    }
}
