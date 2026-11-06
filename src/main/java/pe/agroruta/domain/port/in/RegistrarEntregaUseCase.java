package pe.agroruta.domain.port.in;

import java.math.BigDecimal;

import pe.agroruta.domain.model.Liquidacion;

public interface RegistrarEntregaUseCase {

    /** Registra lo recibido, la merma y el rechazo, y liquida el lote en el mismo acto. */
    Liquidacion registrar(Comando comando);

    record Comando(Long idEnvio, BigDecimal cantidadRecibidaKg, BigDecimal mermaKg, BigDecimal rechazoKg,
                   String usuario) {
    }
}
