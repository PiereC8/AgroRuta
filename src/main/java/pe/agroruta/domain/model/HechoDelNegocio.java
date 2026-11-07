package pe.agroruta.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Algo que pasó en el negocio y que la política de avisos puede convertir en correos. */
public sealed interface HechoDelNegocio {

    record EntregaRegistrada(Long idLiquidacion, String comprador, String producto, String destino,
                             BigDecimal kgDespachados, BigDecimal mermaKg, BigDecimal rechazoKg, Dinero gananciaReal,
                             Dinero perdidaMermaRechazo) implements HechoDelNegocio {
    }

    record PagoRecibido(Long idPago, String comprador, Dinero monto, Long idPedido) implements HechoDelNegocio {
    }

    record ComprobanteRechazado(Long idComprobante, String numero, String mensaje) implements HechoDelNegocio {
    }

    record ComprobanteAtascado(Long idComprobante, String numero, long horas) implements HechoDelNegocio {
    }

    record CobroVencido(Long idLiquidacion, String comprador, Dinero monto, int dias) implements HechoDelNegocio {
    }

    record AlertaDeEnvio(Long idAlerta, Long idEnvio, TipoAlerta tipo, String destino, String descripcion)
            implements HechoDelNegocio {
    }

    record ResumenSemanal(LocalDate desde, LocalDate hasta, Resumen resumen) implements HechoDelNegocio {
    }
}
