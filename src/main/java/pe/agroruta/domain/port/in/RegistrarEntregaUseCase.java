package pe.agroruta.domain.port.in;

import java.math.BigDecimal;

import pe.agroruta.domain.model.Liquidacion;

// RFF-12: puerto de entrada para registrar la entrega.
public interface RegistrarEntregaUseCase {

    Liquidacion registrar(Comando comando);

    record Comando(Long idEnvio, BigDecimal cantidadRecibidaKg, BigDecimal mermaKg, BigDecimal rechazoKg,
                   String usuario) {
    }
}
