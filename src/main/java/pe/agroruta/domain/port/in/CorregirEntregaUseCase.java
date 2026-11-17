package pe.agroruta.domain.port.in;

import java.math.BigDecimal;

import pe.agroruta.domain.model.Liquidacion;

// RFF-12: puerto de entrada para corregir la entrega.
/** Corrige lo recibido, la merma y el rechazo de una entrega mientras no tenga un pago aprobado ni en curso. */
public interface CorregirEntregaUseCase {

    Liquidacion corregir(Comando comando);

    record Comando(Long idEnvio, BigDecimal cantidadRecibidaKg, BigDecimal mermaKg, BigDecimal rechazoKg,
                   String usuario) {
    }
}
