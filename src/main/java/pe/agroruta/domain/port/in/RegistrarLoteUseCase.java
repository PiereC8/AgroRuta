package pe.agroruta.domain.port.in;

import java.math.BigDecimal;
import java.time.LocalDate;

import pe.agroruta.domain.model.Lote;

// RFF-05: puerto de entrada para registrar lotes.
public interface RegistrarLoteUseCase {

    Lote registrar(Comando comando);

    record Comando(String productoLote, BigDecimal cantidadKg, LocalDate fechaCosecha, String chacraOrigen,
                   String usuarioRegistro) {
    }
}
